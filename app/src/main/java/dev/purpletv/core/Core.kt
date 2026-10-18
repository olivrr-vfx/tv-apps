package dev.purpletv.core

/** The apps that live inside PurpleTV. New services get an entry here. */
enum class AppId(val key: String, val label: String, val launcherAlias: String) {
    TWITCH("twitch", "Twitch", "dev.purpletv.launcher.Twitch"),
    YOUTUBE("youtube", "YouTube", "dev.purpletv.launcher.YouTube");

    companion object {
        fun fromKey(key: String?): AppId? = entries.firstOrNull { it.key == key?.lowercase() }
    }
}

/** State of something loaded from the network. */
sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data object NeedLogin : Load<Nothing>
    data class Ok<T>(val data: T) : Load<T>
    data class Error(val message: String) : Load<Nothing>
}

fun <T> Load<List<T>>.items(): List<T> = (this as? Load.Ok)?.data.orEmpty()
