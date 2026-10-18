package dev.purpletv.remote

import java.util.UUID

/**
 * Signaling for screen and camera sharing (WebRTC). A browser posts its offer, the TV opens a
 * receiver (a WebView on [RemoteServer]'s own page) that answers. No servers outside the home
 * network are involved: the browser and the TV talk directly.
 */
object ScreenShare {

    class Session(
        val id: String,
        val offer: String,
        /** "screen" or "camera". */
        val kind: String,
        /** Who is sharing, e.g. "MacBook". */
        val from: String,
    ) {
        @Volatile var answer: String? = null
        @Volatile var ended = false
    }

    @Volatile var current: Session? = null
        private set

    fun start(offer: String, kind: String, from: String): Session {
        current?.ended = true
        return Session(UUID.randomUUID().toString().replace("-", "").take(16), offer, kind, from).also { current = it }
    }

    fun get(id: String?): Session? = current?.takeIf { it.id == id }

    /**
     * The receiver's answer. Browsers hide local addresses behind random ".local" names; the
     * TV knows its own address, so it puts that in, and the sender can always reach it.
     */
    fun answer(id: String, sdp: String, tvAddress: String?) {
        val session = get(id) ?: return
        session.answer = if (tvAddress == null) sdp else sdp.replace(Regex("[0-9a-fA-F-]{36}\\.local"), tvAddress)
    }

    fun end(id: String) {
        get(id)?.ended = true
    }
}
