package com.ospchat.android.data.reactions

import com.ospchat.android.data.discovery.Peer
import com.ospchat.android.data.identity.IdentityRepository
import com.ospchat.android.net.client.MessageClient
import com.ospchat.android.net.dto.ReactionDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReactionRepository
    @Inject
    constructor(
        private val dao: ReactionDao,
        private val client: MessageClient,
        private val identityRepository: IdentityRepository,
    ) {
        fun reactionsForPeer(peerUuid: String): Flow<List<Reaction>> =
            dao.observeForPeer(peerUuid)
                .onEach { rows ->
                    android.util.Log.d(
                        "ReactionRepo",
                        "DAO emit peerUuid=$peerUuid size=${rows.size} ids=${rows.map { it.messageId.take(8) + ":" + it.emoji }}",
                    )
                }
                .map { rows -> rows.map(ReactionEntity::toDomain) }

        /**
         * Local user reacts on [messageId] with [emoji], or removes their
         * reaction entirely when [emoji] is `null`. Persists locally and
         * fires the wire-side update to [peer]. Network failures are not
         * retried at this layer — the next peer rediscovery picks up
         * divergence via newcomer-sync (or, eventually, a periodic
         * reconciliation step).
         */
        suspend fun react(
            peer: Peer,
            messageId: String,
            emoji: String?,
        ): Result<Unit> {
            val selfUuid = identityRepository.ensureUuid()
            val selfNickname = identityRepository.nicknameFlow.first().orEmpty()
            val reactedAt = System.currentTimeMillis()

            if (emoji == null) {
                dao.delete(messageId = messageId, fromUuid = selfUuid)
            } else {
                dao.upsert(
                    ReactionEntity(
                        messageId = messageId,
                        fromUuid = selfUuid,
                        fromNickname = selfNickname,
                        emoji = emoji,
                        reactedAt = reactedAt,
                    ),
                )
            }

            val dto =
                ReactionDto(
                    messageId = messageId,
                    fromUuid = selfUuid,
                    fromNickname = selfNickname,
                    emoji = emoji,
                    reactedAt = reactedAt,
                )
            return runCatching { client.sendReaction(peer, dto) }
        }

        /** Persist an inbound reaction from the peer end of the wire. */
        suspend fun applyReaction(dto: ReactionDto) {
            if (dto.emoji == null) {
                dao.delete(messageId = dto.messageId, fromUuid = dto.fromUuid)
            } else {
                dao.upsert(
                    ReactionEntity(
                        messageId = dto.messageId,
                        fromUuid = dto.fromUuid,
                        fromNickname = dto.fromNickname,
                        emoji = dto.emoji,
                        reactedAt = dto.reactedAt,
                    ),
                )
            }
        }
    }
