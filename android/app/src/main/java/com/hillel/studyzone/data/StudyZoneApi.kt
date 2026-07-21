package com.hillel.studyzone.data

import android.content.Context
import com.hillel.studyzone.BuildConfig
import com.hillel.studyzone.model.AccessRequest
import com.hillel.studyzone.model.AdminOverview
import com.hillel.studyzone.model.AdminUser
import com.hillel.studyzone.model.Chapter
import com.hillel.studyzone.model.Course
import com.hillel.studyzone.model.Lesson
import com.hillel.studyzone.model.SearchResult
import com.hillel.studyzone.model.Section
import com.hillel.studyzone.model.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ApiException(message: String, val status: Int = 0) : Exception(message)

class StudyZoneApi(context: Context) {
    private val apiRoot = BuildConfig.API_BASE_URL.trimEnd('/') + "/api"
    private val client = OkHttpClient.Builder()
        .cookieJar(PersistentCookieJar(context, "$apiRoot/".toHttpUrl()))
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(75, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    data class Bootstrap(val courses: List<Course>, val user: User?, val completedSections: Set<String>)
    data class AdminData(
        val overview: AdminOverview,
        val users: List<AdminUser>,
        val requests: List<AccessRequest>,
        val publicCourseIds: Set<String>
    )

    suspend fun bootstrap(): Bootstrap = withContext(Dispatchers.IO) {
        val body = runCatching { requestJson("/mobile/bootstrap") }.getOrNull()
        if (body == null) {
            val user = runCatching { requestJson("/auth/me").optJSONObject("user")?.toUser() }.getOrNull()
            return@withContext Bootstrap(FallbackCatalog.courses, user, emptySet())
        }
        val user = body.optJSONObject("user")?.toUser()
        val completed = if (user != null) {
            runCatching {
                postJson("/user/data", JSONObject())
                    .optJSONObject("data")
                    ?.optJSONArray("completedSections")
                    .toStringSet()
            }.getOrDefault(emptySet())
        } else emptySet()
        Bootstrap(body.optJSONArray("courses").toCourses(), user, completed)
    }

    suspend fun lesson(courseId: String, sectionId: String): Lesson = withContext(Dispatchers.IO) {
        val path = "/mobile/courses/${encode(courseId)}/sections/${encode(sectionId)}"
        runCatching { requestJson(path).getJSONObject("lesson").toLesson() }
            .getOrElse { FallbackCatalog.lesson(courseId, sectionId) ?: throw it }
    }

    suspend fun search(query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        runCatching {
            requestJson("/mobile/search?q=${encode(query)}")
                .optJSONArray("results")
                .toSearchResults()
        }.getOrElse { FallbackCatalog.search(query) }
    }

    suspend fun login(email: String, password: String): User = withContext(Dispatchers.IO) {
        postJson(
            "/auth/login",
            JSONObject().put("email", email).put("password", password).put("isGoogle", false)
        ).toLoginUser()
    }

    suspend fun register(email: String, password: String, displayName: String): String = withContext(Dispatchers.IO) {
        val response = postJson(
            "/auth/register",
            JSONObject().put("email", email).put("password", password).put("displayName", displayName)
        )
        response.optString("message", "נשלח קישור אימות לאימייל")
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        postJson("/auth/logout", JSONObject(), mapOf("X-Requested-With" to "XMLHttpRequest"))
        Unit
    }

    suspend fun requestCourseAccess(courseId: String) = withContext(Dispatchers.IO) {
        postJson("/user/course-access/request", JSONObject().put("courseId", courseId))
        Unit
    }

    suspend fun syncProgress(completed: Set<String>) = withContext(Dispatchers.IO) {
        postJson(
            "/user/sync",
            JSONObject().put(
                "data",
                JSONObject()
                    .put("completedSections", JSONArray(completed.toList()))
            )
        )
        Unit
    }

    suspend fun loadAdmin(): AdminData = withContext(Dispatchers.IO) {
        val overviewBody = requestJson("/admin/overview")
        val overview = (overviewBody.optJSONObject("summary") ?: overviewBody).toAdminOverview()
        val usersBody = requestJson("/admin/users?page=1&limit=100")
        val requestsBody = requestJson("/admin/course-access?status=pending")
        val publicBody = requestJson("/admin/courses/public")
        AdminData(
            overview = overview,
            users = usersBody.optJSONArray("users").toAdminUsers(),
            requests = requestsBody.optJSONArray("requests").toAccessRequests(),
            publicCourseIds = publicBody.optJSONArray("publicCourseIds").toStringSet()
        )
    }

    suspend fun toggleUserBlock(userId: String): Boolean = withContext(Dispatchers.IO) {
        postJson("/admin/users/${encode(userId)}/block", JSONObject()).optBoolean("isBlocked")
    }

    suspend fun handleAccessRequest(requestId: String, action: String) = withContext(Dispatchers.IO) {
        postJson("/admin/course-access/${encode(requestId)}/${encode(action)}", JSONObject())
        Unit
    }

    suspend fun togglePublicCourse(courseId: String): Set<String> = withContext(Dispatchers.IO) {
        postJson("/admin/courses/public/toggle", JSONObject().put("courseId", courseId))
            .optJSONArray("publicCourseIds").toStringSet()
    }

    suspend fun updateSystemSettings(
        requireCoursePassword: Boolean,
        geminiServerKeysEnabled: Boolean,
        shortExplainEnabled: Boolean
    ) = withContext(Dispatchers.IO) {
        postJson(
            "/admin/system/settings",
            JSONObject()
                .put("requireCoursePassword", requireCoursePassword)
                .put("geminiServerKeysEnabled", geminiServerKeysEnabled)
                .put("askPopoverShortExplainEnabled", shortExplainEnabled)
        )
        Unit
    }

    fun streamChat(
        messages: List<Pair<String, String>>,
        course: Course?,
        lesson: Lesson?,
        onDelta: (String) -> Unit,
        onDone: () -> Unit,
        onError: (Throwable) -> Unit
    ): Call {
        val contents = JSONArray().apply {
            messages.takeLast(18).forEach { (role, text) ->
                put(JSONObject().put("role", role).put("parts", JSONArray().put(JSONObject().put("text", text))))
            }
        }
        val payload = JSONObject()
            .put("contents", contents)
            .put("requestKind", "chat")
            .put("showActionSuggestions", false)
            .put(
                "serverPromptContext",
                JSONObject()
                    .put("courseName", course?.title ?: "StudyZone")
                    .put("currentContext", lesson?.let { "${it.title} (${it.sectionId})" } ?: "מסך הקורסים")
                    .put("courseStructure", course?.chapters?.joinToString("\n") { chapter ->
                        "${chapter.title}: ${chapter.sections.joinToString { it.title }}"
                    }.orEmpty())
            )
        if (lesson != null) {
            payload.put(
                "sectionReference",
                JSONObject()
                    .put("courseId", lesson.courseId)
                    .put("chapterId", lesson.chapterId)
                    .put("sectionId", lesson.sectionId)
            )
        }

        val request = Request.Builder()
            .url(apiRoot + "/gemini/stream")
            .post(payload.toString().toRequestBody(jsonType))
            .build()
        val call = client.newCall(request)
        call.enqueue(object : okhttp3.Callback {
            override fun onFailure(call: Call, e: java.io.IOException) = onError(e)

            override fun onResponse(call: Call, response: okhttp3.Response) {
                response.use {
                    if (!it.isSuccessful) {
                        val message = runCatching { JSONObject(it.body?.string().orEmpty()).optString("error") }
                            .getOrDefault("")
                            .ifBlank { "שגיאת שרת (${it.code})" }
                        onError(ApiException(message, it.code))
                        return
                    }
                    var fullText = ""
                    try {
                        val source = it.body?.source() ?: throw ApiException("השרת החזיר תשובה ריקה")
                        while (!source.exhausted()) {
                            val line = source.readUtf8Line()?.trim().orEmpty()
                            if (line.isBlank()) continue
                            val event = JSONObject(line)
                            when (event.optString("type")) {
                                "chunk" -> {
                                    fullText += event.optString("delta")
                                    onDelta(fullText)
                                }
                                "error" -> throw ApiException(event.optString("error", "הצ׳אט אינו זמין כרגע"))
                            }
                        }
                        onDone()
                    } catch (error: Throwable) {
                        onError(error)
                    }
                }
            }
        })
        return call
    }

    private fun requestJson(path: String): JSONObject {
        val request = Request.Builder().url(apiRoot + path).get().build()
        return executeJson(request)
    }

    private fun postJson(path: String, body: JSONObject, headers: Map<String, String> = emptyMap()): JSONObject {
        val builder = Request.Builder().url(apiRoot + path).post(body.toString().toRequestBody(jsonType))
        headers.forEach { (name, value) -> builder.header(name, value) }
        return executeJson(builder.build())
    }

    private fun executeJson(request: Request): JSONObject {
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
            if (!response.isSuccessful || json.optBoolean("ok", true).not()) {
                throw ApiException(json.optString("error", "שגיאת שרת (${response.code})"), response.code)
            }
            return json
        }
    }

