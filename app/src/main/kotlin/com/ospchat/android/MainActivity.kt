package com.ospchat.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ospchat.android.service.DiscoveryForegroundService
import com.ospchat.android.ui.AppRoot
import com.ospchat.android.ui.theme.OspChatTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (handleExitIntent(intent)) return
        enableEdgeToEdge()
        setContent {
            OspChatTheme {
                AppRoot()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (handleExitIntent(intent)) return
        setIntent(intent)
    }

    /**
     * If [intent] carries [ACTION_EXIT], stop the discovery service and
     * remove this task from the recents list. Returns `true` when the
     * caller should bail out of the rest of the lifecycle method because
     * the Activity is finishing.
     */
    private fun handleExitIntent(intent: Intent?): Boolean {
        if (intent?.action != ACTION_EXIT) return false
        DiscoveryForegroundService.stop(this)
        finishAndRemoveTask()
        return true
    }

    companion object {
        /**
         * Intent action signalling that the user wants to exit OSPChat.
         * Triggered from the persistent notification's "Exit" action and
         * from the About screen's "Exit" button.
         */
        const val ACTION_EXIT = "com.ospchat.android.action.EXIT"
    }
}
