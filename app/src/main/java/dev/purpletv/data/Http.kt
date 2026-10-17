package dev.purpletv.data

import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class HttpStatusException(val code: Int, val url: String, val body: String) :
    IOException("HTTP $code for $url")

object Http {
    const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 12; Android TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build())
        }
        .build()

    private val JSON = "application/json; charset=utf-8".toMediaType()

    fun get(url: String, headers: Map<String, String> = emptyMap()): String =
        execute(Request.Builder().url(url).apply { headers.forEach { (k, v) -> header(k, v) } }.build())

    fun getJson(url: String, headers: Map<String, String> = emptyMap()) = JSONObject(get(url, headers))

    fun postJson(url: String, body: Any, headers: Map<String, String> = emptyMap()): String =
        execute(
            Request.Builder().url(url).post(body.toString().toRequestBody(JSON))
                .apply { headers.forEach { (k, v) -> header(k, v) } }.build()
        )

    fun postForm(url: String, fields: Map<String, String>): String =
        execute(
            Request.Builder().url(url)
                .post(FormBody.Builder().apply { fields.forEach { (k, v) -> add(k, v) } }.build())
                .build()
        )

    private fun execute(request: Request): String =
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw HttpStatusException(response.code, request.url.toString(), body)
            body
        }
}
