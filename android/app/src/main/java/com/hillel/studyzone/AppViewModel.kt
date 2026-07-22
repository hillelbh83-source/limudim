package com.hillel.studyzone

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hillel.studyzone.data.FallbackCatalog
import com.hillel.studyzone.data.LocalPreferences
import com.hillel.studyzone.data.LocalSnapshot
import com.hillel.studyzone.data.StudyZoneApi
import com.hillel.studyzone.model.AppSettings
import com.hillel.studyzone.model.ChatAttachment
import com.hillel.studyzone.model.ChatQuiz
import com.hillel.studyzone.model.ChatMessage
import com.hillel.studyzone.model.Course
import com.hillel.studyzone.model.Flashcard
import com.hillel.studyzone.model.FunctionPlot
import com.hillel.studyzone.model.FunctionPlotSeries
import com.hillel.studyzone.model.QuizQuestion
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
import org.json.JSONArray
import org.json.JSONObject

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
    private var settingsSyncJob: Job? = null
    private var adminJob: Job? = null
    private var adminGeneration = 0L
    private var chatCall: Call? = null
    private var chatRenderJob: Job? = null
    private var chatTimerJob: Job? = null
    @Volatile private var chatGeneration = 0L
    @Volatile private var localSettingsWriteProtectUntil = 0L
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
            val settings = if (
                !includeCachedSession &&
                System.currentTimeMillis() < localSettingsWriteProtectUntil &&
                snapshot.settings != current.settings
            ) {
                current.settings
            } else {
                snapshot.settings
            }
            current.copy(
                settings = settings,
                completedSections = if (includeCachedSession) snapshot.completed else current.completedSections,
                bookmarkedSections = if (includeCachedSession) snapshot.bookmarks else current.bookmarkedSections,
                user = if (includeCachedSession) snapshot.user else current.user,
                pythiMemories = if (includeCachedSession) snapshot.pythiMemories else current.pythiMemories,
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
                pythiMemories = if (payload.pythiMemories.isNotEmpty() || it.pythiMemories.isEmpty()) {
                    payload.pythiMemories
                } else {
                    it.pythiMemories
                },
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
            preferences.savePythiMemories(mutableState.value.pythiMemories)
        }
    }

    fun selectTab(tab: RootTab) {
        cancelLessonRequest()
        mutableState.update {
            it.copy(rootTab = tab, selectedCourse = null, lesson = null, lessonLoading = false)
        }
        if (tab == RootTab.SETTINGS && mutableState.value.user != null) loadUserApiKeys()
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
    fun setPersistChatHistory(enabled: Boolean) = updateSettings(mutableState.value.settings.copy(persistChatHistory = enabled))
    fun setRememberPosition(enabled: Boolean) = updateSettings(mutableState.value.settings.copy(rememberPosition = enabled))
    fun setShowReadingProgress(enabled: Boolean) = updateSettings(mutableState.value.settings.copy(showReadingProgress = enabled))
    fun setShowGreenChecks(enabled: Boolean) = updateSettings(mutableState.value.settings.copy(showGreenChecks = enabled))
    fun setShowActionSuggestions(enabled: Boolean) = updateSettings(mutableState.value.settings.copy(showActionSuggestions = enabled))
    fun setShowChatPromptNavigator(enabled: Boolean) = updateSettings(mutableState.value.settings.copy(showChatPromptNavigator = enabled))
    fun setEnableAskPopover(enabled: Boolean) = updateSettings(mutableState.value.settings.copy(enableAskPopover = enabled))
    fun setClearSelectionAfterPopover(enabled: Boolean) = updateSettings(mutableState.value.settings.copy(clearSelectionAfterPopover = enabled))
    fun setSystemNotifications(enabled: Boolean) = updateSettings(mutableState.value.settings.copy(systemNotifications = enabled))
    fun setEmailLoginNotifications(enabled: Boolean) = updateSettings(mutableState.value.settings.copy(emailLoginNotifications = enabled))
    fun setEmailPasswordNotifications(enabled: Boolean) = updateSettings(mutableState.value.settings.copy(emailPasswordNotifications = enabled))
    fun setLineSpacing(scale: Float) = updateSettings(mutableState.value.settings.copy(lineSpacing = scale.coerceIn(1f, 1.35f)))
    fun setSelectionHighlight(value: String) = updateSettings(mutableState.value.settings.copy(selectionHighlight = value))
    fun setActiveTheme(value: String) = updateSettings(mutableState.value.settings.copy(activeThemeId = value))

    private fun updateSettings(settings: AppSettings) {
        localSettingsWriteProtectUntil = System.currentTimeMillis() + 1_500L
        mutableState.update { it.copy(settings = settings) }
        viewModelScope.launch { preferences.saveSettings(settings) }
        settingsSyncJob?.cancel()
        if (mutableState.value.user != null) {
            settingsSyncJob = viewModelScope.launch {
                delay(650)
                runCatching { api.syncSettings(settings) }
            }
        }
    }

    fun loadUserApiKeys() {
        if (mutableState.value.user == null || mutableState.value.apiKeysLoading) return
        viewModelScope.launch {
            mutableState.update { it.copy(apiKeysLoading = true) }
            runCatching { api.userApiKeys() }
                .onSuccess { keys -> mutableState.update { it.copy(userApiKeys = keys, apiKeysLoading = false) } }
                .onFailure { error -> mutableState.update { it.copy(apiKeysLoading = false, toast = error.userMessage("טעינת מפתחות API נכשלה")) } }
        }
    }

    fun saveUserApiKeys(keys: List<String>) {
        if (mutableState.value.user == null || mutableState.value.apiKeysLoading) return
        viewModelScope.launch {
            mutableState.update { it.copy(apiKeysLoading = true) }
            runCatching { api.saveUserApiKeys(keys) }
                .onSuccess { saved -> mutableState.update { it.copy(userApiKeys = saved, apiKeysLoading = false, toast = "מפתחות ה־API נשמרו") } }
                .onFailure { error -> mutableState.update { it.copy(apiKeysLoading = false, toast = error.userMessage("שמירת מפתחות API נכשלה")) } }
        }
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
                            pythiMemories = account.pythiMemories.ifEmpty { it.pythiMemories },
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
                            pythiMemories = account.pythiMemories.ifEmpty { it.pythiMemories },
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
                pythiMemories = emptyMap(),
                courseAccessLoaded = true,
                courseAccessAll = false,
                accessibleCourseIds = it.publicAccessCourseIds,
                pendingCourseIds = emptySet(),
                deniedCourseIds = emptySet(),
                authOpen = false,
                authLoading = false,
                userApiKeys = emptyList(),
                apiKeysLoading = false,
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
                preferences.savePythiMemories(emptyMap())
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
    fun clearChatReplyContext() = mutableState.update { it.copy(chatReplyContext = null) }

    fun addChatAttachments(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val existing = mutableState.value.chatAttachments
            val remainingSlots = (MAX_CHAT_ATTACHMENTS - existing.size).coerceAtLeast(0)
            if (remainingSlots == 0) {
                showMessage("אפשר לצרף עד $MAX_CHAT_ATTACHMENTS קבצים בכל הודעה")
                return@launch
            }
            val decoded = withContext(Dispatchers.IO) {
                uris.take(remainingSlots).mapNotNull(::readChatAttachment)
            }
            mutableState.update { current ->
                current.copy(
                    chatAttachments = (current.chatAttachments + decoded).take(MAX_CHAT_ATTACHMENTS),
                    toast = if (decoded.isEmpty()) "לא הצלחנו לקרוא את הקובץ" else current.toast
                )
            }
        }
    }

    fun removeChatAttachment(id: Long) {
        mutableState.update { it.copy(chatAttachments = it.chatAttachments.filterNot { file -> file.id == id }) }
    }

    fun sendChat() {
        val snapshot = mutableState.value
        val text = snapshot.chatInput.trim()
        if ((text.isBlank() && snapshot.chatAttachments.isEmpty()) || snapshot.chatStreaming) return
        if (snapshot.user == null) {
            mutableState.update { it.copy(authOpen = true, toast = "יש להתחבר כדי לדבר עם Pythi") }
            return
        }

        val attachments = snapshot.chatAttachments
        val visibleText = text.ifBlank { "מצורפים ${attachments.size} קבצים" }
        val userMessage = ChatMessage(
            role = "user",
            text = visibleText,
            replyTo = snapshot.chatReplyContext,
            attachments = attachments.map { it.copy(base64Data = "") }
        )
        val modelMessage = ChatMessage(role = "model", text = "", isStreaming = true)
        val messages = snapshot.chatMessages + userMessage + modelMessage
        mutableState.update {
            it.copy(
                chatInput = "",
                chatReplyContext = null,
                chatAttachments = emptyList(),
                chatSuggestions = emptyList(),
                chatMessages = messages,
                chatStreaming = true
            )
        }

        val apiMessages = messages.dropLast(1)
            .filterNot { it.isError }
            .map { it.role to it.toChatHistoryText() }
        val generation = ++chatGeneration
        chatCall?.cancel()
        pendingChatText.set(null)
        startChatRenderer(generation)
        chatCall = try {
            api.streamChat(
                messages = apiMessages,
                attachments = attachments,
                course = snapshot.selectedCourse,
                lesson = snapshot.lesson,
                user = snapshot.user,
                pythiMemories = snapshot.pythiMemories,
                completedSections = snapshot.completedSections,
                bookmarkedSections = snapshot.bookmarkedSections,
                showActionSuggestions = snapshot.settings.showActionSuggestions,
                onDelta = { fullText ->
                    if (generation == chatGeneration) pendingChatText.set(fullText)
                },
                onDone = { toolCalls ->
                    finishChat(generation, error = null, toolCalls = toolCalls)
                },
                onError = { error ->
                    finishChat(generation, error, emptyList())
                }
            )
        } catch (error: Throwable) {
            finishChat(generation, error, emptyList())
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

    private fun finishChat(generation: Long, error: Throwable?, toolCalls: List<StudyZoneApi.ToolCall>) {
        viewModelScope.launch {
            if (generation != chatGeneration) return@launch
            chatRenderJob?.cancel()
            chatRenderJob = null
            val finalText = pendingChatText.getAndSet(null)
            mutableState.update { current ->
                val currentText = current.chatMessages.lastOrNull { it.role == "model" }?.text.orEmpty()
                val tools = if (error == null) parseChatTools(toolCalls) else ParsedChatTools()
                val resolvedText = error?.userMessage("Pythi לא זמינה כרגע")
                    ?: finalText
                    ?: currentText
                val fallbackText = when {
                    resolvedText.isNotBlank() -> resolvedText
                    tools.quiz != null || tools.flashcards.isNotEmpty() || tools.plot != null -> ""
                    tools.savedMemory != null -> "רשמתי לפניי! 🧠"
                    else -> toolFallbackText(toolCalls)
                }
                current.copy(
                    chatStreaming = false,
                    chatMessages = current.chatMessages.updateLastModel(
                        text = fallbackText,
                        streaming = false,
                        isError = error != null,
                        quiz = tools.quiz,
                        flashcards = tools.flashcards,
                        functionPlot = tools.plot
                    ),
                    chatSuggestions = tools.suggestions
                )
            }
            applyToolSideEffects(toolCalls)
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
        mutableState.update {
            it.copy(
                chatMessages = emptyList(),
                chatReplyContext = null,
                chatAttachments = emptyList(),
                chatSuggestions = listOf("תסבירי לי בפשטות", "צרי לי בוחן", "הכיני לי כרטיסיות")
            )
        }
    }

    fun askPythiAboutSelection(selectedText: String, question: String) {
        val excerpt = selectedText.trim().replace(Regex("\\s+"), " ").take(1_500)
        val prompt = question.trim().take(2_000)
        if (excerpt.isBlank() || prompt.isBlank()) return
        mutableState.update {
            it.copy(
                chatOpen = true,
                chatExpanded = false,
                chatInput = prompt,
                chatReplyContext = excerpt
            )
        }
        sendChat()
    }

    fun editChatMessage(messageId: Long, text: String) {
        stopChat()
        mutableState.update { current ->
            val index = current.chatMessages.indexOfFirst { it.id == messageId && it.role == "user" }
            if (index < 0) current else current.copy(
                chatOpen = true,
                chatMessages = current.chatMessages.take(index),
                chatInput = text,
                chatReplyContext = current.chatMessages[index].replyTo,
                chatSuggestions = emptyList()
            )
        }
    }

    fun retryChatMessage(messageId: Long) {
        stopChat()
        val current = mutableState.value
        val modelIndex = current.chatMessages.indexOfFirst { it.id == messageId && it.role == "model" }
        if (modelIndex < 0) return
        val userIndex = (modelIndex - 1 downTo 0).firstOrNull { current.chatMessages[it].role == "user" } ?: return
        val userMessage = current.chatMessages[userIndex]
        mutableState.update {
            it.copy(
                chatOpen = true,
                chatMessages = it.chatMessages.take(userIndex),
                chatInput = userMessage.text,
                chatReplyContext = userMessage.replyTo,
                chatSuggestions = emptyList()
            )
        }
        sendChat()
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

    fun savePythiMemory(key: String, value: String) {
        val safeKey = key.trim().take(80)
        val safeValue = value.trim().take(500)
        if (safeKey.isBlank() || safeValue.isBlank()) return
        val updated = mutableState.value.pythiMemories + (safeKey to safeValue)
        mutableState.update { it.copy(pythiMemories = updated, toast = "הזיכרון נשמר") }
        persistPythiMemories(updated)
    }

    fun removePythiMemory(key: String) {
        val updated = mutableState.value.pythiMemories - key
        mutableState.update { it.copy(pythiMemories = updated, toast = "הזיכרון נמחק") }
        persistPythiMemories(updated)
    }

    fun updateProfileName(displayName: String) {
        val name = displayName.trim().take(80)
        if (name.isBlank() || mutableState.value.user == null) return
        viewModelScope.launch {
            runCatching { api.updateProfileName(name) }
                .onSuccess { user ->
                    mutableState.update { it.copy(user = user, toast = "השם עודכן") }
                    preferences.saveRemoteSnapshot(user, mutableState.value.completedSections, mutableState.value.bookmarkedSections)
                }
                .onFailure { error -> mutableState.update { it.copy(toast = error.userMessage("עדכון השם נכשל")) } }
        }
    }

    fun updateProfilePhoto(uri: Uri) {
        if (mutableState.value.user == null) return
        viewModelScope.launch {
            val dataUrl = withContext(Dispatchers.IO) { readProfilePhoto(uri) }
            if (dataUrl == null) {
                showMessage("התמונה גדולה מדי או שאינה בפורמט נתמך")
                return@launch
            }
            runCatching { api.updateProfilePhoto(dataUrl) }
                .onSuccess { user ->
                    mutableState.update { it.copy(user = user, toast = "תמונת הפרופיל עודכנה") }
                    preferences.saveRemoteSnapshot(user, mutableState.value.completedSections, mutableState.value.bookmarkedSections)
                }
                .onFailure { error -> showMessage(error.userMessage("העלאת התמונה נכשלה")) }
        }
    }

    fun changePassword(oldPassword: String, newPassword: String) {
        val requiresOldPassword = mutableState.value.user?.isGoogle != true
        if ((requiresOldPassword && oldPassword.isBlank()) || newPassword.length < 8) {
            showMessage("הסיסמה החדשה חייבת להכיל לפחות 8 תווים")
            return
        }
        viewModelScope.launch {
            runCatching { api.changePassword(oldPassword, newPassword) }
                .onSuccess { showMessage("הסיסמה שונתה בהצלחה") }
                .onFailure { error -> showMessage(error.userMessage("שינוי הסיסמה נכשל")) }
        }
    }

    fun sendAdminMessage(message: String) {
        val text = message.trim().take(2_000)
        if (text.isBlank()) return
        viewModelScope.launch {
            runCatching { api.sendAdminMessage(text) }
                .onSuccess { showMessage("ההודעה נשלחה למנהל") }
                .onFailure { error -> showMessage(error.userMessage("שליחת ההודעה נכשלה")) }
        }
    }

    private fun persistPythiMemories(memories: Map<String, String>) {
        viewModelScope.launch {
            preferences.savePythiMemories(memories)
            if (mutableState.value.user != null) runCatching { api.syncPythiMemories(memories) }
        }
    }

    private fun readChatAttachment(uri: Uri): ChatAttachment? {
        val resolver = getApplication<Application>().contentResolver
        var name = "קובץ"
        var declaredSize = -1L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                name = cursor.getString(0)?.take(120).orEmpty().ifBlank { "קובץ" }
                declaredSize = if (cursor.isNull(1)) -1L else cursor.getLong(1)
            }
        }
        if (declaredSize > MAX_CHAT_ATTACHMENT_BYTES) return null
        val bytes = resolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(MAX_CHAT_ATTACHMENT_BYTES.toInt() + 1)
            var offset = 0
            while (offset < buffer.size) {
                val count = input.read(buffer, offset, buffer.size - offset)
                if (count < 0) break
                offset += count
            }
            if (offset > MAX_CHAT_ATTACHMENT_BYTES) null else buffer.copyOf(offset)
        } ?: return null
        val mimeType = resolver.getType(uri).orEmpty().ifBlank { "application/octet-stream" }
        if (mimeType !in SUPPORTED_CHAT_MIME_TYPES && !mimeType.startsWith("image/")) return null
        return ChatAttachment(
            name = name,
            mimeType = mimeType,
            sizeBytes = bytes.size.toLong(),
            base64Data = Base64.encodeToString(bytes, Base64.NO_WRAP)
        )
    }

    private fun readProfilePhoto(uri: Uri): String? {
        val resolver = getApplication<Application>().contentResolver
        val mimeType = resolver.getType(uri).orEmpty().lowercase()
        if (!mimeType.startsWith("image/")) return null
        val bytes = resolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(MAX_PROFILE_PHOTO_BYTES + 1)
            var offset = 0
            while (offset < buffer.size) {
                val count = input.read(buffer, offset, buffer.size - offset)
                if (count < 0) break
                offset += count
            }
            if (offset > MAX_PROFILE_PHOTO_BYTES) null else buffer.copyOf(offset)
        } ?: return null
        return "data:$mimeType;base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}"
    }

    private fun applyToolSideEffects(calls: List<StudyZoneApi.ToolCall>) {
        calls.forEach { call ->
            when (call.name) {
                "save_memory" -> savePythiMemory(call.arguments.optString("key"), call.arguments.optString("value"))
                "navigate_to_section" -> {
                    val courseId = mutableState.value.selectedCourse?.id ?: mutableState.value.lesson?.courseId
                    val sectionId = call.arguments.optString("sectionId")
                    if (!courseId.isNullOrBlank() && sectionId.isNotBlank()) openLesson(courseId, sectionId)
                }
                "set_theme" -> when (call.arguments.optString("mode").lowercase()) {
                    "dark" -> setTheme(ThemeMode.DARK)
                    "light" -> setTheme(ThemeMode.LIGHT)
                    "system" -> setTheme(ThemeMode.SYSTEM)
                }
                "set_chat_visibility" -> setChatOpen(call.arguments.optBoolean("isVisible", true))
                "start_timer" -> startChatTimer(
                    durationSeconds = call.arguments.optInt("duration", 60),
                    label = call.arguments.optString("label").ifBlank { "טיימר למידה" }
                )
                "stop_timer" -> stopChatTimer()
                "report_bug" -> {
                    val description = call.arguments.optString("description").trim()
                    if (description.isNotBlank()) {
                        viewModelScope.launch {
                            runCatching {
                                api.sendAdminMessage(
                                    description = description,
                                    severity = call.arguments.optString("severity", "medium"),
                                    location = call.arguments.optString("location_context", "Pythi Android"),
                                    originalUserMessage = call.arguments.optString("original_user_message", description)
                                )
                            }.onFailure { showMessage("לא הצלחנו לשלוח את דיווח הבאג") }
                        }
                    }
                }
            }
        }
    }

    private fun startChatTimer(durationSeconds: Int, label: String) {
        val duration = durationSeconds.coerceIn(1, 24 * 60 * 60)
        chatTimerJob?.cancel()
        mutableState.update { it.copy(chatTimerRemainingSeconds = duration, chatTimerLabel = label.take(80)) }
        chatTimerJob = viewModelScope.launch {
            var remaining = duration
            while (remaining > 0) {
                delay(1_000)
                remaining -= 1
                mutableState.update { it.copy(chatTimerRemainingSeconds = remaining.takeIf { seconds -> seconds > 0 }) }
            }
            mutableState.update { it.copy(toast = "הטיימר הסתיים: ${label.take(80)}") }
            chatTimerJob = null
        }
    }

    private fun stopChatTimer() {
        chatTimerJob?.cancel()
        chatTimerJob = null
        mutableState.update { it.copy(chatTimerRemainingSeconds = null) }
    }

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

private data class ParsedChatTools(
    val quiz: ChatQuiz? = null,
    val flashcards: List<Flashcard> = emptyList(),
    val plot: FunctionPlot? = null,
    val suggestions: List<String> = emptyList(),
    val savedMemory: Pair<String, String>? = null
)

private fun parseChatTools(calls: List<StudyZoneApi.ToolCall>): ParsedChatTools {
    var quiz: ChatQuiz? = null
    var flashcards = emptyList<Flashcard>()
    var plot: FunctionPlot? = null
    var suggestions = emptyList<String>()
    var savedMemory: Pair<String, String>? = null

    calls.forEach { call ->
        val args = call.arguments
        when (call.name) {
            "suggest_actions" -> suggestions = args.optJSONArray("suggestions").toStringValues(4)
            "create_quiz", "create_exam" -> {
                val questions = buildList {
                    val array = args.optJSONArray("questions") ?: JSONArray()
                    for (index in 0 until array.length()) {
                        val item = array.optJSONObject(index) ?: continue
                        val question = item.optString("question").trim()
                        if (question.isBlank()) continue
                        val correct = item.opt("correctAnswer")?.toString().orEmpty()
                        add(
                            QuizQuestion(
                                type = item.optString("type", "mcq").lowercase(),
                                question = question,
                                answers = item.optJSONArray("answers").toStringValues(8),
                                correctAnswer = correct
                            )
                        )
                    }
                }
                if (questions.isNotEmpty()) {
                    quiz = ChatQuiz(
                        title = args.optString("title").ifBlank { if (call.name == "create_exam") "מבחן עם פיתי" else "בוחן עם פיתי" },
                        questions = questions,
                        isExam = call.name == "create_exam"
                    )
                }
            }
            "create_flashcards" -> {
                flashcards = buildList {
                    val array = args.optJSONArray("cards") ?: JSONArray()
                    for (index in 0 until array.length()) {
                        val item = array.optJSONObject(index) ?: continue
                        val front = item.optString("front").trim()
                        val back = item.optString("back").trim()
                        if (front.isNotBlank() && back.isNotBlank()) add(Flashcard(front, back))
                    }
                }
            }
            "plot_function" -> {
                val functions = buildList {
                    val array = args.optJSONArray("functions") ?: JSONArray()
                    for (index in 0 until array.length()) {
                        val item = array.optJSONObject(index) ?: continue
                        val expression = item.optString("expression").trim()
                        if (expression.isNotBlank()) {
                            add(FunctionPlotSeries(expression, item.optString("label").ifBlank { expression }, item.optString("color")))
                        }
                    }
                }
                if (functions.isNotEmpty()) {
                    val xMin = args.optDouble("xMin", -10.0)
                    val xMax = args.optDouble("xMax", 10.0)
                    plot = FunctionPlot(
                        title = args.optString("title").ifBlank { "גרף פונקציות" },
                        subtitle = args.optString("subtitle"),
                        functions = functions,
                        xMin = minOf(xMin, xMax - .1),
                        xMax = maxOf(xMax, xMin + .1),
                        yMin = args.opt("yMin")?.takeUnless { it == JSONObject.NULL }?.toString()?.toDoubleOrNull(),
                        yMax = args.opt("yMax")?.takeUnless { it == JSONObject.NULL }?.toString()?.toDoubleOrNull()
                    )
                }
            }
            "save_memory" -> {
                val key = args.optString("key").trim()
                val value = args.optString("value").trim()
                if (key.isNotBlank() && value.isNotBlank()) savedMemory = key to value
            }
        }
    }
    return ParsedChatTools(quiz, flashcards, plot, suggestions, savedMemory)
}

private fun JSONArray?.toStringValues(limit: Int): List<String> = buildList {
    val array = this@toStringValues ?: return@buildList
    for (index in 0 until minOf(array.length(), limit)) {
        array.optString(index).trim().takeIf(String::isNotBlank)?.let(::add)
    }
}

private fun ChatMessage.toChatHistoryText(): String = buildString {
    replyTo?.takeIf(String::isNotBlank)?.let { quoted ->
        append("[בתגובה לקטע מהשיעור: ")
        append(quoted)
        append("]\n")
    }
    append(text)
    quiz?.let { quiz ->
        append("\n\n[בוחן שנוצר בשיחה: ${quiz.title}]")
        quiz.questions.forEachIndexed { index, question ->
            append("\n${index + 1}. ${question.question}")
            if (question.answers.isNotEmpty()) append("\nאפשרויות: ${question.answers.joinToString(" | ")}")
            append("\nתשובה נכונה: ${question.correctAnswer}")
        }
    }
    if (flashcards.isNotEmpty()) {
        append("\n\n[כרטיסיות שנוצרו בשיחה]")
        flashcards.forEachIndexed { index, card -> append("\n${index + 1}. ${card.front} — ${card.back}") }
    }
    functionPlot?.let { plot ->
        append("\n\n[גרף שנוצר בשיחה: ${plot.title}]")
        plot.functions.forEach { function -> append("\n${function.label}: ${function.expression}") }
    }
}.take(20_000)

private fun toolFallbackText(calls: List<StudyZoneApi.ToolCall>): String = when {
    calls.any { it.name == "report_bug" } -> "דיווחתי על הבאג לצוות הפיתוח. תודה על הערנות!"
    calls.any { it.name == "set_theme" && it.arguments.optString("mode") == "dark" } -> "לילה טוב! 🌙"
    calls.any { it.name == "set_theme" } -> "בוקר טוב! ☀️"
    calls.any { it.name == "navigate_to_section" } -> "מעבירה אותך לשיעור שביקשת."
    calls.any { it.name == "start_timer" } -> {
        val seconds = calls.first { it.name == "start_timer" }.arguments.optInt("duration", 60)
        "הפעלתי טיימר ל־${maxOf(1, seconds / 60)} דקות! ⏱️"
    }
    calls.any { it.name == "stop_timer" } -> "עצרתי את הטיימר."
    calls.any { it.name == "set_chat_visibility" } -> "בוצע."
    else -> "סיימתי להכין את זה בשבילך."
}

private fun List<ChatMessage>.updateLastModel(
    text: String,
    streaming: Boolean,
    isError: Boolean = false,
    quiz: ChatQuiz? = null,
    flashcards: List<Flashcard> = emptyList(),
    functionPlot: FunctionPlot? = null
): List<ChatMessage> {
    val index = indexOfLast { it.role == "model" }
    if (index < 0) return this
    return mapIndexed { current, message ->
        if (current == index) {
            message.copy(
                text = text,
                isStreaming = streaming,
                isError = isError,
                quiz = quiz ?: message.quiz,
                flashcards = flashcards.ifEmpty { message.flashcards },
                functionPlot = functionPlot ?: message.functionPlot
            )
        } else message
    }
}

private fun List<ChatMessage>.updateLastStreaming(streaming: Boolean): List<ChatMessage> {
    val index = indexOfLast { it.isStreaming }
    if (index < 0) return this
    return mapIndexed { current, message -> if (current == index) message.copy(isStreaming = streaming) else message }
}

private const val MAX_CHAT_ATTACHMENTS = 4
private const val MAX_CHAT_ATTACHMENT_BYTES = 8L * 1024L * 1024L
private const val MAX_PROFILE_PHOTO_BYTES = 2 * 1024 * 1024
private val SUPPORTED_CHAT_MIME_TYPES = setOf("application/pdf", "text/plain")
