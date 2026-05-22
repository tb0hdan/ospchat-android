package com.ospchat.android.seed

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json

/**
 * Tiny wrapper around `GET /repos/{owner}/{repo}/releases/latest`. Uses
 * unauthenticated requests, which are rate-limited to 60 / hour / IP by
 * GitHub — sufficient for a user-driven Refresh button.
 */
internal class GitHubReleaseClient(
    private val client: HttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchLatestRelease(
        owner: String,
        repo: String,
    ): Result<GitHubRelease> =
        runCatching {
            val response: HttpResponse =
                client.get("https://api.github.com/repos/$owner/$repo/releases/latest") {
                    accept(ContentType("application", "vnd.github+json"))
                    header("X-GitHub-Api-Version", "2022-11-28")
                }
            if (!response.status.isSuccess()) {
                error("GitHub responded ${response.status.value} for $owner/$repo")
            }
            // Parse manually rather than relying on the client's
            // ContentNegotiation plugin so we don't depend on its install
            // order — the seed HTTP client may be created without it.
            json.decodeFromString(GitHubRelease.serializer(), response.bodyAsText())
        }.onFailure { Log.w(TAG, "fetchLatestRelease($owner/$repo) failed", it) }

    private companion object {
        const val TAG = "GitHubReleaseClient"
    }
}
