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
import com.isaakhanimann.journal.data.substances.classes.Substance
import com.isaakhanimann.journal.data.substances.repositories.SubstanceRepositoryInterface
import java.util.concurrent.ConcurrentHashMap
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
        val lowercased = name.lowercase()
        val substanceName = substanceNamesByLowercaseName[lowercased]
            ?: commonNameCorrections[lowercased]
            ?: substanceNamesByCommonName[lowercased]
            ?: return null
        return substanceRepo.getSubstance(substanceName)
    }

    private val substanceNamesByLowercaseName: Map<String, String> by lazy {
        substanceRepo.getAllSubstances().associate { it.name.lowercase() to it.name }
    }

    private val substanceNamesByCommonName: Map<String, String> by lazy {
        substanceRepo.getAllSubstances()
            .flatMap { substance -> substance.commonNames.map { it.lowercase() to substance.name } }
            .filter { (commonName, _) ->
                commonName.length > 1 && commonName !in everydayWords && commonName !in commonNameCorrections
            }
            .distinct()
            .groupBy({ it.first }, { it.second })
            .filterValues { it.size == 1 }
            .mapValues { it.value.first() }
    }

    private fun getInteractionFromAToB(a: Checked, b: Checked): InteractionType? {
        val levels = prepare(a.substance ?: return null)
        val lowercaseNames = b.names.map { it.lowercase() }
        return levels.firstOrNull { level -> lowercaseNames.any { it in level.names } }?.type
            ?: levels.firstOrNull { level ->
                level.wildcards.any { regex -> b.names.any { regex.matches(it) } }
            }?.type
            ?: b.substance?.let { substance ->
                levels.firstOrNull { level -> substance.categories.any { level.matchesCategory(it) } }?.type
            }
    }

    // the interactions of one danger level, prepared once per substance
    private class Level(
        val type: InteractionType,
        val names: Set<String>,
        val wildcards: List<Regex>,
        private val classWords: Set<String>,
    ) {
        // whole words only, so "Antidepressants" doesn't match the category depressant
        fun matchesCategory(category: String): Boolean {
            val lowercased = category.lowercase()
            return lowercased in classWords || "${lowercased}s" in classWords
        }
    }

    private val levelsBySubstance = ConcurrentHashMap<String, List<Level>>()

    private fun prepare(substance: Substance): List<Level> = levelsBySubstance.getOrPut(substance.name) {
        val interactions = substance.interactions ?: return@getOrPut emptyList()
        listOf(
            InteractionType.DANGEROUS to interactions.dangerous,
            InteractionType.UNSAFE to interactions.unsafe,
            InteractionType.UNCERTAIN to interactions.uncertain,
        ).map { (type, entries) -> prepareLevel(type, entries) }
    }

    private fun prepareLevel(type: InteractionType, entries: List<String>): Level {
        val extended = entries.flatMap { name ->
            listOf(name) + (groupMembers[name.lowercase()] ?: emptyList())
        }.distinct()
        return Level(
            type = type,
            names = extended.map { it.lowercase() }.toSet(),
            wildcards = extended.mapNotNull { wildcardRegex(it) },
            // a group like "Tricyclic antidepressants" only matches its members, not every antidepressant
            classWords = extended
                .filter { it.lowercase() !in groupMembers }
                .flatMap { it.lowercase().split(Regex("[\\s,/()]+")) }
                .toSet()
        )
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
        // in names like "DOx", "25x-NBOMe", "2C-T-X" or "5-MeO-xxT" an x stands for any part of the name,
        // but not in words and abbreviations like "Dextromethorphan", "Oxycodone" or "MXE"
        fun wildcardRegex(interaction: String): Regex? {
            val pattern = StringBuilder()
            var hasWildcard = false
            var previousWasWildcard = false
            interaction.forEachIndexed { index, char ->
                val previous = interaction.getOrNull(index - 1)
                val next = interaction.getOrNull(index + 1)
                val isWildcard = (previousWasWildcard && char.equals('x', ignoreCase = true)) || when (char) {
                    'x' -> !previous.isLowercaseLetter() && !(next.isLowercaseLetter() && next != 'x')
                    'X' -> previous == '-' && (next == null || next == '-')
                    else -> false
                }
                if (isWildcard) {
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

        private fun Char?.isLowercaseLetter() = this != null && isLetter() && isLowerCase()
    }
}

// common names that are everyday words, so a custom substance named like that isn't checked as the substance
private val everydayWords = setOf(
    "aces", "al", "aurora", "beans", "beautiful", "bees", "blow", "boat", "brown", "bud", "buttons",
    "cactus", "cat", "circles", "clam", "colour", "crank", "crystal", "dust", "eternity", "eve", "flux",
    "foxtrot", "girl", "glass", "grass", "gravel", "green", "herb", "ice", "jaguar", "jet", "juice", "junk",
    "kitty", "lambda", "lean", "ma", "mal", "nexus", "pep", "pot", "purple", "rolls", "rosy", "sass",
    "sauce", "shard", "smack", "snow", "spice", "syrup", "tabs", "the light", "toad", "tree", "tweak",
    "vegetal", "wet", "white", "zoom",
)

// the substance data lists these as common names of Buspirone, but they mean DMT
private val commonNameCorrections = mapOf(
    "dimethyltryptamine" to "DMT",
    "dmitri" to "DMT",
)

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
