package com.hillel.studyzone.model

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class RootTab { COURSES, SEARCH, PROFILE, SETTINGS }

data class Section(
    val id: String,
    val title: String,
    val preview: String = ""
)

data class Chapter(
    val id: String,
    val title: String,
    val sections: List<Section>
)

data class Course(
    val id: String,
    val title: String,
    val description: String,
    val category: String,
    val iconId: String,
    val chapters: List<Chapter>,
    val isAvailable: Boolean = true
)

data class Lesson(
    val courseId: String,
    val courseTitle: String,
    val chapterId: String,
    val sectionId: String,
    val title: String,
    val content: String,
    val interactiveUrl: String,
    val previousSectionId: String? = null,
    val nextSectionId: String? = null
)

data class SearchResult(
    val courseId: String,
    val courseTitle: String,
    val chapterId: String,
    val sectionId: String,
    val title: String,
    val snippet: String
)

data class User(
    val userId: String,
    val email: String,
    val displayName: String,
    val photoUrl: String? = null,
    val isAdmin: Boolean = false,
    val isGoogle: Boolean = false
)

data class ChatMessage(
    val id: Long = System.nanoTime(),
    val createdAt: Long = System.currentTimeMillis(),
    val role: String,
    val text: String,
    val replyTo: String? = null,
    val isStreaming: Boolean = false,
    val isError: Boolean = false,
    val attachments: List<ChatAttachment> = emptyList(),
    val quiz: ChatQuiz? = null,
    val flashcards: List<Flashcard> = emptyList(),
    val functionPlot: FunctionPlot? = null
)

data class ChatAttachment(
    val id: Long = System.nanoTime(),
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    /** Base64 is held only while the attachment is staged/sent; it is never persisted. */
    val base64Data: String
)

data class QuizQuestion(
    val type: String = "mcq",
    val question: String,
    val answers: List<String> = emptyList(),
    val correctAnswer: String = ""
)

data class ChatQuiz(
    val title: String,
    val questions: List<QuizQuestion>,
    val isExam: Boolean = false
)

data class Flashcard(val front: String, val back: String)

data class FunctionPlotSeries(
    val expression: String,
    val label: String = expression,
    val color: String = ""
)

data class FunctionPlot(
    val title: String = "גרף פונקציות",
    val subtitle: String = "",
    val functions: List<FunctionPlotSeries> = emptyList(),
    val xMin: Double = -10.0,
    val xMax: Double = 10.0,
    val yMin: Double? = null,
    val yMax: Double? = null
)

data class AdminOverview(
    val totalUsers: Int = 0,
    val blockedUsers: Int = 0,
    val pendingRequests: Int = 0,
    val publicCoursesCount: Int = 0,
    val requireCoursePassword: Boolean = true,
    val geminiServerKeysEnabled: Boolean = true,
    val askPopoverShortExplainEnabled: Boolean = false,
    val pythiChatModel: String = "",
    val geminiServerKeysTotal: Int = 0,
    val geminiServerKeysActive: Int = 0,
    val geminiServerKeysCooldown: Int = 0
)

data class AdminUser(
    val id: String,
    val email: String,
    val displayName: String,
    val isBlocked: Boolean,
    val isVerified: Boolean,
    val createdAt: Long?,
    val lastLoginAt: Long? = null,
    val isGoogle: Boolean = false,
    val photoUrl: String? = null
)

data class AccessRequest(
    val id: String,
    val userId: String = "",
    val userEmail: String,
    val userDisplayName: String,
    val courseId: String,
    val courseTitle: String,
    val status: String,
    val createdAt: Long? = null,
    val updatedAt: Long? = null
)

data class AdminUserDetails(
    val userId: String,
    val email: String,
    val displayName: String,
    val photoUrl: String? = null,
    val isGoogle: Boolean = false,
    val isVerified: Boolean = true,
    val isBlocked: Boolean = false,
    val createdAt: Long? = null,
    val lastLoginAt: Long? = null,
    val achievementsCount: Int = 0,
    val apiKeysCount: Int = 0,
    val metrics: Map<String, Int> = emptyMap(),
    val allowedCourseIds: List<String> = emptyList(),
    val pendingCourseIds: List<String> = emptyList(),
    val deniedCourseIds: List<String> = emptyList()
)

