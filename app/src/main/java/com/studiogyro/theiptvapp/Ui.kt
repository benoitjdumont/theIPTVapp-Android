package com.studiogyro.theiptvapp

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

val Bg = Color(0xFF0B1220)
val Panel = Color(0xFF111A2E)
var Accent by mutableStateOf(Color(0xFF2F7BFF))

val ACCENTS = listOf(
    "Bleu" to Color(0xFF2F7BFF),
    "Indigo" to Color(0xFF6C7BE0),
    "Turquoise" to Color(0xFF00ACC1),
    "Vert" to Color(0xFF2EAD6B),
    "Orange" to Color(0xFFF57C00),
    "Rouge" to Color(0xFFE53935),
    "Rose" to Color(0xFFE91E8C),
)

val BgBrush = androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF16205E), Color(0xFF0B1230), Color(0xFF0E1A4A)))

@Composable
fun AppRoot(vm: MainVm) {
    val scheme = darkColorScheme(
        primary = Accent,
        background = Bg,
        surface = Panel,
        onSurface = Color.White,
        onBackground = Color.White,
    )
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val tv = remember {
        (ctx.getSystemService(android.content.Context.UI_MODE_SERVICE) as android.app.UiModeManager).currentModeType ==
            android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
    }
    // sur une télé, les bords de l'écran sont souvent rognés : marge de sécurité
    val safe = if (tv) Modifier.padding(horizontal = 48.dp, vertical = 27.dp) else Modifier
    MaterialTheme(colorScheme = scheme) {
        PinDialog(vm)
        UpdateDialog(vm)
        Surface(Modifier.fillMaxSize().background(BgBrush), color = Color.Transparent) {
            var screen by rememberSaveable { mutableStateOf(if (vm.sources.isEmpty()) "settings" else "home") }
            if (vm.current != null) {
                PlayerScreen(vm)
            } else if (vm.seriesOpen != null) {
                Box(Modifier.fillMaxSize().safeDrawingPadding().then(safe)) { SeriesScreen(vm) }
            } else {
                // keep clear of the status bar (battery, clock) and the navigation bar
                Box(Modifier.fillMaxSize().safeDrawingPadding().then(safe)) {
                    if (screen == "settings") {
                        if (vm.sources.isNotEmpty()) HomeScreen(vm) { }
                        SettingsScreen(vm) { screen = "home" }
                    } else {
                        HomeScreen(vm) { screen = "settings" }
                    }
                }
            }
        }
    }
}