    private fun encode(value: String) = java.net.URLEncoder.encode(value, Charsets.UTF_8.name())
}

private class PersistentCookieJar(context: Context, private val baseUrl: HttpUrl) : CookieJar {
    private val prefs = context.getSharedPreferences("studyzone_cookies", Context.MODE_PRIVATE)
    private val cookies = mutableListOf<Cookie>()

    init {
        prefs.getStringSet("cookies", emptySet()).orEmpty().mapNotNullTo(cookies) { Cookie.parse(baseUrl, it) }
    }

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, incoming: List<Cookie>) {
        incoming.forEach { fresh ->
            cookies.removeAll { it.name == fresh.name && it.domain == fresh.domain && it.path == fresh.path }
            if (fresh.expiresAt > System.currentTimeMillis()) cookies += fresh
        }
        persist()
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        cookies.removeAll { it.expiresAt <= now }
        persist()
        return cookies.filter { it.matches(url) }
    }

    private fun persist() {
        prefs.edit().putStringSet("cookies", cookies.map(Cookie::toString).toSet()).apply()
    }
}

private fun JSONArray?.toCourses(): List<Course> = buildList {
    val array = this@toCourses ?: return@buildList
    for (index in 0 until array.length()) {
        val item = array.optJSONObject(index) ?: continue
        add(
            Course(
                id = item.optString("id"),
                title = item.optString("title"),
                description = item.optString("description"),
                category = item.optString("category", "other"),
                iconId = item.optString("iconId", "book"),
                isAvailable = item.optBoolean("isAvailable", true),
                chapters = buildList {
                    val chapters = item.optJSONArray("chapters") ?: return@buildList
                    for (chapterIndex in 0 until chapters.length()) {
                        val chapter = chapters.optJSONObject(chapterIndex) ?: continue
                        add(
                            Chapter(
                                id = chapter.optString("id"),
                                title = chapter.optString("title"),
                                sections = buildList {
                                    val sections = chapter.optJSONArray("sections") ?: return@buildList
                                    for (sectionIndex in 0 until sections.length()) {
                                        val section = sections.optJSONObject(sectionIndex) ?: continue
                                        add(
                                            Section(
                                                id = section.optString("id"),
                                                title = section.optString("title"),
                                                preview = section.optString("preview")
                                            )
                                        )
                                    }
                                }
                            )
                        )
                    }
                }
            )
        )
    }
}

