package com.ospchat.android.ui

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.util.Consumer
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.ospchat.android.ui.call.CallScreen
import com.ospchat.android.ui.call.IncomingCallOverlay
import com.ospchat.android.ui.chat.ChatScreen
import com.ospchat.android.ui.groupchat.GroupChatScreen
import com.ospchat.android.ui.main.MainShell
import com.ospchat.android.ui.nickname.NicknameScreen
import com.ospchat.android.ui.seed.SeedModeScreen

@Composable
fun AppRoot(viewModel: AppViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Surface(modifier = Modifier.fillMaxSize()) {
        when (state) {
            IdentityUiState.Loading -> LoadingScreen()
            IdentityUiState.NeedsNickname -> NicknameScreen()
            is IdentityUiState.Ready -> MainNav()
        }
    }
}

@Composable
private fun MainNav() {
    val navController = rememberNavController()
    ProcessDeepLinks(navController)
    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(navController = navController, startDestination = Routes.MAIN) {
            composable(Routes.MAIN) {
                MainShell(
                    onPeerClick = { peer -> navController.navigate(Routes.chat(peer.uuid)) },
                    onGroupClick = { group -> navController.navigate(Routes.group(group.id)) },
                    onSeedModeClick = { navController.navigate(Routes.SEED_MODE) },
                )
            }
            composable(Routes.SEED_MODE) {
                SeedModeScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = Routes.CHAT_PATTERN,
                arguments = listOf(navArgument(Routes.PEER_UUID_ARG) { type = NavType.StringType }),
                deepLinks = listOf(navDeepLink { uriPattern = "ospchat://chat/{${Routes.PEER_UUID_ARG}}" }),
            ) {
                ChatScreen(
                    onBack = { navController.popBackStack() },
                    onCallStarted = { callId -> navController.navigate(Routes.call(callId)) },
                )
            }
            composable(
                route = Routes.GROUP_PATTERN,
                arguments = listOf(navArgument(Routes.GROUP_ID_ARG) { type = NavType.StringType }),
                deepLinks = listOf(navDeepLink { uriPattern = "ospchat://group/{${Routes.GROUP_ID_ARG}}" }),
            ) {
                GroupChatScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = Routes.CALL_PATTERN,
                arguments = listOf(navArgument(Routes.CALL_ID_ARG) { type = NavType.StringType }),
                deepLinks = listOf(navDeepLink { uriPattern = "ospchat://call/{${Routes.CALL_ID_ARG}}" }),
            ) {
                CallScreen(onClose = { navController.popBackStack() })
            }
        }
        // Overlay above the NavHost so the incoming-call dialog can appear
        // over any current destination — the user keeps the context of what
        // they were doing while deciding to accept or decline.
        IncomingCallOverlay(
            onAccept = { callId -> navController.navigate(Routes.call(callId)) },
        )
    }
}

/**
 * Wire the activity's incoming intents into Navigation so notification taps
 * route to the right destination — both on cold start (via the activity's
 * initial [Intent]) and while the activity is already running (via
 * `addOnNewIntentListener`).
 */
@Composable
private fun ProcessDeepLinks(navController: NavHostController) {
    val activity = LocalContext.current as? ComponentActivity ?: return
    LaunchedEffect(navController) {
        activity.intent?.let { navController.handleDeepLink(it) }
    }
    DisposableEffect(activity, navController) {
        val listener = Consumer<Intent> { newIntent -> navController.handleDeepLink(newIntent) }
        activity.addOnNewIntentListener(listener)
        onDispose { activity.removeOnNewIntentListener(listener) }
    }
}

@Composable
private fun LoadingScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

private object Routes {
    const val MAIN = "main"
    const val SEED_MODE = "seed_mode"
    const val PEER_UUID_ARG = "peerUuid"
    const val CHAT_PATTERN = "chat/{$PEER_UUID_ARG}"
    const val GROUP_ID_ARG = "groupId"
    const val GROUP_PATTERN = "group/{$GROUP_ID_ARG}"
    const val CALL_ID_ARG = "callId"
    const val CALL_PATTERN = "call/{$CALL_ID_ARG}"

    fun chat(peerUuid: String) = "chat/$peerUuid"

    fun group(groupId: String) = "group/$groupId"

    fun call(callId: String) = "call/$callId"
}
