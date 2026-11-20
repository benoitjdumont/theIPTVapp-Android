package com.studiogyro.theiptvapp

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels

class MainActivity : ComponentActivity() {
    private val vm: MainVm by viewModels()

    private val createBackup = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { vm.writeBackup(this, it) } }

    private val pickBackground = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { vm.setBackground(this, it) } }

    private val openBackup = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { vm.readBackup(this, it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Android 13+ : demander la permission des notifications (rappels d'émissions), une fois, à l'ouverture
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        // plein écran : l'interface touche les bords ; la barre du système se montre seulement par un glissement
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.apply { layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS }
        }
        vm.createBackup = { createBackup.launch("theiptvapp-sauvegarde.json") }
        vm.openBackup = { openBackup.launch(arrayOf("application/json", "text/plain", "*/*")) }
        vm.pickBackground = { pickBackground.launch("image/*") }
        setContent { AppRoot(vm) }
    }

    /**
     * Manette Google TV : le bouton A valide, B revient en arrière, L1/R1 changent de chaîne (page précédente/suivante).
     */
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        val code = when (event.keyCode) {
            android.view.KeyEvent.KEYCODE_BUTTON_A -> android.view.KeyEvent.KEYCODE_DPAD_CENTER
            android.view.KeyEvent.KEYCODE_BUTTON_B -> android.view.KeyEvent.KEYCODE_BACK
            android.view.KeyEvent.KEYCODE_BUTTON_L1 -> android.view.KeyEvent.KEYCODE_PAGE_UP
            android.view.KeyEvent.KEYCODE_BUTTON_R1 -> android.view.KeyEvent.KEYCODE_PAGE_DOWN
            else -> return super.dispatchKeyEvent(event)
        }
        val mapped = android.view.KeyEvent(
            event.downTime, event.eventTime, event.action, code, event.repeatCount,
            event.metaState, event.deviceId, event.scanCode, event.flags, event.source,
        )
        return super.dispatchKeyEvent(mapped)
    }

    /** Leaving the app while a channel plays shrinks it into a small floating window (si l'option est active). */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (vm.current != null && vm.pipOnHome) enterPip()
    }

    fun enterPip() {
        if (Build.VERSION.SDK_INT >= 26) {
            runCatching {
                enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build())
            }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        vm.inPip = isInPictureInPictureMode
    }
}
