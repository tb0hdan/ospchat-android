package com.ospchat.android.data.discovery

data class Peer(
    val uuid: String,
    val nickname: String,
    val host: String,
    val port: Int,
)
