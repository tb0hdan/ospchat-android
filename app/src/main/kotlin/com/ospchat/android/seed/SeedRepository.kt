package com.ospchat.android.seed

import android.content.Context
import android.util.Log
import com.ospchat.android.R
import com.ospchat.android.seed.catalog.PackageDescriptor
import com.ospchat.android.seed.catalog.PackageSource
import com.ospchat.android.seed.catalog.SeedCatalog
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns Seed Mode's cache, GitHub fetches, and hotspot-IP resolution.
 *
 * Stateless from the caller's perspective: [loadManifest] does the work each
 * time it's called, so the ViewModel doesn't need a Flow / observer plumbing
 * for cache-state changes — it just calls `loadManifest()` again after a
 * download completes.
 */
@Singleton
internal class SeedRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        @SeedHttpClient private val httpClient: HttpClient,
        private val githubClient: GitHubReleaseClient,
        private val cache: SeedCache,
    ) {
        /**
         * Resolves the live metadata for every entry in [SeedCatalog.DEFAULT].
         * For `GitHubRelease` sources, fetches the latest release once and
         * reuses the response for every matching descriptor; on fetch failure
         * the GitHub-sourced entries are still listed but marked
         * `downloadUrl = null` so the UI can render them as un-downloadable.
         */
        suspend fun loadManifest(): SeedManifest {
            val release = fetchReleaseOnce()
            val packages =
                SeedCatalog.DEFAULT.map { descriptor ->
                    resolveDescriptor(descriptor, release)
                }
            return SeedManifest(
                releaseTag = release?.tagName,
                fetchedAtMs = System.currentTimeMillis(),
                packages = packages,
            )
        }

        /**
         * Streams the descriptor's GitHub asset to disk, reporting progress
         * as `(bytesDownloaded, totalBytes)`. Writes to a `.part` sibling and
         * renames on success so an interrupted run never leaves a corrupt
         * cached file. Throws if the source is [PackageSource.SelfApk] —
         * the caller shouldn't ask to download the local APK.
         */
        suspend fun downloadPackage(
            info: SeedPackageInfo,
            onProgress: (Long, Long) -> Unit,
        ) {
            val url =
                requireNotNull(info.downloadUrl) {
                    "downloadPackage called for ${info.descriptor.id} with no URL"
                }
            val dest = cache.cachedFile(info.descriptor.id, info.fileName)
            val part = cache.partFile(info.descriptor.id, info.fileName)
            part.parentFile?.mkdirs()
            if (part.exists()) part.delete()
            withContext(Dispatchers.IO) {
                try {
                    httpClient.prepareGet(url).execute { response ->
                        val total = response.contentLength() ?: info.sizeBytes
                        val channel = response.bodyAsChannel()
                        val buffer = ByteArray(BUFFER_BYTES)
                        var downloaded = 0L
                        part.outputStream().use { out ->
                            while (!channel.isClosedForRead) {
                                val read = channel.readAvailable(buffer, 0, buffer.size)
                                if (read <= 0) break
                                out.write(buffer, 0, read)
                                downloaded += read
                                onProgress(downloaded, total)
                            }
                        }
                    }
                    // Some Android filesystems return false from renameTo when
                    // dest exists rather than overwriting atomically; delete
                    // first so a re-download of the same asset name succeeds.
                    if (dest.exists()) dest.delete()
                    if (!part.renameTo(dest)) {
                        part.delete()
                        error("Could not move ${part.name} into place at ${dest.absolutePath}")
                    }
                    cache.deleteStale(info.descriptor.id, info.fileName)
                } catch (t: Throwable) {
                    part.delete()
                    throw t
                }
            }
        }

        /**
         * Deletes every cached installer (and any leftover `.part` files)
         * under the seed cache tree. The bundled Android APK lives outside
         * this tree (`Context.applicationInfo.sourceDir`) and is unaffected.
         * Runs on [Dispatchers.IO] because callers invoke this from the
         * main-bound ViewModel scope.
         */
        suspend fun clearCache() {
            withContext(Dispatchers.IO) { cache.clearAll() }
        }

        /**
         * Returns the file the seed server should stream for [descriptorId]
         * plus the filename the server should advertise to the client (via
         * `Content-Disposition`). `SelfApk` resolves to the installed APK on
         * disk — which Android stores at `.../base.apk` — paired with the
         * versioned download name `ospchat-android-<version>.apk` so the user
         * doesn't receive a generic `base.apk`. `GitHubRelease` resolves to
         * the cached file with its original asset name preserved.
         */
        fun servedFileFor(descriptorId: String): ServedFile? {
            val descriptor = SeedCatalog.DEFAULT.firstOrNull { it.id == descriptorId } ?: return null
            return when (descriptor.source) {
                PackageSource.SelfApk -> {
                    val path = context.applicationInfo.sourceDir
                    val file = File(path)
                    if (file.exists()) ServedFile(file, selfApkFileName()) else null
                }

                is PackageSource.GitHubRelease -> {
                    val file = cache.anyCachedFile(descriptor.id) ?: return null
                    ServedFile(file, file.name)
                }
            }
        }

        private fun selfApkFileName(): String = "ospchat-android-${context.getString(R.string.app_version_name)}.apk"

        /**
         * Resolves the device's hotspot-side IPv4 address by enumerating
         * network interfaces. Picks the first up, non-loopback, site-local
         * IPv4 address — covers `192.168.x.x` and `10.x.x.x`, which is where
         * Android places the AP interface regardless of OEM-specific iface
         * names (`wlan1`, `ap0`, `softap0`, …). Returns null if no hotspot
         * is active.
         */
        fun hotspotIp(): String? {
            val interfaces =
                runCatching { NetworkInterface.getNetworkInterfaces()?.toList() }
                    .getOrNull() ?: return null
            for (iface in interfaces) {
                if (!iface.isUp || iface.isLoopback) continue
                for (addr in iface.inetAddresses) {
                    if (addr is Inet4Address && addr.isSiteLocalAddress) {
                        return addr.hostAddress
                    }
                }
            }
            return null
        }

        private suspend fun fetchReleaseOnce(): GitHubRelease? {
            // All current GitHubRelease descriptors point at the same
            // (owner, repo). If that ever changes, this becomes a per-repo
            // memoization map.
            val gh =
                SeedCatalog.DEFAULT
                    .map { it.source }
                    .filterIsInstance<PackageSource.GitHubRelease>()
                    .firstOrNull() ?: return null
            return githubClient
                .fetchLatestRelease(gh.owner, gh.repo)
                .getOrNull()
        }

        private fun resolveDescriptor(
            descriptor: PackageDescriptor,
            release: GitHubRelease?,
        ): SeedPackageInfo =
            when (val source = descriptor.source) {
                PackageSource.SelfApk -> {
                    val path = context.applicationInfo.sourceDir
                    val file = File(path)
                    SeedPackageInfo(
                        descriptor = descriptor,
                        fileName = selfApkFileName(),
                        sizeBytes = if (file.exists()) file.length() else 0L,
                        downloadUrl = null,
                        isCached = file.exists(),
                    )
                }

                is PackageSource.GitHubRelease -> {
                    val asset = release?.assets?.firstOrNull { source.assetPattern.matches(it.name) }
                    if (asset == null) {
                        if (release != null) {
                            Log.w(
                                TAG,
                                "Descriptor ${descriptor.id} found no asset matching " +
                                    "${source.assetPattern} in release ${release.tagName}",
                            )
                        }
                        SeedPackageInfo(
                            descriptor = descriptor,
                            fileName = "${descriptor.id}.bin",
                            sizeBytes = 0L,
                            downloadUrl = null,
                            isCached = cache.anyCachedFile(descriptor.id) != null,
                        )
                    } else {
                        SeedPackageInfo(
                            descriptor = descriptor,
                            fileName = asset.name,
                            sizeBytes = asset.size,
                            downloadUrl = asset.browserDownloadUrl,
                            isCached = cache.cachedFile(descriptor.id, asset.name).exists(),
                        )
                    }
                }
            }

        private companion object {
            const val TAG = "SeedRepository"

            /** 64 KiB — typical sweet spot for sustained file copy throughput. */
            const val BUFFER_BYTES = 64 * 1024
        }
    }
