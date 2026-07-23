/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.feature.portrait_lab

import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private sealed interface SilhouetteLabUiState {
    data object Empty : SilhouetteLabUiState
    data object Running : SilhouetteLabUiState
    data class Complete(val output: SilhouetteLabRunOutput) : SilhouetteLabUiState
    data class Failed(val message: String) : SilhouetteLabUiState
}

private sealed interface SilhouetteExportState {
    data object Idle : SilhouetteExportState
    data object Saving : SilhouetteExportState
    data class Saved(val uri: Uri) : SilhouetteExportState
    data class Failed(val message: String) : SilhouetteExportState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SilhouetteLabContent(
    onGoBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val runner = remember(context) { createSilhouetteLabRunner(context) }
    val scope = rememberCoroutineScope()

    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var state by remember { mutableStateOf<SilhouetteLabUiState>(SilhouetteLabUiState.Empty) }
    var visibility by remember { mutableStateOf(SilhouetteOverlayVisibility()) }
    var exportState by remember { mutableStateOf<SilhouetteExportState>(SilhouetteExportState.Idle) }
    var exportJob by remember { mutableStateOf<Job?>(null) }
    var pendingCopySource by remember { mutableStateOf<Uri?>(null) }
    var fileActionStatus by remember { mutableStateOf<String?>(null) }

    fun cancelExport() {
        exportJob?.cancel()
        exportJob = null
        exportState = SilhouetteExportState.Idle
    }

    fun startExport(output: SilhouetteLabRunOutput) {
        exportJob?.cancel()
        exportState = SilhouetteExportState.Saving
        exportJob = scope.launch {
            exportState = exportSilhouetteDiagnostics(context, output, visibility).fold(
                onSuccess = { SilhouetteExportState.Saved(it) },
                onFailure = {
                    SilhouetteExportState.Failed(it.message ?: "Unknown silhouette export failure")
                }
            )
            exportJob = null
        }
    }

    fun runAnalysis() {
        val uri = selectedUri ?: return
        cancelExport()
        state = SilhouetteLabUiState.Running
        fileActionStatus = null
        scope.launch {
            state = when (val result = runner.run(uri)) {
                is SilhouetteLabRunResult.Success -> {
                    startExport(result.output)
                    SilhouetteLabUiState.Complete(result.output)
                }
                is SilhouetteLabRunResult.Failure -> SilhouetteLabUiState.Failed(
                    listOfNotNull(result.message, result.exceptionType).joinToString("\n")
                )
            }
        }
    }

    DisposableEffect(runner) {
        onDispose {
            exportJob?.cancel()
            runner.close()
        }
    }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            cancelExport()
            selectedUri = uri
            state = SilhouetteLabUiState.Empty
            visibility = SilhouetteOverlayVisibility()
            fileActionStatus = null
        }
    }

    val saveCopyLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { destination ->
        val source = pendingCopySource
        pendingCopySource = null
        if (destination != null && source != null) {
            scope.launch {
                fileActionStatus = copyPortraitDiagnosticArchive(
                    context = context,
                    source = source,
                    destination = destination
                ).fold(
                    onSuccess = { "Silhouette ZIP copied to: $it" },
                    onFailure = { "Copy failed: ${it.message}" }
                )
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Silhouette Lab")
                        Text(
                            "B1 — pose + subject mask + visible silhouette",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                },
                navigationIcon = {
                    onGoBack?.let { callback ->
                        TextButton(onClick = callback) { Text("Back") }
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SilhouetteStatusCard(
                    "B1 observation policy",
                    "One person is analyzed with ML Kit Pose and Selfie Segmentation. " +
                        "Only mask-supported visible sections are derived. Cropped limbs are not invented. " +
                        "Clothing remains part of the visible silhouette. No deformation is enabled."
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { picker.launch("image/*") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (selectedUri == null) "Select image" else "New image")
                    }
                    Button(
                        onClick = { runAnalysis() },
                        enabled = selectedUri != null && state !is SilhouetteLabUiState.Running,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Run 1×")
                    }
                }
            }

            when (val current = state) {
                SilhouetteLabUiState.Empty -> item {
                    SilhouetteStatusCard(
                        "Ready",
                        if (selectedUri == null) {
                            "Select a photo containing one person."
                        } else {
                            "Run pose and silhouette observation."
                        }
                    )
                }

                SilhouetteLabUiState.Running -> item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                is SilhouetteLabUiState.Failed -> {
                    item { SilhouetteStatusCard("Runtime failed", current.message) }
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(onClick = { runAnalysis() }, modifier = Modifier.weight(1f)) {
                                Text("Try again")
                            }
                            OutlinedButton(
                                onClick = { picker.launch("image/*") },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Change image")
                            }
                        }
                    }
                }

                is SilhouetteLabUiState.Complete -> {
                    val output = current.output
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(onClick = { runAnalysis() }, modifier = Modifier.weight(1f)) {
                                Text("Run again")
                            }
                            OutlinedButton(
                                onClick = { picker.launch("image/*") },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("New image")
                            }
                        }
                    }
                    item {
                        SilhouetteOverlayControls(
                            visibility = visibility,
                            onChange = { visibility = it }
                        )
                    }
                    item {
                        SilhouettePreview(output = output, visibility = visibility)
                    }
                    item {
                        SilhouetteSummaryCard(output)
                    }
                    item {
                        SilhouetteExportCard(
                            output = output,
                            exportState = exportState,
                            onRetry = { startExport(output) },
                            onSaveCopy = { source ->
                                pendingCopySource = source
                                val timestamp = LocalDateTime.now().format(
                                    DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
                                )
                                saveCopyLauncher.launch("silhouette_visual_proof_$timestamp.zip")
                            },
                            onOpen = { source ->
                                fileActionStatus = openPortraitDiagnosticArchive(context, source).fold(
                                    onSuccess = { "Opening silhouette ZIP." },
                                    onFailure = { "Unable to open ZIP: ${it.message}" }
                                )
                            },
                            onCopyReport = {
                                val clipboard = context.getSystemService(
                                    ClipboardManager::class.java
                                )
                                clipboard.setPrimaryClip(
                                    ClipData.newPlainText(
                                        "Silhouette Lab diagnostic report",
                                        buildSilhouetteTextReport(output)
                                    )
                                )
                                fileActionStatus = "Silhouette report copied."
                            }
                        )
                    }
                    fileActionStatus?.let { status ->
                        item { SilhouetteStatusCard("File action", status) }
                    }
                    item {
                        SilhouetteReportCard(buildSilhouetteTextReport(output))
                    }
                }
            }
        }
    }
}

