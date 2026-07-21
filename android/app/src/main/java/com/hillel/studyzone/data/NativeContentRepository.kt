package com.hillel.studyzone.data

import android.content.Context
import com.hillel.studyzone.model.Chapter
import com.hillel.studyzone.model.Course
import com.hillel.studyzone.model.Lesson
import com.hillel.studyzone.model.SearchResult
import com.hillel.studyzone.model.Section
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Collections
import java.util.LinkedHashMap

/**
 * Reads the compact course catalog and lesson text bundled in the APK.  The
 * deployed server does not currently expose /api/mobile, while the web app's
 * generated search indexes are public and contain the same lesson text.  This
 * repository keeps the native reader independent from a WebView and makes the
 * first screen available without waiting for a Render cold start.
 */
internal class NativeContentRepository(
    context: Context,
    private val client: OkHttpClient,
    private val webRoot: String
) {
    private val appContext = context.applicationContext
    // A full-text search can touch every course. Keep only the most recently
    // used parsed indexes so a 14 MB asset catalog cannot balloon into a much
    // larger permanent JSONObject graph after one uncommon search.
    private val memoryIndexes = Collections.synchronizedMap(
        object : LinkedHashMap<String, JSONObject>(5, .75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, JSONObject>?): Boolean =
                size > MAX_MEMORY_INDEXES
        }
    )
    private val cacheDirectory = File(appContext.cacheDir, "native-search-index").apply { mkdirs() }

    val bundledCourses: List<Course> by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        readAssetText(CATALOG_ASSET)
            ?.let(::parseCatalog)
            ?.takeIf { it.isNotEmpty() }
            ?: FallbackCatalog.courses
    }

    fun lesson(courseId: String, sectionId: String): Lesson? {
        val course = bundledCourses.firstOrNull { it.id.equals(courseId, ignoreCase = true) } ?: return null
        val flattened = course.chapters.flatMap { chapter ->
            chapter.sections.map { section -> chapter to section }
        }
        val selectedIndex = flattened.indexOfFirst { (_, section) -> section.id == sectionId }
        if (selectedIndex < 0) return null

        val (chapter, section) = flattened[selectedIndex]
        val index = loadIndex(course.id)
        val raw = resolveLessonText(index?.optJSONObject(chapter.id), chapter.id, section.id)
        val normalized = normalizeLessonContent(raw.ifBlank { section.preview })
        if (normalized.isBlank()) return null

        return Lesson(
            courseId = course.id,
            courseTitle = course.title,
            chapterId = chapter.id,
            sectionId = section.id,
            title = section.title.ifBlank { "סעיף ${section.id}" },
            content = normalized,
            interactiveUrl = "$webRoot/#${course.id}/${chapter.id}/${section.id}",
            previousSectionId = flattened.getOrNull(selectedIndex - 1)?.second?.id,
            nextSectionId = flattened.getOrNull(selectedIndex + 1)?.second?.id
        )
    }

    fun search(rawQuery: String, limit: Int = 40): List<SearchResult> {
        val query = rawQuery.trim()
        if (query.length < 2) return emptyList()
        val normalizedQuery = query.lowercase()
        val results = ArrayList<SearchResult>(limit)
        val courseLevelMatches = HashSet<String>()

        // Metadata is tiny and usually enough for navigation searches.
        bundledCourses.forEach courseMetadataLoop@ { course ->
            val courseMetadata = "${course.title} ${course.description}"
            if (courseMetadata.lowercase().contains(normalizedQuery)) {
                course.chapters.firstOrNull()?.sections?.firstOrNull()?.let { firstSection ->
                    results += SearchResult(
                        courseId = course.id,
                        courseTitle = course.title,
                        chapterId = course.chapters.first().id,
                        sectionId = firstSection.id,
                        title = course.title,
                        snippet = snippetAround(courseMetadata, query)
                    )
                    courseLevelMatches += course.id
                }
            }
            if (course.id in courseLevelMatches) return@courseMetadataLoop
            course.chapters.forEach { chapter ->
                chapter.sections.forEach { section ->
                    val metadata = "${section.title} ${section.preview}"
                    if (metadata.lowercase().contains(normalizedQuery)) {
                        val key = "${course.id}/${section.id}"
                        if (results.none { "${it.courseId}/${it.sectionId}" == key }) {
                            results += SearchResult(
                                courseId = course.id,
                                courseTitle = course.title,
                                chapterId = chapter.id,
                                sectionId = section.id,
                                title = section.title,
                                snippet = snippetAround(metadata, query)
                            )
                        }
                        if (results.size >= limit) return results
                    }
                }
            }
        }

        // A course-name/description search is already resolved precisely. Avoid
        // parsing every multi-megabyte lesson index merely to repeat that course.
        if (courseLevelMatches.isNotEmpty()) return results
        if (results.size >= 8) return results

        // Full-text search is user initiated, so lazily open only the bundled
        // indexes needed to fill the result list rather than parsing 6 MB at boot.
        val seen = results.mapTo(HashSet()) { "${it.courseId}/${it.sectionId}" }
        bundledCourses.forEach courseLoop@ { course ->
            val index = loadIndex(course.id) ?: return@courseLoop
            course.chapters.forEach chapterLoop@ { chapter ->
                val chapterContent = index.optJSONObject(chapter.id) ?: return@chapterLoop
                chapter.sections.forEach sectionLoop@ { section ->
                    val key = "${course.id}/${section.id}"
                    if (key in seen) return@sectionLoop
                    val content = resolveLessonText(chapterContent, chapter.id, section.id)
                    if (!content.contains(query, ignoreCase = true)) return@sectionLoop
                    results += SearchResult(
                        courseId = course.id,
                        courseTitle = course.title,
                        chapterId = chapter.id,
                        sectionId = section.id,
                        title = section.title,
                        snippet = snippetAround(normalizeLessonContent(content), query)
                    )
                    seen += key
                    if (results.size >= limit) return results
                }
            }
        }
        return results
    }

    /** Best-effort refresh. The bundled copy remains the immediate/offline path. */
    fun refreshCourseIndex(courseId: String): Boolean {
        val directory = indexDirectory(courseId)
        val request = Request.Builder()
            .url("$webRoot/search-index/$directory.json")
            .header("Accept", "application/json")
            .get()
            .build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use false
                val raw = response.body?.string().orEmpty()
                if (raw.isBlank()) return@use false
                val parsed = JSONObject(raw)
                if (parsed.length() == 0) return@use false
                val target = cacheFile(directory)
                val temporary = File(target.parentFile, "${target.name}.tmp")
                temporary.writeText(raw, Charsets.UTF_8)
                if (!temporary.renameTo(target)) {
                    target.writeText(raw, Charsets.UTF_8)
                    temporary.delete()
                }
                memoryIndexes[directory] = parsed
                true
            }
        }.getOrDefault(false)
    }

    private fun loadIndex(courseId: String): JSONObject? {
        val directory = indexDirectory(courseId)
        memoryIndexes[directory]?.let { return it }

        val cachedFile = cacheFile(directory)
        if (cachedFile.isFile) {
            cachedFile.runCatchingRead()
                ?.let { raw -> runCatching { JSONObject(raw) }.getOrNull() }
                ?.let { parsed ->
                    memoryIndexes[directory] = parsed
                    return parsed
                }
            // A partial/corrupt refresh must never mask the known-good APK copy.
            cachedFile.delete()
        }

        val bundled = readAssetText("search-index/$directory.json") ?: return null
        return runCatching { JSONObject(bundled) }.getOrNull()?.also {
            memoryIndexes[directory] = it
        }
    }

    private fun cacheFile(directory: String) = File(cacheDirectory, "$directory.json")

    private fun resolveLessonText(chapter: JSONObject?, chapterId: String, sectionId: String): String {
        if (chapter == null) return ""
        chapter.opt(sectionId).asLessonText()?.takeIf { it.isNotBlank() }?.let { return it }

        // Navigation metadata can describe nested sub-sections (for example
        // 2.2.4) while the generated index stores their parent lesson (2.2).
        var candidate = sectionId.substringBeforeLast('.', "")
        while (candidate.isNotBlank()) {
            chapter.opt(candidate).asLessonText()?.takeIf { it.isNotBlank() }?.let { return it }
            candidate = candidate.substringBeforeLast('.', "")
        }

        // Lecture-style courses store one source under the bare chapter id and
        // expose several sidebar anchors that all belong to that lecture.
        chapter.opt(chapterId).asLessonText()?.takeIf { it.isNotBlank() }?.let { return it }
        return ""
    }

    private fun readAssetText(path: String): String? = runCatching {
        appContext.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
    }.getOrNull()

    private fun parseCatalog(raw: String): List<Course> = runCatching {
        val root = JSONObject(raw)
        val array = root.optJSONArray("courses") ?: JSONArray()
        buildList {
            for (courseIndex in 0 until array.length()) {
                val item = array.optJSONObject(courseIndex) ?: continue
                val id = item.optString("id").trim()
                if (id.isBlank()) continue
                val chaptersArray = item.optJSONArray("chapters") ?: JSONArray()
                val chapters = buildList {
                    for (chapterIndex in 0 until chaptersArray.length()) {
                        val chapterObject = chaptersArray.optJSONObject(chapterIndex) ?: continue
                        val chapterId = chapterObject.optString("id").trim()
                        if (chapterId.isBlank()) continue
                        val sectionsArray = chapterObject.optJSONArray("sections") ?: JSONArray()
                        val sections = buildList {
                            for (sectionIndex in 0 until sectionsArray.length()) {
                                val sectionObject = sectionsArray.optJSONObject(sectionIndex) ?: continue
                                val sectionId = sectionObject.optString("id").trim()
                                if (sectionId.isBlank()) continue
                                add(
                                    Section(
                                        id = sectionId,
                                        title = sectionObject.optString("title").ifBlank { "סעיף $sectionId" },
                                        preview = sectionObject.optString("preview")
                                    )
                                )
                            }
                        }
                        if (sections.isNotEmpty()) {
                            add(
                                Chapter(
                                    id = chapterId,
                                    title = chapterObject.optString("title").ifBlank { "פרק $chapterId" },
                                    sections = sections
                                )
                            )
                        }
                    }
                }
                add(
                    Course(
                        id = id,
                        title = item.optString("title").ifBlank { id },
                        description = item.optString("description"),
                        category = item.optString("category", "other"),
                        iconId = item.optString("iconId", "book"),
                        chapters = chapters,
                        isAvailable = item.optBoolean("isAvailable", true)
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    companion object {
        private const val CATALOG_ASSET = "search-index/catalog.json"
        private const val MAX_MEMORY_INDEXES = 4

        fun indexDirectory(courseId: String): String = when (courseId.trim().lowercase()) {
            "history-bagrut" -> "history"
            "lashon-bagrut" -> "lashon"
            else -> courseId.trim().lowercase()
        }
    }
}

private fun File.runCatchingRead(): String? = runCatching { readText(Charsets.UTF_8) }.getOrNull()

private fun Any?.asLessonText(): String? = when (this) {
    is String -> this
    is JSONObject -> optString("content").ifBlank { optString("text") }.takeIf(String::isNotBlank)
    else -> null
}

/**
 * Search-index entries originate in TSX. They are intentionally permissive on
 * the web, but the Android reader needs clean prose and valid TeX delimiters.
 */
internal fun normalizeLessonContent(source: String): String {
    if (source.isBlank()) return ""
    // Bundled indexes already contain the main component's rendered expression.
    // Searching for the last `return (` here is unsafe: lessons often include
    // code examples or nested render callbacks with their own return statement.
    val visibleSource = source
    // Math passed as a component property would otherwise disappear together
    // with the JSX tag. Convert it before the generic markup cleanup.
    var text = replaceMathComponentTags(visibleSource)
    text = text
        .replace("&gt;", ">")
        .replace("&lt;", "<")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&#x27;", "'")
        .replace(Regex("""\{/\*[\s\S]*?\*/\}"""), "\n")
        .replace(Regex("""/\*[\s\S]*?\*/"""), "\n")
        .replace(Regex("""(?m)^\s*import\s+.+?;\s*$"""), "")
        .replace(Regex("""\bexport\s+default\b[^;]*;?"""), " ")
        .replace(Regex("""\breturn\s*\("""), " ")
        .replace(Regex("""\{\s*String\.raw`([\s\S]*?)`\s*\}""")) { match ->
            "\\(${normalizeLatex(match.groupValues[1])}\\)"
        }

    // TSX text expressions such as {'\\frac{a}{b}'} and {'ordinary text'}.
    text = Regex("""\{\s*'((?:\\.|[^'])*)'\s*\}""").replace(text) { match ->
        normalizeTsxString(match.groupValues[1])
    }
    text = Regex("""\{\s*"((?:\\.|[^"])*)"\s*\}""").replace(text) { match ->
        normalizeTsxString(match.groupValues[1])
    }

    // The shared Math component can be inline or block-level. At this point
    // its string expression has already been decoded, so only the wrapper and
    // delimiter type need to be normalized.
    text = Regex("""<Math\b([^>]*)>([\s\S]*?)</Math>""").replace(text) { match ->
        val display = Regex("""\bblock\b""").containsMatchIn(match.groupValues[1])
        wrapLatex(unwrapLatex(match.groupValues[2]), display)
    }

    // Preserve document hierarchy before removing styling-only JSX. Custom
    // lesson blocks become Markdown sections; regular HTML keeps paragraphs,
    // lists, quotes and code readable in the native renderer.
    text = text
        .replace(Regex("""<[A-Z][A-Za-z0-9.]*\b[^>]*\btitle="([^"]+)"[^>]*>""")) { match ->
            "\n\n## ${match.groupValues[1]}\n\n"
        }
        .replace(Regex("""<[A-Z][A-Za-z0-9.]*\b[^>]*\btitle='([^']+)'[^>]*>""")) { match ->
            "\n\n## ${match.groupValues[1]}\n\n"
        }
        .replace(Regex("""<h1\b[^>]*>""", RegexOption.IGNORE_CASE), "\n\n# ")
        .replace(Regex("""</h1\s*>""", RegexOption.IGNORE_CASE), "\n\n")
        .replace(Regex("""<h2\b[^>]*>""", RegexOption.IGNORE_CASE), "\n\n## ")
        .replace(Regex("""</h2\s*>""", RegexOption.IGNORE_CASE), "\n\n")
        .replace(Regex("""<h[3-6]\b[^>]*>""", RegexOption.IGNORE_CASE), "\n\n### ")
        .replace(Regex("""</h[3-6]\s*>""", RegexOption.IGNORE_CASE), "\n\n")
        .replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("""<li\b[^>]*>""", RegexOption.IGNORE_CASE), "\n- ")
        .replace(Regex("""</li\s*>""", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("""<blockquote\b[^>]*>""", RegexOption.IGNORE_CASE), "\n\n> ")
        .replace(Regex("""</blockquote\s*>""", RegexOption.IGNORE_CASE), "\n\n")
        .replace(Regex("""<(?:p|section|article)\b[^>]*>""", RegexOption.IGNORE_CASE), "\n\n")
        .replace(Regex("""</(?:p|section|article)\s*>""", RegexOption.IGNORE_CASE), "\n\n")
        .replace(Regex("""<(?:strong|b)\b[^>]*>""", RegexOption.IGNORE_CASE), "**")
        .replace(Regex("""</(?:strong|b)\s*>""", RegexOption.IGNORE_CASE), "**")
        .replace(Regex("""<code\b[^>]*>""", RegexOption.IGNORE_CASE), "`")
        .replace(Regex("""</code\s*>""", RegexOption.IGNORE_CASE), "`")
        .replace(Regex("""</?(?:Block|ul|ol|div|header|main|footer)\b[^>]*>""", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("""\b(?:className|key|style|onClick|onChange|aria-[\w-]+)=\S+"""), " ")
        .replace(Regex("""</?[A-Za-z][^>]*>"""), " ")
        .let(::stripRemainingJsxExpressions)
        .replace(Regex("""\{\s*\}"""), " ")
        .replace("\\\\(", "\\(")
        .replace("\\\\)", "\\)")
        .replace("\\\\[", "\\[")
        .replace("\\\\]", "\\]")
        .replace(Regex("""\s+\)\s*;?\s*$"""), "")
        .replace(Regex("""\s*[)}]\s*;\s*(?:[)}]\s*;?\s*)*$"""), "")
        .replace(Regex("""\\\\(?=[A-Za-z])""")) { "\\" }
        .replace(Regex("""(?<!\n)\s+(?=(?:הגדרה|טענה|משפט|הוכחה|דוגמה|פתרון|מסקנה|הערה|שימו לב|תרגיל)\s*:)"""), "\n\n")
        .replace(Regex("""(?<!\n)\s+(?=\d+(?:\.\d+)+\s+[\p{L}])"""), "\n\n")
        .replace(Regex("""[ \t]+"""), " ")
        .replace(Regex(""" *\n *"""), "\n")
        .replace(Regex("""\n{3,}"""), "\n\n")
        .trim(' ', '\n', '\t', ';')

    // Mark semantic labels for the lightweight Markdown renderer.
    text = text.replace(
        Regex("""(?m)^(הגדרה|טענה|משפט|הוכחה|דוגמה|פתרון|מסקנה|הערה|שימו לב|תרגיל)\s*:"""),
        "**$1:**"
    )
    return text
}

private fun replaceMathComponentTags(source: String): String {
    var text = source
    fun replace(name: String, display: Boolean) {
        text = Regex("""<$name\s+math=\{\s*String\.raw`([\s\S]*?)`\s*\}\s*/>""").replace(text) { match ->
            wrapLatex(match.groupValues[1], display)
        }
        text = Regex("""<$name\s+math=\{\s*`([\s\S]*?)`\s*\}\s*/>""").replace(text) { match ->
            wrapLatex(match.groupValues[1], display)
        }
        text = Regex("""<$name\s+math="((?:\\.|[^"])*)"\s*/>""").replace(text) { match ->
            wrapLatex(match.groupValues[1], display)
        }
        text = Regex("""<$name\s+math='((?:\\.|[^'])*)'\s*/>""").replace(text) { match ->
            wrapLatex(match.groupValues[1], display)
        }
    }
    replace("BlockMath", display = true)
    replace("InlineMath", display = false)
    return text
}

private fun wrapLatex(raw: String, display: Boolean): String {
    val normalized = normalizeLatex(raw).trim()
    if (normalized.isBlank()) return ""
    return if (display) "\n\n\\[$normalized\\]\n\n" else "\\($normalized\\)"
}

private fun unwrapLatex(raw: String): String {
    val value = raw.trim()
    return when {
        value.startsWith("\\(") && value.endsWith("\\)") -> value.substring(2, value.length - 2)
        value.startsWith("\\[") && value.endsWith("\\]") -> value.substring(2, value.length - 2)
        else -> value
    }
}

/** Removes unresolved JSX expressions without deleting braces inside TeX. */
private fun stripRemainingJsxExpressions(source: String): String {
    val math = mutableListOf<String>()
    val tokenized = Regex("""\\\[[\s\S]*?\\]|\\\([\s\S]*?\\\)|\$\$[\s\S]*?\$\$|(?<!\\)\$[^\n$]+?\$""")
        .replace(source) { match ->
            val index = math.size
            math += match.value
            "@@STUDY_TEX_${index}@@"
        }
    val cleaned = StringBuilder(tokenized.length)
    var index = 0
    while (index < tokenized.length) {
        if (tokenized[index] != '{') {
            cleaned.append(tokenized[index++])
            continue
        }
        var depth = 1
        index++
        while (index < tokenized.length && depth > 0) {
            when (tokenized[index]) {
                '{' -> depth++
                '}' -> depth--
            }
            index++
        }
        cleaned.append(' ')
    }
    return Regex("""@@STUDY_TEX_(\d+)@@""").replace(cleaned.toString()) { match ->
        math.getOrNull(match.groupValues[1].toIntOrNull() ?: -1).orEmpty()
    }
}

private fun normalizeTsxString(raw: String): String {
    val decoded = raw
        .replace("\\'", "'")
        .replace("\\\"", "\"")
        .replace("\\n", "\n")
        .replace(Regex("""\\\\(?=[A-Za-z])""")) { "\\" }
    val looksLikeLatex = Regex("""\\[A-Za-z]+|[_^=]|\\[\(\)\[\]]""").containsMatchIn(decoded)
    return if (looksLikeLatex) "\\(${normalizeLatex(decoded)}\\)" else decoded
}

private fun normalizeLatex(raw: String): String = raw
    .replace(Regex("""\\\\(?=[A-Za-z])""")) { "\\" }
    .trim()

private fun snippetAround(text: String, query: String): String {
    if (text.isBlank()) return ""
    val offset = text.indexOf(query, ignoreCase = true).coerceAtLeast(0)
    val start = (offset - 72).coerceAtLeast(0)
    val end = (start + 240).coerceAtMost(text.length)
    return text.substring(start, end).replace(Regex("""\s+"""), " ").trim()
}
