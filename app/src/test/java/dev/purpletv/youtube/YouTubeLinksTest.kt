package dev.purpletv.youtube

import dev.purpletv.youtube.api.channelIdFromUrl
import dev.purpletv.youtube.api.parseVideoId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YouTubeLinksTest {
    @Test
    fun videoIds() {
        assertEquals("dQw4w9WgXcQ", parseVideoId("dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", parseVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=42s"))
        assertEquals("dQw4w9WgXcQ", parseVideoId("https://youtu.be/dQw4w9WgXcQ?si=abc"))
        assertEquals("dQw4w9WgXcQ", parseVideoId("https://www.youtube.com/shorts/dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", parseVideoId("https://www.youtube.com/live/dQw4w9WgXcQ"))
        assertNull(parseVideoId("https://www.youtube.com/@LinusTechTips"))
        assertNull(parseVideoId("papaplatte"))
    }

    @Test
    fun channelIds() {
        assertEquals("UCXuqSBlHAE6Xw-yeJA0Tunw", channelIdFromUrl("https://www.youtube.com/channel/UCXuqSBlHAE6Xw-yeJA0Tunw"))
        assertNull(channelIdFromUrl("https://www.youtube.com/@LinusTechTips"))
    }
}
