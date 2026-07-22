package com.hillel.studyzone.ui.components

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background 
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.hillel.studyzone.BuildConfig
import com.hillel.studyzone.model.AppSettings
import com.hillel.studyzone.ui.theme.StudyBlue
import org.json.JSONObject

private enum class SiteMode { CONTENT, PYTHI }

/**
 * Hosts the real React course experience inside the app. It keeps quizzes, interactive blocks and
 * lesson behavior identical to the website while the native shell owns tabs, settings and system UI.
 */
@Composable
fun StudyZoneSiteView(
    route: String,
    darkMode: Boolean,
    settings: AppSettings,
    sessionKey: String,
    onAuthRequired: () -> Unit,
    modifier: Modifier = Modifier
) {
    SiteWebView(
        route = route,
        mode = SiteMode.CONTENT,
        darkMode = darkMode,
        settings = settings,
        sessionKey = sessionKey,
        onAuthRequired = onAuthRequired,
        onClose = {},
        modifier = modifier
    )
}

/** The website's actual Pythi component, including quizzes, exams, files and rich actions. */
@Composable
fun PythiSiteOverlay(
    visible: Boolean,
    darkMode: Boolean,
    settings: AppSettings,
    sessionKey: String,
    onAuthRequired: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(enabled = visible, onBack = onDismiss)
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
        SiteWebView(
            route = "",
            mode = SiteMode.PYTHI,
            darkMode = darkMode,
            settings = settings,
            sessionKey = sessionKey,
            onAuthRequired = onAuthRequired,
            onClose = onDismiss,
            modifier = modifier.fillMaxSize()
        )
    }
}

@Composable
fun PythiOrb(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .size(60.dp)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF49B8FF), StudyBlue, Color(0xFF2843C7))
                )
            )
            .border(1.dp, Color.White.copy(alpha = .34f), CircleShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.size(46.dp).clip(CircleShape)
                .background(Color.White.copy(alpha = .1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.AutoAwesome, "פתיחת פיתי", tint = Color.White, modifier = Modifier.size(27.dp))
        }
        Box(
            Modifier.align(Alignment.TopEnd).padding(7.dp).size(8.dp).clip(CircleShape)
                .background(Color(0xFF4ADE80))
                .border(1.dp, Color.White.copy(alpha = .85f), CircleShape)
        )
    }
}

