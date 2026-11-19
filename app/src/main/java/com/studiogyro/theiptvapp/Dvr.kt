package com.studiogyro.theiptvapp

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/**
 * DVR en continu : pendant qu'une chaîne en direct joue, son flux TS est écrit sur le disque
 * en morceaux d'environ une minute. Chaque morceau commence sur une image-clé (paquet TS avec
 * « random access »), donc il se lit seul. Un morceau terminé a un fichier `<début>.len` (durée en ms).
 * Un fichier `index.m3u8` (playlist HLS VOD) liste les morceaux terminés : c'est lui que le lecteur
 * ouvre pour revenir en arrière. Limites : 2 h et 6 Go, les plus vieux morceaux sont supprimés.
 * Un seul canal à la fois.
 */
object Dvr {
    const val MAX_AGE_MS = 2L * 3600_000L
    const val MAX_BYTES = 6L * 1024 * 1024 * 1024
    const val CHUNK_MS = 60_000L
    private const val PKT = 188

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    @Volatile private var job: Job? = null
    @Volatile private var call: okhttp3.Call? = null
    @Volatile var channelId: String? = null
        private set

    class Seg(val start: Long, val len: Long, val file: File)

    fun dirFor(ctx: Context, channelId: String): File {
        val safe = channelId.replace(Regex("[^A-Za-z0-9_-]"), "_")
        return File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "dvr/$safe")
    }

    fun playlistFile(ctx: Context, channelId: String): File = File(dirFor(ctx, channelId), "index.m3u8")

    /** morceaux terminés, dans l'ordre du temps */
    fun segments(ctx: Context, channelId: String): List<Seg> = segmentsIn(dirFor(ctx, channelId))

    private fun segmentsIn(dir: File): List<Seg> {
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles { f -> f.name.endsWith(".ts") }.orEmpty().mapNotNull { f ->
            val start = f.name.removeSuffix(".ts").toLongOrNull() ?: return@mapNotNull null
            val lenF = File(dir, "$start.len")
            if (!lenF.exists()) return@mapNotNull null
            val len = lenF.readText().trim().toLongOrNull() ?: return@mapNotNull null
            Seg(start, len, f)
        }.sortedBy { it.start }
    }

    /** position de lecture (ms) qui correspond à l'heure [wall], en additionnant les morceaux */
    fun positionFor(segs: List<Seg>, wall: Long): Long {
        var cum = 0L
        for (s in segs) {
            if (wall < s.start) return cum
            if (wall < s.start + s.len) return cum + (wall - s.start)
            cum += s.len
        }
        return cum
    }

    fun totalMs(segs: List<Seg>): Long = segs.sumOf { it.len }

    /** Démarre l'enregistrement du canal en direct (arrête le précédent). Ne fait rien pour films et séries. */
    fun start(ctx: Context, c: Channel) {
        if (c.kind != "live") { stop(); return }
        if (channelId == c.id && job?.isActive == true) return
        stop()
        val url = c.alt.firstOrNull { it.contains(".ts", ignoreCase = true) } ?: c.url
        // seuls les flux TS sont enregistrés pour l'instant (pas les flux HLS .m3u8)
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
                val buf = ByteArray(64 * 1024)
                var carry = ByteArray(0)
                var synced = false
                var seg: FileOutputStream? = null
                var segStart = 0L
                try {
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        val now = System.currentTimeMillis()
                        var data = carry + buf.copyOf(n)
                        if (!synced) {
                            // on se cale sur le premier paquet TS complet (octet de synchro 0x47 suivi d'un autre)
                            val k = (0 until minOf(PKT, data.size)).firstOrNull { i ->
                                data[i] == 0x47.toByte() && (i + PKT >= data.size || data[i + PKT] == 0x47.toByte())
                            }
                            if (k == null) { carry = data; continue }
                            data = data.copyOfRange(k, data.size)
                            synced = true
                        }
                        val whole = data.size - data.size % PKT
                        var p = 0
                        while (p < whole) {
                            val cur = seg
                            if (cur == null) {
                                segStart = now
                                seg = FileOutputStream(File(dir, "$now.ts"))
                                continue
                            }
                            if (now - segStart >= CHUNK_MS && (isRai(data, p) || now - segStart >= 3 * CHUNK_MS)) {
                                closeSeg(dir, cur, segStart, now)
                                seg = null
                                continue
                            }
                            cur.write(data, p, PKT)
                            p += PKT
                        }
                        carry = data.copyOfRange(whole, data.size)
                    }
                } finally {
                    seg?.let { closeSeg(dir, it, segStart, System.currentTimeMillis()) }
                }
            }
        }
    }

    /** paquet TS avec « random access » dans son champ d'adaptation : début possible d'un morceau décodable */
    private fun isRai(d: ByteArray, p: Int): Boolean {
        if (d[p] != 0x47.toByte()) return false
        val afc = (d[p + 3].toInt() shr 4) and 3
        if (afc and 2 == 0) return false
        if ((d[p + 4].toInt() and 0xFF) < 1) return false
        return (d[p + 5].toInt() and 0x40) != 0
    }

    private fun closeSeg(dir: File, out: FileOutputStream, start: Long, end: Long) {
        out.close()
        File(dir, "$start.len").writeText((end - start).coerceAtLeast(1L).toString())
        prune(dir)
        writePlaylist(dir, segmentsIn(dir))
    }

    /** Supprime les morceaux trop vieux, puis les plus vieux tant que la limite de taille est dépassée. Le plus récent est gardé. */
    private fun prune(dir: File) {
        val ts = dir.listFiles { f -> f.name.endsWith(".ts") }.orEmpty()
            .sortedBy { it.name.removeSuffix(".ts").toLongOrNull() ?: 0L }
        if (ts.isEmpty()) return
        val limitAge = System.currentTimeMillis() - MAX_AGE_MS
        fun drop(f: File) {
            f.delete()
            File(dir, f.name.removeSuffix(".ts") + ".len").delete()
        }
        val keep = ts.dropLast(1).filter { f ->
            val s = f.name.removeSuffix(".ts").toLongOrNull() ?: 0L
            if (s < limitAge) { drop(f); false } else true
        }
        var total = keep.sumOf { it.length() } + ts.last().length()
        for (f in keep) {
            if (total <= MAX_BYTES) break
            total -= f.length()
            drop(f)
        }
    }

    /** playlist HLS VOD des morceaux terminés (un trou d'enregistrement devient une rupture de continuité) */
    private fun writePlaylist(dir: File, segs: List<Seg>) {
      synchronized(lock) {
        if (segs.isEmpty()) return
        val target = ((segs.maxOf { it.len } + 999) / 1000).coerceAtLeast(1L)
        val sb = StringBuilder()
        sb.append("#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-PLAYLIST-TYPE:VOD\n")
        sb.append("#EXT-X-TARGETDURATION:$target\n#EXT-X-MEDIA-SEQUENCE:0\n")
        var prevEnd = -1L
        for (s in segs) {
            if (prevEnd >= 0 && s.start > prevEnd + 1500) sb.append("#EXT-X-DISCONTINUITY\n")
            sb.append(String.format(Locale.US, "#EXTINF:%.3f,\n", s.len / 1000.0))
            sb.append(Uri.fromFile(s.file).toString()).append('\n')
            prevEnd = s.start + s.len
        }
        sb.append("#EXT-X-ENDLIST\n")
        val tmp = File(dir, "index.m3u8.tmp")
        tmp.writeText(sb.toString())
        tmp.renameTo(File(dir, "index.m3u8"))
      }
    }

    /** Pour une future option « Effacer le DVR » : vide le dossier du canal. */
    fun clear(ctx: Context, channelId: String) {
        dirFor(ctx, channelId).listFiles()?.forEach { it.delete() }
    }
}
