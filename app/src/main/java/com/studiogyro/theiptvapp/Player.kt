package com.studiogyro.theiptvapp

import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
        ExoPlayer.Builder(ctx).setMediaSourceFactory(DefaultMediaSourceFactory(ds)).build()
    }
    DisposableEffect(Unit) { onDispose { player.release() } }

    val tries = remember(ch.id) { attemptsFor(ch) }
    var idx by remember(ch.id) { mutableIntStateOf(0) }
    var status by remember(ch.id) { mutableStateOf("Chargement…") }
    var showUi by remember { mutableStateOf(true) }

    LaunchedEffect(ch.id, idx) {
        val a = tries[idx]
        val item = MediaItem.Builder().setUri(a.url).apply { if (a.mime != null) setMimeType(a.mime) }.build()
        player.setMediaItem(item)
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
                when (ev.key) {
                    Key.DirectionDown, Key.ChannelDown, Key.PageDown -> { vm.step(1); showUi = true; true }
                    Key.DirectionUp, Key.ChannelUp, Key.PageUp -> { vm.step(-1); showUi = true; true }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { showUi = !showUi; true }
                    else -> false
                }
            }
            .pointerInput(Unit) { detectTapGestures { showUi = !showUi } },
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

        if (showUi) {
            Row(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(Color(0xAA000000))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(onClick = { vm.stop() }) { Icon(Icons.Default.ArrowBack, "Retour", tint = Color.White) }
                Text(ch.name, color = Color.White, modifier = Modifier.weight(1f), maxLines = 1)
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
