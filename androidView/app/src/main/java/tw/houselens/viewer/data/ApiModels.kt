@file:UseSerializers(DecimalSerializer::class)
package tw.houselens.viewer.data

import java.math.BigDecimal
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.*

val viewerJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

object DecimalSerializer : KSerializer<BigDecimal> {
    override val descriptor = PrimitiveSerialDescriptor("Decimal", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): BigDecimal = (decoder as JsonDecoder).decodeJsonElement().jsonPrimitive.content.toBigDecimal()
    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(encoder: Encoder, value: BigDecimal) =
        (encoder as JsonEncoder).encodeJsonElement(JsonUnquotedLiteral(value.toPlainString()))
}

@Serializable
data class PropertyDto(
    val id: String, val currentTotalPrice: BigDecimal,
    val title: String = "", val city: String = "", val district: String = "",
    val address: String? = null, val areaPing: BigDecimal? = null, val floor: String? = null,
    val ageYears: Int? = null, val hasParking: Boolean = false,
    val currentUnitPrice: BigDecimal? = null, val status: Int = 0, val score: BigDecimal? = null,
    val isNew: Boolean = false, val firstSeenAt: String? = null, val lastSeenAt: String? = null,
    val imageUrl: String? = null, val listingUrl: String? = null, val sourceSites: List<Int> = emptyList(),
    val latestChangeFlag: Int? = null, val latestChangePercent: BigDecimal? = null,
    val latestIsBigDrop: Boolean = false, val hasPriceDrop: Boolean = false
)
@Serializable
data class PriceEntryDto(
    @SerialName("Id") val id: String,
    @SerialName("CapturedAt") val capturedAt: String,
    @SerialName("TotalPrice") val totalPrice: BigDecimal,
    @SerialName("UnitPrice") val unitPrice: BigDecimal? = null,
    @SerialName("SourceSite") val sourceSite: Int? = null,
    @SerialName("ChangeFlag") val changeFlag: Int = 0,
    @SerialName("ChangePercent") val changePercent: BigDecimal? = null,
    @SerialName("IsBigDrop") val isBigDrop: Boolean = false
)
@Serializable
data class SourceDto(
    @SerialName("SourceSite") val sourceSite: Int,
    @SerialName("Url") val url: String,
    @SerialName("Title") val title: String = "",
    @SerialName("ImageUrl") val imageUrl: String? = null,
    @SerialName("PostedDate") val postedDate: String? = null
)
data class PropertyDetails(val property: PropertyDto, val sources: List<SourceDto>, val priceHistory: List<PriceEntryDto>)
data class PropertyPage(val total: Int, val page: Int, val pageSize: Int, val items: List<PropertyDto>)
data class DropPage(val total: Int, val items: List<DropDto>)
data class TopGroupDto(val district: String, val items: List<TopItemDto>)
@Serializable
data class TopItemDto(
    val district: String, val rank: Int, val id: String, val totalPrice: BigDecimal,
    val score: BigDecimal, val title: String = "", val unitPrice: BigDecimal? = null,
    val ageYears: Int? = null, val hasParking: Boolean = false
)
@Serializable
data class DropDto(
    val id: String, val newPrice: BigDecimal, val dropPercent: BigDecimal,
    val capturedAt: String, val status: Int, val title: String = "", val district: String = "",
    val address: String? = null, val originalPrice: BigDecimal? = null, val dropAmount: BigDecimal? = null
)
@Serializable
data class DistrictStatsDto(
    val district: String, val propertyCount: Int = 0, val avgUnitPrice: BigDecimal? = null,
    val minTotalPrice: BigDecimal? = null, val maxTotalPrice: BigDecimal? = null,
    val priceBuckets: List<PriceBucketDto> = emptyList(), val trend: List<TrendPointDto> = emptyList(),
    val insufficientData: Boolean = true
)
@Serializable data class PriceBucketDto(val range: String, val count: Int)
@Serializable data class TrendPointDto(val date: String, val avgUnitPrice: BigDecimal?)
@Serializable
data class DistrictOptionDto(
    @SerialName("City") val city: String,
    @SerialName("District") val district: String,
    @SerialName("IsEnabled") val isEnabled: Boolean = true
)
data class DistrictChoice(val name: String, val cityLabel: String?)
@Serializable
data class CrawlRunDto(
    @SerialName("Id") val id: String,
    @SerialName("StartedAt") val startedAt: String,
    @SerialName("FinishedAt") val finishedAt: String? = null,
    @SerialName("Status") val status: Int,
    @SerialName("NewCount") val newCount: Int,
    @SerialName("DelistedCount") val delistedCount: Int,
    @SerialName("BigDropCount") val bigDropCount: Int,
    @Transient val sources: List<SourceResultDto> = emptyList()
)
@Serializable
data class SourceResultDto(
    @SerialName("SourceSite") val sourceSite: Int,
    @SerialName("Success") val success: Boolean,
    @SerialName("FetchedCount") val fetchedCount: Int
)
@Serializable
data class PropertyQuery(
    val districts: List<String> = emptyList(), val sourceSites: List<Int> = emptyList(),
    val minPrice: BigDecimal? = null, val maxPrice: BigDecimal? = null,
    val hasParking: Boolean? = null, val priceDropped: Boolean = false,
    val status: Int = 0, val sortBy: String = "score"
)
