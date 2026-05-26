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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ospchat.android.R
import com.ospchat.shared.data.groups.GroupKind
import com.ospchat.shared.data.peers.PeerRecord

private const val MAX_NAME_LEN = 60

/**
 * Bottom sheet for creating a new group. Lets the user enter a name, pick a
 * kind (chat / broadcast), and toggle which of their saved contacts to
 * include as initial members. The creator is implicit; not shown in the
 * list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewGroupSheet(
    contacts: List<PeerRecord>,
    onCreate: (name: String, kind: GroupKind, memberUuids: List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(GroupKind.CHAT) }
    val selected = remember { mutableStateOf(setOf<String>()) }

    val trimmedName = name.trim()
    val canCreate = trimmedName.isNotEmpty() && selected.value.isNotEmpty()

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
            Text(
                text = stringResource(R.string.group_new_title),
                style = MaterialTheme.typography.titleLarge,
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(MAX_NAME_LEN) },
                label = { Text(stringResource(R.string.group_new_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            KindSelector(kind = kind, onKindChange = { kind = it })

            HorizontalDivider()

            Text(
                text = stringResource(R.string.group_new_members_label),
                style = MaterialTheme.typography.titleSmall,
            )
            if (contacts.isEmpty()) {
                Text(
                    text = stringResource(R.string.group_new_no_contacts),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 260.dp)) {
                    items(contacts, key = { it.uuid }) { contact ->
                        ContactPickerRow(
                            contact = contact,
                            checked = contact.uuid in selected.value,
                            onCheckedChange = { isChecked ->
                                selected.value =
                                    if (isChecked) {
                                        selected.value + contact.uuid
                                    } else {
                                        selected.value - contact.uuid
                                    }
                            },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.size(4.dp))
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                Button(
                    onClick = {
                        onCreate(trimmedName, kind, selected.value.toList())
                    },
                    enabled = canCreate,
                ) {
                    Text(stringResource(R.string.group_new_create_button))
                }
            }
            Spacer(modifier = Modifier.size(8.dp))
        }
    }
}

@Composable
private fun KindSelector(
    kind: GroupKind,
    onKindChange: (GroupKind) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        KindRadio(
            label = stringResource(R.string.group_kind_chat),
            description = stringResource(R.string.group_kind_chat_hint),
            selected = kind == GroupKind.CHAT,
            onSelect = { onKindChange(GroupKind.CHAT) },
            modifier = Modifier.weight(1f),
        )
        KindRadio(
            label = stringResource(R.string.group_kind_broadcast),
            description = stringResource(R.string.group_kind_broadcast_hint),
            selected = kind == GroupKind.BROADCAST,
            onSelect = { onKindChange(GroupKind.BROADCAST) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun KindRadio(
    label: String,
    description: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .selectable(selected = selected, onClick = onSelect)
                .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Spacer(modifier = Modifier.size(4.dp))
        Column {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun ContactPickerRow(
    contact: PeerRecord,
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
            Text(text = contact.nickname, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = if (contact.isOnline) contact.displayAddress() else "offline",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
