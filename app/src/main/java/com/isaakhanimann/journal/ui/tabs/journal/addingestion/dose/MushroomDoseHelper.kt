package com.isaakhanimann.journal.ui.tabs.journal.addingestion.dose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.isaakhanimann.journal.ui.tabs.search.substance.roa.toReadableString
import com.isaakhanimann.journal.ui.theme.horizontalPadding

// psilocybin and psilocin as a percent of dry weight: typical, and the low and high that lab tests commonly find.
// dryFraction is how much of a fresh one is left once dried: mushrooms are about 90% water, truffles about 75%
data class MushroomKind(
    val name: String,
    val typicalPercent: Double,
    val lowPercent: Double,
    val highPercent: Double,
    val dryFraction: Double = 0.1,
) {
    fun psilocybinMg(grams: Double, isDried: Boolean, percent: Double = typicalPercent): Double =
        grams * 1000 * percent / 100 * if (isDried) 1.0 else dryFraction

    // the low to high range is read as about two standard deviations either side of the typical amount
    fun customUnit(isDried: Boolean) = MushroomCustomUnit(
        name = "$name, ${if (isDried) "dried" else "fresh"}",
        mgPerGram = psilocybinMg(grams = 1.0, isDried = isDried),
        standardDeviation = (psilocybinMg(1.0, isDried, highPercent) - psilocybinMg(1.0, isDried, lowPercent)) / 4,
    )
}

// a custom unit of one gram of a kind of mushroom, to fill in the create custom unit screen
data class MushroomCustomUnit(val name: String, val mgPerGram: Double, val standardDeviation: Double)

// from Stamets, Beug & Bigwood (1982), the Finnish and Swiss semilanceata studies, Psilocybin Cup lab results
// and Pellegrini et al. (2013) for fresh truffles; cubensis keeps the 1% the custom unit screen gives
val mushroomKinds = listOf(
    MushroomKind("P. cubensis", typicalPercent = 1.0, lowPercent = 0.5, highPercent = 1.5),
    MushroomKind("Penis Envy", typicalPercent = 1.5, lowPercent = 0.7, highPercent = 2.5),
    MushroomKind("Liberty cap (P. semilanceata)", typicalPercent = 1.4, lowPercent = 0.2, highPercent = 2.4),
    MushroomKind("Wavy cap (P. cyanescens)", typicalPercent = 1.2, lowPercent = 0.5, highPercent = 2.0),
    MushroomKind("P. azurescens", typicalPercent = 1.5, lowPercent = 1.0, highPercent = 2.3),
    MushroomKind("Truffles", typicalPercent = 0.4, lowPercent = 0.24, highPercent = 0.7, dryFraction = 0.25),
)

// works out mg psilocybin from the weight of mushrooms, for people who weighed them rather than the psilocybin,
// and fills it in as the dose whenever the weight, kind or dried and fresh change
@Composable
fun MushroomDoseHelper(
    onDoseChange: (doseText: String) -> Unit,
    onSaveAsCustomUnit: (unit: MushroomCustomUnit, grams: Double) -> Unit,
) {
    var gramsText by rememberSaveable { mutableStateOf("") }
    var isDried by rememberSaveable { mutableStateOf(true) }
    var kindIndex by rememberSaveable { mutableIntStateOf(0) }
    val kind = mushroomKinds[kindIndex]
    val focusManager = LocalFocusManager.current

    // an empty or zero weight clears the dose again, so a deleted weight doesn't leave its dose behind
    fun update(newGramsText: String = gramsText, newIsDried: Boolean = isDried, newKindIndex: Int = kindIndex) {
        val wasWeighed = gramsText.isNotEmpty()
        gramsText = newGramsText
        isDried = newIsDried
        kindIndex = newKindIndex
        val grams = newGramsText.toDoubleOrNull()?.takeIf { it > 0 }
        if (grams != null) {
            onDoseChange(mushroomKinds[newKindIndex].psilocybinMg(grams, newIsDried).toReadableString())
        } else if (wasWeighed) {
            onDoseChange("")
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        var isShowingKinds by remember { mutableStateOf(false) }
        Box(modifier = Modifier.wrapContentSize(Alignment.TopStart)) {
            OutlinedButton(onClick = { isShowingKinds = true }, modifier = Modifier.fillMaxWidth()) {
                Text(text = kind.name)
            }
            DropdownMenu(expanded = isShowingKinds, onDismissRequest = { isShowingKinds = false }) {
                mushroomKinds.forEachIndexed { index, option ->
                    DropdownMenuItem(
                        text = { Text(option.name) },
                        onClick = {
                            update(newKindIndex = index)
                            isShowingKinds = false
                        }
                    )
                }
            }
        }
        OutlinedTextField(
            value = gramsText,
            onValueChange = { update(newGramsText = it.replace(oldChar = ',', newChar = '.')) },
            label = { Text("Mushrooms") },
            trailingIcon = {
                Text(text = "g", modifier = Modifier.padding(horizontal = horizontalPadding))
            },
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = isDried, onClick = { update(newIsDried = true) }, label = { Text("Dried") })
            FilterChip(selected = !isDried, onClick = { update(newIsDried = false) }, label = { Text("Fresh") })
        }
        val grams = gramsText.toDoubleOrNull()?.takeIf { it > 0 }
        if (grams != null) {
            val doseText = kind.psilocybinMg(grams, isDried).toReadableString()
            val lowText = kind.psilocybinMg(grams, isDried, kind.lowPercent).toReadableString()
            val highText = kind.psilocybinMg(grams, isDried, kind.highPercent).toReadableString()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "About $doseText mg Psilocybin", style = MaterialTheme.typography.titleMedium)
                    Text(text = "Could be $lowText–$highText mg", style = MaterialTheme.typography.bodyMedium)
                }
                TextButton(onClick = { onSaveAsCustomUnit(kind.customUnit(isDried), grams) }) {
                    Text("Save as custom unit")
                }
            }
        }
        Text(
            text = "Typical lab results. The same strain can test several times stronger or weaker depending on how it was grown, dried and stored, so this is only a rough guide.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}
