package dev.purpletv.player

import java.net.URI

/**
 * Pure-Kotlin HLS playlist rewriting for Twitch streams. No Android dependencies so it can be
 * unit tested on the JVM.
 *
 * Twitch stitches ads server-side into the media playlist. Real stream segments carry the
 * EXTINF title "live", ad segments carry something else (e.g. "Amazon|...") and are surrounded
 * by `#EXT-X-DATERANGE` tags with class `twitch-stitched-ad`.
 */
object PlaylistProcessor {

    private val AD_MARKERS = listOf("stitched-ad", "twitch-stitched-ad", "X-TV-TWITCH-AD", "twitch-ad-quartile")

    private val HEADER_PREFIXES = listOf(
        "#EXTM3U", "#EXT-X-VERSION", "#EXT-X-TARGETDURATION", "#EXT-X-MEDIA-SEQUENCE",
        "#EXT-X-DISCONTINUITY-SEQUENCE", "#EXT-X-PLAYLIST-TYPE", "#EXT-X-INDEPENDENT-SEGMENTS",
        "#EXT-X-START", "#EXT-X-SERVER-CONTROL", "#EXT-X-TWITCH-ELAPSED-SECS",
        "#EXT-X-TWITCH-TOTAL-SECS", "#EXT-X-TWITCH-LIVE-SEQUENCE", "#EXT-X-TWITCH-INFO",
    )

    private const val PREFETCH = "#EXT-X-TWITCH-PREFETCH:"

    data class Result(
        /** Playlist to hand to the player. */
        val playlist: String,
        /** The original playlist contained ad segments. */
        val hadAds: Boolean,
        /** Every segment was an ad, so nothing could be stripped. [playlist] is the original. */
        val allAds: Boolean,
    )

    data class Variant(
        val group: String,
        val name: String,
        val width: Int,
        val height: Int,
        val bandwidth: Long,
        val frameRate: Double,
        val uri: String,
    ) {
        val isAudioOnly get() = group == "audio_only" || height == 0
    }

    private class Segment(val tags: List<String>, val uri: String) {
        val duration: String? = tags.firstOrNull { it.startsWith("#EXTINF:") }
            ?.removePrefix("#EXTINF:")?.substringBefore(",")
        val title: String = tags.firstOrNull { it.startsWith("#EXTINF:") }
            ?.substringAfter(",", "")?.trim().orEmpty()
        val isLive get() = title.startsWith("live")
        val discontinuities get() = tags.count { it == "#EXT-X-DISCONTINUITY" }
    }

    private class Parsed(
        val header: List<String>,
        val segments: List<Segment>,
        val prefetch: List<String>,
        val trailer: List<String>,
    )

    fun isMediaPlaylist(text: String) = text.contains("#EXTINF")

    fun containsAds(text: String): Boolean {
        if (!isMediaPlaylist(text)) return false
        val parsed = parse(text, null)
        return adFlags(parsed, text).any { it }
    }

    /**
     * @param lowLatency convert Twitch prefetch hints into regular segments so the player can
     *   sit closer to the live edge.
     * @param baseUrl used to absolutize relative segment URIs (needed when a backup playlist
     *   is served under the URL of the main one).
     */
    fun process(text: String, lowLatency: Boolean, baseUrl: String? = null): Result {
        if (!isMediaPlaylist(text)) return Result(text, hadAds = false, allAds = false)
        val parsed = parse(text, baseUrl)
        val flags = adFlags(parsed, text)
        val segments = parsed.segments

        if (flags.none { it }) {
            val prefetch = if (lowLatency) parsed.prefetch else emptyList()
            return Result(render(parsed.header, segments, prefetch, parsed.trailer), hadAds = false, allAds = false)
        }
        if (flags.all { it }) {
            return Result(text, hadAds = true, allAds = true)
        }

        // Keep the newest contiguous run of live segments. Dropping only from the front (and
        // bumping the sequence numbers accordingly) keeps segment numbering consistent across
        // playlist reloads.
        val end = flags.lastIndexOf(false)
        var start = end
        while (start > 0 && !flags[start - 1]) start--
        val dropped = segments.subList(0, start)
        val kept = segments.subList(start, end + 1)
        val header = shiftHeader(parsed.header, start, dropped.sumOf { it.discontinuities })
        return Result(render(header, kept, emptyList(), parsed.trailer), hadAds = true, allAds = false)
    }

    private fun adFlags(parsed: Parsed, text: String): List<Boolean> {
        val usesLiveTitles = parsed.segments.any { it.isLive }
        return if (usesLiveTitles) {
            parsed.segments.map { !it.isLive }
        } else {
            val markers = AD_MARKERS.any { text.contains(it) }
            parsed.segments.map { markers }
        }
    }

