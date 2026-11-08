package com.salizzai.app.util

import com.salizzai.app.data.model.FileTreeNode
import com.salizzai.app.data.model.ZipComparison
import com.salizzai.app.data.model.ZipSnapshot
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Bounded ZIP inspection pipeline. This class does not write archive paths to disk;
 * it builds a safe logical tree and extracts a capped amount of text for AI analysis.
 */
object ZipExtractor {

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
        "tex", "bib", "bibtex"
    )

    private val SKIP_DIRS = setOf(
        "node_modules", ".git", ".next", ".nuxt", "dist", "build", "out",
        ".cache", "__pycache__", ".pytest_cache", ".venv", "venv", "env",
        ".idea", ".vscode", "coverage", ".turbo", ".svelte-kit"
    )

    /** ZIP archive bytes accepted from the document picker. */
    const val MAX_COMPRESSED_SIZE = 25 * 1024 * 1024
    /** Maximum number of central/local entries we will inspect. */
    const val MAX_ENTRIES = 2_000
    /** Maximum expanded payload for one entry. */
    const val MAX_ENTRY_SIZE = 10L * 1024 * 1024
    /** Maximum expanded payload across the whole archive. */
    const val MAX_TOTAL_SIZE = 50L * 1024 * 1024
    private const val MAX_PATH_LENGTH = 512
    private const val MAX_PATH_DEPTH = 32
    private const val MAX_FILE_SIZE_FOR_TEXT = 1024 * 1024
    private const val MAX_EXTRACTED_TEXT = 256 * 1024
    private const val PER_FILE_TEXT_CAP = 8_000

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
        require(zipBytes.size <= MAX_COMPRESSED_SIZE) {
            "فایل ZIP بیش از حد بزرگ است؛ سقف فایل فشرده ۲۵ مگابایت است."
        }

        val files = mutableListOf<FileInfo>()
        val textContents = mutableListOf<TextFile>()
        val seenPaths = HashSet<String>()
        var totalSize = 0L
        var extractedTextSize = 0
        var entryCount = 0

        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                entryCount++
                require(entryCount <= MAX_ENTRIES) {
                    "آرشیو ZIP بیش از سقف مجاز ۲۰۰۰ ورودی دارد."
                }

                val rawPath = entry.name
                val safePath = validateEntryPath(rawPath, entry.isDirectory)
                val parts = safePath.split('/')
                val shouldSkip = parts.any { it in SKIP_DIRS } || safePath.startsWith("__MACOSX/")

                if (!entry.isDirectory && !shouldSkip) {
                    val collisionKey = safePath.lowercase(Locale.ROOT)
                    val pathSegments = collisionKey.split('/')
                    val parentPaths = pathSegments.indices.toList().dropLast(1).map { index ->
                        pathSegments.take(index + 1).joinToString("/")
                    }
                    require(collisionKey !in seenPaths &&
                        parentPaths.none { it in seenPaths } &&
                        seenPaths.none { it.startsWith("$collisionKey/") }) {
                        "مسیر تکراری یا مبهم در ZIP پیدا شد: $safePath"
                    }
                    seenPaths.add(collisionKey)

                    val ext = getExtension(safePath)
                    val isTextCandidate = isTextFile(safePath, ext) &&
                        extractedTextSize < MAX_EXTRACTED_TEXT
                    val textBuffer = if (isTextCandidate) ByteArrayOutputStream() else null
                    val buffer = ByteArray(64 * 1024)
                    var bytesCounted = 0L
                    var oversizedForText = false

                    while (true) {
                        val n = zis.read(buffer)
                        if (n < 0) break
                        val amount = n.toLong()
                        require(bytesCounted <= MAX_ENTRY_SIZE - amount) {
                            "یکی از فایل‌های داخل ZIP از سقف ۱۰ مگابایت عبور کرد: $safePath"
                        }
                        require(totalSize <= MAX_TOTAL_SIZE - bytesCounted - amount) {
                            "حجم بازشدهٔ ZIP از سقف ۵۰ مگابایت عبور کرد."
                        }
                        bytesCounted += amount

                        if (textBuffer != null && !oversizedForText) {
                            if (textBuffer.size().toLong() + amount <= MAX_FILE_SIZE_FOR_TEXT) {
                                textBuffer.write(buffer, 0, n)
                            } else {
                                oversizedForText = true
                                textBuffer.reset()
                            }
                        }
                    }

                    // The stream has been consumed, so unknown data-descriptor sizes are now exact.
                    val size = if (entry.size >= 0L) entry.size else bytesCounted
                    require(size <= MAX_ENTRY_SIZE && bytesCounted <= MAX_ENTRY_SIZE) {
                        "یکی از فایل‌های داخل ZIP از سقف ۱۰ مگابایت عبور کرد: $safePath"
                    }
                    require(totalSize <= MAX_TOTAL_SIZE - bytesCounted) {
                        "حجم بازشدهٔ ZIP از سقف ۵۰ مگابایت عبور کرد."
                    }
                    totalSize += bytesCounted
                    files.add(FileInfo(safePath, bytesCounted))

                    if (isTextCandidate && !oversizedForText && textBuffer != null) {
                        val content = textBuffer.toByteArray().toString(Charsets.UTF_8)
                        val truncated = if (content.length > PER_FILE_TEXT_CAP) {
                            content.substring(0, PER_FILE_TEXT_CAP) + "\n... [متوقف شد]"
                        } else content
                        textContents.add(TextFile(safePath, truncated, content.length.toLong()))
                        extractedTextSize += truncated.length
                    }
                } else {
                    // Directory and skipped entries are drained under the same budget,
                    // so crafted records cannot hide expanded data from the limits.
                    val buffer = ByteArray(64 * 1024)
                    var skippedBytes = 0L
                    while (true) {
                        val n = zis.read(buffer)
                        if (n < 0) break
                        val amount = n.toLong()
                        require(skippedBytes <= MAX_ENTRY_SIZE - amount) {
                            "یکی از ورودی‌های کنارگذاشته‌شده بیش از حد بزرگ است."
                        }
                        require(totalSize <= MAX_TOTAL_SIZE - skippedBytes - amount) {
                            "حجم بازشدهٔ ZIP از سقف ۵۰ مگابایت عبور کرد."
                        }
                        skippedBytes += amount
                    }
                    totalSize += skippedBytes
                }

                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        val tree = buildTree(files)
        val topLevel = files.map { it.path.substringBefore('/') }.toSet().sorted()
        val byExt = mutableMapOf<String, Int>()
        files.forEach { file ->
            val ext = getExtension(file.path)
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
            textContents.forEach { file ->
                sb.append("### `${file.path}`\n\n```\n${file.content}\n```\n\n")
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

    private fun validateEntryPath(rawPath: String, isDirectory: Boolean): String {
        require(rawPath.isNotBlank() && rawPath.length <= MAX_PATH_LENGTH) {
            "یکی از مسیرهای ZIP خالی است یا بیش از حد طولانی است."
        }
        require('\u0000' !in rawPath && '\\' !in rawPath && !rawPath.startsWith('/')) {
            "مسیر غیرمجاز در ZIP پیدا شد: $rawPath"
        }
        require(!Regex("^[A-Za-z]:").containsMatchIn(rawPath)) {
            "مسیر مطلق ویندوز در ZIP مجاز نیست: $rawPath"
        }
        val normalized = if (isDirectory) rawPath.trimEnd('/') else rawPath
        val segments = normalized.split('/')
        require(segments.size <= MAX_PATH_DEPTH && segments.none { it.isBlank() || it == "." || it == ".." }) {
            "مسیر ناامن یا بیش از حد عمیق در ZIP پیدا شد: $rawPath"
        }
        return normalized
    }

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

        val pathSets = snapshots.map { it.filesByPath.keys.toSet() }
        val common = pathSets.reduce { acc, set -> acc intersect set }
        val unique = snapshots.mapIndexed { idx, snap ->
            val others = snapshots.filterIndexed { i, _ -> i != idx }
                .flatMap { it.filesByPath.keys }.toSet()
            snap.filesByPath.keys.filter { it !in others }
        }
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
        snapshots.forEachIndexed { i, snapshot ->
            sb.append("### نسخه ${i + 1}: `${snapshot.originalName}`\n")
            sb.append("- تعداد فایل: ${snapshot.fileCount}\n")
            sb.append("- حجم: ${formatBytes(snapshot.totalSize)}\n")
            sb.append("- پوشه‌های اصلی: ${snapshot.topLevelDirs.joinToString(", ")}\n\n")
        }
        sb.append("**فایل‌های مشترک:** ${common.size}\n")
        if (common.isNotEmpty()) {
            sb.append("```\n${common.take(50).joinToString("\n")}${if (common.size > 50) "\n... و ${common.size - 50} فایل دیگر" else ""}\n```\n\n")
        }
        sb.append("**فایل‌های اختصاصی هر نسخه:**\n")
        snapshots.forEachIndexed { i, _ -> sb.append("- نسخه ${i + 1}: ${unique[i].size} فایل اختصاصی\n") }
        if (sizeDeltas.isNotEmpty()) {
            sb.append("\n**تفاوت حجم:**\n")
            sizeDeltas.entries.sortedByDescending { kotlin.math.abs(it.value) }.take(20).forEach { (path, delta) ->
                val sign = if (delta > 0) "+" else ""
                sb.append("- `$path`: $sign${formatBytes(delta)}\n")
            }
        }

        return ZipComparison(snapshots, common.toList(), unique, sizeDeltas, sb.toString())
    }

    fun buildMultiZipPrompt(
        snapshots: List<ZipSnapshot>,
        comparison: ZipComparison,
        userQuestion: String
    ): String {
        val sb = StringBuilder()
        sb.append("# تحلیل چند فایل ZIP پیوست\n\n")
        snapshots.forEachIndexed { i, snapshot ->
            sb.append("## فایل ${i + 1}: `${snapshot.originalName}`\n")
            sb.append("- تعداد فایل: ${snapshot.fileCount}\n")
            sb.append("- حجم کل: ${formatBytes(snapshot.totalSize)}\n")
            sb.append("- پسوندهای پرتکرار: ${snapshot.topExtensions.entries.sortedByDescending { it.value }.take(8).joinToString(", ") { "${it.key}(${it.value})" }}\n\n")
        }
        sb.append("## مقایسه خودکار\n\n${comparison.summary}\n")
        sb.append("\n## محتوای متنی فایل‌ها\n\n")
        snapshots.forEach { snapshot ->
            sb.append("### `${snapshot.originalName}`\n\n")
            snapshot.textPreviewByPath.entries.take(50).forEach { (path, content) ->
                sb.append("#### `$path`\n```\n$content\n```\n\n")
            }
        }
        sb.append("---\n\n**سؤال کاربر:** $userQuestion\n\n")
        sb.append("لطفاً تحلیل عمیق و تخصصی ارائه بده. تفاوت‌های کلیدی را مشخص کن و در صورت وجود مشکل یا بهبود، توضیح بده.")
        return sb.toString()
    }

    private data class FileInfo(val path: String, val size: Long)
    private data class TextFile(val path: String, val content: String, val size: Long)

    fun getExtension(path: String): String {
        val name = path.substringAfterLast('/')
        val dotIdx = name.lastIndexOf('.')
        return if (dotIdx >= 0) name.substring(dotIdx).lowercase(Locale.ROOT) else ""
    }

    private fun isTextFile(path: String, ext: String): Boolean {
        val cleanExt = ext.removePrefix(".").lowercase(Locale.ROOT)
        if (cleanExt in TEXT_EXTENSIONS) return true
        val lowerName = path.substringAfterLast('/').lowercase(Locale.ROOT)
        return setOf("readme", "license", "changelog", "contributing", "authors", "makefile", "dockerfile", "rakefile", "gemfile")
            .any { lowerName == it || lowerName.startsWith("$it.") }
    }

    private fun buildTree(files: List<FileInfo>): List<FileTreeNode> {
        data class MutableNode(
            val name: String,
            val path: String,
            var size: Long = 0L,
            val children: MutableMap<String, MutableNode> = sortedMapOf()
        )

        val mutableRoot = sortedMapOf<String, MutableNode>()
        files.forEach { file ->
            val parts = file.path.split('/')
            var level: MutableMap<String, MutableNode> = mutableRoot
            var currentPath = ""
            parts.forEachIndexed { index, part ->
                currentPath = if (currentPath.isEmpty()) part else "$currentPath/$part"
                val node = level.getOrPut(part) { MutableNode(part, currentPath) }
                if (index == parts.lastIndex) node.size = file.size
                level = node.children
            }
        }

        fun freeze(nodes: Map<String, MutableNode>): List<FileTreeNode> = nodes.values.map { node ->
            FileTreeNode(
                name = node.name,
                path = node.path,
                isDirectory = node.children.isNotEmpty(),
                size = node.size,
                children = freeze(node.children)
            )
        }
        return freeze(mutableRoot)
    }

    private fun renderTree(nodes: List<FileTreeNode>, prefix: String = ""): String {
        val sb = StringBuilder()
        nodes.forEachIndexed { index, node ->
            val last = index == nodes.lastIndex
            sb.append(prefix).append(if (last) "└── " else "├── ")
                .append(node.name)
                .append(if (node.isDirectory) "/" else "")
                .append(if (!node.isDirectory) " (${formatBytes(node.size)})" else "")
                .append('\n')
            if (node.children.isNotEmpty()) {
                sb.append(renderTree(node.children, prefix + if (last) "    " else "│   "))
            }
        }
        return sb.toString()
    }

    fun formatBytes(bytes: Long): String {
        if (bytes == Long.MIN_VALUE) return "—"
        if (bytes < 0) return "-${formatBytes(-bytes)}"
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "%.1f KB".format(Locale.ROOT, bytes / 1024.0)
            else -> "%.1f MB".format(Locale.ROOT, bytes / (1024.0 * 1024.0))
        }
    }
}
