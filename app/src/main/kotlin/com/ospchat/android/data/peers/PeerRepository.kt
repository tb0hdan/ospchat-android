package com.ospchat.android.data.peers

import com.ospchat.android.data.discovery.DiscoveryRepository
import com.ospchat.android.data.discovery.Peer
import com.ospchat.android.data.messages.MessageDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Joins the persisted peer list (Room) with the live NSD snapshot and the
 * unread-message counts to produce [PeerRecord]s.
 *
 * The persistence side effect (writing newly-seen peers into Room) is driven
 * by [DiscoveryForegroundService][com.ospchat.android.service.DiscoveryForegroundService],
 * which calls [recordSeen] from a long-running coroutine while the service
 * is alive.
 */
@Singleton
class PeerRepository
    @Inject
    constructor(
        private val peerDao: PeerDao,
        private val messageDao: MessageDao,
        private val discoveryRepository: DiscoveryRepository,
    ) {
        fun observeAll(): Flow<List<PeerRecord>> =
            combine(
                peerDao.observeAll(),
                messageDao.observeUnreadCounts(),
                discoveryRepository.peerSnapshot,
            ) { stored, unread, live ->
                val unreadMap = unread.associate { it.peerUuid to it.count }
                stored
                    .map { entity -> entity.toRecord(live[entity.uuid], unreadMap[entity.uuid] ?: 0) }
                    .sortedWith(
                        compareByDescending<PeerRecord> { it.isOnline }
                            .thenByDescending { it.lastSeenAt }
                            .thenBy { it.nickname.lowercase() },
                    )
            }

        fun observeOne(uuid: String): Flow<PeerRecord?> =
            combine(
                peerDao.observeAll(),
                messageDao.observeUnreadCounts(),
                discoveryRepository.peerSnapshot,
            ) { stored, unread, live ->
                val entity = stored.firstOrNull { it.uuid == uuid } ?: return@combine null
                val unreadCount = unread.firstOrNull { it.peerUuid == uuid }?.count ?: 0
                entity.toRecord(live[uuid], unreadCount)
            }

        suspend fun recordSeen(peer: Peer) {
            val now = System.currentTimeMillis()
            val existing = peerDao.findByUuid(peer.uuid)
            peerDao.upsert(
                PeerEntity(
                    uuid = peer.uuid,
                    nickname = peer.nickname,
                    lastHost = peer.host,
                    lastPort = peer.port,
                    firstSeenAt = existing?.firstSeenAt ?: now,
                    lastSeenAt = now,
                    // Preserve the user's read mark across re-discovery upserts.
                    lastReadAt = existing?.lastReadAt ?: 0L,
                ),
            )
        }

        /**
         * Records that the local user has acknowledged all inbound messages
         * from [peerUuid] sent at or before [readAt]. Drives the unread
         * indicator on the peer list.
         */
        suspend fun markRead(
            peerUuid: String,
            readAt: Long,
        ) {
            peerDao.updateLastReadAt(uuid = peerUuid, lastReadAt = readAt)
        }

        private fun PeerEntity.toRecord(
            live: Peer?,
            unreadCount: Int,
        ): PeerRecord =
            PeerRecord(
                uuid = uuid,
                nickname = live?.nickname ?: nickname,
                host = live?.host ?: lastHost,
                port = live?.port ?: lastPort,
                isOnline = live != null,
                lastSeenAt = lastSeenAt,
                unreadCount = unreadCount,
            )
    }
