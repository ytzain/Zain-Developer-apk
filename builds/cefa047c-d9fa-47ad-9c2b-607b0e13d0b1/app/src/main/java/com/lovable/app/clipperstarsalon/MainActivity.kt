package com.lovable.app.clipperstarsalon

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var refresh: SwipeRefreshLayout
    private lateinit var offline: View
    private lateinit var splashLayer: View
    private var fileChooser: ValueCallback<Array<Uri>>? = null
    private lateinit var filePicker: ActivityResultLauncher<Intent>
    private val handler = Handler(Looper.getMainLooper())
    private var splashShownAt = 0L
    private var splashHidden = false

    companion object {
        private const val SHOW_SPLASH = true
        private const val MIN_SPLASH_MS = 1800L
        private const val PREFS = "app_prefs"
        private const val KEY_OPEN_COUNT = "open_count"
        private const val KEY_DIALOG_SHOWN = "share_dialog_shown"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keep the system status bar visible and lay content strictly below it
        // (works on Android 15 edge-to-edge too, incl. camera notch + nav bar).
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.parseColor("#0A0505")
        window.navigationBarColor = Color.parseColor("#0A0505")
        window.decorView.setBackgroundColor(Color.parseColor("#0A0505"))
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
            show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
        setContentView(R.layout.activity_main)
        val rootView = findViewById<View>(R.id.rootLayout)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->
            val bars = insets.getInsets(
                androidx.core.view.WindowInsetsCompat.Type.systemBars() or
                    androidx.core.view.WindowInsetsCompat.Type.displayCutout() or
                    androidx.core.view.WindowInsetsCompat.Type.ime()
            )
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            androidx.core.view.WindowInsetsCompat.CONSUMED
        }

        webView = findViewById(R.id.webview)
        refresh = findViewById(R.id.refresh)
        offline = findViewById(R.id.offline)
        splashLayer = findViewById(R.id.splashLayer)

        setupWebView()

        if (SHOW_SPLASH) {
            startSplashAnimation()
        } else {
            splashHidden = true
            splashLayer.visibility = View.GONE
        }

        filePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val cb = fileChooser ?: return@registerForActivityResult
            val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
            cb.onReceiveValue(uris ?: arrayOf())
            fileChooser = null
        }

        refresh.setOnRefreshListener { webView.reload() }
        findViewById<View>(R.id.retry).setOnClickListener { loadOrOffline() }

        loadOrOffline()
        trackOpenAndMaybePrompt()
    }

    private fun startSplashAnimation() {
        splashShownAt = System.currentTimeMillis()
        val logo = findViewById<ImageView>(R.id.splashLogo)
        val title = findViewById<View>(R.id.splashTitle)
        val tagline = findViewById<View>(R.id.splashTagline)
        val bar = findViewById<View>(R.id.splashProgress)
        val fill = findViewById<View>(R.id.splashFill)
        val ring1 = findViewById<View>(R.id.splashRing1)
        val ring2 = findViewById<View>(R.id.splashRing2)
        val orbit = findViewById<View>(R.id.splashOrbit)

        // Logo: drop in with overshoot bounce + slight spin
        logo.alpha = 0f
        logo.scaleX = 0.3f
        logo.scaleY = 0.3f
        logo.rotation = -25f
        logo.animate()
            .alpha(1f).scaleX(1f).scaleY(1f).rotation(0f)
            .setDuration(900)
            .setInterpolator(android.view.animation.OvershootInterpolator(2.2f))
            .withEndAction { breathe(logo) }
            .start()

        // Expanding sonar rings
        pulseRing(ring1, 0L)
        pulseRing(ring2, 700L)

        // Orbiting neon arc
        val spin = android.animation.ObjectAnimator.ofFloat(orbit, View.ROTATION, 0f, 360f)
        spin.duration = 1400
        spin.repeatCount = android.animation.ValueAnimator.INFINITE
        spin.interpolator = android.view.animation.LinearInterpolator()
        spin.start()

        // Title slides up, tagline follows
        title.alpha = 0f
        title.translationY = 40f
        title.animate().alpha(1f).translationY(0f).setStartDelay(450).setDuration(600)
            .setInterpolator(DecelerateInterpolator()).start()
        tagline.alpha = 0f
        tagline.translationY = 30f
        tagline.animate().alpha(0.75f).translationY(0f).setStartDelay(650).setDuration(600)
            .setInterpolator(DecelerateInterpolator()).start()

        // Neon loading bar sweeping left to right
        bar.alpha = 0f
        bar.animate().alpha(1f).setStartDelay(800).setDuration(400).start()
        bar.post {
            val w = bar.width.toFloat()
            fill.translationX = -fill.width.toFloat()
            val sweep = android.animation.ObjectAnimator.ofFloat(fill, View.TRANSLATION_X, -fill.width.toFloat(), w)
            sweep.duration = 1100
            sweep.repeatCount = android.animation.ValueAnimator.INFINITE
            sweep.interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            sweep.start()
        }
    }

    private fun breathe(v: View) {
        if (splashHidden) return
        v.animate().scaleX(1.06f).scaleY(1.06f).setDuration(800)
            .withEndAction {
                v.animate().scaleX(1f).scaleY(1f).setDuration(800)
                    .withEndAction { breathe(v) }.start()
            }.start()
    }

    private fun pulseRing(ring: View, delay: Long) {
        ring.scaleX = 0.6f
        ring.scaleY = 0.6f
        ring.alpha = 0f
        ring.animate().setStartDelay(delay).scaleX(1.9f).scaleY(1.9f).alpha(0f)
            .setDuration(1400)
            .setInterpolator(DecelerateInterpolator())
            .setUpdateListener { a ->
                val f = a.animatedFraction
                ring.alpha = if (f < 0.2f) f * 4f else (1f - f) * 0.8f
            }
            .withEndAction { if (!splashHidden) pulseRing(ring, 0L) }
            .start()
    }

    private fun hideSplash() {
        if (splashHidden) return
        splashHidden = true
        val elapsed = System.currentTimeMillis() - splashShownAt
        val wait = if (elapsed < MIN_SPLASH_MS) MIN_SPLASH_MS - elapsed else 0L
        handler.postDelayed({
            splashLayer.animate()
                .alpha(0f)
                .setDuration(400)
                .withEndAction { splashLayer.visibility = View.GONE }
                .start()
        }, wait)
    }

    private fun isOnline(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val n = cm.activeNetwork ?: return false
            cm.getNetworkCapabilities(n) != null
        } else {
            @Suppress("DEPRECATION") cm.activeNetworkInfo?.isConnected == true
        }
    }

    private fun loadOrOffline() {
        if (isOnline()) {
            offline.visibility = View.GONE
            webView.visibility = View.VISIBLE
            webView.loadUrl("https://clipperstar-salon.lovable.app/")
        } else {
            hideSplash()
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
            allowFileAccess = false
            allowContentAccess = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = false
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString = userAgentString + " Clipper Star SalonApp/1.0.0"
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
                hideSplash()
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
                req.setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    URLUtilCompat.guessFileName(url, contentDisposition, mimetype)
                )
                (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
                Toast.makeText(this, "Download started", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Download failed", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun trackOpenAndMaybePrompt() {
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val count = prefs.getInt(KEY_OPEN_COUNT, 0) + 1
        prefs.edit().putInt(KEY_OPEN_COUNT, count).apply()
        if (count >= 3 && !prefs.getBoolean(KEY_DIALOG_SHOWN, false)) {
            handler.postDelayed({ showShareDialog(prefs) }, 4000)
        }
    }

    private fun showShareDialog(prefs: SharedPreferences) {
        if (isFinishing || isDestroyed) return
        prefs.edit().putBoolean(KEY_DIALOG_SHOWN, true).apply()
        MaterialAlertDialogBuilder(this)
            .setTitle("Enjoying " + getString(R.string.app_name) + "?")
            .setMessage("Share this app with your friends or rate it — it helps a lot!")
            .setPositiveButton("Share") { _, _ -> shareApp() }
            .setNeutralButton("Rate") { _, _ -> openRatePage() }
            .setNegativeButton("Later", null)
            .show()
    }

    private fun shareApp() {
        val send = Intent(Intent.ACTION_SEND)
        send.type = "text/plain"
        send.putExtra(
            Intent.EXTRA_TEXT,
            "Check out " + getString(R.string.app_name) + ": https://clipperstar-salon.lovable.app/"
        )
        startActivity(Intent.createChooser(send, "Share via"))
    }

    private fun openRatePage() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + packageName)))
        } catch (e: Exception) {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=" + packageName)
                )
            )
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}

object URLUtilCompat {
    fun guessFileName(url: String, cd: String?, mime: String?): String =
        android.webkit.URLUtil.guessFileName(url, cd, mime)
}
