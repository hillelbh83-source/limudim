package com.hillel.studyzone.ui.screens

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.material.icons.rounded.School
import com.hillel.studyzone.model.AccessRequest
import com.hillel.studyzone.model.AdminUser
import com.hillel.studyzone.model.RootTab
import com.hillel.studyzone.model.UiState
import com.hillel.studyzone.ui.components.GlassSurface
import com.hillel.studyzone.ui.components.ChatRichText
import com.hillel.studyzone.ui.components.LessonMathView
import com.hillel.studyzone.ui.components.Pressable
import com.hillel.studyzone.ui.components.RoundActionButton
import com.hillel.studyzone.ui.theme.StudyBlue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.hypot

@Composable
fun IntroSplash(visible: Boolean) {
    var internallyVisible by remember { mutableStateOf(visible) }
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        internallyVisible = visible
        if (visible) {
            appeared = true
            // Opening motion is decorative and never blocks an already-ready app for long.
            delay(620)
            internallyVisible = false
        }
    }
    AnimatedVisibility(
        visible = internallyVisible,
        enter = fadeIn(),
        exit = fadeOut(animationSpec = androidx.compose.animation.core.tween(180))
    ) {
        val logoScale by animateFloatAsState(
            targetValue = if (appeared) 1f else .68f,
            animationSpec = spring(dampingRatio = .58f, stiffness = 430f),
            label = "introLogo"
        )
        val contentAlpha by animateFloatAsState(
            targetValue = if (appeared) 1f else 0f,
            animationSpec = androidx.compose.animation.core.tween(180),
            label = "introAlpha"
        )
        val orbit = rememberInfiniteTransition(label = "introOrbit")
        val orbitRotation by orbit.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
            label = "introOrbitRotation"
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(
                    colors = listOf(StudyBlue.copy(alpha = .22f), Color.Black),
                    radius = 720f
                )
            ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .size(146.dp)
                    .graphicsLayer {
                        scaleX = logoScale
                        scaleY = logoScale
                        alpha = contentAlpha
                        rotationZ = orbitRotation
                    }
                    .clip(CircleShape)
                    .background(
                        Brush.sweepGradient(
                            listOf(Color.Transparent, StudyBlue.copy(alpha = .42f), Color.Transparent)
                        )
                    )
            )
            Box(
                Modifier
                    .size(106.dp)
                    .graphicsLayer { scaleX = logoScale; scaleY = logoScale; alpha = contentAlpha }
                    .clip(CircleShape)
                    .background(StudyBlue.copy(alpha = .14f))
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer { alpha = contentAlpha }
            ) {
                Box(
                    Modifier.size(78.dp).graphicsLayer { scaleX = logoScale; scaleY = logoScale }
                        .clip(CircleShape).background(StudyBlue),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(36.dp))
                }
                Spacer(Modifier.height(18.dp))
                Text("StudyZone", color = Color.White, style = MaterialTheme.typography.headlineLarge)
            }
        }
    }
}

/** Circular palette reveal matching the website's theme transition without capturing a bitmap. */
@Composable
fun ThemeRevealOverlay(background: Color) {
    var previousBackground by remember { mutableStateOf(background) }
    var overlayColor by remember { mutableStateOf(background) }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(background) {
        if (background != previousBackground) {
            overlayColor = previousBackground
            previousBackground = background
            progress.snapTo(0f)
            progress.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
        }
    }
    if (progress.value < 1f) {
        Canvas(
            Modifier.fillMaxSize().graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
            }
        ) {
            val origin = Offset(82.dp.toPx(), 56.dp.toPx())
            val radius = hypot(size.width - origin.x, size.height - origin.y) * progress.value
            drawRect(overlayColor)
            drawCircle(Color.Transparent, radius = radius, center = origin, blendMode = BlendMode.Clear)
        }
    }
}

