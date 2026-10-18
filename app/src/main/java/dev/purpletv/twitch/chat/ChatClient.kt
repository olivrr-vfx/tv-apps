package dev.purpletv.twitch.chat

import dev.purpletv.data.Http
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.random.Random

sealed interface ChatPart {
    data class Text(val text: String) : ChatPart
    data class EmotePart(val emote: Emote) : ChatPart
}

data class ChatMessage(
    val id: String,
    val userLogin: String,
    val userName: String,
    val color: Int,
    val parts: List<ChatPart>,
    val isAction: Boolean,
    val deleted: Boolean = false,
)

/** Anonymous, read-only Twitch chat over IRC WebSocket. */
class ChatClient(private val useThirdPartyEmotes: Boolean) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val incoming = ConcurrentLinkedQueue<ChatMessage>()
    private val moderation = ConcurrentLinkedQueue<(List<ChatMessage>) -> List<ChatMessage>>()
    @Volatile private var emotes: Map<String, Emote> = emptyMap()
    private var socket: WebSocket? = null
    private var channel: String? = null
    private var connectJob: Job? = null

    companion object {
        const val MAX_MESSAGES = 150
    }

    init {
        // Batch UI updates so a busy chat doesn't recompose on every single line.
        scope.launch {
            while (isActive) {
                delay(200)
                if (incoming.isEmpty() && moderation.isEmpty()) continue
                var list = _messages.value
                while (true) list = list + (incoming.poll() ?: break)
                while (true) list = (moderation.poll() ?: break)(list)
                _messages.value = list.takeLast(MAX_MESSAGES)
            }
        }
    }

    fun connect(login: String, channelId: String?) {
        disconnect()
        channel = login.lowercase()
        _messages.value = emptyList()
        emotes = emptyMap()
        connectJob = scope.launch {
            if (useThirdPartyEmotes) {
                launch { emotes = withContext(Dispatchers.IO) { EmoteRepository.forChannel(channelId) } }
            }
            open()
        }
    }

    private fun open() {
        val chan = channel ?: return
        val request = Request.Builder().url("wss://irc-ws.chat.twitch.tv:443").build()
        socket = Http.client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send("CAP REQ :twitch.tv/tags twitch.tv/commands")
                webSocket.send("PASS SCHMOOPIIE")
                webSocket.send("NICK justinfan${Random.nextInt(10_000, 99_999)}")
                webSocket.send("JOIN #$chan")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                for (line in text.split("\r\n")) {
                    if (line.isBlank()) continue
                    if (line.startsWith("PING")) {
                        webSocket.send("PONG :tmi.twitch.tv")
                        continue
                    }
                    handleLine(line)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (socket !== webSocket) return
                scope.launch {
                    delay(3_000)
                    if (socket === webSocket && channel == chan) open()
                }
            }
        })
    }

    private fun handleLine(line: String) {
        val irc = IrcLine.parse(line) ?: return
        when (irc.command) {
            "PRIVMSG" -> incoming += toMessage(irc)
            "CLEARMSG" -> irc.tags["target-msg-id"]?.let { id ->
                moderation += { list -> list.map { if (it.id == id) it.copy(deleted = true) else it } }
            }
            "CLEARCHAT" -> {
                val user = irc.trailing
                moderation += { list ->
                    if (user.isNullOrBlank()) emptyList()
                    else list.map { if (it.userLogin == user) it.copy(deleted = true) else it }
                }
            }
        }
    }

    private fun toMessage(irc: IrcLine): ChatMessage {
        var text = irc.trailing.orEmpty()
        val isAction = text.startsWith("\u0001ACTION ") && text.endsWith("\u0001")
        if (isAction) text = text.removePrefix("\u0001ACTION ").removeSuffix("\u0001")
        val login = irc.prefix?.substringBefore("!").orEmpty()
        val name = irc.tags["display-name"]?.ifBlank { null } ?: login
        val color = irc.tags["color"]?.takeIf { it.length == 7 }?.let {
            runCatching { 0xFF000000.toInt() or it.substring(1).toInt(16) }.getOrNull()
        } ?: defaultColor(login)
        return ChatMessage(
            id = irc.tags["id"] ?: "${System.nanoTime()}",
            userLogin = login,
            userName = name,
            color = color,
            parts = ChatParser.parts(text, irc.tags["emotes"].orEmpty(), emotes),
            isAction = isAction,
        )
    }

    private fun defaultColor(login: String): Int {
        val palette = intArrayOf(
            0xFFFF4A4A.toInt(), 0xFF4A7BFF.toInt(), 0xFF2EC27E.toInt(), 0xFFFF9F43.toInt(),
            0xFFB66DFF.toInt(), 0xFF00C2D1.toInt(), 0xFFFF6FB5.toInt(), 0xFFE5D83B.toInt(),
        )
        return palette[(login.hashCode() and 0x7fffffff) % palette.size]
    }

    fun disconnect() {
        connectJob?.cancel()
        val s = socket
        socket = null
        s?.close(1000, null)
        channel = null
    }

    fun release() {
        disconnect()
        scope.cancel()
    }
}

