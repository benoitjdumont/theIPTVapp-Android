@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.studiogyro.theiptvapp

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.width
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.KeyboardArrowRight
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
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
    "Gris" to Color(0xFF7C8593),
)

/** ombre sous les textes de détail : lisibles même sur une image de fond */
val detailShadow = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(color = Color.Black, offset = androidx.compose.ui.geometry.Offset(0f, 2f), blurRadius = 8f))
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
    val safe = if (tv) Modifier.padding(horizontal = 20.dp, vertical = 10.dp) else Modifier
    val dens = androidx.compose.ui.platform.LocalDensity.current
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(dens.density, dens.fontScale * TEXT_SIZES[vm.textSizeIdx].second),
    ) {
    MaterialTheme(colorScheme = scheme) {
        PinDialog(vm)
        UpdateDialog(vm)
        var screen by rememberSaveable { mutableStateOf(if (vm.sources.isEmpty()) "settings" else "home") }
        LaunchedEffect(vm.openSettingsRequest) {
            if (vm.openSettingsRequest) { screen = "settings"; vm.openSettingsRequest = false }
        }
        // Retour sur l'accueil : quitte, ou demande une seconde pression si la confirmation est activée
        var lastBack by remember { mutableStateOf(0L) }
        val onHome = vm.current == null && vm.movieOpen == null && vm.seriesOpen == null && screen == "home" && !vm.searchOpen
        val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
        // le bouton Retour de la manette agit comme la flèche gauche ; quand on est déjà à gauche, il quitte
        BackHandler(enabled = onHome && !Nav.settingsOpen) {
            if (focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Left)) return@BackHandler
            val now = System.currentTimeMillis()
            if (!vm.doubleBack || now - lastBack < 2000) {
                (ctx as? android.app.Activity)?.finish()
            } else {
                lastBack = now
                android.widget.Toast.makeText(ctx, "Appuie encore sur Retour pour quitter", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        Surface(Modifier.fillMaxSize().background(BgBrush), color = Color.Transparent) {
            vm.bgPath?.let { path ->
                AsyncImage(model = java.io.File(path), contentDescription = null, contentScale = ContentScale.Crop, alpha = 0.6f, modifier = Modifier.fillMaxSize())
            }
            if (vm.current != null) {
                PlayerScreen(vm)
            } else if (vm.movieOpen != null) {
                Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars).then(safe)) { MovieScreen(vm) }
            } else if (vm.seriesOpen != null) {
                Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars).then(safe)) { SeriesScreen(vm) }
            } else {
                // keep clear of the status bar (battery, clock) and the navigation bar
                Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars).then(safe)) {
                    // l'accueil reste à sa place ; les préférences glissent par-dessus, de droite à gauche
                    if (vm.sources.isNotEmpty() || screen != "settings") HomeScreen(vm) { screen = "settings" }
                    if (screen == "settings" && vm.sources.isNotEmpty()) Box(Modifier.fillMaxSize().clickable { screen = "home" })
                    Nav.settingsOpen = screen == "settings"
                    androidx.compose.animation.AnimatedVisibility(
                        visible = screen == "settings",
                        modifier = Modifier.align(Alignment.CenterEnd),
                        enter = androidx.compose.animation.slideInHorizontally(initialOffsetX = { it }),
                        exit = androidx.compose.animation.slideOutHorizontally(targetOffsetX = { it }),
                    ) {
                        SettingsScreen(vm, docked = vm.sources.isNotEmpty()) { screen = "home" }
                    }
                }
            }
            if (vm.searchOpen && vm.current == null) SearchOverlay(vm)
        }
    }
    }
}

/** A row that is clearly highlighted when focused with a remote control. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FocusRow(selected: Boolean, onClick: () -> Unit, onLongClick: (() -> Unit)? = null, requester: androidx.compose.ui.focus.FocusRequester? = null, blockUp: Boolean = true, content: @Composable RowScope.() -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (requester != null) Modifier.focusRequester(requester).then(if (blockUp) Modifier.focusProperties { up = androidx.compose.ui.focus.FocusRequester.Cancel } else Modifier) else Modifier)
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

var UiAlpha by mutableStateOf(1f)
val TEXT_SIZES = listOf("Petite" to 0.9f, "Moyenne" to 1f, "Grande" to 1.15f, "Très grande" to 1.3f)
val TRANSPS = listOf("Très transparente" to 0.5f, "Normale" to 1f, "Presque opaque" to 1.8f)
private fun tint(c: Long, a: Float) = Color(c or 0xFF000000L).copy(alpha = (a * UiAlpha).coerceIn(0f, 1f))
/** Couleurs des colonnes : suivent la couleur choisie dans Apparence.
 *  Rail (TV / Films / Séries / Recherche) et Préférences = couleur choisie ; catégories, chaînes et guide = nuance plus foncée. */
fun darker(c: Color, f: Float): Color = androidx.compose.ui.graphics.lerp(c, Color.Black, f)
val AccentDark get() = darker(Accent, 0.4f)
/** transparence des colonnes et des préférences (réglée dans Apparence) : 0 = opaque, 1 = transparent */
object GlassState { var transp by mutableStateOf(0f) }
/** vrai quand le panneau Préférences est ouvert : les autres « Retour » ne doivent pas le court-circuiter */
object Nav { var settingsOpen by mutableStateOf(false) }
fun glass(c: Color): Color = c.copy(alpha = 1f - GlassState.transp)
val RailBg get() = glass(Accent)
val GroupBg get() = glass(AccentDark)
val ListBg get() = glass(AccentDark)
val CellBg get() = tint(0x3A4A9A, 0.33f)

@Composable
private fun RailItem(selected: Boolean, expanded: Boolean, label: String, onClick: () -> Unit, enabled: Boolean = true, requester: androidx.compose.ui.focus.FocusRequester? = null, icon: @Composable () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp)
            .height(44.dp)
            .alpha(if (enabled) 1f else 0.3f)
            .clip(shape)
            .background(if (focused) AccentDark else if (selected) Color.White.copy(alpha = 0.18f) else Color.Transparent)
            .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clickable(enabled = enabled, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(44.dp), contentAlignment = Alignment.Center) { icon() }
        if (expanded) Text(label, color = Color.White, fontSize = 14.sp, maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}

@Composable
private fun TvIcon() = androidx.compose.foundation.Canvas(Modifier.size(24.dp)) {
    val c = Color.White
    val sw = 2.dp.toPx()
    drawRoundRect(c, androidx.compose.ui.geometry.Offset(sw / 2, 3.dp.toPx()), androidx.compose.ui.geometry.Size(size.width - sw, size.height - 9.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()), style = androidx.compose.ui.graphics.drawscope.Stroke(sw))
    drawLine(c, androidx.compose.ui.geometry.Offset(size.width * 0.3f, size.height - 2.dp.toPx()), androidx.compose.ui.geometry.Offset(size.width * 0.7f, size.height - 2.dp.toPx()), sw, androidx.compose.ui.graphics.StrokeCap.Round)
}

@Composable
private fun FilmIcon() = androidx.compose.foundation.Canvas(Modifier.size(24.dp)) {
    val c = Color.White
    val sw = 2.dp.toPx()
    val st = androidx.compose.ui.graphics.drawscope.Stroke(sw)
    drawRoundRect(c, androidx.compose.ui.geometry.Offset(sw / 2, 9.dp.toPx()), androidx.compose.ui.geometry.Size(size.width - sw, size.height - 12.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()), style = st)
    drawRoundRect(c, androidx.compose.ui.geometry.Offset(sw / 2, 3.dp.toPx()), androidx.compose.ui.geometry.Size(size.width - sw, 5.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(1.dp.toPx()), style = st)
    for (x in listOf(0.3f, 0.55f, 0.8f)) drawLine(c, androidx.compose.ui.geometry.Offset(size.width * x - 2.dp.toPx(), 8.dp.toPx()), androidx.compose.ui.geometry.Offset(size.width * x + 1.dp.toPx(), 3.dp.toPx()), sw * 0.8f)
}

@Composable
private fun SeriesIcon() = androidx.compose.foundation.Canvas(Modifier.size(24.dp)) {
    val c = Color.White
    val sw = 2.dp.toPx()
    val st = androidx.compose.ui.graphics.drawscope.Stroke(sw)
    drawRoundRect(c, androidx.compose.ui.geometry.Offset(5.dp.toPx(), sw / 2), androidx.compose.ui.geometry.Size(size.width - 6.dp.toPx(), size.height - 8.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()), style = st)
    drawRoundRect(c, androidx.compose.ui.geometry.Offset(sw / 2, 5.dp.toPx()), androidx.compose.ui.geometry.Size(size.width - 6.dp.toPx(), size.height - 7.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()), style = st)
    val path = androidx.compose.ui.graphics.Path().apply {
        moveTo(9.dp.toPx(), 10.dp.toPx())
        lineTo(9.dp.toPx(), 18.dp.toPx())
        lineTo(15.dp.toPx(), 14.dp.toPx())
        close()
    }
    drawPath(path, c)
}

/** Filtre hors du fil principal, après une courte pause de frappe : le clavier ne saccade plus sur les grosses listes. */
@Composable
private fun searchResults(vm: MainVm, items: List<Channel>, q: String): List<Channel> {
    val state = androidx.compose.runtime.produceState(emptyList<Channel>(), items, q) {
        if (q.isEmpty()) { value = emptyList(); return@produceState }
        kotlinx.coroutines.delay(250)
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { items.filter { vm.displayName(it).contains(q, ignoreCase = true) } }
    }
    return state.value
}

/** Recherche dans sa propre fenêtre : cherche dans TV, Films et Séries à la fois et dit où chaque résultat a été trouvé. */
@Composable
private fun SearchOverlay(vm: MainVm) {
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { vm.loadAllForSearch(); runCatching { focus.requestFocus() } }
    // un film, une série ou une chaîne lancé : la recherche se ferme
    LaunchedEffect(vm.current, vm.movieOpen, vm.seriesOpen) {
        if (vm.current != null || vm.movieOpen != null || vm.seriesOpen != null) vm.closeSearch()
    }
    BackHandler(true) { vm.closeSearch() }
    val q = vm.query.trim()
    val liveRes = searchResults(vm, vm.live.items, q)
    val movieRes = searchResults(vm, vm.movies.items, q)
    val seriesRes = searchResults(vm, vm.series.items, q)
    Column(Modifier.fillMaxSize().background(Color(0xFF0B1220)).windowInsetsPadding(WindowInsets.systemBars).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.closeSearch() }) { Icon(Icons.Default.ArrowBack, "Fermer", tint = Color.White) }
            OutlinedTextField(
                value = vm.query,
                onValueChange = { vm.query = it },
                singleLine = true,
                placeholder = { Text("Rechercher une chaîne, un film ou une série") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                modifier = Modifier.weight(1f).focusRequester(focus),
            )
        }
        Spacer(Modifier.size(8.dp))
        if (q.isEmpty()) {
            Text("Écris un nom : la recherche couvre les chaînes TV, les films et les séries.", color = Color.Gray, fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp))
        } else {
            Text(
                "J'ai trouvé ${liveRes.size} chaîne(s) TV, ${movieRes.size} film(s) et ${seriesRes.size} série(s) pour « $q ».",
                color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
            )
            if (vm.live.loading || vm.movies.loading || vm.series.loading) {
                Text("Chargement des listes…", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            if (q.isNotEmpty()) {
                searchGroup("Chaînes TV", liveRes, vm) { c -> vm.guard(c) { vm.play(liveRes, c) } }
                searchGroup("Films", movieRes, vm) { c -> vm.guard(c) { vm.selectTab(1); vm.openMovie(c) } }
                searchGroup("Séries", seriesRes, vm) { c -> vm.guard(c) { vm.selectTab(2); vm.openSeries(c) } }
            }
        }
    }
}

