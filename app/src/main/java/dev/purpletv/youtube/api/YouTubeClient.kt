package dev.purpletv.youtube.api

import android.util.Xml
import dev.purpletv.data.HttpStatusException
import okhttp3.Request
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException
import org.schabi.newpipe.extractor.exceptions.PaidContentException
import org.schabi.newpipe.extractor.exceptions.PrivateContentException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.exceptions.SignInConfirmNotBotException
import org.schabi.newpipe.extractor.kiosk.KioskInfo
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream
import org.xmlpull.v1.XmlPullParser
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Everything needed to play one video. */
data class YtPlayback(
    val video: YtVideo,
    val hlsUrl: String,
    val dashUrl: String,
    val videoOnly: List<VideoStream>,
    val audio: List<AudioStream>,
    val muxed: List<VideoStream>,
    val description: String,
    val related: List<YtVideo>,
)

/** A trending shelf on the YouTube start page. */
data class YtShelf(val id: String, val title: String)

/**
 * YouTube without an account, through NewPipeExtractor (the library behind NewPipe).
 * All calls are blocking.
 */
object YouTubeClient {
    val shelves = listOf(
        YtShelf("live", "Gerade live"),
        YtShelf("trending_gaming", "Gaming"),
        YtShelf("trending_music", "Musik"),
        YtShelf("trending_podcasts_episodes", "Podcasts"),
        YtShelf("trending_movies_and_shows", "Filme & Serien"),
    )

    @Volatile private var initialized = false
    private val service get() = ServiceList.YouTube

    @Synchronized
    fun init(region: String) {
        val country = region.uppercase().ifBlank { "DE" }
        if (!initialized) {
            NewPipe.init(NewPipeDownloader(), Localization("de", country), ContentCountry(country))
            initialized = true
        } else {
            NewPipe.setupLocalization(Localization("de", country), ContentCountry(country))
        }
    }

    // --- Mapping ---------------------------------------------------------------------------

    private fun List<Image>.best(maxWidth: Int = 720): String {
        if (isEmpty()) return ""
        val sized = filter { it.width in 1..maxWidth }.maxByOrNull { it.width }
        return (sized ?: maxByOrNull { it.width } ?: first()).url
    }

    private fun StreamInfoItem.toVideo(): YtVideo? {
        val id = parseVideoId(url) ?: return null
        val live = streamType == StreamType.LIVE_STREAM || streamType == StreamType.AUDIO_LIVE_STREAM
        return YtVideo(
            id = id,
            title = name,
            channelName = uploaderName.orEmpty(),
            channelUrl = uploaderUrl.orEmpty(),
            thumbnail = thumbnails.best().ifEmpty { YtVideo.thumbnailFor(id) },
            durationSec = duration.coerceAtLeast(0),
            views = viewCount,
            uploadedText = textualUploadDate.orEmpty(),
            uploadedMillis = runCatching { uploadDate?.instant?.toEpochMilli() }.getOrNull() ?: 0L,
            isLive = live,
            isShort = url.contains("/shorts/"),
            channelAvatar = uploaderAvatars.best(200),
        )
    }

    private fun ChannelInfoItem.toChannel() = YtChannel(
        id = channelIdFromUrl(url) ?: url,
        url = url,
        name = name,
        avatar = thumbnails.best(240),
        subscribers = subscriberCount,
        description = description.orEmpty(),
    )

    private fun List<InfoItem>.videos() = filterIsInstance<StreamInfoItem>().mapNotNull { it.toVideo() }

    // --- Calls ----------------------------------------------------------------------------

    fun search(query: String): YtSearchResult = wrap {
        val handler = service.searchQHFactory.fromQuery(query, listOf(YoutubeSearchQueryHandlerFactory.ALL), "")
        val info = SearchInfo.getInfo(service, handler)
        YtSearchResult(
            videos = info.relatedItems.videos(),
            channels = info.relatedItems.filterIsInstance<ChannelInfoItem>().map { it.toChannel() },
        )
    }

    fun shelf(id: String): List<YtVideo> = wrap {
        val extractor = service.kioskList.getExtractorById(id, null)
        extractor.fetchPage()
        KioskInfo.getInfo(extractor).relatedItems.videos()
    }

    fun channel(url: String): YtChannelPage = wrap {
        val info = ChannelInfo.getInfo(service, url)
        val channel = YtChannel(
            id = info.id,
            url = info.url,
            name = info.name,
            avatar = info.avatars.best(240),
            subscribers = info.subscriberCount,
            banner = info.banners.best(2560),
            description = info.description.orEmpty(),
        )
        fun tab(filter: String): List<YtVideo> {
            val handler = info.tabs.firstOrNull { filter in it.contentFilters } ?: return emptyList()
            return runCatching { ChannelTabInfo.getInfo(service, handler).relatedItems.videos() }.getOrDefault(emptyList())
                .map { it.copy(channelName = it.channelName.ifEmpty { channel.name }, channelUrl = it.channelUrl.ifEmpty { channel.url }, channelAvatar = it.channelAvatar.ifEmpty { channel.avatar }) }
        }
        YtChannelPage(channel, videos = tab(ChannelTabs.VIDEOS), live = tab(ChannelTabs.LIVESTREAMS).filter { it.isLive })
    }

    /** Resolves a channel link or @handle to its id, name and avatar (for subscribing). */
    fun channelInfo(urlOrHandle: String): YtChannel = wrap {
        val input = urlOrHandle.trim()
        val url = when {
            input.startsWith("http") -> input
            input.startsWith("@") -> "https://www.youtube.com/$input"
            input.startsWith("UC") && input.length == 24 -> "https://www.youtube.com/channel/$input"
            else -> "https://www.youtube.com/@$input"
        }
        val info = ChannelInfo.getInfo(service, url)
        YtChannel(info.id, "https://www.youtube.com/channel/${info.id}", info.name, info.avatars.best(240), info.subscriberCount)
    }

