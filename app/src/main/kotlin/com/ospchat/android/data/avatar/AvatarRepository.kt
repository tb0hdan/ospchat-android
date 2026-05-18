package com.ospchat.android.data.avatar

import android.content.Context
import android.net.Uri
import com.ospchat.shared.data.attachments.ImageCompressor
import com.ospchat.shared.data.avatar.AvatarStore
import com.ospchat.shared.data.identity.IdentityRepository
import com.ospchat.shared.data.peers.PeerInfoNotifier
import dagger.hilt.android.qualifiers.ApplicationContext
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
 *
 * Reads the URI bytes here (Android-specific) and hands a `ByteArray` to
 * the shared [ImageCompressor] / [AvatarStore], keeping the Uri dependency
 * out of `ospchat-shared`.
 */
@Singleton
class AvatarRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val store: AvatarStore,
        private val compressor: ImageCompressor,
        private val identityRepository: IdentityRepository,
        private val notifier: PeerInfoNotifier,
    ) {
        suspend fun setFromUri(uri: Uri) =
            withContext(Dispatchers.IO) {
                val sourceBytes =
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("Could not open avatar URI: $uri")
                val compressed = compressor.compress(sourceBytes, maxEdge = AVATAR_MAX_EDGE)
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
