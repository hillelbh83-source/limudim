package com.hillel.studyzone.ui.components

import android.graphics.Color
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.MimeTypeMap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import kotlinx.coroutines.delay

/**
 * Displays only an opened course with the local React renderer bundled in the APK.
 * Every root screen, account flow, setting and Pythi remain native Compose screens.
 */
@Composable
fun LocalCourseWebView(
    courseId: String,
    darkMode: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var ready by remember(courseId, darkMode) { mutableStateOf(false) }
    var error by remember(courseId, darkMode) { mutableStateOf<String?>(null) }
    val background = if (darkMode) Color.rgb(2, 6, 23) else Color.rgb(248, 250, 252)
    LaunchedEffect(courseId, darkMode) {
        delay(12_000)
        if (!ready && error == null) error = "טעינת תוכן הקורס ארכה יותר מדי"
    }
    val loader = remember {
        WebViewAssetLoader.Builder()
            .addPathHandler("/app/", WebViewAssetLoader.AssetsPathHandler(context))
            .build()
    }
    val webView = remember(courseId, darkMode) {
        WebView(context).apply {
            setBackgroundColor(background)
            overScrollMode = WebView.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                allowFileAccess = false
                allowContentAccess = false
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                mediaPlaybackRequiresUserGesture = true
                setSupportZoom(false)
                cacheMode = WebSettings.LOAD_DEFAULT
            }
            addJavascriptInterface(
                CourseReadyBridge(
                    onReady = { post { ready = true; error = null } },
                    onError = { message -> post { error = message.ifBlank { "תוכן הקורס לא נטען" } } }
                ),
                "StudyZoneCourse"
            )
            val courseWebView = this
            CookieManager.getInstance().apply {
                setAcceptCookie(true)
                setAcceptThirdPartyCookies(courseWebView, true)
            }
            webViewClient = LocalCourseClient(loader, context.assets) { message ->
                post { error = message }
            }
            loadUrl(
                LOCAL_COURSE_URL + "?course=" + Uri.encode(courseId) +
                    "&theme=" + (if (darkMode) "dark" else "light")
            )
        }
    }

    BackHandler(onBack = onBack)
    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
            webView.webChromeClient = null
            webView.destroy()
        }
    }

    Box(modifier.fillMaxSize()) {
        AndroidView(
            factory = { webView },
            modifier = Modifier.fillMaxSize().background(if (darkMode) ComposeColor(0xFF020617) else ComposeColor(0xFFF8FAFC))
        )
        if (!ready || error != null) {
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (error == null) {
                        CircularProgressIndicator(color = com.hillel.studyzone.ui.theme.StudyBlue)
                        Text(
                            "פותח את הקורס…",
                            modifier = Modifier.padding(top = 16.dp),
                            fontWeight = FontWeight.SemiBold
                        )
                    } else {
                        Text("לא הצלחנו לפתוח את הקורס", fontWeight = FontWeight.Bold)
                        Text(
                            error.orEmpty(),
                            modifier = Modifier.padding(top = 8.dp, start = 28.dp, end = 28.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        RoundActionButton(
            icon = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = "חזרה לאפליקציה",
            onClick = onBack,
            size = 46.dp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(14.dp)
        )
    }
}

private class LocalCourseClient(
    private val loader: WebViewAssetLoader,
    private val assets: android.content.res.AssetManager,
    private val onError: (String) -> Unit
) : WebViewClient() {
    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        val uri = request?.url ?: return null
        loader.shouldInterceptRequest(uri)?.let { return it }
        if (uri.host != LOCAL_HOST) return null
        val path = uri.path.orEmpty().removePrefix("/")
        if (path.isBlank() || path.startsWith("app/")) return null
        return runCatching {
            val mime = mimeType(path)
            WebResourceResponse(
                mime,
                if (mime.startsWith("text/") || mime.contains("javascript")) "UTF-8" else null,
                assets.open(path)
            )
        }.getOrNull()
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val uri = request?.url ?: return true
        return uri.host != LOCAL_HOST
    }

    override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: android.webkit.WebResourceError?
    ) {
        if (request?.isForMainFrame == true) onError("קובץ הקורס המקומי לא נטען")
    }
}

private class CourseReadyBridge(
    private val onReady: () -> Unit,
    private val onError: (String) -> Unit
) {
    @JavascriptInterface fun ready() = onReady()
    @JavascriptInterface fun error(message: String) = onError(message)
}

private fun mimeType(path: String): String {
    val extension = path.substringAfterLast('.', "").lowercase()
    return when (extension) {
        "js", "mjs" -> "text/javascript"
        "css" -> "text/css"
        "html" -> "text/html"
        "svg" -> "image/svg+xml"
        "json" -> "application/json"
        "woff2" -> "font/woff2"
        "wasm" -> "application/wasm"
        else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
    }
}

private const val LOCAL_HOST = "appassets.androidplatform.net"
private const val LOCAL_COURSE_URL = "https://" + LOCAL_HOST + "/app/android.html"
