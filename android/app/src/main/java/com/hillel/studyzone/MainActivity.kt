package com.hillel.studyzone

import android.content.Intent
import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.unit.LayoutDirection.Rtl
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.hillel.studyzone.model.RootTab
import com.hillel.studyzone.model.ThemeMode
import com.hillel.studyzone.ui.screens.AdminScreen
import com.hillel.studyzone.ui.screens.AuthOverlay
import com.hillel.studyzone.ui.screens.BottomGlassNav
import com.hillel.studyzone.ui.screens.ChatOverlay
import com.hillel.studyzone.ui.screens.CourseDetailScreen
import com.hillel.studyzone.ui.screens.CoursesScreen
import com.hillel.studyzone.ui.screens.IntroSplash
import com.hillel.studyzone.ui.screens.ThemeRevealOverlay
import com.hillel.studyzone.ui.screens.LessonScreen
import com.hillel.studyzone.ui.screens.ProfileScreen
import com.hillel.studyzone.ui.screens.SavedScreen
import com.hillel.studyzone.ui.screens.SearchScreen
import com.hillel.studyzone.ui.screens.SettingsScreen
import com.hillel.studyzone.ui.components.LocalHapticsEnabled
import com.hillel.studyzone.ui.theme.StudyZoneTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()
    private var activeIntent by mutableStateOf<Intent?>(null)
    private val chatFilesLauncher = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        viewModel.addChatAttachments(uris)
    }
    private val profilePhotoLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(viewModel::updateProfilePhoto)
    }
    private val speechLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val text = if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
        } else ""
        if (text.isNotBlank()) {
            viewModel.setChatInput(text)
        }
    }
    private val legacyGoogleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        runCatching {
            GoogleSignIn.getSignedInAccountFromIntent(result.data)
                .getResult(ApiException::class.java)
                .idToken
                .orEmpty()
                .ifBlank { error("Google לא החזירה אסימון התחברות") }
        }.onSuccess(viewModel::loginWithGoogle)
            .onFailure { error ->
                viewModel.showMessage(
                    if (error is ApiException && error.statusCode == 10) {
                        "Google OAuth חסום: יש לרשום com.hillel.studyzone עם חתימת SHA-1 של ה-APK ב-Google Auth Platform"
                    } else {
                        "ההתחברות עם Google נכשלה: ${error.message.orEmpty().ifBlank { "בדקו את הגדרת OAuth של האפליקציה" }}"
                    }
                )
            }
    }

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
            // A single collected snapshot drives both the theme and the screen. Keeping these in
            // the same composition frame prevents the icon from changing before the palette.
            val state by viewModel.state.collectAsStateWithLifecycle()
            StudyZoneTheme(
                state.settings.themeMode,
                state.settings.fontScale,
                state.settings.lineSpacing,
                state.settings.activeThemeId
            ) {
                CompositionLocalProvider(
                    androidx.compose.ui.platform.LocalLayoutDirection provides Rtl,
                    LocalHapticsEnabled provides state.settings.haptics
                ) {
                    StudyZoneRoot(viewModel, state, activeIntent)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        activeIntent = intent
    }

    fun launchGoogleSignIn() {
        lifecycleScope.launch {
            runCatching {
                // Request all accounts explicitly. The dedicated button option is reported as a
                // cancellation on some Play Services/device combinations before its UI opens.
                val option = GetGoogleIdOption.Builder()
                    .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
                    .setFilterByAuthorizedAccounts(false)
                    .setAutoSelectEnabled(false)
                    .build()
                val credential = CredentialManager.create(this@MainActivity).getCredential(
                    context = this@MainActivity,
                    request = GetCredentialRequest.Builder().addCredentialOption(option).build()
                ).credential
                if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    error("Google לא החזירה פרטי התחברות תקינים")
                }
                GoogleIdTokenCredential.createFrom(credential.data).idToken
            }.onSuccess(viewModel::loginWithGoogle)
                .onFailure { error ->
                    if (error is GetCredentialCancellationException || error is NoCredentialException) {
                        launchLegacyGoogleSignIn()
                        return@onFailure
                    }
                    viewModel.showMessage(
                        when {
                            error.javaClass.simpleName.contains("Configuration", ignoreCase = true) ->
                                "Google עדיין לא מזהה את חתימת האפליקציה. יש לעדכן את SHA-1 ב-Google Auth Platform"
                            else -> "ההתחברות עם Google נכשלה: ${error.message.orEmpty().ifBlank { "שגיאה לא צפויה" }}"
                        }
                    )
                }
        }
    }

    private fun launchLegacyGoogleSignIn() {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestIdToken(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .build()
        legacyGoogleLauncher.launch(GoogleSignIn.getClient(this, options).signInIntent)
    }

    fun launchChatAttachmentPicker() {
        chatFilesLauncher.launch(arrayOf("image/*", "application/pdf", "text/plain"))
    }

    fun launchProfilePhotoPicker() {
        profilePhotoLauncher.launch("image/*")
    }

    fun launchVoiceInput() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale("he", "IL").toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "דברו עם פיתי")
        }
        runCatching { speechLauncher.launch(intent) }
            .onFailure { viewModel.showMessage("הכתבה קולית אינה זמינה במכשיר הזה") }
    }
}

