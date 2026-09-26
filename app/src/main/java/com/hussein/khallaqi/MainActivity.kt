package com.hussein.khallaqi

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.KeyEvent
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var tts: TextToSpeech

    private val SITE_URL = "https://gemini-api-key-xofn.onrender.com"

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val r = tts.setLanguage(Locale("ar", "SA"))
                if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts.setLanguage(Locale("ar"))
                }
                tts.setPitch(1.08f)
                tts.setSpeechRate(1.0f)
            }
        }

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
                runOnUiThread { try { tts.stop() } catch (e: Exception) { } }
            }
        }, "AndroidTTS")

        val s = webView.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.databaseEnabled = true
        s.loadWithOverviewMode = true
        s.useWideViewPort = true
        s.setSupportZoom(false)
        s.builtInZoomControls = false
        s.mediaPlaybackRequiresUserGesture = false
        s.cacheMode = WebSettings.LOAD_DEFAULT
        s.allowFileAccess = true
        s.allowContentAccess = true
        s.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, req: WebResourceRequest?): Boolean {
                val url = req?.url?.toString() ?: return false
                return if (url.contains("onrender.com")) false else {
                    try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (e: Exception) { }
                    true
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest?) {
                if (request == null) return
                val need = mutableListOf<String>()
                for (r in request.resources) {
                    if (r == PermissionRequest.RESOURCE_AUDIO_CAPTURE && !hasPerm(Manifest.permission.RECORD_AUDIO))
                        need.add(Manifest.permission.RECORD_AUDIO)
                    if (r == PermissionRequest.RESOURCE_VIDEO_CAPTURE && !hasPerm(Manifest.permission.CAMERA))
                        need.add(Manifest.permission.CAMERA)
                }
                if (need.isNotEmpty()) permLauncher.launch(need.toTypedArray())
                request.grant(request.resources)
            }
        }

        val ask = mutableListOf<String>()
        if (!hasPerm(Manifest.permission.RECORD_AUDIO)) ask.add(Manifest.permission.RECORD_AUDIO)
        if (!hasPerm(Manifest.permission.CAMERA)) ask.add(Manifest.permission.CAMERA)
        if (ask.isNotEmpty()) permLauncher.launch(ask.toTypedArray())

        webView.loadUrl(SITE_URL)
    }

    private fun hasPerm(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        if (::tts.isInitialized) { try { tts.stop(); tts.shutdown() } catch (e: Exception) { } }
        super.onDestroy()
    }
}
