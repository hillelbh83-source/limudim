package com.hillel.studyzone.data

import android.content.Context
import android.net.Uri
import android.webkit.CookieManager
import com.hillel.studyzone.BuildConfig
import com.hillel.studyzone.model.AccessRequest
import com.hillel.studyzone.model.AdminOverview
import com.hillel.studyzone.model.AdminUser
import com.hillel.studyzone.model.AdminUserDetails
import com.hillel.studyzone.model.Chapter
import com.hillel.studyzone.model.Course
import com.hillel.studyzone.model.Lesson
import com.hillel.studyzone.model.SearchResult
import com.hillel.studyzone.model.Section
import com.hillel.studyzone.model.User
import com.hillel.studyzone.model.GeminiServerKey
import com.hillel.studyzone.model.SystemPasswordStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class ApiException(message: String, val status: Int = 0, val kind: String? = null) : Exception(message)

class StudyZoneApi(context: Context) {
    private val appContext = context.applicationContext
    private val apiBase = BuildConfig.API_BASE_URL.trimEnd('/')
    private val apiRoot = "$apiBase/api"
    private val cookieJar = PersistentCookieJar(appContext, "$apiRoot/".toHttpUrl())
    private val client = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .callTimeout(28, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    private val chatClient = client.newBuilder()
        .readTimeout(120, TimeUnit.SECONDS)
        .callTimeout(150, TimeUnit.SECONDS)
        .build()
    private val content = NativeContentRepository(appContext)
    private val accountCache = appContext.getSharedPreferences(ACCOUNT_CACHE, Context.MODE_PRIVATE)
    private val avatarDirectory = File(appContext.cacheDir, "profile-images").apply { mkdirs() }
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val authGeneration = AtomicLong(0L)

    /** Local, non-blocking startup payload. Call [refreshBootstrap] after the first frame. */
    data class Bootstrap(
        val courses: List<Course>,
        val user: User?,
        val completedSections: Set<String>,
        val bookmarkedSections: Set<String> = emptySet(),
        val courseAccess: CourseAccess = CourseAccess()
    )

    data class CourseAccess(
        val loaded: Boolean = false,
        val allCourses: Boolean = false,
        val allowedCourseIds: Set<String> = emptySet(),
        val publicCourseIds: Set<String> = emptySet(),
        val pendingCourseIds: Set<String> = emptySet(),
        val deniedCourseIds: Set<String> = emptySet()
    )

    data class AccountSnapshot(
        val user: User?,
        val completedSections: Set<String>,
        val bookmarkedSections: Set<String>
    )

    data class LoginResult(
        val user: User,
        val completedSections: Set<String>,
        val bookmarkedSections: Set<String>,
        val courseAccess: CourseAccess
    )

    data class AdminData(
        val overview: AdminOverview,
        val users: List<AdminUser>,
        val requests: List<AccessRequest>,
        val publicCourseIds: Set<String>,
        val geminiKeys: List<GeminiServerKey>,
        val passwords: List<SystemPasswordStatus>
    )

    /**
     * Returns entirely from APK assets and the last sanitized account snapshot.
     * No Render/network cold start is allowed to hold the splash screen hostage.
     */
    suspend fun bootstrap(): Bootstrap = withContext(Dispatchers.IO) {
        val cached = readCachedAccount()
        Bootstrap(
            courses = content.bundledCourses,
            user = cached.user,
            completedSections = cached.completedSections,
            bookmarkedSections = cached.bookmarkedSections
        )
    }

    /** Revalidates the cookie and merges the real profile/progress from the server. */
    suspend fun refreshBootstrap(): Bootstrap = withContext(Dispatchers.IO) {
        val account = refreshAccountInternal(null)
        val courseAccess = loadCourseAccess(account.user)
        // The current production deployment returns 404 here. Running this only
        // after auth revalidation keeps it off the launch path while allowing a
        // newer server to replace bundled metadata without an app update.
        val serverCourses = runCatching {
            requestJson("/mobile/bootstrap").optJSONArray("courses").toCourses()
        }.getOrDefault(emptyList())
        Bootstrap(
            courses = serverCourses.ifEmpty { content.bundledCourses },
            user = account.user,
            completedSections = account.completedSections,
            bookmarkedSections = account.bookmarkedSections,
            courseAccess = courseAccess
        )
    }

    suspend fun refreshAccount(): AccountSnapshot = withContext(Dispatchers.IO) {
        refreshAccountInternal(null)
    }

    suspend fun lesson(courseId: String, sectionId: String): Lesson = withContext(Dispatchers.IO) {
        // The bundled index is the primary source: instant, native and offline.
        content.lesson(courseId, sectionId)?.let { return@withContext it }

        // Forward compatibility for a server deployment that includes /api/mobile.
        val path = "/mobile/courses/${encode(courseId)}/sections/${encode(sectionId)}"
        runCatching { requestJson(path).optJSONObject("lesson")?.toLesson() }
            .getOrNull()
            ?.takeIf { it.content.isNotBlank() }
            ?: FallbackCatalog.lesson(courseId, sectionId)
            ?: throw ApiException("השיעור לא נמצא", 404)
    }

    suspend fun refreshCourseContent(courseId: String): Boolean = withContext(Dispatchers.IO) {
        content.refreshCourseIndex(courseId)
    }

    suspend fun search(query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        content.search(query)
    }

    suspend fun login(email: String, password: String): LoginResult = withContext(Dispatchers.IO) {
        val generation = authGeneration.incrementAndGet()
        val loginBody = postJson(
            "/auth/login",
            JSONObject().put("email", email).put("password", password).put("isGoogle", false)
        )
        finishLogin(generation, loginBody)
    }

    suspend fun loginWithGoogle(idToken: String): LoginResult = withContext(Dispatchers.IO) {
        val generation = authGeneration.incrementAndGet()
        val loginBody = postJson(
            "/auth/login",
            JSONObject()
                .put("isGoogle", true)
                .put("googleIdToken", idToken)
                .put("password", "GOOGLE_AUTH_USER")
        )
        finishLogin(generation, loginBody)
    }

    private suspend fun finishLogin(generation: Long, loginBody: JSONObject): LoginResult {
        val loginUser = loginBody.toUser(apiBase)
        val loginData = loginBody.optJSONObject("data")

        // The current login response already contains the complete public profile
        // and account data. Avoid repeating /auth/me on the critical login path.
        val user = coroutineScope {
            val admin = async {
                runCatching { requestJson("/admin/access").optBoolean("isAdmin") }.getOrDefault(false)
            }
            val profile = async { cacheProfilePhoto(loginUser) }
            profile.await().copy(isAdmin = admin.await() || loginUser.isAdmin)
        }

        val local = readCachedAccount()
        val remote = loginData.toAccountSnapshot(user)
        val sameCachedAccount = local.user?.sameIdentityAs(user) == true
        val snapshot = remote.copy(
            completedSections = if (sameCachedAccount) local.completedSections + remote.completedSections else remote.completedSections,
            bookmarkedSections = if (sameCachedAccount) local.bookmarkedSections + remote.bookmarkedSections else remote.bookmarkedSections
        )
        if (generation == authGeneration.get()) cacheAccount(snapshot)
        return LoginResult(user, snapshot.completedSections, snapshot.bookmarkedSections, loadCourseAccess(user))
    }

    private fun loadCourseAccess(user: User?): CourseAccess {
        val publicIds = runCatching {
            requestJson("/config").optJSONArray("publicCourseIds").toStringSet().normalizedIds()
        }.getOrDefault(emptySet())
        if (user == null) {
            return CourseAccess(loaded = true, allowedCourseIds = publicIds, publicCourseIds = publicIds)
        }
        return runCatching {
            val response = requestJson("/user/course-access")
            val allCourses = response.opt("allowedCourseIds") is String &&
                response.optString("allowedCourseIds") == "*"
            val allowed = if (allCourses) emptySet() else {
                response.optJSONArray("allowedCourseIds").toStringSet().normalizedIds()
            }
            val serverPublic = response.optJSONArray("publicCourseIds").toStringSet().normalizedIds()
            CourseAccess(
                loaded = true,
                allCourses = allCourses || response.optBoolean("isAdmin"),
                allowedCourseIds = allowed + serverPublic + publicIds,
                publicCourseIds = serverPublic + publicIds,
                pendingCourseIds = response.optJSONArray("pendingCourseIds").toStringSet().normalizedIds(),
                deniedCourseIds = response.optJSONArray("deniedCourseIds").toStringSet().normalizedIds()
            )
        }.getOrElse {
            CourseAccess(loaded = true, allowedCourseIds = publicIds, publicCourseIds = publicIds)
        }
    }

    suspend fun register(email: String, password: String, displayName: String): String = withContext(Dispatchers.IO) {
        val response = postJson(
            "/auth/register",
            JSONObject().put("email", email).put("password", password).put("displayName", displayName)
        )
        val message = response.optString("message")
        if (message.contains("verification", ignoreCase = true)) {
            "נשלח קישור אימות לאימייל"
        } else {
            message.ifBlank { "נשלח קישור אימות לאימייל" }
        }
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        authGeneration.incrementAndGet()
        val logoutUrl = (apiRoot + "/auth/logout").toHttpUrl()
        val cookieHeader = cookieJar.loadForRequest(logoutUrl)
            .joinToString("; ") { "${it.name}=${it.value}" }
        // Local logout is immediate even when Render is asleep or the device is offline.
        cookieJar.clear()
        clearCachedAccount()
        runCatching {
            val request = baseRequest(logoutUrl.toString())
                .header("X-Requested-With", "XMLHttpRequest")
                .apply { if (cookieHeader.isNotBlank()) header("Cookie", cookieHeader) }
                .post(JSONObject().toString().toRequestBody(jsonType))
                .build()
            executeJson(request)
        }
        Unit
    }

    suspend fun requestCourseAccess(courseId: String) = withContext(Dispatchers.IO) {
        val title = content.bundledCourses.firstOrNull { it.id == courseId }?.title.orEmpty()
        postJson(
            "/user/course-access/request",
            JSONObject().put("courseId", courseId).put("courseTitle", title)
        )
        Unit
    }

    suspend fun syncProgress(completed: Set<String>) = syncUserData(completed, null)

    /**
     * Syncs progress. Native lesson bookmarks remain local because the current
     * server has no dedicated field for them; savedItems and Pythi bookmarks
     * have different meanings and must not be overwritten.
     */
    suspend fun syncUserData(completed: Set<String>, bookmarks: Set<String>?) = withContext(Dispatchers.IO) {
        val data = JSONObject().put("completedSections", JSONArray(completed.sorted()))
        postJson("/user/sync", JSONObject().put("data", data))

        val cached = readCachedAccount()
        cacheAccount(
            cached.copy(
                completedSections = completed,
                bookmarkedSections = bookmarks ?: cached.bookmarkedSections
            )
        )
    }

    suspend fun loadAdmin(): AdminData = withContext(Dispatchers.IO) {
        coroutineScope {
            val overview = async { requestJson("/admin/overview") }
            val users = async { requestJson("/admin/users?page=1&limit=200") }
            val requests = async { requestJson("/admin/course-access?status=pending&page=1&limit=300") }
            val publicCourses = async { requestJson("/admin/courses/public") }
            val geminiKeys = async { requestJson("/admin/gemini/server-keys") }
            val passwords = async { requestJson("/admin/system/passwords") }
            val overviewBody = overview.await()
            AdminData(
                overview = (overviewBody.optJSONObject("summary") ?: overviewBody).toAdminOverview(),
                users = users.await().optJSONArray("users").toAdminUsers(apiBase),
                requests = requests.await().optJSONArray("requests").toAccessRequests(),
                publicCourseIds = publicCourses.await().optJSONArray("publicCourseIds").toStringSet(),
                geminiKeys = geminiKeys.await().optJSONArray("keys").toGeminiKeys(),
                passwords = passwords.await().toSystemPasswords()
            )
        }
    }

    suspend fun loadAdminUser(userId: String): AdminUserDetails = withContext(Dispatchers.IO) {
        requestJson("/admin/users/${encode(userId)}").toAdminUserDetails(apiBase)
    }

    suspend fun loadAccessRequests(status: String): List<AccessRequest> = withContext(Dispatchers.IO) {
        requestJson("/admin/course-access?status=${encode(status)}&page=1&limit=300")
            .optJSONArray("requests").toAccessRequests()
    }

    suspend fun deleteAdminUser(userId: String) = withContext(Dispatchers.IO) {
        deleteJson("/admin/users/${encode(userId)}")
        Unit
    }

    suspend fun sendAdminMessage(
        targetUserId: String,
        subject: String,
        content: String,
        sendViaEmail: Boolean,
        sendViaInApp: Boolean
    ) = withContext(Dispatchers.IO) {
        postJson(
            "/admin/messages/send",
            JSONObject()
                .put("targetUserId", targetUserId)
                .put("subject", subject)
                .put("content", content)
                .put("isHtml", false)
                .put("sendViaEmail", sendViaEmail)
                .put("sendViaInApp", sendViaInApp)
        )
        Unit
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

    suspend fun clearPublicCourses() = withContext(Dispatchers.IO) {
        postJson("/admin/courses/public/clear", JSONObject())
        Unit
    }

    suspend fun updateSystemSettings(
        requireCoursePassword: Boolean,
        geminiServerKeysEnabled: Boolean,
        shortExplainEnabled: Boolean,
        pythiChatModel: String
    ) = withContext(Dispatchers.IO) {
        postJson(
            "/admin/system/settings",
            JSONObject()
                .put("requireCoursePassword", requireCoursePassword)
                .put("geminiServerKeysEnabled", geminiServerKeysEnabled)
                .put("askPopoverShortExplainEnabled", shortExplainEnabled)
                .put("pythiChatModel", pythiChatModel)
        )
        Unit
    }

    suspend fun createGeminiKey(label: String, apiKey: String): List<GeminiServerKey> = withContext(Dispatchers.IO) {
        postJson(
            "/admin/gemini/server-keys",
            JSONObject().put("label", label).put("apiKey", apiKey).put("enabled", true)
        ).optJSONArray("keys").toGeminiKeys()
    }

    suspend fun updateGeminiKey(
        keyId: String,
        label: String,
        apiKey: String?,
        enabled: Boolean
    ): List<GeminiServerKey> = withContext(Dispatchers.IO) {
        val body = JSONObject().put("label", label).put("enabled", enabled)
        if (!apiKey.isNullOrBlank()) body.put("apiKey", apiKey)
        postJson("/admin/gemini/server-keys/${encode(keyId)}", body)
            .optJSONArray("keys").toGeminiKeys()
    }

    suspend fun clearGeminiCooldown(keyId: String): List<GeminiServerKey> = withContext(Dispatchers.IO) {
        postJson("/admin/gemini/server-keys/${encode(keyId)}/cooldown/clear", JSONObject())
            .optJSONArray("keys").toGeminiKeys()
    }

    suspend fun deleteGeminiKey(keyId: String): List<GeminiServerKey> = withContext(Dispatchers.IO) {
        deleteJson("/admin/gemini/server-keys/${encode(keyId)}")
            .optJSONArray("keys").toGeminiKeys()
    }

    suspend fun updateSystemPassword(type: String, password: String): List<SystemPasswordStatus> = withContext(Dispatchers.IO) {
        postJson("/admin/system/passwords", JSONObject().put("type", type).put("password", password))
        requestJson("/admin/system/passwords").toSystemPasswords()
    }

    suspend fun exportGeminiKeys(): String = withContext(Dispatchers.IO) {
        val request = baseRequest(apiRoot + "/admin/gemini/server-keys/export").get().build()
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw apiError(raw, response.code)
            raw
        }
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
            messages
                .filter { it.second.isNotBlank() }
                .takeLast(18)
                .forEach { (rawRole, text) ->
                    val role = if (rawRole == "assistant") "model" else rawRole
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

        val request = baseRequest(apiRoot + "/gemini/stream")
            .header("Accept", "application/x-ndjson, application/json")
            .post(payload.toString().toRequestBody(jsonType))
            .build()
        val call = chatClient.newCall(request)
        val terminal = AtomicBoolean(false)
        call.enqueue(object : okhttp3.Callback {
            override fun onFailure(call: Call, error: java.io.IOException) {
                if (terminal.compareAndSet(false, true)) onError(error)
            }

            override fun onResponse(call: Call, response: okhttp3.Response) {
                response.use {
                    if (!it.isSuccessful) {
                        val raw = it.body?.string().orEmpty()
                        val error = apiError(raw, it.code)
                        if (terminal.compareAndSet(false, true)) onError(error)
                        return
                    }
                    try {
                        val source = it.body?.source() ?: throw ApiException("השרת החזיר תשובה ריקה")
                        var fullText = ""
                        var receivedFinal = false
                        while (!source.exhausted()) {
                            val rawLine = source.readUtf8Line() ?: break
                            val line = rawLine.trim().removePrefix("data:").trim()
                            if (line.isBlank() || line == "[DONE]") continue
                            val event = runCatching { JSONObject(line) }.getOrNull() ?: continue
                            when (event.optString("type")) {
                                "chunk", "delta" -> {
                                    val delta = event.optString("delta").ifBlank { event.optString("text") }
                                    if (delta.isNotEmpty()) {
                                        fullText += delta
                                        onDelta(fullText)
                                    }
                                }
                                "final", "done" -> {
                                    val finalText = event.optString("text")
                                    if (fullText.isBlank() && finalText.isNotBlank()) {
                                        fullText = finalText
                                        onDelta(fullText)
                                    }
                                    receivedFinal = true
                                }
                                "error" -> throw ApiException(
                                    event.optString("error").ifBlank { "הצ׳אט אינו זמין כרגע" },
                                    kind = event.optString("kind").takeIf(String::isNotBlank)
                                )
                            }
                        }
                        if (fullText.isBlank()) {
                            val suffix = if (receivedFinal) "" else " (החיבור נסגר לפני אירוע הסיום)"
                            throw ApiException("לא התקבלה תשובה מ־Pythi$suffix")
                        }
                        if (terminal.compareAndSet(false, true)) onDone()
                    } catch (error: Throwable) {
                        if (terminal.compareAndSet(false, true)) onError(error)
                    }
                }
            }
        })
        return call
    }

    private suspend fun refreshAccountInternal(seedUser: JSONObject?): AccountSnapshot {
        val generation = authGeneration.get()
        val meBody = runCatching { requestJson("/auth/me") }.getOrElse { error ->
            if (error is ApiException && error.status == 401) {
                if (generation == authGeneration.get()) clearCachedAccount()
                return AccountSnapshot(null, emptySet(), emptySet())
            }
            return readCachedAccount()
        }
        val meObject = meBody.optJSONObject("user") ?: seedUser
            ?: return AccountSnapshot(null, emptySet(), emptySet())
        var user = meObject.toUser(apiBase)

        val userDataBody = runCatching { postJson("/user/data", JSONObject()) }.getOrNull()
        if (userDataBody != null) {
            val refreshedPhoto = absoluteUrl(userDataBody.optString("photoURL")).takeIf { it.isNotBlank() }
            user = user.copy(
                displayName = userDataBody.optString("displayName").ifBlank { user.displayName },
                photoUrl = refreshedPhoto ?: user.photoUrl
            )
        }
        val profileUser = user
        user = coroutineScope {
            val admin = async {
                runCatching { requestJson("/admin/access").optBoolean("isAdmin") }.getOrDefault(false)
            }
            val profile = async { cacheProfilePhoto(profileUser) }
            profile.await().copy(isAdmin = admin.await() || profileUser.isAdmin)
        }

        val data = userDataBody?.optJSONObject("data") ?: meObject.optJSONObject("data")
        val cached = readCachedAccount()
        val remote = data.toAccountSnapshot(user)
        val sameCachedAccount = cached.user?.sameIdentityAs(user) == true
        return remote.copy(
            completedSections = if (sameCachedAccount) cached.completedSections + remote.completedSections else remote.completedSections,
            bookmarkedSections = if (sameCachedAccount) cached.bookmarkedSections + remote.bookmarkedSections else remote.bookmarkedSections
        ).also { snapshot ->
            if (generation == authGeneration.get()) cacheAccount(snapshot)
        }
    }

    private fun cacheProfilePhoto(user: User): User {
        val remoteUrl = user.photoUrl?.takeIf { it.startsWith("http://") || it.startsWith("https://") } ?: return user
        val identity = user.userId.ifBlank { user.email.lowercase() }
        val safeId = identity.hashCode().toUInt().toString(16)
        val version = remoteUrl.hashCode().toUInt().toString(16)
        val filePrefix = "$safeId-$version."
        avatarDirectory.listFiles()
            ?.firstOrNull { it.isFile && it.length() > 0L && it.name.startsWith(filePrefix) }
            ?.let { return user.copy(photoUrl = Uri.fromFile(it).toString()) }
        return runCatching {
            val request = baseRequest(remoteUrl).header("Accept", "image/*").get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use user
                val body = response.body ?: return@use user
                val declaredLength = body.contentLength()
                if (declaredLength > MAX_AVATAR_BYTES) return@use user.copy(photoUrl = null)
                val bytes = body.bytes()
                if (bytes.isEmpty() || bytes.size.toLong() > MAX_AVATAR_BYTES) return@use user.copy(photoUrl = null)
                val contentType = response.header("Content-Type").orEmpty().lowercase()
                if (!contentType.startsWith("image/")) return@use user.copy(photoUrl = null)
                val extension = when {
                    "png" in contentType -> "png"
                    "webp" in contentType -> "webp"
                    "gif" in contentType -> "gif"
                    else -> "jpg"
                }
                avatarDirectory.listFiles()?.filter { it.name.startsWith("$safeId.") }?.forEach { it.delete() }
                avatarDirectory.listFiles()?.filter { it.name.startsWith("$safeId-") }?.forEach { it.delete() }
                val target = File(avatarDirectory, "$filePrefix$extension")
                val temporary = File(avatarDirectory, "$safeId-$version.tmp")
                temporary.writeBytes(bytes)
                if (!temporary.renameTo(target)) {
                    target.writeBytes(bytes)
                    temporary.delete()
                }
                user.copy(photoUrl = Uri.fromFile(target).toString())
            }
        }.getOrDefault(user)
    }

    private fun requestJson(path: String): JSONObject {
        val request = baseRequest(apiRoot + path).get().build()
        return executeJson(request)
    }

    private fun postJson(path: String, body: JSONObject, headers: Map<String, String> = emptyMap()): JSONObject {
        val builder = baseRequest(apiRoot + path).post(body.toString().toRequestBody(jsonType))
        headers.forEach { (name, value) -> builder.header(name, value) }
        return executeJson(builder.build())
    }

    private fun deleteJson(path: String): JSONObject {
        val request = baseRequest(apiRoot + path).delete().build()
        return executeJson(request)
    }

    private fun baseRequest(url: String): Request.Builder = Request.Builder()
        .url(url)
        .header("Accept", "application/json")
        .header("User-Agent", "StudyZone-Android/${BuildConfig.VERSION_NAME}")

    private fun executeJson(request: Request): JSONObject {
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(raw) }.getOrNull()
            if (!response.isSuccessful || json == null || !json.optBoolean("ok", true)) {
                throw apiError(raw, response.code)
            }
            return json
        }
    }

    private fun apiError(raw: String, status: Int): ApiException {
        val json = runCatching { JSONObject(raw) }.getOrNull()
        val nested = json?.optJSONObject("error")
        val serverMessage = when {
            nested != null -> nested.optString("message")
            else -> json?.optString("error").orEmpty().ifBlank { json?.optString("message").orEmpty() }
        }
        val message = localizeServerMessage(serverMessage).ifBlank {
            when (status) {
                401 -> "החיבור לחשבון פג. יש להתחבר מחדש"
                403 -> "אין הרשאה לבצע את הפעולה"
                404 -> "המידע המבוקש לא נמצא"
                429 -> "יש עומס זמני. נסו שוב בעוד רגע"
                in 500..599 -> "שירות StudyZone אינו זמין כרגע"
                else -> "שגיאת שרת ($status)"
            }
        }
        return ApiException(message, status, json?.optString("kind")?.takeIf(String::isNotBlank))
    }

    private fun absoluteUrl(raw: String): String = when {
        raw.isBlank() || raw == "null" -> ""
        raw.startsWith("http://") || raw.startsWith("https://") || raw.startsWith("file:") -> raw
        raw.startsWith("/") -> apiBase + raw
        else -> "$apiBase/${raw.trimStart('/')}"
    }

    private fun cacheAccount(snapshot: AccountSnapshot) {
        if (snapshot.user == null) {
            clearCachedAccount()
            return
        }
        val payload = JSONObject()
            .put("user", snapshot.user.toJson())
            .put("completedSections", JSONArray(snapshot.completedSections.sorted()))
            .put("bookmarkedSections", JSONArray(snapshot.bookmarkedSections.sorted()))
        accountCache.edit().putString(ACCOUNT_VALUE, payload.toString()).apply()
    }

    private fun readCachedAccount(): AccountSnapshot {
        val raw = accountCache.getString(ACCOUNT_VALUE, null) ?: return AccountSnapshot(null, emptySet(), emptySet())
        return runCatching {
            val payload = JSONObject(raw)
            var user = payload.optJSONObject("user")?.toUser(apiBase)
            if (user?.photoUrl?.startsWith("file:") == true) {
                val file = runCatching { File(Uri.parse(user.photoUrl).path.orEmpty()) }.getOrNull()
                if (file?.isFile != true) user = user.copy(photoUrl = null)
            }
            AccountSnapshot(
                user = user,
                completedSections = payload.optJSONArray("completedSections").toStringSet(),
                bookmarkedSections = payload.optJSONArray("bookmarkedSections").toStringSet()
            )
        }.getOrElse {
            clearCachedAccount()
            AccountSnapshot(null, emptySet(), emptySet())
        }
    }

    private fun clearCachedAccount() {
        accountCache.edit().clear().apply()
        avatarDirectory.listFiles()?.forEach { it.delete() }
    }

    private fun encode(value: String) = java.net.URLEncoder.encode(value, Charsets.UTF_8.name())

    companion object {
        private const val ACCOUNT_CACHE = "studyzone_account_cache_v2"
        private const val ACCOUNT_VALUE = "account"
        private const val MAX_AVATAR_BYTES = 5L * 1024L * 1024L
    }
}

