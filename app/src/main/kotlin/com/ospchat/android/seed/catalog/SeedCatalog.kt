package com.ospchat.android.seed.catalog

/**
 * Hard-coded v1 catalog. The seed server, repository, and UI all iterate
 * [DEFAULT] — adding a new package is a single entry here.
 *
 * Asset patterns target the `tb0hdan/ospchat-desktop` release artifact names
 * (see https://github.com/tb0hdan/ospchat-desktop/releases): e.g.
 * `OSPChat-1.2.2.msi`, `OSPChat-arm64-1.2.2.dmg`, `OSPChat-x86_64-1.2.2.dmg`,
 * `ospchat_1.2.2_amd64.deb`.
 */
object SeedCatalog {
    val DEFAULT: List<PackageDescriptor> =
        listOf(
            PackageDescriptor(
                id = "android",
                displayName = "OSPChat for Android",
                platform = TargetPlatform.Android,
                source = PackageSource.SelfApk,
            ),
            PackageDescriptor(
                id = "windows",
                displayName = "OSPChat for Windows",
                platform = TargetPlatform.Windows,
                source =
                    PackageSource.GitHubRelease(
                        owner = "tb0hdan",
                        repo = "ospchat-desktop",
                        assetPattern = Regex(""".*\.msi$""", RegexOption.IGNORE_CASE),
                    ),
            ),
            PackageDescriptor(
                id = "macos-arm64",
                displayName = "OSPChat for macOS (Apple Silicon)",
                platform = TargetPlatform.MacOsArm64,
                source =
                    PackageSource.GitHubRelease(
                        owner = "tb0hdan",
                        repo = "ospchat-desktop",
                        assetPattern = Regex(""".*-arm64-.*\.dmg$""", RegexOption.IGNORE_CASE),
                    ),
            ),
            PackageDescriptor(
                id = "macos-x86_64",
                displayName = "OSPChat for macOS (Intel)",
                platform = TargetPlatform.MacOsIntel,
                source =
                    PackageSource.GitHubRelease(
                        owner = "tb0hdan",
                        repo = "ospchat-desktop",
                        assetPattern = Regex(""".*-x86_64-.*\.dmg$""", RegexOption.IGNORE_CASE),
                    ),
            ),
            PackageDescriptor(
                id = "linux-deb",
                displayName = "OSPChat for Linux (.deb)",
                platform = TargetPlatform.LinuxDeb,
                source =
                    PackageSource.GitHubRelease(
                        owner = "tb0hdan",
                        repo = "ospchat-desktop",
                        assetPattern = Regex(""".*\.deb$""", RegexOption.IGNORE_CASE),
                    ),
            ),
        )
}
