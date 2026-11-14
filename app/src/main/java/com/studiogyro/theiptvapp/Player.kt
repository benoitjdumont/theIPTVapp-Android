package com.studiogyro.theiptvapp

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.TextButton
import androidx.media3.datasource.DefaultDataSource
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

private data class Attempt(val url: String, val mime: String?)

private fun attemptsFor(c: Channel): List<Attempt> {
    val out = ArrayList<Attempt>()
    out.add(Attempt(c.url, null))
    if (c.kind != "live") return out
    // an address without a clear extension may be an HLS playlist: try it explicitly too
    if (!c.url.contains(".m3u8", ignoreCase = true)) out.add(Attempt(c.url, MimeTypes.APPLICATION_M3U8))
    for (a in c.alt) out.add(Attempt(a, null))
    return out
}

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(vm: MainVm) {
    val ch = vm.current ?: return
    val ctx = LocalContext.current

    val player = remember {
        val ds = OkHttpDataSource.Factory(Repo.client).setUserAgent(Repo.STREAM_UA)
        ExoPlayer.Builder(ctx).setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(ctx, ds))).build()
    }
    DisposableEffect(Unit) { onDispose { player.release() } }

    val tries = remember(ch.id) { attemptsFor(ch) }
    var idx by remember(ch.id) { mutableIntStateOf(0) }
    var status by remember(ch.id) { mutableStateOf("Chargement…") }
    var showUi by remember { mutableStateOf(true) }
    var hud by remember { mutableStateOf("") }
    var playing by remember { mutableStateOf(true) }
    val activity = remember(ctx) {
        var c: Context = ctx
        while (c is ContextWrapper && c !is Activity) c = c.baseContext
        c as? Activity
    }
    LaunchedEffect(hud) { if (hud.isNotEmpty()) { delay(900); hud = "" } }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(vm.recording) { while (vm.recording != null) { now = System.currentTimeMillis(); delay(1000) } }

    LaunchedEffect(ch.id, idx) {
        val a = tries[idx]
        val item = MediaItem.Builder().setUri(a.url).apply { if (a.mime != null) setMimeType(a.mime) }.build()
        val resume = if (ch.kind != "live" && idx == 0) vm.pos(ch.id) else 0L
        if (resume > 0) player.setMediaItem(item, resume) else player.setMediaItem(item)
        player.prepare()
        player.playWhenReady = true
    }

    DisposableEffect(player, ch.id, idx) {
        val l = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                if (idx + 1 < tries.size) {
                    status = "Nouvel essai (${idx + 2}/${tries.size})…"
                    idx += 1
                } else {
                    status = "Impossible de lire cette chaîne.\n(${error.errorCodeName})"
                }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) { playing = playWhenReady }

            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_BUFFERING -> status = "Mise en mémoire…"
                    Player.STATE_READY -> status = ""
                    else -> {}
                }
            }
        }
        player.addListener(l)
        onDispose { player.removeListener(l) }
    }

    LaunchedEffect(ch.id, vm.xmltvReady) { vm.loadEpg(ch) }

    // reprise de lecture : on mémorise la position des films et épisodes
    DisposableEffect(ch.id) {
        onDispose {
            if (ch.kind != "live") runCatching { vm.savePos(ch.id, player.currentPosition, player.duration.coerceAtLeast(0)) }
        }
    }
    LaunchedEffect(ch.id) {
        if (ch.kind != "live") while (true) {
            delay(5000)
            if (player.isPlaying) vm.savePos(ch.id, player.currentPosition, player.duration.coerceAtLeast(0))
        }
    }

    LaunchedEffect(showUi, ch.id) {
        if (showUi) {
            delay(4000)
            showUi = false
        }
    }

    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(showUi) { if (!showUi) runCatching { focus.requestFocus() } }

    BackHandler(true) { vm.stop() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .focusable()
            .onKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onKeyEvent false
                val vod = ch.kind != "live"
                if (vod) {
                    return@onKeyEvent when (ev.key) {
                        Key.DirectionRight -> { player.seekTo(player.currentPosition + 15_000); showUi = true; true }
                        Key.DirectionLeft -> { player.seekTo((player.currentPosition - 15_000).coerceAtLeast(0)); showUi = true; true }
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.MediaPlayPause -> {
                            player.playWhenReady = !player.playWhenReady
                            showUi = true
                            true
                        }
                        Key.DirectionDown, Key.DirectionUp -> { showUi = true; true }
                        else -> false
                    }
                }
                when (ev.key) {
                    Key.DirectionDown, Key.ChannelDown, Key.PageDown -> { vm.step(1); showUi = true; true }
                    Key.DirectionUp, Key.ChannelUp, Key.PageUp -> { vm.step(-1); showUi = true; true }
                    Key.MediaPlayPause -> { player.playWhenReady = !player.playWhenReady; showUi = true; true }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { showUi = !showUi; true }
                    else -> false
                }
            }
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { c ->
                PlayerView(c).apply {
                    useController = false
                    this.player = player
                    keepScreenOn = true
                }
            },
            update = { it.useController = ch.kind != "live" },
        )

        // couche tactile par-dessus la vidéo : gestes + toucher pour afficher/masquer
        // (sur un film, seulement le haut de l'écran, pour ne pas couvrir les boutons du lecteur)
        Box(
            Modifier
                .then(if (ch.kind == "live") Modifier.fillMaxSize() else Modifier.fillMaxWidth().fillMaxHeight(0.38f))
                .pointerInput(ch.id) {
                    val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    val slop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val zone = when {
                            down.position.x < size.width * 0.35f -> 1
                            down.position.x > size.width * 0.65f -> 2
                            else -> 0
                        }
                        var lastY = down.position.y
                        var total = 0f
                        var dragging = false
                        var vol = am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
                        var bright = activity?.window?.attributes?.screenBrightness?.takeIf { it >= 0f }
                            ?: (android.provider.Settings.System.getInt(ctx.contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS, 128) / 255f)
                        while (true) {
                            val ev = awaitPointerEvent()
                            val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (!c.pressed) {
                                if (!dragging) showUi = !showUi
                                else if (zone == 0 && ch.kind == "live" && abs(total) > 100f) {
                                    vm.step(if (total < 0) 1 else -1)
                                    showUi = true
                                }
                                break
                            }
                            val dy = c.position.y - lastY
                            lastY = c.position.y
                            total += dy
                            if (!dragging && abs(total) > slop) dragging = true
                            if (dragging) {
                                c.consume()
                                val frac = -dy / size.height * 2.2f
                                when (zone) {
                                    1 -> activity?.let { a ->
                                        bright = (bright + frac).coerceIn(0.02f, 1f)
                                        val lp = a.window.attributes
                                        lp.screenBrightness = bright
                                        a.window.attributes = lp
                                        hud = "Luminosité ${(bright * 100).roundToInt()} %"
                                    }
                                    2 -> {
                                        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                        vol = (vol + frac * max).coerceIn(0f, max.toFloat())
                                        am.setStreamVolume(AudioManager.STREAM_MUSIC, vol.roundToInt(), 0)
                                        hud = "Volume ${(vol / max * 100).roundToInt()} %"
                                    }
                                }
                            }
                        }
                    }
                }
        )

        if (status.isNotEmpty()) {
            Text(
                status,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color(0x99000000))
                    .padding(16.dp),
            )
        }

        if (hud.isNotEmpty() && !vm.inPip) {
            Text(
                hud, color = Color.White,
                modifier = Modifier.align(Alignment.Center).background(Color(0xAA000000)).padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        if (showUi && !vm.inPip) {
            Row(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(Color(0xAA000000))
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(onClick = { vm.stop() }) { Icon(Icons.Default.ArrowBack, "Retour", tint = Color.White) }
                Column(Modifier.weight(1f)) {
                    Text(ch.name, color = Color.White, maxLines = 1)
                    val e = vm.epg[ch.id]
                    if (e?.now != null) {
                        Text("${e.now.title}  ${hm(e.now.start)}–${hm(e.now.stop)}", color = Color(0xFFCCDDFF), fontSize = 13.sp, maxLines = 1)
                        if (e.next != null) Text("Ensuite : ${e.next.title}  ${hm(e.next.start)}", color = Color(0xFFAAAAAA), fontSize = 12.sp, maxLines = 1)
                    }
                }
                if (ch.kind == "live") {
                    TextButton(onClick = { player.playWhenReady = !player.playWhenReady }) {
                        Text(if (playing) "⏸" else "▶", color = Color.White, fontSize = 20.sp)
                    }
                    val rec = vm.recording
                    TextButton(onClick = { if (rec == null) vm.startRecording(ctx, ch) else vm.stopRecording() }) {
                        val secs = if (rec != null) ((now - rec.startMs) / 1000).coerceAtLeast(0) else 0
                        Text(if (rec == null) "⏺ REC" else "⏹ %d:%02d".format(secs / 60, secs % 60), color = if (rec == null) Color.White else Color(0xFFFF6B6B))
                    }
                }
                TextButton(onClick = { (activity as? MainActivity)?.enterPip() }) { Text("PiP", color = Color.White) }
                val fav = vm.isFav(ch)
                IconButton(onClick = { vm.toggleFav(ch) }) {
                    Icon(
                        if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        "Favori",
                        tint = Color.White,
                    )
                }
            }
        }
    }
}
