package dev.purpletv.twitch.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatParserTest {

    @Test
    fun parsesPrivmsgWithTags() {
        val line = "@badge-info=;color=#1E90FF;display-name=Some\\sUser;emotes=25:0-4;id=abc :someuser!someuser@someuser.tmi.twitch.tv PRIVMSG #chan :Kappa hello"
        val irc = IrcLine.parse(line)!!
        assertEquals("PRIVMSG", irc.command)
        assertEquals("Some User", irc.tags["display-name"])
        assertEquals("Kappa hello", irc.trailing)
        assertEquals(listOf("#chan"), irc.params)
    }

    @Test
    fun twitchEmotePositionsUseCodePoints() {
        // The emoji is two UTF-16 chars but one code point.
        val parts = ChatParser.parts("😀 Kappa x", "25:2-6", emptyMap())
        assertEquals(3, parts.size)
        assertEquals(ChatPart.Text("😀 "), parts[0])
        assertEquals("Kappa", (parts[1] as ChatPart.EmotePart).emote.name)
        assertEquals(ChatPart.Text(" x"), parts[2])
    }

    @Test
    fun thirdPartyEmotesAreMatchedByWord() {
        val emotes = mapOf("catJAM" to Emote("catJAM", "https://cdn.7tv.app/emote/1/2x.webp", 1f))
        val parts = ChatParser.parts("vibing catJAM catJAM!", "", emotes)
        assertEquals(ChatPart.Text("vibing "), parts[0])
        assertTrue(parts[1] is ChatPart.EmotePart)
        assertEquals(ChatPart.Text(" catJAM!"), parts[2])
    }
}
