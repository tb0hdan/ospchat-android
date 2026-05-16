package com.ospchat.android.data.avatar

import android.net.Uri
import com.ospchat.android.data.attachments.AttachmentCompressor
import com.ospchat.android.data.identity.IdentityRepository
import com.ospchat.android.data.peers.PeerInfoNotifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Self-avatar lifecycle: compress a picked image to a square-friendly JPEG,
 * persist it on disk (under a hash-suffixed filename), and store its
 * SHA-256 hash so peers can detect changes via `GET /v1/info` and pull
 * fresh bytes via `GET /v1/avatar`.
 */
@Singleton
class AvatarRepository
    @Inject
    constructor(
        private val store: AvatarStore,
        private val compressor: AttachmentCompressor,
        private val identityRepository: IdentityRepository,
        private val notifier: PeerInfoNotifier,
    ) {
        suspend fun setFromUri(uri: Uri) =
            withContext(Dispatchers.IO) {
                val compressed = compressor.compress(uri, maxEdge = AVATAR_MAX_EDGE)
                val hash = sha256Hex(compressed.bytes)
                store.writeSelf(compressed.bytes, hash)
                store.cleanupSelfExcept(keepHash = hash)
                identityRepository.setAvatarHash(hash)
                notifier.broadcastRefresh()
            }

        suspend fun clear() =
            withContext(Dispatchers.IO) {
                store.cleanupSelfExcept(keepHash = null)
                identityRepository.setAvatarHash(null)
                notifier.broadcastRefresh()
            }

        private fun sha256Hex(bytes: ByteArray): String =
            MessageDigest
                .getInstance("SHA-256")
                .digest(bytes)
                .joinToString("") { "%02x".format(it) }

        private companion object {
            const val AVATAR_MAX_EDGE = 256
        }
    }