private fun localizeServerMessage(raw: String): String {
    val message = raw.trim()
    return when (message.lowercase()) {
        "invalid password", "user not found" -> "האימייל או הסיסמה שגויים"
        "blocked", "account blocked" -> "החשבון חסום. יש לפנות למנהל"
        "use google login" -> "החשבון מחובר דרך Google"
        "please verify your email first.", "account not verified" -> "יש לאמת את כתובת האימייל לפני ההתחברות"
        "user already exists" -> "כבר קיים חשבון עם כתובת האימייל הזו"
        "invalid email" -> "כתובת האימייל אינה תקינה"
        "missing email or password", "missing fields" -> "חסרים פרטי התחברות"
        "forbidden" -> "אין הרשאה לבצע את הפעולה"
        "session expired" -> "החיבור לחשבון פג. יש להתחבר מחדש"
        else -> message
    }
}

private class PersistentCookieJar(context: Context, private val baseUrl: HttpUrl) : CookieJar {
    private val prefs = context.getSharedPreferences("studyzone_cookies", Context.MODE_PRIVATE)
    private val cookies = mutableListOf<Cookie>()
    private val webCookies = CookieManager.getInstance().apply { setAcceptCookie(true) }

    init {
        prefs.getStringSet("cookies", emptySet()).orEmpty().mapNotNullTo(cookies) { Cookie.parse(baseUrl, it) }
        syncToWebView()
    }

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, incoming: List<Cookie>) {
        var changed = false
        incoming.forEach { fresh ->
            changed = cookies.removeAll {
                it.name == fresh.name && it.domain == fresh.domain && it.path == fresh.path
            } || changed
            if (fresh.expiresAt > System.currentTimeMillis()) {
                cookies += fresh
                changed = true
            }
        }
        if (changed) {
            persist()
            syncToWebView()
        }
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        if (cookies.removeAll { it.expiresAt <= now }) persist()
        return cookies.filter { it.matches(url) }
    }

    @Synchronized
    fun clear() {
        cookies.clear()
        prefs.edit().clear().apply()
        webCookies.removeAllCookies(null)
        webCookies.flush()
    }

    private fun persist() {
        prefs.edit().putStringSet("cookies", cookies.map(Cookie::toString).toSet()).apply()
    }

    private fun syncToWebView() {
        val now = System.currentTimeMillis()
        cookies.filter { it.expiresAt > now }.forEach { cookie ->
            val cookieValue = buildString {
                append(cookie.name).append('=').append(cookie.value)
                append("; Path=").append(cookie.path)
                if (cookie.secure) append("; Secure")
                if (cookie.httpOnly) append("; HttpOnly")
                append("; SameSite=None")
            }
            webCookies.setCookie("https://${cookie.domain.removePrefix(".")}", cookieValue)
        }
        webCookies.flush()
    }
}