    fun playback(videoId: String): YtPlayback = wrap {
        val info = StreamInfo.getInfo(service, "https://www.youtube.com/watch?v=$videoId")
        val live = info.streamType == StreamType.LIVE_STREAM || info.streamType == StreamType.AUDIO_LIVE_STREAM
        YtPlayback(
            video = YtVideo(
                id = videoId,
                title = info.name,
                channelName = info.uploaderName.orEmpty(),
                channelUrl = info.uploaderUrl.orEmpty(),
                thumbnail = info.thumbnails.best().ifEmpty { YtVideo.thumbnailFor(videoId) },
                durationSec = info.duration.coerceAtLeast(0),
                views = info.viewCount,
                uploadedText = info.textualUploadDate.orEmpty(),
                isLive = live,
                channelAvatar = info.uploaderAvatars.best(200),
            ),
            hlsUrl = info.hlsUrl.orEmpty(),
            dashUrl = info.dashMpdUrl.orEmpty(),
            videoOnly = info.videoOnlyStreams.orEmpty(),
            audio = info.audioStreams.orEmpty(),
            muxed = info.videoStreams.orEmpty(),
            description = info.description?.content().orEmpty(),
            related = info.relatedItems.videos(),
        )
    }

    /**
     * Latest uploads of a channel from its public RSS feed: one small request, no API quota.
     * Falls back to the channel page when YouTube's feed is down.
     */
    fun latestUploads(channel: YtChannel): List<YtVideo> {
        val rss = runCatching { rss(channel) }
        rss.getOrNull()?.let { return it }
        return channel(channel.url).videos.take(15)
    }

    private val rssDate = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
    }

    private fun rss(channel: YtChannel): List<YtVideo> {
        val id = channelIdFromUrl(channel.url) ?: channel.id
        val request = Request.Builder().url("https://www.youtube.com/feeds/videos.xml?channel_id=$id").build()
        val xml = YouTubeHttp.apiClient.newCall(request).execute().use { r ->
            if (!r.isSuccessful) throw HttpStatusException(r.code, request.url.toString(), "")
            r.body?.string().orEmpty()
        }
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(xml.reader())
        val out = mutableListOf<YtVideo>()
        var inEntry = false
        var videoId = ""
        var title = ""
        var published = ""
        var link = ""
        var thumb = ""
        var views = -1L
        var author = channel.name
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "entry" -> {
                        inEntry = true
                        videoId = ""; title = ""; published = ""; link = ""; thumb = ""; views = -1
                    }
                    "yt:videoId" -> if (inEntry) videoId = parser.nextText()
                    "title" -> if (inEntry) title = parser.nextText()
                    "name" -> if (inEntry) author = parser.nextText().ifBlank { author }
                    "published" -> if (inEntry) published = parser.nextText()
                    "link" -> if (inEntry && parser.getAttributeValue(null, "rel") == "alternate") link = parser.getAttributeValue(null, "href").orEmpty()
                    "media:thumbnail" -> if (inEntry) thumb = parser.getAttributeValue(null, "url").orEmpty()
                    "media:statistics" -> if (inEntry) views = parser.getAttributeValue(null, "views")?.toLongOrNull() ?: -1
                }
                XmlPullParser.END_TAG -> if (parser.name == "entry" && inEntry) {
                    inEntry = false
                    if (videoId.isNotEmpty()) {
                        val millis = runCatching { rssDate.get()!!.parse(published)?.time }.getOrNull() ?: 0L
                        out += YtVideo(
                            id = videoId,
                            title = title,
                            channelName = author,
                            channelUrl = channel.url,
                            thumbnail = thumb.ifEmpty { YtVideo.thumbnailFor(videoId) },
                            views = views,
                            uploadedMillis = millis,
                            isShort = link.contains("/shorts/"),
                            channelAvatar = channel.avatar,
                        )
                    }
                }
            }
        }
        return out
    }

    /** Turns extractor exceptions into messages for the screen. */
    private inline fun <T> wrap(block: () -> T): T = try {
        block()
    } catch (e: AgeRestrictedContentException) {
        throw YouTubeException("Dieses Video ist altersbeschränkt und kann ohne Anmeldung nicht abgespielt werden.", e)
    } catch (e: GeographicRestrictionException) {
        throw YouTubeException("Dieses Video ist in deinem Land nicht verfügbar.", e)
    } catch (e: PaidContentException) {
        throw YouTubeException("Dieses Video ist nur für zahlende Mitglieder.", e)
    } catch (e: PrivateContentException) {
        throw YouTubeException("Dieses Video ist privat.", e)
    } catch (e: ContentNotAvailableException) {
        throw YouTubeException(e.message?.takeIf { it.isNotBlank() } ?: "Inhalt nicht verfügbar.", e)
    } catch (e: SignInConfirmNotBotException) {
        throw YouTubeException("YouTube möchte bestätigen, dass du kein Bot bist. Bitte später nochmal versuchen.", e)
    } catch (e: ReCaptchaException) {
        throw YouTubeException("YouTube hat zu viele Anfragen erkannt. Bitte später nochmal versuchen.", e)
    } catch (e: YouTubeException) {
        throw e
    } catch (e: Exception) {
        throw YouTubeException(e.message ?: "YouTube nicht erreichbar", e)
    }
}

class YouTubeException(message: String, cause: Throwable? = null) : Exception(message, cause)
