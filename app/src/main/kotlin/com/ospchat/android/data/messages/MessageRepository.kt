package com.ospchat.android.data.messages

import android.net.Uri
import android.util.Log
import com.ospchat.android.data.attachments.AttachmentCompressor
import com.ospchat.android.data.attachments.AttachmentStore
import com.ospchat.android.data.discovery.Peer
import com.ospchat.android.data.identity.IdentityRepository
import com.ospchat.android.data.peers.PeerDao
import com.ospchat.android.net.client.MessageClient
import com.ospchat.android.net.dto.AttachmentDto
import com.ospchat.android.net.dto.IncomingMessageDto
import com.ospchat.android.notifications.MessageNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageRepository
    @Inject
    constructor(
        private val messageDao: MessageDao,
        private val peerDao: PeerDao,
        private val client: MessageClient,
        private val identityRepository: IdentityRepository,
        private val notifier: MessageNotifier,
        private val attachmentStore: AttachmentStore,
        private val attachmentCompressor: AttachmentCompressor,
    ) {
        // Long-lived scope for fire-and-forget attachment downloads. Survives
        // the foreground service being recreated.
        private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun messagesFor(peerUuid: String): Flow<List<Message>> =
            messageDao.observeByPeer(peerUuid).map { rows -> rows.map(MessageEntity::toDomain) }

        suspend fun send(
            peer: Peer,
            body: String,
        ): Result<Unit> = send(peer = peer, body = body, attachmentUri = null)

        /**
         * Sends a message with an optional image attachment. The picked URI is
         * compressed (max edge 1920 px, JPEG q85) and stored in
         * [AttachmentStore] before the metadata is POSTed; the receiver's
         * HTTP server will then `GET /v1/attachments/{id}` to pull the bytes.
         */
        suspend fun send(
            peer: Peer,
            body: String,
            attachmentUri: Uri?,
        ): Result<Unit> {
            val selfUuid = identityRepository.ensureUuid()
            val selfNickname = identityRepository.nicknameFlow.first().orEmpty()
            val messageId = UUID.randomUUID().toString()

            // The compressor and file write are CPU- and I/O-bound; running
            // them on the caller's dispatcher (viewModelScope's Main by
            // default) blocks the UI thread and can OOM on large photos.
            // A `Throwable` catch covers `OutOfMemoryError` from Bitmap
            // decode/compress so a bad image never takes the app down.
            val attachment: Attachment? =
                attachmentUri?.let { uri ->
                    try {
                        withContext(Dispatchers.IO) {
                            val compressed = attachmentCompressor.compress(uri)
                            val file = attachmentStore.writeBytes(messageId, compressed.bytes)
                            Attachment(
                                mimeType = compressed.mimeType,
                                sizeBytes = file.length(),
                                width = compressed.width,
                                height = compressed.height,
                                localPath = file.absolutePath,
                            )
                        }
                    } catch (t: Throwable) {
                        Log.e(TAG, "Failed to process attachment", t)
                        return Result.failure(t)
                    }
                }

            val message =
                Message(
                    id = messageId,
                    peerUuid = peer.uuid,
                    fromUuid = selfUuid,
                    fromNickname = selfNickname,
                    body = body,
                    sentAt = System.currentTimeMillis(),
                    direction = Message.Direction.OUT,
                    status = Message.Status.SENDING,
                    attachment = attachment,
                )
            messageDao.insert(message.toEntity())
            return postAndPersistStatus(messageId, peer, message.toIncomingDto())
        }

        suspend fun retry(messageId: String): Result<Unit> {
            val entity =
                messageDao.findById(messageId)
                    ?: return Result.failure(IllegalArgumentException("unknown message"))
            if (entity.status != Message.Status.FAILED.name) return Result.success(Unit)
            val peerEntity =
                peerDao.findByUuid(entity.peerUuid)
                    ?: return Result.failure(IllegalStateException("unknown peer"))
            val peer =
                Peer(
                    uuid = peerEntity.uuid,
                    nickname = peerEntity.nickname,
                    host = peerEntity.lastHost,
                    port = peerEntity.lastPort,
                )
            messageDao.updateStatus(id = messageId, status = Message.Status.SENDING.name)
            return postAndPersistStatus(messageId, peer, entity.toDomain().toIncomingDto())
        }

        suspend fun receive(
            fromPeer: Peer,
            dto: IncomingMessageDto,
        ) {
            val attachment =
                dto.attachment?.let {
                    Attachment(
                        mimeType = it.mimeType,
                        sizeBytes = it.sizeBytes,
                        width = it.width,
                        height = it.height,
                        localPath = null,
                    )
                }
            val message =
                Message(
                    id = dto.id,
                    peerUuid = fromPeer.uuid,
                    fromUuid = dto.fromUuid,
                    fromNickname = dto.fromNickname,
                    body = dto.body,
                    sentAt = dto.sentAt,
                    direction = Message.Direction.IN,
                    status = Message.Status.DELIVERED,
                    attachment = attachment,
                )
            messageDao.insert(message.toEntity())
            notifier.notifyIncoming(fromPeer, message)
            if (attachment != null) {
                // Fire-and-forget: the UI will swap the placeholder for the
                // image once the file lands and Room re-emits.
                backgroundScope.launch { downloadAttachment(fromPeer, dto.id) }
            }
        }

        private suspend fun downloadAttachment(
            fromPeer: Peer,
            messageId: String,
        ) {
            runCatching {
                client.fetchAttachment(fromPeer, messageId) { stream ->
                    attachmentStore.write(messageId, stream)
                }
                messageDao.updateAttachmentLocalPath(
                    id = messageId,
                    localPath = attachmentStore.fileFor(messageId).absolutePath,
                )
            }.onFailure { Log.w(TAG, "Attachment download failed for $messageId", it) }
        }

        suspend fun sendReadReceipt(
            toPeer: Peer,
            upToSentAt: Long,
        ): Result<Unit> {
            val selfUuid = identityRepository.ensureUuid()
            val dto =
                com.ospchat.android.net.dto
                    .ReadReceiptDto(fromUuid = selfUuid, upToSentAt = upToSentAt)
            return runCatching { client.sendReadReceipt(toPeer, dto) }
        }

        private suspend fun postAndPersistStatus(
            id: String,
            peer: Peer,
            dto: IncomingMessageDto,
        ): Result<Unit> {
            val result = runCatching { client.send(peer, dto) }
            messageDao.updateStatus(
                id = id,
                status = if (result.isSuccess) Message.Status.DELIVERED.name else Message.Status.FAILED.name,
            )
            return result
        }

        private companion object {
            const val TAG = "MessageRepository"
        }

        private fun Message.toIncomingDto(): IncomingMessageDto =
            IncomingMessageDto(
                id = id,
                fromUuid = fromUuid,
                fromNickname = fromNickname,
                body = body,
                sentAt = sentAt,
                attachment =
                    attachment?.let {
                        AttachmentDto(
                            mimeType = it.mimeType,
                            sizeBytes = it.sizeBytes,
                            width = it.width,
                            height = it.height,
                        )
                    },
            )
    }
