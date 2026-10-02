package `in`.potr.bonsai.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class ApiException(val status: Int, message: String) : Exception(message)

val json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
}

private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
private val WEBP_TYPE = "image/webp".toMediaType()

private val http = OkHttpClient.Builder()
    .connectTimeout(20, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .writeTimeout(120, TimeUnit.SECONDS)
    .build()

/** Thin client for the Potr Bonsai Worker API. */
class Api(serverUrl: String, private val accessKey: String) {
    private val base = serverUrl.trim().trimEnd('/')

    private suspend fun call(method: String, path: String, body: JsonObject? = null): String =
        withContext(Dispatchers.IO) {
            val requestBody = when {
                body != null -> json.encodeToString(JsonObject.serializer(), body).toRequestBody(JSON_TYPE)
                method == "POST" || method == "PATCH" -> "{}".toRequestBody(JSON_TYPE)
                else -> null
            }
            val request = Request.Builder()
                .url("$base/api$path")
                .header("Authorization", "Bearer $accessKey")
                .method(method, requestBody)
                .build()
            execute(request)
        }

    private fun execute(request: Request): String {
        http.newCall(request).execute().use { res ->
            val text = res.body?.string().orEmpty()
            if (!res.isSuccessful) {
                val message = runCatching { json.decodeFromString(ErrorBody.serializer(), text).error }.getOrNull()
                throw ApiException(res.code, message ?: "Server error (${res.code})")
            }
            return text
        }
    }

    private suspend inline fun <reified T> get(path: String): T = json.decodeFromString(call("GET", path))
    private suspend inline fun <reified T> post(path: String, body: JsonObject? = null): T =
        json.decodeFromString(call("POST", path, body))
    private suspend inline fun <reified T> patch(path: String, body: JsonObject): T =
        json.decodeFromString(call("PATCH", path, body))

    private fun q(v: String) = URLEncoder.encode(v, "UTF-8")

    suspend fun me(): User = get("/me")
    suspend fun meta(): Meta = get("/meta")

    // Bonsai
    suspend fun listBonsai(query: String = "", status: String? = null): List<Bonsai> =
        get<Items<Bonsai>>("/bonsai?limit=200&q=${q(query)}${status?.let { "&status=$it" } ?: ""}").items
    suspend fun bonsai(ref: String): BonsaiDetail = get("/bonsai/${q(ref)}")
    suspend fun createBonsai(body: JsonObject): Bonsai = post("/bonsai", body)
    suspend fun updateBonsai(id: String, body: JsonObject): Bonsai = patch("/bonsai/$id", body)
    suspend fun addEvent(bonsaiId: String, body: JsonObject): CareEvent = post("/bonsai/$bonsaiId/events", body)
    suspend fun syncBonsai(id: String): SyncResult = post("/bonsai/$id/sync")

    // Pots
    suspend fun listPots(query: String = "", status: String? = null): List<Pot> =
        get<Items<Pot>>("/pots?limit=200&q=${q(query)}${status?.let { "&status=$it" } ?: ""}").items
    suspend fun pot(ref: String): PotDetail = get("/pots/${q(ref)}")
    suspend fun createPot(body: JsonObject): Pot = post("/pots", body)
    suspend fun updatePot(id: String, body: JsonObject): Pot = patch("/pots/$id", body)
    suspend fun syncPot(id: String): SyncResult = post("/pots/$id/sync")

    // Photos
    /** Uploads a compressed WebP; the newest photo becomes the featured image. */
    suspend fun uploadPhoto(subjectType: String, subjectId: String, webp: ByteArray): Photo =
        withContext(Dispatchers.IO) {
            val form = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("subject_type", subjectType)
                .addFormDataPart("subject_id", subjectId)
                .addFormDataPart("file", "photo.webp", webp.toRequestBody(WEBP_TYPE))
                .build()
            val request = Request.Builder()
                .url("$base/api/photos")
                .header("Authorization", "Bearer $accessKey")
                .post(form)
                .build()
            json.decodeFromString(Photo.serializer(), execute(request))
        }

    // Tasks
    suspend fun tasks(bonsaiRef: String? = null): List<CareTask> =
        get<Items<CareTask>>("/tasks${bonsaiRef?.let { "?bonsai_id=${q(it)}" } ?: ""}").items
    suspend fun createTask(body: JsonObject): CareTask = post("/tasks", body)
    suspend fun completeTask(id: String, body: JsonObject): CompleteTaskResult = post("/tasks/$id/complete", body)
    suspend fun cancelTask(id: String): CareTask = patch("/tasks/$id", buildJsonObject { put("status", "cancelled") })

    suspend fun lookup(scanned: String): LookupResult = get("/lookup?q=${q(scanned)}")

    // Team (admin)
    suspend fun users(): List<User> = get<Items<User>>("/users").items
    suspend fun createUser(body: JsonObject): User = post("/users", body)
    suspend fun updateUser(id: String, body: JsonObject): User = patch("/users/$id", body)
    suspend fun rotateKey(id: String): AccessKey = post("/users/$id/rotate-key")
}
