package tw.houselens.viewer.find

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import tw.houselens.viewer.data.*
import tw.houselens.viewer.ui.HouseLensTheme

class FilterSheetScrollTest {
    @get:Rule val compose = createComposeRule()

    @Test fun pulling_past_top_settles_without_moving_footer() = checkBoundary(top = true)
    @Test fun flinging_past_bottom_settles_without_moving_footer() = checkBoundary(top = false)

    private fun checkBoundary(top: Boolean) {
        var dismissed = 0
        var applied: PropertyQuery? = null
        compose.setContent {
            HouseLensTheme {
                FilterSheet(PropertyQuery(), (1..30).map { DistrictChoice("測試行政區 $it", "測試城市") },
                    { applied = it }, { dismissed++ })
            }
        }
        val scroll = compose.onNode(hasScrollAction())
        val footer = compose.onNodeWithText("套用")
        compose.onNodeWithText("篩選房源").performScrollTo()
        val initialRange = scroll.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertTrue("Fixture must overflow", initialRange.maxValue() > 0f)
        scroll.performTouchInput { swipeUp() }
        assertTrue("Content must still scroll by touch",
            scroll.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value() > 0f)
        compose.onNodeWithText(if (top) "篩選房源" else "已下架").performScrollTo()
        compose.waitForIdle()
        val original = footer.fetchSemanticsNode().boundsInWindow
        compose.mainClock.autoAdvance = false
        repeat(3) {
            scroll.performTouchInput {
                val start = center
                val end = Offset(center.x, center.y + (if (top) 1 else -1) * height * .2f)
                swipe(start, end, 200)
            }
            // Sample well after release: an endless anchor/size animation must not pass as idle.
            val positions = (1..20).map {
                compose.mainClock.advanceTimeBy(160)
                footer.fetchSemanticsNode().boundsInWindow.top
            }
            println("FILTER_BOUNDARY top=$top positions=$positions original=${original.top}")
            assertTrue("Footer keeps bouncing after release: $positions",
                positions.takeLast(10).max() - positions.takeLast(10).min() <= 1f)
            assertTrue("Scrolling at a boundary must not move the whole filter sheet: $positions",
                positions.all { kotlin.math.abs(it - original.top) <= 1f })
            assertEquals("Footer must return to its original position", original.top, positions.last(), 1f)
        }
        compose.mainClock.autoAdvance = true
        val range = scroll.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertEquals(if (top) 0f else range.maxValue(), range.value(), 1f)
        compose.runOnIdle { assertEquals(0, dismissed); assertNull(applied) }
        if (top) {
            compose.onNodeWithText("關閉篩選").performClick()
            compose.runOnIdle { assertEquals(1, dismissed); assertNull(applied) }
        } else {
            compose.onNodeWithText("已下架").performClick()
            footer.performClick()
            compose.runOnIdle { assertEquals(1, applied!!.status) }
        }
    }
}
