package com.ospchat.android.ui.groups

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ospchat.android.R
import com.ospchat.android.data.groups.GroupKind
import com.ospchat.android.data.groups.GroupRecord
import com.ospchat.android.ui.avatar.Avatar

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun GroupRow(
    group: GroupRecord,
    onClick: () -> Unit,
    onAddMembers: () -> Unit,
    onRemoveMembers: () -> Unit,
    onLeave: () -> Unit,
    onShowInfo: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { menuExpanded = true },
                ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(model = group.toAvatarModel(), size = 44.dp)
            Spacer(modifier = Modifier.size(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.name,
                    style = MaterialTheme.typography.titleMedium,
                )
                val subtitle =
                    pluralStringResource(
                        R.plurals.group_member_count,
                        group.memberCount,
                        group.memberCount,
                    ) +
                        if (group.kind == GroupKind.BROADCAST) {
                            " · " + stringResource(R.string.group_kind_broadcast_short)
                        } else {
                            ""
                        }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (group.unreadCount > 0) {
                UnreadBadge(count = group.unreadCount)
            }
        }
        GroupActionMenu(
            expanded = menuExpanded,
            isCreator = group.isCreator,
            onAddMembers = onAddMembers,
            onRemoveMembers = onRemoveMembers,
            onLeave = onLeave,
            onShowInfo = onShowInfo,
            onDismiss = { menuExpanded = false },
        )
    }
}

@Composable
private fun UnreadBadge(count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Filled.Email,
            contentDescription = stringResource(R.string.peers_unread_content_description),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.size(6.dp))
        Badge { Text(count.toString()) }
    }
}
