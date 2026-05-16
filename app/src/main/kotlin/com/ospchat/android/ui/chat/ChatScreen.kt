package com.ospchat.android.ui.chat

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.emoji2.emojipicker.EmojiPickerView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ospchat.android.data.messages.Message
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onBack: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val peer by viewModel.peer.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val selfUuid by viewModel.selfUuid.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf("") }
    var showEmojiPicker by remember { mutableStateOf(false) }
    val emojiSheetState = rememberModalBottomSheetState()
    val listState = rememberLazyListState()

    // Only auto-scroll if the user is already at (or within one item of) the
    // bottom. This preserves the user's position when they have scrolled up
    // to read history while messages are arriving.
    val atBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            val target = messages.lastIndex
            target < 0 || last >= target - 1
        }
    }
    // Tell the notifier we're looking at this peer's chat; clear when we
    // leave / background. Keyed on the LifecycleOwner so the observer is
    // attached for the full lifetime this screen is in composition.
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

    // Read-receipt timer: once the chat has been on-screen for 2 s with at
    // least one inbound message visible, tell the peer we've read up to the
    // newest one. Re-keys on new messages and on peer.isOnline transitions, so
    // the timer restarts whenever something relevant changes.
    val latestInboundSentAt = messages.lastOrNull { it.direction == Message.Direction.IN }?.sentAt
    LaunchedEffect(latestInboundSentAt, peer?.isOnline) {
        if (latestInboundSentAt == null) return@LaunchedEffect
        if (peer?.isOnline != true) return@LaunchedEffect
        delay(READ_RECEIPT_DELAY_MS)
        viewModel.notifyRead(latestInboundSentAt)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(peer?.nickname ?: "Chat") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    StatusDot(isOnline = peer?.isOnline == true)
                    Spacer(modifier = Modifier.size(16.dp))
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
                    MessageBubble(
                        message = message,
                        isSelf = selfUuid.isNotEmpty() && message.fromUuid == selfUuid,
                        onRetry = { viewModel.retry(message.id) },
                    )
                }
            }
            MessageComposer(
                value = draft,
                onValueChange = { draft = it },
                onEmojiClick = { showEmojiPicker = true },
                sendEnabled = peer?.isOnline == true,
                onSend = {
                    viewModel.send(draft)
                    draft = ""
                },
            )
        }

        if (showEmojiPicker) {
            ModalBottomSheet(
                onDismissRequest = { showEmojiPicker = false },
                sheetState = emojiSheetState,
            ) {
                AndroidView(
                    factory = { ctx ->
                        EmojiPickerView(ctx).apply {
                            setOnEmojiPickedListener { picked ->
                                draft += picked.emoji
                            }
                        }
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 320.dp, max = 480.dp),
                )
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: Message,
    isSelf: Boolean,
    onRetry: () -> Unit,
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
    val retryable = isSelf && message.status == Message.Status.FAILED
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(if (retryable) Modifier.clickable(onClick = onRetry) else Modifier),
        horizontalAlignment = align,
    ) {
        Box(
            modifier =
                Modifier
                    .widthIn(max = 320.dp)
                    .background(container, shape = RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(text = message.body, color = onContainer)
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
                OutboundStatus(status = message.status)
            }
        }
    }
}

@Composable
private fun OutboundStatus(status: Message.Status) {
    val (text, color) =
        when (status) {
            Message.Status.SENDING -> "Sending…" to MaterialTheme.colorScheme.onSurfaceVariant
            Message.Status.DELIVERED -> "✓" to MaterialTheme.colorScheme.onSurfaceVariant
            Message.Status.READ -> "✓✓" to MaterialTheme.colorScheme.primary
            Message.Status.FAILED -> "⚠ Tap to retry" to MaterialTheme.colorScheme.error
        }
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
    )
}

private const val READ_RECEIPT_DELAY_MS = 2_000L

@Composable
private fun MessageComposer(
    value: String,
    onValueChange: (String) -> Unit,
    onEmojiClick: () -> Unit,
    sendEnabled: Boolean,
    onSend: () -> Unit,
) {
    val canSend = sendEnabled && value.trim().isNotEmpty()
    Column(modifier = Modifier.fillMaxWidth()) {
        if (!sendEnabled) {
            Text(
                text = "Peer is offline — messages can't be sent right now.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onEmojiClick) {
                Text(text = "😊", style = MaterialTheme.typography.titleLarge)
            }
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message") },
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
            )
            IconButton(
                onClick = onSend,
                enabled = canSend,
                modifier = Modifier.padding(start = 4.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
            }
        }
    }
}

@Composable
private fun StatusDot(isOnline: Boolean) {
    val color =
        if (isOnline) Color(0xFF22C55E) else MaterialTheme.colorScheme.outline
    Box(
        modifier =
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
    )
}
