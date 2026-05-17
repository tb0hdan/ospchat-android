package com.ospchat.android.data.peers

import com.ospchat.android.data.discovery.DiscoveryRepository
import com.ospchat.android.data.discovery.Peer
import com.ospchat.android.data.messages.MessageDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
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
 *
 * History rows for the Info dialog (`peer_addresses`, `peer_nicknames`) are
 * written by [PeerHistoryRecorder] from inside [recordSeen] so all peer
 * reconciliation continues to flow through a single entry point.
 */
@Singleton
class PeerRepository
    @Inject
    constructor(
        private val peerDao: PeerDao,
        private val messageDao: MessageDao,
        private val historyDao: PeerHistoryDao,
        private val historyRecorder: PeerHistoryRecorder,
        private val discoveryRepository: DiscoveryRepository,
    ) {
        /**
         * Every persisted peer joined with live state. Sorted online-first,
         * then by `lastSeenAt` descending, then alphabetically.
         *
         * Consumers usually want the filtered [observeContacts] /
         * [observeVisiblePeers] variants; this flow is retained for callers
         * (e.g. ChatViewModel) that need a flat peer list.
         */
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

        /**
         * Saved contacts (online or offline). Sorted online-first, then
         * alphabetically — last-seen ordering is less useful here than for
         * transient peers.
         */
        fun observeContacts(): Flow<List<PeerRecord>> =
            observeAll().map { all ->
                all
                    .filter { it.isContact }
                    .sortedWith(
                        compareByDescending<PeerRecord> { it.isOnline }
                            .thenBy { it.nickname.lowercase() },
                    )
            }

        /**
         * Currently-discoverable peers that are NOT saved contacts. A peer
         * disappears from this list as soon as it leaves the NSD snapshot;
         * saved contacts never appear here regardless of their online state.
         */
        fun observeVisiblePeers(): Flow<List<PeerRecord>> =
            observeAll().map { all ->
                all
                    .filter { !it.isContact && it.isOnline }
                    .sortedBy { it.nickname.lowercase() }
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

        /**
         * Full Info-dialog payload for [uuid]: the live [PeerRecord] joined
         * with the per-peer address and nickname history tables.
         */
        fun observeInfo(uuid: String): Flow<PeerInfo?> =
            combine(
                observeOne(uuid),
                historyDao.observeAddresses(uuid),
                historyDao.observeNicknames(uuid),
            ) { record, addresses, nicknames ->
                if (record == null) return@combine null
                PeerInfo(
                    record = record,
                    addresses = addresses,
                    nicknames = nicknames,
                )
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
                    // Preserve any cached avatar — PeerAvatarSync owns these.
                    avatarHash = existing?.avatarHash,
                    avatarLocalPath = existing?.avatarLocalPath,
                    // Preserve the contact promotion across re-discovery.
                    isContact = existing?.isContact ?: false,
                ),
            )
            historyRecorder.record(peer = peer, now = now)
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

        /** Toggle the contact promotion for [uuid]. */
        suspend fun setIsContact(
            uuid: String,
            isContact: Boolean,
        ) {
            peerDao.setIsContact(uuid = uuid, isContact = isContact)
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
                firstSeenAt = firstSeenAt,
                lastSeenAt = lastSeenAt,
                unreadCount = unreadCount,
                avatarLocalPath = avatarLocalPath,
                isContact = isContact,
            )
    }
