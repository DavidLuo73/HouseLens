package tw.houselens.probe

import tw.houselens.probe.sources.*

internal val probeSources = listOf(F591Source, RakuyaSource, YungchingSource, HBHousingSource,
    SinyiSource, CtHouseSource, TwHouseSource).associateBy { it.site }

data class ProbeInput(
    val siteCode: Int = 0, val district: String = "中和區", val price: String = "2000",
    val size: String = "20", val rooms: String = "", val types: String = "R1,R2",
    val age: String = "0", val parking: String = "",
) {
    fun query(collection: Boolean = false): ProbeQuery {
        fun number(value: String): java.math.BigDecimal {
            require(Regex("[0-9]{1,8}(\\.[0-9]{1,2})?").matches(value)) { "請輸入非負數，最多八位整數與兩位小數" }
            return value.toBigDecimal()
        }
        require(Regex("[0-9]{1,3}").matches(age)) { "屋齡須為 0–999 的整數" }
        val site = Site.entries.singleOrNull { it.code == siteCode }
        require(site != null) { "不支援的來源" }
        val query = ProbeQuery(site, "新北市", district, number(price), number(size), rooms, types, "1", age.toInt(), parking)
        validateQuery(query)
        (if (collection) probeSources.keys else listOf(site)).forEach {
            probeSources.getValue(it).request(query.copy(site = it), 0)
        }
        return query
    }
}
