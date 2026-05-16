package com.ospchat.android.data.avatar

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-disk location for user avatars. Files live under `filesDir/avatar/`.
 *
 * Filenames embed the SHA-256 hash of the JPEG bytes
 * (`self-<hash>.jpg`, `peer-<uuid>-<hash>.jpg`). This makes a content change
 * surface as a *path* change — important because Coil keys its in-memory
 * cache on the file path, and a stable path would let stale bitmaps linger
 * even after the disk bytes are overwritten.
 */
@Singleton
class AvatarStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val root: File by lazy {
            File(context.filesDir, "avatar").also { it.mkdirs() }
        }

        fun selfFile(hash: String): File = File(root, "self-$hash.jpg")

        fun peerFile(
            uuid: String,
            hash: String,
        ): File = File(root, "peer-$uuid-$hash.jpg")

        fun writeSelf(
            bytes: ByteArray,
            hash: String,
        ): File = selfFile(hash).apply { writeBytes(bytes) }

        fun writePeer(
            uuid: String,
            hash: String,
            input: InputStream,
        ): File {
            val target = peerFile(uuid, hash)
            target.outputStream().use { out -> input.copyTo(out) }
            return target
        }

        /** Delete any local self-avatar files except the one for [keepHash] (if any). */
        fun cleanupSelfExcept(keepHash: String?) {
            root.listFiles { f -> f.name.startsWith("self-") }?.forEach { file ->
                val keep = keepHash != null && file.name == "self-$keepHash.jpg"
                if (!keep) file.delete()
            }
        }

        /** Delete any cached avatar files for [uuid] except the one for [keepHash] (if any). */
        fun cleanupPeerExcept(
            uuid: String,
            keepHash: String?,
        ) {
            root.listFiles { f -> f.name.startsWith("peer-$uuid-") }?.forEach { file ->
                val keep = keepHash != null && file.name == "peer-$uuid-$keepHash.jpg"
                if (!keep) file.delete()
            }
        }
    }
