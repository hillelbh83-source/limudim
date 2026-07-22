package com.hillel.studyzone.ui.screens

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.view.MotionEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowUpward
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
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Timer
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
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
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
import kotlinx.coroutines.launch
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
fun ThemeRevealOverlay(background: Color, requestedOrigin: Offset) {
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
            val origin = Offset(
                requestedOrigin.x.coerceIn(0f, size.width),
                requestedOrigin.y.coerceIn(0f, size.height)
            )
            val maxRadius = maxOf(
                hypot(origin.x, origin.y),
                hypot(size.width - origin.x, origin.y),
                hypot(origin.x, size.height - origin.y),
                hypot(size.width - origin.x, size.height - origin.y)
            )
            val radius = maxRadius * progress.value
            drawRect(overlayColor)
            drawCircle(Color.Transparent, radius = radius, center = origin, blendMode = BlendMode.Clear)
        }
    }
}

@Composable
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
fun BottomGlassNav(active: RootTab, onTab: (RootTab) -> Unit, modifier: Modifier = Modifier) {
    val tabs = remember {
        listOf(
            Triple(RootTab.COURSES, Icons.Rounded.School, "לימודים"),
            Triple(RootTab.SEARCH, Icons.Rounded.Search, "חיפוש"),
            Triple(RootTab.PROFILE, Icons.Rounded.Group, "פרופיל"),
            Triple(RootTab.SETTINGS, Icons.Rounded.Settings, "הגדרות")
        )
    }
    val activeIndex = tabs.indexOfFirst { it.first == active }.coerceAtLeast(0)
    val direction = LocalLayoutDirection.current
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
    GlassSurface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 14.dp).navigationBarsPadding(),
        shape = RoundedCornerShape(30.dp)
    ) {
        Box(
            Modifier.fillMaxWidth().padding(6.dp).height(58.dp)
                .onSizeChanged { widthPx = it.width }
                .pointerInteropFilter { event ->
                    if (widthPx <= 0) return@pointerInteropFilter false
                    fun logicalIndexAt(x: Float): Int {
                        val physical = (x / (widthPx.toFloat() / tabs.size)).toInt().coerceIn(0, tabs.lastIndex)
                        return if (direction == LayoutDirection.Rtl) tabs.lastIndex - physical else physical
                    }
                    val x = event.x.coerceIn(0f, widthPx.toFloat())
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            pointerX = x
                            visualIndex = logicalIndexAt(x)
                            dragging = true
                            true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            pointerX = x
                            visualIndex = logicalIndexAt(x)
                            true
                        }
                        MotionEvent.ACTION_UP -> {
                            pointerX = x
                            val next = logicalIndexAt(x)
                            visualIndex = next
                            dragging = false
                            onTab(tabs[next].first)
                            true
                        }
                        MotionEvent.ACTION_CANCEL -> {
                            visualIndex = activeIndex
                            dragging = false
                            true
                        }
                        else -> true
                    }
                }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                if (tabWidthPx > 0f) {
                    drawRoundRect(
                        color = StudyBlue.copy(alpha = .15f),
                        topLeft = Offset(indicatorX, 0f),
                        size = androidx.compose.ui.geometry.Size(tabWidthPx, size.height),
                        cornerRadius = CornerRadius(22.dp.toPx(), 22.dp.toPx())
                    )
                }
            }
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceAround) {
                tabs.forEach { item ->
                    val selected = visualIndex == tabs.indexOf(item)
                    val iconScale by animateFloatAsState(
                        if (selected) 1.08f else 1f,
                        spring(stiffness = 760f, dampingRatio = .7f),
                        label = "navIcon"
                    )
                    Column(
                        Modifier.weight(1f).height(58.dp)
                            .clip(RoundedCornerShape(22.dp))
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
    onNext: () -> Unit,
    onAskSelection: (String) -> Unit
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
                    else LessonMathView(
                        content = lesson.content,
                        selectionEnabled = state.settings.enableAskPopover,
                        clearSelectionAfterAction = state.settings.clearSelectionAfterPopover,
                        selectionHighlight = state.settings.selectionHighlight,
                        onAskSelection = onAskSelection,
                        modifier = Modifier.fillMaxSize()
                    )
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
    onAttach: () -> Unit,
    onRemoveAttachment: (Long) -> Unit,
    onVoice: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
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
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    val keyboardVisible = imeBottom > 0
    val targetSheetFraction = when {
            state.chatExpanded -> .96f
            keyboardVisible -> .74f
            else -> .64f
        }
    val sheetFraction = remember { Animatable(targetSheetFraction) }
    val dragScope = rememberCoroutineScope()
    var dragDistance by remember { mutableFloatStateOf(0f) }
    var dragStartFraction by remember { mutableFloatStateOf(targetSheetFraction) }
    LaunchedEffect(targetSheetFraction) {
        sheetFraction.animateTo(
            targetSheetFraction,
            tween(durationMillis = if (state.settings.reduceMotion) 0 else 230, easing = FastOutSlowInEasing)
        )
    }
    Box(modifier.fillMaxSize()) {
      AnimatedVisibility(
        visible = !state.chatOpen,
        modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding()
            .padding(end = 18.dp, bottom = if (state.lesson == null) 100.dp else 94.dp),
        enter = fadeIn() + scaleIn(initialScale = .82f),
        exit = fadeOut() + scaleOut(targetScale = .82f)
      ) {
        RoundActionButton(
            icon = Icons.Rounded.SmartToy,
            contentDescription = "פתיחת Pythi",
            onClick = { onOpen(true) },
            active = true,
            size = 58.dp
        )
      }
      AnimatedVisibility(
        visible = state.chatOpen,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 8 }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 8 })
    ) {
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
          val availableHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
          Box(
              Modifier.fillMaxSize().clickable(
                  interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                  indication = null
              ) { dismissKeyboard(); onOpen(false) }
          )
          Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(sheetFraction.value)
                .padding(
                    bottom = if (!state.chatExpanded && !keyboardVisible && state.lesson == null && state.selectedCourse == null) 92.dp else 0.dp
                )
                .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                .background(MaterialTheme.colorScheme.background)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .65f), RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                .navigationBarsPadding()
                .imePadding()
        ) {
            Box(
                Modifier.fillMaxWidth().height(22.dp).pointerInput(state.chatExpanded) {
                    detectVerticalDragGestures(
                        onDragStart = {
                            dragDistance = 0f
                            dragStartFraction = sheetFraction.value
                            dragScope.launch { sheetFraction.stop() }
                        },
                        onVerticalDrag = { _, amount ->
                            dragDistance += amount
                            val next = (dragStartFraction - dragDistance / availableHeightPx).coerceIn(.46f, .98f)
                            dragScope.launch { sheetFraction.snapTo(next) }
                        },
                        onDragEnd = {
                            val expand = sheetFraction.value >= .80f
                            onExpanded(expand)
                            val destination = if (expand) .96f else if (keyboardVisible) .74f else .64f
                            dragScope.launch {
                                sheetFraction.animateTo(destination, tween(220, easing = FastOutSlowInEasing))
                            }
                            dragDistance = 0f
                        },
                        onDragCancel = { dragDistance = 0f }
                    )
                },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier.width(42.dp).height(5.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .45f))
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(38.dp).clip(RoundedCornerShape(13.dp)).background(StudyBlue), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(21.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("פיתי", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (state.chatStreaming) "כותבת תשובה…" else "העוזרת הלימודית שלך",
                        color = if (state.chatStreaming) StudyBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                if (state.chatMessages.isNotEmpty()) {
                    IconButton(onClick = onClear) { Icon(Icons.Rounded.DeleteOutline, "ניקוי השיחה") }
                }
                IconButton(onClick = { dismissKeyboard(); onOpen(false) }) { Icon(Icons.Rounded.Close, "סגירה") }
            }

            state.chatTimerRemainingSeconds?.let { remaining ->
                val minutes = remaining / 60
                val seconds = remaining % 60
                GlassSurface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 3.dp),
                    shape = RoundedCornerShape(18.dp),
                    selected = true
                ) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Timer, null, tint = StudyBlue, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(9.dp))
                        Text(state.chatTimerLabel, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        Text("%02d:%02d".format(minutes, seconds), color = StudyBlue, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }

            if (state.chatMessages.isEmpty()) {
                Column(
                    Modifier.fillMaxWidth().weight(1f)
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null,
                            onClick = dismissKeyboard
                        )
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        Modifier.size(72.dp).clip(RoundedCornerShape(24.dp))
                            .background(Brush.linearGradient(listOf(StudyBlue, Color(0xFF6846E8)))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(34.dp))
                    }
                    Spacer(Modifier.height(18.dp))
                    Text("איך אפשר לעזור לך היום?", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                    Text(
                        "אפשר לבקש הסבר, תרגול, בוחן, כרטיסיות או פתרון עם נוסחאות.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            } else {
                ChatRichText(
                    messages = state.chatMessages,
                    onTap = dismissKeyboard,
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
            }

            if (state.chatSuggestions.isNotEmpty() && state.chatInput.isBlank() && state.chatAttachments.isEmpty() && !state.chatStreaming) {
                androidx.compose.foundation.lazy.LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 7.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    items(state.chatSuggestions) { suggestion ->
                        Pressable(
                            onClick = { onInput(suggestion); onSend() },
                            shape = CircleShape,
                            contentPadding = 9.dp
                        ) { Text(suggestion, color = StudyBlue, style = MaterialTheme.typography.labelMedium) }
                    }
                }
            }

            if (state.chatAttachments.isNotEmpty()) {
                androidx.compose.foundation.lazy.LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 7.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    items(state.chatAttachments, key = { it.id }) { attachment ->
                        Pressable(
                            onClick = { onRemoveAttachment(attachment.id) },
                            shape = CircleShape,
                            contentPadding = 9.dp
                        ) {
                            Text("📎 ${attachment.name}", maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 180.dp))
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.Rounded.Close, "הסרת קובץ", modifier = Modifier.size(15.dp))
                        }
                    }
                }
            }

            PythiComposer(
                input = state.chatInput,
                streaming = state.chatStreaming,
                hasAttachments = state.chatAttachments.isNotEmpty(),
                focusRequester = focusRequester,
                onInput = onInput,
                onAttach = onAttach,
                onVoice = onVoice,
                onSend = onSend,
                onStop = onStop,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
            )
          }
        }
      }
    }
}

