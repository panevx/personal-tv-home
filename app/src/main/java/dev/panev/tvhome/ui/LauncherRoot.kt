package dev.panev.tvhome.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.panev.tvhome.LauncherViewModel
import dev.panev.tvhome.Overlay
import dev.panev.tvhome.Screen
import dev.panev.tvhome.ui.components.*
import dev.panev.tvhome.ui.glass.*
import dev.panev.tvhome.ui.screens.*

@Composable
fun LauncherRoot(vm: LauncherViewModel) {
    val appearance = vm.prefs.appearance
    val motion = remember(appearance.reducedMotion) { Motion(appearance.reducedMotion) }

    BackHandler { vm.back() }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val rootSize = IntSize(constraints.maxWidth, constraints.maxHeight)
        val screen = vm.screen
        val np = vm.media.nowPlaying

        val wallpaper = Wallpapers.firstOrNull { it.id == appearance.wallpaper } ?: Wallpapers.first()
        val wallBackdrop = rememberWallpaperBackdrop(wallpaper, rootSize)
        val artBackdrop = rememberArtBackdrop(np?.art, rootSize)
        val useArt = artBackdrop != null && np != null &&
            (screen == Screen.Player || (screen == Screen.Home && np.playing && appearance.albumBackdrop))
        val backdrop = if (useArt) artBackdrop else wallBackdrop

        CompositionLocalProvider(
            LocalMotion provides motion,
            LocalGlassStyle provides appearance.glass,
            LocalRootSize provides rootSize,
            LocalBackdrop provides backdrop,
        ) {
            Scenery(backdrop, frosted = screen != Screen.Home && screen != Screen.Player, strongShading = appearance.strongShading, art = useArt)

            val blocked = vm.settingsOpen || vm.overlay != null
            AnimatedContent(
                targetState = screen,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    (fadeIn(motion.fade(380)) + scaleIn(motion.settle(), initialScale = 0.985f)) togetherWith
                        (fadeOut(motion.fade(200)) + scaleOut(motion.fade(200), targetScale = 1.01f))
                },
                label = "screen",
            ) { s ->
                Box(Modifier.fillMaxSize().then(if (blocked) Modifier.blockFocus() else Modifier)) {
                    when (s) {
                        Screen.Home -> HomeScreen(vm)
                        Screen.Discover -> DiscoverScreen(vm)
                        Screen.MySpace -> MySpaceScreen(vm)
                        Screen.Apps -> AppsScreen(vm)
                        Screen.Search -> SearchScreen(vm)
                        Screen.Wallpapers -> WallpapersScreen(vm)
                        Screen.Appearance -> AppearanceScreen(vm)
                        Screen.Player -> PlayerScreen(vm)
                    }
                }
            }

            SettingsPanel(vm)
            OverlayHost(vm)
        }
    }
}

/** Keeps D-pad focus out of the screen underneath while a panel or dialog is up. */
@OptIn(ExperimentalComposeUiApi::class)
private fun Modifier.blockFocus() = this
    .focusProperties { enter = { FocusRequester.Cancel } }
    .focusGroup()

@OptIn(ExperimentalComposeUiApi::class)
private fun Modifier.trapFocus() = this
    .focusProperties { exit = { FocusRequester.Cancel } }
    .focusGroup()

/**
 * The photograph, kept clear. Shading is only a soft fall-off at the very top (for the header) and
 * bottom (to ground the dock) — no broad blur band. Other screens frost the same frame.
 */
@Composable
private fun Scenery(backdrop: Backdrop?, frosted: Boolean, strongShading: Boolean, art: Boolean) {
    val motion = LocalMotion.current
    val frost by animateFloatAsState(if (frosted) 1f else 0f, motion.fade(450), label = "frost")
    Crossfade(backdrop, animationSpec = tween(if (motion.reduced) 200 else 900), label = "scenery") { b ->
        if (b != null) {
            Box(Modifier.fillMaxSize()) {
                Image(b.sharp, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                if (frost > 0f) Image(b.blurred, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds, alpha = frost)
            }
        }
    }
    val k = if (strongShading) 1.45f else 1f
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                0f to Color.Black.copy(alpha = 0.34f * k),
                0.24f to Color.Transparent,
                0.66f to Color.Transparent,
                1f to Color.Black.copy(alpha = 0.26f * k),
            )
        )
    )
    val dim by animateFloatAsState((if (frosted) 0.22f else 0f) + (if (art) 0.18f else 0f), motion.fade(450), label = "dim")
    if (dim > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim)))
}

private data class PanelEntry(val icon: androidx.compose.ui.graphics.vector.ImageVector, val title: String, val subtitle: String?, val screen: Screen?)

