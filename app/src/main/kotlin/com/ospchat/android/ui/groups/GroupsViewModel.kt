package com.ospchat.android.ui.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ospchat.android.data.groups.GroupInfo
import com.ospchat.android.data.groups.GroupKind
import com.ospchat.android.data.groups.GroupRepository
import com.ospchat.android.data.peers.PeerRecord
import com.ospchat.android.data.peers.PeerRepository
import com.ospchat.android.domain.groups.AddGroupMembersUseCase
import com.ospchat.android.domain.groups.CreateGroupUseCase
import com.ospchat.android.domain.groups.LeaveGroupUseCase
import com.ospchat.android.domain.groups.RemoveGroupMembersUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GroupsViewModel
    @Inject
    constructor(
        private val groupRepository: GroupRepository,
        peerRepository: PeerRepository,
        private val createGroup: CreateGroupUseCase,
        private val addMembers: AddGroupMembersUseCase,
        private val removeMembers: RemoveGroupMembersUseCase,
        private val leaveGroup: LeaveGroupUseCase,
    ) : ViewModel() {
        val uiState: StateFlow<GroupsUiState> =
            combine(
                groupRepository.observeContacts(GroupKind.CHAT),
                groupRepository.observeContacts(GroupKind.BROADCAST),
            ) { chats, broadcasts ->
                GroupsUiState.Ready(groupChats = chats, broadcasts = broadcasts) as GroupsUiState
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GroupsUiState.Loading)

        /** Saved contacts, used to seed the picker for create / add. */
        val contacts: StateFlow<List<PeerRecord>> =
            peerRepository
                .observeContacts()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        /**
         * The group whose member list / metadata is being inspected by any
         * dialog or picker. Decoupled from [infoDialogVisible] so the
         * Add/Remove pickers can read [groupInfo] without forcing the Info
         * AlertDialog to also open.
         */
        private val selectedGroupId = MutableStateFlow<String?>(null)
        private val _infoDialogVisible = MutableStateFlow(false)
        val infoDialogVisible: StateFlow<Boolean> = _infoDialogVisible

        val groupInfo: StateFlow<GroupInfo?> =
            selectedGroupId
                .flatMapLatest { id ->
                    if (id == null) flowOf(null) else groupRepository.observeInfo(id)
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        private val _newGroupId = MutableStateFlow<String?>(null)
        val newGroupId: StateFlow<String?> = _newGroupId

        fun onCreate(
            name: String,
            kind: GroupKind,
            memberUuids: List<String>,
        ) {
            viewModelScope.launch {
                val id = createGroup(name = name.trim(), kind = kind, memberUuids = memberUuids)
                _newGroupId.value = id
            }
        }

        fun consumeNewGroupId() {
            _newGroupId.value = null
        }

        fun onAddMembers(
            groupId: String,
            memberUuids: List<String>,
        ) {
            viewModelScope.launch { addMembers(groupId, memberUuids) }
        }

        fun onRemoveMembers(
            groupId: String,
            memberUuids: List<String>,
        ) {
            viewModelScope.launch { removeMembers(groupId, memberUuids) }
        }

        fun onLeave(groupId: String) {
            viewModelScope.launch { leaveGroup(groupId) }
        }

        /** Load the group's members + metadata into [groupInfo]. */
        fun selectGroup(groupId: String) {
            selectedGroupId.value = groupId
        }

        fun clearSelection() {
            selectedGroupId.value = null
            _infoDialogVisible.value = false
        }

        fun showInfo(groupId: String) {
            selectedGroupId.value = groupId
            _infoDialogVisible.value = true
        }

        fun dismissInfo() {
            _infoDialogVisible.value = false
        }
    }
