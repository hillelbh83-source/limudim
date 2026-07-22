package com.hillel.studyzone

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hillel.studyzone.data.FallbackCatalog
import com.hillel.studyzone.data.LocalPreferences
import com.hillel.studyzone.data.LocalSnapshot
import com.hillel.studyzone.data.StudyZoneApi
import com.hillel.studyzone.model.AppSettings
import com.hillel.studyzone.model.ChatMessage
import com.hillel.studyzone.model.Course
import com.hillel.studyzone.model.RootTab
import com.hillel.studyzone.model.ThemeMode
import com.hillel.studyzone.model.UiState
import com.hillel.studyzone.model.User
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import java.util.concurrent.atomic.AtomicReference

class AppViewModel(application: Application) : AndroidViewModel(application) {
    // OkHttp restores its persistent cookie jar during construction. Deferring that work keeps it
    // out of Activity/ViewModel creation and therefore out of the cold-start critical path.
    private val api by lazy { StudyZoneApi(application) }
    private val preferences = LocalPreferences(application)
    // Never gate the first frame on disk or network. The richer cached/server catalog replaces
    // this lightweight fallback without taking the UI away from the user.
    private val mutableState = MutableStateFlow(
        UiState(isBootstrapping = false, courses = FallbackCatalog.courses)
    )
    val state: StateFlow<UiState> = mutableState.asStateFlow()
    val settings: StateFlow<AppSettings> = state
        .map { it.settings }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, mutableState.value.settings)

    private var searchJob: Job? = null
    private var searchGeneration = 0L
    private var lessonJob: Job? = null
    private var lessonGeneration = 0L
    private var bootstrapJob: Job? = null
    private var bootstrapGeneration = 0L
    private var cacheJob: Job? = null
    private var adminJob: Job? = null
    private var adminGeneration = 0L
    private var chatCall: Call? = null
    private var chatRenderJob: Job? = null
    @Volatile private var chatGeneration = 0L
    private val pendingChatText = AtomicReference<String?>(null)

    init {
        viewModelScope.launch {
            // DataStore is fast and decoded off-main, but is still not allowed to delay rendering.
            val local = try {
                preferences.snapshot.first()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                null
            }
            if (local != null) applyLocalSnapshot(local, includeCachedSession = true)

            // Future preference writes only update preference-owned fields. This prevents a stale
            // disk emission from replacing a freshly authenticated server session.
            viewModelScope.launch {
                try {
                    preferences.snapshot.drop(1).collect { snapshot ->
                        applyLocalSnapshot(snapshot, includeCachedSession = false)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Throwable) {
                    // Preference corruption must not take the live server-backed UI down.
                }
            }

            // Replace the tiny first-frame fallback with the full APK-bundled index. This call is
            // strictly local (assets + sanitized account cache) and still runs off the main thread.
            val bundled = try {
                withContext(Dispatchers.IO) { api.bootstrap() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                null
            }
            if (bundled != null) applyBootstrapPayload(bundled, preserveCurrentUser = true)

            // Cookie/profile/progress revalidation is a separate background phase.
            refreshFromServer(preserveCurrentUser = false)
        }
    }

    private fun applyLocalSnapshot(snapshot: LocalSnapshot, includeCachedSession: Boolean) {
        mutableState.update { current ->
            current.copy(
                settings = snapshot.settings,
                completedSections = if (includeCachedSession) snapshot.completed else current.completedSections,
                bookmarkedSections = if (includeCachedSession) snapshot.bookmarks else current.bookmarkedSections,
                user = if (includeCachedSession) snapshot.user else current.user,
                isBootstrapping = false
            )
        }
    }

    fun bootstrap() = refreshFromServer(preserveCurrentUser = false)

    private fun refreshFromServer(preserveCurrentUser: Boolean) {
        val generation = ++bootstrapGeneration
        bootstrapJob?.cancel()
        mutableState.update { current ->
            current.copy(isBootstrapping = current.courses.isEmpty(), error = null)
        }
        bootstrapJob = viewModelScope.launch {
            try {
                // Resolve the lazy network stack (including cookie restoration) off-main.
                val payload = withContext(Dispatchers.IO) { api.refreshBootstrap() }
                if (generation != bootstrapGeneration) return@launch
                applyBootstrapPayload(payload, preserveCurrentUser)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                if (generation != bootstrapGeneration) return@launch
                mutableState.update {
                    it.copy(
                        isBootstrapping = false,
                        // Cached/fallback content remains fully usable while the refresh retries.
                        error = error.userMessage("לא הצלחנו לרענן את הנתונים מהשרת")
                    )
                }
            }
        }
    }

    private fun applyBootstrapPayload(payload: StudyZoneApi.Bootstrap, preserveCurrentUser: Boolean) {
        var resolvedCourses: List<Course> = emptyList()
        var resolvedUser = payload.user
        var resolvedCompleted: Set<String> = emptySet()
        var resolvedBookmarks: Set<String> = emptySet()
        mutableState.update {
            resolvedCourses = preferDetailedCatalog(it.courses, payload.courses)
            val accountChanged = it.user != null && payload.user != null &&
                !it.user.sameIdentityAs(payload.user)
            resolvedUser = payload.user ?: if (preserveCurrentUser) it.user else null
            resolvedCompleted = if (accountChanged) {
                payload.completedSections
            } else {
                it.completedSections + payload.completedSections
            }
            resolvedBookmarks = if (accountChanged) {
                payload.bookmarkedSections
            } else {
                it.bookmarkedSections + payload.bookmarkedSections
            }
            val selectedId = it.selectedCourse?.id
            it.copy(
                isBootstrapping = false,
                courses = resolvedCourses,
                selectedCourse = selectedId?.let { id -> resolvedCourses.firstOrNull { course -> course.id == id } },
                user = resolvedUser,
                courseAccessLoaded = payload.courseAccess.loaded,
                courseAccessAll = payload.courseAccess.allCourses,
                accessibleCourseIds = payload.courseAccess.allowedCourseIds,
                publicAccessCourseIds = payload.courseAccess.publicCourseIds,
                pendingCourseIds = payload.courseAccess.pendingCourseIds,
                deniedCourseIds = payload.courseAccess.deniedCourseIds,
                completedSections = resolvedCompleted,
                bookmarkedSections = resolvedBookmarks,
                error = null
            )
        }

        // Only the newest payload may win the disk cache if local and network phases overlap.
        cacheJob?.cancel()
        cacheJob = viewModelScope.launch {
            preferences.saveRemoteSnapshot(
                user = resolvedUser,
                completed = resolvedCompleted,
                bookmarks = resolvedBookmarks
            )
        }
    }

    fun selectTab(tab: RootTab) {
        cancelLessonRequest()
        mutableState.update {
            it.copy(rootTab = tab, selectedCourse = null, lesson = null, lessonLoading = false)
        }
    }

    fun openCourse(course: Course) {
        cancelLessonRequest()
        val currentCourse = mutableState.value.courses.firstOrNull { it.id == course.id } ?: course
        val snapshot = mutableState.value
        val normalizedId = currentCourse.id.lowercase()
        val mayOpen = currentCourse.isAvailable && snapshot.courseAccessLoaded &&
            (snapshot.courseAccessAll || normalizedId in snapshot.accessibleCourseIds)
        if (!mayOpen) {
            mutableState.update {
                when {
                    !currentCourse.isAvailable -> it.copy(toast = "הקורס עדיין בפיתוח")
                    it.user == null -> it.copy(authOpen = true, toast = "יש להתחבר כדי לגשת לקורס הזה")
                    normalizedId in it.pendingCourseIds -> it.copy(toast = "בקשת הגישה לקורס עדיין ממתינה")
                    normalizedId in it.deniedCourseIds -> it.copy(toast = "בקשת הגישה לקורס לא אושרה")
                    else -> it.copy(toast = "אין לחשבון גישה לקורס הזה")
                }
            }
            return
        }
        mutableState.update {
            it.copy(
                selectedCourse = currentCourse,
                lesson = null,
                lessonLoading = false,
                rootTab = RootTab.COURSES
            )
        }
    }

    fun closeCourse() {
        cancelLessonRequest()
        mutableState.update { it.copy(selectedCourse = null, lesson = null, lessonLoading = false) }
    }

    fun openLesson(courseId: String, sectionId: String) {
        val course = mutableState.value.courses.firstOrNull { it.id == courseId } ?: return
        val access = mutableState.value
        val normalizedId = course.id.lowercase()
        if (!course.isAvailable || !access.courseAccessLoaded ||
            (!access.courseAccessAll && normalizedId !in access.accessibleCourseIds)
        ) {
            openCourse(course)
            return
        }
        if (mutableState.value.lesson?.let { it.courseId == courseId && it.sectionId == sectionId } == true) return
        cancelLessonRequest()
        val generation = lessonGeneration
        mutableState.update { it.copy(selectedCourse = course, lessonLoading = true, lesson = null, error = null) }
        lessonJob = viewModelScope.launch {
            try {
                val lesson = api.lesson(courseId, sectionId)
                if (generation != lessonGeneration) return@launch
                mutableState.update { it.copy(lesson = lesson, lessonLoading = false) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                if (generation != lessonGeneration) return@launch
                mutableState.update {
                    it.copy(lessonLoading = false, toast = error.userMessage("לא הצלחנו לטעון את השיעור"))
                }
            }
        }
    }

    fun closeLesson() {
        cancelLessonRequest()
        mutableState.update { it.copy(lesson = null, lessonLoading = false) }
    }

    private fun cancelLessonRequest() {
        lessonGeneration++
        lessonJob?.cancel()
        lessonJob = null
    }

    fun openAdjacent(sectionId: String?) {
        val courseId = mutableState.value.lesson?.courseId ?: return
        if (!sectionId.isNullOrBlank()) openLesson(courseId, sectionId)
    }

    fun setSearchQuery(query: String) {
        val generation = ++searchGeneration
        searchJob?.cancel()
        if (query.trim().length < 2) {
            mutableState.update {
                it.copy(
                    searchQuery = query,
                    searchResults = emptyList(),
                    searchLoading = false,
                    searchSettledQuery = ""
                )
            }
            return
        }
        // Mark the query as pending immediately. Previously the UI exposed the empty state during
        // this debounce window and then replaced it with the actual results 320 ms later.
        mutableState.update {
            it.copy(
                searchQuery = query,
                searchResults = emptyList(),
                searchLoading = true,
                searchSettledQuery = ""
            )
        }
        searchJob = viewModelScope.launch {
            delay(320)
            if (generation != searchGeneration) return@launch
            val normalized = query.trim()
            try {
                val results = api.search(normalized)
                if (generation != searchGeneration) return@launch
                mutableState.update {
                    it.copy(searchResults = results, searchLoading = false, searchSettledQuery = normalized)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                if (generation != searchGeneration) return@launch
                mutableState.update {
                    it.copy(
                        searchLoading = false,
                        searchSettledQuery = normalized,
                        toast = error.userMessage("החיפוש נכשל")
                    )
                }
            }
        }
    }

    fun toggleCompleted() {
        val lesson = mutableState.value.lesson ?: return
        val key = completionKey(lesson.courseId, lesson.chapterId, lesson.sectionId)
        val next = mutableState.value.completedSections.toggle(key)
        mutableState.update { it.copy(completedSections = next, toast = if (key in next) "השיעור סומן כהושלם" else "סימון ההשלמה הוסר") }
        viewModelScope.launch {
            preferences.saveCompleted(next)
            syncUserData()
        }
    }

    fun toggleBookmark() {
        val lesson = mutableState.value.lesson ?: return
        val key = lessonKey(lesson.courseId, lesson.sectionId)
        val next = mutableState.value.bookmarkedSections.toggle(key)
        mutableState.update { it.copy(bookmarkedSections = next, toast = if (key in next) "נשמר לקריאה מאוחרת" else "הוסר מהשמורים") }
        viewModelScope.launch {
            preferences.saveBookmarks(next)
            syncUserData()
        }
    }

    private suspend fun syncUserData() {
        val snapshot = mutableState.value
        if (snapshot.user == null) return
        runCatching { api.syncUserData(snapshot.completedSections, snapshot.bookmarkedSections) }
    }

    fun setTheme(themeMode: ThemeMode) = updateSettings(mutableState.value.settings.copy(themeMode = themeMode))
    fun setReduceMotion(enabled: Boolean) = updateSettings(mutableState.value.settings.copy(reduceMotion = enabled))
    fun setHaptics(enabled: Boolean) = updateSettings(mutableState.value.settings.copy(haptics = enabled))
    fun setFontScale(scale: Float) = updateSettings(mutableState.value.settings.copy(fontScale = scale.coerceIn(.85f, 1.35f)))
    fun setKeepScreenOn(enabled: Boolean) = updateSettings(mutableState.value.settings.copy(keepScreenOn = enabled))

    private fun updateSettings(settings: AppSettings) {
        mutableState.update { it.copy(settings = settings) }
        viewModelScope.launch { preferences.saveSettings(settings) }
    }

    fun setAuthOpen(open: Boolean) = mutableState.update { it.copy(authOpen = open) }

    fun login(email: String, password: String) {
        if (mutableState.value.authLoading) return
        if (email.isBlank() || password.isBlank()) {
            mutableState.update { it.copy(toast = "יש למלא אימייל וסיסמה") }
            return
        }
        val accountHint = mutableState.value.user
        val localCompletedHint = mutableState.value.completedSections
        val localBookmarksHint = mutableState.value.bookmarkedSections
        viewModelScope.launch {
            mutableState.update { it.copy(authLoading = true) }
            runCatching { api.login(email.trim(), password) }
                .onSuccess { account ->
                    val user = account.user
                    val samePreviousAccount = accountHint?.sameIdentityAs(user) == true
                    val resolvedCompleted = if (samePreviousAccount) {
                        localCompletedHint + account.completedSections
                    } else {
                        account.completedSections
                    }
                    val resolvedBookmarks = if (samePreviousAccount) {
                        localBookmarksHint + account.bookmarkedSections
                    } else {
                        account.bookmarkedSections
                    }
                    // An older launch-time revalidation must not clear the freshly
                    // authenticated session when its pre-login request completes.
                    bootstrapGeneration++
                    bootstrapJob?.cancel()
                    bootstrapJob = null
                    mutableState.update {
                        it.copy(
                            user = user,
                            completedSections = resolvedCompleted,
                            bookmarkedSections = resolvedBookmarks,
                            courseAccessLoaded = account.courseAccess.loaded,
                            courseAccessAll = account.courseAccess.allCourses,
                            accessibleCourseIds = account.courseAccess.allowedCourseIds,
                            publicAccessCourseIds = account.courseAccess.publicCourseIds,
                            pendingCourseIds = account.courseAccess.pendingCourseIds,
                            deniedCourseIds = account.courseAccess.deniedCourseIds,
                            authOpen = false,
                            authLoading = false,
                            toast = "ברוכים הבאים, ${user.displayName}"
                        )
                    }
                    cacheJob?.cancel()
                    try {
                        preferences.saveRemoteSnapshot(
                            user = user,
                            completed = resolvedCompleted,
                            bookmarks = resolvedBookmarks
                        )
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Throwable) {
                        // A cache write failure must not undo a successful login.
                    }
                    val hasLocalStateToMerge = resolvedCompleted != account.completedSections ||
                        resolvedBookmarks != account.bookmarkedSections
                    if (samePreviousAccount && hasLocalStateToMerge) {
                        runCatching { api.syncUserData(resolvedCompleted, resolvedBookmarks) }
                    }
                }
                .onFailure { error -> mutableState.update { it.copy(authLoading = false, toast = error.userMessage("ההתחברות נכשלה")) } }
        }
    }

    fun loginWithGoogle(idToken: String) {
        if (mutableState.value.authLoading || idToken.isBlank()) return
        val accountHint = mutableState.value.user
        val localCompletedHint = mutableState.value.completedSections
        val localBookmarksHint = mutableState.value.bookmarkedSections
        viewModelScope.launch {
            mutableState.update { it.copy(authLoading = true) }
            runCatching { api.loginWithGoogle(idToken) }
                .onSuccess { account ->
                    val user = account.user
                    val samePreviousAccount = accountHint?.sameIdentityAs(user) == true
                    val resolvedCompleted = if (samePreviousAccount) {
                        localCompletedHint + account.completedSections
                    } else {
                        account.completedSections
                    }
                    val resolvedBookmarks = if (samePreviousAccount) {
                        localBookmarksHint + account.bookmarkedSections
                    } else {
                        account.bookmarkedSections
                    }
                    bootstrapGeneration++
                    bootstrapJob?.cancel()
                    bootstrapJob = null
                    mutableState.update {
                        it.copy(
                            user = user,
                            completedSections = resolvedCompleted,
                            bookmarkedSections = resolvedBookmarks,
                            courseAccessLoaded = account.courseAccess.loaded,
                            courseAccessAll = account.courseAccess.allCourses,
                            accessibleCourseIds = account.courseAccess.allowedCourseIds,
                            publicAccessCourseIds = account.courseAccess.publicCourseIds,
                            pendingCourseIds = account.courseAccess.pendingCourseIds,
                            deniedCourseIds = account.courseAccess.deniedCourseIds,
                            authOpen = false,
                            authLoading = false,
                            toast = "ברוך שובך, ${user.displayName} 👋"
                        )
                    }
                    cacheJob?.cancel()
                    runCatching {
                        preferences.saveRemoteSnapshot(
                            user = user,
                            completed = resolvedCompleted,
                            bookmarks = resolvedBookmarks
                        )
                    }
                    if (samePreviousAccount && resolvedCompleted != account.completedSections) {
                        runCatching { api.syncUserData(resolvedCompleted, resolvedBookmarks) }
                    }
                }
                .onFailure { error ->
                    mutableState.update {
                        it.copy(authLoading = false, toast = error.userMessage("ההתחברות עם Google נכשלה"))
                    }
                }
        }
    }

    fun showMessage(message: String) {
        mutableState.update { it.copy(toast = message) }
    }

    fun register(name: String, email: String, password: String) {
        if (mutableState.value.authLoading) return
        if (name.isBlank() || email.isBlank() || password.length < 8) {
            mutableState.update { it.copy(toast = "יש למלא שם, אימייל וסיסמה בת 8 תווים לפחות") }
            return
        }
        viewModelScope.launch {
            mutableState.update { it.copy(authLoading = true) }
            runCatching { api.register(email.trim(), password, name.trim()) }
                .onSuccess { message -> mutableState.update { it.copy(authLoading = false, toast = message) } }
                .onFailure { error -> mutableState.update { it.copy(authLoading = false, toast = error.userMessage("ההרשמה נכשלה")) } }
        }
    }

    fun logout() {
        bootstrapGeneration++
        bootstrapJob?.cancel()
        bootstrapJob = null
        cacheJob?.cancel()
        cacheJob = null
        adminGeneration++
        adminJob?.cancel()
        adminJob = null
        clearChat()
        mutableState.update {
            it.copy(
                user = null,
                completedSections = emptySet(),
                bookmarkedSections = emptySet(),
                courseAccessLoaded = true,
                courseAccessAll = false,
                accessibleCourseIds = it.publicAccessCourseIds,
                pendingCourseIds = emptySet(),
                deniedCourseIds = emptySet(),
                authOpen = false,
                authLoading = false,
                adminOpen = false,
                adminOverview = null,
                adminUsers = emptyList(),
                accessRequests = emptyList(),
                publicCourseIds = emptySet(),
                adminSelectedUser = null,
                adminGeminiKeys = emptyList(),
                adminPasswords = emptyList(),
                adminExportJson = null,
                chatOpen = false,
                chatExpanded = false,
                toast = "התנתקת בהצלחה"
            )
        }
        viewModelScope.launch {
            try {
                preferences.saveRemoteSnapshot(
                    user = null,
                    completed = emptySet(),
                    bookmarks = emptySet()
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                // Server logout still has to run if the local cache is unavailable.
            }
            runCatching { api.logout() }
        }
    }

    fun requestCourseAccess(courseId: String) {
        if (mutableState.value.user == null) {
            setAuthOpen(true)
            return
        }
        viewModelScope.launch {
            runCatching { api.requestCourseAccess(courseId) }
                .onSuccess { mutableState.update { it.copy(toast = "הבקשה נשלחה למנהל") } }
                .onFailure { error -> mutableState.update { it.copy(toast = error.userMessage("שליחת הבקשה נכשלה")) } }
        }
    }

    fun setChatOpen(open: Boolean) {
        mutableState.update { it.copy(chatOpen = open, chatExpanded = if (!open) false else it.chatExpanded) }
    }

    fun setChatExpanded(expanded: Boolean) = mutableState.update { it.copy(chatExpanded = expanded, chatOpen = true) }
    fun setChatInput(input: String) = mutableState.update { it.copy(chatInput = input) }

    fun sendChat() {
        val snapshot = mutableState.value
        val text = snapshot.chatInput.trim()
        if (text.isBlank() || snapshot.chatStreaming) return
        if (snapshot.user == null) {
            mutableState.update { it.copy(authOpen = true, toast = "יש להתחבר כדי לדבר עם Pythi") }
            return
        }

        val userMessage = ChatMessage(role = "user", text = text)
        val modelMessage = ChatMessage(role = "model", text = "", isStreaming = true)
        val messages = snapshot.chatMessages + userMessage + modelMessage
        mutableState.update { it.copy(chatInput = "", chatMessages = messages, chatStreaming = true) }

        val apiMessages = messages.dropLast(1)
            .filterNot { it.isError }
            .map { it.role to it.text }
        val generation = ++chatGeneration
        chatCall?.cancel()
        pendingChatText.set(null)
        startChatRenderer(generation)
        chatCall = try {
            api.streamChat(
                messages = apiMessages,
                course = snapshot.selectedCourse,
                lesson = snapshot.lesson,
                onDelta = { fullText ->
                    if (generation == chatGeneration) pendingChatText.set(fullText)
                },
                onDone = {
                    finishChat(generation, error = null)
                },
                onError = { error ->
                    finishChat(generation, error)
                }
            )
        } catch (error: Throwable) {
            finishChat(generation, error)
            null
        }
    }

    /** Coalesces fast streaming chunks to one state update per frame instead of recomposing per token. */
    private fun startChatRenderer(generation: Long) {
        chatRenderJob?.cancel()
        chatRenderJob = viewModelScope.launch {
            while (isActive && generation == chatGeneration) {
                pendingChatText.getAndSet(null)?.let { text ->
                    mutableState.update { current ->
                        current.copy(chatMessages = current.chatMessages.updateLastModel(text, streaming = true))
                    }
                }
                delay(32)
            }
        }
    }

    private fun finishChat(generation: Long, error: Throwable?) {
        viewModelScope.launch {
            if (generation != chatGeneration) return@launch
            chatRenderJob?.cancel()
            chatRenderJob = null
            val finalText = pendingChatText.getAndSet(null)
            mutableState.update { current ->
                val currentText = current.chatMessages.lastOrNull { it.role == "model" }?.text.orEmpty()
                current.copy(
                    chatStreaming = false,
                    chatMessages = current.chatMessages.updateLastModel(
                        text = error?.userMessage("Pythi לא זמינה כרגע") ?: finalText ?: currentText,
                        streaming = false,
                        isError = error != null
                    )
                )
            }
            chatCall = null
        }
    }

    fun stopChat() {
        chatGeneration++
        chatCall?.cancel()
        chatCall = null
        chatRenderJob?.cancel()
        chatRenderJob = null
        val finalText = pendingChatText.getAndSet(null)
        mutableState.update { current ->
            current.copy(
                chatStreaming = false,
                chatMessages = if (finalText.isNullOrBlank()) {
                    current.chatMessages.updateLastStreaming(false)
                } else {
                    current.chatMessages.updateLastModel(finalText, streaming = false)
                }
            )
        }
    }

    fun clearChat() {
        stopChat()
        mutableState.update { it.copy(chatMessages = emptyList()) }
    }

    fun openAdmin() {
        if (mutableState.value.user?.isAdmin != true) {
            mutableState.update { it.copy(toast = "אין הרשאת מנהל לחשבון זה") }
            return
        }
        mutableState.update { it.copy(adminOpen = true) }
        loadAdmin()
    }

    fun closeAdmin() {
        adminGeneration++
        adminJob?.cancel()
        adminJob = null
        mutableState.update { it.copy(adminOpen = false, adminLoading = false, adminSelectedUser = null) }
    }

    fun loadAdmin() {
        val generation = ++adminGeneration
        adminJob?.cancel()
        adminJob = viewModelScope.launch {
            mutableState.update { it.copy(adminLoading = true) }
            runCatching { api.loadAdmin() }
                .onSuccess { data ->
                    if (generation != adminGeneration) return@onSuccess
                    mutableState.update {
                        it.copy(
                            adminLoading = false,
                            adminOverview = data.overview,
                            adminUsers = data.users,
                            accessRequests = data.requests,
                            publicCourseIds = data.publicCourseIds,
                            adminGeminiKeys = data.geminiKeys,
                            adminPasswords = data.passwords
                        )
                    }
                }
                .onFailure { error ->
                    if (generation != adminGeneration) return@onFailure
                    mutableState.update { it.copy(adminLoading = false, toast = error.userMessage("טעינת הניהול נכשלה")) }
                }
        }
    }

    fun toggleUserBlock(userId: String) {
        viewModelScope.launch {
            runCatching { api.toggleUserBlock(userId) }
                .onSuccess { blocked ->
                    mutableState.update { current ->
                        if (!current.adminOpen || current.user?.isAdmin != true) current else current.copy(
                            adminUsers = current.adminUsers.map { if (it.id == userId) it.copy(isBlocked = blocked) else it },
                            adminSelectedUser = current.adminSelectedUser?.let {
                                if (it.userId == userId) it.copy(isBlocked = blocked) else it
                            }
                        )
                    }
                }
                .onFailure { error -> mutableState.update { it.copy(toast = error.userMessage("הפעולה נכשלה")) } }
        }
    }

    fun selectAdminUser(userId: String) {
        viewModelScope.launch {
            mutableState.update { it.copy(adminLoading = true, adminSelectedUser = null) }
            runCatching { api.loadAdminUser(userId) }
                .onSuccess { details -> mutableState.update { it.copy(adminLoading = false, adminSelectedUser = details) } }
                .onFailure { error ->
                    mutableState.update { it.copy(adminLoading = false, toast = error.userMessage("טעינת המשתמש נכשלה")) }
                }
        }
    }

    fun deleteAdminUser(userId: String) {
        viewModelScope.launch {
            mutableState.update { it.copy(adminLoading = true) }
            runCatching { api.deleteAdminUser(userId) }
                .onSuccess {
                    mutableState.update {
                        it.copy(
                            adminLoading = false,
                            adminUsers = it.adminUsers.filterNot { user -> user.id == userId },
                            adminSelectedUser = null,
                            toast = "המשתמש נמחק"
                        )
                    }
                }
                .onFailure { error ->
                    mutableState.update { it.copy(adminLoading = false, toast = error.userMessage("מחיקת המשתמש נכשלה")) }
                }
        }
    }

    fun sendAdminMessage(targetUserId: String, subject: String, content: String, email: Boolean, inApp: Boolean) {
        if (content.isBlank() || (!email && !inApp)) {
            mutableState.update { it.copy(toast = "יש לכתוב הודעה ולבחור אמצעי שליחה") }
            return
        }
        viewModelScope.launch {
            mutableState.update { it.copy(adminLoading = true) }
            runCatching { api.sendAdminMessage(targetUserId, subject.trim(), content.trim(), email, inApp) }
                .onSuccess { mutableState.update { it.copy(adminLoading = false, toast = "ההודעה נשלחה") } }
                .onFailure { error ->
                    mutableState.update { it.copy(adminLoading = false, toast = error.userMessage("שליחת ההודעה נכשלה")) }
                }
        }
    }

    fun loadAdminRequests(status: String) {
        viewModelScope.launch {
            mutableState.update { it.copy(adminLoading = true) }
            runCatching { api.loadAccessRequests(status) }
                .onSuccess { requests -> mutableState.update { it.copy(adminLoading = false, accessRequests = requests) } }
                .onFailure { error ->
                    mutableState.update { it.copy(adminLoading = false, toast = error.userMessage("טעינת הבקשות נכשלה")) }
                }
        }
    }

    fun handleAccessRequest(requestId: String, action: String) {
        viewModelScope.launch {
            runCatching { api.handleAccessRequest(requestId, action) }
                .onSuccess {
                    if (mutableState.value.adminOpen && mutableState.value.user?.isAdmin == true) loadAdmin()
                }
                .onFailure { error -> mutableState.update { it.copy(toast = error.userMessage("הפעולה נכשלה")) } }
        }
    }

    fun togglePublicCourse(courseId: String) {
        viewModelScope.launch {
            runCatching { api.togglePublicCourse(courseId) }
                .onSuccess { ids ->
                    mutableState.update {
                        if (!it.adminOpen || it.user?.isAdmin != true) it else it.copy(
                            publicCourseIds = ids,
                            adminOverview = it.adminOverview?.copy(publicCoursesCount = ids.size)
                        )
                    }
                }
                .onFailure { error -> mutableState.update { it.copy(toast = error.userMessage("עדכון הקורס נכשל")) } }
        }
    }

    fun clearPublicCourses() {
        viewModelScope.launch {
            runCatching { api.clearPublicCourses() }
                .onSuccess {
                    mutableState.update {
                        it.copy(
                            publicCourseIds = emptySet(),
                            adminOverview = it.adminOverview?.copy(publicCoursesCount = 0),
                            toast = "כל הקורסים הציבוריים נסגרו"
                        )
                    }
                }
                .onFailure { error -> mutableState.update { it.copy(toast = error.userMessage("סגירת הקורסים נכשלה")) } }
        }
    }

    fun updateAdminSettings(requirePassword: Boolean, geminiEnabled: Boolean, shortExplain: Boolean, model: String) {
        viewModelScope.launch {
            runCatching { api.updateSystemSettings(requirePassword, geminiEnabled, shortExplain, model) }
                .onSuccess {
                    mutableState.update {
                        if (!it.adminOpen || it.user?.isAdmin != true) it else it.copy(
                            adminOverview = it.adminOverview?.copy(
                                requireCoursePassword = requirePassword,
                                geminiServerKeysEnabled = geminiEnabled,
                                askPopoverShortExplainEnabled = shortExplain,
                                pythiChatModel = model
                            ),
                            toast = "הגדרות המערכת נשמרו"
                        )
                    }
                }
                .onFailure { error -> mutableState.update { it.copy(toast = error.userMessage("שמירת ההגדרות נכשלה")) } }
        }
    }

    fun createGeminiKey(label: String, key: String) {
        if (key.isBlank()) return
        viewModelScope.launch {
            mutableState.update { it.copy(adminLoading = true) }
            runCatching { api.createGeminiKey(label.trim(), key.trim()) }
                .onSuccess { keys -> mutableState.update { it.copy(adminLoading = false, adminGeminiKeys = keys, toast = "המפתח נוסף") } }
                .onFailure { error -> mutableState.update { it.copy(adminLoading = false, toast = error.userMessage("הוספת המפתח נכשלה")) } }
        }
    }

    fun updateGeminiKey(keyId: String, label: String, apiKey: String?, enabled: Boolean) {
        viewModelScope.launch {
            mutableState.update { it.copy(adminLoading = true) }
            runCatching { api.updateGeminiKey(keyId, label.trim(), apiKey?.trim(), enabled) }
                .onSuccess { keys -> mutableState.update { it.copy(adminLoading = false, adminGeminiKeys = keys, toast = "המפתח עודכן") } }
                .onFailure { error -> mutableState.update { it.copy(adminLoading = false, toast = error.userMessage("עדכון המפתח נכשל")) } }
        }
    }

    fun clearGeminiCooldown(keyId: String) {
        viewModelScope.launch {
            runCatching { api.clearGeminiCooldown(keyId) }
                .onSuccess { keys -> mutableState.update { it.copy(adminGeminiKeys = keys, toast = "ה־cooldown נוקה") } }
                .onFailure { error -> mutableState.update { it.copy(toast = error.userMessage("ניקוי ה־cooldown נכשל")) } }
        }
    }

    fun deleteGeminiKey(keyId: String) {
        viewModelScope.launch {
            runCatching { api.deleteGeminiKey(keyId) }
                .onSuccess { keys -> mutableState.update { it.copy(adminGeminiKeys = keys, toast = "המפתח נמחק") } }
                .onFailure { error -> mutableState.update { it.copy(toast = error.userMessage("מחיקת המפתח נכשלה")) } }
        }
    }

    fun updateSystemPassword(type: String, password: String) {
        if (password.isBlank()) return
        viewModelScope.launch {
            mutableState.update { it.copy(adminLoading = true) }
            runCatching { api.updateSystemPassword(type, password.trim()) }
                .onSuccess { passwords ->
                    mutableState.update { it.copy(adminLoading = false, adminPasswords = passwords, toast = "הסיסמה עודכנה") }
                }
                .onFailure { error -> mutableState.update { it.copy(adminLoading = false, toast = error.userMessage("עדכון הסיסמה נכשל")) } }
        }
    }

    fun exportGeminiKeys() {
        viewModelScope.launch {
            runCatching { api.exportGeminiKeys() }
                .onSuccess { json -> mutableState.update { it.copy(adminExportJson = json) } }
                .onFailure { error -> mutableState.update { it.copy(toast = error.userMessage("ייצוא המפתחות נכשל")) } }
        }
    }

    fun consumeAdminExport() = mutableState.update { it.copy(adminExportJson = null) }

    fun consumeToast() = mutableState.update { it.copy(toast = null) }

    fun handleDeepLink(courseId: String?, sectionId: String?) {
        if (courseId.isNullOrBlank()) return
        val course = mutableState.value.courses.firstOrNull { it.id == courseId } ?: return
        if (sectionId.isNullOrBlank()) openCourse(course) else openLesson(courseId, sectionId)
    }

    override fun onCleared() {
        chatGeneration++
        chatCall?.cancel()
        chatRenderJob?.cancel()
        searchJob?.cancel()
        lessonJob?.cancel()
        bootstrapJob?.cancel()
        cacheJob?.cancel()
        adminJob?.cancel()
        super.onCleared()
    }
}

private fun lessonKey(courseId: String, sectionId: String) = "$courseId::$sectionId"
private fun completionKey(courseId: String, chapterId: String, sectionId: String) = "$courseId/$chapterId/$sectionId"
private fun Set<String>.toggle(value: String) = if (value in this) this - value else this + value
private fun Throwable.userMessage(fallback: String): String {
    if (this is java.io.IOException) return fallback
    return message?.takeIf { it.isNotBlank() } ?: fallback
}
private fun User.sameIdentityAs(other: User): Boolean {
    if (userId.isNotBlank() && other.userId.isNotBlank()) return userId == other.userId
    return email.isNotBlank() && email.equals(other.email, ignoreCase = true)
}

/**
 * `/mobile` may not be deployed yet and StudyZoneApi then returns the tiny fallback catalog.
 * Never let that compatibility fallback overwrite a detailed catalog already cached on-device.
 */
private fun preferDetailedCatalog(current: List<Course>, refreshed: List<Course>): List<Course> {
    if (refreshed.isEmpty()) return current
    val currentSections = current.sumOf { course -> course.chapters.sumOf { it.sections.size } }
    val refreshedSections = refreshed.sumOf { course -> course.chapters.sumOf { it.sections.size } }
    val currentIsFallbackShape = current.isNotEmpty() && current.all { course ->
        course.chapters.size == 1 && course.chapters.single().sections.size == 1
    }
    val refreshedIsFallbackShape = refreshed.isNotEmpty() && refreshed.all { course ->
        course.chapters.size == 1 && course.chapters.single().sections.size == 1
    }
    return if ((!currentIsFallbackShape && currentSections > refreshedSections) ||
        (refreshedIsFallbackShape && currentSections == refreshedSections)
    ) {
        current
    } else {
        refreshed
    }
}

private fun List<ChatMessage>.updateLastModel(text: String, streaming: Boolean, isError: Boolean = false): List<ChatMessage> {
    val index = indexOfLast { it.role == "model" }
    if (index < 0) return this
    return mapIndexed { current, message ->
        if (current == index) message.copy(text = text, isStreaming = streaming, isError = isError) else message
    }
}

private fun List<ChatMessage>.updateLastStreaming(streaming: Boolean): List<ChatMessage> {
    val index = indexOfLast { it.isStreaming }
    if (index < 0) return this
    return mapIndexed { current, message -> if (current == index) message.copy(isStreaming = streaming) else message }
}
