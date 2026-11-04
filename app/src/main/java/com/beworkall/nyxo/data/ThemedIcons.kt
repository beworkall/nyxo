package com.beworkall.nyxo.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.core.graphics.drawable.toBitmap

/** Tints every icon to the theme accent on a dark disc, like Android's themed icons. */
object ThemedIcons {
    private const val DISC = 0xFF161B25.toInt()

    fun render(drawable: Drawable, px: Int, accent: Int): Bitmap {
        val out = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val disc = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = DISC }
        canvas.drawCircle(px / 2f, px / 2f, px / 2f, disc)

        // Apps that ship a monochrome layer (Android 13+) look the cleanest.
        val mono = if (Build.VERSION.SDK_INT >= 33 && drawable is AdaptiveIconDrawable) drawable.monochrome else null
        if (mono != null) {
            val m = mono.mutate()
            m.colorFilter = PorterDuffColorFilter(accent, PorterDuff.Mode.SRC_IN)
            // The monochrome layer is drawn at adaptive-icon scale: the visible part is the middle 2/3.
            val inset = -px / 4
            m.setBounds(inset, inset, px - inset, px - inset)
            m.draw(canvas)
            return out
        }

        // Everything else: a grey-scale version tinted with the accent.
        val src = drawable.toBitmap(px, px)
        val gray = ColorMatrix().apply { setSaturation(0f) }
        val tintM = ColorMatrix(
            floatArrayOf(
                0.33f * r(accent), 0.33f * r(accent), 0.33f * r(accent), 0f, 0f,
                0.33f * g(accent), 0.33f * g(accent), 0.33f * g(accent), 0f, 0f,
                0.33f * b(accent), 0.33f * b(accent), 0.33f * b(accent), 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
        gray.postConcat(tintM)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(gray) }
        val inner = (px * 0.78f).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(src, inner, inner, true)
        val o = (px - inner) / 2f
        canvas.drawBitmap(scaled, o, o, paint)
        return out
    }

    private fun r(c: Int) = ((c shr 16) and 0xff) / 255f * 1.6f
    private fun g(c: Int) = ((c shr 8) and 0xff) / 255f * 1.6f
    private fun b(c: Int) = (c and 0xff) / 255f * 1.6f
}
