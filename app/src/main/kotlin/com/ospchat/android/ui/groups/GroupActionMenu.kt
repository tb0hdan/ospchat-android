package com.ospchat.android.ui.groups

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ospchat.android.R

/**
 * Dropdown opened on long-press of a group row. The visible items depend on
 * whether the local user is the creator (can add/remove members) or a
 * regular member (can leave).
 */
@Composable
fun GroupActionMenu(
    expanded: Boolean,
    isCreator: Boolean,
    onAddMembers: () -> Unit,
    onRemoveMembers: () -> Unit,
    onLeave: () -> Unit,
    onShowInfo: () -> Unit,
    onDismiss: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
    ) {
        if (isCreator) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.group_action_add_members)) },
                onClick = {
                    onAddMembers()
                    onDismiss()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.group_action_remove_members)) },
                onClick = {
                    onRemoveMembers()
                    onDismiss()
                },
            )
        } else {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.group_action_leave)) },
                onClick = {
                    onLeave()
                    onDismiss()
                },
            )
        }
        DropdownMenuItem(
            text = { Text(stringResource(R.string.group_action_info)) },
            onClick = {
                onShowInfo()
                onDismiss()
            },
        )
    }
}
