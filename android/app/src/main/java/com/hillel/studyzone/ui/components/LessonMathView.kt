package com.hillel.studyzone.ui.components

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.MotionEvent
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.JavascriptInterface
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
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
fun LessonMathView(
    content: String,
    selectionEnabled: Boolean = true,
    clearSelectionAfterAction: Boolean = false,
    selectionHighlight: String = "default",
    onAskSelection: (String, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val palette = currentWebPalette()
    val bodySize = MaterialTheme.typography.bodyLarge.fontSize.value.coerceIn(15f, 24f)
    val shell = remember(palette, bodySize, selectionEnabled, clearSelectionAfterAction, selectionHighlight) {
        lessonShell(
            palette = palette,
            bodySize = bodySize,
            katexSource = KatexAsset.get(context),
            selectionEnabled = selectionEnabled,
            clearSelectionAfterAction = clearSelectionAfterAction,
            selectionColor = selectionColor(selectionHighlight)
        )
    }

    key(shell.hashCode()) {
        val webView = remember {
            LocalRendererWebView(context).apply {
                configureLocalRenderer(palette.background)
                addJavascriptInterface(SelectionBridge(onAskSelection), "AndroidSelection")
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
            onDispose {
                webView.removeJavascriptInterface("AndroidSelection")
                webView.disposeSafely()
            }
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
    onEditMessage: (Long, String) -> Unit = { _, _ -> },
    onRetryMessage: (Long) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (messages.isEmpty()) return
    val context = LocalContext.current
    val palette = currentWebPalette()
    val bodySize = MaterialTheme.typography.bodyLarge.fontSize.value.coerceIn(15f, 21f)
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
                val item = JSONObject()
                    .put("id", message.id.toString())
                    .put("createdAt", message.createdAt)
                    .put("role", message.role)
                    .put("text", message.text)
                    .put("replyTo", message.replyTo.orEmpty())
                    .put("streaming", message.isStreaming)
                    .put("error", message.isError)
                    .put("attachments", JSONArray().apply {
                        message.attachments.forEach { attachment -> put(attachment.name) }
                    })
                message.quiz?.let { quiz ->
                    item.put(
                        "quiz",
                        JSONObject()
                            .put("title", quiz.title)
                            .put("isExam", quiz.isExam)
                            .put("questions", JSONArray().apply {
                                quiz.questions.forEach { question ->
                                    put(
                                        JSONObject()
                                            .put("type", question.type)
                                            .put("question", question.question)
                                            .put("answers", JSONArray(question.answers))
                                            .put("correctAnswer", question.correctAnswer)
                                    )
                                }
                            })
                    )
                }
                if (message.flashcards.isNotEmpty()) {
                    item.put("flashcards", JSONArray().apply {
                        message.flashcards.forEach { card -> put(JSONObject().put("front", card.front).put("back", card.back)) }
                    })
                }
                message.functionPlot?.let { plot ->
                    item.put(
                        "plot",
                        JSONObject()
                            .put("title", plot.title)
                            .put("subtitle", plot.subtitle)
                            .put("xMin", plot.xMin)
                            .put("xMax", plot.xMax)
                            .put("functions", JSONArray().apply {
                                plot.functions.forEach { function ->
                                    put(JSONObject().put("expression", function.expression).put("label", function.label).put("color", function.color))
                                }
                            })
                            .also { payload ->
                                plot.yMin?.let { payload.put("yMin", it) }
                                plot.yMax?.let { payload.put("yMax", it) }
                            }
                    )
                }
                put(item)
            }
        }.toString()
    }

    key(shell.hashCode()) {
        val webView = remember {
            LocalRendererWebView(context).apply {
                configureLocalRenderer(palette.background)
                contentDescription = "שיחת Pythi"
                addJavascriptInterface(ChatActionBridge(context, onEditMessage, onRetryMessage), "AndroidChat")
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
            onDispose {
                webView.removeJavascriptInterface("AndroidChat")
                webView.disposeSafely()
            }
        }
    }
}

private data class WebPalette(
    val background: String,
    val foreground: String,
    val surface: String,
    val muted: String,
    val outline: String,
    val error: String,
    val dark: Boolean
)

@Composable
private fun currentWebPalette() = WebPalette(
    background = MaterialTheme.colorScheme.background.toArgb().toHexColor(),
    foreground = MaterialTheme.colorScheme.onBackground.toArgb().toHexColor(),
    surface = MaterialTheme.colorScheme.surfaceVariant.toArgb().toHexColor(),
    muted = MaterialTheme.colorScheme.onSurfaceVariant.toArgb().toHexColor(),
    outline = MaterialTheme.colorScheme.outlineVariant.toArgb().toHexColor(),
    error = MaterialTheme.colorScheme.error.toArgb().toHexColor(),
    dark = MaterialTheme.colorScheme.background.luminance() < .5f
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

    override fun startActionMode(callback: ActionMode.Callback?, type: Int): ActionMode? =
        callback?.let { super.startActionMode(SelectionOnlyActionModeCallback(it), type) }

    @Suppress("DEPRECATION")
    override fun startActionMode(callback: ActionMode.Callback?): ActionMode? =
        callback?.let { super.startActionMode(SelectionOnlyActionModeCallback(it)) }

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

private class SelectionOnlyActionModeCallback(
    private val delegate: ActionMode.Callback
) : ActionMode.Callback {
    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        val created = delegate.onCreateActionMode(mode, menu)
        menu.clear()
        return created
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
        delegate.onPrepareActionMode(mode, menu)
        menu.clear()
        return true
    }

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean = false
    override fun onDestroyActionMode(mode: ActionMode) = delegate.onDestroyActionMode(mode)
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

private fun lessonShell(
    palette: WebPalette,
    bodySize: Float,
    katexSource: String,
    selectionEnabled: Boolean,
    clearSelectionAfterAction: Boolean,
    selectionColor: String
) = """
    <!doctype html>
    <html dir="rtl" lang="he">
    <head>
      <meta charset="utf-8" />
      <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=3,user-scalable=yes" />
      <link rel="stylesheet" href="katex.min.css" />
      <style>
        :root { color-scheme:${if (palette.dark) "dark" else "light"}; --blue:#1473ff; --fg:${palette.foreground}; --muted:${palette.muted};
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
        ::selection { background:$selectionColor; }
        #selection-popover { position:fixed; z-index:9999; display:none; direction:ltr; transform:translate(-50%,-100%);
          width:min(350px,calc(100vw - 20px)); min-height:58px; align-items:center; gap:8px; padding:8px;
          overflow:hidden; isolation:isolate;
          border:1px solid color-mix(in srgb,var(--outline) 66%,#ffffff 34%); border-radius:29px;
          background:linear-gradient(145deg,color-mix(in srgb,var(--surface) 82%,#ffffff 18%),
                                             color-mix(in srgb,var(--surface) 88%,transparent)); color:var(--fg);
          box-shadow:0 20px 52px #00000042,inset 0 1px 0 #ffffff4a,inset 0 -1px 0 #00000014;
          -webkit-backdrop-filter:blur(30px) saturate(185%); backdrop-filter:blur(30px) saturate(185%);
          animation:selectionPopoverIn .2s cubic-bezier(.18,.88,.2,1.12) both; }
        #selection-popover::before { content:""; position:absolute; z-index:-1; inset:-40% -10%; pointer-events:none;
          background:radial-gradient(circle at 18% 12%,#ffffff36,transparent 36%),
                     radial-gradient(circle at 85% 100%,#1473ff1c,transparent 42%); }
        #selection-question { direction:rtl; text-align:right; min-width:0; flex:1; height:42px; padding:0 11px;
          border:0; outline:0; background:transparent; color:var(--fg); caret-color:var(--blue);
          font:520 15.5px/1.25 -apple-system,BlinkMacSystemFont,"Segoe UI",Arial,sans-serif; }
        #selection-question::placeholder { color:var(--muted); opacity:.9; }
        #selection-send { appearance:none; flex:none; width:42px; height:42px; display:grid; place-items:center;
          border:1px solid #ffffff42; border-radius:50%; background:linear-gradient(155deg,#2788ff,#0867ed);
          color:white; box-shadow:0 7px 19px #1473ff66,inset 0 1px 0 #ffffff52;
          transition:transform .14s ease,filter .14s ease; }
        #selection-send:active { transform:scale(.9); filter:brightness(.92); }
        #selection-send svg { width:22px; height:22px; fill:none; stroke:currentColor; stroke-width:2.25;
                              stroke-linecap:round; stroke-linejoin:round; }
        @keyframes selectionPopoverIn { from { opacity:0; transform:translate(-50%,-90%) scale(.9); }
                                        to { opacity:1; transform:translate(-50%,-100%) scale(1); } }
      </style>
      <script>$katexSource</script>
      <script>${sharedRendererScript()}
        window.renderLesson = function(raw) {
          document.getElementById('lesson').innerHTML = renderMarkdown(raw || '');
        };
        document.addEventListener('DOMContentLoaded', function() {
          const popover = document.getElementById('selection-popover');
          const questionInput = document.getElementById('selection-question');
          let selectedText = '';
          let selectionTimer = 0;
          function rememberSelection() {
            const selection = window.getSelection();
            const text = selection ? selection.toString().trim() : '';
            if (text && selection.rangeCount) selectedText = text;
          }
          function updateSelectionPopover(event) {
            if (event && popover.contains(event.target)) return;
            if (document.activeElement === questionInput) return;
            if (!${selectionEnabled}) { popover.style.display = 'none'; return; }
            window.clearTimeout(selectionTimer);
            selectionTimer = window.setTimeout(function() {
              const selection = window.getSelection();
              const text = selection ? selection.toString().trim() : '';
              if (!text || !selection.rangeCount) { popover.style.display = 'none'; return; }
              const rect = selection.getRangeAt(0).getBoundingClientRect();
              selectedText = text;
              const half = Math.min(172, Math.max(120, (window.innerWidth - 24) / 2));
              popover.style.left = Math.max(half + 12, Math.min(window.innerWidth - half - 12, rect.left + rect.width / 2)) + 'px';
              popover.style.top = Math.max(68, rect.top - 10) + 'px';
              popover.style.display = 'flex';
            }, 24);
          }
          function sendSelectionQuestion() {
            const question = questionInput.value.trim();
            if (!selectedText || !question) return;
            if (window.AndroidSelection) window.AndroidSelection.ask(selectedText, question);
            questionInput.value = '';
            popover.style.display = 'none';
            if (${clearSelectionAfterAction}) window.getSelection().removeAllRanges();
          }
          document.addEventListener('selectionchange', function(event) {
            rememberSelection();
            updateSelectionPopover(event);
          }, true);
          document.addEventListener('touchstart', function(event) {
            if (!popover.contains(event.target)) popover.style.display = 'none';
          }, true);
          window.addEventListener('touchend', updateSelectionPopover, true);
          window.addEventListener('pointerup', updateSelectionPopover, true);
          window.addEventListener('mouseup', updateSelectionPopover, true);
          document.getElementById('selection-send').addEventListener('click', sendSelectionQuestion);
          questionInput.addEventListener('keydown', function(event) {
            if (event.key === 'Enter') { event.preventDefault(); sendSelectionQuestion(); }
          });
        });
      </script>
    </head>
    <body><div id="selection-popover" role="dialog" aria-label="שאלו את פיתי על הטקסט המסומן">
      <button id="selection-send" type="button" aria-label="שליחה"><svg viewBox="0 0 24 24" aria-hidden="true">
        <path d="M12 19V5"></path><path d="M6.5 10.5 12 5l5.5 5.5"></path>
      </svg></button>
      <input id="selection-question" type="text" inputmode="text" enterkeyhint="send" placeholder="שאלו את פיתי על הקטע…" />
    </div><article id="lesson" aria-live="polite"></article></body>
    </html>
""".trimIndent()

private class SelectionBridge(private val onAsk: (String, String) -> Unit) {
    @JavascriptInterface
    fun ask(text: String, question: String) {
        Handler(Looper.getMainLooper()).post { onAsk(text, question) }
    }
}

private class ChatActionBridge(
    private val context: Context,
    private val onEditMessage: (Long, String) -> Unit,
    private val onRetryMessage: (Long) -> Unit
) {
    @JavascriptInterface
    fun copy(text: String) {
        Handler(Looper.getMainLooper()).post {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Pythi", text))
        }
    }

    @JavascriptInterface
    fun share(text: String) {
        Handler(Looper.getMainLooper()).post {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            val chooser = Intent.createChooser(send, "שיתוף התשובה של פיתי")
            if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        }
    }

    @JavascriptInterface
    fun edit(id: String, text: String) {
        id.toLongOrNull()?.let { messageId ->
            Handler(Looper.getMainLooper()).post { onEditMessage(messageId, text) }
        }
    }

    @JavascriptInterface
    fun retry(id: String) {
        id.toLongOrNull()?.let { messageId ->
            Handler(Looper.getMainLooper()).post { onRetryMessage(messageId) }
        }
    }
}

private fun selectionColor(id: String): String = when (id) {
    "sunset" -> "#fb923c66"
    "mint" -> "#34d39966"
    "sky" -> "#38bdf866"
    "butter" -> "#facc1566"
    "lavender" -> "#a78bfa66"
    "teal" -> "#2dd4bf66"
    "peach" -> "#fb718566"
    "rose" -> "#f472b666"
    else -> "#1473ff52"
}

private fun chatShell(palette: WebPalette, bodySize: Float, katexSource: String) = """
    <!doctype html>
    <html dir="rtl" lang="he">
    <head>
      <meta charset="utf-8" />
      <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=2,user-scalable=yes" />
      <link rel="stylesheet" href="katex.min.css" />
      <style>
        :root { color-scheme:${if (palette.dark) "dark" else "light"}; --blue:#1473ff; --fg:${palette.foreground}; --muted:${palette.muted};
                --surface:${palette.surface}; --outline:${palette.outline}; --error:${palette.error}; }
        * { box-sizing:border-box; }
        html,body { min-height:100%; background:${palette.background}; }
        body { margin:0; padding:14px 12px 28px; color:var(--fg);
               font:${bodySize}px/1.65 -apple-system,BlinkMacSystemFont,"Segoe UI",Arial,sans-serif;
               -webkit-font-smoothing:antialiased; }
        #messages { display:flex; flex-direction:column; gap:18px; }
        .message { width:fit-content; max-width:78%; padding:12px 18px; border-radius:26px;
                   overflow-wrap:anywhere; border:1px solid transparent; }
        .message.user { align-self:flex-end; color:var(--fg);
                        background:color-mix(in srgb,var(--surface) 78%,var(--fg) 22%);
                        border-color:color-mix(in srgb,var(--outline) 72%,transparent);
                        box-shadow:0 8px 26px #00000024,inset 0 1px 0 #ffffff14;
                        -webkit-user-select:none; user-select:none; }
        .message.model { align-self:flex-start; width:100%; max-width:100%; padding:3px 1px; background:transparent; border:0;
                         direction:rtl; text-align:right; }
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
        .typing { display:flex; width:max-content; margin-left:auto; margin-right:0; direction:ltr; gap:4px; padding:4px; }
        .typing i { width:6px; height:6px; border-radius:50%; background:var(--muted); animation:pulse 1s infinite; }
        .typing i:nth-child(2) { animation-delay:.12s; } .typing i:nth-child(3) { animation-delay:.24s; }
        .cursor { display:inline-block; width:2px; height:1.05em; margin-inline-start:3px; vertical-align:-.14em;
                  background:var(--blue); animation:blink .85s step-end infinite; }
        .attachment-row { display:flex; flex-wrap:wrap; gap:6px; margin-top:7px; }
        .attachment { max-width:190px; padding:5px 9px; border-radius:999px; background:#ffffff22;
                      white-space:nowrap; overflow:hidden; text-overflow:ellipsis; font-size:.78em; }
        .reply-quote { margin:0 0 8px; padding:8px 10px; border-radius:13px; border-right:3px solid var(--blue);
                       background:color-mix(in srgb,var(--surface) 72%,transparent); color:inherit; opacity:.92;
                       font-size:.82em; line-height:1.4;
                       white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
        .message-actions { width:100%; display:flex; direction:ltr; justify-content:flex-start; gap:2px; margin-top:10px; }
        .message-action { appearance:none; width:36px; height:36px; display:grid; place-items:center; padding:0;
                          border:0; border-radius:11px; background:transparent; color:var(--muted);
                          transition:background .14s ease,color .14s ease,transform .14s ease; }
        .message-action svg { width:22px; height:22px; display:block; fill:none; stroke:currentColor;
                              stroke-width:1.8; stroke-linecap:round; stroke-linejoin:round; }
        .message-action:active { background:color-mix(in srgb,var(--surface) 82%,transparent); color:var(--fg); }
        .message-action:active { transform:scale(.9); }
        #message-menu-backdrop { position:fixed; inset:0; z-index:9997; background:transparent; }
        #message-menu { position:fixed; z-index:9998; width:246px; overflow:hidden; direction:ltr;
                        border:1px solid color-mix(in srgb,var(--outline) 82%,transparent); border-radius:27px;
                        background:color-mix(in srgb,var(--surface) 94%,transparent); color:var(--fg);
                        box-shadow:0 24px 70px #00000060,inset 0 1px 0 #ffffff20;
                        -webkit-backdrop-filter:blur(30px) saturate(170%); backdrop-filter:blur(30px) saturate(170%);
                        animation:menuIn .18s cubic-bezier(.2,.82,.2,1) both; }
        .menu-time { padding:20px 20px 10px; color:var(--muted); font-size:.86em; direction:ltr; text-align:left; }
        .menu-item { appearance:none; width:100%; min-height:62px; display:flex; align-items:center; gap:17px;
                     padding:10px 20px; border:0; background:transparent; color:var(--fg); text-align:left;
                     font:600 17px/1.25 -apple-system,BlinkMacSystemFont,"Segoe UI",Arial,sans-serif; }
        .menu-item:active { background:color-mix(in srgb,var(--fg) 9%,transparent); }
        .menu-icon { width:28px; height:28px; flex:none; display:grid; place-items:center; }
        .menu-icon svg { width:27px; height:27px; fill:none; stroke:currentColor; stroke-width:1.8;
                         stroke-linecap:round; stroke-linejoin:round; }
        .tool-card { width:100%; margin:12px 0 4px; overflow:hidden; border:1px solid var(--outline);
                     border-radius:20px; background:var(--surface); color:var(--fg);
                     box-shadow:0 10px 28px -24px #0f172a99; }
        .tool-head { padding:14px 16px; border-bottom:1px solid var(--outline);
                     background:color-mix(in srgb,var(--surface) 88%,var(--fg) 12%); }
        .tool-title { margin:0; font-size:1.05em; font-weight:800; }
        .tool-meta { margin-top:3px; color:var(--muted); font-size:.8em; }
        .tool-body { padding:16px; }
        .quiz-question { font-weight:700; line-height:1.55; margin-bottom:13px; }
        .quiz-options { display:grid; gap:8px; }
        .quiz-option,.quiz-nav,.quiz-submit,.flash-nav { appearance:none; width:100%; border:1px solid var(--outline);
                     border-radius:15px; padding:11px 13px; color:var(--fg); background:transparent;
                     text-align:right; font:inherit; }
        .quiz-option.selected { border-color:var(--blue); background:#1473ff18; }
        .quiz-option.correct { border-color:#22c55e; background:#22c55e18; }
        .quiz-option.wrong { border-color:#ef4444; background:#ef444418; }
        .quiz-answer { width:100%; min-height:46px; border:1px solid var(--outline); border-radius:15px;
                       padding:11px 13px; color:var(--fg); background:transparent; font:inherit; }
        textarea.quiz-answer { min-height:100px; resize:vertical; }
        .quiz-controls { display:flex; gap:8px; align-items:center; margin-top:14px; }
        .quiz-nav { width:auto; flex:1; text-align:center; }
        .quiz-submit { border-color:var(--blue); background:var(--blue); color:white; font-weight:750; text-align:center; }
        .quiz-result { text-align:center; padding:10px; }
        .quiz-score { color:var(--blue); font-size:2.3em; line-height:1; font-weight:900; }
        .flash-wrap { width:100%; }
        .flash-card { appearance:none; position:relative; width:100%; min-height:190px; display:grid;
                      place-items:center; padding:44px 22px 38px; border:1px solid color-mix(in srgb,var(--blue) 32%,var(--outline));
                      border-radius:20px; text-align:center; overflow:hidden; cursor:pointer;
                      transition:background .18s ease,box-shadow .18s ease;
                      background:linear-gradient(145deg,color-mix(in srgb,var(--surface) 92%,var(--blue) 8%),var(--surface));
                      color:var(--fg); font:inherit; box-shadow:inset 0 1px 0 #ffffff12; }
        .flash-card.back { background:linear-gradient(145deg,#2563eb,#4338ca); color:white; border-color:transparent; }
        .flash-label { position:absolute; top:14px; inset-inline-start:16px; opacity:.72; font-size:.72em; font-weight:800; }
        .flash-content { width:100%; font-size:1.16em; font-weight:750; line-height:1.55; overflow-wrap:anywhere; }
        .flash-hint { position:absolute; bottom:13px; opacity:.65; font-size:.72em; }
        .flash-controls { display:flex; gap:10px; align-items:center; margin-top:12px; }
        .flash-nav { width:42px; height:42px; padding:0; border-radius:50%; text-align:center; font-size:1.25em; }
        .flash-count { flex:1; text-align:center; color:var(--muted); font-size:.82em; }
        .plot-canvas { display:block; width:100%; height:240px; border-radius:17px; background:color-mix(in srgb,var(--surface) 70%,transparent); }
        .plot-legend { display:flex; direction:ltr; flex-wrap:wrap; gap:7px 12px; margin-top:11px; }
        .plot-row { direction:ltr; display:flex; align-items:center; gap:7px; font-size:.83em; color:var(--muted); }
        .plot-dot { width:9px; height:9px; border-radius:50%; flex:none; }
        ${if (palette.dark) ".flash-card:not(.back) { background:linear-gradient(145deg,#172033,#25235a); color:var(--fg); }" else ""}
        @keyframes pulse { 50% { opacity:.28; transform:translateY(-2px); } }
        @keyframes blink { 50% { opacity:0; } }
        @keyframes messageIn { from { opacity:0; transform:translateY(18px) scale(.94); }
                               to { opacity:1; transform:translateY(0) scale(1); } }
        @keyframes modelIn { from { opacity:0; transform:translateY(9px); } to { opacity:1; transform:none; } }
        @keyframes menuIn { from { opacity:0; transform:scale(.88) translateY(8px); }
                            to { opacity:1; transform:scale(1) translateY(0); } }
        .message.enter.user { transform-origin:bottom right; animation:messageIn .32s cubic-bezier(.18,.88,.22,1.16) both; }
        .message.enter.model { animation:modelIn .25s ease-out .07s both; }
        @media (prefers-reduced-motion:reduce) { .message.enter,#message-menu { animation:none; } }
      </style>
      <script>$katexSource</script>
      <script>${sharedRendererScript()}
        const quizState = Object.create(null);
        const flashState = Object.create(null);

        function renderQuiz(host, quiz, messageId) {
          const questions = Array.isArray(quiz.questions) ? quiz.questions : [];
          if (!questions.length) return;
          const state = quizState[messageId] || (quizState[messageId] = { index:0, answers:{}, submitted:false });
          const card = document.createElement('section');
          card.className = 'tool-card';
          host.appendChild(card);
          function draw() {
            const index = Math.max(0, Math.min(state.index, questions.length - 1));
            const question = questions[index] || {};
            if (state.submitted) {
              let correct = 0;
              questions.forEach(function(item, questionIndex) {
                const answer = state.answers[questionIndex];
                const type = item.type || 'mcq';
                if (type === 'mcq' && Number(answer) === Number(item.correctAnswer)) correct += 1;
                else if (type === 'fill' && String(answer || '').trim().toLowerCase() === String(item.correctAnswer || '').trim().toLowerCase()) correct += 1;
                else if (type === 'open' && String(answer || '').trim()) correct += 1;
              });
              const percent = Math.round(correct * 100 / questions.length);
              card.innerHTML = '<div class="tool-head"><h3 class="tool-title">' + escapeHtml(quiz.title || 'בוחן עם פיתי') +
                '</h3><div class="tool-meta">התוצאות שלך</div></div><div class="tool-body quiz-result"><div class="quiz-score">' +
                percent + '%</div><p>' + correct + ' תשובות מתוך ' + questions.length + '</p><button class="quiz-submit" data-reset>נסו שוב</button></div>';
              card.querySelector('[data-reset]').addEventListener('click', function() {
                state.index = 0; state.answers = {}; state.submitted = false; draw();
              });
              return;
            }
            card.innerHTML = '<div class="tool-head"><h3 class="tool-title">' + escapeHtml(quiz.title || 'בוחן עם פיתי') +
              '</h3><div class="tool-meta">שאלה ' + (index + 1) + ' מתוך ' + questions.length + '</div></div>' +
              '<div class="tool-body"><div class="quiz-question"></div><div class="quiz-options"></div><div class="quiz-controls">' +
              '<button class="quiz-nav" data-prev>הקודמת</button><button class="quiz-nav" data-next>' +
              (index === questions.length - 1 ? 'סיום' : 'הבאה') + '</button></div></div>';
            card.querySelector('.quiz-question').innerHTML = renderMarkdown(question.question || '');
            const options = card.querySelector('.quiz-options');
            const type = question.type || 'mcq';
            if (type === 'mcq') {
              (question.answers || []).forEach(function(answer, answerIndex) {
                const button = document.createElement('button');
                button.className = 'quiz-option' + (Number(state.answers[index]) === answerIndex ? ' selected' : '');
                button.innerHTML = renderMarkdown(answer || '');
                button.addEventListener('click', function() { state.answers[index] = answerIndex; draw(); });
                options.appendChild(button);
              });
            } else {
              const input = document.createElement(type === 'open' ? 'textarea' : 'input');
              input.className = 'quiz-answer';
              input.value = state.answers[index] || '';
              input.placeholder = type === 'open' ? 'כתבו את תשובתכם כאן…' : 'הקלידו את התשובה…';
              input.addEventListener('input', function() { state.answers[index] = input.value; });
              options.appendChild(input);
            }
            const previous = card.querySelector('[data-prev]');
            previous.disabled = index === 0;
            previous.addEventListener('click', function() { state.index = Math.max(0, index - 1); draw(); });
            card.querySelector('[data-next]').addEventListener('click', function() {
              if (index === questions.length - 1) state.submitted = true;
              else state.index = index + 1;
              draw();
            });
          }
          draw();
        }

        function renderFlashcards(host, cards, messageId) {
          if (!Array.isArray(cards) || !cards.length) return;
          const state = flashState[messageId] || (flashState[messageId] = { index:0, flipped:false });
          const wrapper = document.createElement('section');
          wrapper.className = 'tool-card';
          host.appendChild(wrapper);
          function draw() {
            const card = cards[state.index] || {};
            const label = state.flipped ? 'תשובה / הגדרה' : 'שאלה / מושג';
            const value = state.flipped ? card.back : card.front;
            wrapper.innerHTML = '<div class="tool-head"><h3 class="tool-title">כרטיסיות עם פיתי</h3><div class="tool-meta">כרטיס ' +
              (state.index + 1) + ' מתוך ' + cards.length + '</div></div><div class="tool-body flash-wrap"><button class="flash-card' +
              (state.flipped ? ' back' : '') + '" data-flip><span class="flash-label">' + label + '</span><span class="flash-content"></span>' +
              '<span class="flash-hint">לחצו להפיכה</span></button><div class="flash-controls"><button class="flash-nav" data-prev>‹</button>' +
              '<span class="flash-count">' + (state.index + 1) + ' / ' + cards.length + '</span><button class="flash-nav" data-next>›</button></div></div>';
            wrapper.querySelector('.flash-content').innerHTML = renderMarkdown(value || '');
            wrapper.querySelector('[data-flip]').addEventListener('click', function() { state.flipped = !state.flipped; draw(); });
            const previous = wrapper.querySelector('[data-prev]');
            const next = wrapper.querySelector('[data-next]');
            previous.disabled = state.index === 0;
            next.disabled = state.index === cards.length - 1;
            previous.addEventListener('click', function() { state.index -= 1; state.flipped = false; draw(); });
            next.addEventListener('click', function() { state.index += 1; state.flipped = false; draw(); });
          }
          draw();
        }

        function renderPlot(host, plot) {
          if (!plot || !Array.isArray(plot.functions) || !plot.functions.length) return;
          const card = document.createElement('section');
          card.className = 'tool-card';
          card.innerHTML = '<div class="tool-head"><h3 class="tool-title">' + escapeHtml(plot.title || 'גרף פונקציות') +
            '</h3><div class="tool-meta">' + escapeHtml(plot.subtitle || 'הפונקציות שביקשתם') +
            '</div></div><div class="tool-body"><canvas class="plot-canvas"></canvas><div class="plot-legend"></div></div>';
          const body = card.querySelector('.tool-body');
          const legend = body.querySelector('.plot-legend');
          const palette = ['#1473ff','#8b5cf6','#ef4444','#10b981','#f59e0b'];
          plot.functions.forEach(function(fn, index) {
            const color = /^#[0-9a-f]{3,8}$/i.test(fn.color || '') ? fn.color : palette[index % palette.length];
            const row = document.createElement('div'); row.className = 'plot-row';
            row.innerHTML = '<span class="plot-dot" style="background:' + color + '"></span><span>' + escapeHtml(fn.label || fn.expression || '') + '</span>';
            legend.appendChild(row);
          });
          host.appendChild(card);
          requestAnimationFrame(function() { drawFunctionPlot(body.querySelector('.plot-canvas'), plot, palette); });
        }

        function compilePlotExpression(expression) {
          let value = String(expression || '').trim().toLowerCase();
          if (!value || !/^[0-9a-z+\-*/^().,\s]+$/.test(value)) return null;
          const names = value.match(/[a-z]+/g) || [];
          const allowed = ['x','pi','e','sin','cos','tan','sqrt','abs','ln','log','exp','min','max','pow'];
          if (names.some(function(name) { return allowed.indexOf(name) < 0; })) return null;
          value = value.replace(/\^/g, '**').replace(/\bpi\b/g, 'Math.PI').replace(/\be\b/g, 'Math.E');
          ['sin','cos','tan','sqrt','abs','exp','min','max','pow'].forEach(function(name) {
            value = value.replace(new RegExp('\\b' + name + '\\b', 'g'), 'Math.' + name);
          });
          value = value.replace(/\bln\b/g, 'Math.log').replace(/\blog\b/g, 'Math.log10');
          try { return new Function('x', '"use strict";return (' + value + ');'); } catch (_) { return null; }
        }

        function drawFunctionPlot(canvas, plot, palette) {
          if (!canvas) return;
          const ratio = Math.min(window.devicePixelRatio || 1, 2);
          const width = Math.max(260, canvas.clientWidth || 320);
          const height = 240;
          canvas.width = Math.round(width * ratio); canvas.height = Math.round(height * ratio);
          const context = canvas.getContext('2d'); context.scale(ratio, ratio);
          const xMin = Number.isFinite(Number(plot.xMin)) ? Number(plot.xMin) : -10;
          const xMax = Number.isFinite(Number(plot.xMax)) ? Number(plot.xMax) : 10;
          const compiled = plot.functions.map(function(fn) { return compilePlotExpression(fn.expression); });
          const samples = [];
          compiled.forEach(function(fn) {
            const values = [];
            for (let pixel = 0; pixel <= width; pixel += 2) {
              const x = xMin + (xMax - xMin) * pixel / width;
              let y = fn ? Number(fn(x)) : NaN;
              if (!Number.isFinite(y) || Math.abs(y) > 1e7) y = NaN;
              values.push([pixel, y]);
            }
            samples.push(values);
          });
          const finite = samples.flat().map(function(point) { return point[1]; }).filter(Number.isFinite).sort(function(a,b) { return a-b; });
          let yMin = Number.isFinite(Number(plot.yMin)) ? Number(plot.yMin) : (finite.length ? finite[Math.floor(finite.length * .04)] : -10);
          let yMax = Number.isFinite(Number(plot.yMax)) ? Number(plot.yMax) : (finite.length ? finite[Math.floor(finite.length * .96)] : 10);
          if (!(yMax > yMin)) { yMin -= 1; yMax += 1; }
          const toY = function(y) { return height - (y - yMin) * height / (yMax - yMin); };
          context.clearRect(0,0,width,height); context.strokeStyle = getComputedStyle(document.documentElement).getPropertyValue('--outline'); context.lineWidth = 1;
          for (let step = 1; step < 5; step += 1) {
            const px = width * step / 5, py = height * step / 5;
            context.beginPath(); context.moveTo(px,0); context.lineTo(px,height); context.stroke();
            context.beginPath(); context.moveTo(0,py); context.lineTo(width,py); context.stroke();
          }
          context.strokeStyle = getComputedStyle(document.documentElement).getPropertyValue('--muted'); context.lineWidth = 1.4;
          if (xMin <= 0 && xMax >= 0) { const axisX = (0-xMin)*width/(xMax-xMin); context.beginPath(); context.moveTo(axisX,0); context.lineTo(axisX,height); context.stroke(); }
          if (yMin <= 0 && yMax >= 0) { const axisY = toY(0); context.beginPath(); context.moveTo(0,axisY); context.lineTo(width,axisY); context.stroke(); }
          samples.forEach(function(values, index) {
            const raw = plot.functions[index] || {};
            context.strokeStyle = /^#[0-9a-f]{3,8}$/i.test(raw.color || '') ? raw.color : palette[index % palette.length];
            context.lineWidth = 2.4; context.beginPath(); let drawing = false;
            values.forEach(function(point) {
              const py = toY(point[1]);
              if (!Number.isFinite(py) || py < -height * 2 || py > height * 3) { drawing = false; return; }
              if (!drawing) { context.moveTo(point[0], py); drawing = true; } else context.lineTo(point[0], py);
            });
            context.stroke();
          });
        }

        function chatBridge(method) {
          const args = Array.prototype.slice.call(arguments, 1);
          if (!window.AndroidChat) return;
          if (method === 'copy') window.AndroidChat.copy(String(args[0] || ''));
          else if (method === 'share') window.AndroidChat.share(String(args[0] || ''));
          else if (method === 'edit') window.AndroidChat.edit(String(args[0] || ''), String(args[1] || ''));
          else if (method === 'retry') window.AndroidChat.retry(String(args[0] || ''));
        }

        function iconSvg(name) {
          const open = '<svg viewBox="0 0 24 24" aria-hidden="true">';
          if (name === 'copy') return open + '<rect x="8" y="8" width="12" height="12" rx="3"></rect><rect x="4" y="4" width="12" height="12" rx="3"></rect></svg>';
          if (name === 'select') return open + '<rect x="5" y="3" width="14" height="18" rx="3"></rect><path d="M9 8h6M9 12h6M9 16h4"></path></svg>';
          if (name === 'edit') return open + '<path d="M4 20l4.2-1 10.4-10.4a2.1 2.1 0 0 0-3-3L5.2 16 4 20z"></path><path d="M13.8 6.4l3.8 3.8"></path></svg>';
          if (name === 'retry') return open + '<path d="M20 7v5h-5"></path><path d="M19 12a7 7 0 1 0-1.9 4.8"></path></svg>';
          if (name === 'share') return open + '<circle cx="18" cy="5" r="2.5"></circle><circle cx="6" cy="12" r="2.5"></circle><circle cx="18" cy="19" r="2.5"></circle><path d="M8.2 10.8l7.6-4.5M8.2 13.2l7.6 4.5"></path></svg>';
          return open + '</svg>';
        }

        function actionButton(icon, label, action) {
          const button = document.createElement('button');
          button.type = 'button'; button.className = 'message-action'; button.innerHTML = iconSvg(icon);
          button.setAttribute('aria-label', label); button.title = label;
          button.addEventListener('click', function(event) { event.stopPropagation(); action(); });
          return button;
        }

        function closeMessageMenu() {
          const menu = document.getElementById('message-menu');
          const backdrop = document.getElementById('message-menu-backdrop');
          if (menu) menu.remove();
          if (backdrop) backdrop.remove();
        }

        function showUserMessageMenu(bubble, message, clientX, clientY) {
          closeMessageMenu();
          const backdrop = document.createElement('div');
          backdrop.id = 'message-menu-backdrop';
          backdrop.addEventListener('click', closeMessageMenu);
          const menu = document.createElement('section');
          menu.id = 'message-menu'; menu.setAttribute('role', 'menu');
          const messageDate = new Date(Number(message.createdAt) || Date.now());
          const now = new Date();
          const sameDay = messageDate.toDateString() === now.toDateString();
          const dayLabel = sameDay ? 'Today' : messageDate.toLocaleDateString('en-US', {month:'short',day:'numeric'});
          const when = dayLabel + ', ' + messageDate.toLocaleTimeString('en-US', {hour:'numeric',minute:'2-digit'});
          const time = document.createElement('div'); time.className = 'menu-time'; time.textContent = when;
          menu.appendChild(time);
          function item(icon, label, action) {
            const button = document.createElement('button');
            button.type = 'button'; button.className = 'menu-item';
            button.innerHTML = '<span class="menu-icon">' + iconSvg(icon) + '</span><span>' + label + '</span>';
            button.addEventListener('click', function(event) { event.stopPropagation(); closeMessageMenu(); action(); });
            menu.appendChild(button);
          }
          item('copy', 'Copy', function() { chatBridge('copy', message.text || ''); });
          item('select', 'Select text', function() {
            const content = bubble.querySelector('.message-content');
            if (!content) return;
            bubble.style.webkitUserSelect = 'text'; bubble.style.userSelect = 'text';
            const range = document.createRange(); range.selectNodeContents(content);
            const selection = window.getSelection(); selection.removeAllRanges(); selection.addRange(range);
          });
          item('edit', 'Edit message', function() { chatBridge('edit', String(message.id || ''), message.text || ''); });
          document.body.appendChild(backdrop); document.body.appendChild(menu);
          const width = 246, height = 236;
          menu.style.left = Math.max(10, Math.min(window.innerWidth - width - 10, clientX - width / 2)) + 'px';
          menu.style.top = Math.max(10, Math.min(window.innerHeight - height - 10, clientY - height / 2)) + 'px';
        }

        function installUserLongPress(bubble, message) {
          let timer = 0, startX = 0, startY = 0;
          function cancel() { if (timer) window.clearTimeout(timer); timer = 0; }
          bubble.addEventListener('touchstart', function(event) {
            const point = event.touches[0]; if (!point) return;
            startX = point.clientX; startY = point.clientY; cancel();
            timer = window.setTimeout(function() { timer = 0; showUserMessageMenu(bubble, message, startX, startY); }, 480);
          }, {passive:true});
          bubble.addEventListener('touchmove', function(event) {
            const point = event.touches[0];
            if (point && (Math.abs(point.clientX - startX) > 9 || Math.abs(point.clientY - startY) > 9)) cancel();
          }, {passive:true});
          bubble.addEventListener('touchend', cancel, {passive:true});
          bubble.addEventListener('touchcancel', cancel, {passive:true});
          bubble.addEventListener('contextmenu', function(event) {
            event.preventDefault(); cancel(); showUserMessageMenu(bubble, message, event.clientX, event.clientY);
          });
        }

        const renderedMessageIds = new Set();
        let renderedOnce = false;
        let pendingMessages = null;
        let messageFrame = 0;
        window.renderMessages = function(messages) {
          pendingMessages = messages || [];
          if (messageFrame) return;
          messageFrame = requestAnimationFrame(function() {
          messageFrame = 0;
          const messages = pendingMessages;
          const root = document.getElementById('messages');
          const preservedUserBubbles = Object.create(null);
          root.querySelectorAll('.message.user[data-message-id]').forEach(function(node) {
            preservedUserBubbles[node.getAttribute('data-message-id')] = node;
            node.remove();
          });
          root.innerHTML = '';
          (messages || []).forEach(function(message) {
            const messageId = String(message.id || '');
            const preservedUser = preservedUserBubbles[messageId];
            if (preservedUser) {
              root.appendChild(preservedUser);
              renderedMessageIds.add(messageId);
              return;
            }
            const entering = !renderedMessageIds.has(messageId) && (renderedOnce || messages.length <= 2);
            const bubble = document.createElement('section');
            bubble.className = 'message ' + (message.role === 'user' ? 'user' : 'model') +
              (message.error ? ' error' : '') + (entering ? ' enter' : '');
            bubble.setAttribute('data-message-id', messageId);
            bubble.setAttribute('dir', 'auto');
            if (message.replyTo) {
              const reply = document.createElement('div'); reply.className = 'reply-quote'; reply.textContent = message.replyTo;
              bubble.appendChild(reply);
            }
            const content = document.createElement('div'); content.className = 'message-content';
            if (!message.text && message.streaming) {
              content.innerHTML = '<span class="typing" aria-label="Pythi חושבת"><i></i><i></i><i></i></span>';
            } else {
              content.innerHTML = renderMarkdown(message.text || '') + (message.streaming ? '<span class="cursor"></span>' : '');
            }
            bubble.appendChild(content);
            if (Array.isArray(message.attachments) && message.attachments.length) {
              const attachments = document.createElement('div'); attachments.className = 'attachment-row';
              message.attachments.forEach(function(name) {
                const chip = document.createElement('span'); chip.className = 'attachment'; chip.textContent = '📎 ' + name; attachments.appendChild(chip);
              });
              bubble.appendChild(attachments);
            }
            if (message.quiz) renderQuiz(bubble, message.quiz, message.id || String(Math.random()));
            if (message.flashcards) renderFlashcards(bubble, message.flashcards, message.id || String(Math.random()));
            if (message.plot) renderPlot(bubble, message.plot);
            if (!message.streaming && message.role === 'model') {
              const actions = document.createElement('div'); actions.className = 'message-actions';
              actions.appendChild(actionButton('copy', 'העתקת התשובה', function() { chatBridge('copy', message.text || ''); }));
              actions.appendChild(actionButton('retry', 'ניסיון נוסף', function() { chatBridge('retry', String(message.id || '')); }));
              actions.appendChild(actionButton('share', 'שיתוף התשובה', function() { chatBridge('share', message.text || ''); }));
              bubble.appendChild(actions);
            }
            if (message.role === 'user') installUserLongPress(bubble, message);
            root.appendChild(bubble);
            renderedMessageIds.add(messageId);
          });
          renderedOnce = true;
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
