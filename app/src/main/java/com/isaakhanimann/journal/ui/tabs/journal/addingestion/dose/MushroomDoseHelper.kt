package com.isaakhanimann.journal.ui.tabs.journal.addingestion.dose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
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

// what was entered on the mushroom weight side of the dose screen, kept by the screen so it survives
// switching to the psilocybin dose and back
data class MushroomWeight(val gramsText: String = "", val isDried: Boolean = true, val kindIndex: Int = 0) {
    val kind get() = mushroomKinds[kindIndex]
    val grams get() = gramsText.toDoubleOrNull()?.takeIf { it > 0 }

    // the psilocybin it works out to, or empty until it's weighed
    val doseText get() = grams?.let { kind.psilocybinMg(it, isDried).toReadableString() } ?: ""

    companion object {
        val Saver = listSaver<MushroomWeight, Any>(
            save = { listOf(it.gramsText, it.isDried, it.kindIndex) },
            restore = { MushroomWeight(it[0] as String, it[1] as Boolean, it[2] as Int) }
        )
    }
}

// works out mg psilocybin from the weight of mushrooms, for people who weighed them rather than the psilocybin
@Composable
fun MushroomDoseHelper(
    weight: MushroomWeight,
    onWeightChange: (MushroomWeight) -> Unit,
    onSaveAsCustomUnit: (unit: MushroomCustomUnit, grams: Double?) -> Unit,
) {
    val kind = weight.kind
    val focusManager = LocalFocusManager.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        var isShowingKinds by remember { mutableStateOf(false) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Kind", style = MaterialTheme.typography.titleMedium)
            Box(modifier = Modifier.weight(1f).wrapContentSize(Alignment.TopStart)) {
                OutlinedButton(onClick = { isShowingKinds = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(text = kind.name)
                }
                DropdownMenu(expanded = isShowingKinds, onDismissRequest = { isShowingKinds = false }) {
                    mushroomKinds.forEachIndexed { index, option ->
                        DropdownMenuItem(
                            text = { Text(option.name) },
                            onClick = {
                                onWeightChange(weight.copy(kindIndex = index))
                                isShowingKinds = false
                            }
                        )
                    }
                }
            }
        }
        OutlinedTextField(
            value = weight.gramsText,
            onValueChange = { onWeightChange(weight.copy(gramsText = it.replace(oldChar = ',', newChar = '.'))) },
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
            FilterChip(
                selected = weight.isDried,
                onClick = { onWeightChange(weight.copy(isDried = true)) },
                label = { Text("Dried") })
            FilterChip(
                selected = !weight.isDried,
                onClick = { onWeightChange(weight.copy(isDried = false)) },
                label = { Text("Fresh") })
        }
        val grams = weight.grams
        if (grams != null) {
            val lowText = kind.psilocybinMg(grams, weight.isDried, kind.lowPercent).toReadableString()
            val highText = kind.psilocybinMg(grams, weight.isDried, kind.highPercent).toReadableString()
            Text(text = "About ${weight.doseText} mg Psilocybin", style = MaterialTheme.typography.titleMedium)
            Text(text = "Could be $lowText–$highText mg", style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            text = "Typical lab results. The same strain can test several times stronger or weaker depending on how it was grown, dried and stored, so this is only a rough guide.",
            style = MaterialTheme.typography.bodySmall
        )
        // a custom unit of this kind, so mushrooms can be logged in grams from then on
        Spacer(modifier = Modifier.height(8.dp))
        Text("Always prefer to log mushroom weight?")
        OutlinedButton(onClick = { onSaveAsCustomUnit(kind.customUnit(weight.isDried), grams) }) {
            Text("Create a custom unit")
        }
    }
}
