package com.ospchat.android.seed.catalog

/**
 * Platform tag for a [PackageDescriptor]. Drives grouping in the Seed Mode
 * UI and the `Content-Type` the seed server sends.
 */
enum class TargetPlatform(
    val displayName: String,
    val mimeType: String,
) {
    Android("Android", "application/vnd.android.package-archive"),
    Windows("Windows", "application/x-msi"),
    MacOsArm64("macOS (Apple Silicon)", "application/x-apple-diskimage"),
    MacOsIntel("macOS (Intel)", "application/x-apple-diskimage"),
    LinuxDeb("Linux (.deb)", "application/vnd.debian.binary-package"),
}