@Composable
fun BottomGlassNav(active: RootTab, onTab: (RootTab) -> Unit, modifier: Modifier = Modifier) {
    val tabs = remember {
        listOf(
            Triple(RootTab.COURSES, Icons.Rounded.School, "לימודים"),
            Triple(RootTab.SEARCH, Icons.Rounded.Search, "חיפוש"),
            Triple(RootTab.SAVED, Icons.Rounded.BookmarkBorder, "שמורים"),
            Triple(RootTab.PROFILE, Icons.Rounded.Group, "פרופיל")
        )
    }
    val activeIndex = tabs.indexOfFirst { it.first == active }.coerceAtLeast(0)
    val direction = LocalLayoutDirection.current
    val density = LocalDensity.current
    var widthPx by remember { mutableIntStateOf(0) }
    var dragging by remember { mutableStateOf(false) }
    var pointerX by remember { mutableFloatStateOf(0f) }
    var visualIndex by remember { mutableIntStateOf(activeIndex) }
    LaunchedEffect(activeIndex) {
        if (!dragging) visualIndex = activeIndex
    }
    val tabWidthPx = if (widthPx > 0) widthPx.toFloat() / tabs.size else 0f
    val snappedPhysicalIndex = if (direction == LayoutDirection.Rtl) tabs.lastIndex - visualIndex else visualIndex
    val snappedX = snappedPhysicalIndex * tabWidthPx
    val dragX = if (tabWidthPx > 0f) {
        (pointerX - tabWidthPx / 2f).coerceIn(0f, (widthPx - tabWidthPx).coerceAtLeast(0f))
    } else 0f
    val animatedSnappedX by animateFloatAsState(
        targetValue = snappedX,
        animationSpec = spring(stiffness = 680f, dampingRatio = .82f),
        label = "navIndicator"
    )
    val indicatorX = if (dragging) dragX else animatedSnappedX
    val indicatorWidth = with(density) { tabWidthPx.toDp() }
    GlassSurface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 14.dp).navigationBarsPadding(),
        shape = RoundedCornerShape(30.dp)
    ) {
        Box(
            Modifier.fillMaxWidth().padding(6.dp).height(58.dp)
                .onSizeChanged { widthPx = it.width }
                .pointerInput(widthPx, direction) {
                    if (widthPx <= 0) return@pointerInput
                    fun logicalIndexAt(x: Float): Int {
                        val physical = (x / (widthPx.toFloat() / tabs.size)).toInt().coerceIn(0, tabs.lastIndex)
                        return if (direction == LayoutDirection.Rtl) tabs.lastIndex - physical else physical
                    }
                    detectHorizontalDragGestures(
                        onDragStart = { start ->
                            dragging = true
                            pointerX = start.x.coerceIn(0f, widthPx.toFloat())
                        },
                        onHorizontalDrag = { change, _ ->
                            pointerX = change.position.x.coerceIn(0f, widthPx.toFloat())
                            change.consume()
                        },
                        onDragEnd = {
                            val next = logicalIndexAt(pointerX)
                            visualIndex = next
                            dragging = false
                            onTab(tabs[next].first)
                        },
                        onDragCancel = {
                            visualIndex = activeIndex
                            dragging = false
                        }
                    )
                }
        ) {
            if (indicatorWidth > 0.dp) {
                Box(
                    Modifier.width(indicatorWidth).height(58.dp)
                        .graphicsLayer { translationX = indicatorX }
                        .clip(RoundedCornerShape(22.dp))
                        .background(StudyBlue.copy(alpha = .15f))
                )
            }
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceAround) {
                tabs.forEach { item ->
                    val selected = active == item.first
                    val iconScale by animateFloatAsState(
                        if (selected) 1.08f else 1f,
                        spring(stiffness = 760f, dampingRatio = .7f),
                        label = "navIcon"
                    )
                    Column(
                        Modifier.weight(1f).height(58.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .selectable(
                                selected = selected,
                                onClick = {
                                    visualIndex = tabs.indexOf(item)
                                    onTab(item.first)
                                },
                                role = Role.Tab
                            )
                            .padding(horizontal = 2.dp, vertical = 7.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            item.second,
                            item.third,
                            modifier = Modifier.size(23.dp).graphicsLayer { scaleX = iconScale; scaleY = iconScale },
                            tint = if (selected) StudyBlue else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            item.third,
                            color = if (selected) StudyBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LessonScreen(
    state: UiState,
    onBack: () -> Unit,
    onBookmark: () -> Unit,
    onCompleted: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    val lesson = state.lesson
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (state.lessonLoading || lesson == null) {
            LessonLoadingState()
        } else {
            Column(Modifier.fillMaxSize()) {
                GlassSurface(
                    Modifier.statusBarsPadding().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 6.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(26.dp)
                ) {
                    Row(Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        RoundActionButton(Icons.AutoMirrored.Rounded.ArrowBack, "חזרה", onBack, size = 42.dp)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(lesson.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${lesson.courseTitle} · ${lesson.sectionId}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        RoundActionButton(
                            if ("${lesson.courseId}::${lesson.sectionId}" in state.bookmarkedSections) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                            "שמירה",
                            onBookmark,
                            size = 42.dp,
                            active = "${lesson.courseId}::${lesson.sectionId}" in state.bookmarkedSections
                        )
                    }
                }

                Box(Modifier.fillMaxWidth().weight(1f)) {
                    if (lesson.content.isBlank()) EmptyLessonContent()
                    else LessonMathView(lesson.content, Modifier.fillMaxSize())
                }

                GlassSurface(
                    Modifier.padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 10.dp)
                        .navigationBarsPadding().fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Row(Modifier.padding(7.dp), verticalAlignment = Alignment.CenterVertically) {
                        RoundActionButton(
                            Icons.Rounded.KeyboardArrowRight,
                            "השיעור הקודם",
                            onPrevious,
                            size = 44.dp,
                            enabled = lesson.previousSectionId != null
                        )
                        Spacer(Modifier.weight(1f))
                        val completed = "${lesson.courseId}/${lesson.chapterId}/${lesson.sectionId}" in state.completedSections
                        Pressable(
                            onClick = onCompleted,
                            selected = completed,
                            shape = CircleShape,
                            contentPadding = 11.dp
                        ) {
                            Icon(Icons.Rounded.CheckCircle, null, tint = StudyBlue)
                            Spacer(Modifier.width(7.dp))
                            Text(if (completed) "הושלם" else "סיום שיעור", maxLines = 1)
                        }
                        Spacer(Modifier.weight(1f))
                        RoundActionButton(
                            Icons.Rounded.KeyboardArrowLeft,
                            "השיעור הבא",
                            onNext,
                            size = 44.dp,
                            active = lesson.nextSectionId != null,
                            enabled = lesson.nextSectionId != null
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LessonLoadingState() {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(22.dp),
        verticalArrangement = Arrangement.Center
    ) {
        GlassSurface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(30.dp)) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.fillMaxWidth(.54f).height(26.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant))
                Box(Modifier.fillMaxWidth().height(15.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant))
                Box(Modifier.fillMaxWidth(.86f).height(15.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant))
                Spacer(Modifier.height(10.dp))
                CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).size(28.dp), strokeWidth = 3.dp)
            }
        }
    }
}

@Composable
private fun EmptyLessonContent() {
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(StudyBlue.copy(.13f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.AutoAwesome, null, tint = StudyBlue, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text("התוכן עדיין מסתנכרן", style = MaterialTheme.typography.titleLarge)
        Text(
            "התוכן המקומי של השיעור אינו זמין כרגע.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 10.dp)
        )
    }
}

@Composable
fun ChatOverlay(
    state: UiState,
    onOpen: (Boolean) -> Unit,
    onExpanded: (Boolean) -> Unit,
    onInput: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!state.chatOpen) {
        RoundActionButton(
            icon = Icons.Rounded.SmartToy,
            contentDescription = "פתיחת Pythi",
            onClick = { onOpen(true) },
            modifier = modifier,
            active = true,
            size = 58.dp
        )
        return
    }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val dismissKeyboard = remember(keyboard, focusManager) {
        {
            focusManager.clearFocus(force = true)
            keyboard?.hide()
            Unit
        }
    }
    Box(modifier.imePadding().navigationBarsPadding()) {
        Box(
            Modifier.matchParentSize().clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = dismissKeyboard
            )
        )
        AnimatedVisibility(
            visible = state.chatOpen,
            modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 8.dp, vertical = 6.dp),
            enter = fadeIn() + scaleIn(initialScale = .94f) + slideInVertically(initialOffsetY = { it / 4 }),
            exit = fadeOut() + scaleOut(targetScale = .96f) + slideOutVertically(targetOffsetY = { it / 5 })
        ) {
            GlassSurface(
                Modifier.widthIn(max = 720.dp).fillMaxWidth()
                    .then(
                        if (state.chatExpanded) Modifier.fillMaxSize().statusBarsPadding()
                        else Modifier.heightIn(min = 360.dp, max = 560.dp)
                    )
                    .animateContentSize(spring(dampingRatio = .88f, stiffness = 560f)),
                shape = RoundedCornerShape(if (state.chatExpanded) 32.dp else 30.dp)
            ) {
                Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp)) {
                    Box(
                        Modifier.align(Alignment.CenterHorizontally).width(46.dp).height(5.dp)
                            .clip(CircleShape).background(MaterialTheme.colorScheme.outlineVariant)
                            .clickable { onExpanded(!state.chatExpanded) }
                    )
                    Spacer(Modifier.height(5.dp))
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(StudyBlue), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.SmartToy, null, tint = Color.White, modifier = Modifier.size(22.dp))
                            Box(
                                Modifier.align(Alignment.BottomEnd).size(10.dp).clip(CircleShape)
                                    .background(Color(0xFF31C76A))
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Pythi", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (state.chatStreaming) "כותבת תשובה…" else "עוזרת לימודית",
                                color = if (state.chatStreaming) StudyBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                        if (state.chatMessages.isNotEmpty()) {
                            IconButton(onClick = onClear) { Icon(Icons.Rounded.DeleteOutline, "ניקוי השיחה") }
                        }
                        IconButton(onClick = { onExpanded(!state.chatExpanded) }) {
                            Icon(
                                if (state.chatExpanded) Icons.Rounded.ExpandMore else Icons.Rounded.ExpandLess,
                                if (state.chatExpanded) "הקטנת הצ׳אט" else "הרחבת הצ׳אט"
                            )
                        }
                        IconButton(onClick = { dismissKeyboard(); onOpen(false) }) { Icon(Icons.Rounded.Close, "סגירה") }
                    }

                    if (state.chatMessages.isEmpty()) {
                        Column(
                            Modifier.fillMaxWidth().weight(1f)
                                .clickable(
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                    indication = null,
                                    onClick = dismissKeyboard
                                )
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(Modifier.size(62.dp).clip(RoundedCornerShape(20.dp)).background(StudyBlue.copy(.12f)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.SmartToy, null, tint = StudyBlue, modifier = Modifier.size(31.dp))
                            }
                            Spacer(Modifier.height(12.dp))
                            Text("איך אפשר לעזור היום?", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "אפשר לבקש הסבר, תרגול או פתרון מסודר עם נוסחאות.",
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(Modifier.height(12.dp))
                            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                items(listOf("הסבר פשוט", "תרגיל לדוגמה", "סיכום קצר")) { suggestion ->
                                    Pressable(
                                        onClick = { onInput(suggestion); focusRequester.requestFocus(); keyboard?.show() },
                                        shape = CircleShape,
                                        contentPadding = 9.dp
                                    ) { Text(suggestion, style = MaterialTheme.typography.labelMedium) }
                                }
                            }
                        }
                    } else {
                        ChatRichText(
                            messages = state.chatMessages,
                            onTap = dismissKeyboard,
                            modifier = Modifier.fillMaxWidth().weight(1f)
                        )
                    }

                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(
                                1.dp,
                                if (state.chatInput.isNotBlank()) StudyBlue.copy(alpha = .55f) else MaterialTheme.colorScheme.outlineVariant,
                                RoundedCornerShape(28.dp)
                            )
                            .padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        androidx.compose.foundation.text.BasicTextField(
                            value = state.chatInput,
                            onValueChange = onInput,
                            modifier = Modifier.weight(1f).heightIn(min = 42.dp, max = if (state.chatExpanded) 150.dp else 104.dp)
                                .focusRequester(focusRequester),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(StudyBlue),
                            minLines = 1,
                            maxLines = if (state.chatExpanded) 6 else 4,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(
                                onSend = { if (!state.chatStreaming && state.chatInput.isNotBlank()) onSend() }
                            ),
                            decorationBox = { inner ->
                                Box(Modifier.fillMaxWidth().padding(vertical = 9.dp), contentAlignment = Alignment.CenterStart) {
                                    if (state.chatInput.isBlank()) {
                                        Text("שאלו את Pythi…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    inner()
                                }
                            }
                        )
                        Spacer(Modifier.width(7.dp))
                        RoundActionButton(
                            icon = if (state.chatStreaming) Icons.Rounded.Stop else Icons.AutoMirrored.Rounded.Send,
                            contentDescription = if (state.chatStreaming) "עצירה" else "שליחה",
                            onClick = if (state.chatStreaming) onStop else onSend,
                            active = state.chatStreaming || state.chatInput.isNotBlank(),
                            size = 44.dp,
                            enabled = state.chatStreaming || state.chatInput.isNotBlank()
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AuthOverlay(
    visible: Boolean,
    loading: Boolean,
    onDismiss: () -> Unit,
    onLogin: (String, String) -> Unit,
    onRegister: (String, String, String) -> Unit,
    onGoogleLogin: () -> Unit
) {
    AnimatedVisibility(visible, enter = fadeIn() + scaleIn(initialScale = .9f), exit = fadeOut() + scaleOut(targetScale = .9f)) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(.64f))
                .statusBarsPadding().navigationBarsPadding().imePadding().padding(18.dp),
            contentAlignment = Alignment.Center
        ) {
            var register by remember { mutableStateOf(false) }
            var name by remember { mutableStateOf("") }
            var email by remember { mutableStateOf("") }
            var password by remember { mutableStateOf("") }
            var showPassword by remember { mutableStateOf(false) }
            GlassSurface(Modifier.fillMaxWidth().widthIn(max = 620.dp).heightIn(max = 720.dp), shape = RoundedCornerShape(32.dp)) {
                Column(Modifier.padding(22.dp).verticalScroll(rememberScrollState())) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(50.dp).clip(CircleShape).background(StudyBlue), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Lock, null, tint = Color.White)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(if (register) "יצירת חשבון" else "ברוכים השבים", style = MaterialTheme.typography.headlineMedium)
                            Text("ההתקדמות נשמרת ומסתנכרנת עם החשבון", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "סגירה") }
                    }
                    Spacer(Modifier.height(20.dp))
                    if (register) {
                        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("שם") }, shape = RoundedCornerShape(22.dp), singleLine = true)
                        Spacer(Modifier.height(10.dp))
                    }
                    OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text("אימייל") }, shape = RoundedCornerShape(22.dp), singleLine = true)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        password,
                        { password = it },
                        Modifier.fillMaxWidth(),
                        label = { Text("סיסמה") },
                        shape = RoundedCornerShape(22.dp),
                        singleLine = true,
                        visualTransformation = if (showPassword) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, null)
                            }
                        }
                    )
                    Spacer(Modifier.height(16.dp))
                    Pressable(
                        onClick = { if (register) onRegister(name, email, password) else onLogin(email, password) },
                        modifier = Modifier.fillMaxWidth(),
                        selected = true,
                        enabled = !loading
                    ) {
                        if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Text(if (register) "הרשמה" else "התחברות", color = StudyBlue, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(10.dp))
                    Pressable(
                        onClick = onGoogleLogin,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !loading
                    ) {
                        Text("G", color = Color(0xFF4285F4), fontWeight = FontWeight.ExtraBold)
                        Spacer(Modifier.width(10.dp))
                        Text("המשך עם Google", fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = { register = !register }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text(if (register) "כבר יש לי חשבון" else "אין לי חשבון — הרשמה")
                    }
                }
            }
        }
    }
}

