/*
 * Copyright (c) 2022. Isaak Hanimann.
 * This file is part of PsychonautWiki Journal.
 *
 * PsychonautWiki Journal is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * PsychonautWiki Journal is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with PsychonautWiki Journal.  If not, see https://www.gnu.org/licenses/gpl-3.0.en.html.
 */

package com.isaakhanimann.journal.ui.tabs.journal.addingestion.interactions

import com.isaakhanimann.journal.data.substances.classes.Category
import com.isaakhanimann.journal.data.substances.classes.InteractionType
import com.isaakhanimann.journal.data.substances.classes.Interactions
import com.isaakhanimann.journal.data.substances.classes.Substance
import com.isaakhanimann.journal.data.substances.classes.SubstanceWithCategories
import com.isaakhanimann.journal.data.substances.repositories.SubstanceRepositoryInterface
import com.isaakhanimann.journal.ui.tabs.journal.experience.timeline.testSubstance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private class FakeSubstanceRepository(private val substances: List<Substance>) : SubstanceRepositoryInterface {
    override fun getAllSubstances() = substances
    override fun getAllSubstancesWithCategories(): List<SubstanceWithCategories> = emptyList()
    override fun getAllCategories(): List<Category> = emptyList()
    override fun getSubstance(substanceName: String) = substances.firstOrNull { it.name == substanceName }
    override fun getCategory(categoryName: String): Category? = null
    override fun getSubstanceWithCategories(substanceName: String): SubstanceWithCategories? = null
}

private fun substance(
    name: String,
    categories: List<String> = emptyList(),
    commonNames: List<String> = emptyList(),
    dangerous: List<String> = emptyList(),
    unsafe: List<String> = emptyList(),
) = testSubstance(name).copy(
    categories = categories,
    commonNames = commonNames,
    interactions = Interactions(dangerous = dangerous, unsafe = unsafe, uncertain = emptyList())
)

class InteractionCheckerTest {
    private val lithium = substance(
        "Lithium",
        dangerous = listOf("DOx", "25x-NBOMe", "2C-x", "5-MeO-xxT", "2C-T-X")
    )
    private val mdma = substance(
        "MDMA",
        categories = listOf("entactogen", "stimulant"),
        commonNames = listOf("MDMA", "Molly", "Ecstasy", "E", "Speed"),
        dangerous = listOf("Antidepressants", "MAOI", "SSRIs"),
    )
    private val amphetamine = substance("Amphetamine", categories = listOf("stimulant"), commonNames = listOf("Speed"))
    private val alcohol = substance("Alcohol", categories = listOf("depressant"))
    private val mirtazapine = substance("Mirtazapine", categories = listOf("depressant", "antidepressant"))
    private val tramadol = substance("Tramadol", unsafe = listOf("Stimulants", "Dextromethorphan"))
    private val checker = InteractionChecker(
        FakeSubstanceRepository(
            listOf(
                lithium, mdma, amphetamine, alcohol, mirtazapine, tramadol,
                substance("DOB"), substance("25I-NBOMe"), substance("25C-NBOH"), substance("2C-B"),
                substance("5-MeO-DMT"), substance("2C-T-7"), substance("Ayahuasca"),
            )
        )
    )

    private fun type(a: String, b: String) = checker.getInteractionBetween(a, b)?.interactionType

    @Test
    fun singleLetterWildcardsMatch() {
        assertEquals(InteractionType.DANGEROUS, type("Lithium", "DOB"))
        assertEquals(InteractionType.DANGEROUS, type("25I-NBOMe", "Lithium"))
        assertEquals(InteractionType.DANGEROUS, type("Lithium", "2C-B"))
        assertEquals(InteractionType.DANGEROUS, type("Lithium", "5-MeO-DMT"))
        assertEquals(InteractionType.DANGEROUS, type("Lithium", "2C-T-7"))
    }

    @Test
    fun wildcardKeepsTheRestOfTheName() {
        assertNull(type("Lithium", "25C-NBOH"))
    }

    @Test
    fun aLowercaseXInsideAWordIsNotAWildcard() {
        assertNull(type("Tramadol", "Deutromethorphan"))
        assertEquals(InteractionType.UNSAFE, type("Tramadol", "Dextromethorphan"))
    }

    @Test
    fun classesMatchWholeWordsOnly() {
        assertNull(type("MDMA", "Alcohol"))
        assertEquals(InteractionType.DANGEROUS, type("MDMA", "Mirtazapine"))
        assertEquals(InteractionType.UNSAFE, type("Tramadol", "Amphetamine"))
    }

    @Test
    fun groupsMatchTheirMembersIgnoringCase() {
        assertEquals(InteractionType.DANGEROUS, type("MDMA", "Ayahuasca"))
        assertEquals(InteractionType.DANGEROUS, type("Moclobemide", "MDMA"))
        assertEquals(InteractionType.DANGEROUS, type("MDMA", "sertraline"))
    }

    @Test
    fun uniqueCommonNamesAreCheckedAsTheirSubstance() {
        assertEquals(InteractionType.DANGEROUS, type("Molly", "Ayahuasca"))
        assertEquals(InteractionType.DANGEROUS, type("Moclobemide", "ecstasy"))
        assertNull(type("Molly", "MDMA"))
    }

    @Test
    fun ambiguousOrSingleLetterCommonNamesAreNotResolved() {
        assertNull(type("Speed", "Tramadol"))
        assertNull(type("E", "Ayahuasca"))
    }
}
