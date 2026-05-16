package com.ospchat.android.net.dto

import kotlinx.serialization.Serializable

/** Wire schema for `GET /v1/info`. */
@Serializable
data class InfoDto(
    val uuid: String,
    val nickname: String,
    val apiVersion: String,
    /**
     * SHA-256 hex of this peer's custom avatar JPEG, or `null` if they
     * haven't set one (initials are rendered locally instead). Receivers
     * compare to their cached hash; on mismatch they pull `GET /v1/avatar`.
     */
    val avatarHash: String? = null,
)
