package com.ospchat.android.ui.peers

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
import com.ospchat.android.ui.avatar.Avatar
import com.ospchat.shared.data.peers.PeerInfo

/**
 * Material 3 [AlertDialog] showing the full Info bundle for one peer:
 * avatar, online status, current and previous addresses, nickname history,
 * first-seen timestamp, and UUID.
 */
@Composable
fun PeerInfoDialog(
    info: PeerInfo,
    onDismiss: () -> Unit,
) {
    val currentAddress = "${info.record.host}:${info.record.port}"
    val previousAddresses =
        info.addresses
            .map { "${it.host}:${it.port}" }
            .filter { it != currentAddress }
    val previousNicknames =
        info.nicknames
            .map { it.nickname }
            .filter { it != info.record.nickname }

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
                    Text(
                        text = info.record.nickname,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text =
                            if (info.record.isOnline) {
                                stringResource(R.string.peer_info_online)
                            } else {
                                stringResource(
                                    R.string.peer_info_last_seen,
                                    DateUtils
                                        .getRelativeTimeSpanString(
                                            info.record.lastSeenAt,
                                            System.currentTimeMillis(),
                                            DateUtils.MINUTE_IN_MILLIS,
                                        ).toString(),
                                )
                            },
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
                    label = stringResource(R.string.peer_info_current_address),
                    value = currentAddress,
                )
                if (previousAddresses.isNotEmpty()) {
                    HistorySection(
                        label = stringResource(R.string.peer_info_previous_addresses),
                        items = previousAddresses,
                    )
                }
                if (previousNicknames.isNotEmpty()) {
                    HistorySection(
                        label = stringResource(R.string.peer_info_nickname_history),
                        items = previousNicknames,
                    )
                }
                LabeledValue(
                    label = stringResource(R.string.peer_info_first_seen),
                    value =
                        DateUtils
                            .getRelativeTimeSpanString(
                                info.record.firstSeenAt,
                                System.currentTimeMillis(),
                                DateUtils.MINUTE_IN_MILLIS,
                            ).toString(),
                )
                LabeledValue(
                    label = stringResource(R.string.peer_info_uuid),
                    value = info.record.uuid,
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

@Composable
private fun HistorySection(
    label: String,
    items: List<String>,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        items.forEachIndexed { index, value ->
            if (index > 0) Spacer(modifier = Modifier.size(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
