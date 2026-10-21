package com.therecalibrationyears.keel.checkin

import com.therecalibrationyears.keel.data.db.SymptomEntity
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class CheckInModelTests {
    private fun s(id: String, name: String, raw: String = "body", custom: Boolean = false) =
        SymptomEntity(id, name, raw, isCustom = custom, ownerID = "", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)

    @Test fun tappingCyclesMildModerateSevereThenOff() {
        var d = CheckInDraft()
        val seen = (1..4).map { d = d.toggled("A"); d.selected["A"] }
        assertEquals(listOf(1, 2, 3, null), seen)
    }

    /** The default row keeps its opening order; picks from "more" join after it. */
    @Test fun quickChipsAreTheDefaultsThenWhateverShePickedInMore() {
        val active = listOf(s("a", "Anxious"), s("h", "Hot flushes"), s("t", "Tinnitus"), s("x", "Unpicked"))
        val chips = quickChips(active, defaultOrder = listOf("h", "a", "gone"), selected = mapOf("t" to 2, "a" to 1))
        assertEquals(listOf("h", "a", "t"), chips.map { it.id })
    }

    @Test fun pickerShowsEveryBuiltInGroupThenHerOwnInCatalogOrder() {
        val groups = pickerGroups(
            listOf(s("1", "Night sweats", "sleep"), s("2", "Trouble sleeping", "sleep"), s("3", "Garden knees", "Garden aches", custom = true),
                s("4", "Zz custom", "sleep", custom = true)),
        )
        assertEquals(10, groups.size) // nine built-in groups, even empty, plus hers last
        assertEquals("Garden aches", groups.last().label)
        val sleep = groups.first()
        assertEquals("Sleep & rest", sleep.label)
        // Catalog order (Trouble sleeping before Night sweats), her own after.
        assertEquals(listOf("Trouble sleeping", "Night sweats", "Zz custom"), sleep.items.map { it.name })
        val intimacy = groups.first { it.raw == "intimacy" }
        assertEquals(true, intimacy.isSensitive)
        assertEquals(true, intimacy.items.isEmpty())
    }
}
