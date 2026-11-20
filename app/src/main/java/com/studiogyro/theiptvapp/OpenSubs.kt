package com.studiogyro.theiptvapp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Recherche de sous-titres sur OpenSubtitles avec la clé personnelle de l'utilisateur
 * (saisie dans Préférences › Réseau, gardée sur cet appareil seulement).
 */
object OpenSubs {
    private const val BASE = "https://api.opensubtitles.com/api/v1"

    class Sub(val fileId: Long, val release: String, val downloads: Int, val language: String)

    private fun req(key: String, url: String): Request.Builder =
        Request.Builder().url(url)
            .header("Api-Key", key)
            .header("User-Agent", "theIPTVapp v1.0")
            .header("Accept", "application/json")

    /** sous-titres en français et en anglais (liste séparée par des virgules), les plus téléchargés d'abord */
    suspend fun search(key: String, query: String, year: String = "", lang: String = "fr,en", season: Int = 0, episode: Int = 0): List<Sub> =
        withContext(Dispatchers.IO) {
            val q = URLEncoder.encode(query, "UTF-8")
            var url = "$BASE/subtitles?query=$q&languages=$lang&order_by=download_count&order_direction=desc"
            // épisode : seulement les sous-titres de cette saison et de cet épisode (évite les autres émissions au nom proche)
            if (season > 0 && episode > 0) url += "&type=episode&season_number=$season&episode_number=$episode"
            if (year.isNotBlank()) url += "&year=$year"
            Repo.client.newCall(req(key, url).build()).execute().use { r ->
                if (r.code == 401 || r.code == 403) throw RuntimeException("Clé OpenSubtitles refusée : vérifie-la dans Préférences › Réseau")
                if (!r.isSuccessful) throw RuntimeException("OpenSubtitles : erreur ${r.code}")
                val arr = JSONObject(r.body?.string().orEmpty()).optJSONArray("data")
                    ?: return@withContext emptyList<Sub>()
                (0 until arr.length()).mapNotNull { i ->
                    val a = arr.getJSONObject(i).optJSONObject("attributes") ?: return@mapNotNull null
                    val files = a.optJSONArray("files") ?: return@mapNotNull null
                    if (files.length() == 0) return@mapNotNull null
                    Sub(
                        fileId = files.getJSONObject(0).getLong("file_id"),
                        release = a.optString("release").ifBlank { "sans nom" },
                        downloads = a.optInt("download_count"),
                        language = a.optString("language", "??").uppercase(),
                    )
                }
            }
        }

    /** adresse directe du fichier de sous-titres (SRT) pour un fichier choisi */
    suspend fun link(key: String, fileId: Long): String = withContext(Dispatchers.IO) {
        val body = JSONObject().put("file_id", fileId).toString().toRequestBody("application/json".toMediaType())
        val request = req(key, "$BASE/download").post(body).build()
        Repo.client.newCall(request).execute().use { r ->
            if (r.code == 406 || r.code == 429) throw RuntimeException("Limite de téléchargements OpenSubtitles atteinte (compte gratuit : quelques-uns par jour)")
            if (r.code == 401 || r.code == 403) throw RuntimeException("Clé OpenSubtitles refusée : vérifie-la dans Préférences › Réseau")
            if (!r.isSuccessful) throw RuntimeException("OpenSubtitles : téléchargement impossible (${r.code})")
            JSONObject(r.body?.string().orEmpty()).getString("link")
        }
    }
}

/** titre seul, sans l'année ni les mentions entre parenthèses ou crochets : « Titre (2019) » → « Titre » */
fun cleanTitle(name: String): String =
    name.replace(Regex("\\([^)]*\\)|\\[[^]]*]"), " ").replace(Regex("\\s+"), " ").trim()

/** année entre parenthèses, « (2019) » → « 2019 », ou vide */
fun yearOf(name: String): String = Regex("\\((\\d{4})\\)").find(name)?.groupValues?.get(1).orEmpty()