/** A row that is clearly highlighted when focused with a remote control. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FocusRow(selected: Boolean, onClick: () -> Unit, onLongClick: (() -> Unit)? = null, content: @Composable RowScope.() -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (focused) Accent.copy(alpha = 0.35f) else if (selected) Accent.copy(alpha = 0.18f) else Color.Transparent)
            .then(if (focused) Modifier.border(2.dp, Accent, shape) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
fun HomeScreen(vm: MainVm, openSettings: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= 480.dp) HomeWide(vm, openSettings) else HomeNarrow(vm, openSettings)
    }
}

val RailBg = Color(0xB3080C24)
val GroupBg = Color(0x66101850)
val ListBg = Color(0x33000000)
val CellBg = Color(0x553A4A9A)

@Composable
private fun RailItem(selected: Boolean, expanded: Boolean, label: String, onClick: () -> Unit, icon: @Composable () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp)
            .height(44.dp)
            .clip(shape)
            .background(if (focused) Accent.copy(alpha = 0.55f) else if (selected) Accent.copy(alpha = 0.9f) else Color.Transparent)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(44.dp), contentAlignment = Alignment.Center) { icon() }
        if (expanded) Text(label, color = Color.White, fontSize = 14.sp, maxLines = 1)
    }
}

/** Écran principal façon TiviMate : rail d'icônes, groupes, puis la liste (ou les affiches). */
@Composable
private fun HomeWide(vm: MainVm, openSettings: () -> Unit) {
    val sec = vm.section()
    var searchOpen by rememberSaveable { mutableStateOf(vm.query.isNotEmpty()) }
    Row(Modifier.fillMaxSize()) {
        var railFocus by remember { mutableStateOf(false) }
        val railW by androidx.compose.animation.core.animateDpAsState(if (railFocus) 170.dp else 56.dp, label = "rail")
        Column(
            Modifier.width(railW).fillMaxHeight().background(RailBg).padding(vertical = 6.dp).onFocusChanged { railFocus = it.hasFocus },
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(Modifier.width(56.dp), contentAlignment = Alignment.Center) { Image(painterResource(R.mipmap.ic_launcher), null, Modifier.size(30.dp)) }
            RailItem(searchOpen, railFocus, "Rechercher", { searchOpen = !searchOpen; if (!searchOpen) vm.query = "" }) { Icon(Icons.Default.Search, "Rechercher", tint = Color.White) }
            RailItem(vm.tab == 0, railFocus, "TV", { vm.selectTab(0) }) { Text("📺", fontSize = 20.sp) }
            RailItem(vm.tab == 1, railFocus, "Films", { vm.selectTab(1) }) { Text("🎬", fontSize = 20.sp) }
            RailItem(vm.tab == 2, railFocus, "Séries", { vm.selectTab(2) }) { Text("🎞", fontSize = 20.sp) }
            Spacer(Modifier.weight(1f))
            RailItem(false, railFocus, "Actualiser", { vm.reload() }) { Icon(Icons.Default.Refresh, "Actualiser", tint = Color.White) }
            RailItem(false, railFocus, "Paramètres", openSettings) { Icon(Icons.Default.Settings, "Paramètres", tint = Color.White) }
        }
        Box(Modifier.weight(1f).fillMaxHeight()) {
            when {
                vm.sources.isEmpty() -> Message("Ajoute une source dans les réglages (⚙).")
                sec.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.size(12.dp))
                        Text("Chargement de la liste…", color = Color.White)
                    }
                }
                sec.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (vm.tab == 0) "Erreur : ${sec.error}" else "${sec.error}", color = Color(0xFFFF8A8A))
                        Spacer(Modifier.size(12.dp))
                        if (vm.tab == 0 || vm.sources.firstOrNull { it.id == vm.activeId }?.type == SourceType.XTREAM) {
                            Button(onClick = { vm.reload() }) { Text("Réessayer") }
                        }
                    }
                }
                else -> {
                    val list = remember(sec.items, sec.top, sec.sub, vm.query, vm.favs) { vm.visible() }
                    val subs = sec.subs()
                    Row(Modifier.fillMaxSize()) {
                        LazyColumn(Modifier.width(170.dp).fillMaxHeight().background(GroupBg).padding(horizontal = 4.dp, vertical = 4.dp)) {
                            items(topEntries(sec, vm.tab == 0).filter { it.first == null || it.first == GROUP_FAV || it.first !in vm.hiddenCats }, key = { it.first ?: "\u0001all" }) { (g, label) ->
                                val lock = g != null && g != GROUP_FAV && vm.catLocked(label)
                                FocusRow(
                                    selected = sec.top == g,
                                    onClick = { if (g == null || g == GROUP_FAV) sec.pickTop(g) else vm.guardCat(label) { sec.pickTop(g) } },
                                    onLongClick = if (g != null && g != GROUP_FAV) ({ vm.toggleCatLock(label) }) else null,
                                ) {
                                    Text((if (lock) "🔒 " else "") + label, color = if (sec.top == g) Accent else Color.White, maxLines = 1)
                                }
                            }
                        }
                        if (subs.isNotEmpty()) {
                            LazyColumn(Modifier.width(180.dp).fillMaxHeight().background(GroupBg).padding(horizontal = 4.dp, vertical = 4.dp)) {
                                item(key = "\u0001subAll") {
                                    FocusRow(selected = sec.sub == null, onClick = { sec.pickSub(null) }) {
                                        Text("Tout", color = if (sec.sub == null) Accent else Color.White, maxLines = 1)
                                    }
                                }
                                items(subs, key = { it.first }) { (full, label) ->
                                    FocusRow(
                                        selected = sec.sub == full,
                                        onClick = { vm.guardCat(label, full) { sec.pickSub(full) } },
                                        onLongClick = { vm.toggleCatLock(full) },
                                    ) {
                                        Text((if (vm.catLocked(label, full)) "🔒 " else "") + label, color = if (sec.sub == full) Accent else Color.White, maxLines = 1)
                                    }
                                }
                            }
                        }
                        Column(Modifier.weight(1f).fillMaxHeight().background(ListBg)) {
                            if (searchOpen) {
                                OutlinedTextField(
                                    value = vm.query,
                                    onValueChange = { vm.query = it },
                                    singleLine = true,
                                    placeholder = { Text("Rechercher") },
                                    leadingIcon = { Icon(Icons.Default.Search, null) },
                                    modifier = Modifier.fillMaxWidth().padding(6.dp),
                                )
                            }
                            val pv = vm.preview
                            if (vm.tab == 0 && vm.previewMode && pv != null) PreviewHeader(vm, pv)
                            if (vm.tab == 0) ChannelList(vm, list, Modifier.weight(1f)) else PosterWall(vm, list, Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeNarrow(vm: MainVm, openSettings: () -> Unit) {
    val sec = vm.section()
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        // une seule rangée : l'écran est toujours à l'horizontale
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Image(painterResource(R.mipmap.ic_launcher), null, Modifier.size(36.dp))
            listOf("Direct", "Films", "Séries").forEachIndexed { i, label ->
                FilterChip(selected = vm.tab == i, onClick = { vm.selectTab(i) }, label = { Text(label) })
            }
            OutlinedTextField(
                value = vm.query,
                onValueChange = { vm.query = it },
                singleLine = true,
                placeholder = { Text("Rechercher") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { vm.reload() }) { Icon(Icons.Default.Refresh, "Actualiser", tint = Color.White) }
            IconButton(onClick = openSettings) { Icon(Icons.Default.Settings, "Sources", tint = Color.White) }
        }
        Spacer(Modifier.size(8.dp))

        when {
            vm.sources.isEmpty() -> Message("Ajoute une source dans les réglages (⚙).")
            sec.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.size(12.dp))
                    Text("Chargement de la liste…", color = Color.White)
                }
            }
            sec.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (vm.tab == 0) "Erreur : ${sec.error}" else "${sec.error}", color = Color(0xFFFF8A8A))
                    Spacer(Modifier.size(12.dp))
                    if (vm.tab == 0 || vm.sources.firstOrNull { it.id == vm.activeId }?.type == SourceType.XTREAM) {
                        Button(onClick = { vm.reload() }) { Text("Réessayer") }
                    }
                }
            }
            else -> BoxWithConstraints(Modifier.fillMaxSize()) {
                val list = remember(sec.items, sec.top, sec.sub, vm.query, vm.favs) { vm.visible() }
                val subs = sec.subs()
                val content: @Composable (Modifier) -> Unit = { m ->
                    if (vm.tab == 0) ChannelList(vm, list, m) else PosterGrid(vm, list, m)
                }
                if (maxWidth >= 480.dp) {
                    Row(Modifier.fillMaxSize()) {
                        LazyColumn(Modifier.width(170.dp).fillMaxSize()) {
                            items(topEntries(sec, vm.tab == 0), key = { it.first ?: "\u0001all" }) { (g, label) ->
                                val lock = g != null && g != GROUP_FAV && vm.catLocked(label)
                                FocusRow(
                                    selected = sec.top == g,
                                    onClick = { if (g == null || g == GROUP_FAV) sec.pickTop(g) else vm.guardCat(label) { sec.pickTop(g) } },
                                    onLongClick = if (g != null && g != GROUP_FAV) ({ vm.toggleCatLock(label) }) else null,
                                ) {
                                    Text((if (lock) "🔒 " else "") + label, color = Color.White, maxLines = 1)
                                }
                            }
                        }
                        if (subs.isNotEmpty()) {
                            Spacer(Modifier.width(6.dp))
                            LazyColumn(Modifier.width(190.dp).fillMaxSize()) {
                                item(key = "\u0001subAll") {
                                    FocusRow(selected = sec.sub == null, onClick = { sec.pickSub(null) }) {
                                        Text("Tout", color = Color.White, maxLines = 1)
                                    }
                                }
                                items(subs, key = { it.first }) { (full, label) ->
                                    FocusRow(
                                        selected = sec.sub == full,
                                        onClick = { vm.guardCat(label, full) { sec.pickSub(full) } },
                                        onLongClick = { vm.toggleCatLock(full) },
                                    ) {
                                        Text((if (vm.catLocked(label, full)) "🔒 " else "") + label, color = Color.White, maxLines = 1)
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        content(Modifier.weight(1f))
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(topEntries(sec, vm.tab == 0), key = { it.first ?: "\u0001all" }) { (g, label) ->
                                FilterChip(selected = sec.top == g, onClick = { if (g == null || g == GROUP_FAV) sec.pickTop(g) else vm.guardCat(label) { sec.pickTop(g) } }, label = { Text((if (g != null && g != GROUP_FAV && vm.catLocked(label)) "🔒 " else "") + label, maxLines = 1) })
                            }
                        }
                        if (subs.isNotEmpty()) {
                            Spacer(Modifier.size(4.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                item(key = "\u0001subAll") {
                                    FilterChip(selected = sec.sub == null, onClick = { sec.pickSub(null) }, label = { Text("Tout") })
                                }
                                items(subs, key = { it.first }) { (full, label) ->
                                    FilterChip(selected = sec.sub == full, onClick = { vm.guardCat(label, full) { sec.pickSub(full) } }, label = { Text((if (vm.catLocked(label, full)) "🔒 " else "") + label, maxLines = 1) })
                                }
                            }
                        }
                        Spacer(Modifier.size(6.dp))
                        content(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

private fun topEntries(sec: Section, live: Boolean): List<Pair<String?, String>> {
    val l = ArrayList<Pair<String?, String>>()
    l.add(null to "Toutes (${sec.items.size})")
    if (live) l.add(GROUP_FAV to "★ Favoris")
    sec.tops.forEach { l.add(it to it) }
    return l
}

/** Films et séries : fiche du titre survolé en haut, murs d'affiches en dessous. */
@Composable
private fun PosterWall(vm: MainVm, list: List<Channel>, modifier: Modifier) {
    if (list.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) { Text("Rien à afficher.", color = Color.Gray) }
        return
    }
    var sel by remember(list) { mutableStateOf<Channel?>(null) }
    val cur = sel ?: list.first()
    LaunchedEffect(cur.id) {
        kotlinx.coroutines.delay(300)
        vm.loadDetail(cur)
    }
    val d = vm.details[cur.id]
    Column(modifier) {
        Column(Modifier.fillMaxWidth().height(112.dp).background(GroupBg).padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(cur.name, color = Color.White, fontSize = 19.sp, maxLines = 1)
            val meta = d?.meta?.ifBlank { null } ?: cur.meta
            if (meta.isNotBlank()) Text(meta, color = Color(0xFF9DB4D8), fontSize = 12.sp, maxLines = 1)
            if (d != null && d.cast.isNotBlank()) Text("Acteurs : " + d.cast, color = Color(0xFF8A97A6), fontSize = 11.sp, maxLines = 1)
            val plot = (d?.plot?.ifBlank { null } ?: cur.plot).ifBlank { if (d == null) "…" else "" }
            Text(plot, color = Color(0xFFB8C4DA), fontSize = 12.sp, maxLines = 3, modifier = Modifier.padding(top = 3.dp))
        }
        PosterGrid(vm, list, Modifier.weight(1f).padding(6.dp)) { sel = it }
    }
}

/** Poster wall for movies and series. */
@Composable
private fun PosterGrid(vm: MainVm, list: List<Channel>, modifier: Modifier, onFocus: (Channel) -> Unit = {}) {
    if (list.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) { Text("Rien à afficher.", color = Color.Gray) }
        return
    }
    LazyVerticalGrid(columns = GridCells.Adaptive(120.dp), modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(list, key = { it.id }) { c ->
            var focused by remember { mutableStateOf(false) }
            val shape = RoundedCornerShape(10.dp)
            Column(
                Modifier
                    .clip(shape)
                    .background(if (focused) Accent.copy(alpha = 0.35f) else Panel)
                    .then(if (focused) Modifier.border(2.dp, Accent, shape) else Modifier)
                    .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus(c) }
                    .clickable { vm.guard(c) { if (c.kind == "series") vm.openSeries(c) else vm.play(listOf(c), c) } }
                    .padding(6.dp),
            ) {
                Poster(c.logo, Modifier.fillMaxWidth().aspectRatio(2f / 3f))
                Text((if (vm.isLocked(c)) "🔒 " else "") + c.name, color = Color.White, fontSize = 12.sp, maxLines = 2, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun Poster(url: String, modifier: Modifier) {
    if (url.isNotBlank()) {
        AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier.clip(RoundedCornerShape(8.dp)))
    } else {
        Box(modifier.clip(RoundedCornerShape(8.dp)).background(Bg))
    }
}

/** Series page, laid out for a landscape screen: poster and summary on the left, seasons and episodes on the right. */
@Composable
fun SeriesScreen(vm: MainVm) {
    val c = vm.seriesOpen ?: return
    val data = vm.seriesData
    BackHandler(true) { vm.closeSeries() }
    var season by rememberSaveable(c.id) { mutableStateOf(-1) }
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.closeSeries() }) { Icon(Icons.Default.ArrowBack, "Retour", tint = Color.White) }
            Text(c.name, fontSize = 18.sp, color = Color.White, maxLines = 1, modifier = Modifier.weight(1f))
        }
        if (data == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.width(220.dp).fillMaxSize().verticalScroll(rememberScrollState())) {
                Poster(c.logo, Modifier.width(130.dp).aspectRatio(2f / 3f))
                Text(
                    data.plot.ifBlank { c.plot }.ifBlank { "Pas de résumé." },
                    color = Color(0xFFB8C4DA), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            if (data.seasons.isEmpty()) {
                Message("Aucun épisode trouvé.")
            } else {
                val keys = data.seasons.keys.toList()
                val cur = if (season in keys) season else keys.first()
                val eps = data.seasons[cur].orEmpty()
                Column(Modifier.weight(1f).fillMaxSize()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(keys, key = { it }) { k ->
                            FilterChip(selected = k == cur, onClick = { season = k }, label = { Text("Saison $k") })
                        }
                    }
                    Spacer(Modifier.size(6.dp))
                    LazyColumn(Modifier.weight(1f)) {
                        items(eps, key = { it.id }) { e ->
                            FocusRow(selected = false, onClick = { vm.play(eps, e) }) {
                                Text(e.name, color = Color.White, maxLines = 2, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Message(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(text, color = Color.White) }
}

@Composable
private fun PreviewHeader(vm: MainVm, pv: Channel) {
    val e = vm.epg[pv.id]
    Row(Modifier.fillMaxWidth().height(118.dp).background(GroupBg).padding(8.dp)) {
        Box(Modifier.fillMaxHeight().aspectRatio(16f / 9f).clip(RoundedCornerShape(6.dp)).background(Color.Black)) { PreviewBox(pv) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(pv.name, color = Color.White, fontSize = 18.sp, maxLines = 1)
            val now = e?.now
            if (now != null) {
                Text("${now.title}  ${hm(now.start)} – ${hm(now.stop)}", color = Color(0xFF9DB4D8), fontSize = 12.sp, maxLines = 2)
                val span = (now.stop - now.start).coerceAtLeast(1)
                val frac = ((System.currentTimeMillis() - now.start).toFloat() / span).coerceIn(0f, 1f)
                Box(Modifier.padding(top = 4.dp).fillMaxWidth(0.6f).height(3.dp).background(Color(0x33FFFFFF))) {
                    Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(Accent))
                }
                if (e.next != null) Text("Ensuite : ${e.next.title}", color = Color(0xFF8A97A6), fontSize = 11.sp, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
            }
            Spacer(Modifier.weight(1f))
            Text("Appuie encore sur la chaîne pour le plein écran", color = Color(0xFF8FB8FF), fontSize = 11.sp)
        }
    }
}

@Composable
private fun EpgCell(title: String?, sub: String?, progress: Float?, modifier: Modifier) {
    Box(modifier.padding(horizontal = 2.dp).clip(RoundedCornerShape(5.dp)).background(CellBg)) {
        Column(Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
            Text(title ?: "Pas d'information", color = if (title == null) Color(0xFF7D8A96) else Color.White, fontSize = 12.sp, maxLines = 1)
            if (sub != null) Text(sub, color = Color(0xFF9DB4D8), fontSize = 10.sp, maxLines = 1)
        }
        if (progress != null) {
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(progress.coerceIn(0f, 1f)).height(2.dp).background(Accent))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelList(vm: MainVm, list: List<Channel>, modifier: Modifier) {
    if (list.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) { Text("Aucune chaîne.", color = Color.Gray) }
        return
    }
    BoxWithConstraints(modifier) {
        val showNext = maxWidth >= 430.dp
        Column(Modifier.fillMaxSize()) {
            // en-tête façon guide : date à gauche, « maintenant » / « ensuite » au-dessus des cases
            Row(Modifier.fillMaxWidth().background(Color(0xFF2B3944)).padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                val today = remember { java.text.SimpleDateFormat("EEE d MMM  HH:mm", java.util.Locale.getDefault()).format(java.util.Date()) }
                Text(today, color = Color(0xFF8FB8FF), fontSize = 11.sp, maxLines = 1, modifier = Modifier.width(200.dp))
                Text("Maintenant", color = Color(0xFFB8C4DA), fontSize = 11.sp, modifier = Modifier.weight(1f).padding(start = 6.dp))
                if (showNext) Text("Ensuite", color = Color(0xFFB8C4DA), fontSize = 11.sp, modifier = Modifier.weight(0.8f).padding(start = 6.dp))
            }
            LazyColumn(Modifier.weight(1f)) {
                itemsIndexed(list, key = { _, c -> c.id }) { i, c ->
                    FocusRow(
                        selected = false,
                        onClick = { vm.guard(c) { if (vm.previewMode && vm.preview?.id != c.id) vm.preview = c else vm.play(list, c) } },
                        onLongClick = { vm.toggleFav(c) },
                    ) {
                        LaunchedEffect(c.id, vm.xmltvReady) { vm.loadEpg(c) }
                        val e = vm.epg[c.id]
                        Text("${i + 1}", color = Color(0xFFB8C4DA), fontSize = 13.sp, modifier = Modifier.width(30.dp))
                        if (c.logo.isNotBlank()) {
                            AsyncImage(
                                model = c.logo,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.size(34.dp).clip(RoundedCornerShape(5.dp)),
                            )
                        } else {
                            Box(Modifier.size(34.dp).clip(RoundedCornerShape(5.dp)).background(Panel))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            (if (vm.isLocked(c)) "🔒 " else "") + (if (vm.isFav(c)) "★ " else "") + c.name,
                            color = Color.White, fontSize = 13.sp, maxLines = 1,
                            modifier = Modifier.width(128.dp),
                        )
                        val now = e?.now
                        val span = if (now != null) (now.stop - now.start).coerceAtLeast(1) else 1L
                        val frac = if (now != null) ((System.currentTimeMillis() - now.start).toFloat() / span) else null
                        EpgCell(now?.title, now?.let { "${hm(it.start)} – ${hm(it.stop)}" }, frac, Modifier.weight(1f))
                        if (showNext) EpgCell(e?.next?.title, e?.next?.let { hm(it.start) }, null, Modifier.weight(0.8f))
                    }
                }
            }
        }
    }
}

val PanelHead = Color(0xFF32409A)
val PanelBody = Color(0xFF232C6B)

/** Une ligne d'un panneau de réglages : titre, petit texte, élément à droite (interrupteur, valeur…). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PanelItem(
    title: String,
    sub: String? = null,
    first: Boolean = false,
    onLong: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val fr = remember { androidx.compose.ui.focus.FocusRequester() }
    if (first) LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    val shape = RoundedCornerShape(6.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 1.dp)
            .clip(shape)
            .background(if (focused) Accent else Color.Transparent)
            .androidx_focusRequester(fr)
            .onFocusChanged { focused = it.isFocused }
            .combinedClickable(onClick = onClick, onLongClick = onLong)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 14.sp)
            if (!sub.isNullOrBlank()) Text(sub, color = Color(0xFFB8C4EE), fontSize = 11.sp)
        }
        if (trailing != null) trailing()
    }
}

private fun Modifier.androidx_focusRequester(fr: androidx.compose.ui.focus.FocusRequester): Modifier =
    this.then(Modifier.focusRequester(fr))

@Composable
private fun PanelSwitch(on: Boolean) {
    androidx.compose.material3.Switch(checked = on, onCheckedChange = null)
}

@Composable
private fun PanelLabel(text: String) {
    Text(text, color = Color(0xFF8FB8FF), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp))
}

/** Panneau de réglages qui glisse par-dessus l'accueil, côté droit. */
@Composable
fun SettingsScreen(vm: MainVm, back: () -> Unit) {
    var page by rememberSaveable { mutableStateOf(if (vm.sources.isEmpty()) "add" else "root") }
    val canClose = vm.sources.isNotEmpty()
    BackHandler(true) {
        when {
            page.startsWith("src:") || (page == "add" && canClose) -> page = "lists"
            page == "lists" || page == "groups" || page == "look" || page == "playback" || page == "rec" || page == "parental" || page == "update" || page == "about" -> page = "root"
            canClose -> back()
        }
    }

    val title = when {
        page == "root" -> "Paramètres"
        page == "lists" -> "Listes de lecture"
        page == "add" -> "Ajouter une liste"
        page.startsWith("src:") -> vm.sources.firstOrNull { it.id == page.removePrefix("src:") }?.name?.ifBlank { "Liste" } ?: "Liste"
        page == "groups" -> "Groupes"
        page == "playback" -> "Lecture"
        page == "look" -> "Apparence"
        page == "rec" -> "Enregistrements"
        page == "parental" -> "Contrôle parental"
        page == "update" -> "Mises à jour"
        else -> "À propos"
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = if (maxWidth > 400.dp) 360.dp else maxWidth * 0.92f
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxHeight().background(Color.Black.copy(alpha = 0.35f)).clickable(enabled = canClose, onClick = back))
            Column(Modifier.width(w).fillMaxHeight().background(PanelBody)) {
                Text(title, color = Color.White, fontSize = 18.sp, modifier = Modifier.fillMaxWidth().background(PanelHead).padding(horizontal = 18.dp, vertical = 14.dp))
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 6.dp)) {
                    when {
                        page == "root" -> {
                            PanelItem("Listes de lecture", first = true) { page = "lists" }
                            PanelItem("Groupes", "Masquer ou verrouiller des catégories") { page = "groups" }
                            PanelItem("Apparence", "Couleur : ${ACCENTS[vm.accentIdx].first}") { page = "look" }
                            PanelItem("Lecture") { page = "playback" }
                            PanelItem("Enregistrements") { page = "rec" }
                            PanelItem("Contrôle parental") { page = "parental" }
                            PanelItem("Mises à jour", "Version ${BuildConfig.VERSION_NAME}") { page = "update" }
                            PanelItem("À propos") { page = "about" }
                        }
                        page == "lists" -> {
                            vm.sources.forEachIndexed { i, s ->
                                PanelItem(
                                    s.name.ifBlank { "Liste" },
                                    if (s.type == SourceType.M3U) "M3U" else "Xtream Codes",
                                    first = i == 0,
                                    trailing = { if (s.id == vm.activeId) Text("✔", color = Color.White) },
                                ) { page = "src:" + s.id }
                            }
                            PanelItem("Ajouter une liste", first = vm.sources.isEmpty()) { page = "add" }
                        }
                        page.startsWith("src:") -> {
                            val id = page.removePrefix("src:")
                            PanelItem("Utiliser cette liste", first = true) { vm.select(id); back() }
                            PanelItem("Supprimer cette liste", "Efface la liste de l'appli") {
                                vm.deleteSource(id)
                                if (vm.sources.isEmpty()) page = "add" else page = "lists"
                            }
                        }
                        page == "add" -> AddSourceForm(vm, canClose) { page = "lists"; back() }
                        page == "groups" -> GroupsPage(vm)
                        page == "look" -> {
                            PanelLabel("Couleur de l'interface")
                            ACCENTS.forEachIndexed { i, (n, c) ->
                                PanelItem(n, first = i == 0, trailing = { if (i == vm.accentIdx) Text("✔", color = Color.White) else Box(Modifier.size(14.dp).clip(RoundedCornerShape(7.dp)).background(c)) }) { vm.changeAccent(i) }
                            }
                        }
                        page == "playback" -> {
                            PanelItem("Reprendre la dernière chaîne", "Au démarrage de l'appli", first = true, trailing = { PanelSwitch(vm.autoResume) }) { vm.changeAutoResume(!vm.autoResume) }
                            PanelItem("Aperçu avant le plein écran", "Un clic = aperçu, deux clics = plein écran", trailing = { PanelSwitch(vm.previewMode) }) { vm.changePreviewMode(!vm.previewMode) }
                        }
                        page == "rec" -> Column(Modifier.padding(horizontal = 12.dp)) { RecordingsSection(vm) }
                        page == "parental" -> Column(Modifier.padding(horizontal = 12.dp)) { ParentalSection(vm) }
                        page == "update" -> Column(Modifier.padding(horizontal = 12.dp)) { UpdateSection(vm) }
                        else -> {
                            PanelLabel("theIPTVapp pour Android")
                            Text("Version ${BuildConfig.VERSION_NAME}", color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 18.dp))
                            Text("Studio Gyro", color = Color(0xFFB8C4EE), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddSourceForm(vm: MainVm, canCancel: Boolean, done: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf(SourceType.M3U) }
    var url by rememberSaveable { mutableStateOf("") }
    var host by rememberSaveable { mutableStateOf("") }
    var user by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }
    PanelLabel("Type de liste")
    PanelItem("Liste M3U", first = true, trailing = { PanelSwitch(type == SourceType.M3U) }) { type = SourceType.M3U }
    PanelItem("Xtream Codes", trailing = { PanelSwitch(type == SourceType.XTREAM) }) { type = SourceType.XTREAM }
    Column(Modifier.padding(horizontal = 12.dp)) {
        Spacer(Modifier.size(6.dp))
        OutlinedTextField(name, { name = it }, label = { Text("Nom") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (type == SourceType.M3U) {
            OutlinedTextField(
                url, { url = it }, label = { Text("Adresse de la liste (http…)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth(),
            )
        } else {
            OutlinedTextField(
                host, { host = it }, label = { Text("Serveur (http://exemple.com:8080)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(user, { user = it }, label = { Text("Identifiant") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                pass, { pass = it }, label = { Text("Mot de passe") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.size(10.dp))
        val ok = if (type == SourceType.M3U) url.isNotBlank() else host.isNotBlank() && user.isNotBlank() && pass.isNotBlank()
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = ok,
                onClick = {
                    vm.addSource(
                        Source(vm.newId(), name.ifBlank { if (type == SourceType.M3U) "Ma liste" else "Mon Xtream" }, type, url.trim(), host.trim(), user.trim(), pass),
                    )
                    name = ""; url = ""; host = ""; user = ""; pass = ""
                    done()
                },
            ) { Text("Ajouter") }
        }
    }
}

@Composable
private fun GroupsPage(vm: MainVm) {
    val sec = vm.section()
    val kind = when (vm.tab) { 0 -> "Direct"; 1 -> "Films"; else -> "Séries" }
    val names = sec.tops
    PanelLabel("$kind — interrupteur : afficher ou masquer. Appui long : verrouiller (code).")
    PanelItem("Tout afficher", first = true) { vm.setHidden(names, false) }
    PanelItem("Tout masquer") { vm.setHidden(names, true) }
    if (names.isEmpty()) Text("Aucun groupe chargé pour l'instant.", color = Color(0xFFB8C4EE), fontSize = 12.sp, modifier = Modifier.padding(18.dp))
    names.forEach { n ->
        PanelItem(
            (if (vm.catLocked(n)) "🔒 " else "") + n,
            onLong = { vm.toggleCatLock(n) },
            trailing = { PanelSwitch(n !in vm.hiddenCats) },
        ) { vm.toggleHidden(n) }
    }
}

fun hm(ms: Long): String = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(ms))

@Composable
fun PinDialog(vm: MainVm) {
    if (vm.pendingAction == null) return
    var pin by remember { mutableStateOf("") }
    var bad by remember { mutableStateOf(false) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { vm.cancelPin() },
        title = { Text("Code parental") },
        text = {
            Column {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter(Char::isDigit); bad = false },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    isError = bad,
                )
                if (bad) Text("Code incorrect.", color = Color(0xFFFF8A8A))
            }
        },
        confirmButton = { Button(onClick = { if (!vm.tryPin(pin)) { bad = true; pin = "" } }) { Text("OK") } },
        dismissButton = { OutlinedButton(onClick = { vm.cancelPin() }) { Text("Annuler") } },
    )
}

@Composable
private fun ParentalSection(vm: MainVm) {
    var pin by rememberSaveable { mutableStateOf("") }
    var kw by rememberSaveable { mutableStateOf(vm.keywords.joinToString(", ")) }
    Spacer(Modifier.size(24.dp))
    Text("Contrôle parental", fontSize = 18.sp, color = Color.White)
    Text(
        "Verrouille par un code les chaînes, films et séries dont le nom ou la catégorie contient un des mots ci-dessous.",
        color = Color.Gray, fontSize = 12.sp,
    )
    Spacer(Modifier.size(8.dp))
    if (vm.parentalOn()) {
        val watched = remember(vm.keywords, vm.live.items, vm.movies.items, vm.series.items) { vm.watchedCount() }
        Text("Actif — mots : ${vm.keywords.joinToString(", ")}", color = Color.White)
        Text("$watched élément(s) verrouillé(s) parmi les listes chargées." + if (vm.unlocked) " (déverrouillé pour l'instant)" else "", color = Color.Gray, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            OutlinedButton(onClick = { vm.lockNow() }, enabled = vm.unlocked) { Text("Verrouiller maintenant") }
            OutlinedButton(onClick = { vm.disableParental() }, enabled = vm.unlocked) { Text("Désactiver") }
        }
        Text("Appui long sur une catégorie (colonne de gauche) pour la verrouiller ou la déverrouiller.", color = Color.Gray, fontSize = 12.sp)
        if (!vm.unlocked) Text("Ouvre un élément verrouillé et entre le code pour modifier ces réglages.", color = Color.Gray, fontSize = 12.sp)
    } else {
        OutlinedTextField(kw, { kw = it }, label = { Text("Mots à verrouiller (séparés par des virgules)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            pin, { pin = it.filter(Char::isDigit) }, label = { Text("Nouveau code (4 chiffres ou plus)") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            enabled = pin.length >= 4 && kw.isNotBlank(),
            onClick = { vm.setParental(pin, kw.split(',')); pin = "" },
            modifier = Modifier.padding(top = 8.dp),
        ) { Text("Activer") }
    }
}

@Composable
fun UpdateDialog(vm: MainVm) {
    val up = vm.update ?: return
    if (vm.updateDismissed) return
    val ctx = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { if (vm.updateProgress < 0) vm.updateDismissed = true },
        title = { Text("Mise à jour disponible") },
        text = {
            Column {
                Text("Version 0.1.${up.code} (tu as la ${BuildConfig.VERSION_NAME}).")
                if (vm.updateProgress >= 0) {
                    Spacer(Modifier.size(8.dp))
                    androidx.compose.material3.LinearProgressIndicator(progress = { vm.updateProgress / 100f }, modifier = Modifier.fillMaxWidth())
                    Text("Téléchargement… ${vm.updateProgress} %", fontSize = 12.sp)
                }
            }
        },
        confirmButton = { Button(enabled = vm.updateProgress < 0, onClick = { vm.installUpdate(ctx) }) { Text("Installer") } },
        dismissButton = { OutlinedButton(enabled = vm.updateProgress < 0, onClick = { vm.updateDismissed = true }) { Text("Plus tard") } },
    )
}

@Composable
private fun UpdateSection(vm: MainVm) {
    Spacer(Modifier.size(24.dp))
    Text("Mises à jour", fontSize = 18.sp, color = Color.White)
    Text("Version installée : ${BuildConfig.VERSION_NAME}", color = Color.Gray, fontSize = 12.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
        OutlinedButton(onClick = { vm.checkUpdate(silent = false) }) { Text("Vérifier maintenant") }
        if (vm.update != null) Button(onClick = { vm.updateDismissed = false }) { Text("Installer la nouvelle version") }
    }
    if (vm.updateInfo.isNotBlank()) Text(vm.updateInfo, color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun PlaybackSection(vm: MainVm) {
    Spacer(Modifier.size(24.dp))
    Text("Lecture", fontSize = 18.sp, color = Color.White)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        Text("Reprendre la dernière chaîne au démarrage", color = Color.White, modifier = Modifier.weight(1f))
        androidx.compose.material3.Switch(checked = vm.autoResume, onCheckedChange = { vm.changeAutoResume(it) })
    }
}

@Composable
private fun RecordingsSection(vm: MainVm) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val files = remember(vm.recVersion, vm.recording) { vm.recordings(ctx) }
    Spacer(Modifier.size(24.dp))
    Text("Enregistrements", fontSize = 18.sp, color = Color.White)
    vm.recording?.let { Text("● En cours : ${it.name} (${it.file.length() / 1_000_000} Mo)", color = Color(0xFFFF6B6B), fontSize = 12.sp) }
    if (vm.recMessage.isNotBlank()) Text(vm.recMessage, color = Color(0xFFFF8A8A), fontSize = 12.sp)
    if (files.isEmpty()) Text("Aucun enregistrement. Ouvre une chaîne en direct et touche ⏺ REC.", color = Color.Gray, fontSize = 12.sp)
    files.forEach { f ->
        FocusRow(selected = false, onClick = {
            val ch = Channel(id = "r" + f.name, name = f.name.removeSuffix(".ts"), url = f.toURI().toString(), kind = "movie")
            vm.play(listOf(ch), ch)
        }) {
            Column(Modifier.weight(1f)) {
                Text(f.name.removeSuffix(".ts"), color = Color.White, maxLines = 1)
                Text("${f.length() / 1_000_000} Mo", color = Color.Gray, fontSize = 12.sp)
            }
            IconButton(onClick = { vm.deleteRecording(f) }) { Icon(Icons.Default.Delete, "Supprimer", tint = Color(0xFFFF8A8A)) }
        }
    }
}
