package com.ospchat.android.ui.groups

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ospchat.android.ui.peers.ContactSectionHeader
import com.ospchat.shared.data.groups.GroupRecord

/**
 * Foldable group section, structurally identical to
 * [peerSection][com.ospchat.android.ui.peers.peerSection] but rendering
 * [GroupRow] items.
 */
fun LazyListScope.groupSection(
    groups: List<GroupRecord>,
    keyPrefix: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    title: String,
    emptyHint: String,
    onGroupClick: (GroupRecord) -> Unit,
    onAddMembers: (String) -> Unit,
    onRemoveMembers: (String) -> Unit,
    onLeave: (String) -> Unit,
    onShowInfo: (String) -> Unit,
) {
    item(key = "$keyPrefix-header") {
        ContactSectionHeader(
            title = title,
            count = groups.size,
            expanded = expanded,
            onToggle = onToggle,
        )
    }
    if (expanded) {
        if (groups.isEmpty()) {
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
            items(groups, key = { "$keyPrefix-${it.id}" }) { group ->
                GroupRow(
                    group = group,
                    onClick = { onGroupClick(group) },
                    onAddMembers = { onAddMembers(group.id) },
                    onRemoveMembers = { onRemoveMembers(group.id) },
                    onLeave = { onLeave(group.id) },
                    onShowInfo = { onShowInfo(group.id) },
                )
                HorizontalDivider()
            }
        }
    }
}
