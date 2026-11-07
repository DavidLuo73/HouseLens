package tw.houselens.viewer.detail

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test
import tw.houselens.viewer.data.PriceEntryDto
import tw.houselens.viewer.ui.*

class PriceHistoryTest {
    private fun entry(id:String,day:Int,price:String)=PriceEntryDto(id,"2026-01-${day.toString().padStart(2,'0')}T00:00:00Z",BigDecimal(price))
    @Test fun empty_single_and_zero_base_do_not_claim_change() {
        assertNull(priceStats(emptyList()).high)
        assertNull(priceStats(listOf(entry("1",1,"100"))).change)
        assertNull(priceStats(listOf(entry("1",1,"0"),entry("2",2,"100"))).change)
    }
    @Test fun endpoints_are_chronological_even_when_input_is_newest_first() {
        val stats=priceStats(listOf(entry("3",10,"95"),entry("2",2,"120"),entry("1",1,"100")))
        assertEquals(BigDecimal("120"),stats.high);assertEquals(BigDecimal("95"),stats.low)
        assertEquals(0,BigDecimal("-0.05").compareTo(stats.change))
    }
    @Test fun tied_first_or_last_time_cannot_invent_order() {
        assertNull(priceStats(listOf(entry("a",1,"100"),entry("b",1,"110"),entry("c",2,"95"))).change)
        assertNull(priceStats(listOf(entry("a",1,"100"),entry("b",2,"110"),entry("c",2,"95"))).change)
        val rows=listOf(entry("b",2,"110"),entry("c",2,"95"),entry("a",1,"100"))
        assertEquals(listOf("b","c","a"),chronologicalHistory(rows).sortedByDescending { it.capturedAt }.map { it.id })
        assertNull(rows.first().unitPrice)
    }
    @Test fun coordinates_keep_real_time_gaps_and_close_huge_decimal_differences() {
        val base="99999999999999999999999999999999999999999999999"
        val points=listOf(ChartPoint(0,BigDecimal("$base.01")),ChartPoint(10,BigDecimal("$base.02")),ChartPoint(100,BigDecimal("$base.03")))
        val coordinates=chartCoordinates(points)
        assertEquals(0.1f,coordinates[1].first,0.00001f);assertEquals(0.5f,coordinates[1].second,0.00001f)
        assertEquals(1f,coordinates[2].second,0f)
        (coordinates+chartCoordinates(listOf(ChartPoint(1,BigDecimal.TEN),ChartPoint(1,BigDecimal.TEN))))
            .forEach { assertTrue(it.first.isFinite());assertTrue(it.second.isFinite()) }
        assertEquals(listOf(0,1),overlappingPoints(listOf(ChartPoint(7,BigDecimal.ONE),ChartPoint(7,BigDecimal.TEN),ChartPoint(9,BigDecimal.ONE)),0))
    }
}