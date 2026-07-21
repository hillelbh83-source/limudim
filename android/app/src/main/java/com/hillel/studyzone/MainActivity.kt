package com.hillel.studyzone

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.unit.LayoutDirection.Rtl
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hillel.studyzone.model.RootTab
import com.hillel.studyzone.model.ThemeMode
import com.hillel.studyzone.ui.screens.AdminScreen
import com.hillel.studyzone.ui.screens.AuthOverlay
import com.hillel.studyzone.ui.screens.BottomGlassNav
import com.hillel.studyzone.ui.screens.ChatOverlay
import com.hillel.studyzone.ui.screens.CourseDetailScreen
import com.hillel.studyzone.ui.screens.CoursesScreen
import com.hillel.studyzone.ui.screens.IntroSplash
import com.hillel.studyzone.ui.screens.LessonScreen
import com.hillel.studyzone.ui.screens.PdfOverlay
import com.hillel.studyzone.ui.screens.ProfileScreen
import com.hillel.studyzone.ui.screens.SavedScreen
import com.hillel.studyzone.ui.screens.SearchScreen
import com.hillel.studyzone.ui.screens.ToolsScreen
import com.hillel.studyzone.ui.components.LocalHapticsEnabled
import com.hillel.studyzone.ui.theme.StudyZoneTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()
    private var activeIntent by mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        activeIntent = intent
        enableEdgeToEdge()
        // The system splash only bridges process startup to the first Compose frame. It never
        // waits for the server, and exits into the in-app reveal with a short GPU-only animation.
        splash.setOnExitAnimationListener { provider ->
            provider.view.animate()
                .alpha(0f)
                .scaleX(1.035f)
                .scaleY(1.035f)
                .setDuration(170L)
                .withEndAction { provider.remove() }
                .start()
        }
        setContent {
            // Theme settings are isolated from high-frequency UI state (notably chat streaming),
            // so the entire theme tree is not invalidated for every server chunk.
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            StudyZoneTheme(settings.themeMode, settings.fontScale) {
                CompositionLocalProvider(
                    androidx.compose.ui.platform.LocalLayoutDirection provides Rtl,
                    LocalHapticsEnabled provides settings.haptics
                ) {
                    StudyZoneRoot(viewModel, activeIntent)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        activeIntent = intent
    }
}

@Composable
private fun StudyZoneRoot(viewModel: AppViewModel, launchIntent: Intent?) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var introVisible by rememberSaveable { mutableStateOf(true) }
    var handledDeepLink by rememberSaveable { mutableStateOf<String?>(null) }
    var pdfUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            pdfUri = uri
        }
    }

    LaunchedEffect(Unit) {
        // Let real content draw underneath first, then play a brief branded hand-off. Network and
        // DataStore work continue independently and can never prolong this animation.
        withFrameNanos { }
        delay(if (viewModel.settings.value.reduceMotion) 80 else 360)
        introVisible = false
    }

    LaunchedEffect(state.toast) {
        if (state.toast != null) {
            delay(2800)
            viewModel.consumeToast()
        }
    }

    LaunchedEffect(state.courses, launchIntent?.dataString) {
        val deepLink = launchIntent?.dataString ?: return@LaunchedEffect
        if (handledDeepLink == deepLink) return@LaunchedEffect
        val uri = launchIntent?.data ?: return@LaunchedEffect
        if (state.courses.isEmpty()) return@LaunchedEffect
        val courseId = uri.host ?: uri.pathSegments.firstOrNull()
        val sectionId = if (uri.host != null) uri.pathSegments.firstOrNull() else uri.pathSegments.getOrNull(1)
        if (state.courses.none { it.id == courseId }) return@LaunchedEffect
        handledDeepLink = deepLink
        viewModel.handleDeepLink(courseId, sectionId)
    }

    DisposableEffect(state.settings.keepScreenOn) {
        val activity = context as? ComponentActivity
        if (state.settings.keepScreenOn) activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            if (state.settings.keepScreenOn) {
                activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    val selectedCourse = state.selectedCourse

    val hasBackDestination = introVisible || pdfUri != null || state.adminOpen || state.authOpen ||
        state.chatOpen || state.lesson != null || state.lessonLoading || state.selectedCourse != null
    BackHandler(enabled = hasBackDestination) {
        when {
            introVisible -> introVisible = false
            pdfUri != null -> pdfUri = null
            state.adminOpen -> viewModel.closeAdmin()
            state.authOpen -> viewModel.setAuthOpen(false)
            state.chatExpanded -> viewModel.setChatExpanded(false)
            state.chatOpen -> viewModel.setChatOpen(false)
            state.lesson != null || state.lessonLoading -> viewModel.closeLesson()
            state.selectedCourse != null -> viewModel.closeCourse()
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when {
            state.error != null && state.courses.isEmpty() -> ErrorState(state.error.orEmpty(), viewModel::bootstrap)
            state.lesson != null || state.lessonLoading -> LessonScreen(
                state = state,
                onBack = viewModel::closeLesson,
                onBookmark = viewModel::toggleBookmark,
                onCompleted = viewModel::toggleCompleted,
                onPrevious = { viewModel.openAdjacent(state.lesson?.previousSectionId) },
                onNext = { viewModel.openAdjacent(state.lesson?.nextSectionId) }
            )
            selectedCourse != null -> CourseDetailScreen(
                state = state,
                onBack = viewModel::closeCourse,
                onLesson = { viewModel.openLesson(selectedCourse.id, it) },
                onRequestAccess = { viewModel.requestCourseAccess(selectedCourse.id) }
            )
            else -> AnimatedContent(targetState = state.rootTab, label = "rootTab") { tab ->
                when (tab) {
                    RootTab.COURSES -> CoursesScreen(
                        state,
                        onCourse = viewModel::openCourse,
                        onProfile = { viewModel.selectTab(RootTab.PROFILE) },
                        onThemeToggle = {
                            viewModel.setTheme(if (state.settings.themeMode == ThemeMode.DARK) ThemeMode.LIGHT else ThemeMode.DARK)
                        }
                    )
                    RootTab.SEARCH -> SearchScreen(state, viewModel::setSearchQuery, viewModel::openLesson)
                    RootTab.SAVED -> SavedScreen(state, viewModel::openLesson)
                    RootTab.TOOLS -> ToolsScreen { pdfPicker.launch(arrayOf("application/pdf")) }
                    RootTab.PROFILE -> ProfileScreen(
                        state,
                        onAuth = { viewModel.setAuthOpen(true) },
                        onLogout = viewModel::logout,
                        onAdmin = viewModel::openAdmin,
                        onTheme = viewModel::setTheme,
                        onReduceMotion = viewModel::setReduceMotion,
                        onHaptics = viewModel::setHaptics,
                        onFontScale = viewModel::setFontScale,
                        onKeepScreenOn = viewModel::setKeepScreenOn
                    )
                }
            }
        }

        if (state.lesson == null && !state.lessonLoading && !state.adminOpen && pdfUri == null && !state.chatOpen) {
            BottomGlassNav(
                active = state.rootTab,
                onTab = viewModel::selectTab,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)
            )
        }

        if (!state.adminOpen && pdfUri == null && !state.authOpen) {
            ChatOverlay(
                state = state,
                onOpen = viewModel::setChatOpen,
                onExpanded = viewModel::setChatExpanded,
                onInput = viewModel::setChatInput,
                onSend = viewModel::sendChat,
                onStop = viewModel::stopChat,
                onClear = viewModel::clearChat,
                modifier = if (state.chatExpanded) {
                    Modifier.align(Alignment.Center).padding(8.dp)
                } else if (state.chatOpen) {
                    Modifier.align(Alignment.BottomCenter).padding(horizontal = 10.dp)
                } else {
                    Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 18.dp, bottom = if (state.lesson == null) 100.dp else 94.dp)
                }
            )
        }

        AuthOverlay(
            visible = state.authOpen,
            loading = state.authLoading,
            onDismiss = { viewModel.setAuthOpen(false) },
            onLogin = viewModel::login,
            onRegister = viewModel::register
        )

        AnimatedVisibility(state.adminOpen, enter = fadeIn() + scaleIn(initialScale = .96f), exit = fadeOut() + scaleOut(targetScale = .96f)) {
            AdminScreen(
                state,
                onClose = viewModel::closeAdmin,
                onReload = viewModel::loadAdmin,
                onToggleBlock = viewModel::toggleUserBlock,
                onAccess = viewModel::handleAccessRequest,
                onTogglePublic = viewModel::togglePublicCourse,
                onSettings = viewModel::updateAdminSettings
            )
        }

        pdfUri?.let { PdfOverlay(it) { pdfUri = null } }

        AnimatedVisibility(
            visible = state.toast != null,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 72.dp, start = 18.dp, end = 18.dp),
            enter = fadeIn() + scaleIn(initialScale = .9f),
            exit = fadeOut() + scaleOut(targetScale = .9f)
        ) {
            Snackbar(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface) {
                Text(state.toast.orEmpty())
            }
        }

        IntroSplash(introVisible)
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        com.hillel.studyzone.ui.components.GlassSurface(Modifier.padding(22.dp)) {
            androidx.compose.foundation.layout.Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("לא הצלחנו לטעון את StudyZone", style = MaterialTheme.typography.titleLarge)
                Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 12.dp))
                com.hillel.studyzone.ui.components.Pressable(onClick = onRetry, selected = true) { Text("ניסיון נוסף", color = com.hillel.studyzone.ui.theme.StudyBlue) }
            }
        }
    }
}
