package dev.purpletv.youtube.api

import dev.purpletv.data.Http
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import java.util.concurrent.atomic.AtomicLong
import org.schabi.newpipe.extractor.downloader.Request as NpRequest
import org.schabi.newpipe.extractor.downloader.Response as NpResponse

/**
 * HTTP for YouTube. The shared client forces an Android TV browser user agent, which would break
 * YouTube's API clients (they send their own), so YouTube gets a client without that interceptor.
 */
object YouTubeHttp {
    const val BROWSER_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"

    /** For NewPipeExtractor API calls: their headers win, browser UA otherwise. */
    val apiClient: OkHttpClient = Http.client.newBuilder()
        .apply { interceptors().clear() }
        .addInterceptor { chain ->
            val req = chain.request()
            chain.proceed(if (req.header("User-Agent") == null) req.newBuilder().header("User-Agent", BROWSER_UA).build() else req)
        }
        .build()

    /** For media (googlevideo.com): the stream URLs only work with the client they came from. */
    val mediaClient: OkHttpClient = Http.client.newBuilder()
        .apply { interceptors().clear() }
        .addInterceptor(MediaInterceptor())
        .build()

    private class MediaInterceptor : Interceptor {
        private val requestNumber = AtomicLong(0)

        override fun intercept(chain: Interceptor.Chain): Response {
            val req = chain.request()
            val url = req.url.toString()
            val host = req.url.host
            if (!host.endsWith("googlevideo.com") && !host.endsWith("youtube.com")) {
                return chain.proceed(req.newBuilder().header("User-Agent", BROWSER_UA).build())
            }
            val builder = req.newBuilder()
            if (YoutubeParsingHelper.isWebStreamingUrl(url)) {
                // Web client streams want a POST with a tiny body, like the website player.
                builder.header("User-Agent", BROWSER_UA)
                    .header("Origin", "https://www.youtube.com")
                    .header("Referer", "https://www.youtube.com/")
                if (req.method == "GET" && host.endsWith("googlevideo.com") && url.contains("/videoplayback")) {
                    val withRn = req.url.newBuilder().addQueryParameter("rn", requestNumber.incrementAndGet().toString()).build()
                    builder.url(withRn).post(byteArrayOf(0x78, 0).toRequestBody(null))
                }
            } else {
                builder.header("User-Agent", YoutubeParsingHelper.getVisionOsUserAgent(null))
            }
            return chain.proceed(builder.build())
        }
    }
}

/** NewPipeExtractor's network layer on OkHttp. */
class NewPipeDownloader : Downloader() {
    override fun execute(request: NpRequest): NpResponse {
        val body = request.dataToSend()?.toRequestBody(null)
        val builder = Request.Builder().url(request.url()).method(request.httpMethod(), body)
        request.headers().forEach { (name, values) ->
            builder.removeHeader(name)
            values.forEach { builder.addHeader(name, it) }
        }
        YouTubeHttp.apiClient.newCall(builder.build()).execute().use { response ->
            if (response.code == 429) throw ReCaptchaException("reCaptcha Challenge requested", request.url())
            val text = response.body?.string()
            return NpResponse(response.code, response.message, response.headers.toMultimap(), text, response.request.url.toString())
        }
    }
}
