package com.ospchat.android.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ospchat.android.ui.nickname.NicknameScreen
import com.ospchat.android.ui.peers.PeersScreen

@Composable
fun AppRoot(viewModel: AppViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Surface(modifier = Modifier.fillMaxSize()) {
        when (state) {
            IdentityUiState.Loading -> LoadingScreen()
            IdentityUiState.NeedsNickname -> NicknameScreen()
            is IdentityUiState.Ready -> PeersScreen()
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
