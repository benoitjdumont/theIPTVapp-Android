package com.studiogyro.theiptvapp

import org.json.JSONArray
import org.json.JSONObject

enum class SourceType { M3U, XTREAM }

data class Source(
    val id: String,
    val name: String,
    val type: SourceType,
    val url: String = "",
    val host: String = "",
    val user: String = "",
    val pass: String = "",
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("type", type.name)
        .put("url", url).put("host", host).put("user", user).put("pass", pass)

    companion object {
        fun fromJson(o: JSONObject) = Source(
            id = o.getString("id"),
            name = o.optString("name"),
            type = runCatching { SourceType.valueOf(o.optString("type")) }.getOrDefault(SourceType.M3U),
            url = o.optString("url"),
            host = o.optString("host"),
            user = o.optString("user"),
            pass = o.optString("pass"),
        )
    }
}

data class Channel(
    val id: String,
    val name: String,
    /** main address to try first */
    val url: String,
    /** other addresses to try if the first one fails (e.g. .ts when .m3u8 does not work) */
    val alt: List<String> = emptyList(),
    val logo: String = "",
    val group: String = "",
    /** Xtream stream id, used to ask for the programme guide */
    val sid: String = "",
    /** "live", "movie" (playable file) or "series" (opens the episode list) */
    val kind: String = "live",
    val plot: String = "",
    /** jours de rattrapage (replay) proposés par le fournisseur, 0 = aucun */
    val archive: Int = 0,
    /** ligne d'infos courte : note · année · genre */
    val meta: String = "",
    /** tvg-id of an M3U channel, used to find it in the XMLTV guide */
    val epgId: String = "",
)

data class Detail(val plot: String, val meta: String, val cast: String, val trailer: String = "")

data class SeriesData(val plot: String, val seasons: Map<Int, List<Channel>>, val cover: String = "")

data class Prog(val title: String, val start: Long, val stop: Long, val desc: String = "")

/** Programme guide of one channel: what is on now and what comes next (times in ms). */
data class Epg(val now: Prog?, val next: Prog?, val until: Long, val list: List<Prog> = emptyList())

fun List<Source>.toJsonString(): String = JSONArray().also { a -> forEach { a.put(it.toJson()) } }.toString()

fun parseSources(s: String?): List<Source> {
    if (s.isNullOrBlank()) return emptyList()
    return runCatching {
        val a = JSONArray(s)
        (0 until a.length()).map { Source.fromJson(a.getJSONObject(it)) }
    }.getOrDefault(emptyList())
}
