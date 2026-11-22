package `in`.potr.bonsai.cloud

import `in`.potr.bonsai.data.model.SpecimenWithDetails
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Publishes, updates, or unpublishes a Bonsai specimen on Pots on the Rocks (https://potr.in)
 * under the "Bonsai" WooCommerce category using WooCommerce REST API v3 and WordPress Media API.
 */
class WooCommercePublisher {

    data class WooConfig(
        val storeUrl: String,
        val consumerKey: String,
        val consumerSecret: String,
        val wpUser: String,
        val wpAppPassword: String
    )

    data class WooPublishResult(
        val productId: Int,
        val permalink: String
    )

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    fun publishOrUpdateSpecimen(config: WooConfig, item: SpecimenWithDetails): WooPublishResult {
        val baseUrl = config.storeUrl.trimEnd('/')
        val wcAuth = Credentials.basic(config.consumerKey, config.consumerSecret)
        val s = item.specimen

        // 1. Ensure "Bonsai" category ID
        val categoryId = ensureBonsaiCategory(baseUrl, wcAuth)

        // 2. Upload local compressed JPEG if WP Application Password is configured
        var uploadedMediaId: Int? = null
        val localFile = if (!s.localImagePath.startsWith("asset:") && s.localImagePath.isNotBlank()) {
            File(s.localImagePath).takeIf { it.exists() }
        } else {
            null
        }
        if (config.wpUser.isNotBlank() && config.wpAppPassword.isNotBlank() && localFile != null) {
            uploadedMediaId = uploadPhotoToWordPressMedia(
                baseUrl = baseUrl,
                wpUser = config.wpUser,
                wpAppPassword = config.wpAppPassword,
                code = s.code,
                imageFile = localFile
            )
        }

        // 3. Build rich HTML description including YouTube lifespan video links when available
        val youtubeSection = item.sortedTimeline
            .filter { !it.beforeYoutubeUrl.isNullOrBlank() || !it.afterYoutubeUrl.isNullOrBlank() }
            .take(5)
            .joinToString("\n") { entry ->
                val links = buildList {
                    entry.beforeYoutubeUrl?.let { add("<a href=\"$it\">Before Video</a>") }
                    entry.afterYoutubeUrl?.let { add("<a href=\"$it\">After Video</a>") }
                }.joinToString(" &bull; ")
                "<li><strong>${escapeHtml(entry.dateIso)} — ${escapeHtml(entry.taskTitle)}:</strong> $links</li>"
            }

        val descriptionHtml = buildString {
            append("<p><strong>Specimen Code:</strong> ${escapeHtml(s.code)}</p>")
            append("<p><strong>Botanical Species:</strong> <em>${escapeHtml(s.species)}</em>")
            if (s.subspecies.isNotBlank()) append(" (${escapeHtml(s.subspecies)})")
            append("</p>")
            append("<p><strong>Age:</strong> ${s.age} years (${escapeHtml(s.ageType)})</p>")
            if (s.pot.isNotBlank()) {
                append("<p><strong>Pot &amp; Container:</strong> ${escapeHtml(s.pot)}</p>")
            }
            if (youtubeSection.isNotBlank()) {
                append("<h4>Lifespan Care Timeline</h4><ul>$youtubeSection</ul>")
            }
            append("<p>Curated and maintained at the Pots on the Rocks Bonsai Studio.</p>")
        }

        val productJson = JSONObject().apply {
            put("name", "${s.name} (${s.code})")
            put("type", "simple")
            put("status", "publish")
            put("sku", s.code)
            if (s.sellingPrice > 0) {
                put("regular_price", s.sellingPrice.toLong().toString())
            }
            put("description", descriptionHtml)
            put("short_description", "${s.species} ${s.subspecies} • ${s.age} yrs (${s.ageType}) • ${s.pot}")
            put("categories", JSONArray().put(JSONObject().put("id", categoryId)))
            if (uploadedMediaId != null) {
                put("images", JSONArray().put(JSONObject().put("id", uploadedMediaId)))
            }
        }

        // 4. Resolve existing product ID by stored ID or SKU lookup to avoid duplicate SKU errors
        val existingId = s.wooProductId ?: findProductIdBySku(baseUrl, wcAuth, s.code)

        val req = if (existingId != null) {
            Request.Builder()
                .url("$baseUrl/wp-json/wc/v3/products/$existingId")
                .addHeader("Authorization", wcAuth)
                .put(productJson.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
        } else {
            Request.Builder()
                .url("$baseUrl/wp-json/wc/v3/products")
                .addHeader("Authorization", wcAuth)
                .post(productJson.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
        }

        http.newCall(req).execute().use { resp ->
            val bodyStr = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException("WooCommerce publish failed (${resp.code}): $bodyStr")
            }
            val resJson = JSONObject(bodyStr)
            return WooPublishResult(
                productId = resJson.getInt("id"),
                permalink = resJson.optString("permalink", "$baseUrl/product-category/bonsai/")
            )
        }
    }

    fun unpublishSpecimen(config: WooConfig, productId: Int) {
        val baseUrl = config.storeUrl.trimEnd('/')
        val wcAuth = Credentials.basic(config.consumerKey, config.consumerSecret)
        val payload = JSONObject().put("status", "draft")
        val req = Request.Builder()
            .url("$baseUrl/wp-json/wc/v3/products/$productId")
            .addHeader("Authorization", wcAuth)
            .put(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful && resp.code != 404) {
                throw IllegalStateException("WooCommerce unpublish failed (${resp.code}): ${resp.body?.string()}")
            }
        }
    }

    private fun findProductIdBySku(baseUrl: String, wcAuth: String, sku: String): Int? {
        val encodedSku = URLEncoder.encode(sku, "UTF-8")
        val req = Request.Builder()
            .url("$baseUrl/wp-json/wc/v3/products?sku=$encodedSku")
            .addHeader("Authorization", wcAuth)
            .get()
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val arr = JSONArray(resp.body?.string().orEmpty())
            return if (arr.length() > 0) arr.getJSONObject(0).optInt("id").takeIf { it > 0 } else null
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
            .post(createCat.toString().toRequestBody(JSON_MEDIA_TYPE))
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
        imageFile: File
    ): Int? {
        val safeCode = code.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val wpAuth = Credentials.basic(wpUser, wpAppPassword)
        val req = Request.Builder()
            .url("$baseUrl/wp-json/wp/v2/media")
            .addHeader("Authorization", wpAuth)
            .addHeader("Content-Disposition", "attachment; filename=\"${safeCode}_bonsai.jpg\"")
            .post(imageFile.asRequestBody("image/jpeg".toMediaType()))
            .build()

        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val json = JSONObject(resp.body?.string().orEmpty())
            return if (json.has("id")) json.getInt("id") else null
        }
    }

    private fun escapeHtml(input: String): String =
        input.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
