package com.hillel.studyzone.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hillel.studyzone.model.AppSettings
import com.hillel.studyzone.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "studyzone_preferences")

class LocalPreferences(private val context: Context) {
    private object Keys {
        val theme = stringPreferencesKey("theme")
        val reduceMotion = booleanPreferencesKey("reduce_motion")
        val haptics = booleanPreferencesKey("haptics")
        val fontScale = floatPreferencesKey("font_scale")
        val keepScreenOn = booleanPreferencesKey("keep_screen_on")
        val completed = stringPreferencesKey("completed_sections")
        val bookmarks = stringPreferencesKey("bookmarked_sections")
    }

    val snapshot: Flow<Triple<AppSettings, Set<String>, Set<String>>> = context.dataStore.data.map { prefs ->
        val settings = AppSettings(
            themeMode = runCatching { ThemeMode.valueOf(prefs[Keys.theme] ?: ThemeMode.SYSTEM.name) }
                .getOrDefault(ThemeMode.SYSTEM),
            reduceMotion = prefs[Keys.reduceMotion] ?: false,
            haptics = prefs[Keys.haptics] ?: true,
            fontScale = (prefs[Keys.fontScale] ?: 1f).coerceIn(.85f, 1.35f),
            keepScreenOn = prefs[Keys.keepScreenOn] ?: false
        )
        Triple(settings, decodeSet(prefs[Keys.completed]), decodeSet(prefs[Keys.bookmarks]))
    }

    suspend fun saveSettings(settings: AppSettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.theme] = settings.themeMode.name
            prefs[Keys.reduceMotion] = settings.reduceMotion
            prefs[Keys.haptics] = settings.haptics
            prefs[Keys.fontScale] = settings.fontScale
            prefs[Keys.keepScreenOn] = settings.keepScreenOn
        }
    }

    suspend fun saveCompleted(values: Set<String>) {
        context.dataStore.edit { it[Keys.completed] = encodeSet(values) }
    }

    suspend fun saveBookmarks(values: Set<String>) {
        context.dataStore.edit { it[Keys.bookmarks] = encodeSet(values) }
    }

    private fun encodeSet(values: Set<String>) = values.sorted().joinToString("\n")
    private fun decodeSet(value: String?) = value.orEmpty().lineSequence().filter(String::isNotBlank).toSet()
}
