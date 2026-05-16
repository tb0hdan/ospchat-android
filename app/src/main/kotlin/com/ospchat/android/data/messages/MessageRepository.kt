package com.ospchat.android.data.messages

import com.ospchat.android.data.discovery.Peer
import com.ospchat.android.data.identity.IdentityRepository
import com.ospchat.android.data.peers.PeerDao
import com.ospchat.android.net.client.MessageClient
import com.ospchat.android.net.dto.IncomingMessageDto
import com.ospchat.android.net.dto.ReadReceiptDto
import com.ospchat.android.notifications.MessageNotifier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
    ) {
        fun messagesFor(peerUuid: String): Flow<List<Message>> =
            messageDao.observeByPeer(peerUuid).map { rows -> rows.map(MessageEntity::toDomain) }

        suspend fun send(
            peer: Peer,
            body: String,
        ): Result<Unit> {
            val selfUuid = identityRepository.ensureUuid()
            val selfNickname = identityRepository.nicknameFlow.first().orEmpty()
            val message =
                Message(
                    id = UUID.randomUUID().toString(),
                    peerUuid = peer.uuid,
                    fromUuid = selfUuid,
                    fromNickname = selfNickname,
                    body = body,
                    sentAt = System.currentTimeMillis(),
                    direction = Message.Direction.OUT,
                    status = Message.Status.SENDING,
                )
            messageDao.insert(message.toEntity())
            return postAndPersistStatus(message.id, peer, message.toIncomingDto())
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
                )
            messageDao.insert(message.toEntity())
            notifier.notifyIncoming(fromPeer, message)
        }

        suspend fun sendReadReceipt(
            toPeer: Peer,
            upToSentAt: Long,
        ): Result<Unit> {
            val selfUuid = identityRepository.ensureUuid()
            val dto = ReadReceiptDto(fromUuid = selfUuid, upToSentAt = upToSentAt)
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

        private fun Message.toIncomingDto(): IncomingMessageDto =
            IncomingMessageDto(
                id = id,
                fromUuid = fromUuid,
                fromNickname = fromNickname,
                body = body,
                sentAt = sentAt,
            )
    }
