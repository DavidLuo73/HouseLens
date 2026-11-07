package tw.houselens.viewer.data

import java.math.BigDecimal
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ValueFormatsTest {
    @Test fun decimal_round_trip_preserves_digits_and_null() {
        val p = viewerJson.decodeFromString<PropertyDto>("""{"id":"p","currentTotalPrice":9007199254740993.01,"currentUnitPrice":null,"ignored":true}""")
        assertEquals(BigDecimal("9007199254740993.01"), p.currentTotalPrice)
        assertNull(p.currentUnitPrice)
        assertTrue(viewerJson.encodeToString(p).contains("9007199254740993.01"))
        assertEquals(BigDecimal.ZERO, viewerJson.decodeFromString<PropertyDto>("""{"id":"p","currentTotalPrice":0}""").currentTotalPrice)
    }
    @Test fun fractional_drop_is_percent() {
        assertEquals("▼ 5.0%", formatDrop(BigDecimal("-0.05")))
        assertEquals("▲ 5.0%", formatDrop(BigDecimal("0.05")))
        assertEquals("—", formatDrop(null))
        assertEquals("0.0%", formatDrop(BigDecimal.ZERO))
    }
    @Test fun timestamps_use_taipei_and_date_only_does_not_shift() {
        assertEquals("2026-11-08 00:30", formatTaipeiTime("2026-11-07T16:30:00Z"))
        assertEquals("2026-11-07", formatDateOnly("2026-11-07"))
    }
    @Test fun district_names_merge_without_inventing_city() {
        val choices = mergeDistrictOptions(
            listOf(DistrictOptionDto("臺北市","中正區"), DistrictOptionDto("基隆市","中正區"), DistrictOptionDto("臺北市","大安區",false)),
            listOf(DistrictStatsDto("未知區"))
        )
        assertEquals(3, choices.size)
        assertEquals("所有同名行政區", choices.first { it.name=="中正區" }.cityLabel)
        assertEquals("臺北市", choices.first { it.name=="大安區" }.cityLabel)
        assertNull(choices.first { it.name=="未知區" }.cityLabel)
    }
    @Test fun query_validation_rejects_invalid_prices_status_and_sort() {
        assertNull(validateQuery(PropertyQuery()))
        assertNotNull(validateQuery(PropertyQuery(minPrice=BigDecimal("-1"))))
        assertNotNull(validateQuery(PropertyQuery(minPrice=BigDecimal.TEN,maxPrice=BigDecimal.ONE)))
        assertNotNull(validateQuery(PropertyQuery(status=2)))
        assertNotNull(validateQuery(PropertyQuery(sourceSites=listOf(99))))
        assertNotNull(validateQuery(PropertyQuery(sortBy="SQL")))
    }
}