/** Un groupe de résultats : titre avec le nombre trouvé, puis chaque résultat avec l'endroit où il a été trouvé. */
private fun androidx.compose.foundation.lazy.LazyListScope.searchGroup(title: String, found: List<Channel>, vm: MainVm, onOpen: (Channel) -> Unit) {
    item(key = "h_$title") {
        Text("$title (${found.size})", color = Accent, fontSize = 16.sp, modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 4.dp))
    }
    if (found.isEmpty()) item(key = "e_$title") {
        Text("Rien trouvé dans $title", color = Color.Gray, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp))
    }
    items(found.take(80), key = { "${title}_${it.id}" }) { c ->
        FocusRow(selected = false, onClick = { onOpen(c) }) {
            Text(vm.displayName(c), color = Color.White, maxLines = 1, modifier = Modifier.weight(1f))
            Text("dans $title", color = Color(0xFF8A97A6), fontSize = 12.sp, maxLines = 1)
        }
    }
    if (found.size > 80) item(key = "m_$title") {
        Text("… et ${found.size - 80} autres : précise ta recherche.", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
    }
}

/** Rail de gauche : fin au repos, il se déploie par-dessus la liste quand on navigue dessus. */
@Composable
private fun Rail(vm: MainVm, expanded: Boolean, onFocus: (Boolean) -> Unit, searchOpen: Boolean, onSearch: () -> Unit, openSettings: () -> Unit, onChoose: () -> Unit = {}, firstReq: androidx.compose.ui.focus.FocusRequester? = null) {
    val focus = expanded
    Column(
        Modifier.width(if (focus) 170.dp else 56.dp).fillMaxHeight().background(RailBg).padding(vertical = 6.dp).onFocusChanged { onFocus(it.hasFocus) },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        RailItem(!vm.favMode && vm.tab == 0, focus, "TV", { vm.selectTab(0); onChoose() }, requester = firstReq) { TvIcon() }
        RailItem(!vm.favMode && vm.tab == 1, focus, "Films", { vm.selectTab(1); onChoose() }, enabled = vm.vodEnabled(1)) { FilmIcon() }
        RailItem(!vm.favMode && vm.tab == 2, focus, "Séries", { vm.selectTab(2); onChoose() }, enabled = vm.vodEnabled(2)) { SeriesIcon() }
        RailItem(vm.favMode, focus, "Ma liste", { vm.openFavMode(); onChoose() }) { Icon(Icons.Default.Star, "Ma liste", tint = Color.White) }
        RailItem(false, focus, "Enregistrements", { vm.settingsStart = "rec"; openSettings(); onChoose() }) { Box(Modifier.size(16.dp).background(Color(0xFFE53935), CircleShape)) }
        Spacer(Modifier.weight(1f))
        RailItem(searchOpen, focus, "Rechercher", { onSearch(); onChoose() }) { Icon(Icons.Default.Search, "Rechercher", tint = Color.White) }
        RailItem(false, focus, "Paramètres", { openSettings(); onChoose() }) { Icon(Icons.Default.Settings, "Paramètres", tint = Color.White) }
    }
}

/** Écran principal façon TiviMate : rail d'icônes, groupes, puis la liste (ou les affiches). */
@Composable
private fun HomeWide(vm: MainVm, openSettings: () -> Unit) {
    val sec = vm.section()
    // une fois une catégorie choisie, les colonnes de gauche se replient : la liste prend tout l'écran
    var showCols by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(vm.tab) { if (!vm.favMode) showCols = true }
    val colsHidden = !showCols
    var railFocus by remember { mutableStateOf(false) }
    var groupFocus by remember { mutableStateOf(false) }
    // grand au départ (noms visibles, aussi au toucher) ; il rétrécit en icônes dès qu'un choix est fait
    var railBig by remember { mutableStateOf(true) }
    LaunchedEffect(colsHidden) { if (!colsHidden) railBig = true }
    LaunchedEffect(groupFocus) { if (groupFocus) railBig = false }
    val railExpanded = railFocus || railBig
    val railReq = remember { androidx.compose.ui.focus.FocusRequester() }
    // Retour : si les colonnes sont ouvertes mais la colonne de gauche est réduite, elle se rouvre (au lieu de quitter)
    BackHandler(enabled = !colsHidden && !railExpanded && !Nav.settingsOpen) {
        railBig = true
        runCatching { railReq.requestFocus() }
    }
    // la catégorie choisie garde sa place : on y revient (focus + défilement) au lieu de la première
    val selectedRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val groupState = androidx.compose.foundation.lazy.rememberLazyListState()
    val entries = remember(sec.tops, vm.tab, vm.hiddenCats) {
        topEntries(sec, vm.tab == 0).filter { it.first == null || (it.first == GROUP_FAV || it.first == GROUP_HIST) || it.first !in vm.hiddenCats }
    }
    val listFirst = remember { androidx.compose.ui.focus.FocusRequester() }
    // on clique TV / Films / Séries : la colonne des catégories revient
    LaunchedEffect(vm.tabTick) {
        if (vm.tabTick > 0) showCols = true
    }
    // colonne des catégories affichée (retour arrière ou changement d'onglet) : on remet la catégorie choisie au centre
    LaunchedEffect(showCols, vm.tab, vm.tabTick) {
        if (showCols) {
            val i = entries.indexOfFirst { it.first == sec.top }
            if (i >= 0) groupState.scrollToItem(i)
            kotlinx.coroutines.delay(60)
            runCatching { selectedRequester.requestFocus() }
        }
    }
    // une catégorie choisie : les colonnes se replient et le focus va sur la liste
    LaunchedEffect(colsHidden) {
        if (colsHidden) {
            kotlinx.coroutines.delay(60)
            runCatching { listFirst.requestFocus() }
        }
    }
    BackHandler(enabled = colsHidden && !Nav.settingsOpen) { showCols = true }
    Box(Modifier.fillMaxSize()) {
    if (colsHidden) EdgeHandle(Modifier.align(Alignment.CenterStart).fillMaxHeight().width(14.dp)) { showCols = true }
    Row(Modifier.fillMaxSize()) {
        // rail : grand (avec les noms) au repos ; petit (icônes) quand on parcourt les catégories ; caché une fois une catégorie choisie
        if (!colsHidden) Rail(vm, railExpanded, { railFocus = it; if (it) railBig = true }, vm.searchOpen, { vm.openSearch() }, openSettings, { railBig = false; railFocus = false }, railReq)
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
                    val list = remember(sec.items, sec.top, sec.sub, vm.query, vm.favs, vm.history, vm.hiddenCats, vm.tab) { vm.visible() }
                    val subs = sec.subs()
                    Row(Modifier.fillMaxSize()) {
                        if (!colsHidden && vm.favMode) Column(Modifier.width(170.dp).fillMaxHeight().background(GroupBg).padding(horizontal = 4.dp, vertical = 4.dp).onFocusChanged { groupFocus = it.hasFocus }) {
                            listOf(0 to "★ Favoris TV", 1 to "★ Favoris Films", 2 to "★ Favoris Séries").forEach { (i, label) ->
                                FocusRow(
                                    selected = vm.tab == i && sec.top == GROUP_FAV,
                                    onClick = { vm.pickFav(i); showCols = false },
                                    requester = if (vm.tab == i) selectedRequester else null,
                                    blockUp = false,
                                ) {
                                    Text(label, color = if (vm.tab == i && sec.top == GROUP_FAV) Accent else Color.White, maxLines = 1)
                                }
                            }
                        }
                        if (!colsHidden && !vm.favMode) LazyColumn(Modifier.width(170.dp).fillMaxHeight().background(GroupBg).padding(horizontal = 4.dp, vertical = 4.dp).onFocusChanged { groupFocus = it.hasFocus }, state = groupState) {
                            itemsIndexed(entries, key = { _, e -> e.first ?: "\u0001all" }) { _, (g, label) ->
                                val lock = g != null && (g != GROUP_FAV && g != GROUP_HIST) && vm.catLocked(label)
                                FocusRow(
                                    selected = sec.top == g,
                                    onClick = {
                                        if (g == null || (g == GROUP_FAV || g == GROUP_HIST)) sec.pickTop(g) else vm.guardCat(label) { sec.pickTop(g) }
                                        showCols = false
                                    },
                                    onLongClick = if (g != null && (g != GROUP_FAV && g != GROUP_HIST)) ({ vm.toggleCatLock(label) }) else null,
                                    requester = if (g == sec.top) selectedRequester else null,
                                ) {
                                    Text((if (lock) "🔒 " else "") + label, color = if (sec.top == g) Accent else Color.White, maxLines = 1)
                                }
                            }
                        }
                        if (subs.isNotEmpty() && !colsHidden && !vm.favMode) {
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
                            val pv = vm.preview
                            if (vm.tab == 0 && vm.previewMode && pv != null) PreviewHeader(vm, pv)
                            if (vm.tab == 0) ChannelList(vm, list, Modifier.weight(1f), listFirst) else PosterWall(vm, list, Modifier.weight(1f), listFirst)
                        }
                    }
                }
            }
        }
    }

    }
}

