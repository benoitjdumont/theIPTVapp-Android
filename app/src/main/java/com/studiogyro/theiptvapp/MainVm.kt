package com.studiogyro.theiptvapp

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import java.util.UUID

const val GROUP_FAV = "\u0000fav"

class MainVm(app: Application) : AndroidViewModel(app) {
    private val store = Store(app)

    var sources by mutableStateOf(store.sources()); private set
    var activeId by mutableStateOf(store.activeId() ?: store.sources().firstOrNull()?.id); private set
    var channels by mutableStateOf<List<Channel>>(emptyList()); private set
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var favs by mutableStateOf(store.favorites()); private set

    /** null = all channels, GROUP_FAV = favourites, otherwise a group name */
    var group by mutableStateOf<String?>(null)
    var query by mutableStateOf("")

    var playlist by mutableStateOf<List<Channel>>(emptyList()); private set
    var playIndex by mutableStateOf(-1); private set

    val current: Channel? get() = playlist.getOrNull(playIndex)

    init {
        reload()
    }

    fun reload() {
        val s = sources.firstOrNull { it.id == activeId }
        if (s == null) { channels = emptyList(); return }
        loading = true
        error = null
        viewModelScope.launch {
            try {
                channels = Repo.load(s)
                group = null
            } catch (e: Exception) {
                error = e.message ?: e.toString()
                channels = emptyList()
            }
            loading = false
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

    val groups: List<String>
        get() = channels.map { it.group }.filter { it.isNotBlank() }.distinct()

    fun visible(): List<Channel> {
        val q = query.trim().lowercase()
        return channels.filter { c ->
            val inGroup = when (group) {
                null -> true
                GROUP_FAV -> isFav(c)
                else -> c.group == group
            }
            inGroup && (q.isEmpty() || c.name.lowercase().contains(q))
        }
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
