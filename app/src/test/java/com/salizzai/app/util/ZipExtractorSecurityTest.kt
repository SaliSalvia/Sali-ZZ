package com.salizzai.app.util

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertThrows
import org.junit.Test

class ZipExtractorSecurityTest {

    @Test
    fun rejectsPathTraversalEntries() {
        val archive = zipOf("../outside.txt" to "not extracted")

        assertThrows(IllegalArgumentException::class.java) {
            ZipExtractor.extract(archive)
        }
    }

    @Test
    fun rejectsArchivesWithTooManyEntries() {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            repeat(10_001) { index ->
                zip.putNextEntry(ZipEntry("file-$index.txt"))
                zip.closeEntry()
            }
        }

        assertThrows(IllegalArgumentException::class.java) {
            ZipExtractor.extract(output.toByteArray())
        }
    }

    private fun zipOf(vararg entries: Pair<String, String>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }
}