/** Transparence : une seule zone de focus. Gauche / droite = -10 % / +10 % à la manette ; OK = palier de 25 % ; le curseur sert au tactile. */
@Composable
private fun GlassSetting(vm: MainVm) {
    var focused by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .onKeyEvent { ev ->
                if (ev.type == androidx.compose.ui.input.key.KeyEventType.KeyDown && (ev.key == androidx.compose.ui.input.key.Key.DirectionLeft || ev.key == androidx.compose.ui.input.key.Key.DirectionRight)) {
                    vm.changeGlass(GlassState.transp + if (ev.key == androidx.compose.ui.input.key.Key.DirectionRight) 0.1f else -0.1f)
                    true
                } else false
            }
            .clickable { vm.changeGlass(((Math.round(GlassState.transp * 4f) + 1) % 5) / 4f) }
            .background(if (focused) Color(0x33FFFFFF) else Color.Transparent)
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        Text("Transparence de l'interface : ${Math.round(GlassState.transp * 100)} %", color = Color.White, fontSize = 16.sp)
        androidx.compose.material3.Slider(value = GlassState.transp, onValueChange = { vm.changeGlass(it) }, modifier = Modifier.fillMaxWidth())
    }
}

/** Bord gauche replié : touche-le (ou la manette à gauche) pour ré-afficher les colonnes. */
@Composable
private fun EdgeHandle(modifier: Modifier = Modifier, onOpen: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            .background(if (focused) Color(0x33FFFFFF) else Color.Transparent)
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) onOpen() }
            .clickable(onClick = onOpen),
    )
}

@Composable
private fun HomeNarrow(vm: MainVm, openSettings: () -> Unit) {
    val sec = vm.section()
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        // une seule rangée : l'écran est toujours à l'horizontale
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Direct", "Films", "Séries").forEachIndexed { i, label ->
                val ok = i == 0 || vm.vodEnabled(i)
                FilterChip(selected = vm.tab == i, enabled = ok, onClick = { vm.selectTab(i) }, label = { Text(label) })
            }
            IconButton(onClick = { vm.openSearch() }) { Icon(Icons.Default.Search, "Rechercher", tint = Color.White) }
            Spacer(Modifier.weight(1f))
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
                val list = remember(sec.items, sec.top, sec.sub, vm.query, vm.favs, vm.history, vm.hiddenCats, vm.tab) { vm.visible() }
                val subs = sec.subs()
                val content: @Composable (Modifier) -> Unit = { m ->
                    if (vm.tab == 0) ChannelList(vm, list, m) else PosterGrid(vm, list, m)
                }
                if (maxWidth >= 480.dp) {
                    Row(Modifier.fillMaxSize()) {
                        LazyColumn(Modifier.width(170.dp).fillMaxSize()) {
                            items(topEntries(sec, vm.tab == 0), key = { it.first ?: "\u0001all" }) { (g, label) ->
                                val lock = g != null && (g != GROUP_FAV && g != GROUP_HIST) && vm.catLocked(label)
                                FocusRow(
                                    selected = sec.top == g,
                                    onClick = { if (g == null || (g == GROUP_FAV || g == GROUP_HIST)) sec.pickTop(g) else vm.guardCat(label) { sec.pickTop(g) } },
                                    onLongClick = if (g != null && (g != GROUP_FAV && g != GROUP_HIST)) ({ vm.toggleCatLock(label) }) else null,
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
                                FilterChip(selected = sec.top == g, onClick = { if (g == null || (g == GROUP_FAV || g == GROUP_HIST)) sec.pickTop(g) else vm.guardCat(label) { sec.pickTop(g) } }, label = { Text((if (g != null && (g != GROUP_FAV && g != GROUP_HIST) && vm.catLocked(label)) "🔒 " else "") + label, maxLines = 1) })
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
    l.add(GROUP_FAV to (if (live) "★ Favoris" else "★ Ma liste"))
    if (!live) l.add(GROUP_HIST to "🕘 Historique")
    sec.tops.forEach { l.add(it to it) }
    return l
}

/** Films et séries : fiche du titre survolé en haut, murs d'affiches en dessous. */
@Composable
private fun PosterWall(vm: MainVm, list: List<Channel>, modifier: Modifier, first: androidx.compose.ui.focus.FocusRequester? = null) {
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
        Box(Modifier.fillMaxWidth().height(112.dp).background(GroupBg).clipToBounds()) {
            if (cur.logo.isNotBlank()) AsyncImage(model = cur.logo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().alpha(0.2f))
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(cur.name, color = Color.White, fontSize = 19.sp, maxLines = 1)
            val meta = d?.meta?.ifBlank { null } ?: cur.meta
            if (meta.isNotBlank()) Text(meta, color = Color(0xFF9DB4D8), fontSize = 12.sp, maxLines = 1)
            if (d != null && d.cast.isNotBlank()) Text("Acteurs : " + d.cast, color = Color(0xFF8A97A6), fontSize = 11.sp, maxLines = 1)
            val plot = (d?.plot?.ifBlank { null } ?: cur.plot).ifBlank { if (d == null) "…" else "" }
            Text(plot, color = Color(0xFFB8C4DA), fontSize = 12.sp, maxLines = 3, modifier = Modifier.padding(top = 3.dp))
        }
        }
        PosterGrid(vm, list, Modifier.weight(1f).padding(6.dp), first) { sel = it }
    }
}

/** Poster wall for movies and series. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PosterGrid(vm: MainVm, list: List<Channel>, modifier: Modifier, first: androidx.compose.ui.focus.FocusRequester? = null, onFocus: (Channel) -> Unit = {}) {
    if (list.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) { Text("Rien à afficher.", color = Color.Gray) }
        return
    }
    LazyVerticalGrid(columns = GridCells.Adaptive(120.dp), modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        gridItemsIndexed(list, key = { _, it -> it.id }) { idx, c ->
            var focused by remember { mutableStateOf(false) }
            val grow by animateFloatAsState(if (focused) 1.07f else 1f, label = "posterGrow")
            val shape = RoundedCornerShape(10.dp)
            Column(
                Modifier
                    .then(if (idx == 0 && first != null) Modifier.focusRequester(first) else Modifier)
                    .scale(grow)
                    .clip(shape)
                    .background(if (focused) Accent.copy(alpha = 0.35f) else Panel)
                    .then(if (focused) Modifier.border(3.dp, Color.White, shape) else Modifier)
                    .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus(c) }
                    .combinedClickable(onClick = { vm.guard(c) { if (c.kind == "series") vm.openSeries(c) else vm.openMovie(c) } }, onLongClick = { vm.toggleFav(c) })
                    .padding(6.dp),
            ) {
                Poster(c.logo, Modifier.fillMaxWidth().aspectRatio(2f / 3f), c.name)
                // hauteur fixe pour le titre : toutes les affiches gardent la même dimension
                Box(Modifier.fillMaxWidth().height(40.dp).padding(top = 4.dp)) {
                    Text(
                        (if (vm.isLocked(c)) "🔒 " else "") + (if (vm.isFav(c)) "★ " else "") + c.name,
                        color = Color.White, fontSize = 12.sp, lineHeight = 15.sp, maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Bouton de saison : celui qui a le focus grossit et s'illumine, celui choisi reste en couleur pleine. */
@Composable
private fun SeasonChip(selected: Boolean, label: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val grow by animateFloatAsState(if (focused) 1.1f else 1f, label = "chipGrow")
    val bg by animateColorAsState(
        when {
            selected -> Accent
            focused -> Accent.copy(alpha = 0.35f)
            else -> Panel
        },
        label = "chipBg",
    )
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier
            .scale(grow)
            .clip(shape)
            .background(bg)
            .then(if (focused) Modifier.border(3.dp, Color.White, shape) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) { Text(label, color = Color.White, fontSize = 14.sp, maxLines = 1) }
}

@Composable
private fun Poster(url: String, modifier: Modifier, title: String = "") {
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(Color(0x55202A66)), contentAlignment = Alignment.Center) {
        Text(title.ifBlank { "🎞" }, color = Color(0xFF8A97C0), fontSize = 11.sp, maxLines = 4, modifier = Modifier.padding(6.dp))
        if (url.isNotBlank()) AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

/** Page d'un film : affiche, infos, puis Regarder / Ma liste / Bande annonce. */
@Composable
fun MovieScreen(vm: MainVm) {
    val c = vm.movieOpen ?: return
    BackHandler(true) { vm.closeMovie() }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val d = vm.details[c.id]
    val pos = vm.pos(c.id)
    val fr = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(c.id) { runCatching { fr.requestFocus() } }
    Box(Modifier.fillMaxSize()) {
    if (c.logo.isNotBlank()) AsyncImage(model = c.logo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().alpha(0.25f))
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.closeMovie() }) { Icon(Icons.Default.ArrowBack, "Retour", tint = Color.White) }
            Text("Film", fontSize = 14.sp, color = Color(0xFFB8C4DA))
        }
        Row(Modifier.fillMaxSize()) {
            Poster(c.logo, Modifier.width(140.dp).aspectRatio(2f / 3f), c.name)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f).fillMaxSize()) {
                Text(c.name, color = Color.White, fontSize = 22.sp, maxLines = 2, style = detailShadow)
                val meta = d?.meta?.ifBlank { null } ?: c.meta
                if (meta.isNotBlank()) Text(meta, color = Color(0xFF9DB4D8), fontSize = 13.sp, style = detailShadow, modifier = Modifier.padding(top = 2.dp))
                if (d != null && d.cast.isNotBlank()) Text("Acteurs : " + d.cast, color = Color(0xFFC9D3E0), fontSize = 12.sp, maxLines = 2, style = detailShadow, modifier = Modifier.padding(top = 4.dp))
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 8.dp)) {
                    Text(
                        (d?.plot?.ifBlank { null } ?: c.plot).ifBlank { if (d == null) "Chargement…" else "Pas de résumé." },
                        color = Color(0xFFB8C4DA), fontSize = 13.sp, style = detailShadow,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp).background(Color(0xCC0B1220), RoundedCornerShape(12.dp)).padding(8.dp)) {
                    Button(onClick = { vm.play(listOf(c), c) }, modifier = Modifier.androidx_focusRequester(fr)) {
                        Text(if (pos > 10_000) "Reprendre (" + "%d:%02d".format(pos / 60000, pos / 1000 % 60) + ")" else "▶ Regarder")
                    }
                    OutlinedButton(onClick = { vm.toggleFav(c) }) { Text(if (vm.isFav(c)) "★ Dans ma liste" else "☆ Ma liste") }
                    OutlinedButton(onClick = { if (vm.recording == null) vm.startRecording(ctx, c) else vm.stopRecording() }) { Text(if (vm.recording == null) "⏺ Enregistrer" else "⏹ Arrêter") }
                    val tr = d?.trailer.orEmpty()
                    if (tr.isNotBlank()) OutlinedButton(onClick = {
                        val url = if (tr.startsWith("http")) tr else "https://www.youtube.com/watch?v=$tr"
                        runCatching {
                            ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    }) { Text("Bande annonce") }
                }
            }
        }
    }
    }
}

