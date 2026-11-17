package `in`.potr.bonsai.cloud

import android.util.Base64
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Publishes a Bonsai specimen directly to Pots on the Rocks (https://potr.in)
 * under the "Bonsai" WooCommerce product category using WooCommerce REST API v3
 * and WordPress Application Passwords for media upload.
 */
class WooCommercePublisher {

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    data class WooConfig(
        val storeUrl: String,
        val consumerKey: String,
        val consumerSecret: String,
        val wpUser: String,
        val wpAppPassword: String
    )

    fun publishSpecimen(config: WooConfig, specimen: JSONObject): String {
        val baseUrl = config.storeUrl.trimEnd('/')
        val wcAuth = Credentials.basic(config.consumerKey, config.consumerSecret)

        // 1. Ensure or find "Bonsai" category ID in WooCommerce
        val categoryId = ensureBonsaiCategory(baseUrl, wcAuth)

        // 2. Optionally upload base64 JPEG photo to WordPress Media Library if WP Application Password is set
        var uploadedMediaId: Int? = null
        val imageDataUrl = specimen.optString("image", "")
        if (config.wpUser.isNotBlank() && config.wpAppPassword.isNotBlank() && imageDataUrl.startsWith("data:image/")) {
            uploadedMediaId = uploadPhotoToWordPressMedia(
                baseUrl = baseUrl,
                wpUser = config.wpUser,
                wpAppPassword = config.wpAppPassword,
                code = specimen.optString("code", "BNS"),
                dataUrl = imageDataUrl
            )
        }

        // 3. Build WooCommerce product payload
        val code = specimen.optString("code", "BNS-0000")
        val name = specimen.optString("name", "Bonsai Specimen")
        val species = specimen.optString("species", "")
        val subspecies = specimen.optString("subspecies", "")
        val age = specimen.optInt("age", 10)
        val ageType = specimen.optString("ageType", "approximate")
        val pot = specimen.optString("pot", "Handcrafted POTR Terracotta")
        val sellingPrice = specimen.optDouble("sellingPrice", 0.0)

        val descriptionHtml = """
            <p><strong>Specimen Code:</strong> $code</p>
            <p><strong>Botanical Species:</strong> <em>$species</em> ($subspecies)</p>
            <p><strong>Age:</strong> $age years ($ageType)</p>
            <p><strong>Pot &amp; Container:</strong> $pot</p>
            <p>Curated and maintained via the POTR Bonsai Studio Catalogue.</p>
        """.trimIndent()

        val productJson = JSONObject().apply {
            put("name", "$name ($code)")
            put("type", "simple")
            put("status", "publish")
            put("sku", code)
            if (sellingPrice > 0) {
                put("regular_price", sellingPrice.toInt().toString())
            }
            put("description", descriptionHtml)
            put("short_description", "$species $subspecies • $age yrs ($ageType) • $pot")
            put("categories", JSONArray().put(JSONObject().put("id", categoryId)))
            if (uploadedMediaId != null) {
                put("images", JSONArray().put(JSONObject().put("id", uploadedMediaId)))
            }
        }

        val req = Request.Builder()
            .url("$baseUrl/wp-json/wc/v3/products")
            .addHeader("Authorization", wcAuth)
            .post(productJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        http.newCall(req).execute().use { resp ->
            val bodyStr = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException("WooCommerce publish failed (${resp.code}): $bodyStr")
            }
            val resJson = JSONObject(bodyStr)
            return resJson.optString("permalink", "$baseUrl/product-category/bonsai/")
        }
    }

    private fun ensureBonsaiCategory(baseUrl: String, wcAuth: String): Int {
        val searchReq = Request.Builder()
            .url("$baseUrl/wp-json/wc/v3/products/categories?search=Bonsai")
            .addHeader("Authorization", wcAuth)
            .get()
            .build()

        http.newCall(searchReq).execute().use { resp ->
            val str = resp.body?.string().orEmpty()
            if (resp.isSuccessful) {
                val arr = JSONArray(str)
                for (i in 0 until arr.length()) {
                    val cat = arr.getJSONObject(i)
                    if (cat.optString("name").equals("Bonsai", ignoreCase = true)) {
                        return cat.getInt("id")
                    }
                }
            }
        }

        val createCat = JSONObject().apply {
            put("name", "Bonsai")
            put("slug", "bonsai")
        }
        val createReq = Request.Builder()
            .url("$baseUrl/wp-json/wc/v3/products/categories")
            .addHeader("Authorization", wcAuth)
            .post(createCat.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        http.newCall(createReq).execute().use { resp ->
            val str = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IllegalStateException("Could not create Bonsai category: $str")
            return JSONObject(str).getInt("id")
        }
    }

    private fun uploadPhotoToWordPressMedia(
        baseUrl: String,
        wpUser: String,
        wpAppPassword: String,
        code: String,
        dataUrl: String
    ): Int? {
        val commaIdx = dataUrl.indexOf(',')
        if (commaIdx < 0) return null
        val base64Part = dataUrl.substring(commaIdx + 1)
        val bytes = Base64.decode(base64Part, Base64.DEFAULT)

        val wpAuth = Credentials.basic(wpUser, wpAppPassword)
        val req = Request.Builder()
            .url("$baseUrl/wp-json/wp/v2/media")
            .addHeader("Authorization", wpAuth)
            .addHeader("Content-Disposition", "attachment; filename=\"${code}_bonsai.jpg\"")
            .post(bytes.toRequestBody("image/jpeg".toMediaType()))
            .build()

        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val json = JSONObject(resp.body?.string().orEmpty())
            return if (json.has("id")) json.getInt("id") else null
        }
    }
}