private fun JSONObject.toLesson() = Lesson(
    courseId = optString("courseId"),
    courseTitle = optString("courseTitle"),
    chapterId = optString("chapterId"),
    sectionId = optString("sectionId"),
    title = optString("title"),
    content = optString("content"),
    interactiveUrl = optString("interactiveUrl"),
    previousSectionId = optString("previousSectionId").ifBlank { null },
    nextSectionId = optString("nextSectionId").ifBlank { null }
)

private fun JSONObject.toUser() = User(
    userId = optString("userId"),
    email = optString("email"),
    displayName = optString("displayName").ifBlank { optString("email").substringBefore('@') },
    photoUrl = optString("photoURL").ifBlank { null },
    isAdmin = optBoolean("isAdmin"),
    isGoogle = optBoolean("isGoogle")
)

private fun JSONObject.toLoginUser() = User(
    userId = optString("userId"),
    email = optString("email"),
    displayName = optString("displayName").ifBlank { optString("email").substringBefore('@') },
    photoUrl = optString("photoURL").ifBlank { null },
    isAdmin = optBoolean("isAdmin"),
    isGoogle = optBoolean("isGoogle")
)

private fun JSONArray?.toSearchResults(): List<SearchResult> = buildList {
    val array = this@toSearchResults ?: return@buildList
    for (index in 0 until array.length()) {
        val item = array.optJSONObject(index) ?: continue
        add(
            SearchResult(
                courseId = item.optString("courseId"),
                courseTitle = item.optString("courseTitle"),
                chapterId = item.optString("chapterId"),
                sectionId = item.optString("sectionId"),
                title = item.optString("title"),
                snippet = item.optString("snippet")
            )
        )
    }
}

private fun JSONObject.toAdminOverview() = AdminOverview(
    totalUsers = optInt("totalUsers"),
    blockedUsers = optInt("blockedUsers"),
    pendingRequests = optInt("pendingRequests"),
    publicCoursesCount = optInt("publicCoursesCount"),
    requireCoursePassword = optBoolean("requireCoursePassword", true),
    geminiServerKeysEnabled = optBoolean("geminiServerKeysEnabled", true),
    askPopoverShortExplainEnabled = optBoolean("askPopoverShortExplainEnabled", false),
    pythiChatModel = optString("pythiChatModel")
)

private fun JSONArray?.toAdminUsers(): List<AdminUser> = buildList {
    val array = this@toAdminUsers ?: return@buildList
    for (index in 0 until array.length()) {
        val item = array.optJSONObject(index) ?: continue
        add(
            AdminUser(
                id = item.optString("id"),
                email = item.optString("email"),
                displayName = item.optString("displayName"),
                isBlocked = item.optBoolean("isBlocked"),
                isVerified = item.optBoolean("isVerified", true),
                createdAt = item.optLong("createdAt").takeIf { it > 0 }
            )
        )
    }
}

private fun JSONArray?.toAccessRequests(): List<AccessRequest> = buildList {
    val array = this@toAccessRequests ?: return@buildList
    for (index in 0 until array.length()) {
        val item = array.optJSONObject(index) ?: continue
        add(
            AccessRequest(
                id = item.optString("id"),
                userEmail = item.optString("userEmail"),
                userDisplayName = item.optString("userDisplayName"),
                courseId = item.optString("courseId"),
                courseTitle = item.optString("courseTitle"),
                status = item.optString("status")
            )
        )
    }
}

private fun JSONArray?.toStringSet(): Set<String> = buildSet {
    val array = this@toStringSet ?: return@buildSet
    for (index in 0 until array.length()) add(array.optString(index))
}
