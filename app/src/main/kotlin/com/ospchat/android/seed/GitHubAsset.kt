package com.ospchat.android.seed

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Slice of the GitHub Releases API response we care about. Other fields
 * (author, draft, prerelease, body, etc.) are ignored.
 */
@Serializable
internal data class GitHubAsset(
    @SerialName("name") val name: String,
    @SerialName("size") val size: Long,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
    @SerialName("content_type") val contentType: String = "",
)

@Serializable
internal data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    @SerialName("assets") val assets: List<GitHubAsset> = emptyList(),
)
