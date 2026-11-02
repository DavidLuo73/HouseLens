package tw.houselens.probe

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ProbeRulesTest {
    private val query = ProbeQuery(Site.CtHouse, "新北市", "中和區", BigDecimal("2000.50"))
    private val listing = ProbeListing(
        city = "新北市", district = "中和區", address = null,
        areaPing = BigDecimal("28.5"), floor = null, ageYears = 20,
        hasParking = true, totalPrice = BigDecimal("2000.50"), unitPrice = null,
        sourceSite = Site.CtHouse, sourceListingKey = "A", title = "測試物件",
        url = "https://example.invalid/A", postedDate = null,
    )

    @Test
    fun plannedDistrictsAreValidForEverySite() {
        for (site in Site.entries) {
            for (district in listOf("中和區", "永和區", "新店區")) {
                validateQuery(query.copy(site = site, district = district))
            }
        }
    }

    @Test
    fun rejectsUnsupportedLocationsAndNegativeLimits() {
        val invalid = listOf(
            query.copy(city = "臺北市"), query.copy(district = "板橋區"),
            query.copy(district = ""), query.copy(maxTotalPrice = BigDecimal("-0.01")),
            query.copy(minSizePing = BigDecimal("-0.01")), query.copy(maxAgeYears = -1),
        )
        invalid.forEach { assertThrows(IllegalArgumentException::class.java) { validateQuery(it) } }
    }

    @Test
    fun zeroLimitsAndSourceSpecificCodeFormatsRemainValid() {
        validateQuery(query.copy(maxTotalPrice = BigDecimal.ZERO, typeCodes = "電梯大樓,公寓"))
        validateQuery(query.copy(typeCodes = "apartment,dalou,huaxia", rooms = "2,3,4,5~", parkingCodes = "PF,PM"))
        validateQuery(query.copy(typeCodes = "", useCode = ""))
    }

    @Test
    fun rejectsOversizedOrUnsafeQueryCodes() {
        val invalid = listOf(
            query.copy(rooms = "1".repeat(129)), query.copy(typeCodes = "R".repeat(129)),
            query.copy(useCode = "1".repeat(129)), query.copy(parkingCodes = "P".repeat(129)),
            query.copy(typeCodes = "R1&price=0"), query.copy(rooms = "2,,3"),
            query.copy(useCode = "1\n"), query.copy(parkingCodes = "PF/PM"),
        )
        invalid.forEach { assertThrows(IllegalArgumentException::class.java) { validateQuery(it) } }
    }

    @Test
    fun rejectsMissingIdentityWrongSourceAndCrossDistrictRecommendations() {
        assertEquals("missing_id", exclusionReason(listing.copy(sourceListingKey = " "), query))
        assertEquals("wrong_source", exclusionReason(listing.copy(sourceSite = Site.F591), query))
        assertEquals("wrong_city", exclusionReason(listing.copy(city = "臺北市"), query))
        assertEquals("wrong_district", exclusionReason(listing.copy(district = "板橋區"), query))
    }

    @Test
    fun requiresKnownPositivePricesAndKeepsExactUpperBoundary() {
        assertEquals("invalid_price", exclusionReason(listing.copy(totalPrice = BigDecimal.ZERO), query))
        assertEquals("invalid_price", exclusionReason(listing.copy(totalPrice = BigDecimal("-1")), query))
        assertEquals("unknown_price", exclusionReason(listing.copy(unknownFields = setOf("totalPrice")), query))
        assertEquals("over_price", exclusionReason(listing.copy(totalPrice = BigDecimal("2000.501")), query))
        assertNull(exclusionReason(listing, query))
        assertNull(exclusionReason(listing, query.copy(maxTotalPrice = BigDecimal.ZERO)))
    }

    @Test
    fun onlyKnownAgeOrParkingCanProveExclusion() {
        val restricted = query.copy(maxAgeYears = 20, parkingCodes = "PF,PM")
        assertNull(exclusionReason(listing, restricted))
        assertEquals("over_age", exclusionReason(listing.copy(ageYears = 21), restricted))
        assertEquals("no_parking", exclusionReason(listing.copy(hasParking = false), restricted))
        assertNull(exclusionReason(listing.copy(ageYears = null), restricted))
        val unknown = listing.copy(ageYears = 99, hasParking = false, unknownFields = setOf("ageYears", "hasParking"))
        assertNull(exclusionReason(unknown, restricted))
        assertEquals(99, unknown.ageYears)
        assertEquals(false, unknown.hasParking)
        assertEquals(setOf("ageYears", "hasParking"), unknown.unknownFields)
        assertNull(unknown.address)
        assertNull(unknown.unitPrice)
    }

    @Test
    fun leavesIncomparableAreaDefinitionsToSourceFilters() {
        assertNull(exclusionReason(listing.copy(areaPing = BigDecimal("8.18")), query.copy(minSizePing = BigDecimal("20"))))
    }

    @Test
    fun fullyFilteredPageStillContinues() {
        val keys = (1..10).map { "A$it" }
        val page = PageResult(PageKind.RESULTS, keys, emptyList(), keys.associateWith { "over_price" }, NextPage.MORE, "next_link")
        assertEquals(PageDecision.CONTINUE, nextDecision(page, emptySet(), 0, 30))
    }

    @Test
    fun acceptedCountsDoNotDeterminePagination() {
        val page = resultPage()
        assertEquals(PageDecision.CONTINUE, nextDecision(page, emptySet(), 0, 30))
        assertEquals(PageDecision.CONTINUE, nextDecision(page.copy(listings = listOf(listing)), emptySet(), 0, 30))
        assertEquals(PageDecision.CONTINUE, nextDecision(page.copy(next = NextPage.UNKNOWN), emptySet(), 0, 30))
    }

    @Test
    fun completionRequiresExplicitEndEvidence() {
        val empty = PageResult(PageKind.EMPTY, emptyList(), emptyList(), emptyMap(), NextPage.END, "zero_total")
        assertEquals(PageDecision.COMPLETE, nextDecision(empty, emptySet(), 0, 30))
        assertEquals(PageDecision.COMPLETE, nextDecision(resultPage().copy(next = NextPage.END), emptySet(), 0, 30))
        assertEquals(PageDecision.INCONCLUSIVE, nextDecision(empty.copy(next = NextPage.UNKNOWN), emptySet(), 0, 30))
        assertEquals(PageDecision.INCONCLUSIVE, nextDecision(empty.copy(next = NextPage.MORE), emptySet(), 0, 30))
        assertEquals(PageDecision.INCONCLUSIVE, nextDecision(empty.copy(evidence = ""), emptySet(), 0, 30))
        assertEquals(PageDecision.INCONCLUSIVE, nextDecision(resultPage().copy(next = NextPage.END, evidence = " "), emptySet(), 0, 30))
    }

    @Test
    fun challengesAndInvalidStructuresNeverComplete() {
        val page = resultPage().copy(next = NextPage.END)
        assertEquals(PageDecision.WAIT_FOR_USER, nextDecision(page.copy(kind = PageKind.CHALLENGE), emptySet(), 29, 30))
        assertEquals(PageDecision.FAIL, nextDecision(page.copy(kind = PageKind.INVALID), emptySet(), 29, 30))
        assertEquals(PageDecision.FAIL, nextDecision(page.copy(rawKeys = emptyList()), emptySet(), 0, 30))
        assertEquals(PageDecision.FAIL, nextDecision(page.copy(rawKeys = listOf("")), emptySet(), 0, 30))
        assertEquals(PageDecision.FAIL, nextDecision(page.copy(kind = PageKind.EMPTY), emptySet(), 0, 30))
    }

    @Test
    fun limitsTruncateUnlessEndIsProven() {
        for (next in listOf(NextPage.MORE, NextPage.UNKNOWN)) {
            assertEquals(PageDecision.TRUNCATE, nextDecision(resultPage().copy(next = next), emptySet(), 29, 30))
        }
        assertEquals(PageDecision.COMPLETE, nextDecision(resultPage().copy(next = NextPage.END), emptySet(), 29, 30))
    }

    @Test
    fun repeatPagesNeedEndEvidenceButPartialOverlapCanContinue() {
        val page = resultPage()
        assertEquals(PageDecision.INCONCLUSIVE, nextDecision(page, setOf("A"), 1, 30))
        assertEquals(PageDecision.INCONCLUSIVE, nextDecision(page.copy(next = NextPage.UNKNOWN), setOf("A"), 1, 30))
        assertEquals(PageDecision.COMPLETE, nextDecision(page.copy(next = NextPage.END), setOf("A"), 1, 30))
        assertEquals(PageDecision.CONTINUE, nextDecision(page.copy(rawKeys = listOf("A", "B")), setOf("A"), 1, 30))
    }

    @Test
    fun rejectsInvalidPaginationBounds() {
        for ((index, limit) in listOf(-1 to 30, 0 to 0, 30 to 30)) {
            assertThrows(IllegalArgumentException::class.java) { nextDecision(resultPage(), emptySet(), index, limit) }
        }
    }

    private fun resultPage() = PageResult(
        PageKind.RESULTS, listOf("A"), emptyList(), mapOf("A" to "over_price"), NextPage.MORE, "next_link",
    )
}
