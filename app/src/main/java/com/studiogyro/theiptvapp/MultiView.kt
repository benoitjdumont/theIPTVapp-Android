@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.studiogyro.theiptvapp

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView

/**
 * Multi-vue : jusqu'à 4 chaînes en direct à l'écran, chacune avec son propre lecteur.
 * Le son reste celui du lecteur principal ; les autres écrans sont muets.
 * Toucher un écran pour le remplacer par une autre chaîne ; « Ajouter un écran » en ajoute un.
 */
@Composable
fun MultiViewScreen(vm: MainVm, start: Channel, onClose: () -> Unit) {
    var panes by remember { mutableStateOf(listOf(start)) }
    var picking by remember { mutableStateOf(false) }
    var replaceAt by remember { mutableStateOf(-1) }

    BackHandler(true) { if (picking) picking = false else onClose() }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (picking) {
            ChannelPicker(vm) { c ->
                panes = if (replaceAt in panes.indices) panes.toMutableList().also { it[replaceAt] = c } else panes + c
                picking = false
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                for (r in 0 until (panes.size + 1) / 2) {
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        for (k in 0..1) {
                            val i = r * 2 + k
                            if (i < panes.size) {
                                MvPane(panes[i], Modifier.weight(1f).fillMaxHeight()) { replaceAt = i; picking = true }
                            } else {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            Row(
                Modifier.align(Alignment.BottomEnd).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (panes.size < 4) Button(onClick = { replaceAt = -1; picking = true }) { Text("Ajouter un écran") }
                Button(onClick = onClose) { Text("Quitter la multi-vue") }
            }
        }
    }
}

/** un écran de la multi-vue : son propre lecteur, muet */
@Composable
private fun MvPane(c: Channel, modifier: Modifier, onTap: () -> Unit) {
    val ctx = LocalContext.current
    val player = remember(c.id) {
        val ds = OkHttpDataSource.Factory(Repo.client).setUserAgent(Repo.streamUa)
        ExoPlayer.Builder(ctx)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(ctx, ds)))
            .build()
            .also { p ->
                p.volume = 0f
                p.setMediaItem(MediaItem.fromUri(c.url))
                p.prepare()
                p.playWhenReady = true
            }
    }
    DisposableEffect(c.id) { onDispose { player.release() } }
    Box(modifier.border(1.dp, Color(0x33FFFFFF)).clickable { onTap() }) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { cx -> PlayerView(cx).apply { useController = false; this.player = player } },
        )
        Text(
            c.name,
            color = Color.White,
            fontSize = 12.sp,
            modifier = Modifier.align(Alignment.TopStart).background(Color(0x99000000)).padding(4.dp),
        )
    }
}

/** liste des chaînes en direct pour choisir un écran */
@Composable
private fun ChannelPicker(vm: MainVm, onPick: (Channel) -> Unit) {
    val live = remember(vm.playlist) { vm.playlist.filter { it.kind == "live" } }
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("Choisir une chaîne", color = Color.White, fontSize = 18.sp)
        LazyColumn(Modifier.fillMaxSize()) {
            items(live, key = { it.id }) { c ->
                Text(
                    c.name,
                    color = Color.White,
                    fontSize = 15.sp,
                    modifier = Modifier.fillMaxWidth().clickable { onPick(c) }.padding(vertical = 8.dp, horizontal = 12.dp),
                )
            }
        }
    }
}
