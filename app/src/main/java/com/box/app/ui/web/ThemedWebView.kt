package com.box.app.ui.web

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import com.box.app.R
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * 两阶段 WebView 预热器：
 *
 * 阶段 1（后台线程，立即）：CookieManager.getInstance() 触发 Chromium native library 加载
 * 阶段 2（主线程，空闲时）：预创建 WebView 实例并缓存，不加载任何 URL，零历史
 *
 * 使用时 take() 取出已初始化的 WebView，new WebView() 耗时从 ~500ms 降至 ~0ms。
 */
object WebViewPreloader {
    // 使用 WeakReference 避免静态持有 WebView 导致内存泄漏
    @Volatile
    private var prewarmedRef: java.lang.ref.WeakReference<WebView>? = null
    @Volatile
    private var providerLoaded = false

    /**
     * 在 Application.onCreate 调用。
     * 阶段 1 立即在后台线程加载 native library；
     * 阶段 2 在主线程 idle 时预创建 WebView 实例。
     */
    fun preload(context: Context) {
        if (providerLoaded) return
        // 阶段 1：后台线程加载 Chromium provider（不阻塞主线程）
        Thread {
            runCatching {
                CookieManager.getInstance()
                providerLoaded = true
            }
            // 阶段 2：provider 加载完成后，在主线程空闲时创建 WebView
            Handler(Looper.getMainLooper()).post {
                Looper.myQueue().addIdleHandler {
                    if (prewarmedRef?.get() == null) {
                        runCatching {
                            val wv = WebView(context.applicationContext).apply {
                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    @Suppress("DEPRECATION")
                                    databaseEnabled = true
                                    cacheMode = WebSettings.LOAD_DEFAULT
                                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                    useWideViewPort = true
                                    loadWithOverviewMode = true
                                    textZoom = 100
                                }
                                // 不加载任何 URL → 零历史，零网络请求
                            }
                            prewarmedRef = java.lang.ref.WeakReference(wv)
                        }
                    }
                    false
                }
            }
        }.start()
    }

    /**
     * 取出预热的 WebView（一次性）。取出后清除引用。
     * 若预热未完成或已被 GC 回收则返回 null，调用方 fallback 到 new WebView()。
     */
    fun take(): WebView? {
        val wv = prewarmedRef?.get()
        prewarmedRef = null
        return wv
    }

    /** 释放预热的 WebView（防止长期持有） */
    fun release() {
        val wv = prewarmedRef?.get()
        prewarmedRef = null
        wv?.destroy()
    }
}

// 静态缓存 JS 注入脚本，避免每次 onPageFinished 重建字符串
private val DOWNLOAD_HOOK_JS = """
(function(){
  if (window.__boxDownloadHookInstalled) return;
  window.__boxDownloadHookInstalled = true;
  function guessMimeFromName(name) {
    try {
      var ext = (name || '').split('.').pop().toLowerCase();
      if (ext === 'json') return 'application/json';
      if (ext === 'txt' || ext === 'log') return 'text/plain';
      if (ext === 'yaml' || ext === 'yml') return 'application/yaml';
    } catch(e) {}
    return '';
  }
  function sniffMimeFromText(t) {
    try {
      var s = (t || '').trim();
      if (!s) return '';
      if (s[0] === '{' || s[0] === '[') return 'application/json';
    } catch(e) {}
    return '';
  }
  async function readAsBase64AndMimeFromUrl(href) {
    if (href.startsWith('data:')) {
      try {
        var comma = href.indexOf(',');
        var meta = href.substring(5, comma > 0 ? comma : href.length);
        var m = meta.split(';')[0] || '';
        var b64 = comma > 0 ? href.substring(comma + 1) : '';
        return { b64: b64, mime: m };
      } catch(e) {
        return { b64: '', mime: '' };
      }
    }
    const res = await fetch(href);
    const blob = await res.blob();
    var mime = (blob && blob.type) ? blob.type : '';
    var b64 = await new Promise(function(resolve, reject){
      const reader = new FileReader();
      reader.onloadend = function(){
        try {
          var s = reader.result || '';
          var idx = s.indexOf(',');
          resolve(idx >= 0 ? s.substring(idx + 1) : s);
        } catch(e) { reject(e); }
      };
      reader.onerror = reject;
      reader.readAsDataURL(blob);
    });
    if (!mime) {
      try {
        var text = await blob.text();
        mime = sniffMimeFromText(text);
      } catch(e) {}
    }
    return { b64: b64, mime: mime };
  }
  const __origClick = HTMLAnchorElement.prototype.click;
  HTMLAnchorElement.prototype.click = function(){
    try {
      var dl = this.getAttribute('download') || '';
      var href = this.getAttribute('href') || this.href || '';
      if (dl && (href.startsWith('blob:') || href.startsWith('data:'))) {
        (async () => {
          try {
            var info = await readAsBase64AndMimeFromUrl(href);
            var mime = info.mime || guessMimeFromName(dl) || 'application/octet-stream';
            var b64 = info.b64 || '';
            if (window.BoxAndroid && window.BoxAndroid.saveBase64File) {
              window.BoxAndroid.saveBase64File(dl, mime, b64);
            }
          } catch(e) {}
        })();
        return;
      }
    } catch(e) {}
    return __origClick.apply(this, arguments);
  };
})();
""".trimIndent()

