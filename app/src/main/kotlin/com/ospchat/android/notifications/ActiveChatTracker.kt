package com.ospchat.android.notifications

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-wide handle to the UUID of the peer the user is currently looking
 * at (their chat screen is in the foreground). Used by [MessageNotifier] to
 * suppress notifications for the conversation already on screen.
 *
 * Updated by [com.ospchat.android.ui.chat.ChatViewModel] via lifecycle
 * callbacks; read by the notifier from a Ktor coroutine, hence `@Volatile`.
 */
@Singleton
class ActiveChatTracker
    @Inject
    constructor() {
        @Volatile var activePeerUuid: String? = null
    }
