package com.v2ray.ang.handler

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream

class GeoAssetFilesTest {

    @TempDir
    lateinit var dir: File

    private val payload = ByteArray(4096) { (it % 251).toByte() }

    private fun streamOf(bytes: ByteArray): () -> InputStream = { ByteArrayInputStream(bytes) }

    @Test
    fun missingFileIsInstalledWithExactContents() {
        val target = File(dir, "geosite.dat")
        assertTrue(GeoAssetFiles.installIfNeeded(target, payload.size.toLong(), streamOf(payload)))
        assertArrayEquals(payload, target.readBytes())
    }

    @Test
    fun completeExistingFileIsLeftUntouched() {
        val target = File(dir, "geoip.dat")
        target.writeBytes(payload)
        var opened = false
        val ok = GeoAssetFiles.installIfNeeded(target, payload.size.toLong()) {
            opened = true
            ByteArrayInputStream(ByteArray(0))
        }
        assertTrue(ok)
        assertFalse(opened, "stream must not be opened when the file is already complete")
        assertArrayEquals(payload, target.readBytes())
    }

    @Test
    fun truncatedFileFromInterruptedCopyIsRepaired() {
        val target = File(dir, "geosite.dat")
        target.writeBytes(payload.copyOf(100)) // partial file left by an interrupted first copy
        assertTrue(GeoAssetFiles.installIfNeeded(target, payload.size.toLong(), streamOf(payload)))
        assertArrayEquals(payload, target.readBytes())
    }

    @Test
    fun shortCopyFailsAndLeavesNoPartialTarget() {
        val target = File(dir, "geoip.dat")
        val ok = GeoAssetFiles.installIfNeeded(target, payload.size.toLong(), streamOf(payload.copyOf(10)))
        assertFalse(ok)
        assertFalse(target.exists(), "an incomplete copy must not be installed")
        assertFalse(File(dir, "geoip.dat.tmp").exists(), "temp file must be cleaned up")
    }

    @Test
    fun failingStreamFailsAndLeavesNoPartialTarget() {
        val target = File(dir, "geosite.dat")
        val ok = GeoAssetFiles.installIfNeeded(target, payload.size.toLong()) {
            object : InputStream() {
                override fun read(): Int = throw IOException("boom")
            }
        }
        assertFalse(ok)
        assertFalse(target.exists())
        assertFalse(File(dir, "geosite.dat.tmp").exists())
    }

    @Test
    fun unknownExpectedSizeKeepsExistingNonEmptyFile() {
        val target = File(dir, "geoip.dat")
        target.writeBytes(payload)
        assertTrue(GeoAssetFiles.isComplete(target, -1))
        assertTrue(GeoAssetFiles.installIfNeeded(target, -1, streamOf(ByteArray(0))))
        assertEquals(payload.size.toLong(), target.length())
    }

    @Test
    fun unknownExpectedSizeInstallsNonEmptyCopy() {
        val target = File(dir, "geosite.dat")
        assertTrue(GeoAssetFiles.installIfNeeded(target, -1, streamOf(payload)))
        assertArrayEquals(payload, target.readBytes())
    }

    @Test
    fun emptyCopyWithUnknownSizeIsRejected() {
        val target = File(dir, "geoip.dat")
        assertFalse(GeoAssetFiles.installIfNeeded(target, -1, streamOf(ByteArray(0))))
        assertFalse(target.exists())
    }
}