@Composable
private fun PythiComposer(
    input: String,
    streaming: Boolean,
    hasAttachments: Boolean,
    focusRequester: FocusRequester,
    onInput: (String) -> Unit,
    onAttach: () -> Unit,
    onVoice: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val expanded = input.isNotBlank() || hasAttachments
    val shape = RoundedCornerShape(30.dp)
    val composerColor = MaterialTheme.colorScheme.surfaceVariant
    val contentColor = MaterialTheme.colorScheme.onSurface
    val placeholderColor = MaterialTheme.colorScheme.onSurfaceVariant
    val rtlAnchor = "\u200F"
    var fieldValue by remember { mutableStateOf(TextFieldValue(input, selection = TextRange(input.length))) }
    LaunchedEffect(input) {
        if (input != fieldValue.text) {
            fieldValue = TextFieldValue(input, selection = TextRange(input.length))
        }
    }

    val field: @Composable (Modifier) -> Unit = { fieldModifier ->
        val displayedValue = if (fieldValue.text.isEmpty()) {
            TextFieldValue(rtlAnchor, selection = TextRange(1))
        } else fieldValue
        androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            androidx.compose.foundation.text.BasicTextField(
                value = displayedValue,
                onValueChange = { updated ->
                    val anchored = updated.text.startsWith(rtlAnchor)
                    val cleanText = updated.text.replace(rtlAnchor, "")
                    val selectionShift = if (anchored) 1 else 0
                    val start = (updated.selection.start - selectionShift).coerceIn(0, cleanText.length)
                    val end = (updated.selection.end - selectionShift).coerceIn(0, cleanText.length)
                    fieldValue = TextFieldValue(cleanText, selection = TextRange(start, end))
                    if (cleanText != input) onInput(cleanText)
                },
                modifier = fieldModifier.fillMaxWidth().focusRequester(focusRequester),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = contentColor,
                    textAlign = TextAlign.Right,
                    textDirection = TextDirection.Rtl
                ),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(StudyBlue),
                minLines = 1,
                maxLines = 5,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (!streaming && (input.isNotBlank() || hasAttachments)) onSend() }),
                decorationBox = { inner ->
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        if (input.isBlank()) {
                            Text(
                                "שאלו את פיתי",
                                modifier = Modifier.fillMaxWidth(),
                                color = placeholderColor,
                                textAlign = TextAlign.Right
                            )
                        }
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) { inner() }
                    }
                }
            )
        }
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(
            modifier
                .clip(shape)
                .background(composerColor)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .7f), shape)
                .padding(horizontal = 8.dp, vertical = 7.dp)
        ) {
            // Keep the same BasicTextField instance in the composition while the composer grows.
            // Recreating it after the first character used to drop focus and dismiss typing.
            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!expanded) {
                    ComposerIcon(Icons.Rounded.Mic, "הכתבה קולית", onVoice, contentColor)
                }
                field(
                    Modifier.weight(1f)
                        .heightIn(min = if (expanded) 48.dp else 44.dp, max = 122.dp)
                        .padding(horizontal = if (expanded) 8.dp else 6.dp, vertical = if (expanded) 6.dp else 0.dp)
                )
                if (!expanded) {
                    ComposerIcon(Icons.Rounded.Add, "צירוף קובץ", onAttach, contentColor)
                }
            }
            if (expanded) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    ComposerIcon(
                        if (streaming) Icons.Rounded.Stop else Icons.Rounded.ArrowUpward,
                        if (streaming) "עצירת התשובה" else "שליחה",
                        if (streaming) onStop else onSend,
                        Color.White,
                        background = if (streaming) MaterialTheme.colorScheme.error else StudyBlue
                    )
                    Spacer(Modifier.weight(1f))
                    ComposerIcon(Icons.Rounded.Add, "צירוף קובץ", onAttach, contentColor)
                }
            }
        }
    }
}

@Composable
private fun ComposerIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    tint: Color,
    background: Color = Color.Transparent
) {
    IconButton(onClick = onClick, modifier = Modifier.size(42.dp)) {
        Box(
            Modifier.size(if (background == Color.Transparent) 38.dp else 35.dp)
                .clip(CircleShape)
                .background(background),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, description, tint = tint, modifier = Modifier.size(if (background == Color.Transparent) 21.dp else 19.dp))
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
