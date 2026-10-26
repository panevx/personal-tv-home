package dev.panev.tvhome.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.panev.tvhome.data.AppArt
import dev.panev.tvhome.data.AppEntry
import dev.panev.tvhome.data.AppRepository
import dev.panev.tvhome.data.SavedTitle
import dev.panev.tvhome.ui.*
import dev.panev.tvhome.ui.glass.FocusSurface
import dev.panev.tvhome.ui.glass.glass
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val TileShape = RoundedCornerShape(14.dp)
val CardShape = RoundedCornerShape(18.dp)
val PanelShape = RoundedCornerShape(30.dp)
val PillShape = RoundedCornerShape(percent = 50)

@Composable
fun rememberAppArt(repo: AppRepository, app: AppEntry?): AppArt? {
    if (app == null) return null
    var art by remember(app.pkg) { mutableStateOf(repo.cachedArt(app.pkg)) }
    LaunchedEffect(app.pkg) { if (art == null) art = repo.art(app) }
    return art
}

/** The app's real TV artwork, edge to edge. Icon-only apps sit on their own colour, never a generic black box. */
@Composable
fun AppArtwork(repo: AppRepository, app: AppEntry?, modifier: Modifier = Modifier) {
    val art = rememberAppArt(repo, app)
    Box(modifier.background(art?.background ?: Color(0x33FFFFFF)), contentAlignment = Alignment.Center) {
        when {
            art?.banner != null -> Image(art.banner, app?.label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            art?.icon != null -> Image(art.icon, app?.label, Modifier.fillMaxHeight(0.62f).aspectRatio(1f), contentScale = ContentScale.Fit)
        }
    }
}

@Composable
fun rememberTime(): Long {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(60_000L - value % 60_000L + 30L)
        }
    }
    return now
}

@Composable
fun Clock(clock24: Boolean, modifier: Modifier = Modifier, style: androidx.compose.ui.text.TextStyle = Type.Clock) {
    val now = rememberTime()
    val fmt = remember(clock24) { SimpleDateFormat(if (clock24) "HH:mm" else "h:mm", Locale.getDefault()) }
    Txt(fmt.format(Date(now)), style, modifier)
}

/** Shared frame for every screen other than Home: a quiet title, the time, and generous margins. */
@Composable
fun ScreenScaffold(
    title: String,
    clock24: Boolean,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(start = 52.dp, end = 52.dp, top = 34.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Txt(title, Type.Title)
                if (subtitle != null) {
                    Spacer(Modifier.height(4.dp))
                    Txt(subtitle, Type.Caption)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                actions()
            }
            Spacer(Modifier.width(24.dp))
            Clock(clock24, style = Type.Clock.copy(fontSize = Type.Headline.fontSize))
        }
        Spacer(Modifier.height(22.dp))
        content()
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, detail: String? = null) {
    Row(modifier.padding(bottom = 12.dp), verticalAlignment = Alignment.Bottom) {
        Txt(text, Type.Headline)
        if (detail != null) {
            Spacer(Modifier.width(10.dp))
            Txt(detail, Type.Caption, Modifier.padding(bottom = 2.dp))
        }
    }
}

/** A quiet empty state: one icon, one line of title, one of help, and at most one way forward. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    actionIcon: ImageVector? = null,
    focusRequester: FocusRequester? = null,
    onAction: () -> Unit = {},
) {
    Column(modifier.widthIn(max = 460.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Ico(icon, size = 34.dp, tint = Ink.Tertiary)
        Spacer(Modifier.height(16.dp))
        Txt(title, Type.Headline, align = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Txt(body, Type.Body, color = Ink.Secondary, maxLines = 3, align = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(22.dp))
            GlassPillButton(action, actionIcon, onAction, focusRequester = focusRequester)
        }
    }
}

/** Glass capsule. On focus it turns soft white with dark ink — the control-level focus treatment. */
@Composable
fun GlassPillButton(
    text: String,
    icon: ImageVector?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    FocusSurface(onClick, PillShape, modifier, focusRequester = focusRequester, focusedScale = 1.06f, lift = 14.dp, ring = false) { focused ->
        val fill by animateColorAsState(if (focused) Ink.FocusFill else Color.Transparent, LocalMotion.current.settle(), label = "fill")
        val ink = if (focused) Ink.OnFocus else Ink.Primary
        Box(Modifier.glass(PillShape).background(fill).padding(horizontal = 22.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Ico(icon, size = 20.dp, tint = ink)
                    Spacer(Modifier.width(10.dp))
                }
                Txt(text, Type.Label.copy(shadow = null), color = ink)
            }
        }
    }
}

