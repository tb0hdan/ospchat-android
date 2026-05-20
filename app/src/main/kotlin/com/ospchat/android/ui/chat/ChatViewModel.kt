package com.ospchat.android.ui.chat

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ospchat.android.notifications.MessageNotifier
import com.ospchat.shared.data.calls.CallRepository
import com.ospchat.shared.data.identity.IdentityRepository
import com.ospchat.shared.data.messages.Message
import com.ospchat.shared.data.messages.MessageRepository
import com.ospchat.shared.data.peers.PeerRecord
import com.ospchat.shared.data.peers.PeerRepository
import com.ospchat.shared.data.reactions.Reaction
import com.ospchat.shared.data.reactions.ReactionRepository
import com.ospchat.shared.notifications.ActiveChatTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        @ApplicationContext private val context: Context,
        private val messageRepository: MessageRepository,
        private val identityRepository: IdentityRepository,
        private val peerRepository: PeerRepository,
        private val reactionRepository: ReactionRepository,
        private val activeChatTracker: ActiveChatTracker,
        private val notifier: MessageNotifier,
        private val callRepository: CallRepository,
    ) : ViewModel() {
        val peerUuid: String =
            checkNotNull(savedStateHandle["peerUuid"]) {
                "ChatViewModel requires a 'peerUuid' navigation argument"
            }

        val peer: StateFlow<PeerRecord?> =
            peerRepository
                .observeOne(peerUuid)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        val messages: StateFlow<List<Message>> =
            messageRepository
                .messagesFor(peerUuid)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        /** All reactions on every message in this conversation, grouped by message id. */
        val reactionsByMessage: StateFlow<Map<String, List<Reaction>>> =
            reactionRepository
                .reactionsForPeer(peerUuid)
                .map { reactions -> reactions.groupBy { it.messageId } }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

        private val _selfUuid = MutableStateFlow("")

        /**
         * The current user's stable UUID, used to render messages as self/peer.
         * Loaded eagerly on construction so it is available before the first
         * frame; empty-string until the DataStore read completes, which is
         * typically sub-millisecond on hot pages.
         */
        val selfUuid: StateFlow<String> = _selfUuid.asStateFlow()

        init {
            viewModelScope.launch {
                _selfUuid.value = identityRepository.ensureUuid()
            }
        }

        private val _draftAttachment = MutableStateFlow<Uri?>(null)
        val draftAttachment: StateFlow<Uri?> = _draftAttachment.asStateFlow()

        private val _fullscreenAttachmentPath = MutableStateFlow<String?>(null)
        val fullscreenAttachmentPath: StateFlow<String?> = _fullscreenAttachmentPath.asStateFlow()

        fun attachImage(uri: Uri?) {
            _draftAttachment.value = uri
        }

        fun openFullscreen(localPath: String) {
            _fullscreenAttachmentPath.value = localPath
        }

        fun closeFullscreen() {
            _fullscreenAttachmentPath.value = null
        }

        fun send(body: String) {
            val trimmed = body.trim()
            val attachmentUri = _draftAttachment.value
            // Allow an attachment-only message (empty text).
            if (trimmed.isEmpty() && attachmentUri == null) return
            val target = peer.value ?: return
            if (!target.isOnline) return
            viewModelScope.launch {
                // The shared MessageRepository works on ByteArray (multiplatform).
                // Read the URI bytes here on the Android side; the compressor
                // inside the shared repo handles the rest (decode + scale + JPEG).
                val attachmentBytes =
                    attachmentUri?.let { uri ->
                        runCatching {
                            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        }.getOrNull()
                    }
                messageRepository.send(target.toPeer(), trimmed, attachmentBytes)
                _draftAttachment.value = null
            }
        }

        /**
         * Set or clear the local user's reaction on [messageId]. `null`
         * removes the reaction; a non-null emoji upserts (replacing any
         * previous reaction from the same user on the same message).
         */
        fun react(
            messageId: String,
            emoji: String?,
        ) {
            val target = peer.value
            android.util.Log.d(
                "ChatViewModel",
                "react(messageId=$messageId, emoji=$emoji) peer=${target?.uuid} isOnline=${target?.isOnline}",
            )
            if (target == null) {
                android.util.Log.w("ChatViewModel", "react: peer.value is null; dropping")
                return
            }
            viewModelScope.launch {
                val result = reactionRepository.react(target.toPeer(), messageId, emoji)
                android.util.Log.d(
                    "ChatViewModel",
                    "react result for $messageId: success=${result.isSuccess} err=${result.exceptionOrNull()?.message}",
                )
            }
        }

        /** User tapped a failed bubble. Flip it back to SENDING and re-POST. */
        fun retry(messageId: String) {
            viewModelScope.launch {
                messageRepository.retry(messageId)
            }
        }

        /**
         * Called when the chat has been visible for the receipt threshold.
         * Tells the peer that we've read everything from them with
         * `sentAt <= upToSentAt`. No-op if the peer isn't currently online.
         */
        fun notifyRead(upToSentAt: Long) {
            val target = peer.value ?: return
            if (!target.isOnline) return
            viewModelScope.launch {
                messageRepository.sendReadReceipt(target.toPeer(), upToSentAt)
            }
        }

        /**
         * Called when the chat screen becomes visible. Marks this peer as the
         * one we don't notify about, dismisses any existing notification for
         * them, and advances the local read mark so the unread badge clears.
         */
        fun onChatVisible() {
            activeChatTracker.activePeerUuid = peerUuid
            notifier.cancel(peerUuid)
            viewModelScope.launch {
                peerRepository.markRead(peerUuid = peerUuid, readAt = System.currentTimeMillis())
            }
        }

        /** Inverse of [onChatVisible]: invoked on backgrounding / nav-away. */
        fun onChatHidden() {
            if (activeChatTracker.activePeerUuid == peerUuid) {
                activeChatTracker.activePeerUuid = null
            }
        }

        /**
         * Place an outbound voice call to the current peer. Invokes
         * [onStarted] with the freshly-minted callId so the caller can
         * push the in-call screen. No-op if peer isn't loaded yet or
         * RECORD_AUDIO has not been granted (UI must request the
         * permission before calling).
         */
        fun startCall(onStarted: (String) -> Unit) {
            val target = peer.value ?: return
            viewModelScope.launch {
                runCatching {
                    val callId = callRepository.startCall(target.toPeer())
                    onStarted(callId)
                }.onFailure {
                    android.util.Log.w("ChatViewModel", "startCall failed", it)
                }
            }
        }
    }
