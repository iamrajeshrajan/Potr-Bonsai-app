package `in`.potr.bonsai.bridge

import android.content.Context
import android.webkit.JavascriptInterface
import org.json.JSONArray
import org.json.JSONObject

/**
 * Exposes native Android actions (Google Sign-In, on-device 1600px JPEG photo compression,
 * Google Drive/Sheets sync, and potr.in WooCommerce product publishing) to the POTR Bonsai WebView UI.
 */
class BonsaiJavascriptBridge(
    private val context: Context,
    private val onSignInRequested: () -> Unit,
    private val onCaptureAndCompressRequested: (String) -> Unit,
    private val onSyncSheetRequested: (JSONArray) -> Unit,
    private val onPublishWooRequested: (JSONObject) -> Unit
) {
    private val prefs = context.getSharedPreferences("potr_bonsai_prefs", Context.MODE_PRIVATE)

    @JavascriptInterface
    fun signInWithGoogle() {
        onSignInRequested()
    }

    @JavascriptInterface
    fun captureAndCompressPhoto(specimenIdOrCode: String) {
        onCaptureAndCompressRequested(specimenIdOrCode)
    }

    @JavascriptInterface
    fun syncCatalogToGoogleSheet(specimensJson: String) {
        try {
            val arr = JSONArray(specimensJson)
            onSyncSheetRequested(arr)
        } catch (_: Exception) {
        }
    }

    @JavascriptInterface
    fun saveWooCommerceConfig(configJson: String) {
        try {
            val obj = JSONObject(configJson)
            prefs.edit()
                .putString("wc_store_url", obj.optString("storeUrl", "https://potr.in"))
                .putString("wc_ck", obj.optString("consumerKey", ""))
                .putString("wc_cs", obj.optString("consumerSecret", ""))
                .putString("wc_wp_user", obj.optString("wpUser", ""))
                .putString("wc_wp_pass", obj.optString("wpAppPass", ""))
                .apply()
        } catch (_: Exception) {
        }
    }

    @JavascriptInterface
    fun publishSpecimenToWooCommerce(specimenJson: String) {
        try {
            val obj = JSONObject(specimenJson)
            onPublishWooRequested(obj)
        } catch (_: Exception) {
        }
    }

    @JavascriptInterface
    fun getCloudStatusJson(): String {
        return JSONObject().apply {
            put("email", prefs.getString("google_email", null))
            put("driveFolderId", prefs.getString("drive_folder_id", null))
            put("sheetId", prefs.getString("master_sheet_id", null))
            put("storeUrl", prefs.getString("wc_store_url", "https://potr.in"))
        }.toString()
    }
}