/** Series page, laid out for a landscape screen: poster and summary on the left, seasons and episodes on the right. */
@Composable
fun SeriesScreen(vm: MainVm) {
    val c = vm.seriesOpen ?: return
    val data = vm.seriesData
    BackHandler(true) { vm.closeSeries() }
    var season by rememberSaveable(c.id) { mutableStateOf(-1) }
    val poster = c.logo.ifBlank { data?.cover.orEmpty() }
    Box(Modifier.fillMaxSize()) {
    if (poster.isNotBlank()) AsyncImage(model = poster, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().alpha(0.2f))
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.closeSeries() }) { Icon(Icons.Default.ArrowBack, "Retour", tint = Color.White) }
            Text(c.name, fontSize = 18.sp, color = Color.White, maxLines = 1, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { vm.toggleFav(c) }) { Text(if (vm.isFav(c)) "★ Dans ma liste" else "☆ Ma liste") }
        }
        if (data == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.width(220.dp).fillMaxSize().verticalScroll(rememberScrollState())) {
                Poster(poster, Modifier.width(150.dp).aspectRatio(2f / 3f), c.name)
                if (c.meta.isNotBlank()) Text(c.meta, color = Color(0xFF9DB4D8), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
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
                            SeasonChip(selected = k == cur, label = "Saison $k") { season = k }
                        }
                    }
                    Spacer(Modifier.size(6.dp))
                    LazyColumn(Modifier.weight(1f)) {
                        items(eps, key = { it.id }) { e ->
                            FocusRow(selected = false, onClick = { vm.play(eps, e) }) {
                                Box(Modifier.width(96.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(5.dp)).background(Color(0x55202A66)), contentAlignment = Alignment.Center) {
                                    Text("▶", color = Color(0xFF8A97C0), fontSize = 16.sp)
                                    val img = e.logo.ifBlank { c.logo.ifBlank { data.cover } }
                                    if (img.isNotBlank()) AsyncImage(model = img, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                }
                                Spacer(Modifier.width(10.dp))
                                Text(e.name, color = Color.White, maxLines = 2, modifier = Modifier.weight(1f))
                            }
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

/** Cadre de télévision autour de l'aperçu : antennes en haut, bezel, pattes en bas. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TvFrame(modifier: Modifier, onDoubleClick: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    val bezel = Color(0xFF1B1F27)
    Column(modifier.width(146.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
        // antennes plus courtes, de la même couleur que le cadre
        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(8.dp)) {
            val cx = size.width / 2f
            val sw = 1.5.dp.toPx()
            drawLine(bezel, androidx.compose.ui.geometry.Offset(cx, size.height), androidx.compose.ui.geometry.Offset(cx - size.width * 0.22f, 0f), strokeWidth = sw)
            drawLine(bezel, androidx.compose.ui.geometry.Offset(cx, size.height), androidx.compose.ui.geometry.Offset(cx + size.width * 0.22f, 0f), strokeWidth = sw)
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(bezel).padding(start = 6.dp, top = 6.dp, end = 6.dp, bottom = 1.dp),
        ) {
            Box(
                Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(3.dp)).background(Color.Black)
                    .combinedClickable(onClick = {}, onDoubleClick = onDoubleClick),
            ) { content() }
            Text(
                "THEIPTVAPP", color = Color(0xFF8A94A6), fontSize = 4.sp, maxLines = 1,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(Modifier.fillMaxWidth().height(6.dp), horizontalArrangement = Arrangement.SpaceAround) {
            Box(Modifier.width(22.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(bezel))
            Box(Modifier.width(22.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(bezel))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PreviewHeader(vm: MainVm, pv: Channel) {
    val e = vm.epg[pv.id]
    var replay by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().height(132.dp).background(ListBg).padding(8.dp)) {
        // deux touches sur l'aperçu = plein écran ; l'aperçu est dans un cadre de télévision
        TvFrame(Modifier.fillMaxHeight(), onDoubleClick = { vm.guard(pv) { vm.play(vm.visible(), pv) } }) {
            PreviewBox(pv, paused = vm.previewPausedId == pv.id)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(vm.displayName(pv), color = Color.White, fontSize = 17.sp, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                val fav = vm.isFav(pv)
                Text(
                    if (fav) "★" else "☆",
                    color = if (fav) Color(0xFFFFC107) else Color.White,
                    fontSize = 22.sp,
                    modifier = Modifier.padding(start = 8.dp).clickable { vm.toggleFav(pv) },
                )
            }
            val now = e?.now
            if (now != null) {
                Text("${now.title}  ${hm(now.start)} – ${hm(now.stop)}", color = Color(0xFF9DB4D8), fontSize = 12.sp, maxLines = 1)
                val span = (now.stop - now.start).coerceAtLeast(1)
                val frac = ((System.currentTimeMillis() - now.start).toFloat() / span).coerceIn(0f, 1f)
                Box(Modifier.padding(top = 3.dp).fillMaxWidth(0.6f).height(3.dp).background(Color(0x33FFFFFF))) {
                    Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(Accent))
                }
                if (e.next != null) Text("Ensuite : ${e.next.title}", color = Color(0xFF8A97A6), fontSize = 11.sp, maxLines = 1, modifier = Modifier.padding(top = 3.dp))
            }
            Spacer(Modifier.weight(1f))
            if (pv.archive > 0) {
                Button(
                    onClick = { replay = true; vm.loadPast(pv) },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 2.dp),
                    modifier = Modifier.height(34.dp),
                ) { Text("⏪  Revoir une émission", fontSize = 13.sp) }
            }
        }
    }
    if (replay) androidx.compose.material3.AlertDialog(
        onDismissRequest = { replay = false },
        title = { Text("Revoir sur ${pv.name}") },
        text = {
            val past = vm.past
            when {
                past == null -> Text("Chargement…")
                past.isEmpty() -> Text("Aucune émission disponible pour le rattrapage.")
                else -> LazyColumn(Modifier.heightIn(max = 260.dp)) {
                    items(past, key = { it.start }) { p ->
                        FocusRow(selected = false, onClick = { replay = false; vm.playReplay(pv, p) }) {
                            Text("${hm(p.start)}  ${p.title}", maxLines = 2, modifier = Modifier.padding(vertical = 6.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { OutlinedButton(onClick = { replay = false }) { Text("Fermer") } },
    )
}

@Composable
private fun EpgCell(title: String?, sub: String?, progress: Float?, modifier: Modifier, highlight: Boolean = false, twoLines: Boolean = false) {
    Box(modifier.padding(horizontal = 1.dp).clip(RoundedCornerShape(4.dp)).background(if (highlight) Accent.copy(alpha = 0.35f) else CellBg)) {
        Column(Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
            Text(title ?: "Pas d'information", color = if (title == null) Color(0xFF7D8A96) else Color.White, fontSize = 12.sp, maxLines = if (twoLines) 2 else 1)
            if (sub != null) Text(sub, color = Color(0xFF9DB4D8), fontSize = 10.sp, maxLines = 1)
        }
        if (progress != null) {
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(progress.coerceIn(0f, 1f)).height(2.dp).background(Accent))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelList(vm: MainVm, list: List<Channel>, modifier: Modifier, first: androidx.compose.ui.focus.FocusRequester? = null) {
    if (list.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) { Text("Aucune chaîne.", color = Color.Gray) }
        return
    }
    BoxWithConstraints(modifier) {
        val leftW = 200.dp
        val gridW = (maxWidth - leftW - 18.dp).coerceAtLeast(80.dp)
        val winMin = if (gridW < 300.dp) 90 else if (gridW < 460.dp) 120 else 180
        val nowMs = vm.clock
        val ws = nowMs - nowMs % (30 * 60_000L) + vm.guideShiftMin * 60_000L
        val dpPerMs = gridW.value / (winMin * 60_000f)
        fun xOf(t: Long) = (((t - ws).coerceIn(0L, winMin * 60_000L)) * dpPerMs).dp
        Column(Modifier.fillMaxSize()) {
            // en-tête : date à gauche, repères horaires au-dessus de la grille
            Row(Modifier.fillMaxWidth().background(ListBg).padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.width(leftW), verticalAlignment = Alignment.CenterVertically) {
                    val label = java.text.SimpleDateFormat("EEE d MMM  HH:mm", java.util.Locale.getDefault()).format(java.util.Date(ws))
                    Text(label, color = Color(0xFF8FB8FF), fontSize = 11.sp, maxLines = 1, modifier = Modifier.weight(1f))
                    Text("◀", color = Color.White, fontSize = 14.sp, modifier = Modifier.clickable { vm.shiftGuide(-60) }.padding(horizontal = 8.dp, vertical = 2.dp))
                    Text(if (vm.guideShiftMin == 0) "▶" else "⟲", color = Color.White, fontSize = 14.sp, modifier = Modifier.clickable { vm.shiftGuide(if (vm.guideShiftMin == 0) 60 else 0) }.padding(horizontal = 4.dp, vertical = 2.dp))
                    if (vm.guideShiftMin != 0) Text("▶", color = Color.White, fontSize = 14.sp, modifier = Modifier.clickable { vm.shiftGuide(60) }.padding(horizontal = 4.dp, vertical = 2.dp))
                }
                Box(Modifier.width(gridW).height(22.dp)) {
                    for (k in 0..(winMin / 30)) {
                        val t = ws + k * 30 * 60_000L
                        if (k < winMin / 30 || winMin % 30 == 0) Text(hm(t), color = Color(0xFFB8C4DA), fontSize = 11.sp, maxLines = 1, softWrap = false, modifier = Modifier.offset(x = xOf(t)))
                    }
                }
            }
            LazyColumn(Modifier.weight(1f)) {
                itemsIndexed(list, key = { _, c -> c.id }) { i, c ->
                    FocusRow(
                        selected = false,
                        onClick = { vm.guard(c) { if (vm.previewMode && vm.preview?.id != c.id) vm.preview = c else vm.play(list, c) } },
                        onLongClick = { vm.toggleFav(c) },
                        requester = if (i == 0) first else null,
                    ) {
                        LaunchedEffect(c.id, vm.xmltvReady) { vm.loadEpg(c) }
                        val e = vm.epg[c.id]
                        val vOpt = vm.optVersion
                        if (vm.opt("numbers")) Text("${i + 1}", color = Color(0xFFB8C4DA), fontSize = 13.sp, modifier = Modifier.width(30.dp))
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
                        if (vm.opt("replay") && c.archive > 0) Text("⏪ ", color = Accent, fontSize = 12.sp)
                        if (vm.opt("names")) Text(
                            (if (vm.isLocked(c)) "🔒 " else "") + (if (vm.isFav(c)) "★ " else "") + vm.displayName(c),
                            color = if (vm.opt("curcolor") && vm.current?.id == c.id) Color(0xFF7FB2FF) else Color.White,
                            fontSize = 13.sp, maxLines = 1,
                            modifier = Modifier.width(128.dp),
                        )
                        val progs = remember(e?.list, ws, winMin) { (e?.list ?: emptyList()).filter { it.stop > ws && it.start < ws + winMin * 60_000L } }
                        val sctx = androidx.compose.ui.platform.LocalContext.current
                        Box(Modifier.width(gridW).height(36.dp).clip(RoundedCornerShape(4.dp))) {
                            if (progs.isEmpty()) {
                                EpgCell(e?.now?.title, null, null, Modifier.fillMaxSize())
                            } else {
                                progs.forEach { p ->
                                    val x0 = xOf(p.start)
                                    val w = (xOf(p.stop) - x0).coerceAtLeast(6.dp)
                                    val isNow = p.start <= nowMs && p.stop > nowMs
                                    val frac = if (isNow) ((nowMs - p.start).toFloat() / (p.stop - p.start).coerceAtLeast(1)) else null
                                    EpgCell(
                                        p.title, null, frac,
                                        Modifier.offset(x = x0).width(w).fillMaxHeight().combinedClickable(
                                            onClick = {
                                                val ok = Reminder.schedule(sctx, c.name, p)
                                                android.widget.Toast.makeText(sctx, if (ok) "Rappel réglé : ${p.title}" else "Émission déjà commencée", android.widget.Toast.LENGTH_SHORT).show()
                                            },
                                            onLongClick = {
                                                val ok = vm.schedule(sctx, c, p)
                                                android.widget.Toast.makeText(sctx, if (ok) "Enregistrement programmé : ${p.title}" else "Émission terminée", android.widget.Toast.LENGTH_SHORT).show()
                                            },
                                        ),
                                        highlight = isNow && vm.opt("highlight") && !vm.opt("progonly", false),
                                        twoLines = vm.opt("twolines", false),
                                    )
                                }
                            }
                            // trait de l'heure actuelle
                            if (nowMs in ws..(ws + winMin * 60_000L)) Box(Modifier.offset(x = xOf(nowMs)).width(1.dp).fillMaxHeight().background(Accent.copy(alpha = 0.8f)))
                        }
                    }
                }
            }
        }
    }
}

/** les préférences restent lisibles : jamais plus transparentes que 8 % (sinon la grille du guide passe à travers le texte) */
private fun panelGlass(c: Color): Color = c.copy(alpha = (1f - GlassState.transp).coerceAtLeast(0.97f))
// couleurs inversées : en-tête clair (couleur d'accent), corps foncé
val PanelHead get() = panelGlass(Accent)
val PanelBody get() = panelGlass(Accent)

/** Une ligne d'un panneau de réglages : titre, petit texte, élément à droite (interrupteur, valeur…). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PanelItem(
    title: String,
    sub: String? = null,
    first: Boolean = false,
    onLong: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    selected: Boolean = false,
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
            .background(if (focused) AccentDark else if (selected) Color.White.copy(alpha = 0.22f) else Color.Transparent)
            .then(if (focused) Modifier.border(2.dp, Color.White, shape) else Modifier)
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
fun SettingsScreen(vm: MainVm, docked: Boolean = false, back: () -> Unit) {
    var page by rememberSaveable { mutableStateOf(vm.settingsStart ?: if (vm.sources.isEmpty()) "add" else "root") }
    LaunchedEffect(Unit) { vm.settingsStart = null }
    // le joystick reste dans le panneau : quand on change de page, le focus va dans son contenu (pas dans la colonne de gauche)
    val bodyFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(page) { if (page != "root") runCatching { bodyFocus.requestFocus() } }
    val canClose = vm.sources.isNotEmpty()
    BackHandler(true) {
        when {
            page.startsWith("edit:") -> page = "lists"
            page.startsWith("src:") || (page == "add" && canClose) -> page = "lists"
            page == "rename" -> page = "look_guide"
            page.startsWith("look_") -> page = "look"
            // Retour dans Enregistrements : ferme tout le panneau (les enregistrements restent dans la liste)
            page == "rec" -> back()
            page == "lists" || page == "groups" || page == "look" || page == "playback" || page == "network" || page == "epg" || page == "backup" || page == "parental" || page == "update" || page == "about" || page == "profiles" -> page = "root"
            canClose -> back()
        }
    }

    val title = when {
        page == "root" -> "Paramètres"
        page == "lists" -> "Listes de lecture"
        page == "add" -> "Ajouter une liste"
        page.startsWith("edit:") -> "Modifier la liste"
        page.startsWith("src:") -> vm.sources.firstOrNull { it.id == page.removePrefix("src:") }?.name?.ifBlank { "Liste" } ?: "Liste"
        page == "groups" -> "Groupes"
        page == "playback" -> "Préférences générales"
        page == "network" -> "Réseau"
        page == "profiles" -> "Profils"
        page == "rename" -> "Renommer des chaînes"
        page == "epg" -> "Guide (EPG)"
        page == "backup" -> "Sauvegarde"
        page == "look" -> "Apparence"
        page == "look_color" -> "Couleur de l'interface"
        page == "look_bg" -> "Image de fond"
        page == "look_guide" -> "Guide TV"
        page == "rec" -> "Enregistrements"
        page == "parental" -> "Contrôle parental"
        page == "update" -> "Mises à jour"
        else -> "À propos"
    }

    BoxWithConstraints(if (docked) Modifier.fillMaxWidth(0.42f).fillMaxHeight() else Modifier.fillMaxSize()) {
        val w = if (docked) maxWidth else if (maxWidth > 400.dp) 360.dp else maxWidth * 0.92f
        Row(Modifier.fillMaxSize()) {
            if (!docked) Box(Modifier.weight(1f).fillMaxHeight().background(Color.Black.copy(alpha = 0.35f)).clickable(enabled = canClose, onClick = back))
            // la flèche Haut ne sort plus du panneau de réglages (sinon le focus revient à la colonne TV / Films / Séries)
            Column(Modifier.width(w).fillMaxHeight().background(PanelBody).focusProperties { exit = { androidx.compose.ui.focus.FocusRequester.Cancel } }) {
                Row(Modifier.fillMaxWidth().background(PanelHead).padding(start = 18.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, color = Color.White, fontSize = 18.sp, modifier = Modifier.weight(1f).padding(vertical = 10.dp))
                    if (canClose) IconButton(onClick = back) { Icon(Icons.Default.Close, "Fermer", tint = Color.White) }
                }
                Column(Modifier.weight(1f).focusRequester(bodyFocus).focusable().verticalScroll(rememberScrollState()).padding(vertical = 6.dp)) {
                    when {
                        page == "root" -> {
                            PanelItem("Listes de lecture", first = true) { page = "lists" }
                            PanelItem("Groupes", "Masquer ou verrouiller des catégories") { page = "groups" }
                            PanelItem("Apparence", "${ACCENTS[vm.accentIdx].first} · texte ${TEXT_SIZES[vm.textSizeIdx].first.lowercase()}") { page = "look" }
                            PanelItem("Préférences générales") { page = "playback" }
                            PanelItem("Réseau", "User-Agent, proxy") { page = "network" }
                            PanelItem("Profils", "Plusieurs comptes (jusqu'à ${Profiles.MAX})") { page = "profiles" }
                            PanelItem("Guide (EPG)", vm.epgStatus) { page = "epg" }
                            PanelItem("Sauvegarde", "Exporter ou restaurer les données") { page = "backup" }
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
                            PanelItem("Modifier cette liste", "Nom, adresse, identifiants") { page = "edit:" + id }
                            PanelItem("Supprimer cette liste", "Efface la liste de l'appli") {
                                vm.deleteSource(id)
                                if (vm.sources.isEmpty()) page = "add" else page = "lists"
                            }
                        }
                        page == "add" -> AddSourceForm(vm, canClose) { page = "lists"; back() }
                        page.startsWith("edit:") -> {
                            val es = vm.sources.firstOrNull { it.id == page.removePrefix("edit:") }
                            if (es != null) AddSourceForm(vm, true, es) { page = "lists" }
                        }
                        page == "groups" -> GroupsPage(vm)
                        page == "look" -> {
                            PanelItem("Taille du texte", TEXT_SIZES[vm.textSizeIdx].first, first = true) { vm.changeTextSize((vm.textSizeIdx + 1) % TEXT_SIZES.size) }
                            GlassSetting(vm)
                            PanelItem("Couleur de l'interface", ACCENTS[vm.accentIdx].first) { page = "look_color" }
                            PanelItem("Image de fond", if (vm.bgPath != null) "Une image est active" else "Aucune") { page = "look_bg" }
                            PanelItem("Guide TV") { page = "look_guide" }
                        }
                        page == "look_color" -> {
                            ACCENTS.forEachIndexed { i, (n, c) ->
                                PanelItem(n, first = i == 0, trailing = { if (i == vm.accentIdx) Text("✔", color = Color.White) else Box(Modifier.size(14.dp).clip(RoundedCornerShape(7.dp)).background(c)) }) { vm.changeAccent(i) }
                            }
                        }
                        page == "look_bg" -> {
                            PanelItem("Choisir une image", first = true) { vm.pickBackground?.invoke() }
                            if (vm.bgPath != null) PanelItem("Retirer l'image") { vm.clearBackground() }
                        }
                        page == "look_guide" -> {
                            PanelItem("Afficher les numéros de chaîne", first = true, trailing = { PanelSwitch(vm.opt("numbers")) }) { vm.setOpt("numbers", !vm.opt("numbers")) }
                            PanelItem("Afficher le nom de chaîne", trailing = { PanelSwitch(vm.opt("names")) }) { vm.setOpt("names", !vm.opt("names")) }
                            PanelItem("Icône de rattrapage dans la liste", trailing = { PanelSwitch(vm.opt("replay")) }) { vm.setOpt("replay", !vm.opt("replay")) }
                            PanelItem("Titre de programme sur deux lignes", trailing = { PanelSwitch(vm.opt("twolines", false)) }) { vm.setOpt("twolines", !vm.opt("twolines", false)) }
                            PanelItem("Chaîne actuelle dans une autre couleur", trailing = { PanelSwitch(vm.opt("curcolor")) }) { vm.setOpt("curcolor", !vm.opt("curcolor")) }
                            PanelItem("Mettre en évidence le programme actuel", trailing = { PanelSwitch(vm.opt("highlight")) }) { vm.setOpt("highlight", !vm.opt("highlight")) }
                            PanelItem("Mettre en évidence uniquement la progression", trailing = { PanelSwitch(vm.opt("progonly", false)) }) { vm.setOpt("progonly", !vm.opt("progonly", false)) }
                            PanelItem("Trier les chaînes de A à Z", "Sinon : ordre de la liste", trailing = { PanelSwitch(vm.opt("sortaz", false)) }) { vm.setOpt("sortaz", !vm.opt("sortaz", false)) }
                            PanelItem("Renommer des chaînes") { page = "rename" }
                        }
                        page == "playback" -> {
                            PanelItem("Reprendre la dernière chaîne", "Au démarrage de l'appli", first = true, trailing = { PanelSwitch(vm.autoResume) }) { vm.changeAutoResume(!vm.autoResume) }
                            PanelItem("Aperçu avant le plein écran", "Un clic = aperçu, deux clics = plein écran", trailing = { PanelSwitch(vm.previewMode) }) { vm.changePreviewMode(!vm.previewMode) }
                            PanelItem("Image dans l'image à l'accueil", "Touche Accueil pendant une chaîne", trailing = { PanelSwitch(vm.pipOnHome) }) { vm.changePipOnHome(!vm.pipOnHome) }
                            PanelItem("Confirmer la sortie", "Retour deux fois pour quitter", trailing = { PanelSwitch(vm.doubleBack) }) { vm.changeDoubleBack(!vm.doubleBack) }
                            PanelItem("Démarrer au démarrage d'Android", "Ouvre l'appli quand l'appareil démarre", trailing = { PanelSwitch(vm.bootStart) }) { vm.changeBootStart(!vm.bootStart) }
                        }
                        page == "network" -> NetworkPage(vm)
                        page == "profiles" -> ProfilesPage(vm)
                        page == "rename" -> RenamePage(vm)
                        page == "epg" -> EpgPage(vm)
                        page == "backup" -> BackupPage(vm)
                        page == "rec" -> Column(Modifier.padding(horizontal = 12.dp)) { RecordingsSection(vm) }
                        page == "parental" -> Column(Modifier.padding(horizontal = 12.dp)) { ParentalSection(vm) }
                        page == "update" -> Column(Modifier.padding(horizontal = 12.dp)) { UpdateSection(vm) }
                        else -> {
                            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Image(painterResource(R.mipmap.ic_launcher), null, Modifier.size(64.dp))
                                Spacer(Modifier.size(10.dp))
                                Text("theIPTVapp pour Android", color = Color.White, fontSize = 18.sp)
                                Text("Créé par Benoît Dumont à l'aide de l'IA", color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
                                Text("Projet gratuit · lecteur IPTV", color = Color.Gray, fontSize = 13.sp)
                                Text("Version ${BuildConfig.VERSION_NAME}", color = Color.Gray, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RenamePage(vm: MainVm) {
    var target by remember { mutableStateOf<Channel?>(null) }
    var text by remember { mutableStateOf("") }
    Column(Modifier.padding(horizontal = 6.dp)) {
        PanelLabel("Touchez une chaîne pour changer son nom affiché. Nom vide = nom d'origine.")
        vm.live.items.forEachIndexed { i, c ->
            PanelItem(vm.displayName(c), if (vm.displayName(c) != c.name) "Original : ${c.name}" else null, first = i == 0) {
                target = c
                text = vm.displayName(c)
            }
        }
    }
    target?.let { c ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { target = null },
            title = { Text("Nom de la chaîne") },
            text = { OutlinedTextField(text, { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { Button(onClick = { vm.rename(c, text); target = null }) { Text("OK") } },
            dismissButton = { OutlinedButton(onClick = { vm.rename(c, ""); target = null }) { Text("Original") } },
        )
    }
}

/**
 * Champ de saisie pour la TV : on voit le champ (titre + texte actuel). Appui OK/Entrée : une fenêtre
 * s'ouvre avec le nom du réglage en titre et le texte écrit en gros, pour savoir exactement où l'on écrit.
 */
@Composable
fun TvField(
    value: String,
    onValue: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    secret: Boolean = false,
    keyboard: KeyboardType = KeyboardType.Text,
) {
    var open by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    val shown = if (secret) "•".repeat(value.length) else value
    Column(modifier.fillMaxWidth()) {
        Text(label, color = Color(0xFFB8C4EE), fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color(0x22FFFFFF),
            border = androidx.compose.foundation.BorderStroke(if (focused) 3.dp else 1.dp, if (focused) Color.White else Color(0x66FFFFFF)),
            modifier = Modifier.fillMaxWidth()
                .onFocusChanged { focused = it.isFocused }
                .clickable { draft = value; open = true },
        ) {
            Text(
                shown.ifEmpty { "Appuie sur OK pour écrire" },
                color = if (shown.isEmpty()) Color.Gray else Color.White,
                fontSize = 16.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            )
        }
    }
    if (open) {
        val focus = remember { androidx.compose.ui.focus.FocusRequester() }
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(label) },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = if (keyboard == KeyboardType.NumberPassword) it.filter(Char::isDigit) else it },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 22.sp),
                    visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(keyboardType = keyboard),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            },
            confirmButton = { Button(onClick = { onValue(draft.trim()); open = false }) { Text("OK") } },
            dismissButton = { OutlinedButton(onClick = { open = false }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun ProfilesPage(vm: MainVm) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var tick by remember { mutableStateOf(0) }
    val list = remember(tick) { Profiles.list(ctx) }
    val active = remember(tick) { Profiles.activeId(ctx) }
    var newName by rememberSaveable { mutableStateOf("") }
    var editId by rememberSaveable { mutableStateOf("") }
    var editName by rememberSaveable { mutableStateOf("") }
    var msg by rememberSaveable { mutableStateOf("") }
    Column(Modifier.padding(horizontal = 6.dp)) {
        PanelLabel("Toucher un profil : on y passe (l'appli redémarre). Appui long : modifier le nom ou supprimer.")
        list.forEachIndexed { i, p ->
            PanelItem(
                (if (p.id == active) "✔ " else "") + p.name,
                if (p.id == active) "Profil actif" else "Toucher : passer à ce profil",
                first = i == 0,
                onLong = { editId = p.id; editName = p.name; msg = "" },
                trailing = { OutlinedButton(onClick = { editId = p.id; editName = p.name; msg = "" }) { Text("Modifier", fontSize = 13.sp) } },
            ) { if (p.id != active) Profiles.switchTo(ctx, p.id) }
        }
        if (editId.isNotEmpty()) {
            PanelLabel("Modifier le profil")
            TvField(editName, { editName = it }, "Nom du profil")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = editName.isNotBlank(),
                    onClick = { Profiles.rename(ctx, editId, editName); editId = ""; tick++; msg = "Nom enregistré." },
                ) { Text("Enregistrer") }
                if (editId != active && editId != "p0") OutlinedButton(onClick = {
                    val n = list.firstOrNull { it.id == editId }?.name ?: ""
                    msg = if (Profiles.delete(ctx, editId)) "Profil « $n » supprimé." else "Suppression impossible."
                    editId = ""; tick++
                }) { Text("Supprimer") }
                OutlinedButton(onClick = { editId = "" }) { Text("Annuler") }
            }
        }
        PanelLabel("Ajouter un profil (${list.size}/${Profiles.MAX})")
        TvField(newName, { newName = it }, "Nom du nouveau profil")
        Button(
            enabled = newName.isNotBlank() && list.size < Profiles.MAX,
            onClick = { if (Profiles.add(ctx, newName)) { newName = ""; tick++; msg = "Profil ajouté. Touche-le pour y passer." } },
        ) { Text("Ajouter") }
        if (msg.isNotBlank()) Text(msg, color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(18.dp))
    }
}

