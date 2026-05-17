package com.ospchat.android.ui.groups

import com.ospchat.android.data.groups.GroupKind
import com.ospchat.android.data.groups.GroupRecord
import com.ospchat.android.ui.avatar.AvatarModel
import com.ospchat.android.ui.avatar.computeInitials

/**
 * Initials-style avatar derived from the group name. The seed prefix forces
 * broadcast channels into a different hue from chats with identical names,
 * so a "Family" chat and a "Family" broadcast read as distinct rows.
 */
internal fun GroupRecord.toAvatarModel(): AvatarModel {
    val seedPrefix = if (kind == GroupKind.BROADCAST) "b:" else "g:"
    return AvatarModel.Initials(
        letters = computeInitials(name),
        seed = "$seedPrefix$id",
    )
}
