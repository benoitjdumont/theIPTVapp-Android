package com.studiogyro.theiptvapp

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay

/**
 * Animation d'ouverture : le logo apparaît, puis l'écran s'efface une fois la liste chargée.
 * Reste au moins 1,6 s, au plus 4 s, pendant que la liste des chaînes se charge.
 */
@Composable
fun SplashOverlay(vm: MainVm) {
    val t0 = remember { System.currentTimeMillis() }
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(100)
            val elapsed = System.currentTimeMillis() - t0
            if (elapsed >= 4000 || (elapsed >= 1600 && !vm.live.loading)) break
        }
        visible = false
    }
    val fade by animateFloatAsState(if (visible) 1f else 0f, tween(500), label = "splashFade")
    if (fade > 0f) {
        val intro = remember { Animatable(0f) }
        LaunchedEffect(Unit) { intro.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false, dismissOnClickOutside = false),
        ) {
            Box(
                Modifier.fillMaxSize().alpha(fade).background(Color(0xFF0B0F1A)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Image(
                        painter = painterResource(R.mipmap.ic_launcher),
                        contentDescription = null,
                        modifier = Modifier.size(150.dp).scale(0.75f + 0.25f * intro.value).alpha(intro.value),
                    )
                    Box(Modifier.height(18.dp))
                    Text(
                        "theIPTVapp",
                        color = Color.White.copy(alpha = intro.value),
                        fontSize = 26.sp,
                    )
                }
            }
        }
    }
}
