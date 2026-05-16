package com.ospchat.android.ui.main

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.ospchat.android.R
import com.ospchat.android.data.peers.PeerRecord
import com.ospchat.android.ui.about.AboutScreen
import com.ospchat.android.ui.groups.GroupsScreen
import com.ospchat.android.ui.peers.PeersScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainShell(onPeerClick: (PeerRecord) -> Unit) {
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.app_name)) })
        },
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = index == selectedIndex,
                        onClick = { selectedIndex = index },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(stringResource(tab.labelRes)) },
                    )
                }
            }
        },
    ) { padding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            when (MainTab.entries[selectedIndex]) {
                MainTab.Contacts -> PeersScreen(onPeerClick = onPeerClick)
                MainTab.Groups -> GroupsScreen()
                MainTab.About -> AboutScreen()
            }
        }
    }
}

private enum class MainTab(
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    Contacts(R.string.tab_contacts, Icons.Filled.Person),
    Groups(R.string.tab_groups, Icons.Filled.AccountBox),
    About(R.string.tab_about, Icons.Filled.Info),
}
