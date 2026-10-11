package app.ttlauncher

import android.app.Application
import app.ttlauncher.apps.AppRepo
import app.ttlauncher.apps.IconRepo
import app.ttlauncher.apps.NotificationWatcher
import app.ttlauncher.data.Store
import app.ttlauncher.feed.FeedRepo
import app.ttlauncher.weather.WeatherRepo
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

class LauncherApp : Application(), SingletonImageLoader.Factory {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // One client for feeds, their images and the weather. Its disk cache turns a repeat fetch into a conditional
    // request, so an unchanged feed costs a few hundred bytes.
    private val http by lazy {
        OkHttpClient.Builder()
            .cache(Cache(File(cacheDir, "http"), 20L * 1024 * 1024))
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    lateinit var store: Store
        private set
    lateinit var icons: IconRepo
        private set
    lateinit var apps: AppRepo
        private set
    val feed by lazy { FeedRepo(this, http, scope) }
    val weather by lazy { WeatherRepo(this, http, scope) }

    override fun onCreate() {
        super.onCreate()
        store = Store(this, scope)
        icons = IconRepo(this)
        apps = AppRepo(this, scope, icons::invalidate)
        // The notification listener is only kept while badges are switched on.
        scope.launch {
            store.ready.first { it }
            store.state.map { it.badges }.distinctUntilChanged().collect { NotificationWatcher.setWanted(this@LauncherApp, it) }
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context).components { add(OkHttpNetworkFetcherFactory(callFactory = { http })) }.build()
}
