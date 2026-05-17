package com.ospchat.android.ui.groupchat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ospchat.android.data.groups.GroupKind
import com.ospchat.android.data.groups.GroupMessage
import com.ospchat.android.data.groups.GroupMessageRepository
import com.ospchat.android.data.groups.GroupRecord
import com.ospchat.android.data.groups.GroupRepository
import com.ospchat.android.data.identity.IdentityRepository
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
class GroupChatViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val groupRepository: GroupRepository,
        private val groupMessageRepository: GroupMessageRepository,
        private val identityRepository: IdentityRepository,
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
    }