@Composable
private fun LegacyAdminScreen(
    state: UiState,
    onClose: () -> Unit,
    onReload: () -> Unit,
    onToggleBlock: (String) -> Unit,
    onAccess: (String, String) -> Unit,
    onTogglePublic: (String) -> Unit,
    onSettings: (Boolean, Boolean, Boolean) -> Unit
) {
    var tab by remember { mutableStateOf("overview") }
    val tabs = listOf("overview" to "סקירה", "users" to "משתמשים", "requests" to "בקשות", "courses" to "קורסים", "settings" to "מערכת")
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundActionButton(Icons.AutoMirrored.Rounded.ArrowBack, "סגירה", onClose, size = 46.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("מרכז ניהול", style = MaterialTheme.typography.headlineMedium)
                Text("שליטה מאובטחת במערכת", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            RoundActionButton(Icons.Rounded.Refresh, "רענון", onReload, size = 46.dp)
        }
        androidx.compose.foundation.lazy.LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(tabs.size) { index ->
                val item = tabs[index]
                Pressable(onClick = { tab = item.first }, selected = tab == item.first, shape = CircleShape, contentPadding = 11.dp) {
                    Text(item.second, color = if (tab == item.first) StudyBlue else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        if (state.adminLoading) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).padding(28.dp))
        when (tab) {
            "overview" -> AdminOverviewContent(state)
            "users" -> AdminUsersContent(state.adminUsers, onToggleBlock)
            "requests" -> AdminRequestsContent(state.accessRequests, onAccess)
            "courses" -> AdminCoursesContent(state, onTogglePublic)
            "settings" -> AdminSettingsContent(state, onSettings)
        }
    }
}

@Composable
private fun AdminOverviewContent(state: UiState) {
    val overview = state.adminOverview
    val metrics = listOf(
        Triple("משתמשים", overview?.totalUsers ?: 0, Icons.Rounded.Group),
        Triple("חסומים", overview?.blockedUsers ?: 0, Icons.Rounded.Lock),
        Triple("בקשות", overview?.pendingRequests ?: 0, Icons.Rounded.Shield),
        Triple("קורסים ציבוריים", overview?.publicCoursesCount ?: 0, Icons.Rounded.Public)
    )
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 18.dp, 16.dp, 60.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(metrics) { metric ->
            GlassSurface(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(46.dp).clip(CircleShape).background(StudyBlue.copy(.14f)), contentAlignment = Alignment.Center) {
                        Icon(metric.third, null, tint = StudyBlue)
                    }
                    Spacer(Modifier.width(14.dp))
                    Text(metric.first, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Text(metric.second.toString(), style = MaterialTheme.typography.headlineMedium)
                }
            }
        }
    }
}