@Composable
private fun NetworkPage(vm: MainVm) {
    var ua by rememberSaveable { mutableStateOf(vm.userAgent) }
    var px by rememberSaveable { mutableStateOf(vm.proxy) }
    Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Spacer(Modifier.size(6.dp))
        TvField(ua, { ua = it }, "Préférences › Réseau › User-Agent (vide = par défaut)")
        TvField(px, { px = it }, "Préférences › Réseau › Proxy hôte:port (vide = aucun)", keyboard = KeyboardType.Uri)
        Text("Sous-titres en ligne : mets TA clé OpenSubtitles (gratuite, à créer sur opensubtitles.com). Elle reste sur cet appareil.", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
        var osk by rememberSaveable { mutableStateOf(vm.osKey()) }
        TvField(osk, { osk = it; vm.saveOsKey(it) }, "Clé API OpenSubtitles (ta clé personnelle)", secret = true)
        Button(onClick = { vm.saveNetwork(ua, px) }) { Text("Enregistrer") }
        if (vm.applyNetworkNote.isNotBlank()) Text(vm.applyNetworkNote, color = Color.White, fontSize = 12.sp)
    }
}

@Composable
private fun StepperRow(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)) {
        Text(label, color = Color.White, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = onMinus) { Text("−") }
        Text(value, color = Color.White, modifier = Modifier.padding(horizontal = 12.dp))
        OutlinedButton(onClick = onPlus) { Text("+") }
    }
}

