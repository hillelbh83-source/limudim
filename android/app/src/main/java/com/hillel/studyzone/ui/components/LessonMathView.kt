package com.hillel.studyzone.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.view.View
import android.view.MotionEvent
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.hillel.studyzone.model.ChatMessage
import org.json.JSONArray
import org.json.JSONObject

/**
 * Native lesson shell backed by one local WebView only while a lesson is visible.
 *
 * KaTeX is bundled in the APK. There is no CDN round-trip and content is submitted to the same
 * renderer instead of calling loadData again for every Compose recomposition.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LessonMathView(content: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val palette = currentWebPalette()
    val bodySize = MaterialTheme.typography.bodyLarge.fontSize.value.coerceIn(15f, 24f)
    val shell = remember(palette, bodySize) {
        lessonShell(
            palette = palette,
            bodySize = bodySize,
            katexSource = KatexAsset.get(context)
        )
    }

    key(shell.hashCode()) {
        val webView = remember {
            LocalRendererWebView(context).apply {
                configureLocalRenderer(palette.background)
                webViewClient = rendererClient()
                loadDataWithBaseURL(LOCAL_BASE_URL, shell, "text/html", "UTF-8", null)
            }
        }
        AndroidView(
            factory = { webView },
            modifier = modifier,
            update = { it.submit("lesson", JSONObject.quote(content)) }
        )
        DisposableEffect(webView) {
            onDispose { webView.disposeSafely() }
        }
    }
}

/**
 * One renderer for the complete transcript, never one WebView per bubble. Regular messages,
 * Markdown and all common TeX delimiters are normalized in the same document and equations are
 * emitted as LTR MathML by the bundled KaTeX runtime.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ChatRichText(
    messages: List<ChatMessage>,
    onTap: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (messages.isEmpty()) return
    val context = LocalContext.current
    val palette = currentWebPalette()
    val bodySize = MaterialTheme.typography.bodyMedium.fontSize.value.coerceIn(13f, 20f)
    val shell = remember(palette, bodySize) {
        chatShell(
            palette = palette,
            bodySize = bodySize,
            katexSource = KatexAsset.get(context)
        )
    }
    val payload = remember(messages) {
        JSONArray().apply {
            messages.forEach { message ->
                put(
                    JSONObject()
                        .put("role", message.role)
                        .put("text", message.text)
                        .put("streaming", message.isStreaming)
                        .put("error", message.isError)
                )
            }
        }.toString()
    }

    key(shell.hashCode()) {
        val webView = remember {
            LocalRendererWebView(context).apply {
                configureLocalRenderer(palette.background)
                contentDescription = "שיחת Pythi"
                webViewClient = rendererClient()
                loadDataWithBaseURL(LOCAL_BASE_URL, shell, "text/html", "UTF-8", null)
            }
        }
        AndroidView(
            factory = { webView },
            modifier = modifier,
            update = {
                it.setOnTouchListener { _, event ->
                    if (event.actionMasked == MotionEvent.ACTION_UP) onTap()
                    false
                }
                it.submit("messages", payload)
            }
        )
        DisposableEffect(webView) {
            onDispose { webView.disposeSafely() }
        }
    }
}

/** Explicit secondary interactive mode; lesson navigation never opens this by default. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun InteractiveLessonView(url: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val allowedHost = remember(url) { runCatching { Uri.parse(url).host.orEmpty() }.getOrDefault("") }
    val webView = remember(url) {
        WebView(context).apply {
            setBackgroundColor(AndroidColor.TRANSPARENT)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val target = request?.url ?: return true
                    if (target.scheme == "https" && target.host == allowedHost) return false
                    if (target.scheme == "https" || target.scheme == "http") {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, target)) }
                    }
                    return true
                }
            }
            if (url.startsWith("https://")) loadUrl(url)
        }
    }
    AndroidView(factory = { webView }, modifier = modifier)
    DisposableEffect(webView) {
        onDispose { webView.disposeSafely() }
    }
}

private data class WebPalette(
    val background: String,
    val foreground: String,
    val surface: String,
    val muted: String,
    val outline: String,
    val error: String
)

@Composable
private fun currentWebPalette() = WebPalette(
    background = MaterialTheme.colorScheme.background.toArgb().toHexColor(),
    foreground = MaterialTheme.colorScheme.onBackground.toArgb().toHexColor(),
    surface = MaterialTheme.colorScheme.surfaceVariant.toArgb().toHexColor(),
    muted = MaterialTheme.colorScheme.onSurfaceVariant.toArgb().toHexColor(),
    outline = MaterialTheme.colorScheme.outlineVariant.toArgb().toHexColor(),
    error = MaterialTheme.colorScheme.error.toArgb().toHexColor()
)

private const val LOCAL_BASE_URL = "file:///android_asset/katex/"

private object KatexAsset {
    @Volatile
    private var cached: String? = null

    fun get(context: Context): String = cached ?: synchronized(this) {
        cached ?: context.applicationContext.assets
            .open("katex/katex.min.js")
            .bufferedReader()
            .use { it.readText() }
            .also { cached = it }
    }
}

private class LocalRendererWebView(context: Context) : WebView(context) {
    private var pageReady = false
    private var pendingCall: String? = null
    private var lastSubmission: Int? = null

    fun submit(function: String, argument: String) {
        val signature = 31 * function.hashCode() + argument.hashCode()
        if (lastSubmission == signature) return
        lastSubmission = signature
        pendingCall = "window.render${function.replaceFirstChar { it.uppercase() }}($argument);"
        flushPending()
    }

    fun markReady() {
        pageReady = true
        flushPending()
    }

    private fun flushPending() {
        if (!pageReady) return
        val script = pendingCall ?: return
        pendingCall = null
        evaluateJavascript(script, null)
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun LocalRendererWebView.configureLocalRenderer(background: String) {
    setBackgroundColor(AndroidColor.parseColor(background))
    isVerticalScrollBarEnabled = false
    isHorizontalScrollBarEnabled = false
    overScrollMode = View.OVER_SCROLL_NEVER
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = false
    // KaTeX CSS and its WOFF2 fonts are packaged next to the renderer. File access is limited to
    // this local document; network and universal access from file URLs remain blocked.
    settings.allowFileAccess = true
    settings.allowContentAccess = false
    settings.allowFileAccessFromFileURLs = false
    settings.allowUniversalAccessFromFileURLs = false
    settings.loadsImagesAutomatically = false
    settings.blockNetworkImage = true
    settings.blockNetworkLoads = true
    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
    settings.cacheMode = WebSettings.LOAD_NO_CACHE
    settings.setSupportZoom(true)
    settings.builtInZoomControls = true
    settings.displayZoomControls = false
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) settings.safeBrowsingEnabled = true
}

private fun LocalRendererWebView.rendererClient(): WebViewClient {
    val renderer = this
    return object : WebViewClient() {
        override fun onPageFinished(view: WebView?, url: String?) {
            renderer.markReady()
        }

        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = true
    }
}

private fun WebView.disposeSafely() {
    stopLoading()
    loadUrl("about:blank")
    clearHistory()
    removeAllViews()
    destroy()
}

private fun lessonShell(palette: WebPalette, bodySize: Float, katexSource: String) = """
    <!doctype html>
    <html dir="rtl" lang="he">
    <head>
      <meta charset="utf-8" />
      <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=3,user-scalable=yes" />
      <link rel="stylesheet" href="katex.min.css" />
      <style>
        :root { color-scheme: light dark; --blue:#1473ff; --fg:${palette.foreground}; --muted:${palette.muted};
                --surface:${palette.surface}; --outline:${palette.outline}; }
        * { box-sizing:border-box; }
        html,body { min-height:100%; background:${palette.background}; }
        body { margin:0; padding:18px 20px 64px; color:var(--fg);
               font:${bodySize}px/1.82 -apple-system,BlinkMacSystemFont,"Segoe UI",Arial,sans-serif;
               -webkit-font-smoothing:antialiased; }
        article { max-width:820px; margin:0 auto; overflow-wrap:anywhere; }
        h1,h2,h3 { margin:1.4em 0 .55em; line-height:1.26; letter-spacing:-.018em; }
        h1:first-child,h2:first-child,h3:first-child { margin-top:.25em; }
        h1 { font-size:1.8em; } h2 { font-size:1.42em; } h3 { font-size:1.18em; }
        p { margin:.72em 0; }
        strong { font-weight:750; }
        ul { margin:.7em 0; padding-inline-start:1.3em; } li { margin:.36em 0; }
        blockquote { margin:1em 0; padding:12px 16px; border-inline-start:3px solid var(--blue);
                     background:var(--surface); border-radius:14px; color:var(--muted); }
        hr { height:1px; margin:1.5em 0; border:0; background:var(--outline); }
        code { direction:ltr; unicode-bidi:isolate; font-family:ui-monospace,SFMono-Regular,monospace;
               background:var(--surface); border-radius:7px; padding:.12em .35em; }
        pre { direction:ltr; text-align:left; overflow:auto; padding:16px; border:1px solid var(--outline);
              background:var(--surface); border-radius:18px; }
        .math { direction:ltr; unicode-bidi:isolate; max-width:100%; color:var(--fg); }
        .math.inline { display:inline-block; vertical-align:-.12em; margin:0 .13em; }
        .math.display { display:block; overflow-x:auto; overflow-y:hidden; margin:1.2em 0; padding:18px 14px;
                        text-align:center; background:color-mix(in srgb,var(--surface) 72%,transparent);
                        border-block:1px solid color-mix(in srgb,var(--outline) 72%,transparent); border-radius:16px; }
        .katex { font-size:1.1em; text-rendering:optimizeLegibility; }
        .katex-display { margin:0; }
        .katex-mathml { position:absolute; }
        .math-error { direction:ltr; color:${palette.error}; font-family:ui-monospace,monospace; }
        ::selection { background:#1473ff42; }
      </style>
      <script>$katexSource</script>
      <script>${sharedRendererScript()}
        window.renderLesson = function(raw) {
          document.getElementById('lesson').innerHTML = renderMarkdown(raw || '');
        };
      </script>
    </head>
    <body><article id="lesson" aria-live="polite"></article></body>
    </html>
""".trimIndent()

private fun chatShell(palette: WebPalette, bodySize: Float, katexSource: String) = """
    <!doctype html>
    <html dir="rtl" lang="he">
    <head>
      <meta charset="utf-8" />
      <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=2,user-scalable=yes" />
      <link rel="stylesheet" href="katex.min.css" />
      <style>
        :root { color-scheme:light dark; --blue:#1473ff; --fg:${palette.foreground}; --muted:${palette.muted};
                --surface:${palette.surface}; --outline:${palette.outline}; --error:${palette.error}; }
        * { box-sizing:border-box; }
        html,body { min-height:100%; background:transparent; }
        body { margin:0; padding:10px 4px 22px; color:var(--fg);
               font:${bodySize}px/1.65 -apple-system,BlinkMacSystemFont,"Segoe UI",Arial,sans-serif;
               -webkit-font-smoothing:antialiased; }
        #messages { display:flex; flex-direction:column; gap:10px; }
        .message { width:fit-content; max-width:88%; padding:12px 14px; border-radius:20px;
                   overflow-wrap:anywhere; border:1px solid transparent; }
        .message.user { align-self:flex-start; color:white; background:var(--blue); border-end-start-radius:7px; }
        .message.model { align-self:flex-end; background:var(--surface); border-color:var(--outline); border-end-end-radius:7px; }
        .message.error { color:var(--error); border-color:color-mix(in srgb,var(--error) 35%,transparent); }
        .message p { margin:.45em 0; } .message p:first-child { margin-top:0; } .message p:last-child { margin-bottom:0; }
        .message ul { margin:.5em 0; padding-inline-start:1.2em; }
        .message hr { height:1px; margin:.9em 0; border:0; background:var(--outline); }
        .math { direction:ltr; unicode-bidi:isolate; max-width:100%; color:inherit; }
        .math.inline { display:inline-block; vertical-align:-.12em; margin:0 .1em; }
        .math.display { display:block; overflow-x:auto; overflow-y:hidden; margin:.95em 0; padding:12px 8px;
                        text-align:center; border-radius:12px;
                        background:color-mix(in srgb,var(--outline) 24%,transparent); }
        .katex { font-size:1.08em; text-rendering:optimizeLegibility; }
        .katex-display { margin:0; }
        .katex-mathml { position:absolute; }
        code { direction:ltr; unicode-bidi:isolate; font-family:ui-monospace,monospace; background:#00000016;
               padding:.12em .3em; border-radius:6px; }
        .typing { display:inline-flex; direction:ltr; gap:4px; padding:4px; }
        .typing i { width:6px; height:6px; border-radius:50%; background:var(--muted); animation:pulse 1s infinite; }
        .typing i:nth-child(2) { animation-delay:.12s; } .typing i:nth-child(3) { animation-delay:.24s; }
        .cursor { display:inline-block; width:2px; height:1.05em; margin-inline-start:3px; vertical-align:-.14em;
                  background:var(--blue); animation:blink .85s step-end infinite; }
        @keyframes pulse { 50% { opacity:.28; transform:translateY(-2px); } }
        @keyframes blink { 50% { opacity:0; } }
      </style>
      <script>$katexSource</script>
      <script>${sharedRendererScript()}
        let pendingMessages = null;
        let messageFrame = 0;
        window.renderMessages = function(messages) {
          pendingMessages = messages || [];
          if (messageFrame) return;
          messageFrame = requestAnimationFrame(function() {
          messageFrame = 0;
          const messages = pendingMessages;
          const root = document.getElementById('messages');
          root.innerHTML = '';
          (messages || []).forEach(function(message) {
            const bubble = document.createElement('section');
            bubble.className = 'message ' + (message.role === 'user' ? 'user' : 'model') + (message.error ? ' error' : '');
            bubble.setAttribute('dir', 'auto');
            if (!message.text && message.streaming) {
              bubble.innerHTML = '<span class="typing" aria-label="Pythi חושבת"><i></i><i></i><i></i></span>';
            } else {
              bubble.innerHTML = renderMarkdown(message.text || '') + (message.streaming ? '<span class="cursor"></span>' : '');
            }
            root.appendChild(bubble);
          });
          requestAnimationFrame(function() { window.scrollTo(0, document.body.scrollHeight); });
          });
        };
      </script>
    </head>
    <body><main id="messages" aria-live="polite"></main></body>
    </html>
""".trimIndent()

private fun sharedRendererScript() = """
    function escapeHtml(value) {
      return String(value || '').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
    }
    function renderTex(tex, display) {
      try {
        return '<span class="math ' + (display ? 'display' : 'inline') + '" dir="ltr">' +
          katex.renderToString(String(tex || '').trim(), {
            output:'htmlAndMathml', throwOnError:false, strict:'ignore', displayMode:display, trust:false
          }) + '</span>';
      } catch (_) {
        return '<code class="math-error" dir="ltr">' + escapeHtml(tex) + '</code>';
      }
    }
    function renderMarkdown(source) {
      let raw = String(source || '').replace(/\r\n?/g,'\n');
      const math = [];
      function token(tex, display) {
        const index = math.push({tex:tex, display:display}) - 1;
        return '@@STUDY_MATH_' + index + '@@';
      }
      raw = raw.replace(/\$\$([\s\S]+?)\$\$/g, (_,tex) => token(tex,true));
      raw = raw.replace(/\\\[([\s\S]+?)\\\]/g, (_,tex) => token(tex,true));
      raw = raw.replace(/\\\(([\s\S]+?)\\\)/g, (_,tex) => token(tex,false));
      raw = raw.replace(/(^|[^\\$])\$([^\n$]+?)\$/g, (_,prefix,tex) => prefix + token(tex,false));

      const lines = raw.split('\n');
      let html = '';
      let listOpen = false;
      function closeList() { if (listOpen) { html += '</ul>'; listOpen = false; } }
      function inline(value) {
        let result = escapeHtml(value)
          .replace(/\*\*(.+?)\*\*/g,'<strong>$1</strong>')
          .replace(/`([^`]+)`/g,'<code>$1</code>');
        result = result.replace(/@@STUDY_MATH_(\d+)@@/g, function(_,index) {
          const item = math[Number(index)];
          return item ? renderTex(item.tex,item.display) : '';
        });
        return result;
      }
      lines.forEach(function(line) {
        const trimmed = line.trim();
        if (!trimmed) { closeList(); return; }
        if (/^---+$/.test(trimmed)) { closeList(); html += '<hr>'; return; }
        if (/^@@STUDY_MATH_\d+@@$/.test(trimmed)) { closeList(); html += inline(trimmed); return; }
        let match;
        if ((match = trimmed.match(/^###\s+(.+)$/))) { closeList(); html += '<h3>' + inline(match[1]) + '</h3>'; return; }
        if ((match = trimmed.match(/^##\s+(.+)$/))) { closeList(); html += '<h2>' + inline(match[1]) + '</h2>'; return; }
        if ((match = trimmed.match(/^#\s+(.+)$/))) { closeList(); html += '<h1>' + inline(match[1]) + '</h1>'; return; }
        if ((match = trimmed.match(/^>\s*(.+)$/))) { closeList(); html += '<blockquote>' + inline(match[1]) + '</blockquote>'; return; }
        if ((match = trimmed.match(/^(?:[-*•]|\d+[.)])\s+(.+)$/))) {
          if (!listOpen) { html += '<ul>'; listOpen = true; }
          html += '<li>' + inline(match[1]) + '</li>'; return;
        }
        closeList(); html += '<p>' + inline(trimmed) + '</p>';
      });
      closeList();
      return html;
    }
""".trimIndent()

private fun Int.toHexColor(): String = String.format("#%06X", 0xFFFFFF and this)
