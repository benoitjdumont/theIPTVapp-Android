package com.studiogyro.theiptvapp

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Animation d'ouverture, dessinée dans la même composition que l'appli (pas de fenêtre séparée),
 * pour qu'elle recouvre l'écran dès la première image. Le logo grossit avec un léger rebond,
 * le nom arrive juste après, puis tout s'efface quand la liste est chargée (1,6 s à 2,5 s).
 */
@Composable
fun SplashOverlay(vm: MainVm) {
    val t0 = remember { System.currentTimeMillis() }
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(100)
            val elapsed = System.currentTimeMillis() - t0
            if (elapsed >= 2500 || (elapsed >= 1600 && !vm.live.loading)) break
        }
        visible = false
    }
    val fade by animateFloatAsState(if (visible) 1f else 0f, tween(550, easing = FastOutSlowInEasing), label = "splashFade")
    val logo = remember { Animatable(0f) }
    val title = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        logo.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow))
    }
    LaunchedEffect(Unit) {
        delay(250)
        title.animateTo(1f, tween(500, easing = FastOutSlowInEasing))
    }
    if (fade > 0f) {
        Box(
            Modifier.fillMaxSize().graphicsLayer { alpha = fade }.background(Color(0xFF0B1220)),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Image(
                    painter = painterResource(R.mipmap.ic_launcher),
                    contentDescription = null,
                    modifier = Modifier
                        .size(150.dp)
                        .graphicsLayer {
                            val s = 0.55f + 0.45f * logo.value
                            scaleX = s
                            scaleY = s
                            alpha = logo.value.coerceIn(0f, 1f)
                        },
                )
                Box(Modifier.height(18.dp))
                Text(
                    "theIPTVapp",
                    color = Color.White,
                    fontSize = 26.sp,
                    modifier = Modifier.graphicsLayer {
                        alpha = title.value.coerceIn(0f, 1f)
                        translationY = (1f - title.value) * 8.dp.toPx()
                    },
                )
            }
        }
    }
}
