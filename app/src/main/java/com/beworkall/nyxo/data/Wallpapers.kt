package com.beworkall.nyxo.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.beworkall.nyxo.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/** A theme pack: wallpaper, matching accent and the panel style it looks best with. */
data class ThemePack(
    val id: String,
    val name: String,
    val tagline: String,
    val res: Int?,
    val accent: Color,
    val style: String,
)

val THEME_PACKS = listOf(
    ThemePack("amoled", "Pure AMOLED", "True black. Best for battery.", null, Color(0xFF9D8CFF), "solid"),
    ThemePack("midnight_city", "Midnight City", "Amber lights over a sleeping skyline.", R.drawable.wp_midnight_city, Color(0xFFFFB45A), "hardglass"),
    ThemePack("crimson_ronin", "Crimson Ronin", "Red moon, falling petals.", R.drawable.wp_crimson_ronin, Color(0xFFFF3B4E), "hardglass"),
    ThemePack("golden_moon", "Golden Moon", "A lone tree under a golden moon.", R.drawable.wp_golden_moon, Color(0xFFE9B45C), "glass"),
    ThemePack("summit", "Summit", "Silver peak in the dark.", R.drawable.wp_summit, Color(0xFFE9ECF2), "glass"),
    ThemePack("bare_branches", "Bare Branches", "Looking up through winter trees.", R.drawable.wp_bare_branches, Color(0xFFB8C2D1), "glass"),
    ThemePack("dusk_street", "Dusk Street", "Quiet street at sunset.", R.drawable.wp_dusk_street, Color(0xFF4FD1C5), "water"),
    ThemePack("custom", "My photo", "Pick any photo from your gallery.", null, Color(0xFF9D8CFF), "glass"),
)

fun packById(id: String): ThemePack = THEME_PACKS.firstOrNull { it.id == id } ?: THEME_PACKS[0]

/** Wallpaper fitted to the screen, plus a soft pre-blurred copy used behind glass panels. */
class LoadedWallpaper(val sharp: ImageBitmap, val blurred: ImageBitmap, val blurScale: Int)

object Wallpapers {
    private const val CUSTOM_FILE = "wallpaper_custom.jpg"
    private var cacheKey: String? = null
    private var cache: LoadedWallpaper? = null

    fun customFile(context: Context) = File(context.filesDir, CUSTOM_FILE)

    /** Copies a photo the user picked into Nyxo's private storage. */
    suspend fun importCustom(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                customFile(context).outputStream().use { input.copyTo(it) }
            } ?: return@runCatching false
            synchronized(this@Wallpapers) { cacheKey = null; cache = null }
            true
        }.getOrDefault(false)
    }

    /** Loads [pack] cropped to [w]x[h]. Returns null for packs without an image. */
    suspend fun load(context: Context, pack: ThemePack, w: Int, h: Int, version: Long = 0): LoadedWallpaper? {
        if (w <= 0 || h <= 0) return null
        val key = "${pack.id}:$w:$h:$version"
        synchronized(this) { if (key == cacheKey) return cache }
        val loaded = withContext(Dispatchers.Default) {
            val src: Bitmap = decode(context, pack, w, h) ?: return@withContext null
            val fitted = cropFill(src, w, h)
            if (fitted !== src) src.recycle()
            val scale = 12
            val small = Bitmap.createScaledBitmap(fitted, max(1, w / scale), max(1, h / scale), true)
                .copy(Bitmap.Config.ARGB_8888, true)
            boxBlur(small, 2)
            boxBlur(small, 2)
            LoadedWallpaper(fitted.asImageBitmap(), small.asImageBitmap(), scale)
        }
        synchronized(this) {
            cacheKey = key
            cache = loaded
        }
        return loaded
    }

    /** A small preview for the theme picker. */
    suspend fun thumbnail(context: Context, pack: ThemePack, w: Int, h: Int): ImageBitmap? =
        withContext(Dispatchers.Default) {
            val src = decode(context, pack, w, h) ?: return@withContext null
            cropFill(src, w, h).asImageBitmap()
        }

    private fun decode(context: Context, pack: ThemePack, w: Int, h: Int): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val res = pack.res
        val file = customFile(context)
        when {
            res != null -> BitmapFactory.decodeResource(context.resources, res, bounds)
            pack.id == "custom" && file.exists() -> BitmapFactory.decodeFile(file.path, bounds)
            else -> return null
        }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= w && bounds.outHeight / (sample * 2) >= h) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        if (res != null) BitmapFactory.decodeResource(context.resources, res, opts)
        else BitmapFactory.decodeFile(file.path, opts)
    }.getOrNull()

    private fun cropFill(src: Bitmap, w: Int, h: Int): Bitmap {
        val scale = max(w.toFloat() / src.width, h.toFloat() / src.height)
        val sw = (w / scale).roundToInt().coerceIn(1, src.width)
        val sh = (h / scale).roundToInt().coerceIn(1, src.height)
        val sx = ((src.width - sw) / 2).coerceAtLeast(0)
        val sy = ((src.height - sh) / 2).coerceAtLeast(0)
        val cropped = Bitmap.createBitmap(src, sx, sy, sw, sh)
        val out = Bitmap.createScaledBitmap(cropped, w, h, true)
        if (cropped !== src && cropped !== out) cropped.recycle()
        return out
    }

    /** Simple separable box blur on a small mutable bitmap. Runs once per wallpaper, not per frame. */
    private fun boxBlur(bmp: Bitmap, radius: Int) {
        val w = bmp.width
        val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        val tmp = IntArray(w * h)
        pass(px, tmp, w, h, radius, true)
        pass(tmp, px, w, h, radius, false)
        bmp.setPixels(px, 0, w, 0, 0, w, h)
    }

    private fun pass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val outer = if (horizontal) h else w
        val inner = if (horizontal) w else h
        for (o in 0 until outer) {
            for (i in 0 until inner) {
                var a = 0; var rr = 0; var g = 0; var b = 0; var n = 0
                for (k in -r..r) {
                    val j = (i + k).coerceIn(0, inner - 1)
                    val c = if (horizontal) src[o * w + j] else src[j * w + o]
                    a += (c ushr 24) and 0xff
                    rr += (c shr 16) and 0xff
                    g += (c shr 8) and 0xff
                    b += c and 0xff
                    n++
                }
                val idx = if (horizontal) o * w + i else i * w + o
                dst[idx] = ((a / n) shl 24) or ((rr / n) shl 16) or ((g / n) shl 8) or (b / n)
            }
        }
    }
}
