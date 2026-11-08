package com.salizzai.app.util

import com.salizzai.app.data.model.FileTreeNode
import com.salizzai.app.data.model.ZipComparison
import com.salizzai.app.data.model.ZipSnapshot
import java.io.ByteArrayInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * ZIP file extractor for Android — uses built-in java.util.zip.
 * No external libraries needed.
 *
 * Beyond simple extraction, this object also produces a [ZipSnapshot] per ZIP
 * so that two or more archives can be compared via [compareZips].
 *
 * Designed for technical content (research papers, invention disclosures,
 * source-code packages): per-file text is truncated generously (8 KB) and
 * the overall cap is 256 KB so long documents still fit within the model's
 * context window.
 */
object ZipExtractor {

    // Extensions considered "text" for content extraction
    val TEXT_EXTENSIONS = setOf(
        "txt", "md", "markdown", "rst", "json", "yaml", "yml", "toml", "ini",
        "js", "jsx", "ts", "tsx", "mjs", "cjs",
        "py", "rb", "php", "go", "rs", "c", "h", "cpp", "cc", "hpp",
        "java", "kt", "swift", "cs", "vb",
        "html", "css", "scss", "less", "sass",
        "xml", "svg",
        "sh", "bash", "zsh", "fish",
        "sql", "graphql", "gql",
        "vue", "svelte", "astro",
        "env", "gitignore", "dockerignore", "editorconfig",
        "lock", "conf", "cfg",
        "csv", "tsv",
        "lua", "r", "dart", "scala", "clj", "el", "ex", "exs",
        "makefile", "mk",
        "tex", "bib", "bibtex"   // scientific papers
    )

    private val SKIP_DIRS = setOf(
        "node_modules", ".git", ".next", ".nuxt", "dist", "build", "out",
        ".cache", "__pycache__", ".pytest_cache", ".venv", "venv", "env",
        ".idea", ".vscode", "coverage", ".turbo", ".svelte-kit"
    )

    private const val MAX_FILE_SIZE = 1024 * 1024 // 1MB text preview buffer per file
    private const val MAX_EXTRACTED_TEXT = 256 * 1024 // 256KB total text preview
    private const val PER_FILE_TEXT_CAP = 8_000

    // Hard resource limits apply to every archive, including binary entries.
    private const val MAX_ARCHIVE_ENTRIES = 10_000
    private const val MAX_ENTRY_UNCOMPRESSED_BYTES = 25L * 1024 * 1024
    private const val MAX_ARCHIVE_UNCOMPRESSED_BYTES = 100L * 1024 * 1024
    private const val MAX_ENTRY_PATH_LENGTH = 1_024

    data class ExtractionResult(
        val fileTree: List<FileTreeNode>,
        val extractedText: String,
        val fileCount: Int,
        val totalSize: Long,
        val topExtensions: Map<String, Int>,
        val summary: String,
        val snapshot: ZipSnapshot
    )

