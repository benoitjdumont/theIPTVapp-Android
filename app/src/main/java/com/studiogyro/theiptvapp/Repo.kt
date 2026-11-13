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
    const val STREAM_UA = "VLC/3.0.20 LibVLC/3.0.20"

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun load(src: Source): List<Channel> = withContext(Dispatchers.IO) {
        when (src.type) {
            SourceType.M3U -> loadM3u(src.url.trim())
            SourceType.XTREAM -> loadXtream(src)
        }
    }

    private val epgGate = Semaphore(4)

    /** Programme guide for one Xtream channel (now + next). Never throws: returns an empty guide on failure. */
    suspend fun shortEpg(src: Source, sid: String): Epg = withContext(Dispatchers.IO) {
        val nowMs = System.currentTimeMillis()
        val empty = Epg(null, null, nowMs + 10 * 60_000)
        if (src.type != SourceType.XTREAM || sid.isEmpty()) return@withContext empty
        epgGate.withPermit {
            runCatching {
                val base = normHost(src.host)
                val u = java.net.URLEncoder.encode(src.user, "UTF-8")
                val p = java.net.URLEncoder.encode(src.pass, "UTF-8")
                var txt = ""
                open("$base/player_api.php?username=$u&password=$p&action=get_short_epg&stream_id=$sid&limit=4") { r -> txt = r.body?.string() ?: "" }
                val arr = org.json.JSONObject(txt.trim()).optJSONArray("epg_listings") ?: return@runCatching empty
                val nowSec = nowMs / 1000
                val progs = ArrayList<Prog>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val st = o.optString("start_timestamp").toLongOrNull() ?: continue
                    val en = o.optString("stop_timestamp").toLongOrNull() ?: continue
                    val title = decode(o.optString("title"))
                    if (title.isBlank() || en <= nowSec) continue
                    progs.add(Prog(title, st * 1000, en * 1000))
                }
                progs.sortBy { it.start }
                val now = progs.firstOrNull { it.start <= nowMs }
                val next = progs.firstOrNull { it.start > nowMs }
                Epg(now, next, (now?.stop ?: next?.start ?: (nowMs + 10 * 60_000)).coerceAtLeast(nowMs + 30_000))
            }.getOrDefault(empty)
        }
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
            body.charStream().buffered().use { out = parseM3u(it) }
        }
        if (out.isEmpty()) throw RuntimeException("Aucune chaîne trouvée dans cette liste")
        return out
    }

    private val attrRe = Regex("""([\w-]+)="([^"]*)"""")

    fun parseM3u(reader: BufferedReader): List<Channel> {
        val list = ArrayList<Channel>()
        var name = ""
        var logo = ""
        var group = ""
        var pending = false
        var n = 0
        while (true) {
            val line = (reader.readLine() ?: break).trim()
            if (line.isEmpty()) continue
            if (line.startsWith("#EXTINF", ignoreCase = true)) {
                val attrs = attrRe.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                logo = attrs["tvg-logo"] ?: ""
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
                    list.add(Channel(id = "m$n", name = name.ifEmpty { "Chaîne $n" }, url = line, logo = logo, group = group))
                }
                name = ""; logo = ""; group = ""; pending = false
            }
        }
        return list
    }

    private fun normHost(h: String): String {
        var s = h.trim().trimEnd('/')
        if (!s.startsWith("http", ignoreCase = true)) s = "http://$s"
        return s
    }

    private fun readJsonArray(url: String): JSONArray {
        var txt = ""
        open(url) { r -> txt = r.body?.string() ?: "" }
        val t = txt.trim()
        if (!t.startsWith("[")) throw RuntimeException("Réponse inattendue du serveur (identifiants refusés ?)")
        return JSONArray(t)
    }

    private fun loadXtream(src: Source): List<Channel> {
        val base = normHost(src.host)
        val u = java.net.URLEncoder.encode(src.user, "UTF-8")
        val p = java.net.URLEncoder.encode(src.pass, "UTF-8")
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
                ),
            )
        }
        if (out.isEmpty()) throw RuntimeException("Aucune chaîne trouvée")
        return out
    }
}
