package com.studiogyro.theiptvapp

import android.content.Context

/** Small persistent storage: sources, favourites, active source. */
class Store(ctx: Context) {
    private val sp = ctx.getSharedPreferences(Profiles.prefsName(ctx), Context.MODE_PRIVATE)

    fun sources(): List<Source> = parseSources(sp.getString("sources", null))
    fun saveSources(l: List<Source>) = sp.edit().putString("sources", l.toJsonString()).apply()

    fun activeId(): String? = sp.getString("active", null)
    fun saveActive(id: String?) = sp.edit().putString("active", id).apply()

    fun favorites(): Set<String> = sp.getStringSet("favs", emptySet())?.toSet() ?: emptySet()
    fun saveFavorites(s: Set<String>) = sp.edit().putStringSet("favs", s).apply()

    fun termsAccepted(): Boolean = sp.getBoolean("terms_ok", false)
    fun saveTermsAccepted() = sp.edit().putBoolean("terms_ok", true).apply()

    fun lastChannel(): String? = sp.getString("last", null)
    fun saveLast(id: String?) = sp.edit().putString("last", id).apply()

    fun savePos(id: String, ms: Long) = sp.edit().putLong("pos_$id", ms).apply()
    fun pos(id: String): Long = sp.getLong("pos_$id", 0L)
    fun clearPos(id: String) = sp.edit().remove("pos_$id").apply()

    fun pinHash(): String? = sp.getString("pin", null)
    fun keywords(): List<String> = (sp.getString("kw", "") ?: "").split(',').map { it.trim() }.filter { it.isNotEmpty() }
    fun saveParental(pinHash: String?, kws: List<String>) =
        sp.edit().putString("pin", pinHash).putString("kw", kws.joinToString(",")).apply()

    fun lockedCats(): Set<String> = sp.getStringSet("lockedcats", emptySet())?.toSet() ?: emptySet()
    fun saveLockedCats(s: Set<String>) = sp.edit().putStringSet("lockedcats", s).apply()
    fun hiddenCats(): Set<String> = sp.getStringSet("hiddencats", emptySet())?.toSet() ?: emptySet()
    fun saveHiddenCats(s: Set<String>) = sp.edit().putStringSet("hiddencats", s).apply()

    fun lastLive(): String? = sp.getString("lastlive", null)
    fun saveLastLive(id: String?) = sp.edit().putString("lastlive", id).apply()
    fun previewMode(): Boolean = sp.getBoolean("preview", true)
    fun savePreviewMode(b: Boolean) = sp.edit().putBoolean("preview", b).apply()
    fun history(): List<String> = (sp.getString("history", "") ?: "").split("\n").filter { it.isNotBlank() }
    fun saveHistory(l: List<String>) = sp.edit().putString("history", l.joinToString("\n")).apply()
    fun textSize(): Int = sp.getInt("textsize", 1)
    fun saveTextSize(i: Int) = sp.edit().putInt("textsize", i).apply()
    fun transp(): Int = sp.getInt("transp", 1)
    fun saveTransp(i: Int) = sp.edit().putInt("transp", i).apply()
    fun accent(): Int = sp.getInt("accent", 13)
    fun saveAccent(i: Int) = sp.edit().putInt("accent", i).apply()
    fun autoResume(): Boolean = sp.getBoolean("autoresume", true)
    fun saveAutoResume(b: Boolean) = sp.edit().putBoolean("autoresume", b).apply()

    // ---- préférences générales
    fun pipOnHome(): Boolean = sp.getBoolean("pip_home", true)
    fun savePipOnHome(b: Boolean) = sp.edit().putBoolean("pip_home", b).apply()
    fun doubleBack(): Boolean = sp.getBoolean("double_back", true)
    fun saveDoubleBack(b: Boolean) = sp.edit().putBoolean("double_back", b).apply()
    fun bootStart(): Boolean = sp.getBoolean("boot_start", false)
    fun saveBootStart(b: Boolean) = sp.edit().putBoolean("boot_start", b).apply()

    // ---- réseau
    fun userAgent(): String = sp.getString("ua", "") ?: ""
    fun saveUserAgent(s: String) = sp.edit().putString("ua", s).apply()
    fun proxy(): String = sp.getString("proxy", "") ?: ""
    fun saveProxy(s: String) = sp.edit().putString("proxy", s).apply()

    /** clé API OpenSubtitles : personnelle à chaque personne, jamais fournie par l'appli */
    fun osKey(): String = sp.getString("os_key", "") ?: ""
    fun saveOsKey(s: String) = sp.edit().putString("os_key", s.trim()).apply()
    /** langue(s) des sous-titres en ligne, mémorisée : "fr", "en" ou "fr,en" */
    fun subLang(): String = sp.getString("sub_lang", "fr,en") ?: "fr,en"
    fun saveSubLang(l: String) = sp.edit().putString("sub_lang", l).apply()