@Composable
private fun EpgPage(vm: MainVm) {
    Column(Modifier.padding(horizontal = 6.dp)) {
        PanelLabel("Statut")
        Text(vm.epgStatus, color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 18.dp))
        PanelLabel("Conservation")
        StepperRow("Garder le guide", "${vm.epgDays} j", { vm.changeEpgDays(vm.epgDays - 1) }, { vm.changeEpgDays(vm.epgDays + 1) })
        PanelItem("Stocker les descriptions des émissions", trailing = { PanelSwitch(vm.epgDesc) }) { vm.changeEpgDesc(!vm.epgDesc) }
        PanelLabel("Mise à jour")
        StepperRow(
            "Intervalle (heures, 0 = jamais)",
            if (vm.epgInterval == 0) "aucun" else "${vm.epgInterval} h",
            { vm.changeEpgInterval(vm.epgInterval - 1) }, { vm.changeEpgInterval(vm.epgInterval + 1) },
        )
        PanelItem("Mise à jour au démarrage de l'appli", trailing = { PanelSwitch(vm.epgOnStart) }) { vm.changeEpgOnStart(!vm.epgOnStart) }
        PanelItem("Mise à jour au changement de liste", trailing = { PanelSwitch(vm.epgOnList) }) { vm.changeEpgOnList(!vm.epgOnList) }
        PanelItem(if (vm.epgBusy) "Mise à jour en cours…" else "Mettre à jour le guide") { vm.refreshGuide() }
        PanelItem("Effacer le guide", "Supprime le guide chargé") { vm.clearGuide() }
    }
}

