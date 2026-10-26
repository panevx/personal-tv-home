package dev.panev.tvhome.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import dev.panev.tvhome.LauncherViewModel
import dev.panev.tvhome.MenuItem
import dev.panev.tvhome.Overlay
import dev.panev.tvhome.Screen
import dev.panev.tvhome.data.AppEntry
import dev.panev.tvhome.data.NowPlaying
import dev.panev.tvhome.data.Weather
import dev.panev.tvhome.ui.*
import dev.panev.tvhome.ui.components.*
import dev.panev.tvhome.ui.glass.FocusSurface
import dev.panev.tvhome.ui.glass.glass
import kotlinx.coroutines.android.awaitFrame
import kotlin.math.roundToInt

private val TileW = 144.dp
private val TileH = 84.dp
private val TileGap = 16.dp
private val DockPad = 18.dp
private val DockShape = RoundedCornerShape(14.dp + DockPad) // concentric with the tiles

@Composable
fun HomeScreen(vm: LauncherViewModel) {
    val appearance = vm.prefs.appearance
    val dock = vm.dockApps
    val np = vm.media.nowPlaying
    var focusedLabel by remember { mutableStateOf<String?>(null) }
    val labelFade = LocalMotion.current.fade<Float>(220)

    Box(Modifier.fillMaxSize()) {
        // Header: weather alone on the left; clock and one glass Settings button on the right.
        Row(
            Modifier.fillMaxWidth().padding(start = 52.dp, end = 44.dp, top = 30.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(Modifier.weight(1f)) {
                if (appearance.showWeather) WeatherGlance(vm.weather, appearance.fahrenheit)
            }
            Clock(appearance.clock24, Modifier.padding(top = 6.dp))
            Spacer(Modifier.width(22.dp))
            GlassIconButton(Icons.Rounded.Settings, onClick = { vm.settingsOpen = true })
        }

        Column(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp, start = 40.dp, end = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                Modifier.widthIn(max = dockMaxWidth()).padding(horizontal = DockPad),
                verticalAlignment = Alignment.Bottom,
            ) {
                // The focused app's name floats above the dock rather than being printed inside it.
                AnimatedContent(
                    targetState = focusedLabel,
                    modifier = Modifier.weight(1f),
                    transitionSpec = { fadeIn(labelFade) togetherWith fadeOut(labelFade) },
                    label = "focused-label",
                ) { label ->
                    Txt(label ?: "", Type.Headline.copy(shadow = Shadow(Color(0x99000000), Offset(0f, 1f), 18f)), Modifier.padding(bottom = 4.dp))
                }
                AnimatedVisibility(
                    visible = np != null,
                    enter = fadeIn() + slideInVertically { it / 2 },
                    exit = fadeOut() + slideOutVertically { it / 2 },
                ) {
                    if (np != null) NowPlayingCapsule(np) { vm.navigate(Screen.Player) }
                }
            }
            Spacer(Modifier.height(14.dp))
            Dock(vm, dock, onFocusLabel = { focusedLabel = it })
        }
    }
}

@Composable
private fun dockMaxWidth() = (LocalConfiguration.current.screenWidthDp - 80).dp