@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
private fun SiteWebView(
    route: String,
    mode: SiteMode,
    darkMode: Boolean,
    settings: AppSettings,
    sessionKey: String,
    onAuthRequired: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier
) {
    val context = LocalContext.current
    val webRoot = BuildConfig.WEB_BASE_URL.trimEnd('/')
    val allowedHost = remember(webRoot) { Uri.parse(webRoot).host.orEmpty() }
    val url = remember(route, webRoot) {
        if (route.isBlank()) "$webRoot/" else "$webRoot/#${route.trimStart('#', '/')}"
    }
    val background = MaterialTheme.colorScheme.background.toArgb()
    var progress by remember(sessionKey, url, mode) { mutableFloatStateOf(0f) }
    var pageError by remember(sessionKey, url, mode) { mutableStateOf(false) }
    var pythiReady by remember(sessionKey, url, mode) { mutableStateOf(mode != SiteMode.PYTHI) }
    var canGoBack by remember(sessionKey, url, mode) { mutableStateOf(false) }
    var webViewRef by remember(sessionKey, url, mode) { mutableStateOf<WebView?>(null) }
    val settingsScript = remember(darkMode, settings) { siteSettingsScript(darkMode, settings) }

    BackHandler(enabled = mode == SiteMode.CONTENT && canGoBack) {
        webViewRef?.goBack()
    }

    Box(modifier.background(MaterialTheme.colorScheme.background)) {
        val webView = remember(sessionKey, url, mode) {
            WebView(context).apply {
                webViewRef = this
                setBackgroundColor(background)
                alpha = 0f
                overScrollMode = View.OVER_SCROLL_NEVER
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                this.settings.javaScriptEnabled = true
                this.settings.domStorageEnabled = true
                this.settings.databaseEnabled = true
                this.settings.allowFileAccess = false
                this.settings.allowContentAccess = false
                this.settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                this.settings.cacheMode = WebSettings.LOAD_DEFAULT
                this.settings.loadsImagesAutomatically = true
                this.settings.mediaPlaybackRequiresUserGesture = true
                this.settings.setSupportZoom(false)
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                addJavascriptInterface(
                    SiteBridge(
                        onAuthRequired = onAuthRequired,
                        onClose = onClose,
                        onPythiReady = { pythiReady = true }
                    ),
                    "AndroidStudyZone"
                )
                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        progress = newProgress / 100f
                    }
                }
                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                        pageError = false
                        pythiReady = mode != SiteMode.PYTHI
                        progress = 0f
                        view?.alpha = 0f
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        canGoBack = view?.canGoBack() == true
                        val modeScript = if (mode == SiteMode.PYTHI) pythiIsolationScript() else contentBridgeScript()
                        view?.evaluateJavascript("$settingsScript\n$modeScript") {
                            view.animate().alpha(1f).setDuration(150L).start()
                        }
                    }

                    override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                        canGoBack = view?.canGoBack() == true
                        if (mode == SiteMode.CONTENT) view?.evaluateJavascript(contentBridgeScript(), null)
                    }

                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        val target = request?.url ?: return true
                        if (target.scheme == "https" && target.host == allowedHost) return false
                        if (target.scheme == "https" || target.scheme == "http") {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, target)) }
                        }
                        return true
                    }

                    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                        if (request?.isForMainFrame == true) pageError = true
                    }
                }
                loadUrl(url)
            }
        }

        AndroidView(
            factory = { webView },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                view.setBackgroundColor(background)
                view.evaluateJavascript(settingsScript, null)
            }
        )

        if ((progress < 1f || !pythiReady) && !pageError) {
            CircularProgressIndicator(
                progress = { progress.coerceIn(.04f, 1f) },
                modifier = Modifier.align(Alignment.Center).size(34.dp),
                color = StudyBlue,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeWidth = 3.dp
            )
        }

        if (pageError) {
            Pressable(
                onClick = { pageError = false; webView.reload() },
                modifier = Modifier.align(Alignment.Center)
            ) {
                Icon(Icons.Rounded.Refresh, null, tint = StudyBlue)
                Text("ניסיון טעינה נוסף")
            }
        }
        DisposableEffect(webView) {
            onDispose {
                webViewRef = null
                webView.stopLoading()
                webView.removeJavascriptInterface("AndroidStudyZone")
                webView.destroy()
            }
        }
    }

    LaunchedEffect(settingsScript, webViewRef) {
        webViewRef?.evaluateJavascript(settingsScript, null)
    }
}

private class SiteBridge(
    private val onAuthRequired: () -> Unit,
    private val onClose: () -> Unit,
    private val onPythiReady: () -> Unit
) {
    private val main = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun openAuth() = main.post { onAuthRequired() }

    @JavascriptInterface
    fun closePythi() = main.post { onClose() }

    @JavascriptInterface
    fun pythiReady() = main.post { onPythiReady() }
}

private fun siteSettingsScript(darkMode: Boolean, settings: AppSettings): String {
    val theme = if (darkMode) "dark" else "light"
    val fontSize = when {
        settings.fontScale >= 1.22f -> "xl"
        settings.fontScale >= 1.08f -> "large"
        else -> "normal"
    }
    val lineHeight = when {
        settings.lineSpacing >= 1.25f -> "loose"
        settings.lineSpacing >= 1.1f -> "relaxed"
        else -> "normal"
    }
    val patch = JSONObject()
        .put("theme", theme)
        .put("persistChatHistory", settings.persistChatHistory)
        .put("rememberPosition", settings.rememberPosition)
        .put("showReadingProgress", settings.showReadingProgress)
        .put("showGreenChecks", settings.showGreenChecks)
        .put("showActionSuggestions", settings.showActionSuggestions)
        .put(
            "emailNotifications",
            JSONObject()
                .put("login", settings.emailLoginNotifications)
                .put("passwordChanged", settings.emailPasswordNotifications)
        )
        .put("typography", JSONObject().put("fontSize", fontSize).put("lineHeight", lineHeight))
    return """
        (function() {
          try {
            var current = {};
            try { current = JSON.parse(localStorage.getItem('StudyMaster_user_settings_v1') || '{}'); } catch (_) {}
            var patch = $patch;
            patch.emailNotifications = Object.assign({}, current.emailNotifications || {}, patch.emailNotifications || {});
            patch.typography = Object.assign({}, current.typography || {}, patch.typography || {});
            localStorage.setItem('StudyMaster_user_settings_v1', JSON.stringify(Object.assign({}, current, patch)));
            var applyTheme = function() {
              localStorage.setItem('theme', '$theme');
              document.documentElement.classList.toggle('dark', '$theme' === 'dark');
              document.documentElement.classList.toggle('light', '$theme' === 'light');
              window.dispatchEvent(new CustomEvent('StudyMaster:userSettingsUpdated', { detail: Object.assign({}, current, patch) }));
              window.dispatchEvent(new Event('StudyMaster:dataRestored'));
            };
            applyTheme();
            setTimeout(applyTheme, 120);
            setTimeout(applyTheme, 480);
          } catch (_) {}
        })();
    """.trimIndent()
}

