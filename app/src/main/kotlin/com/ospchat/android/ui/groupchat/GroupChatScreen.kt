package com.ospchat.android.ui.groupchat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ospchat.android.R
import com.ospchat.android.data.groups.GroupKind
import com.ospchat.android.data.groups.GroupMessage
import com.ospchat.android.ui.avatar.Avatar
import com.ospchat.android.ui.groups.GroupInfoDialog
import com.ospchat.android.ui.groups.toAvatarModel
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupChatScreen(
    onBack: () -> Unit,
    viewModel: GroupChatViewModel = hiltViewModel(),
) {
    val group by viewModel.group.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val selfUuid by viewModel.selfUuid.collectAsStateWithLifecycle()
    val canPost by viewModel.canPost.collectAsStateWithLifecycle()
    val groupInfo by viewModel.groupInfo.collectAsStateWithLifecycle()
    val infoDialogVisible by viewModel.infoDialogVisible.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val atBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            val target = messages.lastIndex
            target < 0 || last >= target - 1
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> viewModel.onChatVisible()
                    Lifecycle.Event.ON_STOP -> viewModel.onChatHidden()
                    else -> Unit
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.onChatHidden()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.leftGroup.collect { onBack() }
    }

    var menuExpanded by remember { mutableStateOf(false) }

    var initialScrollDone by remember { mutableStateOf(false) }
    LaunchedEffect(messages.lastOrNull()?.id) {
        if (messages.isEmpty()) return@LaunchedEffect
        if (!initialScrollDone) {
            listState.scrollToItem(messages.lastIndex)
            initialScrollDone = true
        } else if (atBottom) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        group?.let {
                            Avatar(
                                model = it.toAvatarModel(),
                                size = 32.dp,
                                modifier = Modifier.clickable { viewModel.showInfo() },
                            )
                            Spacer(modifier = Modifier.size(10.dp))
                        }
                        Column {
                            Text(group?.name ?: "Group")
                            group?.let {
                                val subtitle =
                                    if (it.kind == GroupKind.BROADCAST) {
                                        stringResource(R.string.group_kind_broadcast)
                                    } else {
                                        stringResource(R.string.group_kind_chat)
                                    }
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (group?.isCreator == false) {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                Icons.Filled.MoreVert,
                                contentDescription =
                                    stringResource(R.string.group_chat_menu_content_description),
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.group_action_leave)) },
                                onClick = {
                                    menuExpanded = false
                                    viewModel.onLeave()
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            LazyColumn(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(messages, key = { it.id }) { message ->
                    GroupMessageBubble(
                        message = message,
                        isSelf = selfUuid.isNotEmpty() && message.fromUuid == selfUuid,
                    )
                }
            }
            if (canPost) {
                GroupMessageComposer(
                    value = draft,
                    onValueChange = { draft = it },
                    sendEnabled = draft.trim().isNotEmpty(),
                    onSend = {
                        viewModel.send(draft)
                        draft = ""
                    },
                )
            } else {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.group_broadcast_read_only),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (infoDialogVisible) {
        groupInfo?.let { info ->
            GroupInfoDialog(info = info, onDismiss = viewModel::dismissInfo)
        }
    }
}

@Composable
private fun GroupMessageBubble(
    message: GroupMessage,
    isSelf: Boolean,
) {
    val align = if (isSelf) Alignment.End else Alignment.Start
    val container =
        if (isSelf) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
    val onContainer =
        if (isSelf) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = align,
    ) {
        Column(
            modifier =
                Modifier
                    .widthIn(max = 320.dp)
                    .background(container, shape = RoundedCornerShape(16.dp))
                    .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (!isSelf) {
                Text(
                    text = message.fromNickname,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            Text(
                text = message.body,
                color = onContainer,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(message.sentAt)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (isSelf) {
                Spacer(modifier = Modifier.size(6.dp))
                val (text, color) =
                    when (message.status) {
                        GroupMessage.Status.SENDING -> {
                            "Sending…" to MaterialTheme.colorScheme.onSurfaceVariant
                        }

                        GroupMessage.Status.DELIVERED -> {
                            "✓" to MaterialTheme.colorScheme.onSurfaceVariant
                        }

                        GroupMessage.Status.FAILED -> {
                            "⚠ Not delivered" to MaterialTheme.colorScheme.error
                        }
                    }
                Text(text = text, style = MaterialTheme.typography.labelSmall, color = color)
            }
        }
    }
}

@Composable
private fun GroupMessageComposer(
    value: String,
    onValueChange: (String) -> Unit,
    sendEnabled: Boolean,
    onSend: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Message") },
            maxLines = 4,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { if (sendEnabled) onSend() }),
        )
        IconButton(
            onClick = onSend,
            enabled = sendEnabled,
            modifier = Modifier.padding(start = 4.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
        }
    }
}