@Composable
private fun StudyZoneRoot(viewModel: AppViewModel, state: com.hillel.studyzone.model.UiState, launchIntent: Intent?) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val resolvedDark = when (state.settings.themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    var introVisible by rememberSaveable { mutableStateOf(true) }
    var handledDeepLink by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingAdminExport by remember { mutableStateOf<String?>(null) }
    var themeRevealOrigin by remember { mutableStateOf(Offset(82f, 56f)) }
    val adminExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val content = pendingAdminExport
        if (uri != null && content != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(content) }
            }
        }
        pendingAdminExport = null
        viewModel.consumeAdminExport()
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) viewModel.setSystemNotifications(false)
    }

    LaunchedEffect(Unit) {
        // Let real content draw underneath first, then play a brief branded hand-off. Network and
        // DataStore work continue independently and can never prolong this animation.
        withFrameNanos { }
        delay(if (state.settings.reduceMotion) 80 else 620)
        introVisible = false
    }

    LaunchedEffect(state.toast) {
        if (state.toast != null) {
            delay(2800)
            viewModel.consumeToast()
        }
    }

    LaunchedEffect(state.adminExportJson) {
        val json = state.adminExportJson ?: return@LaunchedEffect
        if (pendingAdminExport == null) {
            pendingAdminExport = json
            adminExportLauncher.launch("gemini-server-keys-${System.currentTimeMillis()}.json")
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

    val hasBackDestination = introVisible || state.adminOpen || state.authOpen ||
        state.chatOpen || state.lesson != null || state.lessonLoading || state.selectedCourse != null
    BackHandler(enabled = hasBackDestination) {
        when {
            introVisible -> introVisible = false
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
                onNext = { viewModel.openAdjacent(state.lesson?.nextSectionId) },
                onAskSelection = viewModel::askPythiAboutSelection
            )
            selectedCourse != null -> CourseDetailScreen(
                state = state,
                onBack = viewModel::closeCourse,
                onLesson = { sectionId -> viewModel.openLesson(selectedCourse.id, sectionId) },
                onRequestAccess = { viewModel.requestCourseAccess(selectedCourse.id) }
            )
            else -> AnimatedContent(targetState = state.rootTab, label = "rootTab") { tab ->
                when (tab) {
                    RootTab.COURSES -> CoursesScreen(
                        state,
                        onCourse = viewModel::openCourse,
                        onProfile = { viewModel.selectTab(RootTab.PROFILE) },
                        darkMode = resolvedDark,
                        onThemeToggle = { origin ->
                            themeRevealOrigin = origin
                            // SYSTEM means the current phone palette, so the first tap must always
                            // create a visible change instead of merely replacing SYSTEM with DARK.
                            viewModel.setTheme(if (resolvedDark) ThemeMode.LIGHT else ThemeMode.DARK)
                        }
                    )
                    RootTab.SEARCH -> SearchScreen(state, viewModel::setSearchQuery, viewModel::openLesson)
                    RootTab.PROFILE -> ProfileScreen(
                        state,
                        onAuth = { viewModel.setAuthOpen(true) },
                        onLogout = viewModel::logout,
                        onAdmin = viewModel::openAdmin,
                        onSaveMemory = viewModel::savePythiMemory,
                        onRemoveMemory = viewModel::removePythiMemory,
                        onUpdateName = viewModel::updateProfileName,
                        onUpdatePhoto = { (context as? MainActivity)?.launchProfilePhotoPicker() },
                        onSendAdminMessage = viewModel::sendAdminMessage,
                        onChangePassword = viewModel::changePassword
                    )
                    RootTab.SETTINGS -> SettingsScreen(
                        state = state,
                        onTheme = { mode, origin ->
                            themeRevealOrigin = origin
                            viewModel.setTheme(mode)
                        },
                        onReduceMotion = viewModel::setReduceMotion,
                        onHaptics = viewModel::setHaptics,
                        onFontScale = viewModel::setFontScale,
                        onKeepScreenOn = viewModel::setKeepScreenOn,
                        onPersistChatHistory = viewModel::setPersistChatHistory,
                        onRememberPosition = viewModel::setRememberPosition,
                        onReadingProgress = viewModel::setShowReadingProgress,
                        onGreenChecks = viewModel::setShowGreenChecks,
                        onActionSuggestions = viewModel::setShowActionSuggestions,
                        onPromptNavigator = viewModel::setShowChatPromptNavigator,
                        onAskPopover = viewModel::setEnableAskPopover,
                        onClearSelection = viewModel::setClearSelectionAfterPopover,
                        onSystemNotifications = { enabled ->
                            viewModel.setSystemNotifications(enabled)
                            if (enabled && Build.VERSION.SDK_INT >= 33 &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                            ) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        },
                        onLoginNotifications = viewModel::setEmailLoginNotifications,
                        onPasswordNotifications = viewModel::setEmailPasswordNotifications,
                        onLineSpacing = viewModel::setLineSpacing,
                        onSelectionHighlight = viewModel::setSelectionHighlight,
                        onActiveTheme = viewModel::setActiveTheme,
                        onSaveApiKeys = viewModel::saveUserApiKeys
                    )
                }
            }
        }

        if (selectedCourse == null && state.lesson == null && !state.lessonLoading && !state.adminOpen) {
            BottomGlassNav(
                active = state.rootTab,
                onTab = viewModel::selectTab,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)
            )
        }

        if (!state.adminOpen && !state.authOpen) {
            ChatOverlay(
                state = state,
                onOpen = viewModel::setChatOpen,
                onExpanded = viewModel::setChatExpanded,
                onInput = viewModel::setChatInput,
                onAttach = { (context as? MainActivity)?.launchChatAttachmentPicker() },
                onRemoveAttachment = viewModel::removeChatAttachment,
                onVoice = { (context as? MainActivity)?.launchVoiceInput() },
                onSend = viewModel::sendChat,
                onStop = viewModel::stopChat,
                onClear = viewModel::clearChat,
                modifier = Modifier.fillMaxSize()
            )
        }

        AuthOverlay(
            visible = state.authOpen,
            loading = state.authLoading,
            onDismiss = { viewModel.setAuthOpen(false) },
            onLogin = viewModel::login,
            onRegister = viewModel::register,
            onGoogleLogin = { (context as? MainActivity)?.launchGoogleSignIn() }
        )

        AnimatedVisibility(state.adminOpen, enter = fadeIn() + scaleIn(initialScale = .96f), exit = fadeOut() + scaleOut(targetScale = .96f)) {
            AdminScreen(
                state,
                onClose = viewModel::closeAdmin,
                onReload = viewModel::loadAdmin,
                onSelectUser = viewModel::selectAdminUser,
                onToggleBlock = viewModel::toggleUserBlock,
                onDeleteUser = viewModel::deleteAdminUser,
                onSendMessage = viewModel::sendAdminMessage,
                onLoadRequests = viewModel::loadAdminRequests,
                onAccess = viewModel::handleAccessRequest,
                onTogglePublic = viewModel::togglePublicCourse,
                onClearPublic = viewModel::clearPublicCourses,
                onSettings = viewModel::updateAdminSettings,
                onCreateKey = viewModel::createGeminiKey,
                onUpdateKey = viewModel::updateGeminiKey,
                onClearCooldown = viewModel::clearGeminiCooldown,
                onDeleteKey = viewModel::deleteGeminiKey,
                onExportKeys = viewModel::exportGeminiKeys,
                onPassword = viewModel::updateSystemPassword
            )
        }

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
        if (!introVisible) ThemeRevealOverlay(MaterialTheme.colorScheme.background, themeRevealOrigin)
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
