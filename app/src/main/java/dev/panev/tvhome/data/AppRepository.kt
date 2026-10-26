package dev.panev.tvhome.data

import android.app.SearchManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import android.util.LruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

data class AppEntry(
    val pkg: String,
    val component: ComponentName,
    val label: String,
    val searchable: Boolean,
)

/** Artwork for a tile: the app's own TV banner when it has one, otherwise its icon on a matching colour. */
class AppArt(val banner: ImageBitmap?, val icon: ImageBitmap?, val background: Color)

class AppRepository(private val context: Context) {
    private val pm = context.packageManager
    /** Sized by bytes, not entries: enough for the dock and a screenful of Apps. */
    private val artCache = object : LruCache<String, AppArt>(6 * 1024 * 1024) {
        override fun sizeOf(key: String, value: AppArt) =
            (value.banner?.let { it.width * it.height * 4 } ?: 0) + (value.icon?.let { it.width * it.height * 4 } ?: 0)
    }

    var apps by mutableStateOf<List<AppEntry>>(emptyList())
        private set

    suspend fun refresh() {
        val list = withContext(Dispatchers.IO) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
            pm.queryIntentActivities(intent, 0)
                .asSequence()
                .map { it.activityInfo }
                .filter { it.packageName != context.packageName }
                .distinctBy { it.packageName }
                .map { info ->
                    AppEntry(
                        pkg = info.packageName,
                        component = ComponentName(info.packageName, info.name),
                        label = info.loadLabel(pm).toString().trim(),
                        searchable = pm.queryIntentActivities(
                            Intent(Intent.ACTION_SEARCH).setPackage(info.packageName), 0
                        ).isNotEmpty(),
                    )
                }
                .sortedBy { it.label.lowercase() }
                .toList()
        }
        artCache.evictAll()
        apps = list
    }

    fun find(pkg: String?): AppEntry? = apps.firstOrNull { it.pkg == pkg }

    fun clearArt() = artCache.evictAll()

    fun cachedArt(pkg: String): AppArt? = artCache.get(pkg)

    suspend fun art(app: AppEntry): AppArt {
        artCache.get(app.pkg)?.let { return it }
        return withContext(Dispatchers.IO) {
            val banner = runCatching { pm.getActivityBanner(app.component) }.getOrNull()
                ?: runCatching { pm.getApplicationBanner(app.pkg) }.getOrNull()
            val art = if (banner != null) {
                // The widest tile (Apps grid, focused) is about 370 px.
                AppArt(banner.render(maxWidth = 384).asImageBitmap(), null, Color.Black)
            } else {
                val icon = runCatching { pm.getActivityIcon(app.component) }.getOrNull()
                    ?: pm.defaultActivityIcon
                val bmp = icon.render(maxWidth = 192)
                AppArt(null, bmp.asImageBitmap(), backdropColorFor(bmp))
            }
            artCache.put(app.pkg, art)
            art
        }
    }

    fun launch(app: AppEntry): Boolean {
        val intent = pm.getLeanbackLaunchIntentForPackage(app.pkg)
            ?: Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER).setComponent(app.component)
        return start(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
    }

    /** Sends a search straight into an app. Falls back to just opening the app. */
    fun launchSearch(app: AppEntry, query: String): Boolean {
        if (app.searchable) {
            val intent = Intent(Intent.ACTION_SEARCH)
                .setPackage(app.pkg)
                .putExtra(SearchManager.QUERY, query)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (start(intent)) return true
        }
        return launch(app)
    }

    fun openSystemSettings(): Boolean =
        start(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    fun openAppInfo(pkg: String): Boolean =
        start(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )

    fun openNotificationAccess(): Boolean =
        start(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    private fun start(intent: Intent): Boolean = runCatching {
        context.startActivity(intent)
        true
    }.getOrDefault(false)

    private fun Drawable.render(maxWidth: Int): Bitmap {
        val w = max(intrinsicWidth, 1)
        val h = max(intrinsicHeight, 1)
        val scale = if (w > maxWidth) maxWidth.toFloat() / w else 1f
        return toBitmap((w * scale).roundToInt().coerceAtLeast(1), (h * scale).roundToInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    }

    /** Average of the icon's opaque pixels, deepened so a light icon still reads on it. */
    private fun backdropColorFor(bmp: Bitmap): Color {
        val small = Bitmap.createScaledBitmap(bmp, 24, 24, true)
        var r = 0L; var g = 0L; var b = 0L; var n = 0L
        for (y in 0 until small.height) for (x in 0 until small.width) {
            val c = small.getPixel(x, y)
            if ((c ushr 24) < 200) continue
            r += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF; n++
        }
        if (n == 0L) return Color(0xFF2A2D34)
        val k = 0.55f / 255f
        return Color(r / n * k, g / n * k, b / n * k)
    }
}
