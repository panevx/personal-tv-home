package dev.panev.tvhome.ui

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.panev.tvhome.R
import dev.panev.tvhome.data.GlassStyle

val Inter = FontFamily(
    Font(R.font.inter_light, FontWeight.Light),
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
)

object Ink {
    val Primary = Color(0xFFF5F5F7)
    val Secondary = Color(0xB8F5F5F7)
    val Tertiary = Color(0x80F5F5F7)
    /** Text and icons sitting on a focused (soft white) control. */
    val OnFocus = Color(0xFF1C1C1F)
    val FocusFill = Color(0xF2F5F5F7)
}

private val softShadow = Shadow(Color(0x55000000), Offset(0f, 1.5f), 14f)

object Type {
    private val base = TextStyle(fontFamily = Inter, color = Ink.Primary, shadow = softShadow)
    val Clock = base.copy(fontSize = 30.sp, fontWeight = FontWeight.Light, letterSpacing = (-0.2).sp, fontFeatureSettings = "tnum")
    val Temperature = base.copy(fontSize = 28.sp, fontWeight = FontWeight.Light, letterSpacing = (-0.4).sp, fontFeatureSettings = "tnum")
    val Display = base.copy(fontSize = 34.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.6).sp, lineHeight = 40.sp)
    val Title = base.copy(fontSize = 28.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.4).sp)
    val Headline = base.copy(fontSize = 19.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.2).sp)
    val Body = base.copy(fontSize = 16.sp, fontWeight = FontWeight.Normal, lineHeight = 23.sp)
    val Label = base.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium)
    val Caption = base.copy(fontSize = 13.sp, fontWeight = FontWeight.Normal, color = Ink.Secondary, letterSpacing = 0.1.sp)
    val Numeric = base.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Ink.Secondary, fontFeatureSettings = "tnum")
}

/** One spring for the whole launcher, so every surface moves with the same feel. */
class Motion(val reduced: Boolean) {
    fun <T> focus(): FiniteAnimationSpec<T> =
        if (reduced) tween(90) else spring(dampingRatio = 0.72f, stiffness = 420f)

    fun <T> settle(): FiniteAnimationSpec<T> =
        if (reduced) tween(120) else spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 260f)

    fun <T> fade(ms: Int = 320): FiniteAnimationSpec<T> = tween(if (reduced) ms / 3 else ms)
}

val LocalMotion = staticCompositionLocalOf { Motion(false) }
val LocalGlassStyle = staticCompositionLocalOf { GlassStyle.Balanced }

@Composable
fun Txt(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    maxLines: Int = 1,
    align: TextAlign? = null,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = style.merge(TextStyle(color = color, textAlign = align ?: TextAlign.Unspecified)),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun Ico(icon: ImageVector, modifier: Modifier = Modifier, size: Dp = 24.dp, tint: Color = Ink.Primary) {
    Image(icon, contentDescription = null, modifier = modifier.size(size), colorFilter = ColorFilter.tint(tint))
}
