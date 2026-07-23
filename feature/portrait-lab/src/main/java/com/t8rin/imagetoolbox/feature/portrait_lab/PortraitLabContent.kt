/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.feature.portrait_lab

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ObservationBackend
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private sealed interface PortraitLabUiState {
    data object Empty : PortraitLabUiState
    data object Running : PortraitLabUiState

    data class SelectFace(
        val sourceOutput: PortraitLabRunOutput,
        val candidates: List<PortraitFaceCandidate>
    ) : PortraitLabUiState

    data class TooManyFaces(
        val faceCount: Int
    ) : PortraitLabUiState

    data class Complete(
        val sourceOutput: PortraitLabRunOutput,
        val output: PortraitLabRunOutput,
        val candidates: List<PortraitFaceCandidate>,
        val activeFace: PortraitFaceCandidate
    ) : PortraitLabUiState

    data class Failed(val message: String) : PortraitLabUiState
}

private sealed interface PortraitExportState {
    data object Idle : PortraitExportState
    data object Saving : PortraitExportState
    data class Saved(val uri: Uri) : PortraitExportState
    data class Failed(val message: String) : PortraitExportState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortraitLabContent(
    onGoBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val runner = remember(context) { createPortraitLabRunner(context) }
    val scope = rememberCoroutineScope()

    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var selectedBackend by remember {
        mutableStateOf(ObservationBackend.ML_KIT_FACE_DETECTION)
    }
    var state by remember { mutableStateOf<PortraitLabUiState>(PortraitLabUiState.Empty) }
    var exportState by remember { mutableStateOf<PortraitExportState>(PortraitExportState.Idle) }
    var exportJob by remember { mutableStateOf<Job?>(null) }
    var exportGeneration by remember { mutableStateOf(0) }
    var selectedStage by remember { mutableStateOf(VisualProofStage.CONTOURS) }
    var visibility by remember { mutableStateOf(VisualProofStage.CONTOURS.visibility) }
    var controlPointsOnly by remember { mutableStateOf(false) }
    var pendingCopySource by remember { mutableStateOf<Uri?>(null) }
    var fileActionStatus by remember { mutableStateOf<String?>(null) }

    fun resetVisualization() {
        selectedStage = VisualProofStage.CONTOURS
        visibility = VisualProofStage.CONTOURS.visibility
        controlPointsOnly = false
    }

    fun cancelDiagnosticExport() {
        exportGeneration += 1
        exportJob?.cancel()
        exportJob = null
        exportState = PortraitExportState.Idle
    }

    fun resetSession(clearImage: Boolean) {
        cancelDiagnosticExport()
        if (clearImage) selectedUri = null
        state = PortraitLabUiState.Empty
        resetVisualization()
        pendingCopySource = null
        fileActionStatus = null
    }

    fun startDiagnosticExport(output: PortraitLabRunOutput) {
        exportJob?.cancel()
        val generation = exportGeneration + 1
        exportGeneration = generation
        exportState = PortraitExportState.Saving
        exportJob = scope.launch {
            val result = exportPortraitDiagnostics(
                context = context,
                output = output,
                visibility = PortraitOverlayVisibility(),
                controlPointsOnly = false
            )
            if (generation != exportGeneration) return@launch
            exportState = result.fold(
                onSuccess = { PortraitExportState.Saved(it) },
                onFailure = {
                    PortraitExportState.Failed(
                        it.message ?: "Unknown diagnostic export failure"
                    )
                }
            )
            exportJob = null
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
            resetSession(clearImage = false)
            selectedUri = uri
        }
    }

    val saveCopyLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { destination ->
        val source = pendingCopySource
        pendingCopySource = null
        if (destination != null && source != null) {
            scope.launch {
                fileActionStatus = "Copying diagnostic archive..."
                fileActionStatus = copyPortraitDiagnosticArchive(
                    context = context,
                    source = source,
                    destination = destination
                ).fold(
                    onSuccess = { "Diagnostic archive saved to the selected location.\n$it" },
                    onFailure = { "Copy failed: ${it.message}" }
                )
            }
        }
    }

    fun activateFace(
        sourceOutput: PortraitLabRunOutput,
        candidates: List<PortraitFaceCandidate>,
        candidate: PortraitFaceCandidate
    ) {
        state = PortraitLabUiState.Running
        fileActionStatus = null
        resetVisualization()
        scope.launch {
            try {
                val focused = focusPortraitOutput(sourceOutput, candidate).getOrElse { error ->
                    state = PortraitLabUiState.Failed(
                        "Unable to isolate face ${candidate.faceIndex + 1}: ${error.message}"
                    )
                    return@launch
                }

                state = PortraitLabUiState.Complete(
                    sourceOutput = sourceOutput,
                    output = focused,
                    candidates = candidates,
                    activeFace = candidate
                )

                // Export is intentionally detached from the analysis state. A slow ZIP write must
                // never keep the whole screen in Running or block another test.
                startDiagnosticExport(focused)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                state = PortraitLabUiState.Failed(
                    "Unable to prepare the active face: ${error.message ?: error::class.simpleName}"
                )
            }
        }
    }

    fun runOnce() {
        val uri = selectedUri ?: return
        cancelDiagnosticExport()
        state = PortraitLabUiState.Running
        fileActionStatus = null
        resetVisualization()
        scope.launch {
            try {
                when (
                    val result = runner.run(
                        uri = uri,
                        backend = selectedBackend
                    )
                ) {
                    is PortraitLabRunResult.Failure -> {
                        state = PortraitLabUiState.Failed(
                            listOfNotNull(result.message, result.exceptionType).joinToString("\n")
                        )
                    }

                    is PortraitLabRunResult.Success -> {
                        val candidates = extractPortraitFaceCandidates(result.output)
                        when {
                            candidates.isEmpty() -> {
                                state = PortraitLabUiState.Failed(
                                    "No selectable face bounding box was returned."
                                )
                            }

                            candidates.size > PORTRAIT_MAX_FACE_CANDIDATES -> {
                                state = PortraitLabUiState.TooManyFaces(candidates.size)
                            }

                            candidates.size == 1 -> {
                                activateFace(result.output, candidates, candidates.single())
                            }

                            else -> {
                                state = PortraitLabUiState.SelectFace(result.output, candidates)
                            }
                        }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                state = PortraitLabUiState.Failed(
                    "Unexpected detector failure: ${error.message ?: error::class.simpleName}"
                )
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Portrait Lab")
                        Text(
                            text = "P1-VISUAL-PROOF — one active face",
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
                StatusCard(
                    title = "Temporary diagnostic policy",
                    text = "The editor works on exactly one active face. One face is selected automatically. Two faces require an explicit tap. More than two faces are blocked until the image is cropped. Diagnostic ZIP export runs in the background and no longer blocks another test. No deformation is performed."
                )
            }
            item {
                FaceBackendSelector(
                    availability = runner.availability,
                    selected = selectedBackend,
                    onSelected = {
                        selectedBackend = it
                        resetSession(clearImage = false)
                    }
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
                        Text(if (selectedUri == null) "Select image" else "Change image")
                    }
                    Button(
                        onClick = { runOnce() },
                        enabled = selectedUri != null && state !is PortraitLabUiState.Running,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Run 1×")
                    }
                }
            }

            when (val current = state) {
                PortraitLabUiState.Empty -> item {
                    StatusCard(
                        title = "Ready",
                        text = if (selectedUri == null) {
                            "Select one image."
                        } else {
                            "Run the detector once. If two faces are found, tap the face that will become the only active edit target."
                        }
                    )
                }

                PortraitLabUiState.Running -> item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator()
                            Text("Running face analysis...")
                            Text(
                                "Only detector analysis blocks this screen. ZIP export is handled separately.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                is PortraitLabUiState.Failed -> {
                    item { StatusCard("Runtime failed", current.message) }
                    item {
                        RecoveryActionsCard(
                            onRunAgain = { runOnce() },
                            onChangeImage = { picker.launch("image/*") },
                            onNewTest = { resetSession(clearImage = true) }
                        )
                    }
                }

                is PortraitLabUiState.TooManyFaces -> {
                    item {
                        StatusCard(
                            title = "Too many faces",
                            text = "Detected ${current.faceCount} faces. The temporary Portrait Lab limit is $PORTRAIT_MAX_FACE_CANDIDATES. Crop or change the image so that no more than two faces remain, then run again."
                        )
                    }
                    item {
                        RecoveryActionsCard(
                            onRunAgain = { runOnce() },
                            onChangeImage = { picker.launch("image/*") },
                            onNewTest = { resetSession(clearImage = true) }
                        )
                    }
                }

                is PortraitLabUiState.SelectFace -> {
                    item {
                        FaceCandidateSelectionCard(
                            output = current.sourceOutput,
                            candidates = current.candidates,
                            onSelected = { candidate ->
                                activateFace(
                                    sourceOutput = current.sourceOutput,
                                    candidates = current.candidates,
                                    candidate = candidate
                                )
                            }
                        )
                    }
                    item {
                        RecoveryActionsCard(
                            onRunAgain = { runOnce() },
                            onChangeImage = { picker.launch("image/*") },
                            onNewTest = { resetSession(clearImage = true) }
                        )
                    }
                }

                is PortraitLabUiState.Complete -> {
                    val meshAvailable = current.output.visualProof.triangleCount > 0
                    val masksAvailable = current.output.observation.masks.isNotEmpty()

                    item {
                        ResultActionsCard(
                            onRunAgain = { runOnce() },
                            onChangeImage = { picker.launch("image/*") },
                            onNewTest = { resetSession(clearImage = true) }
                        )
                    }
                    item {
                        StatusCard(
                            title = "Active face",
                            text = "Face ${current.activeFace.faceIndex + 1} is isolated in a working crop. Other faces are ignored by the current diagnostic and by future face-edit parameters."
                        )
                    }
                    if (current.candidates.size == 2) {
                        item {
                            OutlinedButton(
                                onClick = {
                                    cancelDiagnosticExport()
                                    state = PortraitLabUiState.SelectFace(
                                        current.sourceOutput,
                                        current.candidates
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Change active face")
                            }
                        }
                    }
                    if (!meshAvailable || !masksAvailable) {
                        item {
                            CapabilityStatusCard(
                                backend = current.output.selectedBackend,
                                meshAvailable = meshAvailable,
                                masksAvailable = masksAvailable
                            )
                        }
                    }
                    item {
                        VisualProofStageSelector(
                            selected = selectedStage,
                            meshAvailable = meshAvailable,
                            onSelected = { stage ->
                                selectedStage = stage
                                visibility = stage.visibility
                                controlPointsOnly = stage.controlPointsOnly
                            }
                        )
                    }
                    item {
                        OverlayControls(
                            visibility = visibility,
                            meshAvailable = meshAvailable,
                            masksAvailable = masksAvailable,
                            onChange = {
                                visibility = it
                                selectedStage = VisualProofStage.fromVisibility(it) ?: selectedStage
                                controlPointsOnly = false
                            }
                        )
                    }
                    item {
                        FrozenDiagnosticPreview(
                            output = current.output,
                            visibility = visibility,
                            controlPointsOnly = controlPointsOnly
                        )
                    }
                    item { VisualProofSummaryCard(current.output) }
                    item {
                        DiagnosticFileCard(
                            output = current.output,
                            exportState = exportState,
                            onRetry = { startDiagnosticExport(current.output) },
                            onSaveCopy = { uri ->
                                pendingCopySource = uri
                                saveCopyLauncher.launch(portraitDiagnosticSuggestedFileName())
                            },
                            onOpen = { uri ->
                                fileActionStatus = openPortraitDiagnosticArchive(context, uri).fold(
                                    onSuccess = { "Opening diagnostic archive." },
                                    onFailure = { "No application can open the ZIP: ${it.message}" }
                                )
                            },
                            onCopyReport = {
                                copyPortraitTextReport(context, current.output)
                                fileActionStatus = "Text report copied to the clipboard."
                            }
                        )
                    }
                    fileActionStatus?.let { status ->
                        item { StatusCard("File action", status) }
                    }
                    if (current.output.warnings.isNotEmpty()) {
                        item {
                            ReportCard(
                                "Warnings",
                                current.output.warnings.joinToString("\n")
                            )
                        }
                    }
                    item {
                        ReportCard(
                            "Copyable runtime report",
                            buildPortraitTextReport(current.output)
                        )
                    }
                }
            }

            item { HorizontalDivider() }
            item {
                Text(
                    text = "Automatic PASS remains disabled. The current block validates detection, active-face isolation, coordinate mapping and diagnostic export only.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
            }
        }
    }
}

@Composable
private fun ResultActionsCard(
    onRunAgain: () -> Unit,
    onChangeImage: () -> Unit,
    onNewTest: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Continue testing", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = onRunAgain, modifier = Modifier.weight(1f)) {
                    Text("Run again")
                }
                OutlinedButton(onClick = onChangeImage, modifier = Modifier.weight(1f)) {
                    Text("New image")
                }
            }
            TextButton(onClick = onNewTest, modifier = Modifier.fillMaxWidth()) {
                Text("Clear and start a new test")
            }
            Text(
                "These controls remain active while the diagnostic ZIP is being written.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun RecoveryActionsCard(
    onRunAgain: () -> Unit,
    onChangeImage: () -> Unit,
    onNewTest: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = onRunAgain, modifier = Modifier.weight(1f)) {
                    Text("Try again")
                }
                OutlinedButton(onClick = onChangeImage, modifier = Modifier.weight(1f)) {
                    Text("Change image")
                }
            }
            TextButton(onClick = onNewTest, modifier = Modifier.fillMaxWidth()) {
                Text("Clear test")
            }
        }
    }
}

@Composable
private fun FaceCandidateSelectionCard(
    output: PortraitLabRunOutput,
    candidates: List<PortraitFaceCandidate>,
    onSelected: (PortraitFaceCandidate) -> Unit
) {
    var previewSize by remember { mutableStateOf(IntSize.Zero) }
    val bitmap = output.previewBitmap

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Select the active face", style = MaterialTheme.typography.titleMedium)
            Text(
                "Tap one highlighted bounding box. The view will zoom to that face and the remaining face will be excluded from the working observation.",
                style = MaterialTheme.typography.bodySmall
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat())
                    .onSizeChanged { previewSize = it }
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Select active face",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.matchParentSize()
                )
                Canvas(
                    modifier = Modifier
                        .matchParentSize()
                        .pointerInput(candidates, previewSize) {
                            detectTapGestures { tap ->
                                if (previewSize.width <= 0 || previewSize.height <= 0) {
                                    return@detectTapGestures
                                }
                                val x = tap.x / previewSize.width.toFloat()
                                val y = tap.y / previewSize.height.toFloat()
                                candidates
                                    .filter { it.bounds.contains(x, y) }
                                    .minByOrNull(PortraitFaceCandidate::area)
                                    ?.let(onSelected)
                            }
                        }
                ) {
                    candidates.forEachIndexed { index, candidate ->
                        val bounds = candidate.bounds
                        val color = if (index == 0) Color.Cyan else Color.Magenta
                        drawRect(
                            color = color,
                            topLeft = Offset(bounds.left * size.width, bounds.top * size.height),
                            size = androidx.compose.ui.geometry.Size(
                                bounds.width * size.width,
                                bounds.height * size.height
                            ),
                            style = Stroke(width = 5.dp.toPx())
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                candidates.forEachIndexed { index, candidate ->
                    OutlinedButton(
                        onClick = { onSelected(candidate) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            "Face ${candidate.faceIndex + 1}" +
                                if (index == 0) " — cyan" else " — magenta"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticFileCard(
    output: PortraitLabRunOutput,
    exportState: PortraitExportState,
    onRetry: () -> Unit,
    onSaveCopy: (Uri) -> Unit,
    onOpen: (Uri) -> Unit,
    onCopyReport: () -> Unit
) {
    val savedUri = (exportState as? PortraitExportState.Saved)?.uri
    val statusText = when (exportState) {
        PortraitExportState.Idle -> "Diagnostic ZIP has not been written yet."
        PortraitExportState.Saving ->
            "Saving diagnostic ZIP in the background. You may run another test now."
        is PortraitExportState.Saved ->
            "Saved automatically in Downloads/ImageToolbox.\n${exportState.uri}"
        is PortraitExportState.Failed ->
            "Automatic save failed: ${exportState.message}"
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Diagnostic files", style = MaterialTheme.typography.titleMedium)
            Text(statusText, style = MaterialTheme.typography.bodySmall)
            if (exportState is PortraitExportState.Failed || exportState is PortraitExportState.Idle) {
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
            OutlinedButton(
                onClick = onCopyReport,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Copy text report")
            }
            Text(
                "The ZIP contains PNG stages, detection_result.json, coordinate_transform.json, visual_status.json and runtime_log.txt.",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "Current backend: ${output.selectedBackend.name}",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun CapabilityStatusCard(
    backend: ObservationBackend,
    meshAvailable: Boolean,
    masksAvailable: Boolean
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("Backend capability", style = MaterialTheme.typography.titleMedium)
            Text("Backend: ${backend.name}")
            if (!meshAvailable) {
                Text(
                    "Dense face mesh is not available in this result. ML Kit Face Detection returns bounding boxes, landmarks and contours, but no mesh triangles. The Mesh stage is therefore disabled rather than shown as an empty result."
                )
            }
            if (!masksAvailable) {
                Text(
                    "No semantic masks were returned by this backend. The Masks layer is disabled.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun VisualProofStageSelector(
    selected: VisualProofStage,
    meshAvailable: Boolean,
    onSelected: (VisualProofStage) -> Unit
) {
    val stages = VisualProofStage.entries.filter {
        it != VisualProofStage.FULL_MESH || meshAvailable
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Validation sequence", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                stages.forEach { stage ->
                    FilterChip(
                        selected = selected == stage,
                        onClick = { onSelected(stage) },
                        label = { Text(stage.label) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FaceBackendSelector(
    availability: List<PortraitBackendAvailability>,
    selected: ObservationBackend,
    onSelected: (ObservationBackend) -> Unit
) {
    val options = listOf(
        ObservationBackend.ML_KIT_FACE_DETECTION,
        ObservationBackend.MEDIAPIPE_FACE_LANDMARKER
    )
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Face backend", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                options.forEach { backend ->
                    val item = availability.firstOrNull { it.backend == backend }
                    FilterChip(
                        selected = selected == backend,
                        onClick = { onSelected(backend) },
                        enabled = item?.available == true,
                        label = { Text(backend.name) }
                    )
                }
            }
            availability
                .filter { it.backend in options && !it.available }
                .forEach { item ->
                    Text(
                        "${item.backend.name}: ${item.reason ?: "not available"}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
        }
    }
}

@Composable
private fun OverlayControls(
    visibility: PortraitOverlayVisibility,
    meshAvailable: Boolean,
    masksAvailable: Boolean,
    onChange: (PortraitOverlayVisibility) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Overlay layers", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = visibility.boundingBox,
                    onClick = { onChange(visibility.copy(boundingBox = !visibility.boundingBox)) },
                    label = { Text("Bounding box") }
                )
                FilterChip(
                    selected = visibility.points,
                    onClick = { onChange(visibility.copy(points = !visibility.points)) },
                    label = { Text("Points") }
                )
                FilterChip(
                    selected = visibility.contours,
                    onClick = { onChange(visibility.copy(contours = !visibility.contours)) },
                    label = { Text("Contours") }
                )
                FilterChip(
                    selected = visibility.mesh && meshAvailable,
                    onClick = { onChange(visibility.copy(mesh = !visibility.mesh)) },
                    enabled = meshAvailable,
                    label = { Text(if (meshAvailable) "Mesh" else "Mesh unavailable") }
                )
                FilterChip(
                    selected = visibility.masks && masksAvailable,
                    onClick = { onChange(visibility.copy(masks = !visibility.masks)) },
                    enabled = masksAvailable,
                    label = { Text(if (masksAvailable) "Masks" else "Masks unavailable") }
                )
            }
        }
    }
}

@Composable
private fun FrozenDiagnosticPreview(
    output: PortraitLabRunOutput,
    visibility: PortraitOverlayVisibility,
    controlPointsOnly: Boolean
) {
    val rendered = remember(output, visibility, controlPointsOnly) {
        renderDiagnosticBitmap(output, visibility, controlPointsOnly)
    }
    DisposableEffect(rendered) {
        onDispose {
            if (!rendered.isRecycled) rendered.recycle()
        }
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Image(
            bitmap = rendered.asImageBitmap(),
            contentDescription = "Frozen active-face detector result",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(rendered.width.toFloat() / rendered.height.toFloat())
        )
    }
}

@Composable
private fun VisualProofSummaryCard(output: PortraitLabRunOutput) {
    val proof = output.visualProof
    val metadata = output.sourceMetadata
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("Visual proof", style = MaterialTheme.typography.titleMedium)
            Text("Backend: ${output.selectedBackend.name}")
            Text("Status: ${proof.status.name}")
            Text("Working crop: ${metadata.orientedWidth} × ${metadata.orientedHeight}")
            Text("Preview bitmap: ${metadata.previewWidth} × ${metadata.previewHeight}")
            Text("Active faces: ${proof.faceCount}")
            Text("Points: ${proof.landmarkCount}")
            Text("Contours: ${proof.contourCount}")
            Text("Triangles: ${proof.triangleCount}")
            Text("Rendered spread: ${proof.renderedLandmarkSpreadPixels} px²")
            Text("Contour groups: ${proof.semanticContourGroups.joinToString()}")
            proof.reasons.forEach { Text("Reason: $it") }
        }
    }
}

@Composable
private fun StatusCard(title: String, text: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(text)
        }
    }
}

@Composable
private fun ReportCard(title: String, report: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
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