@Composable
private fun BackupPage(vm: MainVm) {
    Column(Modifier.padding(horizontal = 6.dp)) {
        PanelItem("Sauvegarder les données", "Listes, favoris, réglages", first = true) { vm.createBackup?.invoke() }
        PanelItem("Restaurer les données", "Choisir un fichier de sauvegarde") { vm.openBackup?.invoke() }
        if (vm.backupMessage.isNotBlank()) Text(vm.backupMessage, color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(18.dp))
    }
}

@Composable
private fun AddSourceForm(vm: MainVm, canCancel: Boolean, edit: Source? = null, done: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(edit?.name ?: "") }
    var type by rememberSaveable { mutableStateOf(edit?.type ?: SourceType.M3U) }
    var url by rememberSaveable { mutableStateOf(edit?.url ?: "") }
    var host by rememberSaveable { mutableStateOf(edit?.host ?: "") }
    var user by rememberSaveable { mutableStateOf(edit?.user ?: "") }
    var pass by rememberSaveable { mutableStateOf(edit?.pass ?: "") }
    PanelLabel("Type de liste")
    PanelItem(if (type == SourceType.M3U) "✔ Liste M3U" else "Liste M3U", first = true, selected = type == SourceType.M3U, trailing = { PanelSwitch(type == SourceType.M3U) }) { type = SourceType.M3U }
    PanelItem(if (type == SourceType.XTREAM) "✔ Xtream Codes" else "Xtream Codes", selected = type == SourceType.XTREAM, trailing = { PanelSwitch(type == SourceType.XTREAM) }) { type = SourceType.XTREAM }
    Column(Modifier.padding(horizontal = 12.dp)) {
        Spacer(Modifier.size(6.dp))
        TvField(name, { name = it }, "Nom de la liste")
        if (type == SourceType.M3U) {
            TvField(url, { url = it }, "Adresse de la liste (http…)", keyboard = KeyboardType.Uri)
        } else {
            TvField(host, { host = it }, "Serveur (http://exemple.com:8080)", keyboard = KeyboardType.Uri)
            TvField(user, { user = it }, "Identifiant")
            TvField(pass, { pass = it }, "Mot de passe", secret = true)
        }
        Spacer(Modifier.size(10.dp))
        val ok = if (type == SourceType.M3U) url.isNotBlank() else host.isNotBlank() && user.isNotBlank() && pass.isNotBlank()
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = ok,
                onClick = {
                    val s = Source(edit?.id ?: vm.newId(), name.ifBlank { if (type == SourceType.M3U) "Ma liste" else "Mon Xtream" }, type, url.trim(), host.trim(), user.trim(), pass.trim())
                    if (edit != null) vm.updateSource(s) else vm.addSource(s)
                    if (edit == null) { name = ""; url = ""; host = ""; user = ""; pass = "" }
                    done()
                },
            ) { Text(if (edit != null) "Enregistrer" else "Ajouter") }
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
        TvField(kw, { kw = it }, "Mots à verrouiller (séparés par des virgules)")
        TvField(pin, { pin = it.filter(Char::isDigit) }, "Nouveau code (4 chiffres ou plus)", secret = true, keyboard = KeyboardType.NumberPassword)
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