    fun extract(zipBytes: ByteArray, originalName: String = "archive.zip"): ExtractionResult {
        val files = mutableListOf<FileInfo>()
        val textContents = mutableListOf<TextFile>()
        var totalSize = 0L
        var extractedTextSize = 0
        var entryCount = 0

        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                entryCount++
                require(entryCount <= MAX_ARCHIVE_ENTRIES) {
                    "Archive contains too many entries (limit: $MAX_ARCHIVE_ENTRIES)."
                }
                if (!entry.isDirectory) {
                    val path = entry.name
                    require(
                        path.isNotBlank() &&
                            path.length <= MAX_ENTRY_PATH_LENGTH &&
                            !path.startsWith("/") &&
                            path.indexOf(92.toChar()) < 0 &&
                            path.split('/').none { it == ".." || it.isEmpty() }
                    ) { "Archive contains an invalid entry path." }

                    if (path.startsWith("__MACOSX/")) {
                        zis.closeEntry()
                        entry = zis.nextEntry
                        continue
                    }
                    val parts = path.split("/")
                    if (parts.any { it in SKIP_DIRS }) {
                        zis.closeEntry()
                        entry = zis.nextEntry
                        continue
                    }

                    // NOTE: many tools (java.util.zip.ZipOutputStream, Gradle, some CI zippers)
                    // write entries with a trailing "data descriptor" — for those, entry.size
                    // is -1 until the entry has been fully consumed. Reading size BEFORE the
                    // content therefore yields 0/-1 and used to disable all text extraction.
                    // Fix: stream the entry once, count the real byte length, and only buffer
                    // the content (capped) for text candidates.
                    val ext = getExtension(path)
                    val isTextCandidate = isTextFile(path, ext) &&
                        extractedTextSize < MAX_EXTRACTED_TEXT

                    val buffer = ByteArray(64 * 1024)
                    val textBuffer = if (isTextCandidate) java.io.ByteArrayOutputStream() else null
                    var bytesCounted = 0L
                    var oversized = false
                    while (true) {
                        val n = zis.read(buffer)
                        if (n < 0) break
                        bytesCounted += n
                        require(bytesCounted <= MAX_ENTRY_UNCOMPRESSED_BYTES) {
                            "Archive entry exceeds the " + formatBytes(MAX_ENTRY_UNCOMPRESSED_BYTES) + " limit."
                        }
                        require(totalSize <= MAX_ARCHIVE_UNCOMPRESSED_BYTES - bytesCounted) {
                            "Archive exceeds the " + formatBytes(MAX_ARCHIVE_UNCOMPRESSED_BYTES) + " expanded-size limit."
                        }
                        if (textBuffer != null && !oversized) {
                            textBuffer.write(buffer, 0, n)
                            if (textBuffer.size() > MAX_FILE_SIZE) oversized = true
                        }
                    }
                    // Count bytes actually streamed; ZIP header sizes are not trusted.
                    val size = bytesCounted
                    files.add(FileInfo(path, size))
                    totalSize += size

                    if (isTextCandidate && !oversized && textBuffer != null) {
                        try {
                            val content = textBuffer.toByteArray().toString(Charsets.UTF_8)
                            val truncated = if (content.length > PER_FILE_TEXT_CAP) {
                                content.substring(0, PER_FILE_TEXT_CAP) + "\n... [متوقف شد]"
                            } else content
                            textContents.add(TextFile(path, truncated, content.length.toLong()))
                            extractedTextSize += truncated.length
                        } catch (e: Exception) {
                            // skip unreadable
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        val tree = buildTree(files)
        val topLevel = files.map { it.path.split("/").first() }.toSet().sorted()

        val byExt = mutableMapOf<String, Int>()
        files.forEach { f ->
            val ext = getExtension(f.path)
            byExt[ext] = (byExt[ext] ?: 0) + 1
        }

        val sb = StringBuilder()
        sb.append("**خلاصه پروژه:**\n")
        sb.append("- نام فایل: `$originalName`\n")
        sb.append("- تعداد فایل‌ها: ${files.size}\n")
        sb.append("- حجم کل: ${formatBytes(totalSize)}\n")
        sb.append("- پوشه‌های اصلی: ${topLevel.joinToString(", ")}\n")
        val topExts = byExt.entries.sortedByDescending { it.value }.take(10)
        if (topExts.isNotEmpty()) {
            sb.append("- پرتکرارترین پسوندها: ${topExts.joinToString(", ") { "${it.key}(${it.value})" }}\n")
        }
        sb.append("\n**ساختار فایل‌ها:**\n```\n")
        sb.append(renderTree(tree))
        sb.append("```\n\n")

        if (textContents.isNotEmpty()) {
            sb.append("**محتوای فایل‌های متنی (${textContents.size} فایل):**\n\n")
            textContents.forEach { tc ->
                sb.append("### `${tc.path}`\n\n```\n${tc.content}\n```\n\n")
            }
        }

        val snapshot = ZipSnapshot(
            originalName = originalName,
            fileCount = files.size,
            totalSize = totalSize,
            topExtensions = byExt,
            topLevelDirs = topLevel,
            filesByPath = files.associate { it.path to it.size },
            textPreviewByPath = textContents.associate { it.path to it.content }
        )

        return ExtractionResult(
            fileTree = tree,
            extractedText = sb.toString(),
            fileCount = files.size,
            totalSize = totalSize,
            topExtensions = byExt,
            summary = "$originalName — ${files.size} فایل، ${formatBytes(totalSize)}",
            snapshot = snapshot
        )
    }

    /**
     * Builds a unified comparison across two or more ZIPs. The returned string is a
     * Markdown report ready to be sent to Z.ai alongside a user's prompt like
     * "compare these two versions of my invention".
     */
    fun compareZips(snapshots: List<ZipSnapshot>): ZipComparison {
        if (snapshots.size < 2) {
            return ZipComparison(
                snapshots = snapshots,
                commonFiles = emptyList(),
                uniqueFilesBySnapshot = snapshots.map { emptyList() },
                sizeDeltas = emptyMap(),
                summary = "حداقل دو ZIP برای مقایسه لازم است."
            )
        }

        // Common files = intersection of all snapshots' file paths
        val pathSets = snapshots.map { it.filesByPath.keys.toSet() }
        val common = pathSets.reduce { acc, set -> acc intersect set }

        // Unique files per snapshot = paths in snapshot but not in any other
        val unique = snapshots.mapIndexed { idx, snap ->
            val others = snapshots.filterIndexed { i, _ -> i != idx }
                .flatMap { it.filesByPath.keys }.toSet()
            snap.filesByPath.keys.filter { it !in others }
        }

        // Size deltas for files present in both first two snapshots
        val a = snapshots[0].filesByPath
        val b = snapshots[1].filesByPath
        val sizeDeltas = mutableMapOf<String, Long>()
        common.forEach { path ->
            val sa = a[path] ?: 0L
            val sb = b[path] ?: 0L
            if (sa != sb) sizeDeltas[path] = sb - sa
        }

        val sb = StringBuilder()
        sb.append("**مقایسه ${snapshots.size} نسخه ZIP:**\n\n")
        snapshots.forEachIndexed { i, s ->
            sb.append("### نسخه ${i + 1}: `${s.originalName}`\n")
            sb.append("- تعداد فایل: ${s.fileCount}\n")
            sb.append("- حجم: ${formatBytes(s.totalSize)}\n")
            sb.append("- پوشه‌های اصلی: ${s.topLevelDirs.joinToString(", ")}\n\n")
        }
        sb.append("**فایل‌های مشترک:** ${common.size}\n")
        if (common.isNotEmpty()) {
            sb.append("```\n${common.take(50).joinToString("\n")}${if (common.size > 50) "\n... و ${common.size - 50} فایل دیگر" else ""}\n```\n\n")
        }
        sb.append("**فایل‌های اختصاصی هر نسخه:**\n")
        snapshots.forEachIndexed { i, _ ->
            sb.append("- نسخه ${i + 1}: ${unique[i].size} فایل اختصاصی\n")
        }
        if (sizeDeltas.isNotEmpty()) {
            sb.append("\n**تفاوت حجم:**\n")
            sizeDeltas.entries.sortedByDescending { Math.abs(it.value) }.take(20).forEach { (path, delta) ->
                val sign = if (delta > 0) "+" else ""
                sb.append("- `$path`: $sign${formatBytes(delta)}\n")
            }
        }

        return ZipComparison(
            snapshots = snapshots,
            commonFiles = common.toList(),
            uniqueFilesBySnapshot = unique,
            sizeDeltas = sizeDeltas,
            summary = sb.toString()
        )
    }

    /**
     * Build a single composite prompt that bundles several ZIPs + the user's question.
     * Used when the user attaches more than one ZIP and asks for analysis / comparison.
     */
    fun buildMultiZipPrompt(
        snapshots: List<ZipSnapshot>,
        comparison: ZipComparison,
        userQuestion: String
    ): String {
        val sb = StringBuilder()
        sb.append("# تحلیل چند فایل ZIP پیوست\n\n")
        snapshots.forEachIndexed { i, s ->
            sb.append("## فایل ${i + 1}: `${s.originalName}`\n")
            sb.append("- تعداد فایل: ${s.fileCount}\n")
            sb.append("- حجم کل: ${formatBytes(s.totalSize)}\n")
            sb.append("- پسوندهای پرتکرار: ${s.topExtensions.entries.sortedByDescending { it.value }.take(8).joinToString(", ") { "${it.key}(${it.value})" }}\n\n")
        }
        sb.append("## مقایسه خودکار\n\n")
        sb.append(comparison.summary)
        sb.append("\n## محتوای متنی فایل‌ها\n\n")
        snapshots.forEach { s ->
            sb.append("### `${s.originalName}`\n\n")
            s.textPreviewByPath.entries.take(50).forEach { (path, content) ->
                sb.append("#### `$path`\n```\n$content\n```\n\n")
            }
        }
        sb.append("---\n\n")
        sb.append("**سؤال کاربر:** $userQuestion\n\n")
        sb.append("لطفاً تحلیل عمیق و تخصصی ارائه بده. تفاوت‌های کلیدی را مشخص کن و در صورت وجود مشکل یا بهبود، توضیح بده.")
        return sb.toString()
    }

    private data class FileInfo(val path: String, val size: Long)
    private data class TextFile(val path: String, val content: String, val size: Long)

    fun getExtension(path: String): String {
        val name = path.substringAfterLast("/")
        val dotIdx = name.lastIndexOf('.')
        return if (dotIdx >= 0) name.substring(dotIdx).lowercase() else ""
    }

    private fun isTextFile(path: String, ext: String): Boolean {
        val cleanExt = ext.removePrefix(".").lowercase()
        if (cleanExt in TEXT_EXTENSIONS) return true
        val lowerName = path.substringAfterLast("/").lowercase()
        return setOf("readme", "license", "changelog", "contributing", "authors",
            "makefile", "dockerfile", "rakefile", "gemfile").any {
            lowerName == it || lowerName.startsWith("$it.")
        }
    }

    private fun buildTree(files: List<FileInfo>): List<FileTreeNode> {
        val root = mutableListOf<FileTreeNode>()
        val dirMap = mutableMapOf<String, MutableList<FileTreeNode>>()

        files.sortedBy { it.path }.forEach { file ->
            val parts = file.path.split("/")
            val path = StringBuilder()
            var currentLevel: MutableList<FileTreeNode>? = null
            var currentDirKey = ""

            for (i in parts.indices) {
                val part = parts[i]
                val isFile = i == parts.lastIndex
                if (path.isNotEmpty()) path.append("/")
                path.append(part)

                if (isFile) {
                    val ext = getExtension(file.path)
                    val node = FileTreeNode(
                        name = part,
                        path = path.toString(),
                        isDirectory = false,
                        size = file.size,
                        ext = ext
                    )
                    (currentLevel ?: root).add(node)
                } else {
                    currentDirKey = path.toString()
                    val existing = dirMap[currentDirKey]
                    if (existing == null) {
                        val newDir = FileTreeNode(
                            name = part,
                            path = path.toString(),
                            isDirectory = true,
                            size = 0,
                            children = mutableListOf()
                        )
                        (currentLevel ?: root).add(newDir)
                        val newChildren = newDir.children as MutableList
                        dirMap[currentDirKey] = newChildren
                        currentLevel = newChildren
                    } else {
                        currentLevel = existing
                    }
                }
            }
        }
        return root
    }

    private fun renderTree(nodes: List<FileTreeNode>, prefix: String = "", depth: Int = 0): String {
        if (depth > 5) return ""
        val sb = StringBuilder()
        nodes.forEachIndexed { idx, node ->
            val isLast = idx == nodes.lastIndex
            sb.append(prefix)
            sb.append(if (isLast) "└── " else "├── ")
            sb.append(node.name)
            if (node.isDirectory) sb.append("/")
            if (node.size > 0) sb.append(" (${formatBytes(node.size)})")
            sb.append("\n")
            if (node.isDirectory && node.children.isNotEmpty()) {
                sb.append(renderTree(node.children,
                    prefix + if (isLast) "    " else "│   ", depth + 1))
            }
        }
        return sb.toString()
    }

    fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        if (bytes < 1024 * 1024) return "%.1f KB".format(bytes / 1024.0)
        if (bytes < 1024L * 1024 * 1024) return "%.1f MB".format(bytes / (1024.0 * 1024))
        return "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
    }
}
