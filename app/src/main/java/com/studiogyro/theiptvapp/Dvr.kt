package com.studiogyro.theiptvapp

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

/**
 * DVR en continu : pendant qu'une chaîne en direct joue, son flux est écrit sur le disque
 * par morceaux d'une minute. On garde au plus [MAX_AGE_MS] (2 h) et [MAX_BYTES] (6 Go) :
 * les plus vieux morceaux sont supprimés. Un seul canal à la fois.
 *
 * Pas encore de lecture en arrière : c'est l'étape suivante (barre de temps dans le lecteur).
 */
object Dvr {
    const val MAX_AGE_MS = 2L * 3600_000L
    const val MAX_BYTES = 6L * 1024 * 1024 * 1024
    const val CHUNK_MS = 60_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var job: Job? = null
    @Volatile private var call: okhttp3.Call? = null
    @Volatile var channelId: String? = null
        private set

    fun dirFor(ctx: Context, channelId: String): File {
        val safe = channelId.replace(Regex("[^A-Za-z0-9_-]"), "_")
        return File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "dvr/$safe")
    }

    /** Démarre l'enregistrement du canal en direct (arrête le précédent). Ne fait rien pour films et séries. */
    fun start(ctx: Context, c: Channel) {
        if (c.kind != "live") { stop(); return }
        if (channelId == c.id && job?.isActive == true) return
        stop()
        val url = c.alt.firstOrNull { it.contains(".ts", ignoreCase = true) } ?: c.url
        // Phase 1 : flux TS seulement (les flux HLS ne sont pas enregistrés en continu pour l'instant)
        if (url.contains(".m3u8", ignoreCase = true)) return
        val dir = dirFor(ctx, c.id).apply { mkdirs() }
        channelId = c.id
        job = scope.launch {
            var failures = 0
            while (isActive) {
                try {
                    recordOnce(url, dir)
                    failures = 0
                } catch (e: Exception) {
                    if (!isActive) break
                    if (++failures > 5) break
                }
                if (!isActive) break
                delay(3000)
            }
        }
    }

    fun stop() {
        channelId = null
        call?.cancel()
        job?.cancel()
        job = null
    }

    private suspend fun recordOnce(url: String, dir: File) {
        val req = Request.Builder().url(url).header("User-Agent", Repo.streamUa).build()
        val cl = Repo.client.newCall(req)
        call = cl
        cl.execute().use { r ->
            if (!r.isSuccessful) throw RuntimeException("HTTP ${r.code}")
            val body = r.body ?: return
            body.byteStream().use { input ->
                var out: FileOutputStream? = null
                var chunkStart = 0L
                val buf = ByteArray(64 * 1024)
                try {
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val now = System.currentTimeMillis()
                        if (out == null || now - chunkStart >= CHUNK_MS) {
                            out?.close()
                            out = FileOutputStream(File(dir, "$now.ts"))
                            chunkStart = now
                            prune(dir, now)
                        }
                        val n = input.read(buf)
                        if (n < 0) break
                        out!!.write(buf, 0, n)
                    }
                } finally {
                    out?.close()
                }
            }
        }
    }

    /** Supprime les morceaux trop vieux, puis les plus vieux tant que la limite de taille est dépassée. Le morceau en cours est gardé. */
    private fun prune(dir: File, now: Long) {
        val files = dir.listFiles { f -> f.name.endsWith(".ts") }?.sortedBy { it.name.removeSuffix(".ts").toLongOrNull() ?: 0L } ?: return
        if (files.isEmpty()) return
        val keep = files.filter { (it.name.removeSuffix(".ts").toLongOrNull() ?: 0L) >= now - MAX_AGE_MS }
        files.filterNot { it in keep }.forEach { it.delete() }
        var total = keep.sumOf { it.length() }
        for (f in keep.dropLast(1)) {
            if (total <= MAX_BYTES) break
            total -= f.length()
            f.delete()
        }
    }

    /** Pour une future option « Effacer le DVR » : vide le dossier du canal. */
    fun clear(ctx: Context, channelId: String) {
        dirFor(ctx, channelId).listFiles()?.forEach { it.delete() }
    }
}
