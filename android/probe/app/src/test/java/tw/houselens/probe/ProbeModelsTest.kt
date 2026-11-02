package tw.houselens.probe

import org.junit.Assert.assertEquals
import org.junit.Test

class ProbeModelsTest {
    @Test
    fun sourceCodesMatchExistingDatabase() {
        assertEquals(
            listOf(0, 1, 2, 3, 4, 5, 6),
            listOf(
                Site.F591, Site.Rakuya, Site.Sinyi, Site.Yungching,
                Site.TwHouse, Site.HBHousing, Site.CtHouse,
            ).map { it.code },
        )
    }
}
