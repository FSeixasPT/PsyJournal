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

package com.isaakhanimann.journal.ui.tabs.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.QuestionAnswer
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.isaakhanimann.journal.ui.VERSION_NAME
import com.isaakhanimann.journal.ui.tabs.journal.experience.components.CardWithTitle
import com.isaakhanimann.journal.ui.theme.horizontalPadding
import com.isaakhanimann.journal.ui.utils.getStringOfPattern
import kotlinx.coroutines.launch
import java.time.Instant

@Preview
@Composable
fun SettingsPreview() {
    SettingsScreen(
        deleteEverything = {},
        navigateToFAQ = {},
        navigateToComboSettings = {},
        navigateToSubstanceColors = {},
        navigateToCustomUnits = {},
        navigateToDonate = {},
        navigateToRouteVisibility = {},
        importFile = {},
        exportFile = {},
        snackbarHostState = remember { SnackbarHostState() },
        areDosageDotsHidden = false,
        saveDosageDotsAreHidden = {},
        isTimelineHidden = false,
        saveIsTimelineHidden = {},
        areSubstanceHeightsIndependent = false,
        saveAreSubstanceHeightsIndependent = {},
        areRoutesMerged = true,
        saveAreRoutesMerged = {},
        isLiveAutoStart = false,
        saveIsLiveAutoStart = {},
        isLiveShownOnLockScreen = false,
        saveIsLiveShownOnLockScreen = {},
    )
}

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    navigateToFAQ: () -> Unit,
    navigateToComboSettings: () -> Unit,
    navigateToSubstanceColors: () -> Unit,
    navigateToCustomUnits: () -> Unit,
    navigateToDonate: () -> Unit,
    navigateToRouteVisibility: () -> Unit,
) {
    SettingsScreen(
        navigateToFAQ = navigateToFAQ,
        navigateToComboSettings = navigateToComboSettings,
        navigateToSubstanceColors = navigateToSubstanceColors,
        navigateToCustomUnits = navigateToCustomUnits,
        navigateToDonate = navigateToDonate,
        navigateToRouteVisibility = navigateToRouteVisibility,
        deleteEverything = viewModel::deleteEverything,
        importFile = viewModel::importFile,
        exportFile = viewModel::exportFile,
        snackbarHostState = viewModel.snackbarHostState,
        areDosageDotsHidden = viewModel.areDosageDotsHiddenFlow.collectAsState().value,
        saveDosageDotsAreHidden = viewModel::saveDosageDotsAreHidden,
        isTimelineHidden = viewModel.isTimelineHiddenFlow.collectAsState().value,
        saveIsTimelineHidden = viewModel::saveIsTimelineHidden,
        areSubstanceHeightsIndependent = viewModel.areSubstanceHeightsIndependentFlow.collectAsState().value,
        saveAreSubstanceHeightsIndependent = viewModel::saveAreSubstanceHeightsIndependent,
        areRoutesMerged = viewModel.areRoutesMergedFlow.collectAsState().value,
        saveAreRoutesMerged = viewModel::saveAreRoutesMerged,
        isLiveAutoStart = viewModel.isLiveAutoStartFlow.collectAsState().value,
        saveIsLiveAutoStart = viewModel::saveIsLiveAutoStart,
        isLiveShownOnLockScreen = viewModel.isLiveShownOnLockScreenFlow.collectAsState().value,
        saveIsLiveShownOnLockScreen = viewModel::saveIsLiveShownOnLockScreen,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navigateToFAQ: () -> Unit,
    navigateToComboSettings: () -> Unit,
    navigateToSubstanceColors: () -> Unit,
    navigateToCustomUnits: () -> Unit,
    navigateToDonate: () -> Unit,
    navigateToRouteVisibility: () -> Unit,
    deleteEverything: () -> Unit,
    importFile: (uri: Uri) -> Unit,
    exportFile: (uri: Uri) -> Unit,
    snackbarHostState: SnackbarHostState,
    areDosageDotsHidden: Boolean,
    saveDosageDotsAreHidden: (Boolean) -> Unit,
    isTimelineHidden: Boolean,
    saveIsTimelineHidden: (Boolean) -> Unit,
    areSubstanceHeightsIndependent: Boolean,
    saveAreSubstanceHeightsIndependent: (Boolean) -> Unit,
    areRoutesMerged: Boolean,
    saveAreRoutesMerged: (Boolean) -> Unit,
    isLiveAutoStart: Boolean,
    saveIsLiveAutoStart: (Boolean) -> Unit,
    isLiveShownOnLockScreen: Boolean,
    saveIsLiveShownOnLockScreen: (Boolean) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = horizontalPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            CardWithTitle(title = "UI", innerPaddingHorizontal = 0.dp) {
                SettingsButton(
                    imageVector = Icons.Outlined.Medication,
                    text = "Custom units"
                ) {
                    navigateToCustomUnits()
                }
                HorizontalDivider()
                SettingsButton(
                    imageVector = Icons.Outlined.Palette,
                    text = "Substance colors"
                ) {
                    navigateToSubstanceColors()
                }
                HorizontalDivider()
                SettingsButton(
                    imageVector = Icons.Outlined.Route,
                    text = "Routes of administration"
                ) {
                    navigateToRouteVisibility()
                }
                HorizontalDivider()
                SettingsButton(
                    imageVector = Icons.Outlined.WarningAmber,
                    text = "Interaction settings"
                ) {
                    navigateToComboSettings()
                }
                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = horizontalPadding),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Hide dosage dots")
                    Switch(
                        checked = areDosageDotsHidden,
                        onCheckedChange = saveDosageDotsAreHidden
                    )
                }
                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = horizontalPadding),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Hide timeline")
                    Switch(
                        checked = isTimelineHidden,
                        onCheckedChange = saveIsTimelineHidden
                    )
                }
                HorizontalDivider()
                SwitchWithInfoRow(
                    title = "Independent substance heights",
                    info = """
                                    Enable this setting if you want the timeline of different substances and routes of administration (roas) to be independent. Then ingestions of different substances and roas will always take the full height of the timeline.
                                    
                                    If this setting is disabled then timelines of different substances have a height relative to each other. In that case the average of the common dose is used as the point to compare it to.
                                    E.g. if the oral average common dose of MDMA is 100mg and the average common dose of insufflated MDMA is 50mg then the timeline for 100mg of oral MDMA is the same height as for 50mg of insufflated MDMA.
                                    This is also applied across substances. E.g. if the common dose of oral 2C-B is 20mg then the timeline of 40mg oral 2C-B will be twice as high as 100mg of oral MDMA.
                                """.trimIndent(),
                    checked = areSubstanceHeightsIndependent,
                    onCheckedChange = saveAreSubstanceHeightsIndependent
                )
                HorizontalDivider()
                SwitchWithInfoRow(
                    title = "Merge routes of same substance",
                    info = "When enabled, ingestions of the same substance through different routes (e.g. insufflated and oral ketamine) are shown as one combined line. Each route keeps its own onset and duration, and doses are made comparable using each route's common dose range, so the line shows the combined effect rather than the combined amount. Routes without dose ranges in the database are always shown separately.",
                    checked = areRoutesMerged,
                    onCheckedChange = saveAreRoutesMerged
                )
                HorizontalDivider()
                SwitchWithInfoRow(
                    title = "Start live notification automatically",
                    info = "When you log an ingestion taken in the last hour, the live notification starts for its experience. It follows the estimated timeline outside the app until the effects end. Notifications must be allowed; turn the live notification on once from an experience to allow them.",
                    checked = isLiveAutoStart,
                    onCheckedChange = saveIsLiveAutoStart
                )
                HorizontalDivider()
                SwitchWithInfoRow(
                    title = "Live notification details on lock screen",
                    info = "When disabled, the lock screen only shows that an experience is in progress, without substance names or the chart. Details are shown after unlocking.",
                    checked = isLiveShownOnLockScreen,
                    onCheckedChange = saveIsLiveShownOnLockScreen
                )
            }
            CardWithTitle(title = "App data", innerPaddingHorizontal = 0.dp) {
                var isShowingExportDialog by remember { mutableStateOf(false) }
                SettingsButton(imageVector = Icons.Outlined.FileUpload, text = "Export File") {
                    isShowingExportDialog = true
                }
                val jsonMIMEType = "application/json"
                val launcherExport =
                    rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.CreateDocument(
                            mimeType = jsonMIMEType
                        )
                    ) { uri ->
                        if (uri != null) {
                            exportFile(uri)
                        }
                    }
                AnimatedVisibility(visible = isShowingExportDialog) {
                    AlertDialog(
                        onDismissRequest = { isShowingExportDialog = false },
                        title = {
                            Text(text = "Export?")
                        },
                        text = {
                            Text("This will export all your data from the app into a file so you can send it to someone or import it again on a new phone")
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    isShowingExportDialog = false
                                    launcherExport.launch(
                                        "PsyJournal ${
                                            Instant.now().getStringOfPattern("dd MMM yyyy")
                                        }.json"
                                    )
                                }
                            ) {
                                Text("Export")
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = { isShowingExportDialog = false }
                            ) {
                                Text("Cancel")
                            }
                        }
                    )
                }
                HorizontalDivider()
                var isShowingImportDialog by remember { mutableStateOf(false) }
                SettingsButton(imageVector = Icons.Outlined.FileDownload, text = "Import file") {
                    isShowingImportDialog = true
                }
                val launcherImport =
                    rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri ->
                        if (uri != null) {
                            importFile(uri)
                        }
                    }
                AnimatedVisibility(visible = isShowingImportDialog) {
                    AlertDialog(
                        onDismissRequest = { isShowingImportDialog = false },
                        title = {
                            Text(text = "Import file?")
                        },
                        text = {
                            Text("Import a file that was exported before. Note that this will delete the data that you already have in the app.")
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    isShowingImportDialog = false
                                    launcherImport.launch(jsonMIMEType)
                                }
                            ) {
                                Text("Import")
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = { isShowingImportDialog = false }
                            ) {
                                Text("Cancel")
                            }
                        }
                    )
                }
                HorizontalDivider()
                var isShowingDeleteDialog by remember { mutableStateOf(false) }
                SettingsButton(
                    imageVector = Icons.Outlined.DeleteForever,
                    text = "Delete everything"
                ) {
                    isShowingDeleteDialog = true
                }
                val scope = rememberCoroutineScope()
                AnimatedVisibility(visible = isShowingDeleteDialog) {
                    AlertDialog(
                        onDismissRequest = { isShowingDeleteDialog = false },
                        title = {
                            Text(text = "Delete everything?")
                        },
                        text = {
                            Text("This will delete all your experiences, ingestions and custom substances.")
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    isShowingDeleteDialog = false
                                    deleteEverything()
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            message = "Deleted everything",
                                            duration = SnackbarDuration.Short
                                        )
                                    }
                                }
                            ) {
                                Text("Delete")
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = { isShowingDeleteDialog = false }
                            ) {
                                Text("Cancel")
                            }
                        }
                    )
                }
            }
            val uriHandler = LocalUriHandler.current
            CardWithTitle(title = "Feedback", innerPaddingHorizontal = 0.dp) {
                SettingsButton(imageVector = Icons.Outlined.QuestionAnswer, text = "FAQ") {
                    navigateToFAQ()
                }
                HorizontalDivider()
                val feedbackContext = LocalContext.current
                SettingsButton(imageVector = Icons.Outlined.ContentCopy, text = "Copy support info") {
                    copySupportInfo(feedbackContext)
                }
            }
            CardWithTitle(title = "App", innerPaddingHorizontal = 0.dp) {
                SettingsButton(imageVector = Icons.Outlined.Code, text = "Source Code") {
                    uriHandler.openUri(SOURCE_CODE_URL)
                }
                HorizontalDivider()
                val context = LocalContext.current
                val sendIntent: Intent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, SHARE_APP_URL)
                    type = "text/plain"
                }
                val shareIntent = Intent.createChooser(sendIntent, null)
                SettingsButton(imageVector = Icons.Outlined.Share, text = "Share") {
                    context.startActivity(shareIntent)
                }
                HorizontalDivider()
                Text(
                    text = "Version $VERSION_NAME",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .padding(horizontal = 15.dp)
                        .padding(vertical = 10.dp)
                )
            }
        }
    }
}

