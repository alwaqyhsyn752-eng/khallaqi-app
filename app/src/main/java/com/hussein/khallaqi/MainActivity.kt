package com.hussein.khallaqi

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.KeyEvent
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var errorView: LinearLayout
    private lateinit var tts: TextToSpeech

    private val SITE_URL = "https://gemini-api-key-xofn.onrender.com"

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        progressBar = findViewById(R.id.progressBar)
        errorView = findViewById(R.id.errorView)

        // ═══ SwipeRefresh: only enable at top (prevents unwanted reload) ═══
        swipeRefresh.isEnabled = true
        swipeRefresh.setOnChildScrollUpCallback { _, _ ->
            webView.canScrollVertically(-1)
        }

        // ═══ Native Android TTS (Arabic Saudi voice) ═══
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val saudi = Locale("ar", "SA")
                val result = tts.setLanguage(saudi)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts.setLanguage(Locale("ar"))
                }
                tts.setPitch(1.08f)
                tts.setSpeechRate(1.0f)
            }
        }

        // ═══ JS Bridge for Native TTS ═══
        webView.addJavascriptInterface(object {
            @JavascriptInterface
            fun speak(text: String) {
                runOnUiThread {
                    try {
                        val clean = text
                            .replace(Regex("```[\\s\\S]*?```"), " كود ")
                            .replace(Regex("`([^`]+)`"), "$1")
                            .replace(Regex("[#*_]"), "")
                            .take(2000)
                        tts.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "khallaqi_tts")
                    } catch (e: Exception) { }
                }
            }

            @JavascriptInterface
            fun stop() {
                runOnUiThread {
                    try { tts.stop() } catch (e: Exception) { }
                }
            }
        }, "AndroidTTS")

        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.setSupportZoom(false)
        settings.builtInZoomControls = false
        settings.mediaPlaybackRequiresUserGesture = false
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        WebView.setWebContentsDebuggingEnabled(false)

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?, request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return false
                return if (url.startsWith(SITE_URL) || url.contains("onrender.com")) {
                    false
                } else {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    } catch (_: Exception) { }
                    true
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.visibility = View.GONE
                swipeRefresh.isRefreshing = false
                errorView.visibility = View.GONE
                webView.visibility = View.VISIBLE
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    progressBar.visibility = View.GONE
                    swipeRefresh.isRefreshing = false
                    errorView.visibility = View.VISIBLE
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress < 100) {
                    progressBar.visibility = View.VISIBLE
                    progressBar.progress = newProgress
                } else {
                    progressBar.visibility = View.GONE
                }
            }

            override fun onPermissionRequest(request: PermissionRequest?) {
                if (request == null) return
                val needed = mutableListOf<String>()
                for (r in request.resources) {
                    when (r) {
                        PermissionRequest.RESOURCE_AUDIO_CAPTURE -> {
                            if (!hasPermission(Manifest.permission.RECORD_AUDIO))
                                needed.add(Manifest.permission.RECORD_AUDIO)
                        }
                        PermissionRequest.RESOURCE_VIDEO_CAPTURE -> {
                            if (!hasPermission(Manifest.permission.CAMERA))
                                needed.add(Manifest.permission.CAMERA)
                        }
                    }
                }
                if (needed.isNotEmpty()) permissionLauncher.launch(needed.toTypedArray())
                request.grant(request.resources)
            }
        }

        swipeRefresh.setOnRefreshListener {
            errorView.visibility = View.GONE
            webView.reload()
        }

        findViewById<Button>(R.id.retryBtn).setOnClickListener {
            errorView.visibility = View.GONE
            webView.loadUrl(SITE_URL)
        }

        val ask = mutableListOf<String>()
        if (!hasPermission(Manifest.permission.RECORD_AUDIO))
            ask.add(Manifest.permission.RECORD_AUDIO)
        if (!hasPermission(Manifest.permission.CAMERA))
            ask.add(Manifest.permission.CAMERA)
        if (ask.isNotEmpty()) permissionLauncher.launch(ask.toTypedArray())

        webView.loadUrl(SITE_URL)
    }

    private fun hasPermission(p: String): Boolean =
        ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        webView.restoreState(savedInstanceState)
    }

    override fun onDestroy() {
        if (::tts.isInitialized) {
            try {
                tts.stop()
                tts.shutdown()
            } catch (e: Exception) { }
        }
        super.onDestroy()
    }
}
