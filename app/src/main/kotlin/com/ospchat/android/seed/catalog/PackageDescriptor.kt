package com.ospchat.android.seed.catalog

/**
 * One downloadable artifact in the seed catalog.
 *
 * [id] must be URL-safe — it becomes the path segment in
 * `GET /download/{id}` and the on-disk directory name under
 * `filesDir/seed/`.
 */
data class PackageDescriptor(
    val id: String,
    val displayName: String,
    val platform: TargetPlatform,
    val source: PackageSource,
)