@Composable
private fun SettingsPanel(vm: LauncherViewModel) {
    val motion = LocalMotion.current
    val open = vm.settingsOpen
    val scrim by animateFloatAsState(if (open) 0.28f else 0f, motion.fade(300), label = "scrim")
    if (scrim > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = scrim)))

    val density = LocalDensity.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
        AnimatedVisibility(
            visible = open,
            enter = slideInHorizontally(motion.settle()) { it + with(density) { 40.dp.roundToPx() } } + fadeIn(motion.fade(250)),
            exit = slideOutHorizontally(motion.fade(240)) { it + with(density) { 40.dp.roundToPx() } } + fadeOut(motion.fade(200)),
        ) {
            val first = remember { FocusRequester() }
            LaunchedEffect(Unit) {
                kotlinx.coroutines.android.awaitFrame()
                runCatching { first.requestFocus() }
            }
            val entries = listOf(
                PanelEntry(Icons.Rounded.Home, "Home", null, Screen.Home),
                PanelEntry(Icons.Rounded.Explore, "Discover", "Jump back into what you use", Screen.Discover),
                PanelEntry(Icons.Rounded.Bookmarks, "My Space", "Saved titles and collections", Screen.MySpace),
                PanelEntry(Icons.Rounded.Apps, "Apps", "Everything installed", Screen.Apps),
                PanelEntry(Icons.Rounded.Search, "Search", null, Screen.Search),
                PanelEntry(Icons.Rounded.Wallpaper, "Wallpapers", null, Screen.Wallpapers),
                PanelEntry(Icons.Rounded.Palette, "Appearance", "Glass, clock, weather, motion", Screen.Appearance),
            )
            Column(
                Modifier
                    .padding(20.dp)
                    .width(372.dp)
                    .fillMaxHeight()
                    .glass(PanelShape, strength = 1.9f, shadow = 30.dp)
                    .trapFocus()
                    .padding(horizontal = 12.dp, vertical = 22.dp)
            ) {
                Txt("Settings", Type.Title, Modifier.padding(start = 18.dp, bottom = 16.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    item {
                        GlassRow(
                            Icons.Rounded.Cast, "Chromecast system settings",
                            { vm.closeSettings(); vm.apps.openSystemSettings() },
                            subtitle = "Network, inputs, display & sound, accounts",
                            focusRequester = first,
                        )
                    }
                    item {
                        Box(Modifier.padding(start = 18.dp, top = 18.dp, bottom = 8.dp)) {
                            Txt("PERSONAL TV HOME", Type.Caption.copy(color = Ink.Tertiary, letterSpacing = Type.Caption.letterSpacing * 8))
                        }
                    }
                    itemsIndexed(entries) { _, e ->
                        GlassRow(e.icon, e.title, { e.screen?.let(vm::navigate) }, subtitle = e.subtitle, selected = e.screen == vm.screen)
                    }
                }
            }
        }
    }
}

@Composable
private fun OverlayHost(vm: LauncherViewModel) {
    val motion = LocalMotion.current
    val overlay = vm.overlay
    val scrim by animateFloatAsState(if (overlay != null) 0.4f else 0f, motion.fade(260), label = "overlay-scrim")
    if (scrim > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = scrim)))

    AnimatedContent(
        targetState = overlay,
        transitionSpec = {
            (fadeIn(motion.fade(240)) + scaleIn(motion.settle(), initialScale = 0.94f)) togetherWith
                (fadeOut(motion.fade(160)) + scaleOut(motion.fade(160), targetScale = 0.97f))
        },
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
        label = "overlay",
    ) { o ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when (o) {
                is Overlay.Menu -> MenuCard(vm, o)
                is Overlay.Prompt -> PromptCard(vm, o)
                null -> Unit
            }
        }
    }
}

@Composable
private fun MenuCard(vm: LauncherViewModel, menu: Overlay.Menu) {
    val first = remember { FocusRequester() }
    LaunchedEffect(menu) {
        kotlinx.coroutines.android.awaitFrame()
        runCatching { first.requestFocus() }
    }
    Column(
        Modifier
            .width(420.dp)
            .heightIn(max = 470.dp)
            .glass(PanelShape, strength = 2.2f, shadow = 30.dp)
            .trapFocus()
            .padding(horizontal = 12.dp, vertical = 20.dp)
    ) {
        Column(Modifier.padding(start = 18.dp, end = 18.dp, bottom = 14.dp)) {
            Txt(menu.title, Type.Headline)
            if (menu.subtitle != null) Txt(menu.subtitle, Type.Caption)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            itemsIndexed(menu.items) { i, item ->
                GlassRow(
                    item.icon, item.label,
                    { vm.dismissOverlay(); item.action() },
                    subtitle = item.detail,
                    focusRequester = if (i == 0) first else null,
                )
            }
        }
    }
}

@Composable
private fun PromptCard(vm: LauncherViewModel, prompt: Overlay.Prompt) {
    var value by remember(prompt) { mutableStateOf(TextFieldValue(prompt.initial, TextRange(prompt.initial.length))) }
    val field = remember { FocusRequester() }
    LaunchedEffect(prompt) {
        kotlinx.coroutines.android.awaitFrame()
        runCatching { field.requestFocus() }
    }
    fun submit() {
        vm.dismissOverlay()
        prompt.onDone(value.text)
    }
    Column(
        Modifier
            .width(520.dp)
            .glass(PanelShape, strength = 2.2f, shadow = 30.dp)
            .trapFocus()
            .padding(28.dp)
    ) {
        Txt(prompt.title, Type.Headline)
        Spacer(Modifier.height(18.dp))
        Box(
            Modifier.fillMaxWidth().glass(PillShape, strength = 1.6f).padding(horizontal = 22.dp, vertical = 14.dp)
        ) {
            if (value.text.isEmpty()) Txt(prompt.hint, Type.Body, color = Ink.Tertiary)
            BasicTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                textStyle = Type.Body.copy(shadow = null),
                cursorBrush = SolidColor(Ink.Primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth().focusRequester(field),
            )
        }
        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassPillButton(prompt.confirm, Icons.Rounded.Check, ::submit)
            GlassPillButton("Cancel", null, { vm.dismissOverlay() })
        }
    }
}
