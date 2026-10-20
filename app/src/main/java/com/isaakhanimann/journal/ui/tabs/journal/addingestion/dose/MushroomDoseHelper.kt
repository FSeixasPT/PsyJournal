package com.isaakhanimann.journal.ui.tabs.journal.addingestion.dose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.isaakhanimann.journal.ui.tabs.search.substance.roa.toReadableString
import com.isaakhanimann.journal.ui.theme.horizontalPadding

// Psilocybe cubensis: about 1% psilocybin dried and 0.1% fresh, the same rule the custom unit screen gives
fun psilocybinMgInMushrooms(grams: Double, isDried: Boolean): Double =
    grams * 1000 * if (isDried) 0.01 else 0.001

// works out mg psilocybin from the weight of mushrooms, for people who weighed them rather than the psilocybin
@Composable
fun MushroomDoseHelper(onUseDose: (doseText: String) -> Unit) {
    var gramsText by rememberSaveable { mutableStateOf("") }
    var isDried by rememberSaveable { mutableStateOf(true) }
    val focusManager = LocalFocusManager.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Weighed the mushrooms instead?", style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
            value = gramsText,
            onValueChange = { gramsText = it.replace(oldChar = ',', newChar = '.') },
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
            FilterChip(selected = isDried, onClick = { isDried = true }, label = { Text("Dried") })
            FilterChip(selected = !isDried, onClick = { isDried = false }, label = { Text("Fresh") })
        }
        val grams = gramsText.toDoubleOrNull()?.takeIf { it > 0 }
        if (grams != null) {
            val doseText = psilocybinMgInMushrooms(grams, isDried).toReadableString()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "About $doseText mg Psilocybin",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { onUseDose(doseText) }) {
                    Text("Use $doseText mg")
                }
            }
        }
        Text(
            text = "Based on Psilocybe cubensis. Potency varies a lot between strains and batches, so this is only a rough guide.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}
