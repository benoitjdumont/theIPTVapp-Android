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

class MainVm(app: Application) : AndroidViewModel(app) {
    private val store = Store(app)

    var sources by mutableStateOf(store.sources()); private set
    var activeId by mutableStateOf(store.activeId() ?: store.sources().firstOrNull()?.id); private set
    var channels by mutableStateOf<List<Channel>>(emptyList()); private set
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var favs by mutableStateOf(store.favorites()); private set

    /** first level: null = all channels, GROUP_FAV = favourites, otherwise a prefix (or a plain group name) */
    var top by mutableStateOf<String?>(null); private set
    /** second level: null = whole prefix, otherwise a full group name */
    var sub by mutableStateOf<String?>(null); private set
    var query by mutableStateOf("")

    /** first-level entries, in list order */
    var tops by mutableStateOf<List<String>>(emptyList()); private set
    private var subsOf: Map<String, List<Pair<String, String>>> = emptyMap()
    private var prefixOf: Map<String, String> = emptyMap()

    val epg = mutableStateMapOf<String, Epg>()

    var playlist by mutableStateOf<List<Channel>>(emptyList()); private set
    var playIndex by mutableStateOf(-1); private set

    val current: Channel? get() = playlist.getOrNull(playIndex)

    init {
        reload()
    }

    fun reload() {
        val s = sources.firstOrNull { it.id == activeId }
        if (s == null) { channels = emptyList(); buildGroups(); return }
        loading = true
        error = null
        epg.clear()
        viewModelScope.launch {
            try {
                channels = Repo.load(s)
                top = null; sub = null
            } catch (e: Exception) {
                error = e.message ?: e.toString()
                channels = emptyList()
            }
            buildGroups()
            loading = false
        }
    }

    private fun buildGroups() {
        val prefixes = LinkedHashMap<String, Boolean>()
        val subs = LinkedHashMap<String, LinkedHashMap<String, String>>()
        val pOf = HashMap<String, String>()
        for (g in channels.map { it.group }.filter { it.isNotBlank() }.distinct()) {
            val (p, label) = splitGroup(g)
            pOf[g] = p
            prefixes[p] = true
            if (label != null) subs.getOrPut(p) { LinkedHashMap() }[g] = label
        }
        tops = prefixes.keys.toList()
        prefixOf = pOf
        subsOf = subs.mapValues { e -> e.value.entries.map { it.key to it.value } }
    }

    /** sub-groups of the selected first-level entry (empty when it has none) */
    fun subs(): List<Pair<String, String>> = top?.let { subsOf[it] } ?: emptyList()

    fun pickTop(t: String?) { top = t; sub = null }
    fun pickSub(g: String?) { sub = g }

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

    fun visible(): List<Channel> {
        val q = query.trim().lowercase()
        val t = top
        val sb = sub
        return channels.filter { c ->
            val inGroup = when (t) {
                null -> true
                GROUP_FAV -> isFav(c)
                else -> (prefixOf[c.group] ?: c.group.trim()) == t && (sb == null || c.group == sb)
            }
            inGroup && (q.isEmpty() || c.name.lowercase().contains(q))
        }
    }

    /** Asks for the guide of a channel once; refreshed when the programme that was on has ended. */
    fun loadEpg(c: Channel) {
        if (c.sid.isEmpty()) return
        val cached = epg[c.id]
        if (cached != null && System.currentTimeMillis() < cached.until) return
        val s = sources.firstOrNull { it.id == activeId } ?: return
        // reserve the slot so a fast scroll does not fire the same request many times
        epg[c.id] = Epg(cached?.now, cached?.next, System.currentTimeMillis() + 60_000)
        viewModelScope.launch { epg[c.id] = Repo.shortEpg(s, c.sid) }
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
