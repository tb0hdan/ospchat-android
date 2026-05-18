package com.ospchat.android.ui.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ospchat.android.R

/**
 * Reusable picker for adding or removing members. The candidate list and
 * the action label are caller-provided so the same component covers both
 * directions (contacts-not-in-group for Add, members-other-than-creator
 * for Remove).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MembershipPickerSheet(
    title: String,
    actionLabel: String,
    candidates: List<MembershipCandidate>,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val selected = remember { mutableStateOf(setOf<String>()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleLarge)

            if (candidates.isEmpty()) {
                Text(
                    text = stringResource(R.string.group_picker_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(candidates, key = { it.uuid }) { candidate ->
                        CandidateRow(
                            candidate = candidate,
                            checked = candidate.uuid in selected.value,
                            onCheckedChange = { isChecked ->
                                selected.value =
                                    if (isChecked) {
                                        selected.value + candidate.uuid
                                    } else {
                                        selected.value - candidate.uuid
                                    }
                            },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.size(4.dp))
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                Button(
                    onClick = { onConfirm(selected.value.toList()) },
                    enabled = selected.value.isNotEmpty(),
                ) {
                    Text(actionLabel)
                }
            }
            Spacer(modifier = Modifier.size(8.dp))
        }
    }
}

/**
 * Lightweight candidate model so this picker can be fed by both contact
 * rows and member entities without forcing a [PeerRecord][com.ospchat.shared.data.peers.PeerRecord]
 * shape.
 */
data class MembershipCandidate(
    val uuid: String,
    val nickname: String,
    val subtitle: String,
)

@Composable
private fun CandidateRow(
    candidate: MembershipCandidate,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .selectable(selected = checked, onClick = { onCheckedChange(!checked) })
                .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.padding(end = 8.dp),
        )
        Column {
            Text(text = candidate.nickname, style = MaterialTheme.typography.bodyMedium)
            if (candidate.subtitle.isNotEmpty()) {
                Text(
                    text = candidate.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
