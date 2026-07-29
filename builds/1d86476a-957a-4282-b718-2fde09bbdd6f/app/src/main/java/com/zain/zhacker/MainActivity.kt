package com.zain.zhacker

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var refresh: SwipeRefreshLayout
    private lateinit var offline: View
    private var fileChooser: ValueCallback<Array<Uri>>? = null
    private lateinit var filePicker: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webview)
        refresh = findViewById(R.id.refresh)
        offline = findViewById(R.id.offline)

        setupWebView()

        filePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val cb = fileChooser ?: return@registerForActivityResult
            val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
            cb.onReceiveValue(uris ?: arrayOf())
            fileChooser = null
        }

        refresh.setOnRefreshListener { webView.reload() }
        findViewById<View>(R.id.retry).setOnClickListener { loadOrOffline() }

        // Predictive-back compatible navigation (replaces deprecated onBackPressed)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        loadOrOffline()
    }

    private fun isOnline(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val n = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(n) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            @Suppress("DEPRECATION") cm.activeNetworkInfo?.isConnected == true
        }
    }

    private fun loadOrOffline() {
        if (isOnline()) {
            offline.visibility = View.GONE
            webView.visibility = View.VISIBLE
            if (webView.url.isNullOrEmpty()) {
                webView.loadUrl("https://synkit-v1.vercel.app/")
            }
        } else {
            offline.visibility = View.VISIBLE
            webView.visibility = View.GONE
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            // Hardened file/URL access
            allowFileAccess = false
            allowContentAccess = false
            @Suppress("DEPRECATION") allowFileAccessFromFileURLs = false
            @Suppress("DEPRECATION") allowUniversalAccessFromFileURLs = false
            javaScriptCanOpenWindowsAutomatically = false
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = false
            setSupportMultipleWindows(false)
            setGeolocationEnabled(false)
            cacheMode = WebSettings.LOAD_DEFAULT
            // Force HTTPS-only mixed-content policy
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            userAgentString = userAgentString + " Z-HackerApp/1.0.0"
        }

        // AndroidX WebKit — Safe Browsing when supported
        if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) {
            WebSettingsCompat.setSafeBrowsingEnabled(webView.settings, true)
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
                val url = req.url.toString()
                val scheme = req.url.scheme?.lowercase() ?: ""
                if (scheme == "http" || scheme == "https") return false
                if (scheme == "intent") {
                    try {
                        val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                        startActivity(intent)
                    } catch (_: Exception) {}
                    return true
                }
                return try {
                    startActivity(Intent(Intent.ACTION_VIEW, req.url))
                    true
                } catch (_: Exception) {
                    Toast.makeText(this@MainActivity, "Cannot open link", Toast.LENGTH_SHORT).show()
                    true
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                refresh.isRefreshing = false
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                fileChooser?.onReceiveValue(null)
                fileChooser = filePathCallback
                val intent = fileChooserParams?.createIntent() ?: return false
                return try {
                    filePicker.launch(intent)
                    true
                } catch (_: Exception) {
                    fileChooser = null
                    false
                }
            }
        }

        webView.setDownloadListener(DownloadListener { url, _, contentDisposition, mimetype, _ ->
            try {
                val req = DownloadManager.Request(Uri.parse(url))
                req.setMimeType(mimetype)
                req.addRequestHeader("cookie", CookieManager.getInstance().getCookie(url) ?: "")
                req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                val fileName = android.webkit.URLUtil.guessFileName(url, contentDisposition, mimetype)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                } else {
                    req.setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, fileName)
                }
                (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
                Toast.makeText(this, "Download started", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Download failed", Toast.LENGTH_SHORT).show()
            }
        })
    }

    override fun onPause() { super.onPause(); webView.onPause() }
    override fun onResume() { super.onResume(); webView.onResume() }
    override fun onDestroy() {
        try { webView.stopLoading(); webView.destroy() } catch (_: Exception) {}
        super.onDestroy()
    }
}
