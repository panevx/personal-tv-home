package dev.panev.tvhome.ui.glass

import android.view.KeyEvent as NativeKeyEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.panev.tvhome.ui.LocalGlassStyle
import dev.panev.tvhome.ui.LocalMotion
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

val LocalBackdrop = androidx.compose.runtime.compositionLocalOf<Backdrop?> { null }

/**
 * Translucent glass: the scenery directly behind the surface, softly blurred, under a restrained
 * tint, a faint top sheen and a hairline edge that is brighter at the top, like light catching it.
 * [strength] lets a surface sit a little denser than the dock (panels, dialogs).
 */
fun Modifier.glass(shape: Shape, strength: Float = 1f, shadow: Dp = 0.dp): Modifier = composed {
    val backdrop = LocalBackdrop.current
    val root = LocalRootSize.current
    val style = LocalGlassStyle.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    val tint = Color.Black.copy(alpha = (style.tint * strength).coerceAtMost(0.6f))
    val frost = Color.White.copy(alpha = style.frost)

    this
        .then(
            if (shadow > 0.dp) Modifier.shadow(shadow, shape, ambientColor = Color(0x66000000), spotColor = Color(0x80000000))
            else Modifier
        )
        .onGloballyPositioned { origin = it.positionInRoot() }
        .drawBehind {
            val outline = shape.createOutline(size, layoutDirection, this)
            val path = Path().apply { addOutline(outline) }
            clipPath(path) {
                val img = backdrop?.blurred
                if (img != null && root.width > 0) {
                    val sx = img.width / root.width.toFloat()
                    val sy = img.height / root.height.toFloat()
                    // Only the part of this surface that is actually on screen maps onto the backdrop.
                    val left = max(origin.x, 0f)
                    val top = max(origin.y, 0f)
                    val right = min(origin.x + size.width, root.width.toFloat())
                    val bottom = min(origin.y + size.height, root.height.toFloat())
                    if (right > left && bottom > top) {
                        val srcX = floor(left * sx).toInt().coerceIn(0, img.width - 1)
                        val srcY = floor(top * sy).toInt().coerceIn(0, img.height - 1)
                        val srcW = ((right - left) * sx).toInt().coerceIn(1, img.width - srcX)
                        val srcH = ((bottom - top) * sy).toInt().coerceIn(1, img.height - srcY)
                        drawImage(
                            image = img,
                            srcOffset = IntOffset(srcX, srcY),
                            srcSize = IntSize(srcW, srcH),
                            dstOffset = IntOffset((left - origin.x).toInt(), (top - origin.y).toInt()),
                            dstSize = IntSize((right - left).toInt(), (bottom - top).toInt()),
                            filterQuality = FilterQuality.Low,
                        )
                    }
                } else {
                    drawRect(Color(0xFF1E2228))
                }
                drawRect(tint)
                drawRect(frost)
                drawRect(
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.10f),
                        0.45f to Color.Transparent,
                    )
                )
            }
            drawOutline(
                outline,
                Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.42f),
                    0.5f to Color.White.copy(alpha = 0.10f),
                    1f to Color.White.copy(alpha = 0.18f),
                ),
                style = Stroke(width = 1.dp.toPx()),
            )
        }
}

/** Select = click, holding select = long click. Works with any remote, no touch needed. */
fun Modifier.dpadClick(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onPressed: (Boolean) -> Unit = {},
): Modifier = composed {
    var longFired by remember { mutableStateOf(false) }
    // Only a press that started here counts. Otherwise the release of a long press that opened a
    // menu lands on the menu's first row and clicks it.
    var armed by remember { mutableStateOf(false) }
    onPreviewKeyEvent { e ->
        val code = e.nativeKeyEvent.keyCode
        if (code != NativeKeyEvent.KEYCODE_DPAD_CENTER && code != NativeKeyEvent.KEYCODE_ENTER &&
            code != NativeKeyEvent.KEYCODE_NUMPAD_ENTER
        ) return@onPreviewKeyEvent false
        when (e.type) {
            KeyEventType.KeyDown -> {
                val native = e.nativeKeyEvent
                if (native.repeatCount == 0) {
                    armed = true
                    longFired = false
                    onPressed(true)
                } else if (armed && !longFired && onLongClick != null && (native.isLongPress || native.repeatCount >= 10)) {
                    longFired = true
                    onPressed(false)
                    onLongClick()
                }
            }
            KeyEventType.KeyUp -> {
                onPressed(false)
                if (armed && !longFired) onClick()
                armed = false
                longFired = false
            }
        }
        true
    }
}

/**
 * The single focus treatment used everywhere: a springy lift (scale + soft shadow) and a delicate
 * bright edge. Content receives the focus state so controls can invert to soft white.
 */
@Composable
fun FocusSurface(
    onClick: () -> Unit,
    shape: Shape,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    onFocusChange: (Boolean) -> Unit = {},
    focusedScale: Float = 1.08f,
    lift: Dp = 22.dp,
    ring: Boolean = true,
    clip: Boolean = true,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    val motion = LocalMotion.current
    var focused by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = when {
            pressed -> focusedScale - (focusedScale - 1f) * 0.6f
            focused -> focusedScale
            else -> 1f
        },
        animationSpec = motion.focus(),
        label = "scale",
    )
    val emphasis by animateFloatAsState(if (focused) 1f else 0f, motion.settle(), label = "emphasis")

    Box(
        modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged {
                focused = it.isFocused
                if (!it.isFocused) pressed = false
                onFocusChange(it.isFocused)
            }
            .dpadClick(onClick, onLongClick) { pressed = it }
            .focusable()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                shadowElevation = lift.toPx() * emphasis
                this.shape = shape
                this.clip = clip
                ambientShadowColor = Color.Black
                spotShadowColor = Color.Black
            }
            .drawWithContent {
                drawContent()
                if (ring && emphasis > 0.01f) {
                    drawOutline(
                        shape.createOutline(size, layoutDirection, this),
                        Color.White.copy(alpha = 0.92f * emphasis),
                        style = Stroke(width = 2.5.dp.toPx()),
                    )
                }
            }
    ) {
        content(focused)
    }
}
