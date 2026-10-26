package dev.panev.tvhome.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.panev.tvhome.LauncherViewModel
import dev.panev.tvhome.MenuItem
import dev.panev.tvhome.Overlay
import dev.panev.tvhome.Screen
import dev.panev.tvhome.data.AppEntry
import dev.panev.tvhome.data.GlassStyle
import dev.panev.tvhome.data.SavedTitle
import dev.panev.tvhome.ui.*
import dev.panev.tvhome.ui.components.*
import dev.panev.tvhome.ui.glass.FocusSurface
import dev.panev.tvhome.ui.glass.Wallpapers
import dev.panev.tvhome.ui.glass.glass
import dev.panev.tvhome.ui.glass.rememberThumbnail
import kotlinx.coroutines.android.awaitFrame

/** Requests focus once the screen has laid out its first frame. */
@Composable
fun rememberEntryFocus(vararg keys: Any?): FocusRequester {
    val fr = remember { FocusRequester() }
    LaunchedEffect(*keys) {
        awaitFrame()
        runCatching { fr.requestFocus() }
    }
    return fr
}

private val GridGapH = 26.dp
private val GridGapV = 22.dp

// ---------------------------------------------------------------- Apps

@Composable
fun AppsScreen(vm: LauncherViewModel) {
    val apps = vm.apps.apps
    val first = rememberEntryFocus()
    val favorites = vm.prefs.favorites.orEmpty()
    ScreenScaffold("Apps", vm.prefs.appearance.clock24, subtitle = "${apps.size} apps · hold select to add one to your dock") {
        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            contentPadding = PaddingValues(top = 10.dp, bottom = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(GridGapH),
            verticalArrangement = Arrangement.spacedBy(GridGapV),
        ) {
            itemsIndexed(apps, key = { _, a -> a.pkg }) { i, app ->
                AppGridTile(
                    vm.apps, app,
                    onClick = { vm.launch(app) },
                    onLongClick = { vm.show(appMenu(vm, app, app.pkg in favorites)) },
                    focusRequester = if (i == 0) first else null,
                )
            }
        }
    }
}

fun appMenu(vm: LauncherViewModel, app: AppEntry, inDock: Boolean) = Overlay.Menu(
    title = app.label,
    items = listOf(
        MenuItem("Open", Icons.Rounded.PlayArrow) { vm.launch(app) },
        if (inDock) MenuItem("Remove from dock", Icons.Rounded.RemoveCircleOutline) { vm.prefs.toggleFavorite(app.pkg) }
        else MenuItem("Add to dock", Icons.Rounded.AddCircleOutline) { vm.prefs.toggleFavorite(app.pkg) },
        MenuItem("App info", Icons.Rounded.Info) { vm.apps.openAppInfo(app.pkg) },
    ),
)

// ---------------------------------------------------------------- Discover

@Composable
fun DiscoverScreen(vm: LauncherViewModel) {
    val apps = vm.apps.apps
    val byPkg = apps.associateBy { it.pkg }
    val recent = vm.prefs.recents.entries.sortedByDescending { it.value }.mapNotNull { byPkg[it.key] }.take(10)
    val saved = vm.prefs.saved.take(10)
    val favorites = vm.prefs.favorites.orEmpty().toSet()
    val notDocked = apps.filterNot { it.pkg in favorites || it.pkg.startsWith("com.sony.dtv") }
    val first = rememberEntryFocus()

    ScreenScaffold("Discover", vm.prefs.appearance.clock24, subtitle = "Picked up from what you use") {
        if (recent.isEmpty() && saved.isEmpty() && notDocked.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    Icons.Rounded.Explore, "Nothing to show yet",
                    "Open a few apps and save some titles. Discover gathers them here so you can jump back in.",
                    action = "Browse apps", actionIcon = Icons.Rounded.Apps, focusRequester = first,
                ) { vm.navigate(Screen.Apps) }
            }
            return@ScreenScaffold
        }
        var firstUsed = false
        fun takeFirst(): FocusRequester? = if (!firstUsed) { firstUsed = true; first } else null

        LazyColumn(contentPadding = PaddingValues(bottom = 48.dp), verticalArrangement = Arrangement.spacedBy(30.dp)) {
            if (recent.isNotEmpty()) {
                val fr = takeFirst()
                item(key = "recent") {
                    AppRow("Jump back in", recent, vm, fr)
                }
            }
            if (saved.isNotEmpty()) {
                val fr = takeFirst()
                item(key = "saved") {
                    Column {
                        SectionTitle("From My Space", detail = "${vm.prefs.saved.size} saved")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                            itemsIndexed(saved, key = { _, t -> t.id }) { i, t ->
                                TitleCard(vm.apps, t, { vm.openSaved(t) }, onLongClick = { vm.show(titleMenu(vm, t)) },
                                    focusRequester = if (i == 0) fr else null)
                            }
                        }
                    }
                }
            }
            if (notDocked.isNotEmpty()) {
                val fr = takeFirst()
                item(key = "more") {
                    AppRow("Not in your dock yet", notDocked, vm, fr, hint = "Hold select to add")
                }
            }
        }
    }
}

