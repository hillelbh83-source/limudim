package com.hillel.studyzone.ui.screens

import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hillel.studyzone.BuildConfig
import com.hillel.studyzone.model.Course
import com.hillel.studyzone.model.RootTab
import com.hillel.studyzone.model.ThemeMode
import com.hillel.studyzone.model.UiState
import com.hillel.studyzone.ui.components.GlassSurface
import com.hillel.studyzone.ui.components.Pressable
import com.hillel.studyzone.ui.components.RoundActionButton
import com.hillel.studyzone.ui.theme.StudyBlue
import kotlinx.coroutines.delay

@Composable
fun CoursesScreen(
    state: UiState,
    onCourse: (Course) -> Unit,
    onProfile: () -> Unit,
    onThemeToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    var category by remember { mutableStateOf("all") }
    val categories = listOf(
        "all" to "הכול",
        "math" to "מתמטיקה",
        "engineering" to "הנדסה",
        "judaism" to "יהדות",
        "school" to "בגרויות",
        "other" to "נוספים"
    )
    val filtered = if (category == "all") state.courses else state.courses.filter { it.category == category }

    Column(modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("StudyZone", style = MaterialTheme.typography.headlineLarge)
                Text(
                    if (state.user != null) "שלום ${state.user.displayName}, ממשיכים ללמוד" else "כל מה שצריך כדי להבין באמת",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            RoundActionButton(
                icon = if (state.settings.themeMode == ThemeMode.DARK) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                contentDescription = "החלפת ערכת צבעים",
                onClick = onThemeToggle,
                size = 46.dp
            )
            Spacer(Modifier.width(8.dp))
            RoundActionButton(Icons.Rounded.Person, "פרופיל", onProfile, size = 46.dp, active = state.user != null)
        }

        androidx.compose.foundation.lazy.LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(categories.size) { index ->
                val item = categories[index]
                Pressable(
                    onClick = { category = item.first },
                    selected = category == item.first,
                    shape = CircleShape,
                    contentPadding = 11.dp
                ) {
                    Text(item.second, color = if (category == item.first) StudyBlue else MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(164.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 18.dp, end = 18.dp, bottom = 130.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(filtered, key = { it.id }) { course ->
                CourseCard(course, state.completedSections, onClick = { onCourse(course) })
            }
        }
    }
}

@Composable
private fun CourseCard(course: Course, completed: Set<String>, onClick: () -> Unit) {
    val allSections = course.chapters.sumOf { it.sections.size }.coerceAtLeast(1)
    val completedCount = completed.count { it.startsWith("${course.id}/") }
    val progress = completedCount.toFloat() / allSections
    val icon = when (course.iconId) {
        "code" -> Icons.Rounded.Code
        "math" -> Icons.Rounded.Calculate
        "activity" -> Icons.Rounded.Psychology
        else -> Icons.AutoMirrored.Rounded.MenuBook
    }
    Pressable(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().aspectRatio(.84f),
        contentPadding = 0.dp
    ) {
        Column(Modifier.fillMaxSize().padding(17.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(StudyBlue.copy(alpha = .13f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = StudyBlue)
                }
                Spacer(Modifier.weight(1f))
                Text("${course.chapters.size}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
            }
            Column {
                Text(course.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Text(
                    course.description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outlineVariant)) {
                    Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(StudyBlue))
                }
            }
        }
    }
}

@Composable
fun CourseDetailScreen(
    state: UiState,
    onBack: () -> Unit,
    onLesson: (String) -> Unit,
    onRequestAccess: () -> Unit
) {
    val course = state.selectedCourse ?: return
    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, bottom = 130.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundActionButton(Icons.AutoMirrored.Rounded.ArrowForward, "חזרה", onBack, size = 46.dp)
                Spacer(Modifier.weight(1f))
                RoundActionButton(Icons.Rounded.MoreHoriz, "פעולות נוספות", onRequestAccess, size = 46.dp)
            }
            Spacer(Modifier.height(22.dp))
            Text(course.title, style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(10.dp))
            Text(course.description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(28.dp))
        }
        course.chapters.forEach { chapter ->
            item(key = "chapter-${chapter.id}") {
                Text(chapter.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 18.dp, bottom = 10.dp))
            }
            items(chapter.sections, key = { it.id }) { section ->
                val done = "${course.id}/${chapter.id}/${section.id}" in state.completedSections
                Pressable(
                    onClick = { onLesson(section.id) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                    contentPadding = 14.dp
                ) {
                    Box(
                        Modifier.size(38.dp).clip(CircleShape)
                            .background(if (done) StudyBlue else MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        if (done) Icon(Icons.Rounded.CheckCircle, null, tint = Color.White, modifier = Modifier.size(19.dp))
                        else Text(section.id.substringAfter('.'), fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(section.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (section.preview.isNotBlank()) Text(section.preview, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                    Icon(Icons.Rounded.ChevronLeft, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun SearchScreen(state: UiState, onQuery: (String) -> Unit, onResult: (String, String) -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp)) {
        Text("חיפוש", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 20.dp, bottom = 14.dp))
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = onQuery,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("מושג, נוסחה או נושא…") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            shape = RoundedCornerShape(26.dp),
            singleLine = true
        )
        Spacer(Modifier.height(14.dp))
        if (state.searchLoading) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).padding(24.dp))
        if (!state.searchLoading && state.searchQuery.length >= 2 && state.searchResults.isEmpty()) {
            EmptyState(Icons.Rounded.Search, "לא מצאנו תוצאה", "נסו ניסוח קצר או שם של קורס")
        }
        LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 130.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(state.searchResults, key = { "${it.courseId}-${it.sectionId}" }) { result ->
                Pressable(onClick = { onResult(result.courseId, result.sectionId) }, modifier = Modifier.fillMaxWidth(), contentPadding = 16.dp) {
                    Column(Modifier.weight(1f)) {
                        Text(result.title, style = MaterialTheme.typography.titleMedium)
                        Text("${result.courseTitle} · ${result.sectionId}", color = StudyBlue, style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(7.dp))
                        Text(result.snippet, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(Icons.Rounded.ChevronLeft, null)
                }
            }
        }
    }
}

@Composable
fun SavedScreen(state: UiState, onLesson: (String, String) -> Unit) {
    val saved = remember(state.bookmarkedSections, state.courses) {
        state.bookmarkedSections.mapNotNull { key ->
            val (courseId, sectionId) = key.split("::", limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
            val course = state.courses.firstOrNull { it.id == courseId } ?: return@mapNotNull null
            val section = course.chapters.flatMap { it.sections }.firstOrNull { it.id == sectionId } ?: return@mapNotNull null
            Triple(course, sectionId, section.title)
        }
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp)) {
        Text("שמורים", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 20.dp, bottom = 16.dp))
        if (saved.isEmpty()) EmptyState(Icons.Rounded.BookmarkBorder, "עדיין אין שמורים", "הקישו על הסימנייה בתוך שיעור כדי לשמור אותו")
        LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 130.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(saved, key = { "${it.first.id}-${it.second}" }) { item ->
                Pressable(onClick = { onLesson(item.first.id, item.second) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Bookmark, null, tint = StudyBlue)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.third, fontWeight = FontWeight.Bold)
                        Text(item.first.title, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Rounded.ChevronLeft, null)
                }
            }
        }
    }
}

@Composable
fun ToolsScreen(onPickPdf: () -> Unit) {
    var timerRunning by remember { mutableStateOf(false) }
    var seconds by remember { mutableIntStateOf(25 * 60) }
    var notes by remember { mutableStateOf("") }
    LaunchedEffect(timerRunning) {
        while (timerRunning && seconds > 0) {
            delay(1000)
            seconds -= 1
        }
        if (seconds == 0) timerRunning = false
    }
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 130.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Text("כלי לימוד", style = MaterialTheme.typography.headlineLarge) }
        item {
            GlassSurface(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Timer, null, tint = StudyBlue)
                        Spacer(Modifier.width(10.dp))
                        Text("טיימר פוקוס", style = MaterialTheme.typography.titleLarge)
                    }
                    Text(
                        "%02d:%02d".format(seconds / 60, seconds % 60),
                        style = MaterialTheme.typography.displaySmall,
                        modifier = Modifier.padding(22.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Pressable(onClick = { timerRunning = !timerRunning }, selected = timerRunning) {
                            Icon(if (timerRunning) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (timerRunning) "עצירה" else "התחלה")
                        }
                        Pressable(onClick = { timerRunning = false; seconds = 25 * 60 }) {
                            Icon(Icons.Rounded.Refresh, null)
                            Spacer(Modifier.width(8.dp))
                            Text("איפוס")
                        }
                    }
                }
            }
        }
        item {
            Pressable(onClick = onPickPdf, modifier = Modifier.fillMaxWidth(), contentPadding = 18.dp) {
                Box(Modifier.size(46.dp).clip(CircleShape).background(StudyBlue.copy(.14f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.PictureAsPdf, null, tint = StudyBlue)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("פתיחת PDF", style = MaterialTheme.typography.titleMedium)
                    Text("בחירה דרך מנהל הקבצים של Android", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Rounded.ChevronLeft, null)
            }
        }
        item {
            GlassSurface(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text("דף טיוטה", style = MaterialTheme.typography.titleLarge)
                    Text("מקום מהיר לחישובים ורעיונות", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        modifier = Modifier.fillMaxWidth().height(180.dp),
                        placeholder = { Text("כתבו כאן…") },
                        shape = RoundedCornerShape(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ProfileScreen(
    state: UiState,
    onAuth: () -> Unit,
    onLogout: () -> Unit,
    onAdmin: () -> Unit,
    onTheme: (ThemeMode) -> Unit,
    onReduceMotion: (Boolean) -> Unit,
    onHaptics: (Boolean) -> Unit,
    onFontScale: (Float) -> Unit,
    onKeepScreenOn: (Boolean) -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 130.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Text("פרופיל והגדרות", style = MaterialTheme.typography.headlineLarge) }
        item {
            GlassSurface(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(58.dp).clip(CircleShape).background(StudyBlue), contentAlignment = Alignment.Center) {
                        Text(state.user?.displayName?.take(1)?.uppercase() ?: "?", color = Color.White, style = MaterialTheme.typography.headlineMedium)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(state.user?.displayName ?: "אורח", style = MaterialTheme.typography.titleLarge)
                        Text(state.user?.email ?: "התחברו כדי לסנכרן התקדמות", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (state.user == null) TextButton(onClick = onAuth) { Text("התחברות") }
                }
            }
        }
        if (state.user?.isAdmin == true) {
            item {
                Pressable(onClick = onAdmin, modifier = Modifier.fillMaxWidth(), selected = true) {
                    Icon(Icons.Rounded.AdminPanelSettings, null, tint = StudyBlue)
                    Spacer(Modifier.width(12.dp))
                    Text("תפריט מנהלים", Modifier.weight(1f), color = StudyBlue, fontWeight = FontWeight.Bold)
                    Icon(Icons.Rounded.ChevronLeft, null, tint = StudyBlue)
                }
            }
        }
        item {
            SettingsGroup("ערכת צבעים", Icons.Rounded.Palette) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        Triple(ThemeMode.SYSTEM, "מערכת", Icons.Rounded.Settings),
                        Triple(ThemeMode.LIGHT, "בהיר", Icons.Rounded.LightMode),
                        Triple(ThemeMode.DARK, "כהה", Icons.Rounded.DarkMode)
                    ).forEach { option ->
                        Pressable(
                            onClick = { onTheme(option.first) },
                            selected = state.settings.themeMode == option.first,
                            modifier = Modifier.weight(1f),
                            contentPadding = 10.dp
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(option.third, null, tint = if (state.settings.themeMode == option.first) StudyBlue else MaterialTheme.colorScheme.onSurface)
                                Text(option.second, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
        item {
            SettingsGroup("נגישות ותנועה", Icons.Rounded.AutoAwesome) {
                ToggleSetting("הפחתת תנועה", "מעדן מעברים ואפקטי spring", state.settings.reduceMotion, onReduceMotion)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ToggleSetting("משוב רטט", "רטט עדין בפעולות", state.settings.haptics, onHaptics)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ToggleSetting("השארת המסך דולק", "שימושי בזמן פתרון תרגילים", state.settings.keepScreenOn, onKeepScreenOn)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text("גודל טקסט", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
                Slider(value = state.settings.fontScale, onValueChange = onFontScale, valueRange = .85f..1.35f)
            }
        }
        if (state.user != null) {
            item {
                Pressable(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Logout, null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(10.dp))
                    Text("התנתקות", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            }
        }
        item {
            Text(
                "StudyZone Android · 1.0.0\nNative Kotlin + Jetpack Compose",
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
private fun SettingsGroup(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    GlassSurface(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = StudyBlue)
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun ToggleSetting(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun EmptyState(icon: ImageVector, title: String, subtitle: String) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 72.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(74.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}
