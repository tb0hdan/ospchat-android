package com.ospchat.android.net.dto

import kotlinx.serialization.Serializable

/** Wire schema for `GET /v1/info`. */
@Serializable
data class InfoDto(
    val uuid: String,
    val nickname: String,
    val apiVersion: String,
)
