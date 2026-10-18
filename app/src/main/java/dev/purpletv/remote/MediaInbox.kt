package dev.purpletv.remote

import android.content.Context
import java.io.File
import java.util.UUID

/** Photos and videos sent from a phone or computer to show on the TV. Kept in the cache. */
object MediaInbox {

    data class Item(val id: String, val file: File, val name: String, val isVideo: Boolean, val addedAt: Long)

    private const val MAX_ITEMS = 40
    private val items = mutableListOf<Item>()

    private val videoExtensions = setOf("mp4", "m4v", "mov", "webm", "3gp", "mkv")
    private val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "avif")

    fun isSupported(name: String) = name.substringAfterLast('.', "").lowercase().let { it in videoExtensions || it in imageExtensions }

    /** Moves an uploaded temp file into the inbox. */
    @Synchronized
    fun add(context: Context, temp: File, originalName: String): Item {
        val dir = File(context.cacheDir, "inbox").apply { mkdirs() }
        val ext = originalName.substringAfterLast('.', "").lowercase().ifEmpty { "jpg" }
        val target = File(dir, "${UUID.randomUUID()}.$ext")
        temp.copyTo(target, overwrite = true)
        val item = Item(target.nameWithoutExtension, target, originalName, ext in videoExtensions, System.currentTimeMillis())
        items += item
        while (items.size > MAX_ITEMS) items.removeAt(0).file.delete()
        return item
    }

    @Synchronized
    fun all(): List<Item> = items.toList()

    @Synchronized
    fun clear() {
        items.forEach { it.file.delete() }
        items.clear()
    }
}
