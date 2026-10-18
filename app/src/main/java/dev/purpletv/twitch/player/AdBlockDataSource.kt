package dev.purpletv.twitch.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import kotlin.math.min

/** Serves rewritten playlists from [AdBlockEngine], everything else (segments) goes to [upstream]. */
class AdBlockDataSource(
    private val upstream: DataSource,
    private val engine: AdBlockEngine,
) : DataSource {

    private var data: ByteArray? = null
    private var position = 0
    private var bytesRemaining = 0L
    private var uri: Uri? = null
    private var upstreamOpen = false

    class Factory(private val upstream: DataSource.Factory, private val engine: AdBlockEngine) : DataSource.Factory {
        override fun createDataSource(): DataSource = AdBlockDataSource(upstream.createDataSource(), engine)
    }

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        val bytes = engine.intercept(dataSpec.uri)
        if (bytes == null) {
            upstreamOpen = true
            return upstream.open(dataSpec)
        }
        data = bytes
        position = min(dataSpec.position, bytes.size.toLong()).toInt()
        val available = (bytes.size - position).toLong()
        bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) min(dataSpec.length, available) else available
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (upstreamOpen) return upstream.read(buffer, offset, length)
        if (length == 0) return 0
        val bytes = data ?: return C.RESULT_END_OF_INPUT
        if (bytesRemaining <= 0) return C.RESULT_END_OF_INPUT
        val count = min(length.toLong(), bytesRemaining).toInt()
        System.arraycopy(bytes, position, buffer, offset, count)
        position += count
        bytesRemaining -= count
        return count
    }

    override fun getUri(): Uri? = uri

    override fun getResponseHeaders(): Map<String, List<String>> =
        if (upstreamOpen) upstream.responseHeaders else emptyMap()

    override fun close() {
        if (upstreamOpen) {
            upstreamOpen = false
            upstream.close()
        }
        data = null
        uri = null
    }
}
