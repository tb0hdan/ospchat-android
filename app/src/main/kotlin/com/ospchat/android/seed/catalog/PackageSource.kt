package com.ospchat.android.seed.catalog

/**
 * Where to obtain the bytes for a [PackageDescriptor]. The sealed hierarchy
 * is the single extension point: adding a new source type is one subclass
 * here plus the matching branch in `SeedRepository.downloadPackage` and
 * `SeedRepository.servedFileFor`.
 */
sealed class PackageSource {
    /**
     * The phone's own installed APK, served from
     * `Context.applicationInfo.sourceDir`. Always available, never requires
     * a network fetch, never written to the cache.
     */
    data object SelfApk : PackageSource()

    /**
     * A release asset from a public GitHub repository's latest release.
     *
     * [assetPattern] is matched against each asset's `name`; first match wins.
     */
    data class GitHubRelease(
        val owner: String,
        val repo: String,
        val assetPattern: Regex,
    ) : PackageSource()
}
