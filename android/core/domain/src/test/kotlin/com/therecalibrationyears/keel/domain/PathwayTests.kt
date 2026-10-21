package com.therecalibrationyears.keel.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Raw values are stored on the profile and in backups, so they must match iOS exactly. */
class PathwayTests {
    @Test fun rawValuesMatchIos() =
        assertEquals(listOf("natural", "medical", "both", "figuring"), Pathway.entries.map { it.raw })

    @Test fun unknownRawIsNull() {
        assertNull(Pathway.fromRaw("unknown"))
        assertNull(Pathway.fromRaw(null))
        assertEquals(Pathway.BOTH, Pathway.fromRaw("both"))
    }
}
