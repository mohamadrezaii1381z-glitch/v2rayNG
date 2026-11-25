package com.v2ray.ang.handler

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * File-level installation logic for the geodata files (`geosite.dat`,
 * `geoip.dat`, ...) that Xray loads from the directory passed to
 * `Libv2ray.initCoreEnv` (the app-private `app_assets` directory).
 *
 * This is the pure-Java core of [SettingsManager.initAssets]: it is kept free
 * of Android types so the copy/repair semantics can be unit-tested on the JVM.
 *
 * Semantics:
 * - A target that already exists with exactly [expectedSize] bytes (when the
 *   expected size is known) is left untouched.
 * - A missing, truncated or otherwise size-mismatched target is re-copied.
 * - Copies are written to a temporary sibling file first and only renamed
 *   over the target once the copied byte count matches the expected size,
 *   so an interrupted or failing copy can never leave a partial `.dat`
 *   behind for Xray to choke on (the previous copy-if-absent logic kept
 *   such partial files forever).
 * - When the expected size is unknown (asset cannot be opened via a file
 *   descriptor), an existing non-empty target is kept, and a fresh copy of
 *   unknown length is accepted as long as it produced at least one byte.
 */
object GeoAssetFiles {

    /**
     * Ensures [target] contains the bytes supplied by [openStream].
     *
     * @param expectedSize exact size of the bundled asset in bytes, or a
     * negative value when it cannot be determined.
     * @param openStream opens a fresh stream over the bundled asset bytes.
     * @return true when [target] now holds a complete copy (or already did),
     * false when installation failed; on failure no partial target or
     * temporary file is left behind.
     */
    fun installIfNeeded(target: File, expectedSize: Long, openStream: () -> InputStream): Boolean {
        if (isComplete(target, expectedSize)) return true

        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, target.name + ".tmp")
        return try {
            val copied = openStream().use { input ->
                FileOutputStream(temp).use { output -> input.copyTo(output) }
            }
            val complete = if (expectedSize >= 0) copied == expectedSize else copied > 0
            if (!complete) {
                temp.delete()
                false
            } else if (temp.renameTo(target)) {
                true
            } else {
                temp.delete()
                false
            }
        } catch (e: Exception) {
            temp.delete()
            // Never let a failed re-copy destroy a pre-existing file check:
            // the target itself was not touched, only the temp file was.
            false
        }
    }

    /** True when [target] is an existing file whose size matches expectations. */
    fun isComplete(target: File, expectedSize: Long): Boolean {
        if (!target.isFile) return false
        return if (expectedSize >= 0) target.length() == expectedSize else target.length() > 0
    }
}