@Composable
fun GlassIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    iconSize: Dp = 24.dp,
    focusRequester: FocusRequester? = null,
) {
    FocusSurface(onClick, CircleShape, modifier.size(size), focusRequester = focusRequester, focusedScale = 1.1f, lift = 14.dp, ring = false) { focused ->
        val fill by animateColorAsState(if (focused) Ink.FocusFill else Color.Transparent, LocalMotion.current.settle(), label = "fill")
        Box(Modifier.fillMaxSize().glass(CircleShape).background(fill), contentAlignment = Alignment.Center) {
            Ico(icon, size = iconSize, tint = if (focused) Ink.OnFocus else Ink.Primary)
        }
    }
}

/** List row used in the Settings panel, menus and Appearance. */
@Composable
fun GlassRow(
    icon: ImageVector?,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    selected: Boolean = false,
    focusRequester: FocusRequester? = null,
) {
    FocusSurface(onClick, CardShape, modifier.fillMaxWidth(), focusRequester = focusRequester, focusedScale = 1.025f, lift = 10.dp, ring = false) { focused ->
        val fill by animateColorAsState(if (focused) Ink.FocusFill else Color.Transparent, LocalMotion.current.settle(), label = "fill")
        val ink = if (focused) Ink.OnFocus else Ink.Primary
        val soft = if (focused) Ink.OnFocus.copy(alpha = 0.62f) else Ink.Secondary
        Row(
            Modifier.fillMaxWidth().background(fill).padding(horizontal = 18.dp, vertical = if (subtitle != null) 12.dp else 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Ico(icon, size = 22.dp, tint = ink)
                Spacer(Modifier.width(16.dp))
            }
            Column(Modifier.weight(1f)) {
                Txt(title, Type.Label.copy(fontSize = Type.Body.fontSize, shadow = null), color = ink)
                if (subtitle != null) {
                    Spacer(Modifier.height(2.dp))
                    Txt(subtitle, Type.Caption.copy(shadow = null), color = soft, maxLines = 2)
                }
            }
            if (value != null) {
                Spacer(Modifier.width(12.dp))
                Txt(value, Type.Label.copy(shadow = null), color = soft)
            }
            if (selected) {
                Spacer(Modifier.width(12.dp))
                Ico(Icons.Rounded.Check, size = 20.dp, tint = ink)
            }
        }
    }
}

/** Full-artwork tile with the name beneath, for Apps, Discover and Search. */
@Composable
fun AppGridTile(
    repo: AppRepository,
    app: AppEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    Column(modifier) {
        FocusSurface(
            onClick, TileShape,
            Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            onLongClick = onLongClick,
            focusRequester = focusRequester,
            onFocusChange = { focused = it },
        ) {
            AppArtwork(repo, app, Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(10.dp))
        val color by animateColorAsState(if (focused) Ink.Primary else Ink.Secondary, label = "name")
        Txt(app.label, Type.Label.copy(fontSize = Type.Caption.fontSize), Modifier.fillMaxWidth(), color = color, align = TextAlign.Center)
    }
}

/** A saved title: the app's artwork, gently shaded, with the title over it. */
@Composable
fun TitleCard(
    repo: AppRepository,
    title: SavedTitle,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
) {
    val app = repo.find(title.pkg)
    FocusSurface(onClick, CardShape, modifier.size(212.dp, 120.dp), onLongClick = onLongClick, focusRequester = focusRequester) {
        if (app != null) {
            AppArtwork(repo, app, Modifier.fillMaxSize())
        } else {
            Box(Modifier.fillMaxSize().glass(CardShape))
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0.25f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.78f))
            )
        )
        Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
            Txt(title.title, Type.Label, maxLines = 2)
            Spacer(Modifier.height(2.dp))
            Txt(app?.label ?: "Search when opened", Type.Caption)
        }
    }
}
