package com.hillel.studyzone.ui.components

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material3.MaterialTheme
import org.json.JSONObject

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LessonMathView(content: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val background = MaterialTheme.colorScheme.background.toArgb().toHexColor()
    val foreground = MaterialTheme.colorScheme.onBackground.toArgb().toHexColor()
    val surface = MaterialTheme.colorScheme.surfaceVariant.toArgb().toHexColor()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant.toArgb().toHexColor()
    val encoded = remember(content) {
        JSONObject.quote(content)
            .replace("<", "\\u003c")
            .replace(">", "\\u003e")
            .replace("&", "\\u0026")
    }
    val html = remember(content, background, foreground, surface, muted) {
        """
        <!doctype html>
        <html dir="rtl" lang="he">
        <head>
          <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=5,user-scalable=yes" />
          <meta charset="utf-8" />
          <style>
            :root { color-scheme: light dark; }
            * { box-sizing: border-box; }
            body { margin: 0; padding: 12px 20px 120px; background: $background; color: $foreground;
                   font: 18px/1.85 -apple-system,BlinkMacSystemFont,"Segoe UI",Arial,sans-serif; }
            article { max-width: 820px; margin: auto; overflow-wrap: anywhere; white-space: pre-wrap; }
            h1,h2,h3 { line-height: 1.25; letter-spacing: -.02em; margin: 1.7em 0 .65em; }
            h1 { font-size: 2rem; } h2 { font-size: 1.55rem; } h3 { font-size: 1.25rem; }
            .MathJax { direction: ltr; }
            mjx-container[display="true"] { direction: ltr; overflow-x: auto; overflow-y: hidden; padding: 12px 0; }
            pre,code { direction: ltr; text-align: left; font-family: ui-monospace,monospace; }
            pre { background:$surface; border:1px solid $muted; border-radius:20px; padding:16px; overflow:auto; }
            a { color:#1473ff; }
            ::selection { background:#1473ff55; }
          </style>
          <script>
            MathJax = { tex: { inlineMath: [['\\(','\\)'],['${'$'}','${'$'}']], displayMath: [['\\[','\\]'],['${'$'}${'$'}','${'$'}${'$'}']] },
                        options: { skipHtmlTags: ['script','noscript','style','textarea','pre','code'] } };
          </script>
          <script defer src="https://cdn.jsdelivr.net/npm/mathjax@3/es5/tex-chtml.js"></script>
        </head>
        <body><article id="lesson"></article>
        <script>
          const raw = $encoded;
          const safe = raw.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
          document.getElementById('lesson').innerHTML = safe
            .replace(/^### (.+)$/gm,'<h3>${'$'}1</h3>')
            .replace(/^## (.+)$/gm,'<h2>${'$'}1</h2>')
            .replace(/^# (.+)$/gm,'<h1>${'$'}1</h1>')
            .replace(/\*\*(.+?)\*\*/g,'<strong>${'$'}1</strong>')
            .replace(/\[\[BR\]\]/g,'<br><br>');
        </script></body></html>
        """.trimIndent()
    }
    val webView = remember {
        WebView(context).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = false
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val uri = request?.url ?: return true
                    if (uri.scheme == "https" || uri.scheme == "http") {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri.toString())))
                    }
                    return true
                }
            }
        }
    }
    AndroidView(
        factory = { webView },
        modifier = modifier,
        update = { it.loadDataWithBaseURL("https://app.studyzone.local/", html, "text/html", "UTF-8", null) }
    )
    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }
}

private fun Int.toHexColor(): String = String.format("#%06X", 0xFFFFFF and this)

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun InteractiveLessonView(url: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val allowedHost = remember(url) { runCatching { Uri.parse(url).host.orEmpty() }.getOrDefault("") }
    val webView = remember(url) {
        WebView(context).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.setSupportZoom(true)
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val target = request?.url ?: return true
                    if (target.scheme == "https" && target.host == allowedHost) return false
                    if (target.scheme == "https" || target.scheme == "http") {
                        context.startActivity(Intent(Intent.ACTION_VIEW, target))
                    }
                    return true
                }
            }
            loadUrl(url)
        }
    }
    AndroidView(factory = { webView }, modifier = modifier)
    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }
}
