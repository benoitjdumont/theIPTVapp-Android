package com.studiogyro.theiptvapp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.BufferedReader
import java.util.concurrent.TimeUnit

/** Loading of channel lists (M3U and Xtream Codes). */
object Repo {
    const val LIST_UA = "theIPTVapp/0.1 (Android)"
    @Volatile var xmltvUrl: String = ""

    /** User-Agent des flux vidéo (modifiable dans Réglages → Réseau) */
    @Volatile var streamUa: String = "VLC/3.0.20 LibVLC/3.0.20"

    @Volatile var client: OkHttpClient = build("", "")

    /** Applique le User-Agent et le proxy choisis dans les réglages. proxy = "hôte:port" ou vide. */
    fun configure(ua: String, proxy: String) {
        streamUa = ua.trim().ifEmpty { "VLC/3.0.20 LibVLC/3.0.20" }
        client = build(ua, proxy)
    }

    private fun build(ua: String, proxy: String): OkHttpClient {
        val b = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
        val host = proxy.trim().substringBefore(':')
        val port = proxy.trim().substringAfter(':', "").toIntOrNull()
        if (host.isNotEmpty() && port != null) {
            b.proxy(java.net.Proxy(java.net.Proxy.Type.HTTP, java.net.InetSocketAddress(host, port)))
        }
        return b.build()
    }

    suspend fun load(src: Source): List<Channel> = withContext(Dispatchers.IO) {
        when (src.type) {
            SourceType.M3U -> loadM3u(src.url.trim())
            SourceType.XTREAM -> loadXtream(src)
        }
    }