@Composable
private fun AppRow(title: String, apps: List<AppEntry>, vm: LauncherViewModel, first: FocusRequester?, hint: String? = null) {
    val favorites = vm.prefs.favorites.orEmpty()
    Column {
        SectionTitle(title, detail = hint)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(GridGapH), contentPadding = PaddingValues(vertical = 8.dp)) {
            itemsIndexed(apps, key = { _, a -> a.pkg }) { i, app ->
                AppGridTile(
                    vm.apps, app, { vm.launch(app) }, Modifier.width(150.dp),
                    onLongClick = { vm.show(appMenu(vm, app, app.pkg in favorites)) },
                    focusRequester = if (i == 0) first else null,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- My Space

@Composable
fun MySpaceScreen(vm: LauncherViewModel) {
    val saved = vm.prefs.saved
    val collections = vm.prefs.collections
    val first = rememberEntryFocus(saved.isEmpty())

    ScreenScaffold(
        "My Space", vm.prefs.appearance.clock24,
        subtitle = if (saved.isEmpty()) "Saved titles and collections" else "${saved.size} saved · ${collections.size} collections",
        actions = {
            if (saved.isNotEmpty()) {
                GlassPillButton("New collection", Icons.Rounded.CreateNewFolder, {
                    vm.show(Overlay.Prompt("New collection", "e.g. Weekend films", confirm = "Create") { vm.prefs.addCollection(it) })
                })
                GlassPillButton("Save a title", Icons.Rounded.Add, { vm.startSaveTitle() })
            }
        },
    ) {
        if (saved.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(bottom = 60.dp), contentAlignment = Alignment.Center) {
                EmptyState(
                    Icons.Rounded.BookmarkBorder, "Nothing saved yet",
                    "Keep the films and shows you mean to watch. Each one opens straight in the app you choose.",
                    action = "Save a title", actionIcon = Icons.Rounded.Add, focusRequester = first,
                ) { vm.startSaveTitle() }
            }
            return@ScreenScaffold
        }
        val grouped = saved.groupBy { it.collection }
        val firstCollection = collections.firstOrNull { grouped[it].orEmpty().isNotEmpty() }
        LazyColumn(contentPadding = PaddingValues(top = 4.dp, bottom = 48.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
            items(collections, key = { it }) { name ->
                val titles = grouped[name].orEmpty()
                Column {
                    SectionTitle(name, detail = if (titles.isEmpty()) null else "${titles.size}")
                    if (titles.isEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Txt("Empty — move titles here from their menu.", Type.Caption)
                            Spacer(Modifier.width(16.dp))
                            if (name != dev.panev.tvhome.data.Prefs.DEFAULT_COLLECTION) {
                                GlassPillButton("Remove collection", Icons.Rounded.DeleteOutline, { vm.prefs.removeCollection(name) })
                            }
                        }
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                            itemsIndexed(titles, key = { _, t -> t.id }) { i, t ->
                                TitleCard(
                                    vm.apps, t, { vm.openSaved(t) },
                                    onLongClick = { vm.show(titleMenu(vm, t)) },
                                    focusRequester = if (i == 0 && name == firstCollection) first else null,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

fun titleMenu(vm: LauncherViewModel, t: SavedTitle): Overlay.Menu {
    val app = vm.apps.find(t.pkg)
    return Overlay.Menu(
        title = t.title,
        subtitle = app?.label,
        items = listOf(
            MenuItem(if (app != null) "Open in ${app.label}" else "Search for it", Icons.Rounded.PlayArrow) { vm.openSaved(t) },
            MenuItem("Move to collection", Icons.Rounded.DriveFileMove) {
                vm.show(Overlay.Menu("Move to", "“${t.title}”", vm.prefs.collections.map { c ->
                    MenuItem(c, Icons.Rounded.Bookmarks, if (c == t.collection) "Current" else null) { vm.prefs.moveTitle(t.id, c) }
                }))
            },
            MenuItem("Remove", Icons.Rounded.DeleteOutline) { vm.prefs.removeTitle(t.id) },
        ),
    )
}

// ---------------------------------------------------------------- Search

@Composable
fun SearchScreen(vm: LauncherViewModel) {
    val query = vm.searchQuery
    val q = query.trim()
    val fieldFocus = rememberEntryFocus()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val apps = vm.apps.apps
    val appHits = if (q.isEmpty()) emptyList() else apps.filter { it.label.contains(q, ignoreCase = true) }
    val titleHits = if (q.isEmpty()) emptyList() else vm.prefs.saved.filter { it.title.contains(q, ignoreCase = true) }
    val searchable = (vm.dockApps.filter { it.searchable } + apps.filter { it.searchable }).distinctBy { it.pkg }

    ScreenScaffold("Search", vm.prefs.appearance.clock24) {
        SearchField(query, { vm.searchQuery = it }, fieldFocus) {
            keyboard?.hide()
            focusManager.moveFocus(FocusDirection.Down)
        }
        Spacer(Modifier.height(26.dp))
        if (q.isEmpty()) {
            Txt(
                "Find apps and saved titles, or send a search straight into an app.",
                Type.Body, color = Ink.Secondary, maxLines = 2,
            )
            return@ScreenScaffold
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 48.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
            if (searchable.isNotEmpty()) item(key = "in") {
                Column {
                    SectionTitle("Search “$q” in")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                        items(searchable, key = { it.pkg }) { app ->
                            AppChip(vm, app) { vm.searchIn(app, q) }
                        }
                    }
                }
            }
            if (appHits.isNotEmpty()) item(key = "apps") { AppRow("Apps", appHits, vm, null) }
            if (titleHits.isNotEmpty()) item(key = "titles") {
                Column {
                    SectionTitle("My Space")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                        items(titleHits, key = { it.id }) { t ->
                            TitleCard(vm.apps, t, { vm.openSaved(t) }, onLongClick = { vm.show(titleMenu(vm, t)) })
                        }
                    }
                }
            }
            item(key = "save") {
                Row { GlassPillButton("Save “$q” to My Space", Icons.Rounded.BookmarkAdd, { vm.startSaveTitle(q) }) }
            }
        }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, focus: FocusRequester, onSearch: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .widthIn(max = 620.dp)
            .fillMaxWidth()
            .glass(PillShape, strength = 1.4f)
            .background(if (focused) Color.White.copy(alpha = 0.10f) else Color.Transparent, PillShape)
            .padding(horizontal = 22.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ico(Icons.Rounded.Search, size = 22.dp, tint = if (focused) Ink.Primary else Ink.Secondary)
        Spacer(Modifier.width(14.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Txt("Apps, saved titles, or anything to search for", Type.Body, color = Ink.Tertiary)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = Type.Body.copy(shadow = null),
                cursorBrush = SolidColor(Ink.Primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }, onDone = { onSearch() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus).onFocusChanged { focused = it.isFocused },
            )
        }
    }
}

@Composable
private fun AppChip(vm: LauncherViewModel, app: AppEntry, onClick: () -> Unit) {
    FocusSurface(onClick, PillShape, focusedScale = 1.06f, lift = 14.dp, ring = false) { focused ->
        Row(
            Modifier.glass(PillShape).background(if (focused) Ink.FocusFill else Color.Transparent)
                .padding(start = 6.dp, end = 18.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppArtwork(vm.apps, app, Modifier.size(56.dp, 32.dp).glass(PillShape).let { it })
            Spacer(Modifier.width(12.dp))
            Txt(app.label, Type.Label.copy(shadow = null), color = if (focused) Ink.OnFocus else Ink.Primary)
        }
    }
}

// ---------------------------------------------------------------- Wallpapers

@Composable
fun WallpapersScreen(vm: LauncherViewModel) {
    val current = vm.prefs.appearance.wallpaper
    val startIndex = Wallpapers.indexOfFirst { it.id == current }.coerceAtLeast(0)
    val first = rememberEntryFocus()
    ScreenScaffold("Wallpapers", vm.prefs.appearance.clock24, subtitle = "Scenic photography from Unsplash") {
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            contentPadding = PaddingValues(top = 10.dp, bottom = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(GridGapH),
            verticalArrangement = Arrangement.spacedBy(GridGapV),
        ) {
            itemsIndexed(Wallpapers, key = { _, w -> w.id }) { i, wp ->
                val thumb = rememberThumbnail(wp.res)
                var focused by remember { mutableStateOf(false) }
                Column {
                    FocusSurface(
                        onClick = { vm.prefs.updateAppearance { it.copy(wallpaper = wp.id) } },
                        shape = TileShape,
                        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                        focusRequester = if (i == startIndex) first else null,
                        onFocusChange = { focused = it },
                    ) {
                        if (thumb != null) Image(thumb, wp.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        if (wp.id == current) {
                            Box(
                                Modifier.align(Alignment.TopEnd).padding(8.dp).size(26.dp).background(Ink.FocusFill, PillShape),
                                contentAlignment = Alignment.Center,
                            ) { Ico(Icons.Rounded.Check, size = 16.dp, tint = Ink.OnFocus) }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Txt(wp.name, Type.Caption.copy(fontWeight = Type.Label.fontWeight), Modifier.fillMaxWidth(),
                        color = if (focused || wp.id == current) Ink.Primary else Ink.Secondary, align = TextAlign.Center)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Appearance

@Composable
fun AppearanceScreen(vm: LauncherViewModel) {
    val a = vm.prefs.appearance
    val first = rememberEntryFocus()
    fun set(change: (dev.panev.tvhome.data.Appearance) -> dev.panev.tvhome.data.Appearance) = vm.prefs.updateAppearance(change)

    ScreenScaffold("Appearance", a.clock24, subtitle = "Changes apply instantly") {
        LazyColumn(
            Modifier.widthIn(max = 640.dp).padding(bottom = 36.dp).glass(PanelShape, strength = 1.3f),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item {
                GlassRow(Icons.Rounded.BlurOn, "Glass", {
                    set { it.copy(glass = GlassStyle.entries[(it.glass.ordinal + 1) % GlassStyle.entries.size]) }
                }, subtitle = "How much the dock and panels tint the scenery", value = a.glass.label, focusRequester = first)
            }
            item {
                GlassRow(Icons.Rounded.Gradient, "Wallpaper shading", { set { it.copy(strongShading = !it.strongShading) } },
                    subtitle = "Extra shade at the top and bottom for readability", value = if (a.strongShading) "Stronger" else "Subtle")
            }
            item {
                GlassRow(Icons.Rounded.Album, "Album art backdrop", { set { it.copy(albumBackdrop = !it.albumBackdrop) } },
                    subtitle = "While music plays, Home takes on the album's colours", value = if (a.albumBackdrop) "On" else "Off")
            }
            item {
                GlassRow(Icons.Rounded.Schedule, "Clock", { set { it.copy(clock24 = !it.clock24) } },
                    value = if (a.clock24) "24-hour" else "12-hour")
            }
            item {
                GlassRow(Icons.Rounded.WbSunny, "Weather", { set { it.copy(showWeather = !it.showWeather) } },
                    value = if (a.showWeather) "Shown" else "Hidden")
            }
            item {
                GlassRow(Icons.Rounded.Place, "Weather location", {
                    vm.show(Overlay.Prompt("Weather location", "City — leave empty to detect automatically", a.weatherCity.orEmpty(), "Save") { city ->
                        set { it.copy(weatherCity = city.trim().ifBlank { null }) }
                        vm.refreshWeather()
                    })
                }, value = a.weatherCity ?: "Automatic")
            }
            item {
                GlassRow(Icons.Rounded.Thermostat, "Temperature", { set { it.copy(fahrenheit = !it.fahrenheit) } },
                    value = if (a.fahrenheit) "°F" else "°C")
            }
            item {
                GlassRow(Icons.Rounded.Animation, "Motion", { set { it.copy(reducedMotion = !it.reducedMotion) } },
                    subtitle = "Reduced keeps transitions short and still", value = if (a.reducedMotion) "Reduced" else "Fluid")
            }
            item {
                GlassRow(
                    Icons.Rounded.MusicNote, "Now Playing access", { if (!vm.media.hasAccess) vm.apps.openNotificationAccess() },
                    subtitle = if (vm.media.hasAccess) "Music from any app shows on Home"
                    else "Allow notification access for Personal TV Home to show music",
                    value = if (vm.media.hasAccess) "Allowed" else "Off",
                )
            }
        }
    }
}