private fun contentBridgeScript() = """
    (function() {
      if (window.__studyZoneAndroidContentBridge) return;
      window.__studyZoneAndroidContentBridge = true;
      var nativeStyle = document.createElement('style');
      nativeStyle.textContent = '#pythi-chat-toggle{display:none!important}';
      document.head.appendChild(nativeStyle);
      var previousHash = location.hash;
      var selectionHash = function(hash) { return !hash || hash === '#' || hash === '#/'; };
      var saveScroll = function() {
        if (selectionHash(location.hash)) localStorage.setItem('StudyZone_android_course_scroll', String(window.scrollY || 0));
      };
      window.addEventListener('scroll', function() {
        if (!selectionHash(location.hash)) return;
        clearTimeout(window.__studyZoneScrollTimer);
        window.__studyZoneScrollTimer = setTimeout(saveScroll, 80);
      }, { passive: true });
      window.addEventListener('hashchange', function() {
        if (selectionHash(previousHash)) saveScroll();
        previousHash = location.hash;
        if (selectionHash(location.hash)) {
          var y = Number(localStorage.getItem('StudyZone_android_course_scroll') || 0);
          setTimeout(function() { window.scrollTo(0, y); }, 100);
          setTimeout(function() { window.scrollTo(0, y); }, 420);
        }
      });
      if (selectionHash(location.hash)) {
        var y = Number(localStorage.getItem('StudyZone_android_course_scroll') || 0);
        setTimeout(function() { window.scrollTo(0, y); }, 180);
      }
      document.addEventListener('click', function(event) {
        if (selectionHash(location.hash)) {
          localStorage.setItem('StudyZone_android_course_scroll', String(window.scrollY || 0));
        }
        var button = event.target && event.target.closest ? event.target.closest('button') : null;
        var text = button ? String(button.textContent || '').trim() : '';
        if (button && (text === 'התחברות' || text.indexOf('התחברות לחשבון') >= 0)) {
          event.preventDefault();
          event.stopPropagation();
          AndroidStudyZone.openAuth();
        }
      }, true);
    })();
""".trimIndent()

private fun pythiIsolationScript() = """
    (function() {
      if (window.__studyZoneAndroidPythiBridge) return;
      window.__studyZoneAndroidPythiBridge = true;
      var opened = false;
      var style = document.createElement('style');
      style.textContent = '#root{opacity:0!important;pointer-events:none!important}#pythi-chat-toggle{display:none!important}body{overflow:hidden!important}';
      document.head.appendChild(style);
      var reveal = function() {
        var chat = document.getElementById('pythi-chat-window');
        if (chat) {
          opened = true;
          chat.style.zIndex = '2147483646';
          AndroidStudyZone.pythiReady();
          return true;
        }
        var toggle = document.getElementById('pythi-chat-toggle');
        if (toggle) {
          toggle.style.display = 'block';
          toggle.click();
          toggle.style.display = 'none';
        }
        return false;
      };
      var attempts = 0;
      var timer = setInterval(function() {
        attempts += 1;
        if (reveal() || attempts > 120) clearInterval(timer);
      }, 100);
      new MutationObserver(function() {
        var present = !!document.getElementById('pythi-chat-window');
        if (opened && !present) AndroidStudyZone.closePythi();
      }).observe(document.body, { childList: true, subtree: true });
    })();
""".trimIndent()