suspend fun clearWebViewAppData(context: Context): Boolean {
    return withContext(Dispatchers.Main) {
        runCatching {
            val cookieManager = CookieManager.getInstance()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                cookieManager.removeAllCookies(null)
                cookieManager.flush()
            } else {
                @Suppress("DEPRECATION")
                cookieManager.removeAllCookie()
            }

            runCatching { WebStorage.getInstance().deleteAllData() }

            WebView(context).apply {
                clearCache(true)
                clearHistory()
                clearFormData()
                clearSslPreferences()
                destroy()
            }
        }.isSuccess
    }
}

@Composable
@SuppressLint("SetJavaScriptEnabled")
fun ThemedWebView(
    url: String,
    isDark: Boolean,
    reloadKey: Int,
    sessionKey: Int = 0,
    backRequestKey: Int = 0,
    modifier: Modifier = Modifier,
    hideUntilCommitVisible: Boolean = true,
    resetHistoryOnUrlChange: Boolean = false,
    onPageFinished: ((String?) -> Unit)? = null,
    onTitleChange: ((String?) -> Unit)? = null,
    onWebError: ((String?) -> Unit)? = null,
    onCanGoBackChange: ((Boolean) -> Unit)? = null,
    injectJsOnPageFinished: String? = null,
) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var contentVisible by remember(url, isDark, hideUntilCommitVisible) { mutableStateOf(!hideUntilCommitVisible) }
    var clearHistoryAfterNextPageFinished by remember { mutableStateOf(false) }
    val surfaceColor = if (isDark) Color.Black else Color.White

    // Persist cookies across sessions (login state, etc.)
    val cookieManager = remember { CookieManager.getInstance() }
    DisposableEffect(cookieManager) {
        onDispose {
            runCatching { cookieManager.flush() }
        }
    }

    data class PendingSave(
        val displayName: String,
        val mimeType: String,
        val bytes: ByteArray
    )

    var pendingSave by remember { mutableStateOf<PendingSave?>(null) }

    data class PendingHttpDownload(
        val url: String,
        val fileName: String,
        val mimeType: String,
        val userAgent: String,
        val cookie: String?,
        val referer: String
    )

    var pendingHttpDownload by remember { mutableStateOf<PendingHttpDownload?>(null) }

    val saveFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val outUri = result.data?.data
        val payload = pendingSave
        val httpPayload = pendingHttpDownload
        pendingSave = null
        pendingHttpDownload = null

        if (outUri != null && httpPayload != null) {
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        val conn = (URL(httpPayload.url).openConnection() as HttpURLConnection).apply {
                            instanceFollowRedirects = true
                            setRequestProperty("User-Agent", httpPayload.userAgent)
                            setRequestProperty("Referer", httpPayload.referer)
                            httpPayload.cookie?.takeIf { it.isNotBlank() }?.let { setRequestProperty("Cookie", it) }
                        }

                        conn.inputStream.use { input ->
                            context.contentResolver.openOutputStream(outUri)?.use { output ->
                                input.copyTo(output)
                            } ?: throw IllegalStateException("Open output stream failed")
                        }
                        runCatching { conn.disconnect() }
                    }
                }.onFailure {
                    onWebError?.invoke("Download failed")
                }
            }
            return@rememberLauncherForActivityResult
        }

        if (outUri == null || payload == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openOutputStream(outUri)?.use { it.write(payload.bytes) }
        }.onFailure {
            onWebError?.invoke("Save failed")
        }
    }

    val jsBridge = remember {
        object {
            @JavascriptInterface
            fun saveBase64File(filename: String?, mimeType: String?, base64: String?) {
                val rawName = filename?.takeIf { it.isNotBlank() } ?: "export"
                val mime = mimeType?.takeIf { it.isNotBlank() } ?: "application/octet-stream"
                val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
                    ?: when (mime) {
                        "application/json" -> "json"
                        "text/plain" -> "txt"
                        else -> null
                    }
                    ?: "bin"
                val name = if (rawName.contains('.') || rawName.endsWith('.')) {
                    rawName.trimEnd('.')
                } else {
                    "$rawName.$ext"
                }
                val b64 = base64?.takeIf { it.isNotBlank() } ?: return
                val data = runCatching { Base64.decode(b64, Base64.DEFAULT) }.getOrNull() ?: return

                pendingSave = PendingSave(displayName = name, mimeType = mime, bytes = data)
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = mime
                    putExtra(Intent.EXTRA_TITLE, name)
                }

                // JS bridge callbacks may not be on the main thread; SAF launch must run on main.
                Handler(Looper.getMainLooper()).post {
                    runCatching { saveFileLauncher.launch(intent) }
                        .onFailure { onWebError?.invoke("Open save dialog failed") }
                }
            }
        }
    }

    val viewClient = remember(isDark, onPageFinished, onWebError, onCanGoBackChange, injectJsOnPageFinished) {
        object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val requestUrl = request?.url?.toString().orEmpty()
                if (requestUrl.isBlank()) return false
                view?.loadUrl(requestUrl)
                return true
            }

            override fun onPageCommitVisible(view: WebView?, url: String?) {
                contentVisible = true
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                contentVisible = true
                if (clearHistoryAfterNextPageFinished) {
                    runCatching { view?.clearHistory() }
                    clearHistoryAfterNextPageFinished = false
                }
                onWebError?.invoke(null)
                onPageFinished?.invoke(url)
                onCanGoBackChange?.invoke(view?.canGoBack() == true)

                // Ensure cookies are written to disk.
                runCatching { cookieManager.flush() }

                // Inject download hook（使用静态缓存 JS，避免每次重建字符串）
                view?.evaluateJavascript(DOWNLOAD_HOOK_JS, null)

                injectJsOnPageFinished?.let { js -> view?.evaluateJavascript(js, null) }
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                if (request?.isForMainFrame == true) {
                    onWebError?.invoke(error?.description?.toString()?.ifBlank { null })
                }
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?
            ) {
                if (request?.isForMainFrame == true) {
                    val code = errorResponse?.statusCode
                    if (code != null) onWebError?.invoke("HTTP $code")
                }
            }
        }
    }

    var pendingFileCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }

    fun normalizeAcceptType(raw: String?): String {
        val v = raw?.trim().orEmpty()
        if (v.isBlank() || v == "*/*") return "*/*"
        if (v.startsWith(".")) {
            val ext = v.removePrefix(".").lowercase()
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            return mime ?: "*/*"
        }
        return v
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val cb = pendingFileCallback
        if (cb != null) {
            val uris = ArrayList<Uri>()
            val data = result.data
            val clip = data?.clipData
            if (clip != null) {
                for (i in 0 until clip.itemCount) {
                    clip.getItemAt(i)?.uri?.let { uris.add(it) }
                }
            } else {
                data?.data?.let { uris.add(it) }
            }
            cb.onReceiveValue(uris.toTypedArray().takeIf { it.isNotEmpty() })
        }
        pendingFileCallback = null
    }

    val chromeClient = remember(onTitleChange) {
        object : WebChromeClient() {
            override fun onReceivedTitle(view: WebView?, title: String?) {
                onTitleChange?.invoke(title?.ifBlank { null })
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                pendingFileCallback?.onReceiveValue(null)
                pendingFileCallback = filePathCallback

                val intent = try {
                    fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                    }
                } catch (_: Throwable) {
                    Intent(Intent.ACTION_GET_CONTENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                    }
                }

                // Some sites pass extension like ".json" as typ; normalize to MIME.
                val accept = normalizeAcceptType(fileChooserParams?.acceptTypes?.firstOrNull())
                if (intent.type.isNullOrBlank() || intent.type?.startsWith(".") == true) {
                    intent.type = accept
                }

                val canHandle = intent.resolveActivity(context.packageManager) != null
                if (!canHandle) {
                    pendingFileCallback?.onReceiveValue(null)
                    pendingFileCallback = null
                    onWebError?.invoke("No file picker found")
                    return true
                }

                try {
                    filePickerLauncher.launch(intent)
                } catch (_: Throwable) {
                    pendingFileCallback?.onReceiveValue(null)
                    pendingFileCallback = null
                    onWebError?.invoke("Open file picker failed")
                }
                return true
            }
        }
    }

    key(isDark, sessionKey) {
        Box(modifier = modifier.fillMaxSize().background(surfaceColor)) {
            AndroidView(
                factory = { ctx ->
                    // 根据 isDark 选择亮/暗主题，确保 WebView 的 uiMode 与 App 设置一致
                    val themeRes = if (isDark) R.style.Theme_BoxReApp else R.style.Theme_BoxReApp_Light
                    val themedCtx = ContextThemeWrapper(ctx, themeRes)
                    // 优先取预热实例（已完成 Chromium 初始化），fallback 到新建
                    (WebViewPreloader.take() ?: WebView(themedCtx)).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )

                        // Prevent ModalBottomSheet from hijacking vertical scroll gestures
                        // while the user interacts with the WebView.
                        setOnTouchListener { v, event ->
                            when (event.actionMasked) {
                                MotionEvent.ACTION_DOWN,
                                MotionEvent.ACTION_MOVE -> v.parent?.requestDisallowInterceptTouchEvent(true)
                                MotionEvent.ACTION_UP,
                                MotionEvent.ACTION_CANCEL -> v.parent?.requestDisallowInterceptTouchEvent(false)
                            }
                            false
                        }

                        // Cookies
                        cookieManager.setAcceptCookie(true)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            cookieManager.setAcceptThirdPartyCookies(this, true)
                        }

                        addJavascriptInterface(jsBridge, "BoxAndroid")

                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            cacheMode = WebSettings.LOAD_DEFAULT
                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            allowFileAccess = true
                            allowContentAccess = true
                            javaScriptCanOpenWindowsAutomatically = true
                            blockNetworkLoads = false
                            useWideViewPort = true
                            loadWithOverviewMode = true

                            // 防止系统字体缩放影响 WebView 内页面布局
                            textZoom = 100

                            // ── 深色模式 ──
                            // API 33+: algorithmicDarkening（自动跳过已支持 prefers-color-scheme 的站点）
                            // API 29-32: forceDark（已弃用但为唯一选项）
                            // API 28: 无原生 WebView 深色支持，由 Activity uiMode 间接影响
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                isAlgorithmicDarkeningAllowed = isDark
                            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                @Suppress("DEPRECATION")
                                forceDark = if (isDark)
                                    WebSettings.FORCE_DARK_ON
                                else
                                    WebSettings.FORCE_DARK_OFF
                            }
                        }

                        webViewClient = viewClient
                        webChromeClient = chromeClient

                        setDownloadListener { downloadUrl, userAgent, contentDisposition, mimeType, _ ->
                            // DownloadManager can't handle blob/data URLs; those are handled via injected JS bridge.
                            if (downloadUrl.startsWith("blob:") || downloadUrl.startsWith("data:")) return@setDownloadListener

                            val filename = URLUtil.guessFileName(downloadUrl, contentDisposition, mimeType)
                            val mime = mimeType?.ifBlank { "application/octet-stream" } ?: "application/octet-stream"
                            val cookie = CookieManager.getInstance().getCookie(downloadUrl)

                            pendingHttpDownload = PendingHttpDownload(
                                url = downloadUrl,
                                fileName = filename,
                                mimeType = mime,
                                userAgent = userAgent ?: "",
                                cookie = cookie,
                                referer = url
                            )

                            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = mime
                                putExtra(Intent.EXTRA_TITLE, filename)
                            }
                            Handler(Looper.getMainLooper()).post {
                                runCatching { saveFileLauncher.launch(intent) }
                                    .onFailure { onWebError?.invoke("Open save dialog failed") }
                            }
                        }

                        // Set background to opaque to avoid rendering conflicts with backdrop layers (LiquidGlass)
                        // This prevents RenderThread NPEs during navigation transitions.
                        setBackgroundColor(if (isDark) android.graphics.Color.BLACK else android.graphics.Color.WHITE)

                        loadUrl(url)

                        tag = TagState(reloadKey = reloadKey, url = url, backRequestKey = backRequestKey)
                    }
                },
                update = { webView ->
                    // ── 深色模式同步（每次 isDark 变化时重新应用） ──
                    webView.settings.apply {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            if (isAlgorithmicDarkeningAllowed != isDark) {
                                isAlgorithmicDarkeningAllowed = isDark
                            }
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            @Suppress("DEPRECATION")
                            val target = if (isDark) WebSettings.FORCE_DARK_ON else WebSettings.FORCE_DARK_OFF
                            @Suppress("DEPRECATION")
                            if (forceDark != target) {
                                forceDark = target
                            }
                        }
                    }
                    webView.setBackgroundColor(
                        if (isDark) android.graphics.Color.BLACK
                        else android.graphics.Color.WHITE
                    )

                    if (hideUntilCommitVisible) {
                        webView.alpha = if (contentVisible) 1f else 0f
                        webView.visibility = if (contentVisible) WebView.VISIBLE else WebView.INVISIBLE
                    } else {
                        webView.alpha = 1f
                        webView.visibility = WebView.VISIBLE
                    }

                    val last = webView.tag as? TagState
                    val needsReload = last?.reloadKey != reloadKey
                    val urlChanged = last?.url != url
                    val backRequested = last?.backRequestKey != backRequestKey
                    if (urlChanged) {
                        contentVisible = !hideUntilCommitVisible
                        if (resetHistoryOnUrlChange) {
                            clearHistoryAfterNextPageFinished = true
                            onCanGoBackChange?.invoke(false)
                        }
                        webView.loadUrl(url)
                        webView.tag = TagState(reloadKey = reloadKey, url = url, backRequestKey = backRequestKey)
                    } else if (needsReload) {
                        // Real refresh (box.app uses WebView.reload())
                        contentVisible = !hideUntilCommitVisible
                        webView.reload()
                        webView.tag = TagState(reloadKey = reloadKey, url = url, backRequestKey = backRequestKey)
                    } else if (backRequested) {
                        if (webView.canGoBack()) {
                            webView.goBack()
                        }
                        onCanGoBackChange?.invoke(webView.canGoBack())
                        webView.tag = TagState(reloadKey = reloadKey, url = url, backRequestKey = backRequestKey)
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
            )
        }
    }
}

private data class TagState(
    val reloadKey: Int,
    val url: String,
    val backRequestKey: Int,
)
