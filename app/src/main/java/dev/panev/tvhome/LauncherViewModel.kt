package dev.panev.tvhome

import android.app.Application
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.panev.tvhome.data.AppEntry
import dev.panev.tvhome.data.AppRepository
import dev.panev.tvhome.data.MediaRepository
import dev.panev.tvhome.data.Prefs
import dev.panev.tvhome.data.SavedTitle
import dev.panev.tvhome.data.Weather
import dev.panev.tvhome.data.WeatherRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class Screen { Home, Discover, MySpace, Apps, Search, Wallpapers, Appearance, Player }

data class MenuItem(val label: String, val icon: ImageVector, val detail: String? = null, val action: () -> Unit)

sealed interface Overlay {
    data class Menu(val title: String, val subtitle: String? = null, val items: List<MenuItem>) : Overlay
    data class Prompt(
        val title: String,
        val hint: String,
        val initial: String = "",
        val confirm: String = "Done",
        val onDone: (String) -> Unit,
    ) : Overlay
}

class LauncherViewModel(app: Application) : AndroidViewModel(app) {
    val prefs = Prefs(app)
    val apps = AppRepository(app)
    val media = MediaRepository(app, viewModelScope)
    private val weatherRepo = WeatherRepository()

    private val stack = mutableStateListOf(Screen.Home)
    val screen: Screen get() = stack.last()

    var settingsOpen by mutableStateOf(false)
    var overlay by mutableStateOf<Overlay?>(null)
        private set
    var weather by mutableStateOf<Weather?>(null)
        private set
    var searchQuery by mutableStateOf("")

    /** Which dock tile had focus, so coming home lands where you left off. */
    var dockFocus by mutableIntStateOf(0)

    /** Bumped on every Home press so Home can pull focus back to the dock. */
    var homeSignal by mutableIntStateOf(0)
        private set

    private var weatherJob: Job? = null

    val dockApps: List<AppEntry>
        get() {
            val byPkg = apps.apps.associateBy { it.pkg }
            return prefs.favorites.orEmpty().mapNotNull { byPkg[it] }
        }

    init {
        viewModelScope.launch {
            apps.refresh()
            if (prefs.favorites == null) prefs.replaceFavorites(defaultFavorites())
        }
        refreshWeather()
    }

    fun onResume() {
        media.start()
        viewModelScope.launch { apps.refresh() }
    }

    fun onPackagesChanged() {
        viewModelScope.launch { apps.refresh() }
    }

    fun navigate(to: Screen) {
        settingsOpen = false
        if (to == Screen.Home) return goHome()
        stack.remove(to)
        stack.add(to)
    }

    fun back(): Boolean = when {
        overlay != null -> { dismissOverlay(); true }
        settingsOpen -> { closeSettings(); true }
        stack.size > 1 -> { stack.removeAt(stack.lastIndex); true }
        else -> false
    }

    fun goHome() {
        overlay = null
        settingsOpen = false
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
        homeSignal++
    }

    fun show(o: Overlay) { overlay = o }
    fun dismissOverlay() {
        overlay = null
        refocusHome()
    }

    fun closeSettings() {
        settingsOpen = false
        refocusHome()
    }

    /** Panels and dialogs take focus away from Home; give it back to the dock when they close. */
    private fun refocusHome() {
        if (screen == Screen.Home) homeSignal++
    }

    fun launch(app: AppEntry) {
        if (apps.launch(app)) prefs.recordLaunch(app.pkg)
    }

    /** Moves a dock tile and keeps focus on it, rather than on whatever slid into its old slot. */
    fun moveDockApp(pkg: String, by: Int) {
        prefs.moveFavorite(pkg, by)
        prefs.favorites.orEmpty().indexOf(pkg).takeIf { it >= 0 }?.let { dockFocus = it }
    }

    fun searchIn(app: AppEntry, query: String) {
        if (apps.launchSearch(app, query)) prefs.recordLaunch(app.pkg)
    }

    fun openSaved(t: SavedTitle) {
        val app = apps.find(t.pkg)
        if (app != null) {
            searchIn(app, t.title)
        } else {
            searchQuery = t.title
            navigate(Screen.Search)
        }
    }

    fun refreshWeather() {
        weatherJob?.cancel()
        weatherJob = viewModelScope.launch {
            while (true) {
                weatherRepo.fetch(prefs.appearance.weatherCity)?.let { weather = it }
                delay(if (weather == null) 2 * 60_000L else 30 * 60_000L)
            }
        }
    }

    // ---- Save-a-title flow: name → which app opens it → which collection. ----

    fun startSaveTitle(prefill: String = "") {
        show(Overlay.Prompt("Save a title", "Film or show, e.g. Severance", prefill, "Next") { title ->
            if (title.isNotBlank()) chooseAppFor(title.trim())
        })
    }

    private fun chooseAppFor(title: String) {
        val candidates = (dockApps + apps.apps.filter { it.searchable }).distinctBy { it.pkg }
        val items = candidates.take(9).map { app ->
            MenuItem(app.label, Icons.Rounded.Tv, if (app.searchable) "Opens straight to the title" else "Opens the app") {
                chooseCollectionFor(title, app.pkg)
            }
        } + MenuItem("Decide when I open it", Icons.Rounded.Search, "Opens Search") { chooseCollectionFor(title, null) }
        show(Overlay.Menu("Where do you watch it?", "“$title”", items))
    }

    private fun chooseCollectionFor(title: String, pkg: String?) {
        val items = prefs.collections.map { c ->
            MenuItem(c, Icons.Rounded.Bookmarks) { prefs.saveTitle(title, pkg, c) }
        } + MenuItem("New collection…", Icons.Rounded.Add) {
            show(Overlay.Prompt("New collection", "e.g. Weekend films", confirm = "Save") { name ->
                if (name.isNotBlank()) prefs.saveTitle(title, pkg, name)
            })
        }
        show(Overlay.Menu("Add to", "“$title”", items))
    }

    private fun defaultFavorites(): List<String> {
        val preferred = listOf(
            "com.google.android.youtube.tv", "app.smarttube.fdroid", "app.smarttube.stable",
            "com.netflix.ninja", "com.amazon.amazonvideo.livingroom", "com.disney.disneyplus",
            "com.spotify.tv.android", "com.plexapp.android", "org.xbmc.kodi",
            "com.apple.atve.androidtv.appletv", "com.google.android.youtube.tvmusic",
        )
        val installed = apps.apps.map { it.pkg }.toSet()
        val picks = preferred.filter { it in installed }.toMutableList()
        apps.apps.map { it.pkg }
            .filterNot { it in picks || it.startsWith("com.sony.dtv") || it.startsWith("com.android") }
            .forEach { if (picks.size < 6) picks += it }
        return picks
    }
}
