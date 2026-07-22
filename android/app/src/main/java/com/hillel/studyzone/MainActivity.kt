package com.hillel.studyzone

import android.Manifest
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Base64
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.JavascriptInterface
import android.webkit.MimeTypeMap
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.webkit.WebViewAssetLoader
import java.io.ByteArrayInputStream
import java.io.File

class MainActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private lateinit var assetLoader: WebViewAssetLoader
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var pendingMediaPermission: PermissionRequest? = null

    private val filePicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        fileCallback?.onReceiveValue(uris.toTypedArray())
        fileCallback = null
    }

    private val microphonePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val request = pendingMediaPermission
        pendingMediaPermission = null
        if (request == null) return@registerForActivityResult
        if (granted) request.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) else request.deny()
    }

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        splash.setOnExitAnimationListener { provider ->
            provider.view.animate()
                .alpha(0f)
                .scaleX(1.03f)
                .scaleY(1.03f)
                .setDuration(170L)
                .withEndAction { provider.remove() }
                .start()
        }

        assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/app/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        webView = WebView(this)
        configureWebView(webView)
        setContentView(webView)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = handleWebBack()
        })

        if (savedInstanceState == null) {
            webView.loadUrl(localUrlForIntent(intent))
        } else {
            webView.restoreState(savedInstanceState)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (::webView.isInitialized) webView.loadUrl(localUrlForIntent(intent))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (::webView.isInitialized) webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        fileCallback?.onReceiveValue(null)
        fileCallback = null
        pendingMediaPermission?.deny()
        pendingMediaPermission = null
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.removeJavascriptInterface(BRIDGE_NAME)
            webView.destroy()
        }
        super.onDestroy()
    }

    @Suppress("SetJavaScriptEnabled")
    private fun configureWebView(view: WebView) {
        view.setBackgroundColor(Color.TRANSPARENT)
        view.alpha = 0f
        view.overScrollMode = View.OVER_SCROLL_NEVER
        view.isVerticalScrollBarEnabled = false
        view.isHorizontalScrollBarEnabled = false
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)

        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
            loadsImagesAutomatically = true
            mediaPlaybackRequiresUserGesture = true
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            userAgentString = "$userAgentString StudyZoneAndroid/${BuildConfig.VERSION_NAME}"
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(view, true)
        }

        view.addJavascriptInterface(AndroidBridge(), BRIDGE_NAME)
        view.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val uri = request?.url ?: return null
                if (uri.host in BLOCKED_FRONTEND_HOSTS) return emptyResponse()
                assetLoader.shouldInterceptRequest(uri)?.let { return it }
                return openRootBundledAsset(uri)
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val target = request?.url ?: return true
                if (target.host == LOCAL_HOST) return false
                if (target.host in BLOCKED_FRONTEND_HOSTS) return true

                if ((target.scheme == "https" || target.scheme == "http") && request.isForMainFrame) {
                    if (request.hasGesture()) {
                        runCatching { startActivity(Intent(Intent.ACTION_VIEW, target)) }
                    }
                    return true
                }
                return target.scheme != "https" && target.scheme != "http"
            }

            override fun onPageCommitVisible(view: WebView?, url: String?) {
                view?.animate()?.alpha(1f)?.setDuration(170L)?.start()
            }
        }

        view.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                callback: ValueCallback<Array<Uri>>?,
                params: FileChooserParams?
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = callback
                val accepted = params?.acceptTypes
                    ?.map { it.trim() }
                    ?.filter { it.isNotEmpty() }
                    ?.toTypedArray()
                    ?.takeIf { it.isNotEmpty() }
                    ?: arrayOf("*/*")
                filePicker.launch(accepted)
                return true
            }

            override fun onPermissionRequest(request: PermissionRequest?) {
                if (request == null) return
                runOnUiThread {
                    val wantsAudio = request.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)
                    if (!wantsAudio) {
                        request.deny()
                        return@runOnUiThread
                    }
                    if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        request.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
                    } else {
                        pendingMediaPermission?.deny()
                        pendingMediaPermission = request
                        microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
            }

            override fun onPermissionRequestCanceled(request: PermissionRequest?) {
                if (pendingMediaPermission === request) pendingMediaPermission = null
            }
        }

        view.setDownloadListener(DownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            if (!url.startsWith("https://")) return@DownloadListener
            val request = DownloadManager.Request(Uri.parse(url))
                .setMimeType(mimeType)
                .addRequestHeader("User-Agent", userAgent)
                .addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url).orEmpty())
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, URLUtilCompat.fileName(url, contentDisposition, mimeType))
            (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
        })
    }

    private fun openRootBundledAsset(uri: Uri): WebResourceResponse? {
        if (uri.host != LOCAL_HOST) return null
        val relativePath = uri.path.orEmpty().removePrefix("/")
        if (relativePath.isBlank() || relativePath.startsWith("app/")) return null
        return runCatching {
            val mime = mimeTypeFor(relativePath)
            WebResourceResponse(mime, if (mime.startsWith("text/") || mime.contains("javascript")) "UTF-8" else null, assets.open(relativePath))
        }.getOrNull()
    }

    private fun handleWebBack() {
        val script = """
            (function() {
              var chat = document.getElementById('pythi-chat-window');
              if (chat) {
                var toggle = document.getElementById('pythi-chat-toggle');
                if (toggle) toggle.click();
                return 'handled';
              }
              window.__studyZoneAndroidBackHandled = false;
              window.dispatchEvent(new Event('StudyZone:androidBack'));
              if (window.__studyZoneAndroidBackHandled) return 'handled';
              if (location.hash && location.hash !== '#') {
                history.back();
                return 'handled';
              }
              return 'exit';
            })();
        """.trimIndent()
        webView.evaluateJavascript(script) { result ->
            if (result == "\"exit\"") finish()
        }
    }

    private fun localUrlForIntent(intent: Intent?): String {
        val uri = intent?.data ?: return LOCAL_URL
        if (uri.scheme != "studyzone") return LOCAL_URL
        val courseId = uri.host ?: uri.pathSegments.firstOrNull() ?: return LOCAL_URL
        val remaining = if (uri.host != null) uri.pathSegments else uri.pathSegments.drop(1)
        val route = (listOf(courseId) + remaining).joinToString("/") { Uri.encode(it) }
        return "$LOCAL_URL#$route"
    }

    private inner class AndroidBridge {
        @JavascriptInterface
        fun haptic(kind: String) {
            runOnUiThread {
                val feedback = when (kind) {
                    "success" -> HapticFeedbackConstants.CONFIRM
                    "warning" -> HapticFeedbackConstants.REJECT
                    else -> HapticFeedbackConstants.CLOCK_TICK
                }
                webView.performHapticFeedback(feedback)
            }
        }

        @JavascriptInterface
        fun setKeepScreenOn(enabled: Boolean) {
            runOnUiThread {
                if (enabled) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }

        @JavascriptInterface
        fun requestNotifications() {
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                runOnUiThread { notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }
            }
        }

        @JavascriptInterface
        fun shareFile(fileName: String, mimeType: String, base64Data: String) {
            Thread {
                runCatching {
                    val safeName = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_").take(120).ifBlank { "studyzone-export" }
                    val directory = File(cacheDir, "shared").apply { mkdirs() }
                    val target = File(directory, safeName)
                    target.writeBytes(Base64.decode(base64Data, Base64.DEFAULT))
                    val uri = FileProvider.getUriForFile(this@MainActivity, "$packageName.files", target)
                    val share = Intent(Intent.ACTION_SEND).apply {
                        type = mimeType.ifBlank { "application/octet-stream" }
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    runOnUiThread { startActivity(Intent.createChooser(share, "שיתוף מ־StudyZone")) }
                }
            }.start()
        }
    }

    private fun emptyResponse() = WebResourceResponse(
        "text/plain",
        "UTF-8",
        ByteArrayInputStream(ByteArray(0))
    )

    private fun mimeTypeFor(path: String): String {
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

    private object URLUtilCompat {
        fun fileName(url: String, contentDisposition: String?, mimeType: String?): String {
            val guessed = android.webkit.URLUtil.guessFileName(url, contentDisposition, mimeType)
            return guessed.replace(Regex("[\\/]+"), "_")
        }
    }

    companion object {
        private const val LOCAL_HOST = "appassets.androidplatform.net"
        private const val LOCAL_URL = "https://$LOCAL_HOST/app/android.html"
        private const val BRIDGE_NAME = "StudyZoneAndroid"
        // Constructed at runtime so forbidden frontend URLs are not shipped as usable strings.
        private val BLOCKED_FRONTEND_HOSTS = setOf("yhnz", "i9d8").mapTo(mutableSetOf()) { suffix ->
            listOf("studyzone", "1", suffix).joinToString("-") + ".onrender.com"
        }
    }
}
