package com.hillel.studyzone.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hillel.studyzone.BuildConfig
import com.hillel.studyzone.model.Course
import com.hillel.studyzone.model.ThemeMode
import com.hillel.studyzone.model.UiState
import com.hillel.studyzone.ui.components.GlassSurface
import com.hillel.studyzone.ui.components.LiquidSlider
import com.hillel.studyzone.ui.components.LiquidSwitch
import com.hillel.studyzone.ui.components.LiquidTextField
import com.hillel.studyzone.ui.components.Pressable
import com.hillel.studyzone.ui.components.ProfileAvatar
import com.hillel.studyzone.ui.components.RoundActionButton
import com.hillel.studyzone.ui.theme.StudyBlue
import kotlinx.coroutines.delay

@Composable
fun CoursesScreen(
    state: UiState,
    onCourse: (Course) -> Unit,
    onProfile: () -> Unit,
    darkMode: Boolean,
    onThemeToggle: (Offset) -> Unit,
    modifier: Modifier = Modifier
) {
    var category by remember { mutableStateOf("all") }
    var themeButtonCenter by remember { mutableStateOf(Offset.Zero) }
    val categories = listOf(
        "all" to "הכול",
        "math" to "מתמטיקה",
        "engineering" to "הנדסה",
        "judaism" to "יהדות",
        "school" to "בגרויות",
        "other" to "נוספים"
    )
    val filtered = remember(category, state.courses) {
        if (category == "all") state.courses else state.courses.filter { it.category == category }
    }
    var headerEntered by remember { mutableStateOf(state.settings.reduceMotion) }
    var chipsEntered by remember { mutableStateOf(state.settings.reduceMotion) }
    LaunchedEffect(state.settings.reduceMotion) {
        if (state.settings.reduceMotion) {
            headerEntered = true
            chipsEntered = true
        } else {
            headerEntered = true
            delay(45)
            chipsEntered = true
        }
    }

    Column(modifier.fillMaxSize().statusBarsPadding()) {
        AnimatedVisibility(
            visible = headerEntered,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it / 3 }),
            exit = fadeOut()
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("StudyZone", style = MaterialTheme.typography.headlineLarge)
                    Text(
                        state.user?.displayName
                            ?.takeIf { it.isNotBlank() }
                            ?.let { "שלום $it, ממשיכים ללמוד" }
                            ?: "כל מה שצריך כדי להבין באמת",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(10.dp))
                RoundActionButton(
                    icon = if (darkMode) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                    contentDescription = "החלפת ערכת צבעים",
                    onClick = { onThemeToggle(themeButtonCenter) },
                    modifier = Modifier.onGloballyPositioned { coordinates ->
                        val topLeft = coordinates.positionInRoot()
                        themeButtonCenter = topLeft + Offset(coordinates.size.width / 2f, coordinates.size.height / 2f)
                    },
                    size = 46.dp
                )
                Spacer(Modifier.width(8.dp))
                Pressable(
                    onClick = onProfile,
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    selected = state.user != null,
                    contentPadding = 0.dp
                ) {
                    ProfileAvatar(
                        photoUrl = state.user?.photoUrl,
                        displayName = state.user?.displayName ?: "אורח",
                        size = 46.dp
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = chipsEntered,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
            exit = fadeOut()
        ) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories.size) { index ->
                    val item = categories[index]
                    Pressable(
                        onClick = { category = item.first },
                        selected = category == item.first,
                        shape = CircleShape,
                        contentPadding = 10.dp
                    ) {
                        Text(
                            item.second,
                            color = if (category == item.first) StudyBlue else MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(292.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 136.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.courses.isEmpty()) {
                items(4) { CourseCardSkeleton() }
            } else if (filtered.isEmpty()) {
                item { EmptyState(Icons.Rounded.School, "אין קורסים בקטגוריה", "אפשר לבחור קטגוריה אחרת") }
            } else {
                itemsIndexed(filtered, key = { _, course -> course.id }) { index, course ->
                    var entered by remember(course.id) { mutableStateOf(state.settings.reduceMotion) }
                    LaunchedEffect(course.id, state.settings.reduceMotion) {
                        if (!state.settings.reduceMotion) delay(index.coerceAtMost(7) * 32L)
                        entered = true
                    }
                    AnimatedVisibility(
                        visible = entered,
                        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 5 }) + scaleIn(initialScale = .97f),
                        exit = fadeOut() + scaleOut(targetScale = .97f)
                    ) {
                        val courseId = course.id.lowercase()
                        val accessGranted = course.isAvailable && state.courseAccessLoaded &&
                            (state.courseAccessAll || courseId in state.accessibleCourseIds)
                        val accessLabel = when {
                            !course.isAvailable -> "בפיתוח"
                            !state.courseAccessLoaded -> "בודק גישה"
                            courseId in state.publicAccessCourseIds -> "ציבורי"
                            accessGranted -> "פתוח"
                            courseId in state.pendingCourseIds -> "ממתין"
                            else -> "נעול"
                        }
                        CourseCard(
                            course = course,
                            completed = state.completedSections,
                            accessGranted = accessGranted,
                            accessLabel = accessLabel,
                            onClick = { onCourse(course) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun CourseCard(
    course: Course,
    completed: Set<String>,
    accessGranted: Boolean,
    accessLabel: String,
    onClick: () -> Unit
) {
    val sectionCount = course.chapters.sumOf { it.sections.size }
    val completedCount = completed.count { it.startsWith("${course.id}/") }.coerceAtMost(sectionCount)
    val targetProgress = if (sectionCount == 0) 0f else completedCount.toFloat() / sectionCount
    val progress by animateFloatAsState(targetProgress, spring(stiffness = 700f, dampingRatio = .82f), label = "courseProgress")
    val icon = courseIcon(course.iconId)
    val progressPercent = (progress * 100).toInt().coerceIn(0, 100)

    Pressable(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = 0.dp,
        // Locked courses must still open their detail page so the user can
        // understand the restriction and request access there.
        enabled = true
    ) {
        Column(
            Modifier.fillMaxWidth().heightIn(min = 310.dp).padding(22.dp),
            verticalArrangement = Arrangement.Top
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                CourseIcon(icon, 58.dp)
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "$progressPercent%",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text("הושלם", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                course.title,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.weight(1f).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 11.dp, vertical = 7.dp)
                ) {
                    Text(
                        "${course.chapters.size} פרקים · $sectionCount שיעורים",
                        modifier = Modifier.fillMaxWidth().basicMarquee(
                            iterations = Int.MAX_VALUE,
                            initialDelayMillis = 900,
                            repeatDelayMillis = 500
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1
                    )
                }
                Box(
                    Modifier.clip(CircleShape)
                        .background(if (accessGranted) StudyBlue.copy(alpha = .12f) else MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                ) {
                    Text(
                        accessLabel,
                        color = if (accessGranted) StudyBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                course.description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.weight(1f))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when {
                        !accessGranted -> if (accessLabel == "בודק גישה") "טוען הרשאות…" else "נדרשת הרשאה"
                        progressPercent == 100 -> "הקורס הושלם"
                        progressPercent > 0 -> "המשך ללמוד"
                        else -> "התחלת הקורס"
                    },
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
                Box(
                    Modifier.size(42.dp).clip(CircleShape)
                        .background(if (accessGranted && progressPercent > 0) StudyBlue else MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        when {
                            !accessGranted -> Icons.Rounded.Lock
                            progressPercent == 100 -> Icons.Rounded.CheckCircle
                            else -> Icons.Rounded.ChevronLeft
                        },
                        null,
                        tint = if (accessGranted && progressPercent > 0) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            CourseProgress(progress, completedCount, sectionCount)
        }
    }
}

@Composable
private fun CourseIcon(icon: ImageVector, size: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * .36f)).background(StudyBlue.copy(alpha = .13f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = StudyBlue, modifier = Modifier.size(size * .48f))
    }
}

@Composable
private fun CourseProgress(progress: Float, completed: Int, total: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.weight(1f).height(5.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outlineVariant)
        ) {
            Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(StudyBlue))
        }
        Spacer(Modifier.width(9.dp))
        Text(
            if (total == 0) "חדש" else "$completed/$total",
            color = if (progress > 0f) StudyBlue else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun CourseCardSkeleton() {
    GlassSurface(Modifier.fillMaxWidth().height(174.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant))
            Box(Modifier.fillMaxWidth(.64f).height(18.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant))
            Box(Modifier.fillMaxWidth().height(12.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant))
            Box(Modifier.fillMaxWidth(.8f).height(12.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant))
        }
    }
}

private fun courseIcon(iconId: String): ImageVector = when (iconId) {
    "code" -> Icons.Rounded.Code
    "math", "function", "probability", "complex" -> Icons.Rounded.Calculate
    "activity", "wave", "electricity" -> Icons.Rounded.Psychology
    "history" -> Icons.Rounded.Description
    "school" -> Icons.Rounded.School
    else -> Icons.AutoMirrored.Rounded.MenuBook
}

@Composable
fun CourseDetailScreen(
    state: UiState,
    onBack: () -> Unit,
    onLesson: (String) -> Unit,
    onRequestAccess: () -> Unit
) {
    val course = state.selectedCourse ?: return
    var expandedChapters by remember(course.id) {
        mutableStateOf(course.chapters.firstOrNull()?.id?.let(::setOf).orEmpty())
    }
    val totalSections = course.chapters.sumOf { it.sections.size }
    val completedInCourse = state.completedSections.count { it.startsWith("${course.id}/") }.coerceAtMost(totalSections)
    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 136.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundActionButton(Icons.AutoMirrored.Rounded.ArrowBack, "חזרה", onBack, size = 46.dp)
                Spacer(Modifier.weight(1f))
                if (!course.isAvailable) {
                    RoundActionButton(Icons.Rounded.Lock, "בקשת גישה לקורס", onRequestAccess, size = 46.dp)
                }
            }
            Spacer(Modifier.height(12.dp))
            GlassSurface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(32.dp)) {
                Column(Modifier.padding(22.dp)) {
                    CourseIcon(courseIcon(course.iconId), 60.dp)
                    Spacer(Modifier.height(18.dp))
                    Text(course.title, style = MaterialTheme.typography.displaySmall)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        course.description,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(Modifier.height(20.dp))
                    CourseProgress(
                        progress = if (totalSections == 0) 0f else completedInCourse.toFloat() / totalSections,
                        completed = completedInCourse,
                        total = totalSections
                    )
                }
            }
            Spacer(Modifier.height(22.dp))
            Text("תוכן הקורס", style = MaterialTheme.typography.headlineMedium)
            Text(
                "${course.chapters.size} פרקים · $totalSections שיעורים",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(10.dp))
        }
        if (course.chapters.isEmpty()) {
            item { EmptyState(Icons.AutoMirrored.Rounded.MenuBook, "הקורס מתעדכן", "התכנים יופיעו כאן ברגע שיהיו זמינים") }
        }
        course.chapters.forEach { chapter ->
            item(key = "chapter-${chapter.id}") {
                val expanded = chapter.id in expandedChapters
                val completedInChapter = chapter.sections.count { section ->
                    "${course.id}/${chapter.id}/${section.id}" in state.completedSections
                }
                GlassSurface(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).animateContentSize(spring(dampingRatio = .85f)),
                    shape = RoundedCornerShape(26.dp),
                    selected = expanded
                ) {
                    Column {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(26.dp))
                                .clickable {
                                    expandedChapters = if (expanded) expandedChapters - chapter.id else expandedChapters + chapter.id
                                }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.size(44.dp).clip(CircleShape).background(StudyBlue.copy(alpha = if (expanded) .2f else .11f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(chapter.id, color = StudyBlue, fontWeight = FontWeight.ExtraBold)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(chapter.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${chapter.sections.size} שיעורים · $completedInChapter הושלמו",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                            Icon(
                                if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                if (expanded) "כיווץ פרק" else "פתיחת פרק",
                                tint = if (expanded) StudyBlue else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        AnimatedVisibility(
                            visible = expanded,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                chapter.sections.forEachIndexed { index, section ->
                                    val done = "${course.id}/${chapter.id}/${section.id}" in state.completedSections
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable(enabled = course.isAvailable) { onLesson(section.id) }
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            Modifier.size(38.dp).clip(CircleShape)
                                                .background(if (done) StudyBlue else MaterialTheme.colorScheme.surfaceVariant),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (done) {
                                                Icon(Icons.Rounded.CheckCircle, "הושלם", tint = Color.White, modifier = Modifier.size(19.dp))
                                            } else {
                                                Text(
                                                    section.id.substringAfterLast('.').ifBlank { (index + 1).toString() },
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.labelLarge
                                                )
                                            }
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(section.title, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                            if (section.preview.isNotBlank()) {
                                                Text(
                                                    section.preview,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        Icon(Icons.Rounded.ChevronLeft, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (index != chapter.sections.lastIndex) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(start = 64.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .7f)
                                        )
                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SearchScreen(state: UiState, onQuery: (String) -> Unit, onResult: (String, String) -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp)) {
        Text("חיפוש", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 20.dp, bottom = 14.dp))
        LiquidTextField(
            value = state.searchQuery,
            onValueChange = onQuery,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("מושג, נוסחה או נושא…") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            shape = RoundedCornerShape(26.dp),
            singleLine = true
        )
        Spacer(Modifier.height(10.dp))
        val normalizedQuery = state.searchQuery.trim()
        val phase = when {
            normalizedQuery.length < 2 -> "idle"
            state.searchLoading || state.searchSettledQuery != normalizedQuery -> "loading"
            state.searchResults.isEmpty() -> "empty"
            else -> "results"
        }
        AnimatedContent(
            targetState = phase,
            modifier = Modifier.fillMaxWidth().weight(1f),
            transitionSpec = {
                (fadeIn(tween(220)) + slideInVertically { it / 14 }) togetherWith fadeOut(tween(120))
            },
            label = "searchPhase"
        ) { currentPhase ->
            when (currentPhase) {
                "idle" -> EmptyState(Icons.Rounded.Search, "מחפשים בכל השיעורים", "כתבו לפחות שתי אותיות ונמצא את המקום המדויק")
                "loading" -> SearchLoadingAnimation(normalizedQuery)
                "empty" -> EmptyState(Icons.Rounded.Search, "לא מצאנו תוצאה", "נסו ניסוח קצר או שם של קורס")
                else -> LazyColumn(
                    contentPadding = PaddingValues(top = 4.dp, bottom = 130.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
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
    }
}

@Composable
private fun SearchLoadingAnimation(query: String) {
    val infinite = rememberInfiniteTransition(label = "searchPulse")
    val pulse by infinite.animateFloat(
        initialValue = .72f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(720, easing = LinearEasing), RepeatMode.Reverse),
        label = "searchPulseScale"
    )
    Column(
        Modifier.fillMaxSize().padding(bottom = 92.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.size(82.dp).graphicsLayer { scaleX = pulse; scaleY = pulse }
                .clip(CircleShape).background(StudyBlue.copy(alpha = .12f)),
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.size(56.dp).clip(CircleShape).background(StudyBlue.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Search, null, tint = StudyBlue, modifier = Modifier.size(26.dp))
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("מחפשים ״$query״", style = MaterialTheme.typography.titleMedium)
        Text("עוברים על הקורסים והשיעורים…", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    LiquidTextField(
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
    onSaveMemory: (String, String) -> Unit,
    onRemoveMemory: (String) -> Unit,
    onUpdateName: (String) -> Unit,
    onUpdatePhoto: () -> Unit,
    onSendAdminMessage: (String) -> Unit,
    onChangePassword: (String, String) -> Unit
) {
    var displayName by remember { mutableStateOf(state.user?.displayName.orEmpty()) }
    var memoryTitle by remember { mutableStateOf("") }
    var memoryDetails by remember { mutableStateOf("") }
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var adminMessage by remember { mutableStateOf("") }
    var passwordMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.user?.displayName) {
        displayName = state.user?.displayName.orEmpty()
    }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 130.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Text("פרופיל", style = MaterialTheme.typography.headlineLarge) }
        item {
            GlassSurface(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(contentAlignment = Alignment.BottomEnd) {
                        ProfileAvatar(
                            photoUrl = state.user?.photoUrl,
                            displayName = state.user?.displayName ?: "אורח",
                            size = 62.dp
                        )
                        if (state.user != null) {
                            Box(
                                Modifier.size(27.dp).clip(CircleShape).background(StudyBlue).clickable(onClick = onUpdatePhoto),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.CameraAlt, "החלפת תמונת פרופיל", tint = Color.White, modifier = Modifier.size(15.dp))
                            }
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(state.user?.displayName ?: "אורח", style = MaterialTheme.typography.titleLarge)
                        Text(state.user?.email ?: "התחברו כדי לסנכרן התקדמות", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (state.user == null) {
                        Pressable(onClick = onAuth, shape = CircleShape, contentPadding = 10.dp) {
                            Text("התחברות", color = StudyBlue, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        if (state.user != null) {
            item {
                GlassSurface(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.ChatBubbleOutline, null, tint = StudyBlue)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("הודעה למנהל", style = MaterialTheme.typography.titleLarge)
                                Text("הצעה, שאלה או דיווח על תקלה", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        LiquidTextField(
                            value = adminMessage,
                            onValueChange = { adminMessage = it.take(2_000) },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("כתבו כאן ואנחנו נקרא הכול…") },
                            minLines = 3,
                            maxLines = 6,
                            shape = RoundedCornerShape(20.dp)
                        )
                        Pressable(
                            onClick = {
                                if (adminMessage.isNotBlank()) {
                                    onSendAdminMessage(adminMessage)
                                    adminMessage = ""
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            selected = adminMessage.isNotBlank(),
                            contentPadding = 13.dp
                        ) {
                            Icon(Icons.Rounded.ChatBubbleOutline, null, tint = StudyBlue)
                            Spacer(Modifier.width(9.dp))
                            Text("שליחת הודעה", color = StudyBlue, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            item {
                GlassSurface(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.AutoAwesome, null, tint = StudyBlue)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("פרטי החשבון", style = MaterialTheme.typography.titleLarge)
                                Text("השם שמופיע באתר ובשיחות עם פיתי", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        LiquidTextField(
                            value = displayName,
                            onValueChange = { displayName = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("שם תצוגה") },
                            singleLine = true,
                            shape = RoundedCornerShape(20.dp)
                        )
                        Pressable(
                            onClick = { if (displayName.isNotBlank()) onUpdateName(displayName.trim()) },
                            modifier = Modifier.fillMaxWidth(),
                            selected = true,
                            contentPadding = 13.dp
                        ) {
                            Icon(Icons.Rounded.CheckCircle, null, tint = StudyBlue)
                            Spacer(Modifier.width(9.dp))
                            Text("שמירת השם", color = StudyBlue, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            item {
                GlassSurface(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(44.dp).clip(CircleShape).background(StudyBlue.copy(.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.Psychology, null, tint = StudyBlue)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("הזיכרונות של פיתי", style = MaterialTheme.typography.titleLarge)
                                Text("עובדות שפיתי תזכור ותשתמש בהן בשיחות הבאות", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (state.pythiMemories.isEmpty()) {
                            Text(
                                "עדיין אין זיכרונות. אפשר להוסיף העדפות למידה, נושאים חשובים או כל פרט שיעזור לפיתי.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            state.pythiMemories.forEach { (title, details) ->
                                GlassSurface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(title, fontWeight = FontWeight.Bold)
                                            if (details.isNotBlank()) {
                                                Spacer(Modifier.height(3.dp))
                                                Text(details, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                        Pressable(onClick = { onRemoveMemory(title) }, shape = CircleShape, contentPadding = 9.dp) {
                                            Text("מחיקה", color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(.45f))
                        Text("זיכרון חדש", fontWeight = FontWeight.Bold)
                        LiquidTextField(
                            value = memoryTitle,
                            onValueChange = { memoryTitle = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("כותרת קצרה") },
                            placeholder = { Text("לדוגמה: איך נוח לי ללמוד") },
                            singleLine = true,
                            shape = RoundedCornerShape(20.dp)
                        )
                        LiquidTextField(
                            value = memoryDetails,
                            onValueChange = { memoryDetails = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("מה פיתי צריכה לזכור?") },
                            minLines = 2,
                            maxLines = 4,
                            shape = RoundedCornerShape(20.dp)
                        )
                        Pressable(
                            onClick = {
                                if (memoryTitle.isNotBlank() && memoryDetails.isNotBlank()) {
                                    onSaveMemory(memoryTitle.trim(), memoryDetails.trim())
                                    memoryTitle = ""
                                    memoryDetails = ""
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            selected = memoryTitle.isNotBlank() && memoryDetails.isNotBlank(),
                            contentPadding = 13.dp
                        ) {
                            Icon(Icons.Rounded.Add, null, tint = StudyBlue)
                            Spacer(Modifier.width(9.dp))
                            Text("הוספה לזיכרונות", color = StudyBlue, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            item {
                GlassSurface(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Key, null, tint = StudyBlue)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("סיסמה ואבטחה", style = MaterialTheme.typography.titleLarge)
                                Text("עדכון סיסמת החשבון", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (state.user?.isGoogle != true) {
                            LiquidTextField(
                                oldPassword,
                                { oldPassword = it; passwordMessage = null },
                                Modifier.fillMaxWidth(),
                                label = { Text("סיסמה נוכחית") },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                shape = RoundedCornerShape(20.dp)
                            )
                        } else {
                            Text(
                                "אפשר להגדיר סיסמה לחשבון Google גם בלי סיסמה נוכחית.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        LiquidTextField(
                            newPassword,
                            { newPassword = it; passwordMessage = null },
                            Modifier.fillMaxWidth(),
                            label = { Text("סיסמה חדשה") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            shape = RoundedCornerShape(20.dp)
                        )
                        LiquidTextField(
                            confirmPassword,
                            { confirmPassword = it; passwordMessage = null },
                            Modifier.fillMaxWidth(),
                            label = { Text("אימות סיסמה חדשה") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            isError = passwordMessage != null,
                            shape = RoundedCornerShape(20.dp)
                        )
                        passwordMessage?.let {
                            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                        Pressable(
                            onClick = {
                                passwordMessage = when {
                                    state.user?.isGoogle != true && oldPassword.isBlank() -> "צריך להזין את הסיסמה הנוכחית"
                                    newPassword.length < 8 -> "הסיסמה החדשה חייבת להכיל לפחות 8 תווים"
                                    newPassword != confirmPassword -> "הסיסמאות החדשות אינן תואמות"
                                    else -> {
                                        onChangePassword(oldPassword, newPassword)
                                        oldPassword = ""
                                        newPassword = ""
                                        confirmPassword = ""
                                        null
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            selected = true,
                            contentPadding = 13.dp
                        ) {
                            Icon(Icons.Rounded.Lock, null, tint = StudyBlue)
                            Spacer(Modifier.width(9.dp))
                            Text("עדכון סיסמה", color = StudyBlue, fontWeight = FontWeight.Bold)
                        }
                    }
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
                "StudyZone · ${BuildConfig.VERSION_NAME}",
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
fun SettingsScreen(
    state: UiState,
    onTheme: (ThemeMode, Offset) -> Unit,
    onReduceMotion: (Boolean) -> Unit,
    onHaptics: (Boolean) -> Unit,
    onFontScale: (Float) -> Unit,
    onKeepScreenOn: (Boolean) -> Unit,
    onPersistChatHistory: (Boolean) -> Unit,
    onRememberPosition: (Boolean) -> Unit,
    onReadingProgress: (Boolean) -> Unit,
    onGreenChecks: (Boolean) -> Unit,
    onActionSuggestions: (Boolean) -> Unit,
    onPromptNavigator: (Boolean) -> Unit,
    onAskPopover: (Boolean) -> Unit,
    onClearSelection: (Boolean) -> Unit,
    onSystemNotifications: (Boolean) -> Unit,
    onLoginNotifications: (Boolean) -> Unit,
    onPasswordNotifications: (Boolean) -> Unit,
    onLineSpacing: (Float) -> Unit,
    onSelectionHighlight: (String) -> Unit,
    onActiveTheme: (String) -> Unit,
    onSaveApiKeys: (List<String>) -> Unit
) {
    var newApiKey by remember { mutableStateOf("") }
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 132.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            GlassSurface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(34.dp), selected = true) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(54.dp).clip(RoundedCornerShape(18.dp)).background(StudyBlue),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Rounded.Settings, null, tint = Color.White, modifier = Modifier.size(27.dp)) }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("הגדרות", style = MaterialTheme.typography.headlineLarge)
                        Text("המראה והלמידה שלך, במקום אחד", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item {
            SettingsGroup("מראה וקריאות", Icons.Rounded.Palette) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        Triple(ThemeMode.SYSTEM, "מערכת", Icons.Rounded.Settings),
                        Triple(ThemeMode.LIGHT, "בהיר", Icons.Rounded.LightMode),
                        Triple(ThemeMode.DARK, "כהה", Icons.Rounded.DarkMode)
                    ).forEach { option ->
                        var optionCenter by remember(option.first) { mutableStateOf(Offset.Zero) }
                        Pressable(
                            onClick = { onTheme(option.first, optionCenter) },
                            selected = state.settings.themeMode == option.first,
                            modifier = Modifier.weight(1f).onGloballyPositioned { coordinates ->
                                val topLeft = coordinates.positionInRoot()
                                optionCenter = topLeft + Offset(coordinates.size.width / 2f, coordinates.size.height / 2f)
                            },
                            contentPadding = 9.dp
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(option.third, null, tint = if (state.settings.themeMode == option.first) StudyBlue else MaterialTheme.colorScheme.onSurface)
                                Text(option.second, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
                Text("גודל טקסט", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 16.dp))
                LiquidSlider(value = state.settings.fontScale, onValueChange = onFontScale, valueRange = .85f..1.35f)
                Text("מרווח שורות", fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1f to "רגיל", 1.16f to "מרווח", 1.3f to "רחב").forEach { option ->
                        Pressable(
                            onClick = { onLineSpacing(option.first) },
                            selected = kotlin.math.abs(state.settings.lineSpacing - option.first) < .05f,
                            modifier = Modifier.weight(1f),
                            contentPadding = 8.dp
                        ) { Text(option.second, style = MaterialTheme.typography.labelMedium) }
                    }
                }
                Text("ערכת צבעים", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 14.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf("default" to "קלאסי", "midnight" to "חצות", "ocean" to "אוקיינוס", "forest" to "יער", "sunset" to "שקיעה", "rose" to "ורד", "gold" to "זהב")) { theme ->
                        Pressable(onClick = { onActiveTheme(theme.first) }, selected = state.settings.activeThemeId == theme.first, contentPadding = 9.dp) {
                            Text(theme.second, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                Text("צבע סימון טקסט", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 14.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    items(listOf("default", "sunset", "mint", "sky", "butter", "lavender", "teal", "peach", "rose")) { color ->
                        Pressable(onClick = { onSelectionHighlight(color) }, selected = state.settings.selectionHighlight == color, contentPadding = 9.dp) {
                            Text(if (color == "default") "ברירת מחדל" else color, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
        item {
            SettingsGroup("למידה", Icons.Rounded.School) {
                ToggleSetting("זכירת מיקום גלילה", "חזרה למקום האחרון בקורס", state.settings.rememberPosition, onRememberPosition)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ToggleSetting("מד התקדמות קריאה", "פס התקדמות בראש השיעור", state.settings.showReadingProgress, onReadingProgress)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ToggleSetting("סימון וי ירוק", "סימון שיעורים שהושלמו", state.settings.showGreenChecks, onGreenChecks)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ToggleSetting("השארת המסך פעיל", "מונע כיבוי בזמן קריאה", state.settings.keepScreenOn, onKeepScreenOn)
            }
        }
        item {
            SettingsGroup("פיתי וצ׳אט", Icons.Rounded.ChatBubbleOutline) {
                ToggleSetting("שמירת היסטוריית צ׳אט", "השיחה תישמר בין פתיחות", state.settings.persistChatHistory, onPersistChatHistory)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ToggleSetting("הצעות לפעולה", "כפתורי תגובה, תרגול ובחנים", state.settings.showActionSuggestions, onActionSuggestions)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ToggleSetting("פורמטים בשיחה", "סרגל פורמטים בצד הצ׳אט", state.settings.showChatPromptNavigator, onPromptNavigator)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ToggleSetting("שאל את פיתי על טקסט", "פעולות מהירות לאחר סימון טקסט", state.settings.enableAskPopover, onAskPopover)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ToggleSetting("ניקוי סימון אוטומטי", "ביטול הסימון לאחר פתיחת הפעולה", state.settings.clearSelectionAfterPopover, onClearSelection)
            }
        }
        item {
            SettingsGroup("התראות", Icons.Rounded.NotificationsNone) {
                ToggleSetting("התראות מערכת", "הרשאת Android להתראות חשובות", state.settings.systemNotifications, onSystemNotifications)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ToggleSetting("מייל בעת התחברות", "התראה על כניסה לחשבון", state.settings.emailLoginNotifications, onLoginNotifications)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ToggleSetting("מייל כשסיסמה משתנה", "התראה לאחר עדכון סיסמה", state.settings.emailPasswordNotifications, onPasswordNotifications)
            }
        }
        item {
            SettingsGroup("תנועה ומגע", Icons.Rounded.AutoAwesome) {
                ToggleSetting("הפחתת תנועה", "ביטול springs והברקות לא חיוניות", state.settings.reduceMotion, onReduceMotion)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ToggleSetting("משוב רטט", "רטט עדין בניווט ובפעולות", state.settings.haptics, onHaptics)
            }
        }
        item {
            SettingsGroup("מפתחות API", Icons.Rounded.Key) {
                if (state.user == null) {
                    Text("יש להתחבר כדי לנהל מפתחות Gemini אישיים.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text("המפתחות נשמרים ומסונכרנים דרך החשבון.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    state.userApiKeys.forEachIndexed { index, _ ->
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("••••••••••••  #${index + 1}", modifier = Modifier.weight(1f))
                            Pressable(
                                onClick = { onSaveApiKeys(state.userApiKeys.filterIndexed { keyIndex, _ -> keyIndex != index }) },
                                shape = CircleShape,
                                contentPadding = 9.dp
                            ) { Text("מחיקה", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                    LiquidTextField(
                        value = newApiKey,
                        onValueChange = { newApiKey = it },
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        label = { Text("מפתח Gemini חדש") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        trailingIcon = { Icon(Icons.Rounded.Key, null) },
                        shape = RoundedCornerShape(18.dp)
                    )
                    Pressable(
                        onClick = {
                            val key = newApiKey.trim()
                            if (key.isNotBlank()) {
                                onSaveApiKeys(state.userApiKeys + key)
                                newApiKey = ""
                            }
                        },
                        enabled = newApiKey.isNotBlank() && !state.apiKeysLoading,
                        selected = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                    ) {
                        if (state.apiKeysLoading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Rounded.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text("הוספת מפתח")
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsGroup(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    GlassSurface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(32.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.padding(horizontal = 7.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(StudyBlue.copy(alpha = .14f)),
                    contentAlignment = Alignment.Center
                ) { Icon(icon, null, tint = StudyBlue, modifier = Modifier.size(21.dp)) }
                Spacer(Modifier.width(11.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(7.dp))
            content()
        }
    }
}

@Composable
private fun ToggleSetting(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable { onChecked(!checked) }
            .padding(horizontal = 8.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
        LiquidSwitch(
            checked = checked,
            onCheckedChange = onChecked
        )
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
