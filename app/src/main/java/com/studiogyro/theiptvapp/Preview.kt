package com.studiogyro.theiptvapp

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.compose.runtime.LaunchedEffect

/** Petite fenêtre vidéo de l'accueil : la chaîne choisie joue ici avant de passer en plein écran. */
@OptIn(UnstableApi::class)
@Composable
fun PreviewBox(ch: Channel, modifier: Modifier = Modifier, paused: Boolean = false) {
    val ctx = LocalContext.current
    val player = remember {
        val ds = OkHttpDataSource.Factory(Repo.client).setUserAgent(Repo.streamUa)
        ExoPlayer.Builder(ctx)
            .setLoadControl(androidx.media3.exoplayer.DefaultLoadControl.Builder().setBufferDurationsMs(2000, 6000, 800, 1500).build())
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(ctx, ds))).build()
    }
    DisposableEffect(Unit) { onDispose { player.release() } }
    val tries = remember(ch.id) { attemptsFor(ch) }
    var idx by remember(ch.id) { mutableIntStateOf(0) }

    LaunchedEffect(ch.id, idx) {
        val a = tries[idx]
        val item = MediaItem.Builder().setUri(a.url).apply { if (a.mime != null) setMimeType(a.mime) }.build()
        player.setMediaItem(item)
        player.prepare()
        player.playWhenReady = !paused
    }
    DisposableEffect(player, ch.id) {
        val l = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                if (idx + 1 < tries.size) idx++
            }
        }
        player.addListener(l)
        onDispose { player.removeListener(l) }
    }
    Box(modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { c ->
                PlayerView(c).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    this.player = player
                }
            },
        )
    }
}
