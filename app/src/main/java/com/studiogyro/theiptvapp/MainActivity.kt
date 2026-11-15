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

    private val openBackup = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { vm.readBackup(this, it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vm.createBackup = { createBackup.launch("theiptvapp-sauvegarde.json") }
        vm.openBackup = { openBackup.launch(arrayOf("application/json", "text/plain", "*/*")) }
        setContent { AppRoot(vm) }
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
