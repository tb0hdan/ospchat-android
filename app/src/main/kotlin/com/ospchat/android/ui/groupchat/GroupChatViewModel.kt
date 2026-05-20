package com.ospchat.android.ui.groupchat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ospchat.android.notifications.MessageNotifier
import com.ospchat.shared.data.groups.GroupInfo
import com.ospchat.shared.data.groups.GroupKind
import com.ospchat.shared.data.groups.GroupMessage
import com.ospchat.shared.data.groups.GroupMessageRepository
import com.ospchat.shared.data.groups.GroupRecord
import com.ospchat.shared.data.groups.GroupRepository
import com.ospchat.shared.data.identity.IdentityRepository
import com.ospchat.shared.data.reactions.Reaction
import com.ospchat.shared.data.reactions.ReactionRepository
import com.ospchat.shared.domain.groups.LeaveGroupUseCase
import com.ospchat.shared.notifications.ActiveChatTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GroupChatViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val groupRepository: GroupRepository,
        private val groupMessageRepository: GroupMessageRepository,
        private val identityRepository: IdentityRepository,
        private val reactionRepository: ReactionRepository,
        private val leaveGroup: LeaveGroupUseCase,
        private val activeChatTracker: ActiveChatTracker,
        private val notifier: MessageNotifier,
    ) : ViewModel() {
        val groupId: String =
            checkNotNull(savedStateHandle["groupId"]) {
                "GroupChatViewModel requires a 'groupId' navigation argument"
            }

        val group: StateFlow<GroupRecord?> =
            groupRepository
                .observeOne(groupId)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        val messages: StateFlow<List<GroupMessage>> =
            groupMessageRepository
                .messagesFor(groupId)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        /** All reactions on every message in this group, grouped by message id. */
        val reactionsByMessage: StateFlow<Map<String, List<Reaction>>> =
            reactionRepository
                .reactionsForGroup(groupId)
                .map { reactions -> reactions.groupBy { it.messageId } }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

        private val _selfUuid = MutableStateFlow("")
        val selfUuid: StateFlow<String> = _selfUuid.asStateFlow()

        init {
            viewModelScope.launch {
                _selfUuid.value = identityRepository.ensureUuid()
            }
        }

        /**
         * `true` when the local user is allowed to post here.
         * Always true for `CHAT` groups; only true for the creator of a
         * `BROADCAST` channel.
         */
        val canPost: StateFlow<Boolean> =
            run {
                val flow = MutableStateFlow(true)
                viewModelScope.launch {
                    group.collect { g ->
                        flow.value =
                            g == null ||
                            g.kind != GroupKind.BROADCAST ||
                            g.creatorUuid == _selfUuid.value
                    }
                }
                flow.asStateFlow()
            }

        fun send(body: String) {
            val trimmed = body.trim()
            if (trimmed.isEmpty()) return
            viewModelScope.launch {
                groupMessageRepository.send(groupId, trimmed)
            }
        }

        /**
         * Set or clear the local user's reaction on [messageId]. `null`
         * removes; a non-null emoji upserts (replacing any previous reaction
         * from the same user on the same message). Fans out to every other
         * current group member.
         */
        fun react(
            messageId: String,
            emoji: String?,
        ) {
            viewModelScope.launch {
                reactionRepository.reactToGroup(
                    groupId = groupId,
                    messageId = messageId,
                    emoji = emoji,
                )
            }
        }

        fun onChatVisible() {
            activeChatTracker.activeGroupId = groupId
            notifier.cancelGroup(groupId)
            viewModelScope.launch {
                groupRepository.markRead(groupId)
            }
        }

        fun onChatHidden() {
            if (activeChatTracker.activeGroupId == groupId) {
                activeChatTracker.activeGroupId = null
            }
        }

        /**
         * Full Info payload for the avatar-tap dialog. Lazily loaded the
         * first time the user opens the dialog, then kept warm by
         * [SharingStarted.WhileSubscribed] so re-opens are instantaneous.
         */
        val groupInfo: StateFlow<GroupInfo?> =
            groupRepository
                .observeInfo(groupId)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        private val _infoDialogVisible = MutableStateFlow(false)
        val infoDialogVisible: StateFlow<Boolean> = _infoDialogVisible.asStateFlow()

        fun showInfo() {
            _infoDialogVisible.value = true
        }

        fun dismissInfo() {
            _infoDialogVisible.value = false
        }

        /**
         * One-shot signal emitted after the user leaves a group, telling the
         * screen to navigate back. `extraBufferCapacity = 1` so the emission
         * never suspends even if no collector is ready in time.
         */
        private val _leftGroup = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        val leftGroup: SharedFlow<Unit> = _leftGroup.asSharedFlow()

        fun onLeave() {
            viewModelScope.launch {
                leaveGroup(groupId)
                _leftGroup.tryEmit(Unit)
            }
        }
    }
