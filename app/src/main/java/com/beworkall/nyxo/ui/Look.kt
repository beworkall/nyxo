package com.beworkall.nyxo.ui

import android.view.View
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toAndroidRect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.beworkall.nyxo.LauncherViewModel
import com.beworkall.nyxo.data.AppEntry
import com.beworkall.nyxo.data.LoadedWallpaper
import kotlin.math.roundToInt

/** Everything a surface needs to know to draw itself in the current theme. */
class Look(
    val style: String,
    val wallpaper: LoadedWallpaper?,
    val motion: Boolean,
    val tiltEnabled: Boolean,
    val tiltX: () -> Float,
    val tiltY: () -> Float,
) {
    val isGlass: Boolean get() = wallpaper != null && style != "solid"
}

val LocalLook = compositionLocalOf {
    Look("solid", null, motion = true, tiltEnabled = false, tiltX = { 0f }, tiltY = { 0f })
}

private class GlassParams(
    val tint: Color,
    val shine: Float,
    val border: Dp,
    val edgeTop: Float,
    val edgeBottom: Float,
)

private fun paramsFor(style: String): GlassParams = when (style) {
    "hardglass" -> GlassParams(Color(0xFF05070A).copy(alpha = 0.62f), 0.16f, 1.5.dp, 0.50f, 0.08f)
    "water" -> GlassParams(Color(0xFF031420).copy(alpha = 0.42f), 0.12f, 1.dp, 0.20f, 0.12f)
    else -> GlassParams(Color.Black.copy(alpha = 0.38f), 0.10f, 1.dp, 0.24f, 0.06f)
}

/**
 * The background of every card, panel and menu.
 * Solid style: an opaque dark panel. Glass styles: the wallpaper behind it, softly blurred,
 * with a highlight that follows the phone's tilt. The blur is computed once, not every frame.
 */
fun Modifier.nyxoSurface(shape: Shape, solid: Color = Panel): Modifier = composed {
    val look = LocalLook.current
    val wp = look.wallpaper
    if (wp == null || look.style == "solid") {
        this.clip(shape).background(solid).border(1.dp, Line, shape)
    } else {
        val view = LocalView.current
        var origin by remember { mutableStateOf(Offset.Zero) }
        val p = paramsFor(look.style)
        this
            .onGloballyPositioned {
                val loc = IntArray(2)
                view.rootView.getLocationOnScreen(loc)
                origin = it.positionInWindow() + Offset(loc[0].toFloat(), loc[1].toFloat())
            }
            .clip(shape)
            .drawBehind {
                val img = wp.blurred
                val s = wp.blurScale.toFloat()
                val sx = (origin.x / s).roundToInt().coerceIn(0, img.width - 1)
                val sy = (origin.y / s).roundToInt().coerceIn(0, img.height - 1)
                val sw = (size.width / s).roundToInt().coerceIn(1, img.width - sx)
                val sh = (size.height / s).roundToInt().coerceIn(1, img.height - sy)
                drawImage(
                    image = img,
                    srcOffset = IntOffset(sx, sy),
                    srcSize = IntSize(sw, sh),
                    dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                    filterQuality = FilterQuality.Low,
                )
                drawRect(p.tint)
                val center = if (look.tiltEnabled) {
                    Offset(size.width * (0.5f - look.tiltX() * 0.6f), size.height * (0.15f + look.tiltY() * 0.4f))
                } else {
                    Offset(size.width * 0.3f, 0f)
                }
                drawRect(
                    Brush.radialGradient(
                        listOf(Color.White.copy(alpha = p.shine), Color.Transparent),
                        center = center,
                        radius = size.maxDimension.coerceAtLeast(1f),
                    )
                )
            }
            .border(
                p.border,
                Brush.verticalGradient(listOf(Color.White.copy(alpha = p.edgeTop), Color.White.copy(alpha = p.edgeBottom))),
                shape,
            )
    }
}

/** Full-screen wallpaper behind the home screen, with a gentle zoom when panels open. */
@Composable
fun WallpaperLayer(wallpaper: LoadedWallpaper?, dim: Float, depth: Float) {
    Box(Modifier.fillMaxSize()) {
        if (wallpaper != null) {
            Image(
                bitmap = wallpaper.sharp,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    val k = 1f + 0.06f * depth
                    scaleX = k
                    scaleY = k
                },
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = (dim + depth * 0.25f).coerceIn(0f, 0.95f))))
        } else {
            Box(Modifier.fillMaxSize().background(Color.Black))
        }
    }
}

/** Background for full-screen panels (drawer, radar…): frosted wallpaper in glass themes. */
@Composable
fun PanelBackdrop() {
    val look = LocalLook.current
    val wp = look.wallpaper
    if (look.isGlass && wp != null) {
        Box(Modifier.fillMaxSize()) {
            Image(
                bitmap = wp.blurred,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.Low,
                modifier = Modifier.fillMaxSize(),
            )
            Box(Modifier.fillMaxSize().background(paramsFor(look.style).tint.copy(alpha = 0.55f)))
        }
    } else {
        Box(Modifier.fillMaxSize().background(Color(0xF505070A)))
    }
}

// ---------------------------------------------------------------- water ripples

class Ripple(val center: Offset, val start: Long)

class Ripples {
    val items = mutableStateListOf<Ripple>()
    fun add(at: Offset) {
        if (items.size > 6) items.removeAt(0)
        items.add(Ripple(at, System.nanoTime()))
    }
}

/** Rings that spread from your finger in the Water style. Only animates while ripples exist. */
@Composable
fun RippleOverlay(ripples: Ripples, color: Color) {
    var now by remember { mutableLongStateOf(System.nanoTime()) }
    val active = ripples.items.isNotEmpty()
    LaunchedEffect(active) {
        while (ripples.items.isNotEmpty()) {
            withFrameNanos { }
            now = System.nanoTime()
            ripples.items.removeAll { now - it.start > RIPPLE_NS }
        }
    }
    if (!active) return
    Canvas(Modifier.fillMaxSize()) {
        for (r in ripples.items) {
            val t = ((now - r.start).toFloat() / RIPPLE_NS).coerceIn(0f, 1f)
            for (k in 0..2) {
                val tt = (t - k * 0.14f).coerceIn(0f, 1f)
                if (tt <= 0f) continue
                drawCircle(
                    color = color.copy(alpha = (1f - tt) * 0.32f),
                    radius = 24.dp.toPx() + tt * 200.dp.toPx(),
                    center = r.center,
                    style = Stroke(width = ((1f - tt) * 3f + 0.6f).dp.toPx()),
                )
            }
        }
    }
}

private const val RIPPLE_NS = 1_300_000_000L

// ---------------------------------------------------------------- touch feel

/** Springy press: the thing you touch squishes slightly and bounces back. */
@Composable
fun rememberSquish(source: MutableInteractionSource): Float {
    val look = LocalLook.current
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && look.motion) 0.9f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 650f),
        label = "squish",
    )
    return scale
}

/** Remembers where an icon is on screen so the app can zoom out of it when it opens. */
class LaunchTarget(val view: View) {
    var bounds: androidx.compose.ui.geometry.Rect? = null
}

@Composable
fun rememberLaunchTarget(): LaunchTarget {
    val view = LocalView.current
    return remember(view) { LaunchTarget(view) }
}

fun Modifier.trackBounds(target: LaunchTarget): Modifier = onGloballyPositioned { target.bounds = it.boundsInWindow() }

fun LauncherViewModel.launchFrom(app: AppEntry, target: LaunchTarget) {
    launch(app, target.view, target.bounds?.toAndroidRect())
}
