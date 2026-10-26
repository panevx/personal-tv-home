package dev.panev.tvhome.ui.glass

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import dev.panev.tvhome.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

data class Wallpaper(val id: String, val name: String, @DrawableRes val res: Int)

val Wallpapers = listOf(
    Wallpaper("fjord", "Fjord", R.drawable.wp_fjord),
    Wallpaper("valley", "Valley", R.drawable.wp_valley),
    Wallpaper("highlands", "Highlands", R.drawable.wp_highlands),
    Wallpaper("falls", "Falls", R.drawable.wp_falls),
    Wallpaper("coast", "Coast", R.drawable.wp_coast),
    Wallpaper("golden", "Golden Hour", R.drawable.wp_golden),
    Wallpaper("summit", "Summit", R.drawable.wp_summit),
    Wallpaper("canyon", "Canyon", R.drawable.wp_canyon),
)

/**
 * What sits behind the UI: the sharp picture filling the screen, and a heavily blurred copy of the
 * same frame that glass surfaces sample from. Both are cropped to the screen's exact aspect.
 */
class Backdrop(val sharp: ImageBitmap, val blurred: ImageBitmap)

/** Root size in px, so glass can map its on-screen position onto the blurred backdrop. */
val LocalRootSize = staticCompositionLocalOf { IntSize(1920, 1080) }

@Composable
fun rememberWallpaperBackdrop(wallpaper: Wallpaper, size: IntSize): Backdrop? {
    val context = LocalContext.current
    var backdrop by remember { mutableStateOf<Backdrop?>(null) }
    LaunchedEffect(wallpaper.id, size) {
        if (size.width <= 0 || size.height <= 0) return@LaunchedEffect
        backdrop = withContext(Dispatchers.Default) { loadWallpaper(context, wallpaper.res, size) }
    }
    return backdrop
}

/** Album art turned into a full-screen, softly blurred field. */
@Composable
fun rememberArtBackdrop(art: Bitmap?, size: IntSize): Backdrop? {
    var backdrop by remember { mutableStateOf<Backdrop?>(null) }
    LaunchedEffect(art, size) {
        if (art == null || size.width <= 0) {
            backdrop = null
            return@LaunchedEffect
        }
        backdrop = withContext(Dispatchers.Default) {
            val framed = centerCrop(art, 320, (320f * size.height / size.width).roundToInt())
            val soft = Blur.blur(framed, targetWidth = 80, radius = 4)
            Backdrop(soft.asImageBitmap(), Blur.blur(soft, targetWidth = 48, radius = 3).asImageBitmap())
        }
    }
    return backdrop
}

private val thumbCache = HashMap<Int, ImageBitmap>()

@Composable
fun rememberThumbnail(@DrawableRes res: Int): ImageBitmap? {
    val context = LocalContext.current
    var thumb by remember(res) { mutableStateOf(thumbCache[res]) }
    LaunchedEffect(res) {
        if (thumb != null) return@LaunchedEffect
        thumb = withContext(Dispatchers.IO) {
            val opts = BitmapFactory.Options().apply { inSampleSize = 4; inScaled = false }
            BitmapFactory.decodeResource(context.resources, res, opts).asImageBitmap()
        }.also { thumbCache[res] = it }
    }
    return thumb
}

private fun loadWallpaper(context: Context, @DrawableRes res: Int, size: IntSize): Backdrop {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true; inScaled = false }
    BitmapFactory.decodeResource(context.resources, res, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= size.width && bounds.outHeight / (sample * 2) >= size.height) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample; inScaled = false }
    val src = BitmapFactory.decodeResource(context.resources, res, opts)
    val sharp = centerCrop(src, size.width, size.height)
    return Backdrop(sharp.asImageBitmap(), Blur.blur(sharp, targetWidth = 192, radius = 5).asImageBitmap())
}

fun centerCrop(src: Bitmap, w: Int, h: Int): Bitmap {
    if (src.width == w && src.height == h) return src
    val scale = max(w.toFloat() / src.width, h.toFloat() / src.height)
    val cw = (w / scale).roundToInt().coerceAtMost(src.width)
    val ch = (h / scale).roundToInt().coerceAtMost(src.height)
    val left = (src.width - cw) / 2
    val top = (src.height - ch) / 2
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    Canvas(out).drawBitmap(src, Rect(left, top, left + cw, top + ch), Rect(0, 0, w, h), Paint(Paint.FILTER_BITMAP_FLAG))
    return out
}

/** Three-pass box blur on a downscaled copy. Close to gaussian, cheap enough for a TV SoC, works on every API level. */
object Blur {
    fun blur(src: Bitmap, targetWidth: Int, radius: Int, passes: Int = 3): Bitmap {
        val w = targetWidth.coerceAtMost(src.width).coerceAtLeast(1)
        val h = (src.height * w.toFloat() / src.width).roundToInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(src, w, h, true)
        val a = IntArray(w * h)
        val b = IntArray(w * h)
        small.getPixels(a, 0, w, 0, 0, w, h)
        repeat(passes) {
            pass(a, b, w, h, radius, horizontal = true)
            pass(b, a, w, h, radius, horizontal = false)
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(a, 0, w, 0, 0, w, h)
        return out
    }

    private fun pass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val lines = if (horizontal) h else w
        val len = if (horizontal) w else h
        val window = 2 * r + 1
        for (line in 0 until lines) {
            fun at(i: Int): Int {
                val c = i.coerceIn(0, len - 1)
                return if (horizontal) src[line * w + c] else src[c * w + line]
            }
            var sr = 0; var sg = 0; var sb = 0
            for (i in -r..r) {
                val p = at(i)
                sr += (p shr 16) and 0xFF; sg += (p shr 8) and 0xFF; sb += p and 0xFF
            }
            for (i in 0 until len) {
                val idx = if (horizontal) line * w + i else i * w + line
                dst[idx] = (0xFF shl 24) or ((sr / window) shl 16) or ((sg / window) shl 8) or (sb / window)
                val add = at(i + r + 1)
                val sub = at(i - r)
                sr += ((add shr 16) and 0xFF) - ((sub shr 16) and 0xFF)
                sg += ((add shr 8) and 0xFF) - ((sub shr 8) and 0xFF)
                sb += (add and 0xFF) - (sub and 0xFF)
            }
        }
    }
}
