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
import com.ospchat.android.ui.chat.ChatScreen
import com.ospchat.android.ui.main.MainShell
import com.ospchat.android.ui.nickname.NicknameScreen

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
    NavHost(navController = navController, startDestination = Routes.MAIN) {
        composable(Routes.MAIN) {
            MainShell(
                onPeerClick = { peer -> navController.navigate(Routes.chat(peer.uuid)) },
            )
        }
        composable(
            route = Routes.CHAT_PATTERN,
            arguments = listOf(navArgument(Routes.PEER_UUID_ARG) { type = NavType.StringType }),
            deepLinks = listOf(navDeepLink { uriPattern = "ospchat://chat/{${Routes.PEER_UUID_ARG}}" }),
        ) {
            ChatScreen(onBack = { navController.popBackStack() })
        }
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
    const val PEER_UUID_ARG = "peerUuid"
    const val CHAT_PATTERN = "chat/{$PEER_UUID_ARG}"

    fun chat(peerUuid: String) = "chat/$peerUuid"
}