@Composable
private fun SilhouetteOverlayControls(
    visibility: SilhouetteOverlayVisibility,
    onChange: (SilhouetteOverlayVisibility) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = visibility.mask,
                onClick = { onChange(visibility.copy(mask = !visibility.mask)) },
                label = { Text("Subject mask") }
            )
            FilterChip(
                selected = visibility.pose,
                onClick = { onChange(visibility.copy(pose = !visibility.pose)) },
                label = { Text("Pose") }
            )
            FilterChip(
                selected = visibility.sections,
                onClick = { onChange(visibility.copy(sections = !visibility.sections)) },
                label = { Text("Silhouette sections") }
            )
        }
    }
}

@Composable
private fun SilhouettePreview(
    output: SilhouetteLabRunOutput,
    visibility: SilhouetteOverlayVisibility
) {
    val rendered = remember(output, visibility) {
        renderSilhouetteDiagnosticBitmap(output, visibility)
    }
    DisposableEffect(rendered) {
        onDispose { if (!rendered.isRecycled) rendered.recycle() }
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Image(
            bitmap = rendered.asImageBitmap(),
            contentDescription = "Silhouette visual proof",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(rendered.width.toFloat() / rendered.height.toFloat())
        )
    }
}

@Composable
private fun SilhouetteSummaryCard(output: SilhouetteLabRunOutput) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("Silhouette proof", style = MaterialTheme.typography.titleMedium)
            Text("Status: ${output.status.name}")
            Text("Subjects: ${output.observation.subjectCount}")
            Text("Bodies: ${output.observation.bodyCount}")
            Text("Pose landmarks: ${output.observation.landmarks.values.count { it.backend.name == \"ML_KIT_POSE\" }}")
            Text("Masks: ${output.observation.masks.size}")
            Text("Derived regions: ${output.enrichment.derivedRegionIds.size}")
            output.regionCapabilities.forEach { region ->
                Text(
                    "${region.regionId}: " +
                        if (region.available) "AVAILABLE" else "BLOCKED (${region.reason})"
                )
            }
        }
    }
}

@Composable
private fun SilhouetteExportCard(
    output: SilhouetteLabRunOutput,
    exportState: SilhouetteExportState,
    onRetry: () -> Unit,
    onSaveCopy: (Uri) -> Unit,
    onOpen: (Uri) -> Unit,
    onCopyReport: () -> Unit
) {
    val savedUri = (exportState as? SilhouetteExportState.Saved)?.uri
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Diagnostic files", style = MaterialTheme.typography.titleMedium)
            Text(
                when (exportState) {
                    SilhouetteExportState.Idle -> "ZIP not saved yet."
                    SilhouetteExportState.Saving -> "Saving ZIP in the background."
                    is SilhouetteExportState.Saved -> "Saved in Downloads/ImageToolbox.\n${exportState.uri}"
                    is SilhouetteExportState.Failed -> "Export failed: ${exportState.message}"
                },
                style = MaterialTheme.typography.bodySmall
            )
            if (exportState is SilhouetteExportState.Idle ||
                exportState is SilhouetteExportState.Failed
            ) {
                Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                    Text("Save diagnostic ZIP")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { savedUri?.let(onSaveCopy) },
                    enabled = savedUri != null,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Save copy...")
                }
                OutlinedButton(
                    onClick = { savedUri?.let(onOpen) },
                    enabled = savedUri != null,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Open ZIP")
                }
            }
            OutlinedButton(onClick = onCopyReport, modifier = Modifier.fillMaxWidth()) {
                Text("Copy text report")
            }
            Text(
                "Current status: ${output.status.name}",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun SilhouetteStatusCard(title: String, text: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(text)
        }
    }
}

@Composable
private fun SilhouetteReportCard(report: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Copyable runtime report", style = MaterialTheme.typography.titleMedium)
            SelectionContainer {
                Text(
                    report,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
