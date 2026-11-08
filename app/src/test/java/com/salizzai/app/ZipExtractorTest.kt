package com.salizzai.app

import com.salizzai.app.util.ZipExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Plain-JVM unit tests for the on-device ZIP extraction pipeline.
 * Build ZIP archives in memory and assert deterministic extraction results.
 */
class ZipExtractorTest {

    private fun zip(vararg entries: Pair<String, String>): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zos ->
            entries.forEach { (path, content) ->
                val bytes = content.toByteArray(Charsets.UTF_8)
                val entry = ZipEntry(path)
                entry.size = bytes.size.toLong()
                zos.putNextEntry(entry)
                zos.write(bytes)
                zos.closeEntry()
            }
        }
        return bos.toByteArray()
    }

    @Test
    fun countsFilesAndExtensions() {
        val bytes = zip(
            "project/README.md" to "# hello",
            "project/src/Main.kt" to "fun main() {}",
            "project/data/config.json" to "{}"
        )
        val result = ZipExtractor.extract(bytes, "test.zip")
        assertEquals(3, result.fileCount)
        assertEquals(3, result.topExtensions.values.sum())
        assertTrue(result.topExtensions.containsKey(".md"))
        assertTrue(result.topExtensions.containsKey(".kt"))
        assertTrue(result.topExtensions.containsKey(".json"))
    }

    @Test
    fun skipsNoiseEntries() {
        val bytes = zip(
            "__MACOSX/junk" to "noise",
            "project/node_modules/lib.js" to "skip me",
            "project/.git/config" to "skip me too",
            "project/keep.txt" to "keep"
        )
        val result = ZipExtractor.extract(bytes)
        assertEquals(1, result.fileCount)
        assertTrue(result.extractedText.contains("keep"))
    }

    @Test
    fun extractedTextContainsFileTreeAndContents() {
        val bytes = zip("project/README.md" to "# Documentation for the test")
        val result = ZipExtractor.extract(bytes)
        assertTrue(result.extractedText.contains("project/"))
        assertTrue(result.extractedText.contains("# Documentation for the test"))
    }

    @Test
    fun truncatesLongTextFiles() {
        val long = "a".repeat(20_000)
        val bytes = zip("project/big.md" to long)
        val result = ZipExtractor.extract(bytes)
        // Per-file text extraction is capped at 8000 chars plus a marker.
        assertTrue(result.extractedText.contains("[متوقف شد]"))
    }


    @Test
    fun rejectsZipSlipAndAbsolutePaths() {
        val traversal = zip("../outside.txt" to "no")
        val absolute = zip("/outside.txt" to "no")
        assertTrue(runCatching { ZipExtractor.extract(traversal) }.isFailure)
        assertTrue(runCatching { ZipExtractor.extract(absolute) }.isFailure)
    }

    @Test
    fun rejectsCaseInsensitiveDuplicatePaths() {
        val bytes = zip(
            "root/Readme.txt" to "one",
            "root/README.TXT" to "two"
        )
        assertTrue(runCatching { ZipExtractor.extract(bytes) }.isFailure)
    }

    @Test
    fun rejectsFileAndDirectoryPathCollisions() {
        val fileBeforeChild = zip(
            "root/item" to "file",
            "root/item/child.txt" to "child"
        )
        val childBeforeFile = zip(
            "root/item/child.txt" to "child",
            "root/item" to "file"
        )
        assertTrue(runCatching { ZipExtractor.extract(fileBeforeChild) }.isFailure)
        assertTrue(runCatching { ZipExtractor.extract(childBeforeFile) }.isFailure)
    }

    @Test
    fun rejectsArchivesWithTooManyEntries() {
        val entries = Array(ZipExtractor.MAX_ENTRIES + 1) { index ->
            "root/file-$index.txt" to "x"
        }
        val bytes = zip(*entries)
        assertTrue(runCatching { ZipExtractor.extract(bytes) }.isFailure)
    }

    @Test
    fun rejectsEntriesThatExpandBeyondPerFileLimit() {
        val bytes = zip("root/large.txt" to "x".repeat(11 * 1024 * 1024))
        assertTrue(runCatching { ZipExtractor.extract(bytes) }.isFailure)
    }

    @Test
    fun rejectsTotalExpandedSizeOverLimit() {
        val entries = Array(6) { index ->
            "root/large-$index.bin" to "x".repeat(9 * 1024 * 1024)
        }
        val bytes = zip(*entries)
        assertTrue(runCatching { ZipExtractor.extract(bytes) }.isFailure)
    }

    @Test
    fun formatsByteSizes() {
        assertEquals("512 B", ZipExtractor.formatBytes(512))
        assertEquals("1.0 KB", ZipExtractor.formatBytes(1024))
        assertEquals("1.5 MB", ZipExtractor.formatBytes((1.5 * 1024 * 1024).toLong()))
    }

    @Test
    fun snapshotContainsExpectedMetadata() {
        val bytes = zip(
            "v1/main.py" to "print('hello')",
            "v1/utils.py" to "def add(a, b): return a + b"
        )
        val result = ZipExtractor.extract(bytes, "v1.zip")
        assertEquals(2, result.snapshot.fileCount)
        assertEquals(2, result.snapshot.filesByPath.size)
        assertEquals("v1.zip", result.snapshot.originalName)
        assertTrue(result.snapshot.textPreviewByPath.isNotEmpty())
    }

    @Test
    fun compareZipsFindsCommonAndUniqueFiles() {
        val v1 = zip(
            "src/main.py" to "print('v1')",
            "src/utils.py" to "def add(a, b): return a + b",
            "README.md" to "# v1"
        )
        val v2 = zip(
            "src/main.py" to "print('v2')",
            "src/utils.py" to "def add(a, b): return a + b",
            "src/new.py" to "def greet(): return 'hi'"
        )
        val r1 = ZipExtractor.extract(v1, "v1.zip")
        val r2 = ZipExtractor.extract(v2, "v2.zip")
        val comparison = ZipExtractor.compareZips(listOf(r1.snapshot, r2.snapshot))
        assertEquals(2, comparison.snapshots.size)
        // Common: src/main.py and src/utils.py
        assertTrue(comparison.commonFiles.contains("src/main.py"))
        assertTrue(comparison.commonFiles.contains("src/utils.py"))
        // v2 has src/new.py unique
        assertTrue(comparison.uniqueFilesBySnapshot[1].contains("src/new.py"))
    }

    @Test
    fun buildMultiZipPromptIncludesAllSnapshots() {
        val v1 = zip("v1/x.txt" to "alpha")
        val v2 = zip("v2/x.txt" to "beta")
        val r1 = ZipExtractor.extract(v1, "v1.zip")
        val r2 = ZipExtractor.extract(v2, "v2.zip")
        val comparison = ZipExtractor.compareZips(listOf(r1.snapshot, r2.snapshot))
        val prompt = ZipExtractor.buildMultiZipPrompt(
            listOf(r1.snapshot, r2.snapshot),
            comparison,
            "compare these two versions"
        )
        assertTrue(prompt.contains("v1.zip"))
        assertTrue(prompt.contains("v2.zip"))
        assertTrue(prompt.contains("alpha"))
        assertTrue(prompt.contains("beta"))
        assertTrue(prompt.contains("compare these two versions"))
    }
}
