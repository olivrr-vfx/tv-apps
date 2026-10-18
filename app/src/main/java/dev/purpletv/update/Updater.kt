package dev.purpletv.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import dev.purpletv.BuildConfig
import dev.purpletv.data.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.Request

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Downloading(val version: String, val progress: Float) : UpdateState
    data class Installing(val version: String) : UpdateState
    /** The user has to allow "install unknown apps" for Couch once. */
    data object NeedsPermission : UpdateState
    data class Failed(val message: String) : UpdateState
}

/**
 * Self-update from the GitHub releases of this repo. Each CI build publishes a release tagged
 * `build-<run number>` and the APK's versionCode is that same run number.
 */
object Updater {
    private const val REPO = "olivrr-vfx/tv-apps"
    private const val ACTION_STATUS = "dev.purpletv.UPDATE_STATUS"

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state

    private data class Release(val versionCode: Int, val name: String, val apkUrl: String)

    private fun latestRelease(): Release? {
        val json = Http.getJson(
            "https://api.github.com/repos/$REPO/releases/latest",
            mapOf("Accept" to "application/vnd.github+json"),
        )
        val code = json.optString("tag_name").removePrefix("build-").toIntOrNull() ?: return null
        val assets = json.optJSONArray("assets") ?: return null
        val apks = (0 until assets.length()).map { assets.getJSONObject(it) }.filter { it.optString("name").endsWith(".apk") }
        val apk = apks.firstOrNull { it.optString("name") == "Couch.apk" } ?: apks.firstOrNull() ?: return null
        return Release(code, json.optString("name").ifBlank { "1.0.$code" }, apk.getString("browser_download_url"))
    }

    /** Checks for a newer build and, if there is one, downloads and installs it. */
    suspend fun checkAndInstall(context: Context, manual: Boolean = false) {
        val current = _state.value
        if (current is UpdateState.Downloading || current is UpdateState.Installing || current == UpdateState.Checking) return
        _state.value = UpdateState.Checking
        val release = withContext(Dispatchers.IO) { runCatching { latestRelease() } }.getOrElse {
            Log.w("Updater", "check failed", it)
            _state.value = if (manual) UpdateState.Failed("Update-Prüfung fehlgeschlagen") else UpdateState.Idle
            return
        }
        if (release == null || release.versionCode <= BuildConfig.VERSION_CODE) {
            _state.value = if (manual) UpdateState.UpToDate else UpdateState.Idle
            return
        }
        if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            _state.value = UpdateState.NeedsPermission
            return
        }
        try {
            withContext(Dispatchers.IO) { downloadAndInstall(context.applicationContext, release) }
        } catch (e: Exception) {
            Log.w("Updater", "update failed", e)
            _state.value = UpdateState.Failed("Update fehlgeschlagen: ${e.message}")
        }
    }

    /** Streams the APK straight into a PackageInstaller session, no temp file or FileProvider needed. */
    private fun downloadAndInstall(context: Context, release: Release) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            // Android 12+: once Couch is its own installer of record, updates go through
            // without a confirmation dialog.
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            _state.value = UpdateState.Downloading(release.name, 0f)
            Http.client.newCall(Request.Builder().url(release.apkUrl).build()).execute().use { response ->
                if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
                val body = response.body ?: throw IllegalStateException("Leere Antwort")
                val total = body.contentLength()
                session.openWrite("Couch.apk", 0, total).use { out ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        var written = 0L
                        var lastReported = 0f
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            out.write(buffer, 0, n)
                            written += n
                            if (total > 0) {
                                val p = written.toFloat() / total
                                if (p - lastReported >= 0.02f) {
                                    lastReported = p
                                    _state.value = UpdateState.Downloading(release.name, p)
                                }
                            }
                        }
                    }
                    session.fsync(out)
                }
            }
            _state.value = UpdateState.Installing(release.name)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
            val intent = Intent(context, InstallResultReceiver::class.java).setAction(ACTION_STATUS)
            session.commit(PendingIntent.getBroadcast(context, sessionId, intent, flags).intentSender)
        }
    }

    /** Opens the "install unknown apps" setting for Couch (not present on every TV). */
    fun openPermissionSettings(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 26) return false
        return runCatching {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.isSuccess
    }

    fun dismiss() {
        _state.value = UpdateState.Idle
    }

    internal fun onResult(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (confirm != null) context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PackageInstaller.STATUS_SUCCESS -> _state.value = UpdateState.Idle
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                Log.w("Updater", "install status $status: $msg")
                _state.value = if (status == PackageInstaller.STATUS_FAILURE_ABORTED) UpdateState.Idle
                else UpdateState.Failed("Installation fehlgeschlagen: ${msg ?: status}")
            }
        }
    }
}

class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Updater.onResult(context, intent)
}
