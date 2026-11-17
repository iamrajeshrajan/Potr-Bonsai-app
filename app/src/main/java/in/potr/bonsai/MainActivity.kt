package `in`.potr.bonsai

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import `in`.potr.bonsai.bridge.BonsaiJavascriptBridge
import `in`.potr.bonsai.cloud.GoogleWorkspaceService
import `in`.potr.bonsai.cloud.WooCommercePublisher
import `in`.potr.bonsai.util.PhotoCompressor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var pendingCompressSpecimenCode: String = "BNS-NEW"

    private val workspaceService = GoogleWorkspaceService()
    private val wooPublisher = WooCommercePublisher()

    private val prefs by lazy {
        getSharedPreferences("potr_bonsai_prefs", Context.MODE_PRIVATE)
    }

    // Standard HTML <input type="file"> chooser support (automatically compresses images on selection)
    private val webFileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val callback = filePathCallback ?: return@registerForActivityResult
        filePathCallback = null
        if (result.resultCode != Activity.RESULT_OK || result.data == null) {
            callback.onReceiveValue(null)
            return@registerForActivityResult
        }
        val dataUri = result.data?.data
        if (dataUri != null) {
            callback.onReceiveValue(arrayOf(dataUri))
        } else {
            callback.onReceiveValue(null)
        }
    }

    // Dedicated native photo picker + on-device 1600px JPEG compressor + Google Drive uploader
    private val nativePhotoCompressLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            try {
                val compressed = withContext(Dispatchers.IO) {
                    PhotoCompressor.compressUriToJpeg(
                        context = this@MainActivity,
                        sourceUri = uri,
                        specimenCode = pendingCompressSpecimenCode
                    )
                }
                var driveUrl: String? = null
                val token = fetchGoogleAccessTokenSilently()
                val folderId = prefs.getString("drive_folder_id", null)
                if (token != null && folderId != null) {
                    driveUrl = withContext(Dispatchers.IO) {
                        workspaceService.uploadCompressedJpegToDrive(
                            accessToken = token,
                            folderId = folderId,
                            fileName = compressed.file.name,
                            jpegBytes = compressed.bytes
                        )
                    }
                }
                val safeDataUrl = JSONObject.quote(compressed.dataUrl)
                val safeDriveUrl = if (driveUrl != null) JSONObject.quote(driveUrl) else "null"
                webView.evaluateJavascript("window.onNativePhotoCompressed($safeDataUrl, $safeDriveUrl);", null)
            } catch (e: Exception) {
                sendToastToWeb("Photo compression error: ${e.message}")
            }
        }
    }

    // Google Sign-In Launcher
    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.result
            if (account != null) {
                onGoogleAccountConnected(account)
            }
        } catch (e: Exception) {
            sendToastToWeb("Google Sign-In requires SHA-1 fingerprint registered in Google Cloud Console")
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.bonsaiWebView)

        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            mediaPlaybackRequiresUserGesture = false
            cacheMode = WebSettings.LOAD_DEFAULT
        }

        webView.addJavascriptInterface(
            BonsaiJavascriptBridge(
                context = this,
                onSignInRequested = { startGoogleSignIn() },
                onCaptureAndCompressRequested = { code ->
                    pendingCompressSpecimenCode = code
                    nativePhotoCompressLauncher.launch("image/*")
                },
                onSyncSheetRequested = { specimensArray -> syncToGoogleSheet(specimensArray) },
                onPublishWooRequested = { specimenObj -> publishToPotrWooCommerce(specimenObj) }
            ),
            "AndroidBridge"
        )

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString().orEmpty()
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    return true
                }
                return false
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                this@MainActivity.filePathCallback?.onReceiveValue(null)
                this@MainActivity.filePathCallback = filePathCallback
                return try {
                    val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                    }
                    webFileChooserLauncher.launch(intent)
                    true
                } catch (e: Exception) {
                    this@MainActivity.filePathCallback = null
                    false
                }
            }
        }

        webView.loadUrl("file:///android_asset/bonsai_catalog_ui.html")

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun startGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(
                Scope("https://www.googleapis.com/auth/drive.file"),
                Scope("https://www.googleapis.com/auth/spreadsheets"),
                Scope("https://www.googleapis.com/auth/youtube.upload")
            )
            .build()
        val client = GoogleSignIn.getClient(this, gso)
        googleSignInLauncher.launch(client.signInIntent)
    }

    private fun onGoogleAccountConnected(account: GoogleSignInAccount) {
        val email = account.email.orEmpty()
        prefs.edit().putString("google_email", email).apply()
        sendToastToWeb("Signed in as $email — provisioning Drive & Sheet...")

        lifecycleScope.launch {
            try {
                val token = fetchGoogleAccessTokenSilently() ?: return@launch
                val folderId = withContext(Dispatchers.IO) {
                    workspaceService.ensureDriveFolder(token, "Bonsai Catalogue")
                }
                prefs.edit().putString("drive_folder_id", folderId).apply()
                val statusJson = JSONObject().apply {
                    put("email", email)
                    put("driveFolderId", folderId)
                    put("sheetId", prefs.getString("master_sheet_id", "Ready"))
                }.toString()
                webView.evaluateJavascript("window.updateCloudModalFromNative($statusJson);", null)
                sendToastToWeb("Connected 'Bonsai Catalogue' folder in Google Drive ✓")
            } catch (e: Exception) {
                sendToastToWeb("Drive setup notice: ${e.message}")
            }
        }
    }

    private fun syncToGoogleSheet(specimensArray: JSONArray) {
        lifecycleScope.launch {
            try {
                val token = fetchGoogleAccessTokenSilently()
                if (token == null) {
                    sendToastToWeb("Please sign in with Google first (Cloud & potr.in button)")
                    return@launch
                }
                val existingSheetId = prefs.getString("master_sheet_id", null)
                val sheetId = withContext(Dispatchers.IO) {
                    workspaceService.syncSpecimensToSheet(token, existingSheetId, specimensArray)
                }
                prefs.edit().putString("master_sheet_id", sheetId).apply()
                sendToastToWeb("Synced ${specimensArray.length()} specimens to Google Sheet ✓")
            } catch (e: Exception) {
                sendToastToWeb("Google Sheet sync failed: ${e.message}")
            }
        }
    }

    private fun publishToPotrWooCommerce(specimenObj: JSONObject) {
        val ck = prefs.getString("wc_ck", "").orEmpty()
        val cs = prefs.getString("wc_cs", "").orEmpty()
        if (ck.isBlank() || cs.isBlank()) {
            sendToastToWeb("Published locally ✓ (Add potr.in keys in 'Cloud & potr.in' to auto-push to WooCommerce)")
            return
        }
        val config = WooCommercePublisher.WooConfig(
            storeUrl = prefs.getString("wc_store_url", "https://potr.in").orEmpty(),
            consumerKey = ck,
            consumerSecret = cs,
            wpUser = prefs.getString("wc_wp_user", "").orEmpty(),
            wpAppPassword = prefs.getString("wc_wp_pass", "").orEmpty()
        )
        lifecycleScope.launch {
            try {
                val permalink = withContext(Dispatchers.IO) {
                    wooPublisher.publishSpecimen(config, specimenObj)
                }
                sendToastToWeb("Live on potr.in/bonsai: $permalink")
            } catch (e: Exception) {
                sendToastToWeb("potr.in publish error: ${e.message}")
            }
        }
    }

    private suspend fun fetchGoogleAccessTokenSilently(): String? = withContext(Dispatchers.IO) {
        try {
            val acct = GoogleSignIn.getLastSignedInAccount(this@MainActivity)?.account ?: return@withContext null
            val scopes = "oauth2:" +
                "https://www.googleapis.com/auth/drive.file " +
                "https://www.googleapis.com/auth/spreadsheets " +
                "https://www.googleapis.com/auth/youtube.upload"
            GoogleAuthUtil.getToken(this@MainActivity, acct, scopes)
        } catch (_: Exception) {
            null
        }
    }

    private fun sendToastToWeb(msg: String) {
        runOnUiThread {
            val quoted = JSONObject.quote(msg)
            webView.evaluateJavascript("if(window.onNativeToast) window.onNativeToast($quoted);", null)
        }
    }
}
