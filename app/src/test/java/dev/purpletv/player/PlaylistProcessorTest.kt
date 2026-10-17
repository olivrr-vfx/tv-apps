package dev.purpletv.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistProcessorTest {

    private val header = """
        #EXTM3U
        #EXT-X-VERSION:3
        #EXT-X-TARGETDURATION:6
        #EXT-X-MEDIA-SEQUENCE:100
        #EXT-X-TWITCH-ELAPSED-SECS:200.000
    """.trimIndent()

    private fun live(n: Int) = "#EXT-X-PROGRAM-DATE-TIME:2026-10-17T12:00:0${n % 10}.000Z\n#EXTINF:2.000,live\nhttps://edge.ttvnw.net/v1/segment/live$n.ts"
    private fun ad(n: Int, discontinuity: Boolean = false) = buildString {
        if (discontinuity) appendLine("#EXT-X-DISCONTINUITY")
        appendLine("""#EXT-X-DATERANGE:ID="stitched-ad-$n",CLASS="twitch-stitched-ad",START-DATE="2026-10-17T12:00:00.000Z"""")
        appendLine("#EXTINF:2.000,Amazon|123456")
        append("https://edge.ttvnw.net/v1/segment/ad$n.ts")
    }

    private fun playlist(vararg parts: String, prefetch: List<String> = emptyList()) =
        (listOf(header) + parts + prefetch.map { "#EXT-X-TWITCH-PREFETCH:$it" }).joinToString("\n") + "\n"

    @Test
    fun cleanPlaylistPassesThrough() {
        val text = playlist(live(1), live(2), live(3))
        val r = PlaylistProcessor.process(text, lowLatency = false)
        assertFalse(r.hadAds)
        assertTrue(r.playlist.contains("#EXT-X-MEDIA-SEQUENCE:100"))
        assertEquals(3, r.playlist.lines().count { it.endsWith(".ts") })
    }

    @Test
    fun lowLatencyConvertsPrefetch() {
        val text = playlist(live(1), live(2), prefetch = listOf("https://edge.ttvnw.net/v1/segment/p1.ts", "https://edge.ttvnw.net/v1/segment/p2.ts"))
        val r = PlaylistProcessor.process(text, lowLatency = true)
        assertFalse(r.playlist.contains("PREFETCH"))
        assertTrue(r.playlist.contains("#EXTINF:2.000,live\nhttps://edge.ttvnw.net/v1/segment/p2.ts"))
        assertEquals(4, r.playlist.lines().count { it.endsWith(".ts") })

        val normal = PlaylistProcessor.process(text, lowLatency = false)
        assertEquals(2, normal.playlist.lines().count { it.endsWith(".ts") })
    }

    @Test
    fun adsAtTailAreDroppedWithoutSequenceChange() {
        val text = playlist(live(1), live(2), ad(3, discontinuity = true), ad(4), prefetch = listOf("https://edge.ttvnw.net/v1/segment/adp.ts"))
        val r = PlaylistProcessor.process(text, lowLatency = true)
        assertTrue(r.hadAds)
        assertFalse(r.allAds)
        assertTrue(r.playlist.contains("#EXT-X-MEDIA-SEQUENCE:100"))
        assertFalse(r.playlist.contains("ad3"))
        assertFalse(r.playlist.contains("adp"))
        assertFalse(r.playlist.contains("stitched-ad"))
        assertEquals(2, r.playlist.lines().count { it.endsWith(".ts") })
    }

    @Test
    fun adsAtHeadShiftSequenceNumbers() {
        val text = playlist(ad(1), ad(2), "#EXT-X-DISCONTINUITY\n" + live(3), live(4))
        val r = PlaylistProcessor.process(text, lowLatency = false)
        assertTrue(r.hadAds)
        assertTrue(r.playlist.contains("#EXT-X-MEDIA-SEQUENCE:102"))
        assertTrue(r.playlist.lines().first { it.endsWith(".ts") }.endsWith("live3.ts"))
    }

    @Test
    fun droppedDiscontinuitiesAreCounted() {
        val text = playlist(live(0), ad(1, discontinuity = true), ad(2), "#EXT-X-DISCONTINUITY\n" + live(3))
        val r = PlaylistProcessor.process(text, lowLatency = false)
        assertTrue(r.playlist.contains("#EXT-X-MEDIA-SEQUENCE:103"))
        assertTrue(r.playlist.contains("#EXT-X-DISCONTINUITY-SEQUENCE:1"))
    }

    @Test
    fun allAdsReturnsOriginal() {
        val text = playlist(ad(1), ad(2), ad(3))
        val r = PlaylistProcessor.process(text, lowLatency = false)
        assertTrue(r.allAds)
        assertEquals(text, r.playlist)
    }

    @Test
    fun relativeUrisAreAbsolutized() {
        val text = "$header\n#EXTINF:2.000,live\nseg1.ts\n"
        val r = PlaylistProcessor.process(text, false, baseUrl = "https://weaver.ttvnw.net/v1/playlist/abc.m3u8")
        assertTrue(r.playlist.contains("https://weaver.ttvnw.net/v1/playlist/seg1.ts"))
    }

    @Test
    fun parsesMasterPlaylist() {
        val master = """
            #EXTM3U
            #EXT-X-TWITCH-INFO:NODE="video-edge",SERVER-TIME="1.0"
            #EXT-X-MEDIA:TYPE=VIDEO,GROUP-ID="chunked",NAME="1080p60 (source)",AUTOSELECT=YES,DEFAULT=YES
            #EXT-X-STREAM-INF:BANDWIDTH=8000000,RESOLUTION=1920x1080,CODECS="avc1.64002A,mp4a.40.2",VIDEO="chunked",FRAME-RATE=60.000
            https://weaver.ttvnw.net/v1/playlist/source.m3u8
            #EXT-X-MEDIA:TYPE=VIDEO,GROUP-ID="720p60",NAME="720p60",AUTOSELECT=YES,DEFAULT=YES
            #EXT-X-STREAM-INF:BANDWIDTH=3400000,RESOLUTION=1280x720,CODECS="avc1.4D401F,mp4a.40.2",VIDEO="720p60",FRAME-RATE=60.000
            https://weaver.ttvnw.net/v1/playlist/720.m3u8
            #EXT-X-MEDIA:TYPE=VIDEO,GROUP-ID="audio_only",NAME="audio_only",AUTOSELECT=NO,DEFAULT=NO
            #EXT-X-STREAM-INF:BANDWIDTH=160000,CODECS="mp4a.40.2",VIDEO="audio_only"
            https://weaver.ttvnw.net/v1/playlist/audio.m3u8
        """.trimIndent()
        val v = PlaylistProcessor.parseMaster(master)
        assertEquals(3, v.size)
        assertEquals("1080p60 (source)", v[0].name)
        assertEquals(1080, v[0].height)
        assertEquals(60.0, v[1].frameRate, 0.01)
        assertTrue(v[2].isAudioOnly)

        val backup = listOf(v[1].copy(group = "720p30", frameRate = 30.0, uri = "b720"), v[2])
        assertEquals("b720", PlaylistProcessor.pickMatching(backup, v[0])?.uri)
        assertEquals("b720", PlaylistProcessor.pickMatching(backup, v[1])?.uri)
    }
}
