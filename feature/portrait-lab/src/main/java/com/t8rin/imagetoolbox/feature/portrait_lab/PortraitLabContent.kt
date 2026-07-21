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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ObservationBackend
import com.t8rin.imagetoolbox.lib.portrait_analysis.visual.VisualProofStatus
import kotlinx.coroutines.launch

private sealed interface PortraitLabUiState {
    data object Empty : PortraitLabUiState
    data object Running : PortraitLabUiState
    data class Complete(val output: PortraitLabRunOutput) : PortraitLabUiState
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
    var selectedStage by remember { mutableStateOf(VisualProofStage.SOURCE) }
    var visibility by remember { mutableStateOf(VisualProofStage.SOURCE.visibility) }
    var controlPointsOnly by remember { mutableStateOf(false) }
    var exportStatus by remember { mutableStateOf<String?>(null) }

    DisposableEffect(runner) {
        onDispose { runner.close() }
    }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        selectedUri = uri
        state = PortraitLabUiState.Empty
        selectedStage = VisualProofStage.SOURCE
        visibility = selectedStage.visibility
        controlPointsOnly = false
        exportStatus = null
    }

    fun runOnce() {
        val uri = selectedUri ?: return
        state = PortraitLabUiState.Running
        exportStatus = null
        scope.launch {
            state = when (
                val result = runner.run(
                    uri = uri,
                    backend = selectedBackend
                )
            ) {
                is PortraitLabRunResult.Success -> PortraitLabUiState.Complete(result.output)
                is PortraitLabRunResult.Failure -> PortraitLabUiState.Failed(
                    listOfNotNull(result.message, result.exceptionType).joinToString("\n")
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
                            text = "P1-VISUAL-PROOF — one frozen result",
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
            item { BackendAvailabilityCard(runner.availability) }
            item { ModelSelectionPolicyCard() }
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
                            "Select one image. The detector will run exactly once."
                        } else {
                            "Run once, then validate stages 1 through 5 in order."
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

                is PortraitLabUiState.Complete -> {
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
                                selectedStage = VisualProofStage.fromVisibility(it)
                                    ?: selectedStage
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
                        Button(
                            onClick = {
                                scope.launch {
                                    exportStatus = "Saving visual proof package..."
                                    exportStatus = exportPortraitDiagnostics(
                                        context = context,
                                        output = current.output,
                                        visibility = visibility,
                                        controlPointsOnly = controlPointsOnly
                                    ).fold(
                                        onSuccess = { "Saved: $it" },
                                        onFailure = { "Export failed: ${it.message}" }
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Export PNG + detection_result.json + transform JSON")
                        }
                    }
                    exportStatus?.let { status ->
                        item { StatusCard("Diagnostic package", status) }
                    }
                    if (current.output.warnings.isNotEmpty()) {
                        item {
                            ReportCard(
                                "Warnings",
                                current.output.warnings.joinToString("\n")
                            )
                        }
                    }
                    if (
                        current.output.visualProof.status == VisualProofStatus.FAIL ||
                        current.output.visualProof.status ==
                        VisualProofStatus.VISUAL_VALIDATION_FAILED
                    ) {
                        item {
                            ReportCard(
                                "First 20 raw coordinates",
                                current.output.visualProof.firstTwentyCoordinates.joinToString("\n") {
                                    "${it.id}: x=${it.x}, y=${it.y}, backend=${it.backend}"
                                }
                            )
                        }
                    }
                    item {
                        ReportCard(
                            "Runtime log",
                            current.output.runtimeLog.joinToString("\n")
                        )
                    }
                }
            }

            item { HorizontalDivider() }
            item {
                Text(
                    text = "Automatic PASS is disabled. READY_FOR_VISUAL_REVIEW means only that the frozen image is ready for human inspection. No face deformation is performed.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
            }
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
            contentDescription = "Frozen portrait detector result",
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
            Text("Encoded image: ${metadata.encodedWidth} × ${metadata.encodedHeight}")
            Text("Oriented image: ${metadata.orientedWidth} × ${metadata.orientedHeight}")
            Text("Preview bitmap: ${metadata.previewWidth} × ${metadata.previewHeight}")
            Text("EXIF orientation: ${metadata.exifOrientation}")
            Text("EXIF rotation: ${metadata.orientationDegrees}°")
            Text("Mirrored: ${metadata.mirrored}")
            Text("Faces: ${proof.faceCount}")
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
private fun BackendAvailabilityCard(items: List<PortraitBackendAvailability>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("Runtime backends", style = MaterialTheme.typography.titleMedium)
            items.forEach { item ->
                Text(
                    buildString {
                        append(if (item.available) "READY  " else "BLOCKED  ")
                        append(item.backend.name)
                        item.reason?.let { append(" — ").append(it) }
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun ModelSelectionPolicyCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("Detector model", style = MaterialTheme.typography.titleMedium)
            Text(
                "Do not select a model from the general AI Tools model list. Portrait Lab uses isolated detector backends."
            )
            Text(
                "Detailed face analysis requires models/face_landmarker.task. Upscale, denoise, restoration and enhancement models are incompatible.",
                style = MaterialTheme.typography.bodySmall
            )
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
            Text(
                report,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
