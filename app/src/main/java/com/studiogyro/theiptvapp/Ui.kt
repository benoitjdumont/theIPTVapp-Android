package com.studiogyro.theiptvapp

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
val Accent = Color(0xFF2F7BFF)

@Composable
fun AppRoot(vm: MainVm) {
    val scheme = darkColorScheme(
        primary = Accent,
        background = Bg,
        surface = Panel,
        onSurface = Color.White,
        onBackground = Color.White,
    )
    MaterialTheme(colorScheme = scheme) {
        Surface(Modifier.fillMaxSize(), color = Bg) {
            var screen by rememberSaveable { mutableStateOf(if (vm.sources.isEmpty()) "settings" else "home") }
            if (vm.current != null) {
                PlayerScreen(vm)
            } else if (vm.seriesOpen != null) {
                Box(Modifier.fillMaxSize().safeDrawingPadding()) { SeriesScreen(vm) }
            } else {
                // keep clear of the status bar (battery, clock) and the navigation bar
                Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                    if (screen == "settings") {
                        BackHandler(vm.sources.isNotEmpty()) { screen = "home" }
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
@Composable
fun FocusRow(selected: Boolean, onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (focused) Accent.copy(alpha = 0.35f) else if (selected) Accent.copy(alpha = 0.18f) else Color.Transparent)
            .then(if (focused) Modifier.border(2.dp, Accent, shape) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
fun HomeScreen(vm: MainVm, openSettings: () -> Unit) {
    val sec = vm.section()
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.mipmap.ic_launcher), null, Modifier.size(36.dp))
            Spacer(Modifier.width(8.dp))
            Text("theIPTVapp", fontSize = 20.sp, color = Color.White, modifier = Modifier.weight(1f))
            IconButton(onClick = { vm.reload() }) { Icon(Icons.Default.Refresh, "Actualiser", tint = Color.White) }
            IconButton(onClick = openSettings) { Icon(Icons.Default.Settings, "Sources", tint = Color.White) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 6.dp)) {
            listOf("En direct", "Films", "Séries").forEachIndexed { i, label ->
                FilterChip(selected = vm.tab == i, onClick = { vm.selectTab(i) }, label = { Text(label) })
            }
        }
        OutlinedTextField(
            value = vm.query,
            onValueChange = { vm.query = it },
            singleLine = true,
            placeholder = { Text("Rechercher") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            modifier = Modifier.fillMaxWidth(),
        )
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
                if (maxWidth >= 700.dp) {
                    Row(Modifier.fillMaxSize()) {
                        LazyColumn(Modifier.width(200.dp).fillMaxSize()) {
                            items(topEntries(sec, vm.tab == 0), key = { it.first ?: "\u0001all" }) { (g, label) ->
                                FocusRow(selected = sec.top == g, onClick = { sec.pickTop(g) }) {
                                    Text(label, color = Color.White, maxLines = 1)
                                }
                            }
                        }
                        if (subs.isNotEmpty()) {
                            Spacer(Modifier.width(6.dp))
                            LazyColumn(Modifier.width(220.dp).fillMaxSize()) {
                                item(key = "\u0001subAll") {
                                    FocusRow(selected = sec.sub == null, onClick = { sec.pickSub(null) }) {
                                        Text("Tout", color = Color.White, maxLines = 1)
                                    }
                                }
                                items(subs, key = { it.first }) { (full, label) ->
                                    FocusRow(selected = sec.sub == full, onClick = { sec.pickSub(full) }) {
                                        Text(label, color = Color.White, maxLines = 1)
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
                                FilterChip(selected = sec.top == g, onClick = { sec.pickTop(g) }, label = { Text(label, maxLines = 1) })
                            }
                        }
                        if (subs.isNotEmpty()) {
                            Spacer(Modifier.size(4.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                item(key = "\u0001subAll") {
                                    FilterChip(selected = sec.sub == null, onClick = { sec.pickSub(null) }, label = { Text("Tout") })
                                }
                                items(subs, key = { it.first }) { (full, label) ->
                                    FilterChip(selected = sec.sub == full, onClick = { sec.pickSub(full) }, label = { Text(label, maxLines = 1) })
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

/** Poster wall for movies and series. */
@Composable
private fun PosterGrid(vm: MainVm, list: List<Channel>, modifier: Modifier) {
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
                    .onFocusChanged { focused = it.isFocused }
                    .clickable { if (c.kind == "series") vm.openSeries(c) else vm.play(listOf(c), c) }
                    .padding(6.dp),
            ) {
                Poster(c.logo, Modifier.fillMaxWidth().aspectRatio(2f / 3f))
                Text(c.name, color = Color.White, fontSize = 12.sp, maxLines = 2, modifier = Modifier.padding(top = 4.dp))
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

/** Episode list of a series: seasons as chips, episodes below. */
@Composable
fun SeriesScreen(vm: MainVm) {
    val c = vm.seriesOpen ?: return
    val data = vm.seriesData
    BackHandler(true) { vm.closeSeries() }
    var season by rememberSaveable(c.id) { mutableStateOf(-1) }
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.closeSeries() }) { Icon(Icons.Default.ArrowBack, "Retour", tint = Color.White) }
            Text(c.name, fontSize = 20.sp, color = Color.White, maxLines = 2, modifier = Modifier.weight(1f))
        }
        if (data == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }
        Row(Modifier.padding(vertical = 8.dp)) {
            Poster(c.logo, Modifier.width(90.dp).aspectRatio(2f / 3f))
            Spacer(Modifier.width(10.dp))
            Text(data.plot.ifBlank { c.plot }.ifBlank { "Pas de résumé." }, color = Color(0xFFB8C4DA), fontSize = 13.sp, maxLines = 6)
        }
        if (data.seasons.isEmpty()) {
            Message("Aucun épisode trouvé.")
            return@Column
        }
        val keys = data.seasons.keys.toList()
        val cur = if (season in keys) season else keys.first()
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(keys, key = { it }) { k ->
                FilterChip(selected = k == cur, onClick = { season = k }, label = { Text("Saison $k") })
            }
        }
        Spacer(Modifier.size(6.dp))
        val eps = data.seasons[cur].orEmpty()
        LazyColumn(Modifier.weight(1f)) {
            items(eps, key = { it.id }) { e ->
                FocusRow(selected = false, onClick = { vm.play(eps, e) }) {
                    Text(e.name, color = Color.White, maxLines = 2, modifier = Modifier.weight(1f))
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
private fun ChannelList(vm: MainVm, list: List<Channel>, modifier: Modifier) {
    if (list.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) { Text("Aucune chaîne.", color = Color.Gray) }
        return
    }
    LazyColumn(modifier) {
        items(list, key = { it.id }) { c ->
            FocusRow(selected = false, onClick = { vm.play(list, c) }) {
                if (c.logo.isNotBlank()) {
                    AsyncImage(
                        model = c.logo,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)),
                    )
                } else {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)).background(Panel))
                }
                Spacer(Modifier.width(10.dp))
                LaunchedEffect(c.id) { vm.loadEpg(c) }
                val e = vm.epg[c.id]
                Column(Modifier.weight(1f)) {
                    Text(c.name, color = Color.White, maxLines = 1)
                    if (e?.now != null) {
                        Text("${e.now.title}  ${hm(e.now.start)}–${hm(e.now.stop)}", color = Color(0xFF9DB4D8), fontSize = 12.sp, maxLines = 1)
                        if (e.next != null) Text("Ensuite : ${e.next.title}", color = Color.Gray, fontSize = 11.sp, maxLines = 1)
                    }
                }
                IconButton(onClick = { vm.toggleFav(c) }) {
                    Icon(
                        if (vm.isFav(c)) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        "Favori",
                        tint = if (vm.isFav(c)) Accent else Color.Gray,
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(vm: MainVm, back: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf(SourceType.M3U) }
    var url by rememberSaveable { mutableStateOf("") }
    var host by rememberSaveable { mutableStateOf("") }
    var user by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (vm.sources.isNotEmpty()) {
                IconButton(onClick = back) { Icon(Icons.Default.ArrowBack, "Retour", tint = Color.White) }
            }
            Text("Sources", fontSize = 22.sp, color = Color.White)
        }
        Spacer(Modifier.size(8.dp))
        vm.sources.forEach { s ->
            FocusRow(selected = s.id == vm.activeId, onClick = { vm.select(s.id); back() }) {
                Column(Modifier.weight(1f)) {
                    Text(s.name.ifBlank { "Source" }, color = Color.White)
                    Text(if (s.type == SourceType.M3U) "M3U" else "Xtream", color = Color.Gray, fontSize = 12.sp)
                }
                IconButton(onClick = { vm.deleteSource(s.id) }) { Icon(Icons.Default.Delete, "Supprimer", tint = Color(0xFFFF8A8A)) }
            }
        }

        Spacer(Modifier.size(16.dp))
        Text("Ajouter une source", fontSize = 18.sp, color = Color.White)
        Spacer(Modifier.size(8.dp))
        OutlinedTextField(name, { name = it }, label = { Text("Nom") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            FilterChip(selected = type == SourceType.M3U, onClick = { type = SourceType.M3U }, label = { Text("Liste M3U") })
            FilterChip(selected = type == SourceType.XTREAM, onClick = { type = SourceType.XTREAM }, label = { Text("Xtream Codes") })
        }
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
        Spacer(Modifier.size(12.dp))
        val ok = if (type == SourceType.M3U) url.isNotBlank() else host.isNotBlank() && user.isNotBlank() && pass.isNotBlank()
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = ok,
                onClick = {
                    vm.addSource(
                        Source(vm.newId(), name.ifBlank { if (type == SourceType.M3U) "Ma liste" else "Mon Xtream" }, type, url.trim(), host.trim(), user.trim(), pass),
                    )
                    name = ""; url = ""; host = ""; user = ""; pass = ""
                    back()
                },
            ) { Text("Ajouter") }
            if (vm.sources.isNotEmpty()) OutlinedButton(onClick = back) { Text("Annuler") }
        }
    }
}

fun hm(ms: Long): String = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(ms))
