package com.ospchat.android.seed

import java.io.File

/**
 * On-disk layout for seed-cached installers:
 *
 *   <filesDir>/seed/<descriptor-id>/<asset-filename>
 *
 * One directory per [com.ospchat.android.seed.catalog.PackageDescriptor.id].
 * Atomic writes use a `.part` sibling that is renamed onto the final path
 * after the stream completes, so a killed download never leaves a
 * half-written file masquerading as cached.
 */
internal class SeedCache(
    private val parentDir: File,
) {
    fun rootDir(): File = File(parentDir, "seed").apply { mkdirs() }

    fun packageDir(id: String): File = File(rootDir(), id).apply { mkdirs() }

    fun cachedFile(
        id: String,
        fileName: String,
    ): File = File(packageDir(id), fileName)

    fun partFile(
        id: String,
        fileName: String,
    ): File = File(packageDir(id), "$fileName.part")

    /**
     * Finds the single non-`.part` file in this descriptor's directory.
     * Returns null if the descriptor has nothing cached. Used by the server
     * to serve whatever is on disk without knowing the latest asset name —
     * cache layout is "newest known asset wins" with one file per id.
     */
    fun anyCachedFile(id: String): File? =
        packageDir(id)
            .listFiles { f -> f.isFile && !f.name.endsWith(".part") }
            ?.firstOrNull()

    /** Removes any non-`.part` file in this id's directory other than [keepName]. */
    fun deleteStale(
        id: String,
        keepName: String,
    ) {
        packageDir(id)
            .listFiles { f -> f.isFile && !f.name.endsWith(".part") && f.name != keepName }
            ?.forEach { it.delete() }
    }

    /**
     * Wipes every cached file (completed downloads and any leftover `.part`
     * scratch files) under every descriptor. Per-descriptor directories are
     * left in place — they're re-created on demand by [packageDir] anyway.
     * The bundled Android APK is not in this tree, so it is unaffected.
     */
    fun clearAll() {
        val root = File(parentDir, "seed")
        if (!root.exists()) return
        root.listFiles()?.forEach { dir ->
            if (dir.isDirectory) {
                dir.listFiles()?.forEach { it.delete() }
            }
        }
    }
}
