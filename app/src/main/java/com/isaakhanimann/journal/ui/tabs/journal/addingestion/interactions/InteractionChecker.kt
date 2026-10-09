/*
 * Copyright (c) 2022-2023. Isaak Hanimann.
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

import com.isaakhanimann.journal.data.substances.classes.InteractionType
import com.isaakhanimann.journal.data.substances.classes.Interactions
import com.isaakhanimann.journal.data.substances.classes.Substance
import com.isaakhanimann.journal.data.substances.repositories.SubstanceRepositoryInterface
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class InteractionChecker @Inject constructor(
    private val substanceRepo: SubstanceRepositoryInterface,
) {
    fun getInteractionBetween(aName: String, bName: String): Interaction? {
        val a = Checked(aName, resolve(aName))
        val b = Checked(bName, resolve(bName))
        if (a.names.any { name -> b.names.any { it.equals(name, ignoreCase = true) } }) {
            return null
        }
        val interactionFromAToB = getInteractionFromAToB(a, b)
        val interactionFromBToA = getInteractionFromAToB(b, a)
        val interactionType = listOfNotNull(interactionFromAToB, interactionFromBToA)
            .maxByOrNull { it.dangerCount } ?: return null
        return Interaction(
            aName = aName,
            bName = bName,
            interactionType = interactionType,
        )
    }

    // a name as it was logged, together with the substance it stands for, if any
    private class Checked(name: String, val substance: Substance?) {
        val names = listOfNotNull(name, substance?.name).distinct()
    }

    // a custom substance named "mdma" or with a common name like "Molly" is checked as MDMA,
    // but a common name only if no other substance shares it and it is longer than one letter
    private fun resolve(name: String): Substance? {
        substanceRepo.getSubstance(name)?.let { return it }
        val substanceName = substanceNamesByLowercaseName[name.lowercase()]
            ?: substanceNamesByCommonName[name.lowercase()]
            ?: return null
        return substanceRepo.getSubstance(substanceName)
    }

    private val substanceNamesByLowercaseName: Map<String, String> by lazy {
        substanceRepo.getAllSubstances().associate { it.name.lowercase() to it.name }
    }

    private val substanceNamesByCommonName: Map<String, String> by lazy {
        substanceRepo.getAllSubstances()
            .flatMap { substance -> substance.commonNames.map { it.lowercase() to substance.name } }
            .filter { (commonName, _) -> commonName.length > 1 }
            .distinct()
            .groupBy({ it.first }, { it.second })
            .filterValues { it.size == 1 }
            .mapValues { it.value.first() }
    }

    private fun getInteractionFromAToB(a: Checked, b: Checked): InteractionType? {
        val interactions = a.substance?.interactions ?: return null
        return getDirectInteraction(interactions, b.names)
            ?: getWildcardInteraction(interactions, b.names)
            ?: b.substance?.let { getClassInteraction(interactions, it.categories) }
    }

    private fun getDirectInteraction(interactions: Interactions, names: List<String>) =
        firstMatchingType(interactions) { isDirectMatch(it, names) }

    private fun getWildcardInteraction(interactions: Interactions, names: List<String>) =
        firstMatchingType(interactions) { isWildcardMatch(it, names) }

    private fun getClassInteraction(interactions: Interactions, categories: List<String>) =
        firstMatchingType(interactions) { isClassMatch(it, categories) }

    private fun firstMatchingType(
        interactions: Interactions,
        matches: (List<String>) -> Boolean
    ): InteractionType? = when {
        matches(interactions.dangerous) -> InteractionType.DANGEROUS
        matches(interactions.unsafe) -> InteractionType.UNSAFE
        matches(interactions.uncertain) -> InteractionType.UNCERTAIN
        else -> null
    }

    // whole words only, so "Antidepressants" doesn't match the category depressant
    private fun isClassMatch(interactions: List<String>, categories: List<String>): Boolean {
        // a group like "Tricyclic antidepressants" only matches its members, not every antidepressant
        val words = extendAndCleanInteractions(interactions)
            .filter { it.lowercase() !in groupMembers }
            .flatMap { it.lowercase().split(Regex("[\\s,/()]+")) }
            .toSet()
        return categories.any { category ->
            val lowercased = category.lowercase()
            lowercased in words || "${lowercased}s" in words
        }
    }

    private fun isWildcardMatch(interactions: List<String>, names: List<String>): Boolean {
        return extendAndCleanInteractions(interactions).any { interaction ->
            val regex = wildcardRegex(interaction) ?: return@any false
            names.any { regex.matches(it) }
        }
    }

    private fun isDirectMatch(interactions: List<String>, names: List<String>): Boolean {
        return extendAndCleanInteractions(interactions).any { interaction ->
            names.any { it.equals(interaction, ignoreCase = true) }
        }
    }

    private fun extendAndCleanInteractions(interactions: List<String>): List<String> {
        return interactions.flatMap { name ->
            listOf(name) + (groupMembers[name.lowercase()] ?: emptyList())
        }.distinct()
    }

    private val groupMembers = mapOf(
        "substituted amphetamines" to substitutedAmphetamines,
        "serotonin releasers" to listOf("MDMA", "MDA", "Mephedrone"),
        "maoi" to listOf(
            "Ayahuasca", "Syrian rue", "Harmala alkaloids", "Harmaline", "Harmine",
            "Banisteriopsis caapi", "Moclobemide", "Phenelzine", "Tranylcypromine",
            "Isocarboxazid", "Selegiline", "Rasagiline"
        ),
        "ssris" to listOf("Fluoxetine", "Sertraline", "Paroxetine", "Citalopram", "Escitalopram", "Fluvoxamine"),
        "snris" to listOf("Venlafaxine", "Desvenlafaxine", "Duloxetine", "Milnacipran", "Levomilnacipran"),
        "tricyclic antidepressants" to listOf(
            "Amitriptyline", "Nortriptyline", "Clomipramine", "Imipramine", "Desipramine", "Doxepin"
        ),
    )

    companion object {
        // in names like "DOx", "25x-NBOMe" or "5-MeO-xxT" an x stands for any part of the name,
        // but an x after a lowercase letter, as in "Dextromethorphan", is just a letter
        fun wildcardRegex(interaction: String): Regex? {
            val pattern = StringBuilder()
            var hasWildcard = false
            var previousWasWildcard = false
            interaction.forEachIndexed { index, char ->
                val previous = interaction.getOrNull(index - 1)
                val isLetter = previous != null && previous.isLowerCase() && !previousWasWildcard
                if (char.equals('x', ignoreCase = true) && !isLetter) {
                    // consecutive x like in "xxT" become a single wildcard
                    if (!previousWasWildcard) pattern.append("\\S+")
                    hasWildcard = true
                    previousWasWildcard = true
                } else {
                    pattern.append(Regex.escape(char.toString()))
                    previousWasWildcard = false
                }
            }
            return if (hasWildcard) Regex(pattern.toString(), RegexOption.IGNORE_CASE) else null
        }
    }
}

data class Interaction(
    val aName: String,
    val bName: String,
    val interactionType: InteractionType,
)

private val substitutedAmphetamines = listOf(
    "Amphetamine",
    "Methamphetamine",
    "Ethylamphetamine",
    "Propylamphetamine",
    "Isopropylamphetamine",
    "Bromo-DragonFLY",
    "Lisdexamfetamine",
    "Clobenzorex",
    "Dimethylamphetamine",
    "Selegiline",
    "Benzphetamine",
    "Ortetamine",
    "3-Methylamphetamine",
    "4-Methylamphetamine",
    "4-MMA",
    "Xylopropamine",
    "ß-methylamphetamine",
    "3-phenylmethamphetamine",
    "2-FA",
    "2-FMA",
    "2-FEA",
    "3-FA",
    "3-FMA",
    "3-FEA",
    "Fenfluramine",
    "Norfenfluramine",
    "4-FA",
    "4-FMA",
    "4-CA",
    "4-BA",
    "4-IA",
    "DCA",
    "4-HA",
    "4-HMA",
    "3,4-DHA",
    "OMA",
    "3-MA",
    "MMMA",
    "MMA",
    "PMA",
    "PMMA",
    "PMEA",
    "4-ETA",
    "TMA-2",
    "TMA-6",
    "4-MTA",
    "5-API",
    "Cathine",
    "Phenmetrazine",
    "3-FPM",
    "Prolintane"
)