@Composable
private fun Dock(vm: LauncherViewModel, apps: List<AppEntry>, onFocusLabel: (String?) -> Unit) {
    val count = apps.size + 1
    val requesters = remember(count) { List(count) { FocusRequester() } }
    val listState = rememberLazyListState()

    // Land on the last-focused tile whenever Home appears or the Home key is pressed.
    val ready = vm.prefs.favorites != null && vm.apps.apps.isNotEmpty()
    LaunchedEffect(vm.homeSignal, count, ready) {
        if (!ready) return@LaunchedEffect
        val target = vm.dockFocus.coerceIn(0, count - 1)
        listState.scrollToItem((target - 1).coerceAtLeast(0))
        // The target tile may only be composed a frame or two after the scroll.
        repeat(6) {
            awaitFrame()
            if (runCatching { requesters[target].requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }

    Box(
        Modifier
            .widthIn(max = dockMaxWidth())
            .glass(DockShape, shadow = 28.dp)
    ) {
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(DockPad),
            horizontalArrangement = Arrangement.spacedBy(TileGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            itemsIndexed(apps, key = { _, a -> a.pkg }) { i, app ->
                FocusSurface(
                    onClick = { vm.launch(app) },
                    shape = TileShape,
                    modifier = Modifier.size(TileW, TileH).animateItem(),
                    onLongClick = { vm.show(dockMenu(vm, app)) },
                    focusRequester = requesters[i],
                    onFocusChange = { if (it) { vm.dockFocus = i; onFocusLabel(app.label) } },
                ) {
                    AppArtwork(vm.apps, app, Modifier.fillMaxSize())
                }
            }
            item(key = "all-apps") {
                FocusSurface(
                    onClick = { vm.navigate(Screen.Apps) },
                    shape = TileShape,
                    modifier = Modifier.size(TileH, TileH),
                    focusRequester = requesters[count - 1],
                    onFocusChange = { if (it) { vm.dockFocus = count - 1; onFocusLabel("All Apps") } },
                ) { focused ->
                    Box(
                        Modifier.fillMaxSize().background(if (focused) Ink.FocusFill else Color.White.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Ico(Icons.Rounded.Apps, size = 30.dp, tint = if (focused) Ink.OnFocus else Ink.Primary)
                    }
                }
            }
        }
    }
}

private fun dockMenu(vm: LauncherViewModel, app: AppEntry) = Overlay.Menu(
    title = app.label,
    items = listOf(
        MenuItem("Open", Icons.Rounded.PlayArrow) { vm.launch(app) },
        MenuItem("Move left", Icons.Rounded.ChevronLeft) { vm.moveDockApp(app.pkg, -1) },
        MenuItem("Move right", Icons.Rounded.ChevronRight) { vm.moveDockApp(app.pkg, 1) },
        MenuItem("Remove from dock", Icons.Rounded.RemoveCircleOutline) { vm.prefs.toggleFavorite(app.pkg) },
        MenuItem("App info", Icons.Rounded.Info) { vm.apps.openAppInfo(app.pkg) },
    ),
)

@Composable
private fun WeatherGlance(weather: Weather?, fahrenheit: Boolean) {
    AnimatedVisibility(visible = weather != null, enter = fadeIn(LocalMotion.current.fade(600))) {
        if (weather == null) return@AnimatedVisibility
        val temp = if (fahrenheit) weather.tempC * 9 / 5 + 32 else weather.tempC
        Row(verticalAlignment = Alignment.CenterVertically) {
            Ico(weatherIcon(weather), size = 30.dp, tint = Ink.Primary)
            Spacer(Modifier.width(12.dp))
            Column {
                Txt("${temp.roundToInt()}°", Type.Temperature)
                Txt(listOf(weather.place, weather.condition).filter { it.isNotBlank() }.joinToString(" · "), Type.Caption)
            }
        }
    }
}

private fun weatherIcon(w: Weather): ImageVector = when (w.code) {
    0, 1 -> if (w.isDay) Icons.Rounded.WbSunny else Icons.Rounded.NightsStay
    2 -> if (w.isDay) Icons.Rounded.WbCloudy else Icons.Rounded.NightsStay
    3, 45, 48 -> Icons.Rounded.Cloud
    in 51..67, in 80..82 -> Icons.Rounded.WaterDrop
    in 71..77, 85, 86 -> Icons.Rounded.AcUnit
    in 95..99 -> Icons.Rounded.Thunderstorm
    else -> Icons.Rounded.Cloud
}

@Composable
private fun NowPlayingCapsule(np: NowPlaying, onClick: () -> Unit) {
    FocusSurface(onClick, PillShape, focusedScale = 1.05f, lift = 14.dp, ring = false) { focused ->
        val ink = if (focused) Ink.OnFocus else Ink.Primary
        Row(
            Modifier
                .glass(PillShape)
                .background(if (focused) Ink.FocusFill else Color.Transparent)
                .padding(start = 8.dp, end = 20.dp, top = 8.dp, bottom = 8.dp)
                .widthIn(max = 320.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val art = remember(np.art) { np.art?.asImageBitmap() }
            Box(Modifier.size(38.dp).glass(PillShape), contentAlignment = Alignment.Center) {
                if (art != null) {
                    Image(art, null, Modifier.fillMaxSize().clip(PillShape), contentScale = ContentScale.Crop)
                } else {
                    Ico(Icons.Rounded.MusicNote, size = 18.dp, tint = ink)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f, fill = false)) {
                Txt(np.title, Type.Label.copy(shadow = null), color = ink)
                if (np.artist.isNotBlank()) {
                    Txt(np.artist, Type.Caption.copy(shadow = null), color = if (focused) Ink.OnFocus.copy(alpha = 0.6f) else Ink.Secondary)
                }
            }
            Spacer(Modifier.width(12.dp))
            Ico(if (np.playing) Icons.Rounded.GraphicEq else Icons.Rounded.Pause, size = 18.dp, tint = ink)
        }
    }
}
