package com.isaakhanimann.journal.data.substances

import com.isaakhanimann.journal.data.substances.parse.SubstanceParser
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// the remark shown above the mushroom dose: the gram doses per species it used to list are what the
// mushroom weight picker now works out, and its last sentence was a garbled translation
class MushroomDoseRemarkTest {
    private val remark = SubstanceParser()
        .parseSubstanceFile(File("src/main/assets/Substances.json").readText()).substances
        .first { it.name == "Psilocybin mushrooms" }.dosageRemark!!

    @Test
    fun itSaysTheDoseIsPsilocybinAndPointsToMushroomWeight() {
        assertTrue(remark, remark.contains("mg of psilocybin"))
        assertTrue(remark, remark.contains("Mushroom weight"))
    }

    @Test
    fun itNoLongerListsGramsPerSpeciesOrTheUnfilledAmount() {
        assertFalse(remark, remark.contains("Pointed cone"))
        assertFalse(remark, remark.contains("x mg"))
    }
}
