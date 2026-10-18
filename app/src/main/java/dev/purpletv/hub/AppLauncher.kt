package dev.purpletv.hub

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import dev.purpletv.core.AppId
import dev.purpletv.data.Settings

/**
 * Every app inside PurpleTV also gets its own tile on the TV home screen (an activity-alias in
 * the manifest). Disabled apps lose their tile.
 */
object AppLauncher {

    fun sync(context: Context, settings: Settings) {
        val pm = context.packageManager
        AppId.entries.forEach { app ->
            val state = if (settings.isEnabled(app)) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            val component = ComponentName(context.packageName, app.launcherAlias)
            runCatching {
                if (pm.getComponentEnabledSetting(component) != state) {
                    pm.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
                }
            }
        }
    }

    /** The app tile the activity was started from, null for the main PurpleTV tile. */
    fun appFor(intent: Intent?): AppId? {
        val name = intent?.component?.className ?: return null
        return AppId.entries.firstOrNull { it.launcherAlias == name }
    }
}