    suspend fun loadVod(src: Source): List<Channel> = withContext(Dispatchers.IO) {
        val base = normHost(src.host)
        val u = encCred(src.user)
        val p = encCred(src.pass)
        val api = "$base/player_api.php?username=$u&password=$p"
        val cats = categoryMap("$api&action=get_vod_categories")
        val a = readJsonArrayCached("$api&action=get_vod_streams")
        val out = ArrayList<Channel>(a.length())
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            val id = o.optString("stream_id")
            if (id.isEmpty()) continue
            val ext = o.optString("container_extension").ifEmpty { "mp4" }
            out.add(
                Channel(
                    id = "v$id", name = o.optString("name").ifEmpty { "Film $id" },
                    url = "$base/movie/$u/$p/$id.$ext", logo = o.optString("stream_icon"),
                    group = cats[o.optString("category_id")] ?: "", kind = "movie",
                    sid = id, meta = metaOf(o.optString("rating"), "", ""),
                ),
            )
        }
        if (out.isEmpty()) throw RuntimeException("Aucun film trouvé")
        out
    }

    suspend fun loadSeries(src: Source): List<Channel> = withContext(Dispatchers.IO) {
        val base = normHost(src.host)
        val u = encCred(src.user)
        val p = encCred(src.pass)
        val api = "$base/player_api.php?username=$u&password=$p"
        val cats = categoryMap("$api&action=get_series_categories")
        val a = readJsonArrayCached("$api&action=get_series")
        val out = ArrayList<Channel>(a.length())
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            val id = o.optString("series_id")
            if (id.isEmpty()) continue
            out.add(
                Channel(
                    id = "s$id", name = o.optString("name").ifEmpty { "Série $id" }, url = "",
                    logo = o.optString("cover").ifBlank { o.optJSONArray("backdrop_path")?.optString(0) ?: "" },
                    group = cats[o.optString("category_id")] ?: "",
                    sid = id, kind = "series", plot = o.optString("plot"),
                    meta = metaOf(o.optString("rating"), o.optString("releaseDate").take(4), o.optString("genre")),
                ),
            )
        }
        if (out.isEmpty()) throw RuntimeException("Aucune série trouvée")
        out
    }

    private fun metaOf(rating: String, year: String, genre: String): String {
        val parts = ArrayList<String>()
        val r = rating.trim().toDoubleOrNull()
        if (r != null && r > 0) parts.add("★ " + String.format(java.util.Locale.US, "%.1f", r))
        if (year.length == 4 && year.all(Char::isDigit)) parts.add(year)
        if (genre.isNotBlank()) parts.add(genre.trim())
        return parts.joinToString("  ·  ")
    }

    /** fiche d'un film : résumé, durée, genre, distribution */
    suspend fun vodInfo(src: Source, sid: String): Detail = withContext(Dispatchers.IO) {
        val base = normHost(src.host)
        val u = encCred(src.user)
        val p = encCred(src.pass)
        var txt = ""
        open("$base/player_api.php?username=$u&password=$p&action=get_vod_info&vod_id=$sid") { r -> txt = r.body?.string() ?: "" }
        val info = org.json.JSONObject(txt.trim()).optJSONObject("info") ?: return@withContext Detail("", "", "")
        val plot = info.optString("plot").ifBlank { info.optString("description") }
        val year = info.optString("releasedate").take(4).ifBlank { info.optString("year") }
        val dur = info.optString("duration")
        val meta = metaOf(info.optString("rating"), year, info.optString("genre")) +
            (if (dur.isNotBlank()) (if (info.optString("rating").isNotBlank() || year.isNotBlank()) "  ·  " else "") + dur else "")
        Detail(plot, meta, info.optString("cast"), info.optString("youtube_trailer").trim())
    }

    suspend fun seriesInfo(src: Source, sid: String, seriesName: String = ""): SeriesData = withContext(Dispatchers.IO) {
        val base = normHost(src.host)
        val u = encCred(src.user)
        val p = encCred(src.pass)
        var txt = ""
        open("$base/player_api.php?username=$u&password=$p&action=get_series_info&series_id=$sid") { r -> txt = r.body?.string() ?: "" }
        val root = org.json.JSONObject(txt.trim())
        val plot = root.optJSONObject("info")?.optString("plot") ?: ""
        val seasons = java.util.TreeMap<Int, MutableList<Channel>>()
        fun addEp(season: Int, o: org.json.JSONObject) {
            val id = o.optString("id")
            if (id.isEmpty()) return
            val ext = o.optString("container_extension").ifEmpty { "mp4" }
            val num = o.optString("episode_num")
            val raw = o.optString("title")
            // « 9-1-1: Lone Star - S03E01 - The Big Chill » → « The Big Chill »
            val title = raw.replace(Regex("""^.*?[Ss]\d{1,2}[Ee]\d{1,3}\s*[-–:]?\s*"""), "").ifBlank { raw }
            val label = "S${season}E$num" + if (title.isNotBlank()) " · $title" else ""
            val img = o.optJSONObject("info")?.optString("movie_image") ?: ""
            seasons.getOrPut(season) { ArrayList() }.add(
                Channel(
                    id = "e$id", name = label, url = "$base/series/$u/$p/$id.$ext", logo = img, kind = "movie",
                    seriesName = seriesName, season = season, episode = num.toIntOrNull() ?: 0,
                ),
            )
        }
        val eps = root.opt("episodes")
        if (eps is org.json.JSONObject) {
            for (k in eps.keys()) {
                val season = k.toIntOrNull() ?: continue
                val arr = eps.optJSONArray(k) ?: continue
                for (i in 0 until arr.length()) addEp(season, arr.getJSONObject(i))
            }
        } else if (eps is JSONArray) {
            for (si in 0 until eps.length()) {
                val arr = eps.optJSONArray(si) ?: continue
                for (i in 0 until arr.length()) addEp(si + 1, arr.getJSONObject(i))
            }
        }
        val info = root.optJSONObject("info")
        val cover = info?.optString("cover").orEmpty().ifBlank { info?.optJSONArray("backdrop_path")?.optString(0) ?: "" }
        SeriesData(plot, seasons, cover)
    }

    private fun categoryMap(url: String): Map<String, String> {
        val m = HashMap<String, String>()
        runCatching {
            val a = readJsonArray(url)
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                m[o.optString("category_id")] = o.optString("category_name")
            }
        }
        return m
    }

    /** Reads an XMLTV guide (plain or gzip) and keeps `days` days ahead for the wanted channels. */
    suspend fun loadXmltv(url: String, ids: Set<String>, days: Int, withDesc: Boolean): Map<String, List<Prog>> = withContext(Dispatchers.IO) {
        val out = HashMap<String, MutableList<Prog>>()
        val nowMs = System.currentTimeMillis()
        val horizon = nowMs + days.coerceIn(1, 30) * 86400_000L
        val req = Request.Builder().url(url).header("User-Agent", LIST_UA).build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw RuntimeException("HTTP ${r.code}")
            val raw = (r.body ?: throw RuntimeException("réponse vide")).byteStream().buffered()
            raw.mark(2)
            val b1 = raw.read()
            val b2 = raw.read()
            raw.reset()
            val stream = if (b1 == 0x1f && b2 == 0x8b) java.util.zip.GZIPInputStream(raw) else raw
            val p = android.util.Xml.newPullParser()
            p.setInput(stream, null)
            var channel = ""
            var start = 0L
            var stop = 0L
            var title = ""
            var desc = ""
            var keep = false
            var ev = p.eventType
            while (ev != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if (ev == org.xmlpull.v1.XmlPullParser.START_TAG) {
                    if (p.name == "programme") {
                        channel = p.getAttributeValue(null, "channel") ?: ""
                        start = xmltvTime(p.getAttributeValue(null, "start"))
                        stop = xmltvTime(p.getAttributeValue(null, "stop"))
                        title = ""
                        desc = ""
                        keep = channel in ids && stop > nowMs && start < horizon
                    } else if (p.name == "title" && keep && title.isEmpty()) {
                        title = p.nextText().trim()
                    } else if (p.name == "desc" && keep && withDesc && desc.isEmpty()) {
                        desc = p.nextText().trim()
                    }
                } else if (ev == org.xmlpull.v1.XmlPullParser.END_TAG && p.name == "programme") {
                    if (keep && title.isNotEmpty()) out.getOrPut(channel) { ArrayList() }.add(Prog(title, start, stop, desc))
                    keep = false
                }
                ev = p.next()
            }
        }
        out.mapValues { e -> e.value.sortedBy { it.start } }
    }

    private fun xmltvTime(s: String?): Long {
        if (s == null) return 0L
        for (f in listOf("yyyyMMddHHmmss Z", "yyyyMMddHHmmss")) {
            val d = runCatching {
                val fmt = java.text.SimpleDateFormat(f, java.util.Locale.US)
                // sans décalage, le fournisseur envoie l'heure de l'Est (pas l'UTC) : on la lit donc dans ce fuseau
                if (f.length == 14) fmt.timeZone = java.util.TimeZone.getTimeZone("America/Toronto")
                fmt.parse(s.trim())
            }.getOrNull()
            if (d != null) return d.time
        }
        return 0L
    }

    private val epgGate = Semaphore(4)

    /** Programme guide for one Xtream channel (now + next). Never throws: returns an empty guide on failure. */
    /** écart entre l'heure du serveur du fournisseur et l'heure UTC, appris des guides (pour le replay) */
    @Volatile var serverOffsetMs = 0L

    suspend fun shortEpg(src: Source, sid: String, archive: Boolean = false): Epg = withContext(Dispatchers.IO) {
        val nowMs = System.currentTimeMillis()
        val empty = Epg(null, null, nowMs + 10 * 60_000)
        if (src.type != SourceType.XTREAM || sid.isEmpty()) return@withContext empty
        epgGate.withPermit {
            runCatching {
                val base = normHost(src.host)
                val u = encCred(src.user)
                val p = encCred(src.pass)
                var txt = ""
                open("$base/player_api.php?username=$u&password=$p&action=${if (archive) "get_simple_data_table" else "get_short_epg"}&stream_id=$sid${if (archive) "" else "&limit=8"}") { r -> txt = r.body?.string() ?: "" }
                val arr = org.json.JSONObject(txt.trim()).optJSONArray("epg_listings") ?: return@runCatching empty
                val nowSec = nowMs / 1000
                val progs = ArrayList<Prog>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val st = o.optString("start_timestamp").toLongOrNull() ?: continue
                    val en = o.optString("stop_timestamp").toLongOrNull() ?: continue
                    val title = decode(o.optString("title"))
                    val txtStart = o.optString("start")
                    if (txtStart.length >= 19) runCatching {
                        val f = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                        f.timeZone = java.util.TimeZone.getTimeZone("UTC")
                        val off = f.parse(txtStart.take(19))!!.time - st * 1000
                        if (kotlin.math.abs(off) <= 24 * 3600_000L) serverOffsetMs = (off / 900_000L) * 900_000L
                    }
                    val minStop = if (archive) nowSec - 12 * 3600 else nowSec
                    if (title.isBlank() || en <= minStop || (archive && st > nowSec + 12 * 3600)) continue
                    progs.add(Prog(title, st * 1000, en * 1000))
                }
                progs.sortBy { it.start }
                val now = progs.firstOrNull { it.start <= nowMs }
                val next = progs.firstOrNull { it.start > nowMs }
                Epg(now, next, (now?.stop ?: next?.start ?: (nowMs + 10 * 60_000)).coerceAtLeast(nowMs + 30_000), progs)
            }.getOrDefault(empty)
        }
    }

    /** adresse de rattrapage Xtream pour revoir une émission passée */
    fun timeshiftUrl(src: Source, sid: String, p: Prog): String {
        val base = normHost(src.host)
        val u = encCred(src.user)
        val pw = encCred(src.pass)
        val minutes = ((p.stop - p.start) / 60_000L).coerceAtLeast(1)
        val f = java.text.SimpleDateFormat("yyyy-MM-dd:HH-mm", java.util.Locale.US)
        f.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return "$base/timeshift/$u/$pw/$minutes/${f.format(java.util.Date(p.start + serverOffsetMs))}/$sid.ts"
    }

    private fun decode(s: String): String =
        runCatching { String(android.util.Base64.decode(s, android.util.Base64.DEFAULT), Charsets.UTF_8) }.getOrDefault(s).trim()

    private fun open(url: String, block: (okhttp3.Response) -> Unit) {
        val req = Request.Builder().url(url).header("User-Agent", LIST_UA).build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw RuntimeException("HTTP ${r.code}")
            block(r)
        }
    }

    private fun loadM3u(url: String): List<Channel> {
        require(url.startsWith("http", ignoreCase = true)) { "Adresse M3U invalide" }
        var out: List<Channel> = emptyList()
        open(url) { r ->
            val body = r.body ?: throw RuntimeException("Réponse vide")
            xmltvUrl = ""
            body.charStream().buffered().use { out = parseM3u(it) { u -> xmltvUrl = u } }
        }
        if (out.isEmpty()) throw RuntimeException("Aucune chaîne trouvée dans cette liste")
        return out
    }

    private val attrRe = Regex("""([\w-]+)="([^"]*)"""")

    fun parseM3u(reader: BufferedReader, onGuideUrl: (String) -> Unit = {}): List<Channel> {
        val list = ArrayList<Channel>()
        var name = ""
        var logo = ""
        var group = ""
        var pending = false
        var epgId = ""
        var n = 0
        while (true) {
            val line = (reader.readLine() ?: break).trim()
            if (line.isEmpty()) continue
            if (line.startsWith("#EXTM3U", ignoreCase = true)) {
                val h = attrRe.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                (h["url-tvg"] ?: h["x-tvg-url"])?.split(',')?.firstOrNull { it.startsWith("http") }?.let(onGuideUrl)
            } else if (line.startsWith("#EXTINF", ignoreCase = true)) {
                val attrs = attrRe.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                logo = attrs["tvg-logo"] ?: ""
                epgId = attrs["tvg-id"] ?: ""
                group = attrs["group-title"] ?: ""
                val comma = line.lastIndexOf(',')
                name = if (comma >= 0) line.substring(comma + 1).trim() else ""
                if (name.isEmpty()) name = attrs["tvg-name"] ?: ""
                pending = true
            } else if (line.startsWith("#EXTGRP:", ignoreCase = true)) {
                if (group.isEmpty()) group = line.substringAfter(':').trim()
            } else if (!line.startsWith("#")) {
                if (pending || line.contains("://")) {
                    n++
                    list.add(Channel(id = "m$n", name = name.ifEmpty { "Chaîne $n" }, url = line, logo = logo, group = group, epgId = epgId))
                }
                name = ""; logo = ""; group = ""; epgId = ""; pending = false
            }
        }
        return list
    }

    private fun normHost(h: String): String {
        var s = h.trim().trimEnd('/')
        if (!s.startsWith("http", ignoreCase = true)) s = "http://$s"
        return s
    }

    private fun readJsonArray(url: String): JSONArray = JSONArray(fetchJsonText(url))

    private fun fetchJsonText(url: String): String {
        var txt = ""
        open(url) { r -> txt = r.body?.string() ?: "" }
        val t = txt.trim()
        if (!t.startsWith("[")) throw RuntimeException("Réponse inattendue du serveur (identifiants refusés ?)")
        return t
    }

    /** dossier du cache des listes Films et Séries (défini au démarrage) */
    @Volatile var cacheDir: java.io.File? = null

    private const val LIST_CACHE_MS = 12L * 3600_000L

    /**
     * Liste Xtream (films ou séries) gardée sur le disque 12 h : l'ouverture suivante est immédiate.
     * La clé contient l'adresse complète (avec identifiants) : changer de compte ne réutilise pas l'ancien cache.
     */
    private fun readJsonArrayCached(url: String): JSONArray {
        val dir = cacheDir ?: return readJsonArray(url)
        val f = java.io.File(dir, "list_${url.hashCode()}.json")
        if (f.isFile && System.currentTimeMillis() - f.lastModified() < LIST_CACHE_MS) {
            runCatching { return JSONArray(f.readText()) }
        }
        val txt = fetchJsonText(url)
        runCatching {
            dir.mkdirs()
            val tmp = java.io.File(dir, "list_${url.hashCode()}.tmp")
            tmp.writeText(txt)
            tmp.renameTo(f)
        }
        return JSONArray(txt)
    }

    private fun loadXtream(src: Source): List<Channel> {
        val base = normHost(src.host)
        val u = encCred(src.user)
        val p = encCred(src.pass)
        val api = "$base/player_api.php?username=$u&password=$p"
        val cats = HashMap<String, String>()
        runCatching {
            val a = readJsonArray("$api&action=get_live_categories")
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                cats[o.optString("category_id")] = o.optString("category_name")
            }
        }
        val a = readJsonArray("$api&action=get_live_streams")
        val out = ArrayList<Channel>(a.length())
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            val id = o.optString("stream_id")
            if (id.isEmpty()) continue
            val prefix = "$base/live/$u/$p/$id"
            out.add(
                Channel(
                    id = "x$id",
                    name = o.optString("name").ifEmpty { "Chaîne $id" },
                    url = "$prefix.m3u8",
                    alt = listOf("$prefix.ts"),
                    logo = o.optString("stream_icon"),
                    group = cats[o.optString("category_id")] ?: "",
                    sid = id,
                    archive = o.optString("tv_archive").toIntOrNull() ?: 0,
                ),
            )
        }
        if (out.isEmpty()) throw RuntimeException("Aucune chaîne trouvée")
        return out
    }
}

/** Identifiants dans un chemin d URL : espaces en %20 (pas +), blancs de saisie retirés. */
fun encCred(s: String): String = java.net.URLEncoder.encode(s.trim(), "UTF-8").replace("+", "%20")