    private fun parse(text: String, baseUrl: String?): Parsed {
        val header = mutableListOf<String>()
        val segments = mutableListOf<Segment>()
        val prefetch = mutableListOf<String>()
        val pending = mutableListOf<String>()
        var inHeader = true
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (inHeader && HEADER_PREFIXES.any { line.startsWith(it) }) {
                header += line
                continue
            }
            inHeader = false
            when {
                line.startsWith(PREFETCH) -> prefetch += absolutize(line.removePrefix(PREFETCH), baseUrl)
                line.startsWith("#") -> pending += line
                else -> {
                    segments += Segment(pending.toList(), absolutize(line, baseUrl))
                    pending.clear()
                }
            }
        }
        return Parsed(header, segments, prefetch, pending.toList())
    }

    private fun shiftHeader(header: List<String>, droppedSegments: Int, droppedDiscontinuities: Int): List<String> {
        val out = header.toMutableList()
        val seqIdx = out.indexOfFirst { it.startsWith("#EXT-X-MEDIA-SEQUENCE:") }
        if (seqIdx >= 0) {
            val seq = out[seqIdx].substringAfter(":").trim().toLongOrNull() ?: 0L
            out[seqIdx] = "#EXT-X-MEDIA-SEQUENCE:${seq + droppedSegments}"
        } else if (droppedSegments > 0) {
            out += "#EXT-X-MEDIA-SEQUENCE:$droppedSegments"
        }
        if (droppedDiscontinuities > 0) {
            val dIdx = out.indexOfFirst { it.startsWith("#EXT-X-DISCONTINUITY-SEQUENCE:") }
            if (dIdx >= 0) {
                val d = out[dIdx].substringAfter(":").trim().toLongOrNull() ?: 0L
                out[dIdx] = "#EXT-X-DISCONTINUITY-SEQUENCE:${d + droppedDiscontinuities}"
            } else {
                out += "#EXT-X-DISCONTINUITY-SEQUENCE:$droppedDiscontinuities"
            }
        }
        return out
    }

    private fun render(
        header: List<String>,
        segments: List<Segment>,
        prefetch: List<String>,
        trailer: List<String>,
    ): String = buildString {
        header.forEach { appendLine(it) }
        for (s in segments) {
            s.tags.filterNot { it.startsWith("#EXT-X-DATERANGE") && AD_MARKERS.any { m -> it.contains(m) } }
                .forEach { appendLine(it) }
            appendLine(s.uri)
        }
        if (prefetch.isNotEmpty()) {
            val duration = segments.lastOrNull()?.duration ?: "2.000"
            for (uri in prefetch) {
                appendLine("#EXTINF:$duration,live")
                appendLine(uri)
            }
        }
        trailer.forEach { appendLine(it) }
    }

    private fun absolutize(uri: String, baseUrl: String?): String {
        if (baseUrl == null || uri.startsWith("http://") || uri.startsWith("https://")) return uri
        return runCatching { URI(baseUrl).resolve(uri).toString() }.getOrDefault(uri)
    }

    // ---------------------------------------------------------------------------------------
    // Master playlist

    fun parseMaster(text: String): List<Variant> {
        val names = mutableMapOf<String, String>()
        val variants = mutableListOf<Variant>()
        var pendingInf: String? = null
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            when {
                line.startsWith("#EXT-X-MEDIA:") -> {
                    val attrs = attributes(line)
                    val group = attrs["GROUP-ID"]
                    val name = attrs["NAME"]
                    if (group != null && name != null) names[group] = name
                }
                line.startsWith("#EXT-X-STREAM-INF:") -> pendingInf = line
                line.isNotEmpty() && !line.startsWith("#") && pendingInf != null -> {
                    val attrs = attributes(pendingInf)
                    val group = attrs["VIDEO"] ?: ""
                    val res = attrs["RESOLUTION"]?.split("x")
                    variants += Variant(
                        group = group,
                        name = names[group] ?: group,
                        width = res?.getOrNull(0)?.toIntOrNull() ?: 0,
                        height = res?.getOrNull(1)?.toIntOrNull() ?: 0,
                        bandwidth = attrs["BANDWIDTH"]?.toLongOrNull() ?: 0,
                        frameRate = attrs["FRAME-RATE"]?.toDoubleOrNull() ?: 0.0,
                        uri = line,
                    )
                    pendingInf = null
                }
            }
        }
        return variants
    }

    /** Pick the variant in [candidates] that best matches [wanted] (same group, else closest height). */
    fun pickMatching(candidates: List<Variant>, wanted: Variant?): Variant? {
        val video = candidates.filterNot { it.isAudioOnly }
        if (video.isEmpty()) return null
        if (wanted == null) return video.maxByOrNull { it.bandwidth }
        video.firstOrNull { it.group == wanted.group }?.let { return it }
        video.firstOrNull { it.height == wanted.height && it.frameRate == wanted.frameRate }?.let { return it }
        return video.filter { it.height <= wanted.height }.maxByOrNull { it.bandwidth }
            ?: video.minByOrNull { it.height }
    }

    private fun attributes(line: String): Map<String, String> {
        val body = line.substringAfter(":")
        val out = mutableMapOf<String, String>()
        var i = 0
        while (i < body.length) {
            val eq = body.indexOf('=', i)
            if (eq < 0) break
            val key = body.substring(i, eq).trim()
            var j = eq + 1
            val value: String
            if (j < body.length && body[j] == '"') {
                val close = body.indexOf('"', j + 1).let { if (it < 0) body.length else it }
                value = body.substring(j + 1, close)
                j = close + 1
            } else {
                val comma = body.indexOf(',', j).let { if (it < 0) body.length else it }
                value = body.substring(j, comma)
                j = comma
            }
            out[key] = value
            i = if (j < body.length && body[j] == ',') j + 1 else j + 1
        }
        return out
    }
}
