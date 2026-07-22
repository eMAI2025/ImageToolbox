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
        val activeFace: PortraitFaceCandidate,
        val autoSavedUri: Uri?,
        val autoSaveStatus: String
    ) : PortraitLabUiState
    data class Failed(val message: String) : PortraitLabUiState
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
    var selectedStage by remember { mutableStateOf(VisualProofStage.CONTOURS) }
    var visibility by remember { mutableStateOf(VisualProofStage.CONTOURS.visibility) }
    var controlPointsOnly by remember { mutableStateOf(false) }
    var pendingCopySource by remember { mutableStateOf<Uri?>(null) }
    var fileActionStatus by remember { mutableStateOf<String?>(null) }

    DisposableEffect(runner) {
        onDispose { runner.close() }
    }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        selectedUri = uri
        state = PortraitLabUiState.Empty
        selectedStage = VisualProofStage.CONTOURS
        visibility = selectedStage.visibility
        controlPointsOnly = false
        pendingCopySource = null
        fileActionStatus = null
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
        selectedStage = VisualProofStage.CONTOURS
        visibility = selectedStage.visibility
        controlPointsOnly = false
        scope.launch {
            val focused = focusPortraitOutput(sourceOutput, candidate).getOrElse { error ->
                state = PortraitLabUiState.Failed(
                    "Unable to isolate face ${candidate.faceIndex + 1}: ${error.message}"
                )
                return@launch
            }
            val autoSave = exportPortraitDiagnostics(
                context = context,
                output = focused,
                visibility = PortraitOverlayVisibility(),
                controlPointsOnly = false
            )
            state = PortraitLabUiState.Complete(
                sourceOutput = sourceOutput,
                output = focused,
                candidates = candidates,
                activeFace = candidate,
                autoSavedUri = autoSave.getOrNull(),
                autoSaveStatus = autoSave.fold(
                    onSuccess = {
                        "Saved automatically in Downloads/ImageToolbox.\n$it"
                    },
                    onFailure = {
                        "Automatic save failed: ${it.message}"
                    }
                )
            )
        }
    }

    fun runOnce() {
        val uri = selectedUri ?: return
        state = PortraitLabUiState.Running
        fileActionStatus = null
        scope.launch {
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
                    text = "The editor works on exactly one active face. One face is selected automatically. Two faces require an explicit tap. More than two faces are blocked until the image is cropped. Diagnostic ZIP files are saved automatically; no deformation is performed."
                )
            }
            item {
                FaceBackendSelector(
                    availability = runner.availability,
                    selected = selectedBackend,
                    onSelected = {
                        selectedBackend = it
                        state = PortraitLabUiState.Empty
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
                        onClick = ::runOnce,
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
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                is PortraitLabUiState.Failed -> item {
                    StatusCard("Runtime failed", current.message)
                }

                is PortraitLabUiState.TooManyFaces -> item {
                    StatusCard(
                        title = "Too many faces",
                        text = "Detected ${current.faceCount} faces. The temporary Portrait Lab limit is $PORTRAIT_MAX_FACE_CANDIDATES. Crop the image so that no more than two faces remain, then run again."
                    )
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
                }

                is PortraitLabUiState.Complete -> {
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
                    item {
                        VisualProofStageSelector(
                            selected = selectedStage,
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
                            autoSavedUri = current.autoSavedUri,
                            autoSaveStatus = current.autoSaveStatus,
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
                        Text("Face ${candidate.faceIndex + 1}${if (index == 0) " — cyan" else " — magenta"}")
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticFileCard(
    output: PortraitLabRunOutput,
    autoSavedUri: Uri?,
    autoSaveStatus: String,
    onSaveCopy: (Uri) -> Unit,
    onOpen: (Uri) -> Unit,
    onCopyReport: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Diagnostic files", style = MaterialTheme.typography.titleMedium)
            Text(autoSaveStatus, style = MaterialTheme.typography.bodySmall)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { autoSavedUri?.let(onSaveCopy) },
                    enabled = autoSavedUri != null,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Save copy...")
                }
                OutlinedButton(
                    onClick = { autoSavedUri?.let(onOpen) },
                    enabled = autoSavedUri != null,
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
private fun VisualProofStageSelector(
    selected: VisualProofStage,
    onSelected: (VisualProofStage) -> Unit
) {
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
                VisualProofStage.entries.forEach { stage ->
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
            availability.firstOrNull { it.backend == selected }?.reason?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun OverlayControls(
    visibility: PortraitOverlayVisibility,
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
                    selected = visibility.mesh,
                    onClick = { onChange(visibility.copy(mesh = !visibility.mesh)) },
                    label = { Text("Mesh") }
                )
                FilterChip(
                    selected = visibility.masks,
                    onClick = { onChange(visibility.copy(masks = !visibility.masks)) },
                    label = { Text("Masks") }
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
