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

    // ---- mise à jour de l'application
    var update by mutableStateOf<Updater.Update?>(null); private set
    var updateInfo by mutableStateOf(""); private set
    var updateProgress by mutableStateOf(-1); private set
    var updateDismissed by mutableStateOf(false)

    fun checkUpdate(silent: Boolean) {
        if (!silent) updateInfo = "Vérification…"
        viewModelScope.launch {
            try {
                val u = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Updater.check() }
                update = u
                updateDismissed = false
                if (!silent) updateInfo = if (u == null) "Tu as la dernière version (${BuildConfig.VERSION_NAME})." else "Nouvelle version disponible."
            } catch (e: Exception) {
                if (!silent) updateInfo = "Vérification impossible : ${e.message}"
            }
        }
    }

    fun installUpdate(ctx: android.content.Context) {
        val u = update ?: return
        updateProgress = 0
        viewModelScope.launch {
            try {
                val f = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    Updater.download(ctx, u.url) { updateProgress = it }
                }
                updateProgress = -1
                Updater.install(ctx, f)
            } catch (e: Exception) {
                updateProgress = -1
                updateInfo = "Téléchargement impossible : ${e.message}"
            }
        }
    }

    private val activeSource: Source? get() = sources.firstOrNull { it.id == activeId }

    init {
        reload()
        checkUpdate(silent = true)
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
                if (which == 0) afterLiveLoaded(s)
            } catch (e: Exception) {
                sec.error = e.message ?: e.toString()
                sec.replaceItems(emptyList())
            }
            sec.loading = false
        }
    }

    // ---- reprise de la dernière chaîne, guide XMLTV (listes M3U)
    var autoResume by mutableStateOf(store.autoResume()); private set
    private var resumed = false
    private var xmltv: Map<String, List<Prog>> = emptyMap()
    var xmltvReady by mutableStateOf(false); private set

    fun changeAutoResume(b: Boolean) { autoResume = b; store.saveAutoResume(b) }

    private fun afterLiveLoaded(s: Source) {
        if (!resumed) {
            resumed = true
            if (autoResume) {
                val id = store.lastLive()
                live.items.firstOrNull { it.id == id }?.let { c -> guard(c) { play(live.items, c) } }
            }
        }
        xmltvReady = false
        xmltv = emptyMap()
        val url = Repo.xmltvUrl
        if (s.type == SourceType.M3U && url.isNotBlank()) {
            val ids = live.items.map { it.epgId }.filter { it.isNotEmpty() }.toSet()
            if (ids.isNotEmpty()) viewModelScope.launch {
                try {
                    xmltv = Repo.loadXmltv(url, ids)
                    epg.clear()
                    xmltvReady = true
                } catch (_: Exception) { }
            }
        }
    }

    // ---- image dans l'image
    var inPip by mutableStateOf(false)

    // ---- enregistrements
    data class Rec(val name: String, val file: java.io.File, val startMs: Long)
    var recording by mutableStateOf<Rec?>(null); private set
    var recBytes by mutableStateOf(0L); private set
    var recVersion by mutableStateOf(0); private set
    var recMessage by mutableStateOf(""); private set
    private var recJob: kotlinx.coroutines.Job? = null

    fun startRecording(ctx: android.content.Context, c: Channel) {
        if (recording != null) return
        val safe = c.name.replace(Regex("[^A-Za-z0-9À-ÿ _-]"), "").trim().ifEmpty { "chaine" }.replace(' ', '_')
        val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmm", java.util.Locale.US).format(java.util.Date())
        val f = java.io.File(Recorder.dir(ctx), "${safe}_$stamp.ts")
        recording = Rec(c.name, f, System.currentTimeMillis())
        recBytes = 0
        recMessage = ""
        runCatching { androidx.core.content.ContextCompat.startForegroundService(ctx, android.content.Intent(ctx, RecordService::class.java)) }
        recJob = viewModelScope.launch {
            try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Recorder.record(c, f) { recBytes = it } }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // arrêt demandé : le fichier est conservé
            } catch (e: Exception) {
                recMessage = "Enregistrement arrêté : ${e.message}"
            } finally {
                ctx.stopService(android.content.Intent(ctx, RecordService::class.java))
                if (f.exists() && f.length() < 1024) f.delete()
                recording = null
                recVersion++
            }
        }
    }

    fun stopRecording() { recJob?.cancel() }

    fun deleteRecording(f: java.io.File) { f.delete(); recVersion++ }

    fun recordings(ctx: android.content.Context): List<java.io.File> =
        Recorder.dir(ctx).listFiles { x -> x.isFile && x.name.endsWith(".ts") }?.sortedByDescending { it.lastModified() } ?: emptyList()

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
        val l = section().visible(query) { isFav(it) }
        return if (hiddenCats.isEmpty()) l else l.filterNot { groupHidden(it) }
    }

    /** groupes masqués (par leur nom de premier niveau ou de groupe complet) */
    var hiddenCats by mutableStateOf(store.hiddenCats()); private set

    private fun groupHidden(c: Channel): Boolean {
        if (c.group.isBlank()) return false
        return c.group in hiddenCats || splitGroup(c.group).let { (p, l) -> p in hiddenCats || (l != null && l in hiddenCats) }
    }

    fun toggleHidden(name: String) {
        hiddenCats = if (name in hiddenCats) hiddenCats - name else hiddenCats + name
        store.saveHiddenCats(hiddenCats)
    }

    fun setHidden(names: Collection<String>, hide: Boolean) {
        hiddenCats = if (hide) hiddenCats + names else hiddenCats - names.toSet()
        store.saveHiddenCats(hiddenCats)
    }

    /** Asks for the guide of a channel once; refreshed when the programme that was on has ended. */
    fun loadEpg(c: Channel) {
        if (c.kind != "live") return
        if (c.sid.isEmpty()) {
            // liste M3U : guide XMLTV chargé en arrière-plan
            if (c.epgId.isEmpty() || !xmltvReady) return
            val list = xmltv[c.epgId] ?: return
            val nowMs = System.currentTimeMillis()
            val now = list.firstOrNull { it.start <= nowMs && it.stop > nowMs }
            val next = list.firstOrNull { it.start > nowMs }
            epg[c.id] = Epg(now, next, (now?.stop ?: (nowMs + 300_000)).coerceAtLeast(nowMs + 30_000))
            return
        }
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

    // ---- reprise de lecture
    fun savePos(id: String, ms: Long, durationMs: Long) {
        if (ms < 15_000 || (durationMs > 0 && ms > durationMs - 45_000)) store.clearPos(id) else store.savePos(id, ms)
    }
    fun pos(id: String): Long = store.pos(id)

    // ---- contrôle parental
    var pinHash by mutableStateOf(store.pinHash()); private set
    var keywords by mutableStateOf(store.keywords()); private set
    var unlocked by mutableStateOf(false); private set
    var pendingAction by mutableStateOf<(() -> Unit)?>(null); private set

    fun parentalOn() = pinHash != null && keywords.isNotEmpty()

    fun matchesKeyword(c: Channel): Boolean {
        val n = c.name.lowercase()
        val g = c.group.lowercase()
        return keywords.any { n.contains(it) || g.contains(it) }
    }

    /** categories (first or second level) locked by hand with a long press */
    var lockedCats by mutableStateOf(store.lockedCats()); private set

    private fun groupLockedByHand(c: Channel): Boolean {
        if (lockedCats.isEmpty() || c.group.isBlank()) return false
        return c.group in lockedCats || splitGroup(c.group).let { (p, l) -> p in lockedCats || (l != null && l in lockedCats) }
    }

    fun isLocked(c: Channel): Boolean = parentalOn() && !unlocked && (matchesKeyword(c) || groupLockedByHand(c))

    /** is a category entry locked? (its name matches a watched word, or it was locked by hand) */
    fun catLocked(label: String, full: String = label): Boolean {
        if (!parentalOn() || unlocked) return false
        val l = label.lowercase()
        val f = full.lowercase()
        return keywords.any { l.contains(it) || f.contains(it) } || label in lockedCats || full in lockedCats
    }

    fun guardCat(label: String, full: String = label, action: () -> Unit) {
        if (catLocked(label, full)) pendingAction = action else action()
    }

    /** long press on a category: lock or unlock it by hand (needs the parental code to be active) */
    fun toggleCatLock(label: String) {
        if (!parentalOn()) {
            android.widget.Toast.makeText(getApplication(), "Active d'abord le contrôle parental dans les réglages.", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        val doIt = {
            lockedCats = if (label in lockedCats) lockedCats - label else lockedCats + label
            store.saveLockedCats(lockedCats)
            android.widget.Toast.makeText(getApplication(), if (label in lockedCats) "« $label » verrouillée" else "« $label » déverrouillée", android.widget.Toast.LENGTH_SHORT).show()
        }
        if (unlocked) doIt() else pendingAction = doIt
    }

    /** how many items of the loaded lists match the watched words (shown in the settings) */
    fun watchedCount(): Int = if (!parentalOn()) 0 else (live.items + movies.items + series.items).count { matchesKeyword(it) || groupLockedByHand(it) }

    /** Runs the action now, or after the code has been entered if the item is locked. */
    fun guard(c: Channel, action: () -> Unit) {
        if (isLocked(c)) pendingAction = action else action()
    }

    fun tryPin(pin: String): Boolean {
        if (hashPin(pin) != pinHash) return false
        unlocked = true
        val a = pendingAction
        pendingAction = null
        a?.invoke()
        return true
    }

    fun cancelPin() { pendingAction = null }
    fun lockNow() { unlocked = false }

    fun setParental(pin: String, kws: List<String>) {
        pinHash = hashPin(pin)
        keywords = kws.map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        store.saveParental(pinHash, keywords)
        unlocked = false
    }

    fun disableParental() {
        pinHash = null
        keywords = emptyList()
        lockedCats = emptySet()
        store.saveLockedCats(emptySet())
        store.saveParental(null, emptyList())
        unlocked = false
    }

    private fun hashPin(p: String): String =
        java.security.MessageDigest.getInstance("SHA-256").digest(("theiptvapp:" + p).toByteArray()).joinToString("") { "%02x".format(it) }

    fun play(list: List<Channel>, c: Channel) {
        playlist = list
        playIndex = list.indexOf(c).coerceAtLeast(0)
        store.saveLast(c.id)
        if (c.kind == "live") store.saveLastLive(c.id)
    }

    fun step(delta: Int) {
        if (playlist.isEmpty()) return
        playIndex = (playIndex + delta + playlist.size) % playlist.size
        current?.let { store.saveLast(it.id); if (it.kind == "live") store.saveLastLive(it.id) }
    }

    fun stop() {
        playIndex = -1
    }
}
