package tw.houselens.viewer.data

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun formatDrop(value: BigDecimal?): String {
    if (value == null) return "—"
    val prefix = when (value.signum()) { -1 -> "▼ "; 1 -> "▲ "; else -> "" }
    return prefix + value.abs().multiply(BigDecimal(100)).setScale(1, RoundingMode.HALF_UP).toPlainString() + "%"
}
fun formatTaipeiTime(isoUtc: String): String =
    OffsetDateTime.parse(isoUtc).atZoneSameInstant(ZoneId.of("Asia/Taipei"))
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
fun formatDateOnly(date: String): String = LocalDate.parse(date).toString()

fun validateQuery(query: PropertyQuery): String? = when {
    query.minPrice?.signum() == -1 || query.maxPrice?.signum() == -1 -> "總價不能為負數"
    query.minPrice != null && query.maxPrice != null && query.minPrice > query.maxPrice -> "最低總價不能高於最高總價"
    query.status !in 0..1 -> "不支援的房源狀態"
    query.sourceSites.any { it !in 0..6 } -> "不支援的平台"
    query.sortBy !in setOf("score", "price", "unitPrice", "newest") -> "不支援的排序"
    else -> null
}
fun mergeDistrictOptions(config: List<DistrictOptionDto>, stats: List<DistrictStatsDto>): List<DistrictChoice> {
    val byName = config.groupBy { it.district }
    return (byName.keys + stats.map { it.district }).sorted().map { name ->
        val cities = byName[name].orEmpty().map { it.city }.filter { it.isNotBlank() }.distinct()
        DistrictChoice(name, when {
            cities.size > 1 -> "所有同名行政區"
            cities.size == 1 -> cities.single()
            else -> null
        })
    }
}
