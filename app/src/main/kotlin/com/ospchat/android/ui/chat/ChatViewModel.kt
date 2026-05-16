package com.ospchat.android.ui.chat

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ospchat.android.data.identity.IdentityRepository
import com.ospchat.android.data.messages.Message
import com.ospchat.android.data.messages.MessageRepository
import com.ospchat.android.data.peers.PeerRecord
import com.ospchat.android.data.peers.PeerRepository
import com.ospchat.android.notifications.ActiveChatTracker
import com.ospchat.android.notifications.MessageNotifier
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val messageRepository: MessageRepository,
        private val identityRepository: IdentityRepository,
        private val peerRepository: PeerRepository,
        private val activeChatTracker: ActiveChatTracker,
        private val notifier: MessageNotifier,
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
                messageRepository.send(target.toPeer(), trimmed, attachmentUri)
                _draftAttachment.value = null
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
    }