/** Programme un enregistrement à la main : chaîne (nom), jour, heure de début et de fin. */
@Composable
private fun ScheduleForm(vm: MainVm) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var chName by rememberSaveable { mutableStateOf("") }
    var day by rememberSaveable { mutableStateOf("0") }
    var from by rememberSaveable { mutableStateOf("") }
    var to by rememberSaveable { mutableStateOf("") }
    var msg by rememberSaveable { mutableStateOf("") }
    var asDvr by rememberSaveable { mutableStateOf(false) }
    val match = if (chName.isBlank()) null else vm.live.items.firstOrNull { it.name.contains(chName, ignoreCase = true) || vm.displayName(it).contains(chName, ignoreCase = true) }
    fun parseHm(s: String): Pair<Int, Int>? {
        val m = Regex("^(\\d{1,2}):(\\d{2})$").find(s.trim()) ?: return null
        val h = m.groupValues[1].toInt(); val mi = m.groupValues[2].toInt()
        return if (h in 0..23 && mi in 0..59) h to mi else null
    }
    val a = parseHm(from)
    val b = parseHm(to)
    Spacer(Modifier.size(12.dp))
    Text("Programmer un enregistrement", color = Color.White, fontSize = 15.sp)
    TvField(chName, { chName = it; msg = "" }, "Nom de la chaîne (une partie du nom suffit)")
    if (chName.isNotBlank()) Text(
        if (match != null) "Chaîne trouvée : ${vm.displayName(match)}" else "Aucune chaîne ne correspond",
        color = if (match != null) Color(0xFFB8F5C8) else Color(0xFFFF8A8A), fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp),
    )
    TvField(day, { day = it.filter(Char::isDigit).take(1) }, "Jour : 0 = aujourd'hui, 1 = demain")
    TvField(from, { from = it; msg = "" }, "Heure de début (hh:mm, ex. 20:00)")
    TvField(to, { to = it; msg = "" }, "Heure de fin (hh:mm, ex. 22:00)")
    OutlinedButton(onClick = { asDvr = !asDvr; msg = "" }) {
        Text(if (asDvr) "Mode : DVR (garder dans le DVR, 2 h max)" else "Mode : fichier (enregistrer l'émission)")
    }
    Button(
        enabled = match != null && a != null && b != null,
        onClick = {
            val (ah, am) = a!!
            val (bh, bm) = b!!
            val cal = java.util.Calendar.getInstance()
            cal.add(java.util.Calendar.DAY_OF_YEAR, day.toIntOrNull() ?: 0)
            cal.set(java.util.Calendar.HOUR_OF_DAY, ah)
            cal.set(java.util.Calendar.MINUTE, am)
            cal.set(java.util.Calendar.SECOND, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            val start = cal.timeInMillis
            var diff = (bh * 60 + bm) - (ah * 60 + am)
            if (diff <= 0) diff += 24 * 60 // fin après minuit
            val stop = start + diff * 60_000L
            msg = if (stop <= System.currentTimeMillis()) "Cette heure est déjà passée."
            else if (asDvr && diff > 120) "Le DVR garde 2 h : pour plus long, choisis le mode fichier."
            else if (vm.scheduleManual(ctx, match!!, start, stop, asDvr)) {
                val txt = "Programmé : ${vm.displayName(match)} de ${"%02d:%02d".format(ah, am)} à ${"%02d:%02d".format(bh, bm)}"
                chName = ""; from = ""; to = ""
                txt
            } else "Impossible de programmer."
        },
    ) { Text("Programmer") }
    // enregistrer tout de suite, pendant 30 min, 1 h ou 2 h (comme la version PC)
    Text("Enregistrer maintenant :", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(30, 60, 120).forEach { mins ->
            OutlinedButton(enabled = match != null, onClick = {
                val now = System.currentTimeMillis()
                val label = if (mins < 60) "$mins min" else "${mins / 60} h"
                msg = if (vm.scheduleManual(ctx, match!!, now, now + mins * 60_000L, false)) "Enregistrement lancé : ${vm.displayName(match)} pendant $label."
                else "Impossible de lancer l'enregistrement."
            }) { Text(if (mins < 60) "$mins min" else "${mins / 60} h") }
        }
    }
    if (msg.isNotBlank()) Text(msg, color = Color.White, fontSize = 12.sp)
}

@Composable
private fun RecordingsSection(vm: MainVm) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    // où sauvegarder films enregistrés, émissions et DVR (clé USB possible)
    val storageOpts = remember { StorageChoice.options(ctx) }
    var storagePath by remember { mutableStateOf(StorageChoice.current(ctx).absolutePath) }
    Text("Où sauvegarder (enregistrements et DVR) :", color = Color.White, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp))
    storageOpts.forEach { o ->
        FocusRow(selected = o.root.absolutePath == storagePath, onClick = { StorageChoice.set(ctx, o.root); storagePath = o.root.absolutePath }) {
            Column(Modifier.weight(1f)) {
                Text(o.label + if (o.root.absolutePath == storagePath) "  (choisi)" else "", color = Color.White, maxLines = 1)
                Text(o.root.absolutePath, color = Color.Gray, fontSize = 11.sp, maxLines = 1)
            }
        }
    }
    Text("Les fichiers déjà enregistrés restent dans l'ancien dossier.", color = Color.Gray, fontSize = 12.sp)
    val files = remember(vm.recVersion, vm.recording) { vm.recordings(ctx) }
    vm.recording?.let { Text("● En cours : ${it.name} (${it.file.length() / 1_000_000} Mo)", color = Color(0xFFFF6B6B), fontSize = 12.sp) }
    // DVR : ce qui est gardé pour chaque chaîne en direct (2 h / 6 Go max)
    var dvrTick by remember { mutableStateOf(0) }
    val dvrInfos = remember(dvrTick, vm.recVersion, vm.recording) { Dvr.infos(ctx) }
    Text("DVR (chaînes en direct)", color = Color.White, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp))
    if (dvrInfos.isEmpty()) Text("Rien d'enregistré. Le DVR se remplit quand une chaîne en direct joue.", color = Color.Gray, fontSize = 12.sp)
    dvrInfos.forEach { d ->
        FocusRow(selected = false, onClick = { Dvr.clearDir(ctx, d.dirName); dvrTick++ }) {
            Column(Modifier.weight(1f)) {
                Text(d.name + if (d.recording) "  (en cours)" else "", color = Color.White, maxLines = 1)
                Text("${d.totalMs / 60_000} min gardées · ${d.bytes / 1_000_000} Mo", color = Color.Gray, fontSize = 12.sp)
            }
            Text("Effacer", color = Color(0xFFFF8A8A), fontSize = 12.sp)
        }
    }
    val sched = vm.scheduled(ctx)
    if (sched.isNotEmpty()) {
        Text("Programmés", color = Color.White, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp))
        val fmt = java.text.SimpleDateFormat("EEE d MMM HH:mm", java.util.Locale.CANADA_FRENCH)
        sched.forEach { s ->
            FocusRow(selected = false, onClick = { vm.cancelSched(ctx, s.id) }) {
                Text("${s.title.ifEmpty { s.chName }} · ${s.chName} · ${fmt.format(java.util.Date(s.start))}", color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 2)
                Text("Annuler", color = Color(0xFFFF8A8A), fontSize = 12.sp)
            }
        }
    }
    ScheduleForm(vm)
    if (vm.recMessage.isNotBlank()) Text(vm.recMessage, color = Color(0xFFFF8A8A), fontSize = 12.sp)
    if (files.isEmpty()) Text("Aucun enregistrement. Dans le lecteur, touche le bouton rouge ⏺ (chaîne, film ou épisode).", color = Color.Gray, fontSize = 12.sp)
    files.forEach { f ->
        FocusRow(selected = false, onClick = {
            val ch = Channel(id = "r" + f.name, name = f.name.substringBeforeLast('.'), url = f.toURI().toString(), kind = "movie")
            vm.play(listOf(ch), ch)
        }) {
            Column(Modifier.weight(1f)) {
                Text(f.name.substringBeforeLast('.'), color = Color.White, maxLines = 1)
                Text("${f.length() / 1_000_000} Mo", color = Color.Gray, fontSize = 12.sp)
            }
            IconButton(onClick = { vm.deleteRecording(f) }) { Icon(Icons.Default.Delete, "Supprimer", tint = Color(0xFFFF8A8A)) }
        }
    }
}
