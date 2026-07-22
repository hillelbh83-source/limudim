package com.hillel.studyzone.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hillel.studyzone.model.AppSettings
import com.hillel.studyzone.model.ThemeMode
import com.hillel.studyzone.model.User
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONObject

private val Context.dataStore by preferencesDataStore(name = "studyzone_preferences")

/** Small preference/account snapshot applied without waiting for a network response. */
data class LocalSnapshot(
    val settings: AppSettings,
    val completed: Set<String>,
    val bookmarks: Set<String>,
    val user: User?
)

class LocalPreferences(private val context: Context) {
    private object Keys {
        val theme = stringPreferencesKey("theme")
        val reduceMotion = booleanPreferencesKey("reduce_motion")
        val haptics = booleanPreferencesKey("haptics")
        val fontScale = floatPreferencesKey("font_scale")
        val keepScreenOn = booleanPreferencesKey("keep_screen_on")
        val persistChatHistory = booleanPreferencesKey("persist_chat_history")
        val rememberPosition = booleanPreferencesKey("remember_position")
        val showReadingProgress = booleanPreferencesKey("show_reading_progress")
        val showGreenChecks = booleanPreferencesKey("show_green_checks")
        val showActionSuggestions = booleanPreferencesKey("show_action_suggestions")
        val showChatPromptNavigator = booleanPreferencesKey("show_chat_prompt_navigator")
        val enableAskPopover = booleanPreferencesKey("enable_ask_popover")
        val clearSelectionAfterPopover = booleanPreferencesKey("clear_selection_after_popover")
        val systemNotifications = booleanPreferencesKey("system_notifications")
        val emailLoginNotifications = booleanPreferencesKey("email_login_notifications")
        val emailPasswordNotifications = booleanPreferencesKey("email_password_notifications")
        val lineSpacing = floatPreferencesKey("line_spacing")
        val selectionHighlight = stringPreferencesKey("selection_highlight")
        val activeThemeId = stringPreferencesKey("active_theme_id")
        val completed = stringPreferencesKey("completed_sections")
        val bookmarks = stringPreferencesKey("bookmarked_sections")
        val courses = stringPreferencesKey("cached_courses_v1")
        val user = stringPreferencesKey("cached_user_v1")
    }

    private var decodedUserReady = false
    private var decodedUserSource: String? = null
    private var decodedUserValue: User? = null

    /** Preference decoding stays off-main so even a migrated legacy file cannot delay first paint. */
    val snapshot: Flow<LocalSnapshot> = context.dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { prefs ->
            LocalSnapshot(
                settings = AppSettings(
                    themeMode = runCatching { ThemeMode.valueOf(prefs[Keys.theme] ?: ThemeMode.SYSTEM.name) }
                        .getOrDefault(ThemeMode.SYSTEM),
                    reduceMotion = prefs[Keys.reduceMotion] ?: false,
                    haptics = prefs[Keys.haptics] ?: true,
                    fontScale = (prefs[Keys.fontScale] ?: 1f).coerceIn(.85f, 1.35f),
                    keepScreenOn = prefs[Keys.keepScreenOn] ?: false,
                    persistChatHistory = prefs[Keys.persistChatHistory] ?: true,
                    rememberPosition = prefs[Keys.rememberPosition] ?: true,
                    showReadingProgress = prefs[Keys.showReadingProgress] ?: false,
                    showGreenChecks = prefs[Keys.showGreenChecks] ?: true,
                    showActionSuggestions = prefs[Keys.showActionSuggestions] ?: true,
                    showChatPromptNavigator = prefs[Keys.showChatPromptNavigator] ?: true,
                    enableAskPopover = prefs[Keys.enableAskPopover] ?: true,
                    clearSelectionAfterPopover = prefs[Keys.clearSelectionAfterPopover] ?: false,
                    systemNotifications = prefs[Keys.systemNotifications] ?: false,
                    emailLoginNotifications = prefs[Keys.emailLoginNotifications] ?: true,
                    emailPasswordNotifications = prefs[Keys.emailPasswordNotifications] ?: true,
                    lineSpacing = (prefs[Keys.lineSpacing] ?: 1f).coerceIn(1f, 1.35f),
                    selectionHighlight = prefs[Keys.selectionHighlight] ?: "default",
                    activeThemeId = prefs[Keys.activeThemeId] ?: "default"
                ),
                completed = decodeSet(prefs[Keys.completed]),
                bookmarks = decodeSet(prefs[Keys.bookmarks]),
                user = cachedDecodeUser(prefs[Keys.user])
            )
        }
        .flowOn(Dispatchers.IO)

