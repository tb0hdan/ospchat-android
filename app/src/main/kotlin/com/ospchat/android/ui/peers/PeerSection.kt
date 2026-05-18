package com.ospchat.android.ui.peers

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ospchat.shared.data.peers.PeerRecord

/**
 * Renders one foldable section (header + rows) into the parent `LazyColumn`.
 * [keyPrefix] disambiguates row keys between the two sections, so the same
 * UUID can legitimately appear once in each list (e.g. a peer being demoted
 * mid-frame) without `LazyColumn` key collisions.
 */
fun LazyListScope.peerSection(
    peers: List<PeerRecord>,
    keyPrefix: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    title: String,
    emptyHint: String,
    onPeerClick: (PeerRecord) -> Unit,
    onAddContact: (String) -> Unit,
    onRemoveContact: (String) -> Unit,
    onShowInfo: (String) -> Unit,
) {
    item(key = "$keyPrefix-header") {
        ContactSectionHeader(
            title = title,
            count = peers.size,
            expanded = expanded,
            onToggle = onToggle,
        )
    }
    if (expanded) {
        if (peers.isEmpty()) {
            item(key = "$keyPrefix-empty") {
                Text(
                    text = emptyHint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                )
                HorizontalDivider()
            }
        } else {
            items(peers, key = { "$keyPrefix-${it.uuid}" }) { peer ->
                PeerRow(
                    peer = peer,
                    onClick = { onPeerClick(peer) },
                    onAddContact = { onAddContact(peer.uuid) },
                    onRemoveContact = { onRemoveContact(peer.uuid) },
                    onShowInfo = { onShowInfo(peer.uuid) },
                )
                HorizontalDivider()
            }
        }
    }
}
