package tw.houselens.probe

import org.junit.Assert.*
import org.junit.Test

class ProbeInputTest {
    @Test fun defaultsAndEverySiteUseSupportedRequests() {
        for (site in Site.entries) {
            val q = ProbeInput(siteCode = site.code).query()
            assertEquals("中和區", q.district)
            assertEquals("2000", q.maxTotalPrice.toPlainString())
            assertEquals("20", q.minSizePing.toPlainString())
            assertEquals("R1,R2", q.typeCodes)
        }
        assertEquals("永和區", ProbeInput(district = "永和區").query().district)
        assertEquals("新店區", ProbeInput(district = "新店區").query().district)
    }

    @Test fun rejectsUnboundedNumbersLocationsAndCodes() {
        val invalid = listOf(ProbeInput(siteCode = 99), ProbeInput(district = "板橋區"),
            ProbeInput(price = "-1"), ProbeInput(size = "NaN"), ProbeInput(price = "1e99999"),
            ProbeInput(price = "9".repeat(200)), ProbeInput(age = "2147483648"),
            ProbeInput(rooms = "9".repeat(129)), ProbeInput(types = "R99"),
            ProbeInput(parking = "https://example.com"), ProbeInput(rooms = "0"))
        invalid.forEach { input -> assertThrows(IllegalArgumentException::class.java) { input.query() } }
    }

    @Test fun preservesDecimalsAndChecksEverySiteBeforeCollection() {
        assertEquals("798.00", ProbeInput(price = "798.00", size = "0").query().maxTotalPrice.toPlainString())
        ProbeInput(siteCode = Site.Rakuya.code, types = "R3").query()
        assertThrows(IllegalArgumentException::class.java) {
            ProbeInput(siteCode = Site.Rakuya.code, types = "R3").query(collection = true)
        }
    }
}
