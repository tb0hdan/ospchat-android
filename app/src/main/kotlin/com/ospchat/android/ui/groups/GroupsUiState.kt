package com.ospchat.android.ui.groups

import com.ospchat.shared.data.groups.GroupRecord

sealed interface GroupsUiState {
    data object Loading : GroupsUiState

    data class Ready(
        val groupChats: List<GroupRecord>,
        val broadcasts: List<GroupRecord>,
    ) : GroupsUiState
}