const val SOURCE_CODE_URL = "https://github.com/FSeixasPT/PsyJournal"
const val SHARE_APP_URL = SOURCE_CODE_URL


@Composable
fun SettingsButton(imageVector: ImageVector, text: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 2.dp)
    ) {
        Icon(
            imageVector,
            contentDescription = imageVector.name,
            modifier = Modifier.size(ButtonDefaults.IconSize)
        )
        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
        Text(text)
        Spacer(modifier = Modifier.weight(1f))
    }
}

// a setting with a switch, whose title opens an explanation
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwitchWithInfoRow(
    title: String,
    info: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var showBottomSheet by remember { mutableStateOf(false) }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier
                .clickable {
                    showBottomSheet = true
                }
                .padding(end = ButtonDefaults.IconSpacing)
        ) {
            Text(text = title)
            if (showBottomSheet) {
                ModalBottomSheet(
                    onDismissRequest = {
                        showBottomSheet = false
                    },
                    sheetState = sheetState
                ) {
                    Text(
                        text = info,
                        modifier = Modifier
                            .padding(horizontal = horizontalPadding)
                            .padding(bottom = 15.dp)
                            .verticalScroll(state = rememberScrollState())
                    )
                }
            }
            Icon(Icons.Outlined.Info, contentDescription = "Show more info")
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