@Composable
private fun AdminUsersContent(users: List<AdminUser>, onToggleBlock: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val filtered = users.filter { query.isBlank() || it.email.contains(query, true) || it.displayName.contains(query, true) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(top = 14.dp), placeholder = { Text("חיפוש משתמש") }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, shape = RoundedCornerShape(22.dp))
        LazyColumn(contentPadding = PaddingValues(vertical = 12.dp, horizontal = 0.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filtered, key = { it.id }) { user ->
                GlassSurface(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(StudyBlue.copy(.15f)), contentAlignment = Alignment.Center) {
                            Text(user.displayName.ifBlank { user.email }.take(1).uppercase(), fontWeight = FontWeight.Bold, color = StudyBlue)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(user.displayName.ifBlank { user.email.substringBefore('@') }, fontWeight = FontWeight.Bold)
                            Text(user.email, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                        }
                        RoundActionButton(if (user.isBlocked) Icons.Rounded.LockOpen else Icons.Rounded.Lock, "חסימה", { onToggleBlock(user.id) }, size = 42.dp, active = user.isBlocked)
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminRequestsContent(requests: List<AccessRequest>, onAccess: (String, String) -> Unit) {
    if (requests.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("אין בקשות ממתינות", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(requests, key = { it.id }) { request ->
            GlassSurface(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(request.userDisplayName.ifBlank { request.userEmail }, fontWeight = FontWeight.Bold)
                    Text(request.userEmail, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(request.courseTitle.ifBlank { request.courseId }, color = StudyBlue, modifier = Modifier.padding(vertical = 8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pressable(onClick = { onAccess(request.id, "approve") }, selected = true, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.Check, null, tint = StudyBlue)
                            Text("אישור", color = StudyBlue)
                        }
                        Pressable(onClick = { onAccess(request.id, "reject") }, modifier = Modifier.weight(1f)) { Text("דחייה") }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminCoursesContent(state: UiState, onToggle: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(state.courses, key = { it.id }) { course ->
            Pressable(onClick = { onToggle(course.id) }, modifier = Modifier.fillMaxWidth(), selected = course.id in state.publicCourseIds) {
                Icon(if (course.id in state.publicCourseIds) Icons.Rounded.Public else Icons.Rounded.Lock, null, tint = if (course.id in state.publicCourseIds) StudyBlue else MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
                Text(course.title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                // The whole row is the control. A nested click handler here can
                // dispatch the toggle twice on some Compose gesture paths.
                Switch(checked = course.id in state.publicCourseIds, onCheckedChange = null)
            }
        }
    }
}

@Composable
private fun AdminSettingsContent(state: UiState, onSave: (Boolean, Boolean, Boolean) -> Unit) {
    val overview = state.adminOverview ?: return
    var password by remember(overview) { mutableStateOf(overview.requireCoursePassword) }
    var gemini by remember(overview) { mutableStateOf(overview.geminiServerKeysEnabled) }
    var explain by remember(overview) { mutableStateOf(overview.askPopoverShortExplainEnabled) }
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        GlassSurface(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp)) {
                AdminToggle("דרישת הרשאת קורס", password) { password = it }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                AdminToggle("מפתחות Gemini של השרת", gemini) { gemini = it }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                AdminToggle("הסבר קצר בבחירה", explain) { explain = it }
            }
        }
        Pressable(onClick = { onSave(password, gemini, explain) }, modifier = Modifier.fillMaxWidth(), selected = true) {
            Text("שמירת הגדרות", color = StudyBlue, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AdminToggle(title: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
fun PdfOverlay(uri: Uri, onClose: () -> Unit) {
    val context = LocalContext.current
    var page by remember(uri) { mutableIntStateOf(0) }
    val pageCount by produceState(initialValue = 0, uri) {
        value = withContext(Dispatchers.IO) {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                PdfRenderer(descriptor).use { it.pageCount }
            } ?: 0
        }
    }
    val bitmap by produceState<Bitmap?>(initialValue = null, uri, page) {
        value = withContext(Dispatchers.IO) {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    if (renderer.pageCount == 0) return@use null
                    renderer.openPage(page.coerceIn(0, renderer.pageCount - 1)).use { pdfPage ->
                        val width = 1440
                        val height = (width * pdfPage.height.toFloat() / pdfPage.width).toInt().coerceAtLeast(1)
                        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { target ->
                            target.eraseColor(android.graphics.Color.WHITE)
                            pdfPage.render(target, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        }
                    }
                }
            }
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        bitmap?.let {
            Image(
                it.asImageBitmap(),
                null,
                Modifier.fillMaxSize().padding(top = 78.dp, bottom = 90.dp),
                contentScale = ContentScale.Fit
            )
        } ?: CircularProgressIndicator(Modifier.align(Alignment.Center))
        GlassSurface(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(10.dp).fillMaxWidth()) {
            Row(Modifier.padding(7.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundActionButton(Icons.Rounded.Close, "סגירה", onClose, size = 42.dp)
                Spacer(Modifier.width(10.dp))
                Text("קורא PDF", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                Text("${(page + 1).coerceAtMost(pageCount)} / $pageCount", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        GlassSurface(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(10.dp)) {
            Row(Modifier.padding(7.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundActionButton(Icons.Rounded.KeyboardArrowRight, "עמוד קודם", { if (page > 0) page-- }, size = 44.dp)
                Spacer(Modifier.width(24.dp))
                Text("עמוד ${page + 1}", fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(24.dp))
                RoundActionButton(Icons.Rounded.KeyboardArrowLeft, "עמוד הבא", { if (page + 1 < pageCount) page++ }, size = 44.dp, active = page + 1 < pageCount)
            }
        }
    }
}
