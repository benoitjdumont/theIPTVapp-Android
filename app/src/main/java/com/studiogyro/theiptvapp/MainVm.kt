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
const val GROUP_HIST = "\u0000hist"

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

    fun visible(query: String, isFav: (Channel) -> Boolean, inHist: (Channel) -> Boolean = { false }): List<Channel> {
        val q = query.trim().lowercase()
        val t = top
        val sb = sub
        return items.filter { c ->
            val inGroup = when (t) {
                null -> true
                GROUP_FAV -> isFav(c)
                GROUP_HIST -> inHist(c)
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
        Repo.configure(store.userAgent(), store.proxy())
        viewModelScope.launch { while (true) { kotlinx.coroutines.delay(60_000); clock = System.currentTimeMillis() } }
        Accent = ACCENTS[store.accent().coerceIn(0, ACCENTS.lastIndex)].second
        UiAlpha = TRANSPS[store.transp().coerceIn(0, TRANSPS.lastIndex)].second
        reload()
        checkUpdate(silent = true)
        guideTimer()
    }

    fun section(): Section = when (tab) { 0 -> live; 1 -> movies; else -> series }

    /** Films / Séries cliquables : grisés seulement quand la liste est chargée et vide (Xtream : chargée en arrière-plan). */
    fun vodEnabled(which: Int): Boolean {
        if (tab == which) return true
        val sec = if (which == 1) movies else series
        return if (activeSource?.type == SourceType.XTREAM) !(sec.loaded && sec.items.isEmpty()) else sec.items.isNotEmpty()
    }

    /** incrémenté à chaque clic sur TV / Films / Séries : le focus passe alors dans la colonne des catégories */
    var tabTick by mutableStateOf(0); private set

    fun selectTab(i: Int) {
        tabTick++
        tab = i
        preview = null
        query = ""
        ensureLoaded()
    }

    fun reload() {
        epg.clear()
        for (s in listOf(live, movies, series)) { s.replaceItems(emptyList()); s.loaded = false; s.error = null; s.loading = false }
        seriesOpen = null
        ensureLoaded()
    }

    private fun ensureLoaded() = loadSection(tab, visible = true)

    /** Charge en arrière-plan films et séries pour que le premier clic soit instantané. */
    fun prefetchCatalogs() {
        val s = activeSource ?: return
        if (s.type != SourceType.XTREAM) return
        loadSection(1, visible = false)
        loadSection(2, visible = false)
    }

    private fun loadSection(which: Int, visible: Boolean) {
        val sec = when (which) { 0 -> live; 1 -> movies; else -> series }
        if (sec.loaded || sec.loading) return
        val s = activeSource ?: return
        if (which != 0 && s.type != SourceType.XTREAM) {
            if (visible) sec.error = "Films et séries : disponible avec un compte Xtream."
            return
        }
        sec.loading = true
        sec.error = null
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
                if (which == 0) { afterLiveLoaded(s); prefetchCatalogs() }
            } catch (e: Exception) {
                sec.error = e.message ?: e.toString()
                sec.replaceItems(emptyList())
            }
            sec.loading = false
        }
    }

    // ---- reprise de la dernière chaîne, guide XMLTV (listes M3U)
    var autoResume by mutableStateOf(store.autoResume()); private set
    var textSizeIdx by mutableStateOf(store.textSize().coerceIn(0, TEXT_SIZES.lastIndex)); private set
    fun changeTextSize(i: Int) { textSizeIdx = i; store.saveTextSize(i) }
    var transpIdx by mutableStateOf(store.transp().coerceIn(0, TRANSPS.lastIndex)); private set
    fun changeTransp(i: Int) { transpIdx = i; UiAlpha = TRANSPS[i].second; store.saveTransp(i) }
    /** heure courante, rafraîchie chaque minute pour la grille du guide */
    var clock by mutableStateOf(System.currentTimeMillis()); private set

    /** décalage du guide en minutes (0 = maintenant) */
    var guideShiftMin by mutableStateOf(0)
    fun shiftGuide(delta: Int) { guideShiftMin = if (delta == 0) 0 else (guideShiftMin + delta).coerceIn(-12 * 60, 12 * 60) }

    var past by mutableStateOf<List<Prog>?>(null)

    /** charge la liste des émissions passées d'une chaîne avec rattrapage (à la demande) */
    fun loadPast(c: Channel) {
        past = null
        val s = activeSource ?: return
        viewModelScope.launch {
            val e = runCatching { Repo.shortEpg(s, c.sid, true) }.getOrNull()
            val now = System.currentTimeMillis()
            past = e?.list?.filter { it.stop < now }?.sortedByDescending { it.start } ?: emptyList()
        }
    }

    /** revoir une émission passée d'une chaîne qui propose le rattrapage */
    fun playReplay(c: Channel, p: Prog) {
        val s = activeSource ?: return
        val ch = Channel(id = "rp${c.id}_${p.start}", name = "${c.name} · ${p.title}", url = Repo.timeshiftUrl(s, c.sid, p), kind = "movie")
        play(listOf(ch), ch)
    }

    var previewMode by mutableStateOf(store.previewMode()); private set
    fun changePreviewMode(b: Boolean) { previewMode = b; store.savePreviewMode(b); if (!b) preview = null }
    /** chaîne en aperçu sur l'accueil (premier clic) ; un second clic ouvre le plein écran */
    var preview by mutableStateOf<Channel?>(null)
    var accentIdx by mutableStateOf(store.accent().coerceIn(0, ACCENTS.lastIndex)); private set

    fun changeAccent(i: Int) { accentIdx = i; Accent = ACCENTS[i].second; store.saveAccent(i) }
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
        // mise à jour du guide : au démarrage, puis à chaque changement de liste (réglages EPG)
        val startup = !guideStarted
        guideStarted = true
        if (if (startup) store.epgOnStart() else store.epgOnList()) refreshGuide(auto = true)
    }

    // ---- apparence : guide TV, noms, image de fond
    var optVersion by mutableStateOf(0); private set
    fun opt(key: String, def: Boolean = true): Boolean = store.guideOpt(key, def)
    fun setOpt(key: String, b: Boolean) { store.saveGuideOpt(key, b); optVersion++ }
    var renames by mutableStateOf(store.renames()); private set
    fun displayName(c: Channel): String = renames[c.id] ?: c.name
    fun rename(c: Channel, name: String) {
        renames = if (name.isBlank()) renames - c.id else renames + (c.id to name.trim())
        store.saveRenames(renames)
    }
    var bgPath by mutableStateOf(store.bgPath()); private set
    var pickBackground: (() -> Unit)? = null
    fun setBackground(ctx: android.content.Context, uri: android.net.Uri) {
        val f = java.io.File(ctx.filesDir, "background.jpg")
        runCatching { ctx.contentResolver.openInputStream(uri)?.use { i -> f.outputStream().use { o -> i.copyTo(o) } } }
            .onSuccess { bgPath = f.absolutePath; store.saveBgPath(bgPath) }
    }
    fun clearBackground() { bgPath = null; store.saveBgPath(null) }

    // ---- préférences générales
    private var guideStarted = false
    var pipOnHome by mutableStateOf(store.pipOnHome()); private set
    fun changePipOnHome(b: Boolean) { pipOnHome = b; store.savePipOnHome(b) }
    var doubleBack by mutableStateOf(store.doubleBack()); private set
    fun changeDoubleBack(b: Boolean) { doubleBack = b; store.saveDoubleBack(b) }
    var bootStart by mutableStateOf(store.bootStart()); private set
    fun changeBootStart(b: Boolean) { bootStart = b; store.saveBootStart(b) }

    // ---- réseau
    var userAgent by mutableStateOf(store.userAgent()); private set
    var proxy by mutableStateOf(store.proxy()); private set
    fun saveNetwork(ua: String, px: String) {
        userAgent = ua.trim(); proxy = px.trim()
        store.saveUserAgent(userAgent); store.saveProxy(proxy)
        Repo.configure(userAgent, proxy)
        applyNetworkNote = "Réglages réseau enregistrés. Recharge la liste pour les utiliser."
    }
    var applyNetworkNote by mutableStateOf(""); private set

    // ---- guide (EPG)
    var epgDays by mutableStateOf(store.epgDays()); private set
    fun changeEpgDays(n: Int) { epgDays = n.coerceIn(1, 30); store.saveEpgDays(epgDays) }
    var epgDesc by mutableStateOf(store.epgDesc()); private set
    fun changeEpgDesc(b: Boolean) { epgDesc = b; store.saveEpgDesc(b) }
    var epgInterval by mutableStateOf(store.epgInterval()); private set
    fun changeEpgInterval(h: Int) { epgInterval = h.coerceIn(0, 168); store.saveEpgInterval(epgInterval) }
    var epgOnStart by mutableStateOf(store.epgOnStart()); private set
    fun changeEpgOnStart(b: Boolean) { epgOnStart = b; store.saveEpgOnStart(b) }
    var epgOnList by mutableStateOf(store.epgOnList()); private set
    fun changeEpgOnList(b: Boolean) { epgOnList = b; store.saveEpgOnList(b) }
    var epgStatus by mutableStateOf(store.epgStatus().ifEmpty { "Jamais mis à jour" }); private set
    var epgBusy by mutableStateOf(false); private set

    /** Télécharge le guide XMLTV de la liste active. auto = déclenché par le réglage (pas d'erreur affichée). */
    fun refreshGuide(auto: Boolean = false) {
        val url = Repo.xmltvUrl
        val s = activeSource
        if (epgBusy) return
        if (s == null || s.type != SourceType.M3U || url.isBlank()) {
            if (!auto) epgStatus = "Cette liste n'a pas de guide XMLTV."
            return
        }
        val ids = live.items.map { it.epgId }.filter { it.isNotEmpty() }.toSet()
        if (ids.isEmpty()) {
            if (!auto) epgStatus = "Aucune chaîne avec un identifiant de guide."
            return
        }
        epgBusy = true
        xmltvReady = false
        viewModelScope.launch {
            try {
                xmltv = Repo.loadXmltv(url, ids, store.epgDays(), store.epgDesc())
                epg.clear()
                xmltvReady = true
                val now = System.currentTimeMillis()
                epgStatus = "Mis à jour à ${hm(now)} · ${xmltv.size} chaîne(s)"
                store.saveEpgStatus(now, epgStatus)
            } catch (e: Exception) {
                epgStatus = "Échec de la mise à jour : ${e.message ?: "erreur"}"
                if (!auto) store.saveEpgStatus(System.currentTimeMillis(), epgStatus)
            } finally {
                epgBusy = false
            }
        }
    }

    fun clearGuide() {
        xmltv = emptyMap()
        xmltvReady = false
        epg.clear()
        store.saveEpgStatus(0L, "")
        epgStatus = "Guide effacé."
    }

    /** Mise à jour périodique selon l'intervalle choisi (0 = jamais). */
    private fun guideTimer() {
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(15 * 60_000L)
                val h = store.epgInterval()
                if (h > 0 && System.currentTimeMillis() - store.epgLast() >= h * 3_600_000L) refreshGuide(auto = true)
            }
        }
    }

    // ---- sauvegarde / restauration (les lanceurs de fichiers viennent de MainActivity)
    var createBackup: (() -> Unit)? = null
    var openBackup: (() -> Unit)? = null
    var backupMessage by mutableStateOf(""); private set

    fun writeBackup(ctx: android.content.Context, uri: android.net.Uri) {
        backupMessage = try {
            ctx.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(store.backupJson()) }
            "Sauvegarde enregistrée."
        } catch (e: Exception) { "Sauvegarde impossible : ${e.message}" }
    }

    fun readBackup(ctx: android.content.Context, uri: android.net.Uri) {
        val text = try { ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } } catch (e: Exception) { null }
        backupMessage = if (text != null && store.restoreJson(text)) {
            "Restauration terminée. Redémarre l'appli pour tout appliquer."
        } else "Fichier de sauvegarde invalide."
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

    /** films et séries ouverts récemment (le plus récent en premier) */
    var history by mutableStateOf(store.history()); private set

    fun addHistory(id: String) {
        history = (listOf(id) + history.filter { it != id }).take(40)
        store.saveHistory(history)
    }

    fun toggleFav(c: Channel) {
        val key = favKey(c)
        favs = if (key in favs) favs - key else favs + key
        store.saveFavorites(favs)
    }

    fun isFav(c: Channel) = favKey(c) in favs

    private fun favKey(c: Channel) = "${activeId}|${c.id}|${c.name}"

    fun visible(): List<Channel> {
        val sec = section()
        var l = sec.visible(query, { isFav(it) }, { it.id in history })
        if (sec.top == GROUP_HIST) l = l.sortedBy { history.indexOf(it.id) }
        if (tab == 0 && store.guideOpt("sortaz", false)) l = l.sortedBy { displayName(it).lowercase() }
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
            epg[c.id] = Epg(now, next, (now?.stop ?: (nowMs + 300_000)).coerceAtLeast(nowMs + 30_000), list)
            return
        }
        val cached = epg[c.id]
        if (cached != null && System.currentTimeMillis() < cached.until) return
        val s = activeSource ?: return
        epg[c.id] = Epg(cached?.now, cached?.next, System.currentTimeMillis() + 60_000, cached?.list ?: emptyList())
        viewModelScope.launch { epg[c.id] = Repo.shortEpg(s, c.sid, false) }
    }

    /** fiches (film : résumé, distribution) chargées à la demande quand on survole une affiche */
    val details = mutableStateMapOf<String, Detail>()

    fun loadDetail(c: Channel) {
        if (c.id in details) return
        if (c.kind == "series") { details[c.id] = Detail(c.plot, c.meta, ""); return }
        val s = activeSource ?: return
        viewModelScope.launch {
            val d = runCatching { Repo.vodInfo(s, c.sid) }.getOrNull() ?: Detail("", c.meta, "")
            details[c.id] = if (d.meta.isBlank()) d.copy(meta = c.meta) else d
        }
    }

    var movieOpen by mutableStateOf<Channel?>(null); private set

    fun openMovie(c: Channel) {
        movieOpen = c
        addHistory(c.id)
        loadDetail(c)
    }

    fun closeMovie() { movieOpen = null }

    fun openSeries(c: Channel) {
        addHistory(c.id)
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

    /** derniers canaux regardés (10 au maximum, les plus anciens sont retirés) */
    var recentIds by mutableStateOf(store.recentLive()); private set
    fun recentChannels(): List<Channel> = recentIds.mapNotNull { id -> live.items.firstOrNull { it.id == id } }
    private fun rememberRecent(c: Channel) {
        recentIds = (listOf(c.id) + recentIds.filter { it != c.id }).take(10)
        store.saveRecentLive(recentIds)
    }

    fun play(list: List<Channel>, c: Channel) {
        preview = null
        if (c.kind == "live") rememberRecent(c)
        if (c.kind == "movie" && !c.id.startsWith("r")) addHistory(c.id)
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

    /** demandé par le menu du lecteur (Préférences) : l'accueil ouvre les réglages */
    var openSettingsRequest by mutableStateOf(false)

    /** Retour depuis le plein écran : la chaîne revient en aperçu (elle continue de jouer). */
    fun stop() {
        val c = current
        playIndex = -1
        if (previewMode && c != null && c.kind == "live") preview = c
    }
}
