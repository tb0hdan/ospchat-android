package com.ospchat.android.ui.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ospchat.android.R
import com.ospchat.shared.data.groups.GroupRecord

@Composable
fun GroupsScreen(
    onGroupClick: (GroupRecord) -> Unit,
    viewModel: GroupsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val info by viewModel.groupInfo.collectAsStateWithLifecycle()
    val infoDialogVisible by viewModel.infoDialogVisible.collectAsStateWithLifecycle()
    val newGroupId by viewModel.newGroupId.collectAsStateWithLifecycle()
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()

    var showNewGroup by remember { mutableStateOf(false) }
    var addingToGroupId by remember { mutableStateOf<String?>(null) }
    var removingFromGroupId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(addingToGroupId) {
        addingToGroupId?.let { viewModel.selectGroup(it) }
    }
    LaunchedEffect(removingFromGroupId) {
        removingFromGroupId?.let { viewModel.selectGroup(it) }
    }

    // After creating a group, navigate straight into it.
    LaunchedEffect(newGroupId) {
        val id = newGroupId ?: return@LaunchedEffect
        val record =
            (uiState as? GroupsUiState.Ready)
                ?.let { it.groupChats + it.broadcasts }
                ?.firstOrNull { it.id == id }
        if (record != null) {
            viewModel.consumeNewGroupId()
            showNewGroup = false
            onGroupClick(record)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            GroupsUiState.Loading -> {
                Unit
            }

            is GroupsUiState.Ready -> {
                GroupsTabContent(
                    state = state,
                    onGroupClick = onGroupClick,
                    onAddMembers = { addingToGroupId = it },
                    onRemoveMembers = { removingFromGroupId = it },
                    onLeave = viewModel::onLeave,
                    onShowInfo = viewModel::showInfo,
                )
            }
        }

        ExtendedFloatingActionButton(
            onClick = { showNewGroup = true },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text(stringResource(R.string.group_new_fab)) },
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp),
        )
    }

    if (showNewGroup) {
        NewGroupSheet(
            contacts = contacts,
            onCreate = { name, kind, memberUuids ->
                viewModel.onCreate(name = name, kind = kind, memberUuids = memberUuids)
            },
            onDismiss = { showNewGroup = false },
        )
    }

    if (infoDialogVisible) {
        info?.let { groupInfo ->
            GroupInfoDialog(info = groupInfo, onDismiss = viewModel::dismissInfo)
        }
    }

    addingToGroupId?.let { groupId ->
        val current = info
        val existingMemberUuids =
            current
                ?.takeIf { it.record.id == groupId }
                ?.members
                ?.map { it.memberUuid }
                ?.toSet()
                ?: emptySet()
        val candidates =
            contacts
                .filter { it.uuid !in existingMemberUuids }
                .map {
                    MembershipCandidate(
                        uuid = it.uuid,
                        nickname = it.nickname,
                        subtitle = if (it.isOnline) "${it.host}:${it.port}" else "offline",
                    )
                }
        MembershipPickerSheet(
            title = stringResource(R.string.group_add_members_title),
            actionLabel = stringResource(R.string.group_add_members_button),
            candidates = candidates,
            onConfirm = { uuids ->
                viewModel.onAddMembers(groupId, uuids)
                addingToGroupId = null
            },
            onDismiss = { addingToGroupId = null },
        )
    }

    removingFromGroupId?.let { groupId ->
        val current = info
        val candidates =
            current
                ?.takeIf { it.record.id == groupId }
                ?.members
                ?.filter { it.memberUuid != current.record.creatorUuid }
                ?.map {
                    MembershipCandidate(
                        uuid = it.memberUuid,
                        nickname = it.memberNickname,
                        subtitle = "",
                    )
                }.orEmpty()
        MembershipPickerSheet(
            title = stringResource(R.string.group_remove_members_title),
            actionLabel = stringResource(R.string.group_remove_members_button),
            candidates = candidates,
            onConfirm = { uuids ->
                viewModel.onRemoveMembers(groupId, uuids)
                removingFromGroupId = null
            },
            onDismiss = { removingFromGroupId = null },
        )
    }
}

@Composable
private fun GroupsTabContent(
    state: GroupsUiState.Ready,
    onGroupClick: (GroupRecord) -> Unit,
    onAddMembers: (String) -> Unit,
    onRemoveMembers: (String) -> Unit,
    onLeave: (String) -> Unit,
    onShowInfo: (String) -> Unit,
) {
    var chatsExpanded by rememberSaveable { mutableStateOf(true) }
    var broadcastsExpanded by rememberSaveable { mutableStateOf(true) }

    val chatsTitle = stringResource(R.string.groups_section_chats)
    val chatsEmpty = stringResource(R.string.groups_section_chats_empty)
    val broadcastsTitle = stringResource(R.string.groups_section_broadcasts)
    val broadcastsEmpty = stringResource(R.string.groups_section_broadcasts_empty)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Top,
    ) {
        groupSection(
            groups = state.groupChats,
            keyPrefix = "gc",
            expanded = chatsExpanded,
            onToggle = { chatsExpanded = !chatsExpanded },
            title = chatsTitle,
            emptyHint = chatsEmpty,
            onGroupClick = onGroupClick,
            onAddMembers = onAddMembers,
            onRemoveMembers = onRemoveMembers,
            onLeave = onLeave,
            onShowInfo = onShowInfo,
        )
        groupSection(
            groups = state.broadcasts,
            keyPrefix = "bc",
            expanded = broadcastsExpanded,
            onToggle = { broadcastsExpanded = !broadcastsExpanded },
            title = broadcastsTitle,
            emptyHint = broadcastsEmpty,
            onGroupClick = onGroupClick,
            onAddMembers = onAddMembers,
            onRemoveMembers = onRemoveMembers,
            onLeave = onLeave,
            onShowInfo = onShowInfo,
        )
    }
}