private fun JSONArray?.toCourses(): List<Course> = buildList {
    val array = this@toCourses ?: return@buildList
    for (index in 0 until array.length()) {
        val item = array.optJSONObject(index) ?: continue
        val id = item.optString("id")
        if (id.isBlank()) continue
        add(
            Course(
                id = id,
                title = item.optString("title").ifBlank { id },
                description = item.optString("description"),
                category = item.optString("category", "other"),
                iconId = item.optString("iconId", "book"),
                isAvailable = item.optBoolean("isAvailable", true),
                chapters = buildList chapterList@ {
                    val chapters = item.optJSONArray("chapters") ?: return@chapterList
                    for (chapterIndex in 0 until chapters.length()) {
                        val chapter = chapters.optJSONObject(chapterIndex) ?: continue
                        val chapterId = chapter.optString("id")
                        val sections = buildList sectionList@ {
                            val sectionArray = chapter.optJSONArray("sections") ?: return@sectionList
                            for (sectionIndex in 0 until sectionArray.length()) {
                                val section = sectionArray.optJSONObject(sectionIndex) ?: continue
                                val sectionId = section.optString("id")
                                if (sectionId.isBlank()) continue
                                add(Section(sectionId, section.optString("title").ifBlank { "סעיף $sectionId" }, section.optString("preview")))
                            }
                        }
                        if (chapterId.isNotBlank() && sections.isNotEmpty()) {
                            add(Chapter(chapterId, chapter.optString("title").ifBlank { "פרק $chapterId" }, sections))
                        }
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
    content = normalizeLessonContent(optString("content")),
    interactiveUrl = "",
    previousSectionId = optNullableString("previousSectionId"),
    nextSectionId = optNullableString("nextSectionId")
)

private fun JSONObject.toUser(apiBase: String): User {
    val rawPhoto = firstString("photoURL", "photoUrl", "picture", "avatar")
    val absolutePhoto = when {
        rawPhoto.isBlank() -> null
        rawPhoto.startsWith("http://") || rawPhoto.startsWith("https://") || rawPhoto.startsWith("file:") -> rawPhoto
        rawPhoto.startsWith("/") -> apiBase + rawPhoto
        else -> "$apiBase/${rawPhoto.trimStart('/')}"
    }
    val email = firstString("email", "mail")
    return User(
        userId = firstString("userId", "uid", "id"),
        email = email,
        displayName = firstString("displayName", "name").ifBlank { email.substringBefore('@') },
        photoUrl = absolutePhoto,
        isAdmin = optBoolean("isAdmin", false),
        isGoogle = optBoolean("isGoogle", false)
    )
}

private fun User.sameIdentityAs(other: User): Boolean {
    if (userId.isNotBlank() && other.userId.isNotBlank()) return userId == other.userId
    return email.isNotBlank() && email.equals(other.email, ignoreCase = true)
}

private fun User.toJson() = JSONObject()
    .put("userId", userId)
    .put("email", email)
    .put("displayName", displayName)
    .put("photoURL", photoUrl)
    .put("isAdmin", isAdmin)
    .put("isGoogle", isGoogle)

private fun JSONObject?.toAccountSnapshot(user: User?): StudyZoneApi.AccountSnapshot {
    val data = this
    val completed = data?.optJSONArray("completedSections").toStringSet()
    val bookmarks = data?.optJSONObject("pythiMemories")?.opt("bookmarks").toBookmarkKeys()
    return StudyZoneApi.AccountSnapshot(user, completed, bookmarks)
}

private fun Any?.toBookmarkKeys(): Set<String> {
    val array = when (this) {
        is JSONArray -> this
        is String -> runCatching { JSONArray(this) }.getOrNull()
        else -> null
    } ?: return emptySet()
    return buildSet {
        for (index in 0 until array.length()) {
            when (val item = array.opt(index)) {
                is String -> if (item.isNotBlank()) add(item)
                is JSONObject -> {
                    val courseId = item.optString("courseId")
                    val sectionId = item.optString("sectionId")
                    if (courseId.isNotBlank() && sectionId.isNotBlank()) add("$courseId::$sectionId")
                }
            }
        }
    }
}

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
    pythiChatModel = optString("pythiChatModel"),
    geminiServerKeysTotal = optInt("geminiServerKeysTotal"),
    geminiServerKeysActive = optInt("geminiServerKeysActive"),
    geminiServerKeysCooldown = optInt("geminiServerKeysCooldown")
)

private fun JSONArray?.toAdminUsers(apiBase: String): List<AdminUser> = buildList {
    val array = this@toAdminUsers ?: return@buildList
    for (index in 0 until array.length()) {
        val item = array.optJSONObject(index) ?: continue
        add(
            AdminUser(
                id = item.firstString("id", "userId", "uid"),
                email = item.optString("email"),
                displayName = item.optString("displayName"),
                isBlocked = item.optBoolean("isBlocked"),
                isVerified = item.optBoolean("isVerified", true),
                createdAt = item.optLongOrNull("createdAt"),
                lastLoginAt = item.optLongOrNull("lastLoginAt"),
                isGoogle = item.optBoolean("isGoogle"),
                photoUrl = item.optNullableString("photoURL")?.let {
                    if (it.startsWith("/")) apiBase + it else it
                }
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
                userId = item.optString("userId"),
                userEmail = item.optString("userEmail"),
                userDisplayName = item.optString("userDisplayName"),
                courseId = item.optString("courseId"),
                courseTitle = item.optString("courseTitle"),
                status = item.optString("status"),
                createdAt = item.optLongOrNull("createdAt"),
                updatedAt = item.optLongOrNull("updatedAt")
            )
        )
    }
}

private fun JSONObject.toAdminUserDetails(apiBase: String): AdminUserDetails {
    val profile = optJSONObject("profile") ?: JSONObject()
    val courseAccess = optJSONObject("courseAccess") ?: JSONObject()
    val metricsObject = optJSONObject("metrics") ?: JSONObject()
    val metrics = buildMap {
        metricsObject.keys().forEach { key -> put(key, metricsObject.optInt(key)) }
    }
    val rawPhoto = profile.optNullableString("photoURL")
    val photo = rawPhoto?.let { if (it.startsWith("/")) apiBase + it else it }
    return AdminUserDetails(
        userId = optString("userId"),
        email = profile.optString("email"),
        displayName = profile.optString("displayName"),
        photoUrl = photo,
        isGoogle = profile.optBoolean("isGoogle"),
        isVerified = profile.optBoolean("isVerified", true),
        isBlocked = profile.optBoolean("isBlocked"),
        createdAt = profile.optLongOrNull("createdAt"),
        lastLoginAt = profile.optLongOrNull("lastLoginAt"),
        achievementsCount = optInt("achievementsCount"),
        apiKeysCount = optJSONArray("apiKeys")?.length() ?: 0,
        metrics = metrics,
        allowedCourseIds = courseAccess.optJSONArray("allowedCourseIds").toStringList(),
        pendingCourseIds = courseAccess.optJSONArray("pendingCourseIds").toStringList(),
        deniedCourseIds = courseAccess.optJSONArray("deniedCourseIds").toStringList()
    )
}

private fun JSONArray?.toGeminiKeys(): List<GeminiServerKey> = buildList {
    val array = this@toGeminiKeys ?: return@buildList
    for (index in 0 until array.length()) {
        val item = array.optJSONObject(index) ?: continue
        add(
            GeminiServerKey(
                id = item.optString("id"),
                label = item.optString("label"),
                maskedKey = item.optString("maskedKey"),
                enabled = item.optBoolean("enabled", true),
                state = item.optString("state"),
                createdAt = item.optLongOrNull("createdAt"),
                updatedAt = item.optLongOrNull("updatedAt"),
                remainingCooldownMs = item.optLong("remainingCooldownMs", 0L),
                lastFailureMessage = item.optString("lastFailureMessage"),
                lastFailureRoute = item.optString("lastFailureRoute"),
                lastFailureModel = item.optString("lastFailureModel"),
                lastFailureStatus = item.opt("lastFailureStatus").let { value ->
                    when (value) {
                        is Number -> value.toInt()
                        is String -> value.toIntOrNull()
                        else -> null
                    }
                }
            )
        )
    }
}

private fun JSONObject.toSystemPasswords(): List<SystemPasswordStatus> {
    val statuses = optJSONObject("passwords") ?: JSONObject()
    val labels = optJSONObject("labels") ?: JSONObject()
    return buildList {
        statuses.keys().forEach { type ->
            val status = statuses.optJSONObject(type)
            add(
                SystemPasswordStatus(
                    type = type,
                    label = labels.optString(type).ifBlank { type },
                    configured = status?.optBoolean("configured") ?: statuses.optBoolean(type),
                    hashed = status?.optBoolean("hashed") ?: false
                )
            )
        }
    }
}

private fun JSONObject.firstString(vararg keys: String): String {
    keys.forEach { key -> optString(key).takeIf { it.isNotBlank() && it != "null" }?.let { return it } }
    return ""
}

private fun JSONObject.optNullableString(key: String): String? =
    optString(key).takeIf { it.isNotBlank() && it != "null" }

private fun JSONObject.optLongOrNull(key: String): Long? {
    val value = opt(key)
    return when (value) {
        is Number -> value.toLong()
        is String -> value.toLongOrNull()
        else -> null
    }?.takeIf { it > 0L }
}

private fun JSONArray?.toStringSet(): Set<String> = buildSet {
    val array = this@toStringSet ?: return@buildSet
    for (index in 0 until array.length()) {
        array.optString(index).takeIf { it.isNotBlank() }?.let(::add)
    }
}

private fun Set<String>.normalizedIds(): Set<String> =
    mapTo(linkedSetOf()) { it.trim().lowercase() }.filterTo(linkedSetOf()) { it.isNotBlank() }

private fun JSONArray?.toStringList(): List<String> = buildList {
    val array = this@toStringList ?: return@buildList
    for (index in 0 until array.length()) {
        array.optString(index).takeIf { it.isNotBlank() }?.let(::add)
    }
}
