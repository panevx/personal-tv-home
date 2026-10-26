package dev.panev.tvhome.data

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class GlassStyle(val label: String, val tint: Float, val frost: Float) {
    Clear("Clear", 0.06f, 0.03f),
    Balanced("Balanced", 0.14f, 0.06f),
    Frosted("Frosted", 0.22f, 0.11f),
}

data class Appearance(
    val wallpaper: String = "fjord",
    val glass: GlassStyle = GlassStyle.Balanced,
    val clock24: Boolean = true,
    val showWeather: Boolean = true,
    val fahrenheit: Boolean = false,
    val weatherCity: String? = null,
    val reducedMotion: Boolean = false,
    val albumBackdrop: Boolean = true,
    val strongShading: Boolean = false,
)

data class SavedTitle(
    val id: String,
    val title: String,
    val pkg: String?,
    val collection: String,
    val addedAt: Long,
)

/** Everything the launcher remembers, stored in one small SharedPreferences file as JSON. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("tvhome", Context.MODE_PRIVATE)
    private val systemClock24 = DateFormat.is24HourFormat(context)

    var appearance by mutableStateOf(loadAppearance())
        private set

    /** Package names on the dock, in order. Null until first-run defaults are chosen. */
    var favorites by mutableStateOf(loadStringList("favorites"))
        private set

    var saved by mutableStateOf(loadSaved())
        private set

    var collections by mutableStateOf(loadStringList("collections") ?: listOf(DEFAULT_COLLECTION))
        private set

    var recents by mutableStateOf(loadRecents())
        private set

    fun updateAppearance(change: (Appearance) -> Appearance) {
        appearance = change(appearance)
        val a = appearance
        sp.edit().putString(
            "appearance",
            JSONObject()
                .put("wallpaper", a.wallpaper)
                .put("glass", a.glass.name)
                .put("clock24", a.clock24)
                .put("showWeather", a.showWeather)
                .put("fahrenheit", a.fahrenheit)
                .put("weatherCity", a.weatherCity ?: JSONObject.NULL)
                .put("reducedMotion", a.reducedMotion)
                .put("albumBackdrop", a.albumBackdrop)
                .put("strongShading", a.strongShading)
                .toString()
        ).apply()
    }

    fun replaceFavorites(list: List<String>) {
        favorites = list.distinct()
        saveStringList("favorites", favorites!!)
    }

    fun toggleFavorite(pkg: String) {
        val current = favorites.orEmpty()
        replaceFavorites(if (pkg in current) current - pkg else current + pkg)
    }

    fun moveFavorite(pkg: String, by: Int) {
        val list = favorites.orEmpty().toMutableList()
        val from = list.indexOf(pkg)
        if (from < 0) return
        val to = (from + by).coerceIn(0, list.lastIndex)
        list.add(to, list.removeAt(from))
        replaceFavorites(list)
    }

    fun saveTitle(title: String, pkg: String?, collection: String) {
        addCollection(collection)
        saved = listOf(SavedTitle(UUID.randomUUID().toString(), title.trim(), pkg, collection, System.currentTimeMillis())) + saved
        persistSaved()
    }

    fun moveTitle(id: String, collection: String) {
        addCollection(collection)
        saved = saved.map { if (it.id == id) it.copy(collection = collection) else it }
        persistSaved()
    }

    fun removeTitle(id: String) {
        saved = saved.filterNot { it.id == id }
        persistSaved()
    }

    fun addCollection(name: String) {
        val clean = name.trim()
        if (clean.isEmpty() || clean in collections) return
        collections = collections + clean
        saveStringList("collections", collections)
    }

    fun removeCollection(name: String) {
        if (name == DEFAULT_COLLECTION) return
        saved = saved.map { if (it.collection == name) it.copy(collection = DEFAULT_COLLECTION) else it }
        persistSaved()
        collections = collections - name
        saveStringList("collections", collections)
    }

    fun recordLaunch(pkg: String) {
        recents = recents + (pkg to System.currentTimeMillis())
        val json = JSONObject()
        recents.forEach { (k, v) -> json.put(k, v) }
        sp.edit().putString("recents", json.toString()).apply()
    }

    private fun loadAppearance(): Appearance {
        val raw = sp.getString("appearance", null) ?: return Appearance(clock24 = systemClock24)
        return runCatching {
            val o = JSONObject(raw)
            Appearance(
                wallpaper = o.optString("wallpaper", "fjord"),
                glass = runCatching { GlassStyle.valueOf(o.optString("glass")) }.getOrDefault(GlassStyle.Balanced),
                clock24 = o.optBoolean("clock24", systemClock24),
                showWeather = o.optBoolean("showWeather", true),
                fahrenheit = o.optBoolean("fahrenheit", false),
                weatherCity = if (o.isNull("weatherCity")) null else o.optString("weatherCity").ifBlank { null },
                reducedMotion = o.optBoolean("reducedMotion", false),
                albumBackdrop = o.optBoolean("albumBackdrop", true),
                strongShading = o.optBoolean("strongShading", false),
            )
        }.getOrDefault(Appearance(clock24 = systemClock24))
    }

    private fun loadStringList(key: String): List<String>? {
        val raw = sp.getString(key, null) ?: return null
        return runCatching {
            val arr = JSONArray(raw)
            List(arr.length()) { arr.getString(it) }
        }.getOrNull()
    }

    private fun saveStringList(key: String, list: List<String>) {
        sp.edit().putString(key, JSONArray(list).toString()).apply()
    }

    private fun loadSaved(): List<SavedTitle> {
        val raw = sp.getString("saved", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            List(arr.length()) {
                val o = arr.getJSONObject(it)
                SavedTitle(
                    id = o.getString("id"),
                    title = o.getString("title"),
                    pkg = if (o.isNull("pkg")) null else o.optString("pkg"),
                    collection = o.optString("collection", DEFAULT_COLLECTION),
                    addedAt = o.optLong("addedAt"),
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun persistSaved() {
        val arr = JSONArray()
        saved.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("title", it.title)
                    .put("pkg", it.pkg ?: JSONObject.NULL)
                    .put("collection", it.collection)
                    .put("addedAt", it.addedAt)
            )
        }
        sp.edit().putString("saved", arr.toString()).apply()
    }

    private fun loadRecents(): Map<String, Long> {
        val raw = sp.getString("recents", null) ?: return emptyMap()
        return runCatching {
            val o = JSONObject(raw)
            o.keys().asSequence().associateWith { o.getLong(it) }
        }.getOrDefault(emptyMap())
    }

    companion object {
        const val DEFAULT_COLLECTION = "Watchlist"
    }
}
