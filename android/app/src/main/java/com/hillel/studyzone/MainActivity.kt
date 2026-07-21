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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.CompositionLocalProvider
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
import com.hillel.studyzone.ui.theme.StudyZoneTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setKeepOnScreenCondition { viewModel.state.value.isBootstrapping }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            StudyZoneTheme(state.settings.themeMode, state.settings.fontScale) {
                CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides Rtl) {
                    StudyZoneRoot(viewModel, intent)
                }
            }
        }
    }
}

@Composable
private fun StudyZoneRoot(viewModel: AppViewModel, launchIntent: Intent?) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var introVisible by remember { mutableStateOf(true) }
    var pdfUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            pdfUri = uri
        }
    }

    LaunchedEffect(state.isBootstrapping) {
        if (!state.isBootstrapping) {
            delay(if (state.settings.reduceMotion) 120 else 720)
            introVisible = false
        }
    }

    LaunchedEffect(state.toast) {
        if (state.toast != null) {
            delay(2800)
            viewModel.consumeToast()
        }
    }

    LaunchedEffect(state.courses, launchIntent?.dataString) {
        val uri = launchIntent?.data ?: return@LaunchedEffect
        if (state.courses.isEmpty()) return@LaunchedEffect
        val courseId = uri.host ?: uri.pathSegments.firstOrNull()
        val sectionId = if (uri.host != null) uri.pathSegments.firstOrNull() else uri.pathSegments.getOrNull(1)
        viewModel.handleDeepLink(courseId, sectionId)
    }

    DisposableEffect(state.settings.keepScreenOn) {
        val activity = context as? ComponentActivity
        if (state.settings.keepScreenOn) activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { }
    }

    BackHandler(enabled = state.adminOpen) { viewModel.closeAdmin() }
    BackHandler(enabled = pdfUri != null) { pdfUri = null }
    BackHandler(enabled = state.chatExpanded) { viewModel.setChatExpanded(false) }
    BackHandler(enabled = state.chatOpen && !state.chatExpanded) { viewModel.setChatOpen(false) }
    BackHandler(enabled = state.authOpen) { viewModel.setAuthOpen(false) }
    BackHandler(enabled = state.lesson != null) { viewModel.closeLesson() }
    BackHandler(enabled = state.selectedCourse != null && state.lesson == null) { viewModel.closeCourse() }

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
            state.selectedCourse != null -> CourseDetailScreen(
                state = state,
                onBack = viewModel::closeCourse,
                onLesson = { viewModel.openLesson(state.selectedCourse.id, it) },
                onRequestAccess = { viewModel.requestCourseAccess(state.selectedCourse.id) }
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

        if (state.lesson == null && !state.lessonLoading && !state.adminOpen && pdfUri == null) {
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
                    Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 10.dp, end = 10.dp, bottom = if (state.lesson == null) 96.dp else 82.dp)
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
