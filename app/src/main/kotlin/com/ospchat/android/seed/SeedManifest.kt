package com.ospchat.android.seed

import com.ospchat.android.seed.catalog.PackageDescriptor

/**
 * One row in the Seed Mode UI checklist — descriptor plus the live metadata
 * resolved from the descriptor's [com.ospchat.android.seed.catalog.PackageSource].
 *
 * For `SelfApk`: [fileName] is `"ospchat-android.apk"`, [downloadUrl] is null,
 * [isCached] is always true.
 *
 * For `GitHubRelease`: [fileName] / [sizeBytes] / [downloadUrl] come from the
 * GitHub asset record, and [isCached] reflects whether a file with that exact
 * name has already been written to [SeedCache].
 */
internal data class SeedPackageInfo(
    val descriptor: PackageDescriptor,
    val fileName: String,
    val sizeBytes: Long,
    val downloadUrl: String?,
    val isCached: Boolean,
)

internal data class SeedManifest(
    val releaseTag: String?,
    val fetchedAtMs: Long,
    val packages: List<SeedPackageInfo>,
)

/**
 * Bundle of "what's on disk" + "what to call it on the wire". Separate from
 * the on-disk filename because for `SelfApk` the installed APK lives at
 * `.../base.apk` but the server advertises a versioned download name like
 * `ospchat-android-0.2.4.apk`.
 */
internal data class ServedFile(
    val file: java.io.File,
    val downloadName: String,
)
