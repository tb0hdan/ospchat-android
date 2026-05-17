package com.ospchat.android.data.peers

/**
 * Heavy peer detail bundle for the Info dialog. Loaded on demand via
 * [PeerRepository.observeInfo] — list rendering uses [PeerRecord] alone and
 * never pays for the history-table joins.
 */
data class PeerInfo(
    val record: PeerRecord,
    val addresses: List<PeerAddressEntity>,
    val nicknames: List<PeerNicknameEntity>,
)
