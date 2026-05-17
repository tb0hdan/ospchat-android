package com.ospchat.android.data.peers

import com.ospchat.android.data.discovery.Peer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes per-peer history rows (`peer_addresses`, `peer_nicknames`) whenever
 * [PeerRepository.recordSeen] observes a peer in the NSD snapshot.
 *
 * For each (uuid, host:port) or (uuid, nickname) tuple, the first observation
 * inserts a row; subsequent observations advance `last_seen_at`. SQLite's
 * `INSERT OR IGNORE` on the composite primary key keeps the `first_seen_at`
 * stable — we never want to overwrite the original sighting timestamp.
 *
 * After each record, both tables are pruned to the [KEEP_LAST_N] most-recent
 * rows per peer so storage is bounded against pathological churn (e.g. a peer
 * cycling through many DHCP leases or renaming repeatedly).
 */
@Singleton
class PeerHistoryRecorder
    @Inject
    constructor(
        private val historyDao: PeerHistoryDao,
    ) {
        suspend fun record(
            peer: Peer,
            now: Long,
        ) {
            historyDao.insertAddressIfAbsent(
                PeerAddressEntity(
                    uuid = peer.uuid,
                    host = peer.host,
                    port = peer.port,
                    firstSeenAt = now,
                    lastSeenAt = now,
                ),
            )
            historyDao.touchAddress(
                uuid = peer.uuid,
                host = peer.host,
                port = peer.port,
                lastSeenAt = now,
            )
            historyDao.insertNicknameIfAbsent(
                PeerNicknameEntity(
                    uuid = peer.uuid,
                    nickname = peer.nickname,
                    firstSeenAt = now,
                    lastSeenAt = now,
                ),
            )
            historyDao.touchNickname(
                uuid = peer.uuid,
                nickname = peer.nickname,
                lastSeenAt = now,
            )
            historyDao.pruneAddresses(uuid = peer.uuid, keep = KEEP_LAST_N)
            historyDao.pruneNicknames(uuid = peer.uuid, keep = KEEP_LAST_N)
        }

        companion object {
            /**
             * Maximum number of history rows to retain per peer per table.
             * The Info dialog never surfaces more than a handful, and tighter
             * caps keep pathological churn bounded.
             */
            const val KEEP_LAST_N = 10
        }
    }