    // ---- guide (EPG)
    fun epgDays(): Int = sp.getInt("epg_days", 1).coerceIn(1, 30)
    fun saveEpgDays(n: Int) = sp.edit().putInt("epg_days", n.coerceIn(1, 30)).apply()
    fun epgDesc(): Boolean = sp.getBoolean("epg_desc", false)
    fun saveEpgDesc(b: Boolean) = sp.edit().putBoolean("epg_desc", b).apply()
    fun epgInterval(): Int = sp.getInt("epg_interval", 0).coerceIn(0, 168)
    fun saveEpgInterval(h: Int) = sp.edit().putInt("epg_interval", h.coerceIn(0, 168)).apply()
    fun epgOnStart(): Boolean = sp.getBoolean("epg_onstart", true)
    fun saveEpgOnStart(b: Boolean) = sp.edit().putBoolean("epg_onstart", b).apply()
    fun epgOnList(): Boolean = sp.getBoolean("epg_onlist", true)
    fun saveEpgOnList(b: Boolean) = sp.edit().putBoolean("epg_onlist", b).apply()
    fun epgLast(): Long = sp.getLong("epg_last", 0L)
    fun epgStatus(): String = sp.getString("epg_status", "") ?: ""
    fun saveEpgStatus(time: Long, text: String) = sp.edit().putLong("epg_last", time).putString("epg_status", text).apply()

    // ---- sauvegarde : toutes les préférences et listes, en JSON
    fun backupJson(): String {
        val root = org.json.JSONObject()
        for ((k, v) in sp.all) {
            val o = org.json.JSONObject()
            when (v) {
                is Set<*> -> o.put("t", "set").put("v", org.json.JSONArray(v.map { it.toString() }))
                is Int -> o.put("t", "i").put("v", v)
                is Long -> o.put("t", "l").put("v", v)
                is Boolean -> o.put("t", "b").put("v", v)
                is String -> o.put("t", "s").put("v", v)
                else -> continue
            }
            root.put(k, o)
        }
        return root.toString(2)
    }

    /** Remplace les préférences par celles d'une sauvegarde. Renvoie false si le fichier n'est pas valide. */
    fun restoreJson(text: String): Boolean {
        val root = runCatching { org.json.JSONObject(text) }.getOrNull() ?: return false
        val e = sp.edit().clear()
        for (k in root.keys()) {
            val o = root.optJSONObject(k) ?: continue
            when (o.optString("t")) {
                "set" -> {
                    val a = o.optJSONArray("v") ?: continue
                    e.putStringSet(k, (0 until a.length()).map { a.getString(it) }.toSet())
                }
                "i" -> e.putInt(k, o.optInt("v"))
                "l" -> e.putLong(k, o.optLong("v"))
                "b" -> e.putBoolean(k, o.optBoolean("v"))
                "s" -> e.putString(k, o.optString("v"))
            }
        }
        return e.commit()
    }

    // ---- apparence : guide TV, noms de chaînes, image de fond
    fun guideOpt(key: String, def: Boolean): Boolean = sp.getBoolean("g_$key", def)
    fun saveGuideOpt(key: String, b: Boolean) = sp.edit().putBoolean("g_$key", b).apply()
    fun renames(): Map<String, String> {
        val o = runCatching { org.json.JSONObject(sp.getString("renames", "{}") ?: "{}") }.getOrElse { org.json.JSONObject() }
        return o.keys().asSequence().associateWith { o.optString(it) }
    }
    fun saveRenames(m: Map<String, String>) {
        val o = org.json.JSONObject()
        m.forEach { (k, v) -> o.put(k, v) }
        sp.edit().putString("renames", o.toString()).apply()
    }
    // ---- lecteur : piste audio surround par défaut
    fun audioPassthrough(): Boolean = sp.getBoolean("audio_passthrough", false)
    fun saveAudioPassthrough(b: Boolean) = sp.edit().putBoolean("audio_passthrough", b).apply()
    fun surroundDefault(): Boolean = sp.getBoolean("surround_default", true)
    fun saveSurroundDefault(b: Boolean) = sp.edit().putBoolean("surround_default", b).apply()

    // ---- apparence : couleur de surbrillance et couleur du texte sélectionné (index dans ACCENTS, -1 = défaut)
    fun hiliteIdx(): Int = sp.getInt("hilite", -1)
    fun saveHiliteIdx(i: Int) = sp.edit().putInt("hilite", i).apply()
    fun seltextIdx(): Int = sp.getInt("seltext", -1)
    fun saveSeltextIdx(i: Int) = sp.edit().putInt("seltext", i).apply()

    // ---- télécommande : action choisie pour chaque touche configurable (vide = action par défaut)
    fun keyChoice(i: Int): String? = sp.getString("key_$i", null)
    fun saveKeyChoice(i: Int, s: String) = sp.edit().putString("key_$i", s).apply()
    fun clearKeyChoices(count: Int) { val e = sp.edit(); for (i in 0 until count) e.remove("key_$i"); e.apply() }

    // ---- lecteur : délai d'expiration des panneaux (secondes)
    fun panelTimeout(): Int = sp.getInt("panel_timeout", 5)
    fun savePanelTimeout(s: Int) = sp.edit().putInt("panel_timeout", s).apply()
    fun glassPct(): Int = sp.getInt("glass", 0)
    fun saveGlassPct(p: Int) = sp.edit().putInt("glass", p).apply()
    fun bgPath(): String? = sp.getString("bg", null)
    fun saveBgPath(p: String?) = sp.edit().putString("bg", p).apply()

    fun recentLive(): List<String> = (sp.getString("recent", "") ?: "").split("\n").filter { it.isNotBlank() }
    fun saveRecentLive(l: List<String>) = sp.edit().putString("recent", l.joinToString("\n")).apply()
}
