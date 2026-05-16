package com.ospchat.android.data.attachments

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the on-disk location for message attachment binaries. Files live in
 * `filesDir/attachments/<messageId>.bin` so they're app-private, survive
 * across restarts, and are removed by Android only when the app is
 * uninstalled or its data is cleared.
 */
@Singleton
class AttachmentStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val root: File by lazy {
            File(context.filesDir, "attachments").also { it.mkdirs() }
        }

        fun fileFor(messageId: String): File = File(root, "$messageId.bin")

        fun exists(messageId: String): Boolean = fileFor(messageId).isFile

        /** Streams [input] into the file for [messageId]; returns the file. */
        fun write(
            messageId: String,
            input: InputStream,
        ): File {
            val target = fileFor(messageId)
            target.outputStream().use { out -> input.copyTo(out) }
            return target
        }

        fun writeBytes(
            messageId: String,
            bytes: ByteArray,
        ): File {
            val target = fileFor(messageId)
            target.writeBytes(bytes)
            return target
        }
    }
