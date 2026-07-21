package com.hillel.studyzone.model

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class RootTab { COURSES, SEARCH, SAVED, TOOLS, PROFILE }

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
    val role: String,
    val text: String,
    val isStreaming: Boolean = false,
    val isError: Boolean = false
)

data class AdminOverview(
    val totalUsers: Int = 0,
    val blockedUsers: Int = 0,
    val pendingRequests: Int = 0,
    val publicCoursesCount: Int = 0,
    val requireCoursePassword: Boolean = true,
    val geminiServerKeysEnabled: Boolean = true,
    val askPopoverShortExplainEnabled: Boolean = false,
    val pythiChatModel: String = ""
)

data class AdminUser(
    val id: String,
    val email: String,
    val displayName: String,
    val isBlocked: Boolean,
    val isVerified: Boolean,
    val createdAt: Long?
)

data class AccessRequest(
    val id: String,
    val userEmail: String,
    val userDisplayName: String,
    val courseId: String,
    val courseTitle: String,
    val status: String
)

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val reduceMotion: Boolean = false,
    val haptics: Boolean = true,
    val fontScale: Float = 1f,
    val keepScreenOn: Boolean = false
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
    val completedSections: Set<String> = emptySet(),
    val bookmarkedSections: Set<String> = emptySet(),
    val settings: AppSettings = AppSettings(),
    val user: User? = null,
    val authOpen: Boolean = false,
    val authLoading: Boolean = false,
    val adminOpen: Boolean = false,
    val adminOverview: AdminOverview? = null,
    val adminUsers: List<AdminUser> = emptyList(),
    val accessRequests: List<AccessRequest> = emptyList(),
    val publicCourseIds: Set<String> = emptySet(),
    val adminLoading: Boolean = false,
    val chatOpen: Boolean = false,
    val chatExpanded: Boolean = false,
    val chatInput: String = "",
    val chatMessages: List<ChatMessage> = emptyList(),
    val chatStreaming: Boolean = false,
    val toast: String? = null
)
