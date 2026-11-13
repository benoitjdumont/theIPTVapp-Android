package com.studiogyro.theiptvapp

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import java.util.UUID

const val GROUP_FAV = "\u0000fav"

/** Splits "FR | SPORTS", "|FR| SPORTS" or "FR| SPORTS" into ("FR", "SPORTS"); a name without a bar has no prefix. */
fun splitGroup(g: String): Pair<String, String?> {
    val t = g.trim().trimStart('|').trim()
    val i = t.indexOf('|')
    if (i <= 0) return g.trim() to null
    val prefix = t.substring(0, i).trim()
    val rest = t.substring(i + 1).trim().trimStart('|').trim()
    if (prefix.isEmpty() || rest.isEmpty()) return g.trim() to null
    return prefix to rest
}

/** One catalogue (live, movies or series) with its two-level category menu. */
class Section {
    var items by mutableStateOf<List<Channel>>(emptyList()); private set
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var loaded = false

    /** first level: null = all, GROUP_FAV = favourites, otherwise a prefix (or a plain group name) */
    var top by mutableStateOf<String?>(null); private set
    /** second level: null = whole prefix, otherwise a full group name */
    var sub by mutableStateOf<String?>(null); private set
    var tops by mutableStateOf<List<String>>(emptyList()); private set
    private var subsOf: Map<String, List<Pair<String, String>>> = emptyMap()
    private var prefixOf: Map<String, String> = emptyMap()

    fun replaceItems(l: List<Channel>) {
        items = l
        top = null
        sub = null
        val prefixes = LinkedHashMap<String, Boolean>()
        val subs = LinkedHashMap<String, LinkedHashMap<String, String>>()
        val pOf = HashMap<String, String>()
        for (g in l.map { it.group }.filter { it.isNotBlank() }.distinct()) {
            val (p, label) = splitGroup(g)
            pOf[g] = p
            prefixes[p] = true
            if (label != null) subs.getOrPut(p) { LinkedHashMap() }[g] = label
        }
        tops = prefixes.keys.toList()
        prefixOf = pOf
        subsOf = subs.mapValues { e -> e.value.entries.map { it.key to it.value } }
    }

    fun subs(): List<Pair<String, String>> = top?.let { subsOf[it] } ?: emptyList()
    fun pickTop(t: String?) { top = t; sub = null }
    fun pickSub(g: String?) { sub = g }

    fun visible(query: String, isFav: (Channel) -> Boolean): List<Channel> {
        val q = query.trim().lowercase()
        val t = top
        val sb = sub
        return items.filter { c ->
            val inGroup = when (t) {
                null -> true
                GROUP_FAV -> isFav(c)
                else -> (prefixOf[c.group] ?: c.group.trim()) == t && (sb == null || c.group == sb)
            }
            inGroup && (q.isEmpty() || c.name.lowercase().contains(q))
        }
    }
}

class MainVm(app: Application) : AndroidViewModel(app) {
    private val store = Store(app)

    var sources by mutableStateOf(store.sources()); private set
    var activeId by mutableStateOf(store.activeId() ?: store.sources().firstOrNull()?.id); private set
    var favs by mutableStateOf(store.favorites()); private set
    var query by mutableStateOf("")

    val live = Section()
    val movies = Section()
    val series = Section()

    /** 0 = live, 1 = movies, 2 = series */
    var tab by mutableStateOf(0); private set

    val epg = mutableStateMapOf<String, Epg>()

    var playlist by mutableStateOf<List<Channel>>(emptyList()); private set
    var playIndex by mutableStateOf(-1); private set
    val current: Channel? get() = playlist.getOrNull(playIndex)

    var seriesOpen by mutableStateOf<Channel?>(null); private set
    var seriesData by mutableStateOf<SeriesData?>(null); private set

    private val activeSource: Source? get() = sources.firstOrNull { it.id == activeId }

    init {
        reload()
    }

    fun section(): Section = when (tab) { 0 -> live; 1 -> movies; else -> series }

    fun selectTab(i: Int) {
        tab = i
        query = ""
        ensureLoaded()
    }

    fun reload() {
        epg.clear()
        for (s in listOf(live, movies, series)) { s.replaceItems(emptyList()); s.loaded = false; s.error = null; s.loading = false }
        seriesOpen = null
        ensureLoaded()
    }

    private fun ensureLoaded() {
        val sec = section()
        if (sec.loaded || sec.loading) return
        val s = activeSource ?: return
        if (tab != 0 && s.type != SourceType.XTREAM) {
            sec.error = "Films et séries : disponible avec un compte Xtream."
            return
        }
        sec.loading = true
        sec.error = null
        val which = tab
        viewModelScope.launch {
            try {
                sec.replaceItems(
                    when (which) {
                        0 -> Repo.load(s)
                        1 -> Repo.loadVod(s)
                        else -> Repo.loadSeries(s)
                    },
                )
                sec.loaded = true
            } catch (e: Exception) {
                sec.error = e.message ?: e.toString()
                sec.replaceItems(emptyList())
            }
            sec.loading = false
        }
    }

    fun addSource(s: Source) {
        sources = sources + s
        store.saveSources(sources)
        select(s.id)
    }

    fun deleteSource(id: String) {
        sources = sources.filterNot { it.id == id }
        store.saveSources(sources)
        if (activeId == id) select(sources.firstOrNull()?.id)
    }

    fun select(id: String?) {
        activeId = id
        store.saveActive(id)
        reload()
    }

    fun newId(): String = UUID.randomUUID().toString()

    fun toggleFav(c: Channel) {
        val key = favKey(c)
        favs = if (key in favs) favs - key else favs + key
        store.saveFavorites(favs)
    }

    fun isFav(c: Channel) = favKey(c) in favs

    private fun favKey(c: Channel) = "${activeId}|${c.id}|${c.name}"

    fun visible(): List<Channel> = section().visible(query) { isFav(it) }

    /** Asks for the guide of a channel once; refreshed when the programme that was on has ended. */
    fun loadEpg(c: Channel) {
        if (c.sid.isEmpty() || c.kind != "live") return
        val cached = epg[c.id]
        if (cached != null && System.currentTimeMillis() < cached.until) return
        val s = activeSource ?: return
        epg[c.id] = Epg(cached?.now, cached?.next, System.currentTimeMillis() + 60_000)
        viewModelScope.launch { epg[c.id] = Repo.shortEpg(s, c.sid) }
    }

    fun openSeries(c: Channel) {
        val s = activeSource ?: return
        seriesOpen = c
        seriesData = null
        viewModelScope.launch {
            seriesData = runCatching { Repo.seriesInfo(s, c.sid) }.getOrNull() ?: SeriesData("", emptyMap())
        }
    }

    fun closeSeries() {
        seriesOpen = null
        seriesData = null
    }

    fun play(list: List<Channel>, c: Channel) {
        playlist = list
        playIndex = list.indexOf(c).coerceAtLeast(0)
        store.saveLast(c.id)
    }

    fun step(delta: Int) {
        if (playlist.isEmpty()) return
        playIndex = (playIndex + delta + playlist.size) % playlist.size
        current?.let { store.saveLast(it.id) }
    }

    fun stop() {
        playIndex = -1
    }
}
