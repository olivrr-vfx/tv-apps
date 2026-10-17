package dev.purpletv

import android.app.Application
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import dev.purpletv.data.Http
import dev.purpletv.data.Settings
import dev.purpletv.twitch.Helix
import dev.purpletv.twitch.TwitchAuth
import dev.purpletv.twitch.TwitchGql

class PurpleApp : Application(), ImageLoaderFactory {
    lateinit var settings: Settings
        private set
    lateinit var gql: TwitchGql
        private set
    lateinit var auth: TwitchAuth
        private set
    lateinit var helix: Helix
        private set

    override fun onCreate() {
        super.onCreate()
        settings = Settings(this)
        gql = TwitchGql(settings.deviceId)
        auth = TwitchAuth(settings)
        helix = Helix(settings, auth)
    }

    // Animated emotes (7TV/BTTV webp + gif) need the platform ImageDecoder on Android 9+.
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient(Http.client)
        .components {
            if (Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory()) else add(GifDecoder.Factory())
        }
        .crossfade(150)
        .build()
}
