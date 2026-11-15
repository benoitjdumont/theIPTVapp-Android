package com.studiogyro.theiptvapp

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.text.style.TextAlign
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.focus.onFocusChanged
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Tracks
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.foundation.text.KeyboardOptions
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

internal data class Attempt(val url: String, val mime: String?)

internal fun attemptsFor(c: Channel): List<Attempt> {
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
    val tvPad = remember {
        if ((ctx.getSystemService(Context.UI_MODE_SERVICE) as android.app.UiModeManager).currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION) 40.dp else 0.dp
    }

    val player = remember {
        val ds = OkHttpDataSource.Factory(Repo.client).setUserAgent(Repo.streamUa)
        ExoPlayer.Builder(ctx).setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(ctx, ds))).build()
    }
    DisposableEffect(Unit) { onDispose { player.release() } }

    val tries = remember(ch.id) { attemptsFor(ch) }
    var idx by remember(ch.id) { mutableIntStateOf(0) }
    var status by remember(ch.id) { mutableStateOf("Chargement…") }
    var showUi by remember { mutableStateOf(true) }
    var hud by remember { mutableStateOf("") }
    var playing by remember { mutableStateOf(true) }
    // menu du bas (comme la télé Samsung) et liste des chaînes par-dessus la vidéo
    var menuOpen by remember(ch.id) { mutableStateOf(false) }
    var stripOpen by remember(ch.id) { mutableStateOf(false) }
    var displayMode by remember { mutableIntStateOf(0) }
    var sleepMin by remember { mutableIntStateOf(0) }
    var picker by remember(ch.id) { mutableStateOf<String?>(null) }
    val stripFirst = remember { FocusRequester() }
    var listOpen by remember(ch.id) { mutableStateOf(false) }
    val menuFirst = remember { FocusRequester() }
    val listFirst = remember { FocusRequester() }
    val focusRoot = remember { FocusRequester() }
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
    // menu ouvert : le focus va sur le menu (la télécommande le parcourt) ; fermé : retour à la vidéo
    // minuterie : arrête la lecture après le temps choisi
    LaunchedEffect(sleepMin) {
        if (sleepMin > 0) {
            delay(sleepMin * 60_000L)
            player.playWhenReady = false
            hud = "Minuterie : lecture arrêtée"
            sleepMin = 0
        }
    }

    LaunchedEffect(stripOpen) { if (stripOpen) runCatching { stripFirst.requestFocus() } }
    LaunchedEffect(menuOpen) { if (menuOpen) runCatching { menuFirst.requestFocus() } }
    LaunchedEffect(listOpen) { if (listOpen) runCatching { listFirst.requestFocus() } }
    LaunchedEffect(menuOpen, listOpen, stripOpen) { if (!menuOpen && !listOpen && !stripOpen) runCatching { focus.requestFocus() } }

    // Retour : ferme d'abord la liste ou le menu, puis revient à l'aperçu (le flux continue)
    BackHandler(true) {
        when {
            listOpen -> listOpen = false
            menuOpen -> menuOpen = false
            stripOpen -> stripOpen = false
            else -> vm.stop()
        }
    }

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
                // menu ou liste ouverts : les flèches déplacent le focus dans le menu, sans changer de chaîne
                if (menuOpen || listOpen || stripOpen) return@onKeyEvent false
                when (ev.key) {
                    Key.ChannelDown, Key.PageDown -> { vm.step(1); showUi = true; true }
                    Key.ChannelUp, Key.PageUp -> { vm.step(-1); showUi = true; true }
                    // flèche bas : menu complet ; flèche haut : derniers canaux
                    Key.DirectionDown -> { menuOpen = true; showUi = true; true }
                    Key.DirectionUp -> { stripOpen = true; showUi = true; true }
                    Key.MediaPlayPause -> { player.playWhenReady = !player.playWhenReady; showUi = true; true }
                    // bouton central de la manette : ouvre le menu du bas
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { stripOpen = true; showUi = true; true }
                    else -> false
                }
            }
    ) {
        AndroidView(
            modifier = when (displayMode) {
                2 -> Modifier.align(Alignment.Center).fillMaxHeight().aspectRatio(4f / 3f)
                3 -> Modifier.align(Alignment.Center).fillMaxHeight().aspectRatio(16f / 9f)
                else -> Modifier.fillMaxSize()
            },
            factory = { c ->
                PlayerView(c).apply {
                    useController = false
                    this.player = player
                    keepScreenOn = true
                }
            },
            update = {
                it.useController = ch.kind != "live"
                it.resizeMode = if (displayMode == 1) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
            },
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

        // bas de l'écran (manette et téléphone) : derniers canaux, puis le menu complet plus bas
        if (ch.kind == "live" && !vm.inPip) {
            val rec = vm.recording
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (stripOpen) {
                    val recents = vm.recentChannels()
                    LazyRow(
                        Modifier
                            .fillMaxWidth()
                            .background(Color(0xD9000000))
                            .padding(horizontal = tvPad + 8.dp, vertical = 8.dp)
                            .onKeyEvent { ev ->
                                if (ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionDown) { stripOpen = false; menuOpen = true; true } else false
                            },
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        itemsIndexed(recents, key = { _, c -> c.id }) { idx, c ->
                            RecentTile(
                                c,
                                selected = c.id == ch.id,
                                modifier = if (idx == 0) Modifier.focusRequester(stripFirst) else Modifier,
                            ) { stripOpen = false; vm.play(vm.playlist, c) }
                        }
                    }
                }
                if (menuOpen) {
                    val rec = vm.recording
                    fun close() { menuOpen = false }
                    val row1 = listOf<Pair<String, () -> Unit>>(
                        "Résolution" to { close(); picker = "video" },
                        "Préférences" to { close(); vm.stop(); vm.openSettingsRequest = true },
                        "Sous-titres (CC)" to { close(); picker = "text" },
                        "Image dans l'image" to { close(); (activity as? MainActivity)?.enterPip() },
                        (if (rec == null) "Enregistrer" else "Arrêter l'enregistrement") to {
                            if (rec == null) vm.startRecording(ctx, ch) else vm.stopRecording()
                            close()
                        },
                        "Multi-vue" to { close(); hud = "Multi-vue : bientôt disponible" },
                    )
                    val row2 = listOf<Pair<String, () -> Unit>>(
                        "Liste des chaînes" to { close(); listOpen = true },
                        "Rechercher" to { close(); listOpen = true },
                        "Audio" to { close(); picker = "audio" },
                        "Affichage : " + displayModes[displayMode] to { displayMode = (displayMode + 1) % displayModes.size },
                        "Minuterie : " + (if (sleepMin == 0) "désactivée" else "$sleepMin min") to {
                            sleepMin = sleepSteps[(sleepSteps.indexOf(sleepMin) + 1) % sleepSteps.size]
                        },
                        (if (vm.isFav(ch)) "Retirer des favoris" else "Ajouter aux favoris") to { vm.toggleFav(ch) },
                    )
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(Color(0xE6000000))
                            .padding(horizontal = tvPad + 8.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row1.forEachIndexed { i, (label, action) ->
                                PlayerMenuItem(label, if (i == 0) Modifier.focusRequester(menuFirst) else Modifier, onClick = action)
                            }
                        }
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row2.forEach { (label, action) -> PlayerMenuItem(label, Modifier, onClick = action) }
                        }
                    }
                }
                // flèche : ouvre les derniers canaux, puis le menu plus bas
                Box(
                    Modifier
                        .width(72.dp)
                        .height(24.dp)
                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                        .background(Color(0xAA000000))
                        .clickable {
                            when {
                                !stripOpen && !menuOpen -> stripOpen = true
                                stripOpen -> { stripOpen = false; menuOpen = true }
                                else -> { menuOpen = false; stripOpen = false }
                            }
                            showUi = true
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (stripOpen || menuOpen) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                        "Menu", tint = Color.White,
                    )
                }
            }
        }

        // liste des chaînes / recherche, à droite
        if (listOpen && ch.kind == "live") {
            var q by remember { mutableStateOf("") }
            val all = vm.playlist
            val shown = remember(q, all) { all.filter { q.isBlank() || it.name.contains(q, ignoreCase = true) } }
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxHeight().clickable { listOpen = false })
                Column(Modifier.width(340.dp).fillMaxHeight().background(Color(0xEE101828)).padding(8.dp)) {
                    OutlinedTextField(
                        value = q,
                        onValueChange = { q = it },
                        singleLine = true,
                        placeholder = { Text("Rechercher une chaîne") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    LazyColumn(Modifier.fillMaxSize().padding(top = 6.dp)) {
                        itemsIndexed(shown, key = { _, c -> c.id }) { idx, c ->
                            Text(
                                vm.displayName(c),
                                color = if (c.id == ch.id) Color(0xFF7FB2FF) else Color.White,
                                maxLines = 1,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .let { if (idx == 0) it.focusRequester(listFirst) else it }
                                    .clickable { listOpen = false; vm.play(all, c) }
                                    .padding(horizontal = 10.dp, vertical = 10.dp),
                            )
                        }
                    }
                }
            }
        }

        // choix de résolution, sous-titres ou audio
        picker?.let { kind -> TrackPickerDialog(player, kind) { picker = null } }

        if (showUi && !vm.inPip) {
            Row(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(Color(0xAA000000))
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                    .padding(horizontal = tvPad, vertical = tvPad / 2)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(onClick = { vm.stop() }) { Icon(Icons.Default.ArrowBack, "Retour", tint = Color.White) }
                Column(Modifier.weight(1f)) {
                    val pos = vm.playlist.indexOf(ch)
                    Text(
                        vm.displayName(ch) + if (pos >= 0 && ch.kind == "live") "   ${pos + 1} / ${vm.playlist.size}" else "",
                        color = Color.White, maxLines = 1,
                    )
                    val e = vm.epg[ch.id]
                    if (e?.now != null) {
                        Text("${e.now.title}  ${hm(e.now.start)}–${hm(e.now.stop)}", color = Color(0xFFCCDDFF), fontSize = 13.sp, maxLines = 1)
                        if (e.next != null) Text("Ensuite : ${e.next.title}  ${hm(e.next.start)}", color = Color(0xFFAAAAAA), fontSize = 12.sp, maxLines = 1)
                    }
                }
                if (ch.kind == "live") {
                    Button(onClick = { player.playWhenReady = !player.playWhenReady }) {
                        Text(if (playing) "Pause" else "Lecture", fontSize = 13.sp)
                    }
                    val rec = vm.recording
                    OutlinedButton(onClick = { if (rec == null) vm.startRecording(ctx, ch) else vm.stopRecording() }) {
                        val secs = if (rec != null) ((now - rec.startMs) / 1000).coerceAtLeast(0) else 0
                        Text(if (rec == null) "Enregistrer" else "Arrêter %d:%02d".format(secs / 60, secs % 60), fontSize = 13.sp, color = if (rec == null) Color.White else Color(0xFFFF6B6B))
                    }
                }
                OutlinedButton(onClick = { (activity as? MainActivity)?.enterPip() }) { Text("Image dans l'image", fontSize = 13.sp) }
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

private val displayModes = listOf("Normal", "Zoom", "4:3", "16:9")
private val sleepSteps = listOf(0, 15, 30, 60, 90)

@Composable
private fun PlayerMenuItem(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Text(
        label,
        color = Color.White,
        fontSize = 14.sp,
        maxLines = 1,
        textAlign = TextAlign.Center,
        modifier = modifier
            .widthIn(min = 150.dp)
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(6.dp))
            .background(if (focused) Accent else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    )
}

/** Une chaîne récente, dans la rangée du bas. */
@Composable
private fun RecentTile(c: Channel, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier
            .width(150.dp)
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) Accent else if (selected) Accent.copy(alpha = 0.5f) else Color(0xFF111A2E))
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (c.logo.isNotBlank()) AsyncImage(model = c.logo, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(40.dp))
        else Spacer(Modifier.height(40.dp))
        Text(c.name, color = Color.White, fontSize = 12.sp, maxLines = 1)
    }
}

