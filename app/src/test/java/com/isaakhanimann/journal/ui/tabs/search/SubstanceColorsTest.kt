package com.isaakhanimann.journal.ui.tabs.search

import com.isaakhanimann.journal.data.room.experiences.entities.AdaptiveColor
import com.isaakhanimann.journal.data.room.experiences.entities.SubstanceCompanion
import org.junit.Assert.assertEquals
import org.junit.Test

class SubstanceColorsTest {
    private fun substance(name: String) = SubstanceModel(
        name = name,
        commonNames = emptyList(),
        categories = emptyList(),
        hasSaferUse = false,
        hasInteractions = false
    )

    @Test
    fun aSubstanceTakesTheColorTheUserGaveIt() {
        val colored = withCompanionColors(
            substances = listOf(substance("MDMA"), substance("LSD")),
            companions = listOf(SubstanceCompanion(substanceName = "MDMA", color = AdaptiveColor.PINK))
        )
        assertEquals(listOf(AdaptiveColor.PINK, null), colored.map { it.color })
    }

    @Test
    fun withoutColorsTheSubstancesStayAsTheyAre() {
        val substances = listOf(substance("MDMA"))
        assertEquals(substances, withCompanionColors(substances, companions = emptyList()))
    }
}
