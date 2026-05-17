package com.ospchat.android.ui.groups

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.ospchat.android.R
import com.ospchat.android.data.groups.GroupInfo
import com.ospchat.android.data.groups.GroupKind
import com.ospchat.android.ui.avatar.Avatar

@Composable
fun GroupInfoDialog(
    info: GroupInfo,
    onDismiss: () -> Unit,
) {
    val kindLabel =
        if (info.record.kind == GroupKind.BROADCAST) {
            stringResource(R.string.group_kind_broadcast)
        } else {
            stringResource(R.string.group_kind_chat)
        }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.peer_info_close))
            }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(model = info.record.toAvatarModel(), size = 48.dp)
                Spacer(modifier = Modifier.size(12.dp))
                Column {
                    Text(text = info.record.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = kindLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LabeledValue(
                    label = stringResource(R.string.group_info_creator),
                    value = info.creatorNickname,
                )
                LabeledValue(
                    label = stringResource(R.string.group_info_created_at),
                    value =
                        DateUtils
                            .getRelativeTimeSpanString(
                                info.record.createdAt,
                                System.currentTimeMillis(),
                                DateUtils.MINUTE_IN_MILLIS,
                            ).toString(),
                )
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.group_info_members),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    info.members.forEach { member ->
                        val isCreator = member.memberUuid == info.record.creatorUuid
                        Text(
                            text =
                                member.memberNickname +
                                    if (isCreator) {
                                        " " + stringResource(R.string.group_info_creator_tag)
                                    } else {
                                        ""
                                    },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                LabeledValue(
                    label = stringResource(R.string.peer_info_uuid),
                    value = info.record.id,
                    monospace = true,
                )
            }
        },
    )
}

@Composable
private fun LabeledValue(
    label: String,
    value: String,
    monospace: Boolean = false,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style =
                MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = if (monospace) FontFamily.Monospace else null,
                ),
        )
    }
}
