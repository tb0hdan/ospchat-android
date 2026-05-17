package com.ospchat.android.ui.peers

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ospchat.android.R

/**
 * Two-item dropdown opened on long-press of a peer row. The first action
 * swaps between Add/Remove depending on whether the peer is already saved.
 */
@Composable
fun PeerActionMenu(
    expanded: Boolean,
    isContact: Boolean,
    onAddContact: () -> Unit,
    onRemoveContact: () -> Unit,
    onShowInfo: () -> Unit,
    onDismiss: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
    ) {
        if (isContact) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.peer_action_remove_contact)) },
                onClick = {
                    onRemoveContact()
                    onDismiss()
                },
            )
        } else {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.peer_action_add_contact)) },
                onClick = {
                    onAddContact()
                    onDismiss()
                },
            )
        }
        DropdownMenuItem(
            text = { Text(stringResource(R.string.peer_action_info)) },
            onClick = {
                onShowInfo()
                onDismiss()
            },
        )
    }
}
