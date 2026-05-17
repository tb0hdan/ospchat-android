package com.ospchat.android.ui.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.emoji2.emojipicker.EmojiPickerView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.ospchat.android.data.messages.Attachment
import com.ospchat.android.data.messages.Message
import com.ospchat.android.data.peers.PeerRecord
import com.ospchat.android.data.reactions.Reaction
import com.ospchat.android.ui.avatar.Avatar
import com.ospchat.android.ui.avatar.AvatarModel
import com.ospchat.android.ui.avatar.computeInitials
import kotlinx.coroutines.delay
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onBack: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val peer by viewModel.peer.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val reactionsByMessage by viewModel.reactionsByMessage.collectAsStateWithLifecycle()
    val selfUuid by viewModel.selfUuid.collectAsStateWithLifecycle()
    var reactingToMessage by remember { mutableStateOf<Message?>(null) }
    val reactionSheetState = rememberModalBottomSheetState()
    val draftAttachment by viewModel.draftAttachment.collectAsStateWithLifecycle()
    val fullscreenPath by viewModel.fullscreenAttachmentPath.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf("") }
    var showEmojiPicker by remember { mutableStateOf(false) }
    val emojiSheetState = rememberModalBottomSheetState()
    var showAttachSheet by remember { mutableStateOf(false) }
    val attachSheetState = rememberModalBottomSheetState()
    val context = androidx.compose.ui.platform.LocalContext.current
    var pendingCaptureUri by androidx.compose.runtime.saveable.rememberSaveable {
        mutableStateOf<Uri?>(null)
    }

    val takePictureLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.TakePicture(),
        ) { success ->
            if (success) pendingCaptureUri?.let { viewModel.attachImage(it) }
            pendingCaptureUri = null
        }

    fun launchCamera() {
        val captureDir = java.io.File(context.cacheDir, "captures").apply { mkdirs() }
        // Sweep stale captures from previous attempts so cache doesn't grow.
        captureDir.listFiles()?.forEach { it.delete() }
        val captureFile = java.io.File(captureDir, "${UUID.randomUUID()}.jpg")
        val uri =
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                captureFile,
            )
        pendingCaptureUri = uri
        takePictureLauncher.launch(uri)
    }
    val listState = rememberLazyListState()

    val photoPickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia(),
        ) { uri -> viewModel.attachImage(uri) }

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
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        peer?.let {
                            Avatar(model = it.toAvatarModel(), size = 32.dp)
                            Spacer(modifier = Modifier.size(10.dp))
                        }
                        Text(peer?.nickname ?: "Chat")
                    }
                },
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
                    val reactions = reactionsByMessage[message.id].orEmpty()
                    MessageBubble(
                        message = message,
                        isSelf = selfUuid.isNotEmpty() && message.fromUuid == selfUuid,
                        reactions = reactions,
                        selfUuid = selfUuid,
                        onRetry = { viewModel.retry(message.id) },
                        onImageTap = { localPath -> viewModel.openFullscreen(localPath) },
                        onLongPress = { reactingToMessage = message },
                        onChipTap = { emoji ->
                            // Toggle: if my current reaction matches this chip, remove it;
                            // otherwise upsert with this chip's emoji.
                            val mine = reactions.firstOrNull { it.fromUuid == selfUuid }
                            val next = if (mine?.emoji == emoji) null else emoji
                            viewModel.react(message.id, next)
                        },
                    )
                }
            }
            MessageComposer(
                value = draft,
                onValueChange = { draft = it },
                onEmojiClick = { showEmojiPicker = true },
                onAttachClick = { showAttachSheet = true },
                draftAttachment = draftAttachment,
                onClearAttachment = { viewModel.attachImage(null) },
                sendEnabled =
                    peer?.isOnline == true &&
                        (draft.trim().isNotEmpty() || draftAttachment != null),
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

        if (showAttachSheet) {
            ModalBottomSheet(
                onDismissRequest = { showAttachSheet = false },
                sheetState = attachSheetState,
            ) {
                AttachOption(
                    glyph = "📷",
                    label = "Camera",
                    onClick = {
                        showAttachSheet = false
                        launchCamera()
                    },
                )
                AttachOption(
                    glyph = "🖼️",
                    label = "Gallery",
                    onClick = {
                        showAttachSheet = false
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                )
                Spacer(modifier = Modifier.size(8.dp))
            }
        }

        reactingToMessage?.let { msg ->
            ModalBottomSheet(
                onDismissRequest = { reactingToMessage = null },
                sheetState = reactionSheetState,
            ) {
                AndroidView(
                    factory = { ctx ->
                        EmojiPickerView(ctx).apply {
                            setOnEmojiPickedListener { picked ->
                                android.util.Log.d(
                                    "ChatScreen",
                                    "reaction picker picked emoji=${picked.emoji} for messageId=${msg.id}",
                                )
                                viewModel.react(msg.id, picked.emoji)
                                reactingToMessage = null
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

        fullscreenPath?.let { path ->
            FullscreenImageDialog(path = path, onDismiss = { viewModel.closeFullscreen() })
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun MessageBubble(
    message: Message,
    isSelf: Boolean,
    reactions: List<Reaction>,
    selfUuid: String,
    onRetry: () -> Unit,
    onImageTap: (String) -> Unit,
    onLongPress: () -> Unit,
    onChipTap: (String) -> Unit,
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
                .combinedClickable(
                    onClick = { if (retryable) onRetry() },
                    onLongClick = onLongPress,
                ),
        horizontalAlignment = align,
    ) {
        Column(
            modifier =
                Modifier
                    .widthIn(max = 320.dp)
                    .background(container, shape = RoundedCornerShape(16.dp))
                    .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            message.attachment?.let { attachment ->
                AttachmentImage(attachment = attachment, onTap = onImageTap)
            }
            if (message.body.isNotEmpty()) {
                Text(
                    text = message.body,
                    color = onContainer,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
            if (reactions.isNotEmpty()) {
                ReactionRow(
                    reactions = reactions,
                    selfUuid = selfUuid,
                    isSelfBubble = isSelf,
                    onChipTap = onChipTap,
                )
            }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReactionRow(
    reactions: List<Reaction>,
    selfUuid: String,
    isSelfBubble: Boolean,
    onChipTap: (String) -> Unit,
) {
    data class Group(
        val emoji: String,
        val count: Int,
        val containsSelf: Boolean,
    )
    val groups =
        reactions
            .groupBy { it.emoji }
            .map { (emoji, list) ->
                Group(
                    emoji = emoji,
                    count = list.size,
                    containsSelf = selfUuid.isNotEmpty() && list.any { it.fromUuid == selfUuid },
                )
            }
    // Chips sit inside the bubble, so they must contrast with whichever
    // container colour the bubble uses; pick a "highlight" tone for self
    // reactions and a neutral surface tone for the rest.
    val highlight = MaterialTheme.colorScheme.tertiaryContainer
    val onHighlight = MaterialTheme.colorScheme.onTertiaryContainer
    val neutral =
        if (isSelfBubble) {
            MaterialTheme.colorScheme.surface
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        }
    val onNeutral = MaterialTheme.colorScheme.onSurface
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        FlowRow {
            groups.forEach { group ->
                val containerColor = if (group.containsSelf) highlight else neutral
                val contentColor = if (group.containsSelf) onHighlight else onNeutral
                Surface(
                    color = containerColor,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.padding(end = 4.dp),
                    onClick = { onChipTap(group.emoji) },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = group.emoji, style = MaterialTheme.typography.labelMedium)
                        if (group.count > 1) {
                            Spacer(modifier = Modifier.size(4.dp))
                            Text(
                                text = group.count.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = contentColor,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentImage(
    attachment: Attachment,
    onTap: (String) -> Unit,
) {
    val aspect =
        if (attachment.width > 0 && attachment.height > 0) {
            attachment.width.toFloat() / attachment.height.toFloat()
        } else {
            1f
        }
    val shape = RoundedCornerShape(12.dp)
    val baseModifier =
        Modifier
            .fillMaxWidth()
            .aspectRatio(aspect.coerceIn(0.5f, 2.5f))
            .clip(shape)
    if (attachment.localPath != null) {
        AsyncImage(
            model = File(attachment.localPath),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = baseModifier.clickable { onTap(attachment.localPath) },
        )
    } else {
        Box(
            modifier =
                baseModifier
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
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
    onAttachClick: () -> Unit,
    draftAttachment: android.net.Uri?,
    onClearAttachment: () -> Unit,
    sendEnabled: Boolean,
    onSend: () -> Unit,
) {
    val canSend = sendEnabled
    Column(modifier = Modifier.fillMaxWidth()) {
        if (draftAttachment != null) {
            DraftAttachmentChip(uri = draftAttachment, onClear = onClearAttachment)
        }
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onAttachClick) {
                Icon(Icons.Filled.Add, contentDescription = "Attach image")
            }
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
private fun DraftAttachmentChip(
    uri: android.net.Uri,
    onClear: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp)),
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = "Image attached",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onClear) {
            Icon(Icons.Filled.Close, contentDescription = "Remove attachment")
        }
    }
}

@Composable
private fun AttachOption(
    glyph: String,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = glyph, style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.size(16.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun FullscreenImageDialog(
    path: String,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = true),
    ) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        val transform =
            rememberTransformableState { zoomChange, panChange, _ ->
                scale = (scale * zoomChange).coerceIn(1f, 5f)
                offset += panChange
            }
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = File(path),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .transformable(state = transform)
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y,
                        ),
            )
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

private fun PeerRecord.toAvatarModel(): AvatarModel =
    if (avatarLocalPath != null) {
        AvatarModel.Custom(avatarLocalPath)
    } else {
        AvatarModel.Initials(letters = computeInitials(nickname), seed = uuid)
    }