    suspend fun saveSettings(settings: AppSettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.theme] = settings.themeMode.name
            prefs[Keys.reduceMotion] = settings.reduceMotion
            prefs[Keys.haptics] = settings.haptics
            prefs[Keys.fontScale] = settings.fontScale
            prefs[Keys.keepScreenOn] = settings.keepScreenOn
            prefs[Keys.persistChatHistory] = settings.persistChatHistory
            prefs[Keys.rememberPosition] = settings.rememberPosition
            prefs[Keys.showReadingProgress] = settings.showReadingProgress
            prefs[Keys.showGreenChecks] = settings.showGreenChecks
            prefs[Keys.showActionSuggestions] = settings.showActionSuggestions
            prefs[Keys.showChatPromptNavigator] = settings.showChatPromptNavigator
            prefs[Keys.enableAskPopover] = settings.enableAskPopover
            prefs[Keys.clearSelectionAfterPopover] = settings.clearSelectionAfterPopover
            prefs[Keys.systemNotifications] = settings.systemNotifications
            prefs[Keys.emailLoginNotifications] = settings.emailLoginNotifications
            prefs[Keys.emailPasswordNotifications] = settings.emailPasswordNotifications
            prefs[Keys.lineSpacing] = settings.lineSpacing
            prefs[Keys.selectionHighlight] = settings.selectionHighlight
            prefs[Keys.activeThemeId] = settings.activeThemeId
        }
    }

    suspend fun saveCompleted(values: Set<String>) {
        context.dataStore.edit { it[Keys.completed] = encodeSet(values) }
    }

    suspend fun saveBookmarks(values: Set<String>) {
        context.dataStore.edit { it[Keys.bookmarks] = encodeSet(values) }
    }

    /** Atomically caches the small account snapshot received from the server. */
    suspend fun saveRemoteSnapshot(
        user: User?,
        completed: Set<String>,
        bookmarks: Set<String>
    ) {
        val encodedUser = withContext(Dispatchers.Default) { user?.let(::encodeUser) }
        context.dataStore.edit { prefs ->
            // Catalog metadata is already bundled in the APK. Older builds
            // duplicated its ~273 KB JSON inside DataStore, forcing two parses
            // on every launch. Remove that legacy value during the next save.
            prefs.remove(Keys.courses)
            if (encodedUser == null) prefs.remove(Keys.user) else prefs[Keys.user] = encodedUser
            prefs[Keys.completed] = encodeSet(completed)
            prefs[Keys.bookmarks] = encodeSet(bookmarks)
        }
    }

    private fun encodeSet(values: Set<String>) = values.sorted().joinToString("\n")
    private fun decodeSet(value: String?) = value.orEmpty().lineSequence().filter(String::isNotBlank).toSet()

    private fun cachedDecodeUser(raw: String?): User? {
        if (decodedUserReady && raw == decodedUserSource) return decodedUserValue
        return decodeUser(raw).also {
            decodedUserReady = true
            decodedUserSource = raw
            decodedUserValue = it
        }
    }
}

private fun encodeUser(user: User): String = JSONObject().apply {
    put("userId", user.userId)
    put("email", user.email)
    put("displayName", user.displayName)
    put("photoUrl", user.photoUrl ?: JSONObject.NULL)
    put("isAdmin", user.isAdmin)
    put("isGoogle", user.isGoogle)
}.toString()

private fun decodeUser(raw: String?): User? = runCatching {
    val item = JSONObject(raw ?: return@runCatching null)
    val id = item.optString("userId")
    val email = item.optString("email")
    if (id.isBlank() && email.isBlank()) return@runCatching null
    User(
        userId = id,
        email = email,
        displayName = item.optString("displayName").ifBlank { email.substringBefore('@') },
        photoUrl = item.optString("photoUrl").takeUnless { item.isNull("photoUrl") || it.isBlank() },
        isAdmin = item.optBoolean("isAdmin"),
        isGoogle = item.optBoolean("isGoogle")
    )
}.getOrNull()
