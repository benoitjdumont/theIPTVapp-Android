package com.studiogyro.theiptvapp

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
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
        // grosse mémoire tampon (≈ 100 Mo) : on peut mettre le direct en pause longtemps et, sur les flux qui le permettent, reculer
        val lc = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(30_000, 600_000, 2_500, 5_000)
            .setTargetBufferBytes(100 * 1024 * 1024)
            .setPrioritizeTimeOverSizeThresholds(false)
            .setBackBuffer(300_000, true)
            .build()
        ExoPlayer.Builder(ctx).setLoadControl(lc).setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(ctx, ds))).build()
    }
    DisposableEffect(Unit) { onDispose { player.release() } }

    val tries = remember(ch.id) { attemptsFor(ch) }
    var idx by remember(ch.id) { mutableIntStateOf(0) }
    var status by remember(ch.id) { mutableStateOf("Chargement…") }
    var showUi by remember { mutableStateOf(true) }
    var hud by remember { mutableStateOf("") }
    var playing by remember { mutableStateOf(true) }
    // menu du bas (comme la télé Samsung) et liste des chaînes par-dessus la vidéo
    // menu : 0 fermé ; 1 = historique des chaînes seul ; 2 = rangée d'options (remplace l'historique)
    var menuStage by remember(ch.id) { mutableIntStateOf(0) }
    val stripFirst = remember { FocusRequester() }
    var displayMode by remember { mutableIntStateOf(0) }
    var sleepMin by remember { mutableIntStateOf(0) }
    var picker by remember(ch.id) { mutableStateOf<String?>(null) }
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
    // le menu disparaît en même temps que la barre (après le même délai)
    // l'interface (pause / enregistrer / titre) s'affiche avec l'historique et la flèche des options ; tout disparaît ensemble
    // même affichage que la TV en direct : la barre d'options (fond sombre, enregistrer, favoris, PIP…) apparaît avec les commandes
    LaunchedEffect(showUi) { if (!showUi) menuStage = 0 else if (menuStage == 0) menuStage = if (ch.kind == "live") 1 else 2 }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(vm.recording) { while (vm.recording != null) { now = System.currentTimeMillis(); delay(1000) } }

    // sous-titres chargés depuis une adresse (.srt / .vtt), valables pour la chaîne en cours
    val speeds = remember { listOf(0.75f, 1f, 1.25f, 1.5f, 2f) }
    var speedIdx by remember(ch.id) { mutableStateOf(1) }
    LaunchedEffect(ch.id) { player.setPlaybackSpeed(1f) }
    // DVR : le canal en direct s'enregistre en continu (2 h / 6 Go) pendant qu'il joue
    LaunchedEffect(ch.id) { Dvr.start(ctx, ch) }
    DisposableEffect(Unit) { onDispose { Dvr.stop() } }
    // décalage par rapport au direct (flux qui le permettent) ; on le lit chaque seconde
    var liveOffsetMs by remember(ch.id) { mutableStateOf(0L) }
    LaunchedEffect(ch.id) {
        while (ch.kind == "live") {
            // retard réel = décalage actuel moins le décalage normal du direct (les flux HLS ont déjà 10 à 30 s de marge)
            val off = player.currentLiveOffset.takeIf { it != C.TIME_UNSET } ?: 0L
            val tl = player.currentTimeline
            val normal = if (tl.isEmpty) 0L else androidx.media3.common.Timeline.Window().let { w ->
                tl.getWindow(player.currentMediaItemIndex, w)
                w.liveConfiguration?.targetOffsetMs?.takeIf { it != C.TIME_UNSET } ?: 0L
            }
            liveOffsetMs = (off - normal).coerceAtLeast(0L)
            kotlinx.coroutines.delay(1000)
        }
    }
    var subUrl by remember(ch.id) { mutableStateOf<String?>(null) }
    var subStart by remember(ch.id) { mutableStateOf(0L) }
    // DVR : état du retour en arrière (liveKick relance le direct)
    var dvrOn by remember(ch.id) { mutableStateOf(false) }
    var liveKick by remember(ch.id) { mutableStateOf(0) }
    var dvrTotal by remember(ch.id) { mutableStateOf(0L) }
    var dvrBehind by remember(ch.id) { mutableStateOf(0L) }
    LaunchedEffect(ch.id, idx, subUrl, liveKick) {
        val a = tries[idx]
        val item = MediaItem.Builder().setUri(a.url).apply {
            if (a.mime != null) setMimeType(a.mime)
            subUrl?.let { s ->
                val mime = if (s.substringBefore('?').lowercase().endsWith(".vtt")) androidx.media3.common.MimeTypes.TEXT_VTT else androidx.media3.common.MimeTypes.APPLICATION_SUBRIP
                setSubtitleConfigurations(listOf(MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(s)).setMimeType(mime).setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build()))
            }
        }.build()
        val resume = if (ch.kind != "live") (if (subStart > 0) subStart else if (idx == 0) vm.pos(ch.id) else 0L) else 0L
        if (resume > 0) player.setMediaItem(item, resume) else player.setMediaItem(item)
        player.prepare()
        player.playWhenReady = true
    }

    /** revient en arrière de [backMs] dans le DVR (la lecture part de la playlist des morceaux enregistrés) */
    fun dvrGo(backMs: Long) {
        val segs = Dvr.segments(ctx, ch.id)
        if (segs.isEmpty()) { hud = "DVR : rien d'enregistré encore, attends une minute"; return }
        val pos = Dvr.positionFor(segs, System.currentTimeMillis() - backMs)
        dvrTotal = Dvr.totalMs(segs)
        player.setMediaItem(
            MediaItem.Builder().setUri(android.net.Uri.fromFile(Dvr.playlistFile(ctx, ch.id)))
                .setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8).build(),
            pos,
        )
        player.prepare()
        player.playWhenReady = true
        dvrOn = true
    }

    fun goLive() { dvrOn = false; liveKick++ }

    // suivi du retard pendant le DVR ; à la fin de l'enregistrement lu, retour automatique au direct
    LaunchedEffect(dvrOn) {
        while (dvrOn) {
            dvrBehind = (dvrTotal - player.currentPosition).coerceAtLeast(0L)
            if (player.playbackState == Player.STATE_ENDED) { goLive(); break }
            delay(1000)
        }
    }

    DisposableEffect(player, ch.id, idx) {
        val l = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                if (idx + 1 < tries.size) {
                    status = "Nouvel essai (${idx + 2}/${tries.size})…"
                    idx += 1
                } else {
                    val code = generateSequence(error.cause) { it.cause }
                        .filterIsInstance<androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException>()
                        .firstOrNull()?.responseCode
                    val hint = when (code) {
                        401, 403 -> "Accès refusé par le fournisseur : vérifie l'identifiant et le mot de passe, le nombre de connexions permises (un autre appareil regarde-t-il en même temps ?) ou le User-Agent dans Réglages > Réseau."
                        404 -> "Flux introuvable chez le fournisseur."
                        in 500..599 -> "Le serveur du fournisseur a un problème."
                        else -> null
                    }
                    status = "Impossible de lire cette chaîne.\n(${error.errorCodeName}${if (code != null) " · HTTP $code" else ""})" + (if (hint != null) "\n$hint" else "")
                }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) { playing = playWhenReady }

            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_BUFFERING -> status = "Mise en mémoire…"
                    Player.STATE_READY -> status = ""
                    // fin d'un épisode : on enchaîne avec le suivant (pas pour un film seul ni le direct)
                    Player.STATE_ENDED -> if (ch.kind != "live" && vm.playIndex + 1 < vm.playlist.size) vm.step(1)
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

    // la barre disparaît après un moment sans action ; plus long quand un menu est ouvert, et chaque touche ou toucher repousse le délai
    val lastTouch = remember { longArrayOf(System.currentTimeMillis()) }
    fun poke() { lastTouch[0] = System.currentTimeMillis() }
    LaunchedEffect(showUi, ch.id) {
        if (showUi) {
            poke()
            while (true) {
                delay(400)
                val limit = if (menuStage == 2 || listOpen || picker != null) 10_000L else 6_000L
                if (System.currentTimeMillis() - lastTouch[0] > limit) { showUi = false; break }
            }
        }
    }

    // position des films et séries, rafraîchie pendant que la barre est affichée
    var vodPos by remember { mutableStateOf(0L) }
    var vodDur by remember { mutableStateOf(0L) }
    LaunchedEffect(showUi, ch.id) {
        if (ch.kind != "live" && showUi) while (true) {
            vodPos = player.currentPosition
            vodDur = player.duration.coerceAtLeast(0L)
            delay(500)
        }
    }

    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    // appli en arrière-plan (hors fenêtre flottante) : la lecture s'arrête, elle ne continue pas en silence
    LaunchedEffect(AppState.stopped.value) {
        if (AppState.stopped.value && !AppState.pip.value) player.playWhenReady = false
    }
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

    LaunchedEffect(menuStage) {
        when (menuStage) {
            1 -> runCatching { stripFirst.requestFocus() }
            2 -> runCatching { menuFirst.requestFocus() }
        }
    }
    LaunchedEffect(listOpen) { if (listOpen) runCatching { listFirst.requestFocus() } }
    LaunchedEffect(menuStage, listOpen) { if (menuStage == 0 && !listOpen) runCatching { focus.requestFocus() } }

    fun openMenu(stage: Int) {
        // pas d'historique à montrer : on passe directement aux options
        menuStage = stage
    }

    // Retour : ferme d'abord la liste ou le menu, puis revient à l'aperçu (le flux continue)
    BackHandler(true) {
        when {
            listOpen -> listOpen = false
            menuStage == 2 -> if (ch.kind == "live") menuStage = 1 else { showUi = false; menuStage = 0 }
            menuStage == 1 -> { showUi = false; menuStage = 0 }
            else -> vm.stop(!playing)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .focusable()
            .onKeyEvent { ev ->
                poke()
                // bouton central : le menu s'ouvre au RELÂCHER (sinon le clic arrive sur l'élément focus)
                if (ev.type == KeyEventType.KeyUp) {
                    val center = ev.key == Key.DirectionCenter || ev.key == Key.Enter || ev.key == Key.NumPadEnter
                    if (center && ch.kind == "live" && menuStage == 0 && !listOpen) { openMenu(1); showUi = true; return@onKeyEvent true }
                    return@onKeyEvent false
                }
                if (ev.type != KeyEventType.KeyDown) return@onKeyEvent false
                val vod = ch.kind != "live"
                if (vod) {
                    // menu ouvert : les flèches et OK doivent déplacer le focus dans le menu, pas faire avancer la vidéo
                    if (menuStage != 0 || listOpen) return@onKeyEvent false
                    return@onKeyEvent when (ev.key) {
                        Key.DirectionRight -> { player.seekTo(player.currentPosition + 15_000); showUi = true; true }
                        Key.DirectionLeft -> { player.seekTo((player.currentPosition - 15_000).coerceAtLeast(0)); showUi = true; true }
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.MediaPlayPause -> {
                            player.playWhenReady = !player.playWhenReady
                            showUi = true
                            true
                        }
                        Key.DirectionDown -> { openMenu(2); showUi = true; true }
                        Key.DirectionUp -> { openMenu(2); showUi = true; true }
                        else -> false
                    }
                }
                // menu ou liste ouverts : les flèches déplacent le focus dans le menu, sans changer de chaîne
                if (menuStage != 0 || listOpen) return@onKeyEvent false
                when (ev.key) {
                    Key.ChannelDown, Key.PageDown -> { vm.step(1); showUi = true; true }
                    Key.ChannelUp, Key.PageUp -> { vm.step(-1); showUi = true; true }
                    // flèche bas, flèche haut ou bouton central : ouvre le menu (derniers canaux + rangée d'icônes)
                    Key.DirectionDown -> { openMenu(2); showUi = true; true }
                    Key.DirectionUp -> { openMenu(1); showUi = true; true }
                    Key.MediaPlayPause -> { player.playWhenReady = !player.playWhenReady; showUi = true; true }
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
                // pas de contrôleur natif (Vitesse / Audio / engrenage) : nos propres boutons et options suffisent
                it.useController = false
                // films et séries : nos propres boutons (reculer, lecture, avancer) suffisent ; pas de précédent/suivant
                it.setShowPreviousButton(false)
                it.setShowNextButton(false)
                it.setShowRewindButton(false)
                it.setShowFastForwardButton(false)
                it.resizeMode = if (displayMode == 1) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
            },
        )

        // couche tactile par-dessus la vidéo : gestes + toucher pour afficher/masquer
        // (sur un film, seulement le haut de l'écran, pour ne pas couvrir les boutons du lecteur)
        Box(
            Modifier
                .fillMaxSize()
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
                                if (!dragging) { if (menuStage == 2) menuStage = 1 else showUi = !showUi }
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

        // bas de l'écran : derniers canaux + une rangée d'icônes ; flèche pour l'ouvrir au téléphone
        // (films et séries : seulement la rangée d'options, qui s'ouvre au premier clic)
        if (!vm.inPip) {
            val rec = vm.recording
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        awaitPointerEventScope { while (true) { awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial); poke() } }
                    }
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                fun close() { menuStage = 0 }
                val recents = vm.recentChannels()
                if (menuStage == 1 && ch.kind == "live" && recents.isNotEmpty()) {
                    // historique : une ligne de chaînes récentes, sans fond ; glisser vers le bas ouvre les options
                    LazyRow(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = tvPad + 8.dp, vertical = 8.dp)
                            .pointerInput(Unit) {
                                var acc = 0f
                                detectVerticalDragGestures(
                                    onDragStart = { acc = 0f },
                                    onVerticalDrag = { _, dy -> acc += dy; if (acc > 80f) { menuStage = 2; acc = 0f } },
                                )
                            }
                            .onKeyEvent { ev ->
                                if (ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionDown) { menuStage = 2; true } else false
                            },
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        itemsIndexed(recents, key = { _, c -> c.id }) { idx, c ->
                            RecentTile(
                                c,
                                selected = c.id == ch.id,
                                modifier = if (idx == 0) Modifier.focusRequester(stripFirst) else Modifier,
                            ) { close(); vm.play(vm.playlist, c) }
                        }
                    }
                }
                // flèche : seulement sous l'historique ; elle ouvre les options
                if (menuStage == 1 && ch.kind == "live") {
                    Box(
                        Modifier
                            .width(72.dp)
                            .height(24.dp)
                            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                            .background(Color(0xAA000000))
                            .clickable { menuStage = 2 },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Default.KeyboardArrowDown, "Options", tint = Color.White) }
                }
                // films et séries : temps écoulé et barre de progression au-dessus des options
                if (ch.kind != "live" && menuStage == 2) {
                    val frac = if (vodDur > 0) (vodPos.toFloat() / vodDur).coerceIn(0f, 1f) else 0f
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(Color(0xAA000000))
                            .padding(horizontal = tvPad + 8.dp, vertical = 4.dp),
                    ) {
                        Box(Modifier.fillMaxWidth().height(4.dp).background(Color(0x55FFFFFF))) {
                            Box(Modifier.fillMaxWidth(frac).height(4.dp).background(Color.White))
                        }
                        Text("${mmss(vodPos)} / ${mmss(vodDur)}", color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                }
                if (menuStage == 2) {
                    val menuItems = listOf<Triple<String, ImageVector, () -> Unit>>(
                        Triple("Rechercher", Icons.Default.Search) { close(); listOpen = true },
                        Triple("Liste des chaînes", Icons.Default.List) { close(); listOpen = true },
                        Triple(
                            if (rec == null) "Enregistrer" else "Arrêter",
                            if (rec == null) IconRecord else IconStop,
                        ) {
                            if (rec == null) vm.startRecording(ctx, ch) else vm.stopRecording()
                            close()
                        },
                        Triple("Multi-vue", IconMultiView) { close(); hud = "Multi-vue : bientôt disponible" },
                        Triple("Image dans l'image", IconPip) { (activity as? MainActivity)?.enterPip() },
                        Triple("Résolution", IconScreen) { picker = "video" },
                        Triple("Sous-titres", IconCaption) { picker = "text" },
                        Triple("Audio", IconAudio) { picker = "audio" },
                        Triple("Affichage : " + displayModes[displayMode], IconAspect) {
                            displayMode = (displayMode + 1) % displayModes.size
                        },
                        *(if (ch.kind != "live") arrayOf(
                            Triple("Vitesse ${speeds[speedIdx].let { if (it % 1f == 0f) it.toInt().toString() else it.toString() }}×", IconTimer) {
                                speedIdx = (speedIdx + 1) % speeds.size
                                player.setPlaybackSpeed(speeds[speedIdx])
                            },
                        ) else emptyArray<Triple<String, ImageVector, () -> Unit>>()),
                        Triple(if (sleepMin == 0) "Minuterie" else "Minuterie $sleepMin min", IconTimer) {
                            sleepMin = sleepSteps[(sleepSteps.indexOf(sleepMin) + 1) % sleepSteps.size]
                        },
                        Triple(if (vm.isFav(ch)) "Retirer favori" else "Favoris", if (vm.isFav(ch)) Icons.Default.Favorite else Icons.Default.FavoriteBorder) {
                            vm.toggleFav(ch)
                        },
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            // glisser vers le haut revient à l'historique
                            .pointerInput(Unit) {
                                var acc = 0f
                                detectVerticalDragGestures(
                                    onDragStart = { acc = 0f },
                                    onVerticalDrag = { _, dy -> acc += dy; if (acc < -80f) { menuStage = 1; acc = 0f } },
                                )
                            }
                            // même teinte noire que la barre du haut ; l'historique, lui, reste sans fond
                            .background(Color(0xAA000000))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        // toutes les options tiennent à l'écran : pas de défilement
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        menuItems.filter { ch.kind == "live" || it.first !in setOf("Rechercher", "Liste des chaînes", "Multi-vue") }
                            .forEachIndexed { i, (label, icon, action) ->
                            PlayerMenuItem(label, icon, modifier = if (i == 0) Modifier.focusRequester(menuFirst) else Modifier, onClick = action)
                        }
                    }
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
        picker?.let { kind ->
            TrackPickerDialog(
                player, kind,
                query = if (ch.seriesName.isNotBlank()) cleanTitle(ch.seriesName) else cleanTitle(ch.name),
                year = if (ch.seriesName.isNotBlank()) "" else yearOf(ch.name), key = vm.osKey(),
                season = ch.season, episode = ch.episode,
                lang = vm.subLang(), onLang = { vm.saveSubLang(it) },
                live = ch.kind == "live",
                onSubUrl = { s ->
                    subStart = if (ch.kind != "live") player.currentPosition else 0L
                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build()
                    subUrl = s
                },
            ) { picker = null }
        }

        if (showUi && !vm.inPip) {
            Row(
                Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CenterControl(IconRewind, "Reculer de 15 s") {
                    poke()
                    if (player.isCurrentMediaItemSeekable) player.seekTo((player.currentPosition - 15_000).coerceAtLeast(0L))
                    else hud = "Retour en arrière impossible sur ce flux (pause possible)"
                }
                CenterControl(if (playing) IconPause else Icons.Default.PlayArrow, if (playing) "Pause" else "Lecture") {
                    player.playWhenReady = !player.playWhenReady
                }
                CenterControl(
                    IconForward,
                    if (dvrOn || liveOffsetMs > 8_000) "Retour au direct" else "Avancer de 15 s",
                    tint = if (dvrOn || liveOffsetMs > 8_000) Color(0xFFFF6B6B) else Color.White,
                ) {
                    poke()
                    if (dvrOn) goLive()
                    else if (liveOffsetMs > 8_000 && player.isCurrentMediaItemSeekable) player.seekToDefaultPosition()
                    else if (player.isCurrentMediaItemSeekable) player.seekTo(player.currentPosition + 15_000)
                    else hud = "Déjà au direct"
                }
                // enregistrer : seulement en direct (sur un film, c'est dans la rangée d'options)
                if (ch.kind == "live") run {
                    val rec = vm.recording
                    // la minuterie flotte sous le bouton : elle ne déplace plus pause ni enregistrer
                    Box(contentAlignment = Alignment.Center) {
                        CenterControl(
                            if (rec == null) IconRecord else IconStop,
                            if (rec == null) "Enregistrer" else "Arrêter",
                            tint = if (rec == null) Color.White else Color(0xFFFF6B6B),
                        ) { if (rec == null) vm.startRecording(ctx, ch) else vm.stopRecording() }
                        if (rec != null) {
                            val secs = ((now - rec.startMs) / 1000).coerceAtLeast(0)
                            Text("%d:%02d".format(secs / 60, secs % 60), color = Color(0xFFFF6B6B), fontSize = 13.sp, modifier = Modifier.align(Alignment.BottomCenter).offset(y = 34.dp))
                        }
                    }
                }
            }
        }
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
                IconButton(onClick = { vm.stop(!playing) }, modifier = Modifier.focusRing()) { Icon(Icons.Default.ArrowBack, "Retour", tint = Color.White) }
                Column(Modifier.weight(1f)) {
                    val pos = vm.playlist.indexOf(ch)
                    Text(
                        vm.displayName(ch) + if (pos >= 0 && ch.kind == "live") "   ${pos + 1} / ${vm.playlist.size}" else "",
                        color = Color.White, maxLines = 1,
                    )
                    if (dvrOn) Text(
                        "En différé : −%d:%02d".format(dvrBehind / 60_000, (dvrBehind / 1000) % 60),
                        color = Color(0xFFFFD27F), fontSize = 13.sp, maxLines = 1,
                    )
                    val e = vm.epg[ch.id]
                    if (e?.now != null) {
                        Text("${e.now.title}  ${hm(e.now.start)}–${hm(e.now.stop)}", color = Color(0xFFCCDDFF), fontSize = 13.sp, maxLines = 1)
                        if (e.next != null) Text("Ensuite : ${e.next.title}  ${hm(e.next.start)}", color = Color(0xFFAAAAAA), fontSize = 12.sp, maxLines = 1)
                    }
                }
                if (ch.kind == "live") {
                    OutlinedButton(onClick = { poke(); dvrGo(5 * 60_000L) }, modifier = Modifier.focusRing()) { Text("−5 min", fontSize = 13.sp) }
                    OutlinedButton(onClick = { poke(); dvrGo(60 * 60_000L) }, modifier = Modifier.focusRing()) { Text("−1 h", fontSize = 13.sp) }
                }
                if (ch.kind == "live") LiveBadge(live = !dvrOn, onClick = { poke(); goLive() })
                val fav = vm.isFav(ch)
                IconButton(onClick = { vm.toggleFav(ch) }, modifier = Modifier.focusRing()) {
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

/**
 * Bouton LIVE : rouge quand on regarde le direct, gris quand on est en différé.
 * Un appui revient au direct.
 */
@Composable
fun LiveBadge(live: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val color = if (live) Color(0xFFE53935) else Color(0xFF7A7F8A)
    Box(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .border(2.dp, color, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text("LIVE", color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

/** temps lisible : m:ss, ou h:mm:ss au-delà d'une heure */
private fun mmss(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

private val displayModes = listOf("Normal", "Zoom", "4:3", "16:9")
private val sleepSteps = listOf(0, 15, 30, 60, 90)

@Composable
private fun PlayerMenuItem(label: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    // l'élément qui a le focus grossit et change de couleur : on voit où on est à chaque pression
    val grow by animateFloatAsState(if (focused) 1.12f else 1f, label = "grow")
    val tint by animateColorAsState(if (focused) Accent else Color.Transparent, label = "tint")
    Column(
        modifier
            .widthIn(max = 96.dp)
            .scale(grow)
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(8.dp))
            .background(tint)
            .then(if (focused) Modifier.border(3.dp, Color.White, RoundedCornerShape(8.dp)) else Modifier)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = Color.White, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, color = Color.White, fontSize = 11.sp, maxLines = 2, textAlign = TextAlign.Center)
    }
}

private fun playerIcon(name: String, build: ImageVector.Builder.() -> Unit): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply(build).build()

private fun ImageVector.Builder.outline(d: String) =
    addPath(addPathNodes(d), stroke = SolidColor(Color.White), strokeLineWidth = 2f)

private fun ImageVector.Builder.solid(d: String) =
    addPath(addPathNodes(d), fill = SolidColor(Color.White))

/** Icônes du menu qui n'existent pas dans le jeu de base de Material. */
private val IconPause = playerIcon("pause") { solid("M6,5h4v14h-4z"); solid("M14,5h4v14h-4z") }
/** Anneau blanc bien visible autour du bouton qui a le focus (manette). */
@Composable
private fun Modifier.focusRing(): Modifier {
    var f by remember { mutableStateOf(false) }
    return this.onFocusChanged { f = it.isFocused }
        .then(if (f) Modifier.border(3.dp, Color.White, CircleShape).background(Color(0x66FFFFFF), CircleShape) else Modifier)
}

@Composable
private fun CenterControl(icon: ImageVector, label: String, tint: Color = Color.White, onClick: () -> Unit) {
    Box(
        Modifier.size(76.dp).clip(CircleShape).background(Color(0x99000000)).focusRing().clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = tint, modifier = Modifier.size(42.dp)) }
}

private val IconRewind = playerIcon("rewind") { solid("M11,6l-8,6l8,6z"); solid("M21,6l-8,6l8,6z") }
private val IconForward = playerIcon("forward") { solid("M13,6l8,6l-8,6z"); solid("M3,6l8,6l-8,6z") }
private val IconRecord = playerIcon("record") { solid("M12,4a8,8 0 1 1 -0.01,0z") }
private val IconStop = playerIcon("stop") { solid("M6,6h12v12h-12z") }
private val IconPip = playerIcon("pip") { outline("M3,4h18v16h-18z"); solid("M12,12h7v5h-7z") }
private val IconMultiView = playerIcon("multi") {
    solid("M3,3h8v8h-8z"); solid("M13,3h8v8h-8z"); solid("M3,13h8v8h-8z"); solid("M13,13h8v8h-8z")
}
private val IconScreen = playerIcon("screen") { outline("M3,5h18v12h-18z"); outline("M8,21h8") }
private val IconCaption = playerIcon("caption") { outline("M3,5h18v14h-18z"); outline("M6,11h5"); outline("M13,11h5"); outline("M6,15h12") }
private val IconAudio = playerIcon("audio") { solid("M3,9h4l5,-4v14l-5,-4h-4z"); outline("M16,8q3,4 0,8") }
private val IconAspect = playerIcon("aspect") { outline("M2,6h20v12h-20z"); outline("M6,9h12v6h-12z") }
private val IconTimer = playerIcon("timer") { outline("M12,4a8,8 0 1 0 0.01,0"); outline("M12,8v4l3,2") }

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

private fun codecName(mime: String?): String? = when {
    mime == null -> null
    mime.contains("avc") -> "H.264"
    mime.contains("hevc") -> "H.265 (HEVC)"
    mime.contains("av01") -> "AV1"
    mime.contains("mpeg2") || mime.contains("mpeg-2") -> "MPEG-2"
    mime.contains("mp4a") || mime.contains("aac") -> "AAC"
    mime.contains("ac-3") && !mime.contains("eac") -> "AC-3"
    mime.contains("eac3") || mime.contains("e-ac-3") -> "E-AC-3"
    mime.contains("mpeg") -> "MP3/MPEG"
    else -> mime.substringAfter('/').uppercase()
}

/** Ce que le flux envoie réellement (résolution, images/s, codec, débit, canaux audio). */
private fun streamInfo(player: ExoPlayer, kind: String): String? {
    val f = player.currentTracks.groups.filter { it.isSelected && it.type == (if (kind == "video") C.TRACK_TYPE_VIDEO else C.TRACK_TYPE_AUDIO) }
        .firstNotNullOfOrNull { g -> (0 until g.length).firstOrNull { g.isTrackSelected(it) }?.let { g.getTrackFormat(it) } } ?: return null
    val parts = mutableListOf<String>()
    if (kind == "video") {
        if (f.height > 0) parts += "${f.width}×${f.height}" + (if (f.height >= 100) " (${f.height}p)" else "")
        if (f.frameRate > 0) parts += "${f.frameRate.let { if (it % 1f == 0f) it.toInt().toString() else "%.2f".format(it) }} im/s"
    } else {
        if (f.language != null && f.language != "und") parts += f.language!!
        if (f.channelCount > 0) parts += when (f.channelCount) { 1 -> "mono"; 2 -> "stéréo"; 6 -> "5.1"; else -> "${f.channelCount} canaux" }
        if (f.sampleRate > 0) parts += "${f.sampleRate / 1000.0} kHz"
    }
    codecName(f.sampleMimeType)?.let { parts += it }
    if (f.bitrate > 0) parts += "${f.bitrate / 1000} kb/s"
    return parts.joinToString(" · ").ifEmpty { null }
}

/** Choix de la résolution, des sous-titres ou de l'audio, selon les pistes du flux. */
@Composable
private fun TrackPickerDialog(
    player: ExoPlayer, kind: String,
    query: String = "", year: String = "", key: String = "", live: Boolean = false,
    season: Int = 0, episode: Int = 0,
    lang: String = "fr,en", onLang: (String) -> Unit = {},
    onSubUrl: (String) -> Unit, onDone: () -> Unit,
) {
    var url by remember { mutableStateOf("") }
    var langSel by remember { mutableStateOf(lang) }
    // recherche en ligne (OpenSubtitles, langue mémorisée)
    var online by remember { mutableStateOf<List<OpenSubs.Sub>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
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
            Column(Modifier.verticalScroll(rememberScrollState())) {
            if (kind != "text") {
                val info = remember(kind) { streamInfo(player, kind) }
                Text(if (info != null) "Le flux envoie : $info" else "Le flux n'indique pas ses détails (lecture pas encore démarrée ?)", color = Color(0xFFB0BEC5), modifier = Modifier.padding(bottom = 8.dp))
            }
            if (kind == "text" && live) Text(
                "Sous-titres externes indisponibles sur la TV en direct : ils ne se synchronisent pas avec le direct.",
                color = Color(0xFFB0BEC5), fontSize = 12.sp,
            )
            else if (kind == "text") {
                androidx.compose.material3.OutlinedTextField(
                    value = url, onValueChange = { url = it }, singleLine = true,
                    label = { Text("Adresse d'un fichier .srt ou .vtt") },
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    enabled = url.trim().startsWith("http"),
                    onClick = { onSubUrl(url.trim()); onDone() },
                ) { Text("Charger ces sous-titres") }

                Text("Sous-titres en ligne (OpenSubtitles)", color = Color.White, modifier = Modifier.padding(top = 10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("fr" to "Français", "en" to "Anglais", "fr,en" to "Les deux").forEach { (code, label) ->
                        TextButton(onClick = { langSel = code; onLang(code) }) {
                            Text(if (langSel == code) "✔ $label" else label)
                        }
                    }
                }
                Text(
                    if (key.isBlank()) "Clé OpenSubtitles absente : ajoute-la dans Préférences › Réseau."
                    else "Clé OpenSubtitles enregistrée. Lance la recherche ci-dessous.",
                    color = Color(0xFFB0BEC5), fontSize = 12.sp,
                )
                run {
                    TextButton(enabled = !busy && key.isNotBlank() && query.isNotBlank(), onClick = {
                        busy = true; err = ""; online = null
                        scope.launch {
                            try { online = OpenSubs.search(key, query, year, lang = langSel, season = season, episode = episode) }
                            catch (e: Exception) { err = e.message ?: "Recherche impossible" }
                            finally { busy = false }
                        }
                    }) { Text(if (busy) "Recherche…" else "Chercher « $query »") }
                    if (err.isNotEmpty()) Text(err, color = Color(0xFFFF8A80), fontSize = 12.sp)
                    online?.let { list ->
                        if (list.isEmpty()) Text("Aucun sous-titre français trouvé pour ce titre.", color = Color(0xFFB0BEC5), fontSize = 12.sp)
                        else LazyColumn(Modifier.heightIn(max = 240.dp)) {
                            items(list) { s ->
                                Text(
                                    "${s.language} · ${s.release} · ${s.downloads} téléchargements",
                                    color = Color.White, fontSize = 13.sp, maxLines = 2,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = !busy) {
                                            busy = true; err = ""
                                            scope.launch {
                                                try { onSubUrl(OpenSubs.link(key, s.fileId)); onDone() }
                                                catch (e: Exception) { err = e.message ?: "Téléchargement impossible"; busy = false }
                                            }
                                        }
                                        .padding(vertical = 8.dp),
                                )
                            }
                        }
                    }
                }
            }
            if (options.size <= 1) Text(
                if (kind == "text") "Cette chaîne n'envoie pas de sous-titres."
                else "Un seul choix : la chaîne n'offre pas d'autre " + (if (kind == "video") "qualité." else "piste audio."),
            )
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
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text("Fermer") } },
    )
}
