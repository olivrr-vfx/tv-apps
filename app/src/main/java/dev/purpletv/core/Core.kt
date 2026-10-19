package dev.purpletv.core

/** The name of the hub app. */
const val HUB_NAME = "Couch"

/**
 * The apps that live inside Couch. New services get an entry here.
 * [label] is the app's name in Couch, [service] the platform its content comes from.
 */
enum class AppId(val key: String, val label: String, val service: String, val launcherAlias: String) {
    TWITCH("twitch", "PurpleTV", "Twitch", "dev.purpletv.launcher.Twitch"),
    YOUTUBE("youtube", "YouTube", "YouTube", "dev.purpletv.launcher.YouTube");

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
