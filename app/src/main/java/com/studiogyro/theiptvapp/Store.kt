package com.studiogyro.theiptvapp

import android.content.Context

/** Small persistent storage: sources, favourites, active source. */
class Store(ctx: Context) {
    private val sp = ctx.getSharedPreferences("theiptvapp", Context.MODE_PRIVATE)

    fun sources(): List<Source> = parseSources(sp.getString("sources", null))
    fun saveSources(l: List<Source>) = sp.edit().putString("sources", l.toJsonString()).apply()

    fun activeId(): String? = sp.getString("active", null)
    fun saveActive(id: String?) = sp.edit().putString("active", id).apply()

    fun favorites(): Set<String> = sp.getStringSet("favs", emptySet())?.toSet() ?: emptySet()
    fun saveFavorites(s: Set<String>) = sp.edit().putStringSet("favs", s).apply()

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
    fun accent(): Int = sp.getInt("accent", 0)
    fun saveAccent(i: Int) = sp.edit().putInt("accent", i).apply()
    fun autoResume(): Boolean = sp.getBoolean("autoresume", true)
    fun saveAutoResume(b: Boolean) = sp.edit().putBoolean("autoresume", b).apply()
}
