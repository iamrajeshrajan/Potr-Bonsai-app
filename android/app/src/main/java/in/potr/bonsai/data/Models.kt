package `in`.potr.bonsai.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class User(
    val id: String,
    val name: String,
    val email: String? = null,
    val role: String,
    val active: Int? = null,
    @SerialName("last_seen_at") val lastSeenAt: String? = null,
    @SerialName("access_key") val accessKey: String? = null,
) {
    val isAdmin get() = role == "admin"
}

@Serializable
data class Bonsai(
    val id: String,
    val code: String,
    val name: String? = null,
    @SerialName("species_common") val speciesCommon: String,
    @SerialName("species_botanical") val speciesBotanical: String? = null,
    val style: String? = null,
    @SerialName("origin_type") val originType: String? = null,
    @SerialName("origin_location") val originLocation: String? = null,
    @SerialName("source_name") val sourceName: String? = null,
    @SerialName("acquired_on") val acquiredOn: String? = null,
    @SerialName("start_year") val startYear: Int? = null,
    @SerialName("age_basis") val ageBasis: String? = null,
    @SerialName("age_years") val ageYears: Int? = null,
    @SerialName("height_cm") val heightCm: Double? = null,
    @SerialName("trunk_cm") val trunkCm: Double? = null,
    @SerialName("spread_cm") val spreadCm: Double? = null,
    @SerialName("pot_id") val potId: String? = null,
    val location: String? = null,
    val description: String? = null,
    @SerialName("internal_notes") val internalNotes: String? = null,
    @SerialName("cost_inr") val costInr: Double? = null,
    val status: String,
    @SerialName("for_sale") val forSale: Boolean = false,
    @SerialName("price_inr") val priceInr: Double? = null,
    @SerialName("cover_photo_url") val coverPhotoUrl: String? = null,
    @SerialName("certificate_url") val certificateUrl: String = "",
    @SerialName("woo_product_id") val wooProductId: Long? = null,
    @SerialName("woo_sync_status") val wooSyncStatus: String = "none",
    @SerialName("woo_sync_error") val wooSyncError: String? = null,
) {
    val title get() = name ?: speciesCommon
}

@Serializable
data class Pot(
    val id: String,
    val code: String,
    val name: String,
    val maker: String? = null,
    val origin: String? = null,
    val material: String? = null,
    val glaze: String? = null,
    val shape: String? = null,
    @SerialName("length_cm") val lengthCm: Double? = null,
    @SerialName("width_cm") val widthCm: Double? = null,
    @SerialName("height_cm") val heightCm: Double? = null,
    @SerialName("year_made") val yearMade: Int? = null,
    val condition: String? = null,
    val description: String? = null,
    @SerialName("internal_notes") val internalNotes: String? = null,
    @SerialName("cost_inr") val costInr: Double? = null,
    val status: String,
    @SerialName("for_sale") val forSale: Boolean = false,
    @SerialName("price_inr") val priceInr: Double? = null,
    @SerialName("cover_photo_url") val coverPhotoUrl: String? = null,
    @SerialName("woo_product_id") val wooProductId: Long? = null,
    @SerialName("woo_sync_status") val wooSyncStatus: String = "none",
    @SerialName("woo_sync_error") val wooSyncError: String? = null,
)

@Serializable
data class CareEvent(
    val id: String,
    val seq: Int,
    val type: String,
    @SerialName("performed_on") val performedOn: String,
    @SerialName("performed_by") val performedBy: String? = null,
    val notes: String? = null,
    val details: JsonObject = JsonObject(emptyMap()),
    @SerialName("is_public") val isPublic: Boolean = true,
    val hash: String = "",
)

@Serializable
data class Photo(
    val id: String,
    val url: String,
    val caption: String? = null,
    @SerialName("taken_on") val takenOn: String? = null,
    @SerialName("event_id") val eventId: String? = null,
    @SerialName("created_at") val createdAt: String = "",
)

@Serializable
data class CareTask(
    val id: String,
    @SerialName("bonsai_id") val bonsaiId: String,
    val type: String,
    val title: String? = null,
    @SerialName("due_on") val dueOn: String,
    val notes: String? = null,
    @SerialName("recurrence_days") val recurrenceDays: Int? = null,
    val status: String = "open",
    @SerialName("bonsai_code") val bonsaiCode: String? = null,
    @SerialName("bonsai_label") val bonsaiLabel: String? = null,
)

@Serializable
data class ChainCheck(val valid: Boolean, val length: Int, val head: String)

@Serializable
data class BonsaiDetail(
    val bonsai: Bonsai,
    val pot: Pot? = null,
    val events: List<CareEvent> = emptyList(),
    val photos: List<Photo> = emptyList(),
    val tasks: List<CareTask> = emptyList(),
    val chain: ChainCheck? = null,
)

@Serializable
data class PotDetail(val pot: Pot, val bonsai: Bonsai? = null, val photos: List<Photo> = emptyList())

@Serializable
data class Items<T>(val items: List<T>)

@Serializable
data class SyncResult(
    @SerialName("woo_sync_status") val status: String,
    @SerialName("woo_sync_error") val error: String? = null,
)

@Serializable
data class LookupResult(val kind: String, val id: String, val code: String)

@Serializable
data class CompleteTaskResult(val event: CareEvent, @SerialName("next_task") val nextTask: CareTask? = null)

@Serializable
data class AccessKey(@SerialName("access_key") val accessKey: String)

@Serializable
data class ErrorBody(val error: String? = null)

@Serializable
data class Meta(
    @SerialName("public_base_url") val publicBaseUrl: String,
    @SerialName("woo_enabled") val wooEnabled: Boolean = false,
)
