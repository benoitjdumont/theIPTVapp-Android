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
}