private fun trackLabel(f: Format): String {
    val parts = mutableListOf<String>()
    if (f.label != null) parts += f.label!!
    if (f.language != null && f.language != "und") parts += f.language!!
    if (f.height > 0) parts += "${f.width}×${f.height}"
    if (f.channelCount > 0) parts += "${f.channelCount} canaux"
    return parts.joinToString(" · ").ifEmpty { "Piste" }
}

/** Choix de la résolution, des sous-titres ou de l'audio, selon les pistes du flux. */
@Composable
private fun TrackPickerDialog(player: ExoPlayer, kind: String, onDone: () -> Unit) {
    val type = when (kind) { "video" -> C.TRACK_TYPE_VIDEO; "audio" -> C.TRACK_TYPE_AUDIO; else -> C.TRACK_TYPE_TEXT }
    val title = when (kind) { "video" -> "Résolution"; "audio" -> "Audio"; else -> "Sous-titres (CC)" }
    val options = remember(kind) {
        val out = mutableListOf<Triple<String, Tracks.Group?, Int>>()
        if (kind != "audio") out += Triple(if (kind == "video") "Automatique" else "Désactivés", null, -1)
        player.currentTracks.groups.filter { it.type == type }.forEach { g ->
            for (i in 0 until g.length) if (g.isTrackSupported(i)) out += Triple(trackLabel(g.getTrackFormat(i)), g, i)
        }
        out
    }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(title) },
        text = {
            if (options.size <= 1 && kind != "text") Text("Aucune piste disponible sur cette chaîne.")
            else LazyColumn(Modifier.heightIn(max = 300.dp)) {
                items(options) { (label, group, index) ->
                    Text(
                        label, color = Color.White, maxLines = 2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val b = player.trackSelectionParameters.buildUpon()
                                if (group == null) {
                                    b.clearOverridesOfType(type)
                                    b.setTrackTypeDisabled(type, kind == "text")
                                } else {
                                    b.setTrackTypeDisabled(type, false)
                                    b.clearOverridesOfType(type)
                                    b.addOverride(TrackSelectionOverride(group.mediaTrackGroup, index))
                                }
                                player.trackSelectionParameters = b.build()
                                onDone()
                            }
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text("Fermer") } },
    )
}