data class GeminiServerKey(
    val id: String,
    val label: String,
    val maskedKey: String,
    val enabled: Boolean,
    val state: String,
    val createdAt: Long? = null,
    val updatedAt: Long? = null,
    val remainingCooldownMs: Long = 0L,
    val lastFailureMessage: String = "",
    val lastFailureRoute: String = "",
    val lastFailureModel: String = "",
    val lastFailureStatus: Int? = null
)

data class SystemPasswordStatus(
    val type: String,
    val label: String,
    val configured: Boolean,
    val hashed: Boolean
)

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val reduceMotion: Boolean = false,
    val haptics: Boolean = true,
    val fontScale: Float = 1f,
    val keepScreenOn: Boolean = false,
    val persistChatHistory: Boolean = true,
    val rememberPosition: Boolean = true,
    val showReadingProgress: Boolean = false,
    val showGreenChecks: Boolean = true,
    val showActionSuggestions: Boolean = true,
    val showChatPromptNavigator: Boolean = true,
    val enableAskPopover: Boolean = true,
    val clearSelectionAfterPopover: Boolean = false,
    val systemNotifications: Boolean = false,
    val emailLoginNotifications: Boolean = true,
    val emailPasswordNotifications: Boolean = true,
    val lineSpacing: Float = 1f,
    val selectionHighlight: String = "default",
    val activeThemeId: String = "default"
)

data class UiState(
    val isBootstrapping: Boolean = true,
    val error: String? = null,
    val rootTab: RootTab = RootTab.COURSES,
    val courses: List<Course> = emptyList(),
    val selectedCourse: Course? = null,
    val lesson: Lesson? = null,
    val lessonLoading: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<SearchResult> = emptyList(),
    val searchLoading: Boolean = false,
    val searchSettledQuery: String = "",
    val completedSections: Set<String> = emptySet(),
    val bookmarkedSections: Set<String> = emptySet(),
    val settings: AppSettings = AppSettings(),
    val user: User? = null,
    val courseAccessLoaded: Boolean = false,
    val courseAccessAll: Boolean = false,
    val accessibleCourseIds: Set<String> = emptySet(),
    val publicAccessCourseIds: Set<String> = emptySet(),
    val pendingCourseIds: Set<String> = emptySet(),
    val deniedCourseIds: Set<String> = emptySet(),
    val authOpen: Boolean = false,
    val authLoading: Boolean = false,
    val userApiKeys: List<String> = emptyList(),
    val apiKeysLoading: Boolean = false,
    val adminOpen: Boolean = false,
    val adminOverview: AdminOverview? = null,
    val adminUsers: List<AdminUser> = emptyList(),
    val accessRequests: List<AccessRequest> = emptyList(),
    val publicCourseIds: Set<String> = emptySet(),
    val adminSelectedUser: AdminUserDetails? = null,
    val adminGeminiKeys: List<GeminiServerKey> = emptyList(),
    val adminPasswords: List<SystemPasswordStatus> = emptyList(),
    val adminExportJson: String? = null,
    val adminLoading: Boolean = false,
    val chatOpen: Boolean = false,
    val chatExpanded: Boolean = false,
    val chatInput: String = "",
    val chatReplyContext: String? = null,
    val chatAttachments: List<ChatAttachment> = emptyList(),
    val chatMessages: List<ChatMessage> = emptyList(),
    val chatStreaming: Boolean = false,
    val chatSuggestions: List<String> = listOf("תסבירי לי בפשטות", "צרי לי בוחן", "הכיני לי כרטיסיות"),
    val chatTimerRemainingSeconds: Int? = null,
    val chatTimerLabel: String = "טיימר למידה",
    val pythiMemories: Map<String, String> = emptyMap(),
    val toast: String? = null
)
