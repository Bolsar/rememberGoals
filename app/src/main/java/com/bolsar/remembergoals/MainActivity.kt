package com.bolsar.remembergoals

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.bolsar.remembergoals.ui.theme.RememberGoalsTheme

class MainActivity : ComponentActivity() {
    /** (goal id, preselect "Achieved") from a notification; GoalsScreen clears it once the sheet is open. */
    private var resolve by mutableStateOf<Pair<Long, Boolean>?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // A recreation (rotation) or relaunch from Recents carries the old intent again: don't reopen the sheet.
        if (savedInstanceState == null && intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == 0) read(intent)
        // ponytail: asked on launch; the system stops showing it after two denials. The app works without it.
        if (savedInstanceState == null && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) registerForActivityResult(ActivityResultContracts.RequestPermission()) {}.launch(Manifest.permission.POST_NOTIFICATIONS)
        setContent { RememberGoalsTheme { GoalsScreen(resolve = resolve, onResolveHandled = { resolve = null }) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        read(intent)
    }

    private fun read(intent: Intent) {
        val id = intent.getLongExtra(EXTRA_GOAL_ID, -1)
        if (id > 0) resolve = id to intent.getBooleanExtra(EXTRA_ACHIEVED, true)
    }
}