/** Minimal IRCv3 line parser. */
data class IrcLine(val tags: Map<String, String>, val prefix: String?, val command: String, val params: List<String>, val trailing: String?) {
    companion object {
        fun parse(raw: String): IrcLine? {
            var rest = raw
            val tags = mutableMapOf<String, String>()
            if (rest.startsWith("@")) {
                val end = rest.indexOf(' ').takeIf { it > 0 } ?: return null
                rest.substring(1, end).split(';').forEach { kv ->
                    val k = kv.substringBefore('=')
                    val v = kv.substringAfter('=', "")
                    tags[k] = v.replace("\\s", " ").replace("\\:", ";").replace("\\\\", "\\")
                }
                rest = rest.substring(end + 1)
            }
            var prefix: String? = null
            if (rest.startsWith(":")) {
                val end = rest.indexOf(' ').takeIf { it > 0 } ?: return null
                prefix = rest.substring(1, end)
                rest = rest.substring(end + 1)
            }
            val trailingIdx = rest.indexOf(" :")
            val trailing = if (trailingIdx >= 0) rest.substring(trailingIdx + 2) else null
            val head = if (trailingIdx >= 0) rest.substring(0, trailingIdx) else rest
            val pieces = head.split(' ').filter { it.isNotEmpty() }
            if (pieces.isEmpty()) return null
            return IrcLine(tags, prefix, pieces[0], pieces.drop(1), trailing)
        }
    }
}

object ChatParser {
    /**
     * Splits [text] into text and emote parts. [twitchEmotes] is the IRC `emotes` tag
     * (`id:start-end,start-end/id2:...`) whose positions count Unicode code points.
     */
    fun parts(text: String, twitchEmotes: String, thirdParty: Map<String, Emote>): List<ChatPart> {
        val ranges = mutableListOf<Triple<Int, Int, String>>()
        if (twitchEmotes.isNotEmpty()) {
            for (entry in twitchEmotes.split('/')) {
                val id = entry.substringBefore(':')
                for (pos in entry.substringAfter(':', "").split(',')) {
                    val start = pos.substringBefore('-').toIntOrNull() ?: continue
                    val end = pos.substringAfter('-').toIntOrNull() ?: continue
                    ranges += Triple(start, end, id)
                }
            }
            ranges.sortBy { it.first }
        }
        val cps = text.codePoints().toArray()
        fun slice(from: Int, toExclusive: Int): String {
            val f = from.coerceIn(0, cps.size)
            val t = toExclusive.coerceIn(f, cps.size)
            return String(cps, f, t - f)
        }

        val out = mutableListOf<ChatPart>()
        var cursor = 0
        for ((start, end, id) in ranges) {
            if (start < cursor || start >= cps.size) continue
            if (start > cursor) splitWords(slice(cursor, start), thirdParty, out)
            val name = slice(start, end + 1)
            out += ChatPart.EmotePart(Emote(name, EmoteRepository.twitchEmoteUrl(id)))
            cursor = end + 1
        }
        if (cursor < cps.size) splitWords(slice(cursor, cps.size), thirdParty, out)
        return out
    }

    private fun splitWords(text: String, emotes: Map<String, Emote>, out: MutableList<ChatPart>) {
        if (emotes.isEmpty()) {
            appendText(out, text)
            return
        }
        val words = text.split(' ')
        words.forEachIndexed { i, word ->
            val emote = emotes[word]
            if (emote != null) out += ChatPart.EmotePart(emote) else appendText(out, word)
            if (i < words.lastIndex) appendText(out, " ")
        }
    }

    private fun appendText(out: MutableList<ChatPart>, text: String) {
        if (text.isEmpty()) return
        val last = out.lastOrNull()
        if (last is ChatPart.Text) out[out.lastIndex] = ChatPart.Text(last.text + text) else out += ChatPart.Text(text)
    }
}
