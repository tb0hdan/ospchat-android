package com.ospchat.android.data.groups

/**
 * What flavour of group this is. Stored as the enum name in the `groups.kind`
 * column and on the wire as the same string.
 *
 * - [CHAT] — any member can post.
 * - [BROADCAST] — only the creator can post; everyone else is read-only.
 *   Inbound broadcasts from non-creators are rejected by the receiver.
 */
enum class GroupKind {
    CHAT,
    BROADCAST,
}
