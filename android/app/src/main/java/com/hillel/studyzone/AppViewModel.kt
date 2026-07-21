package com.hillel.studyzone

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hillel.studyzone.data.LocalPreferences
import com.hillel.studyzone.data.StudyZoneApi
import com.hillel.studyzone.model.AppSettings
import com.hillel.studyzone.model.ChatMessage
import com.hillel.studyzone.model.Course
import com.hillel.studyzone.model.RootTab
import com.hillel.studyzone.model.ThemeMode
import com.hillel.studyzone.model.UiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.Call

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val api = StudyZoneApi(application)
    private val preferences = LocalPreferences(application)
    private val mutableState = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = mutableState.asStateFlow()

    private var searchJob: Job? = null
    private var chatCall: Call? = null

    init {
        viewModelScope.launch {
            preferences.snapshot.collect { (settings, completed, bookmarks) ->
                mutableState.update {
                    it.copy(settings = settings, completedSections = completed, bookmarkedSections = bookmarks)
                }
            }
        }
        bootstrap()
    }

    fun bootstrap() {
        viewModelScope.launch {
            mutableState.update { it.copy(isBootstrapping = true, error = null) }
            runCatching { api.bootstrap() }
                .onSuccess { payload ->
                    mutableState.update {
                        it.copy(
                            isBootstrapping = false,
                            courses = payload.courses,
                            user = payload.user,
                            completedSections = it.completedSections + payload.completedSections,
                            error = null
                        )
                    }
                }
                .onFailure { error ->
                    mutableState.update {
                        it.copy(
                            isBootstrapping = false,
                            error = error.userMessage("לא הצלחנו להתחבר לשרת")
                        )
                    }
                }
        }
    }

    fun selectTab(tab: RootTab) {
        mutableState.update { it.copy(rootTab = tab, selectedCourse = null, lesson = null) }
    }

    fun openCourse(course: Course) {
        mutableState.update { it.copy(selectedCourse = course, lesson = null, rootTab = RootTab.COURSES) }
    }

    fun closeCourse() {
        mutableState.update { it.copy(selectedCourse = null, lesson = null) }
    }

    fun openLesson(courseId: String, sectionId: String) {
        val course = mutableState.value.courses.firstOrNull { it.id == courseId } ?: return
        mutableState.update { it.copy(selectedCourse = course, lessonLoading = true, lesson = null, error = null) }
        viewModelScope.launch {
            runCatching { api.lesson(courseId, sectionId) }
                .onSuccess { lesson -> mutableState.update { it.copy(lesson = lesson, lessonLoading = false) } }
                .onFailure { error ->
                    mutableState.update {
                        it.copy(lessonLoading = false, toast = error.userMessage("לא הצלחנו לטעון את השיעור"))
                    }
                }
        }
    }

    fun closeLesson() {
        mutableState.update { it.copy(lesson = null, lessonLoading = false) }
    }

    fun openAdjacent(sectionId: String?) {
        val courseId = mutableState.value.lesson?.courseId ?: return
        if (!sectionId.isNullOrBlank()) openLesson(courseId, sectionId)
    }

    fun setSearchQuery(query: String) {
        mutableState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        if (query.trim().length < 2) {
            mutableState.update { it.copy(searchResults = emptyList(), searchLoading = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(320)
            mutableState.update { it.copy(searchLoading = true) }
            runCatching { api.search(query.trim()) }
                .onSuccess { results -> mutableState.update { it.copy(searchResults = results, searchLoading = false) } }
                .onFailure { error ->
                    mutableState.update {
                        it.copy(searchLoading = false, toast = error.userMessage("החיפוש נכשל"))
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
        runCatching { api.syncProgress(snapshot.completedSections) }
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
        if (email.isBlank() || password.isBlank()) {
            mutableState.update { it.copy(toast = "יש למלא אימייל וסיסמה") }
            return
        }
        viewModelScope.launch {
            mutableState.update { it.copy(authLoading = true) }
            runCatching { api.login(email.trim(), password) }
                .onSuccess { user ->
                    mutableState.update { it.copy(user = user, authOpen = false, authLoading = false, toast = "ברוכים הבאים, ${user.displayName}") }
                    refreshSession()
                }
                .onFailure { error -> mutableState.update { it.copy(authLoading = false, toast = error.userMessage("ההתחברות נכשלה")) } }
        }
    }

    fun register(name: String, email: String, password: String) {
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
        viewModelScope.launch {
            runCatching { api.logout() }
            mutableState.update { it.copy(user = null, adminOpen = false, toast = "התנתקת בהצלחה") }
        }
    }

    private fun refreshSession() {
        viewModelScope.launch {
            runCatching { api.bootstrap() }.onSuccess { payload ->
                mutableState.update {
                    it.copy(
                        courses = payload.courses,
                        user = payload.user ?: it.user,
                        completedSections = it.completedSections + payload.completedSections
                    )
                }
            }
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

        val apiMessages = messages.dropLast(1).map { it.role to it.text }
        chatCall?.cancel()
        chatCall = api.streamChat(
            messages = apiMessages,
            course = snapshot.selectedCourse,
            lesson = snapshot.lesson,
            onDelta = { fullText ->
                mutableState.update { current ->
                    current.copy(chatMessages = current.chatMessages.updateLastModel(fullText, streaming = true))
                }
            },
            onDone = {
                mutableState.update { current ->
                    current.copy(
                        chatStreaming = false,
                        chatMessages = current.chatMessages.updateLastModel(
                            current.chatMessages.lastOrNull()?.text.orEmpty(),
                            streaming = false
                        )
                    )
                }
            },
            onError = { error ->
                mutableState.update { current ->
                    current.copy(
                        chatStreaming = false,
                        chatMessages = current.chatMessages.updateLastModel(
                            error.userMessage("Pythi לא זמינה כרגע"),
                            streaming = false,
                            isError = true
                        )
                    )
                }
            }
        )
    }

    fun stopChat() {
        chatCall?.cancel()
        chatCall = null
        mutableState.update { it.copy(chatStreaming = false, chatMessages = it.chatMessages.updateLastStreaming(false)) }
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

    fun closeAdmin() = mutableState.update { it.copy(adminOpen = false) }

    fun loadAdmin() {
        viewModelScope.launch {
            mutableState.update { it.copy(adminLoading = true) }
            runCatching { api.loadAdmin() }
                .onSuccess { data ->
                    mutableState.update {
                        it.copy(
                            adminLoading = false,
                            adminOverview = data.overview,
                            adminUsers = data.users,
                            accessRequests = data.requests,
                            publicCourseIds = data.publicCourseIds
                        )
                    }
                }
                .onFailure { error -> mutableState.update { it.copy(adminLoading = false, toast = error.userMessage("טעינת הניהול נכשלה")) } }
        }
    }

    fun toggleUserBlock(userId: String) {
        viewModelScope.launch {
            runCatching { api.toggleUserBlock(userId) }
                .onSuccess { blocked ->
                    mutableState.update { current ->
                        current.copy(adminUsers = current.adminUsers.map { if (it.id == userId) it.copy(isBlocked = blocked) else it })
                    }
                }
                .onFailure { error -> mutableState.update { it.copy(toast = error.userMessage("הפעולה נכשלה")) } }
        }
    }

    fun handleAccessRequest(requestId: String, action: String) {
        viewModelScope.launch {
            runCatching { api.handleAccessRequest(requestId, action) }
                .onSuccess { loadAdmin() }
                .onFailure { error -> mutableState.update { it.copy(toast = error.userMessage("הפעולה נכשלה")) } }
        }
    }

    fun togglePublicCourse(courseId: String) {
        viewModelScope.launch {
            runCatching { api.togglePublicCourse(courseId) }
                .onSuccess { ids -> mutableState.update { it.copy(publicCourseIds = ids) } }
                .onFailure { error -> mutableState.update { it.copy(toast = error.userMessage("עדכון הקורס נכשל")) } }
        }
    }

    fun updateAdminSettings(requirePassword: Boolean, geminiEnabled: Boolean, shortExplain: Boolean) {
        viewModelScope.launch {
            runCatching { api.updateSystemSettings(requirePassword, geminiEnabled, shortExplain) }
                .onSuccess {
                    mutableState.update {
                        it.copy(
                            adminOverview = it.adminOverview?.copy(
                                requireCoursePassword = requirePassword,
                                geminiServerKeysEnabled = geminiEnabled,
                                askPopoverShortExplainEnabled = shortExplain
                            ),
                            toast = "הגדרות המערכת נשמרו"
                        )
                    }
                }
                .onFailure { error -> mutableState.update { it.copy(toast = error.userMessage("שמירת ההגדרות נכשלה")) } }
        }
    }

    fun consumeToast() = mutableState.update { it.copy(toast = null) }

    fun handleDeepLink(courseId: String?, sectionId: String?) {
        if (courseId.isNullOrBlank()) return
        val course = mutableState.value.courses.firstOrNull { it.id == courseId } ?: return
        if (sectionId.isNullOrBlank()) openCourse(course) else openLesson(courseId, sectionId)
    }
}

private fun lessonKey(courseId: String, sectionId: String) = "$courseId::$sectionId"
private fun completionKey(courseId: String, chapterId: String, sectionId: String) = "$courseId/$chapterId/$sectionId"
private fun Set<String>.toggle(value: String) = if (value in this) this - value else this + value
private fun Throwable.userMessage(fallback: String) = message?.takeIf { it.isNotBlank() } ?: fallback

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
