/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.feature.portrait_lab

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.t8rin.imagetoolbox.lib.portrait_analysis.overlay.PortraitOverlayScene
import com.t8rin.imagetoolbox.lib.portrait_analysis.report.ParameterGateReportRenderer
import com.t8rin.imagetoolbox.lib.portrait_analysis.report.PortraitBackendComparisonRenderer
import com.t8rin.imagetoolbox.lib.portrait_analysis.report.PortraitRepeatabilityRenderer
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
    var state by remember { mutableStateOf<PortraitLabUiState>(PortraitLabUiState.Empty) }

    DisposableEffect(runner) {
        onDispose { runner.close() }
    }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        selectedUri = uri
        state = PortraitLabUiState.Empty
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Portrait Lab")
                        Text(
                            text = "POSTAC_MASTER A1 — observation only",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                },
                navigationIcon = {
                    onGoBack?.let { callback ->
                        TextButton(onClick = callback) {
                            Text("Back")
                        }
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
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { picker.launch("image/*") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (selectedUri == null) "Select image" else "Change image")
                    }
                    Button(
                        onClick = {
                            val uri = selectedUri ?: return@Button
                            state = PortraitLabUiState.Running
                            scope.launch {
                                state = when (val result = runner.run(uri, repeatedRuns = 3)) {
                                    is PortraitLabRunResult.Success ->
                                        PortraitLabUiState.Complete(result.output)
                                    is PortraitLabRunResult.Failure ->
                                        PortraitLabUiState.Failed(
                                            listOfNotNull(
                                                result.message,
                                                result.exceptionType
                                            ).joinToString("\n")
                                        )
                                }
                            }
                        },
                        enabled = selectedUri != null && state !is PortraitLabUiState.Running,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Run 3×")
                    }
                }
            }

            when (val current = state) {
                PortraitLabUiState.Empty -> item {
                    StatusCard(
                        title = "Ready",
                        text = if (selectedUri == null) {
                            "Select one benchmark image. No pixels will be modified."
                        } else {
                            "Image selected. Run the detector benchmark."
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
                    StatusCard(title = "Benchmark failed", text = current.message)
                }

                is PortraitLabUiState.Complete -> {
                    item {
                        PortraitOverlayPreview(
                            bitmap = current.output.sourceBitmap,
                            scene = current.output.overlayScene
                        )
                    }
                    item { ResultSummaryCard(current.output) }
                    if (current.output.warnings.isNotEmpty()) {
                        item {
                            ReportCard(
                                title = "Warnings",
                                report = current.output.warnings.joinToString("\n")
                            )
                        }
                    }
                    item {
                        ReportCard(
                            title = "Parameter gates",
                            report = ParameterGateReportRenderer.render(
                                current.output.parameterReport
                            )
                        )
                    }
                    item {
                        ReportCard(
                            title = "Backend comparison",
                            report = PortraitBackendComparisonRenderer.render(
                                current.output.backendComparisonReport
                            )
                        )
                    }
                    item {
                        ReportCard(
                            title = "Repeatability",
                            report = PortraitRepeatabilityRenderer.render(
                                current.output.repeatabilityReport
                            )
                        )
                    }
                }
            }

            item { HorizontalDivider() }
            item {
                Text(
                    text = "A1 does not perform face/body deformation, identity recognition, clothing replacement or background generation.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
            }
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
                    text = buildString {
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
private fun StatusCard(title: String, text: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ResultSummaryCard(output: PortraitLabRunOutput) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("Result summary", style = MaterialTheme.typography.titleMedium)
            Text("backend results=${output.backendResults.size}")
            Text("overlay points=${output.overlayScene.pointCount}")
            Text("overlay contours=${output.overlayScene.polylineCount}")
            Text("overlay triangles=${output.overlayScene.triangleCount}")
            Text("overlay masks=${output.overlayScene.maskCount}")
            Text("enabled parameters=${output.parameterReport.enabledCount}")
            Text("disabled parameters=${output.parameterReport.disabledCount}")
            Text("repeatability=${output.repeatabilityReport.allDeterministic}")
        }
    }
}

@Composable
private fun ReportCard(title: String, report: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = report,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun PortraitOverlayPreview(
    bitmap: Bitmap,
    scene: PortraitOverlayScene
) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary

    Card(modifier = Modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 260.dp, max = 560.dp)
        ) {
            val scale = minOf(size.width / image.width, size.height / image.height)
            val drawWidth = image.width * scale
            val drawHeight = image.height * scale
            val left = (size.width - drawWidth) / 2f
            val top = (size.height - drawHeight) / 2f

            drawImage(
                image = image,
                dstOffset = IntOffset(left.toInt(), top.toInt()),
                dstSize = IntSize(drawWidth.toInt(), drawHeight.toInt())
            )

            scene.triangles.forEach { triangle ->
                drawTriangle(
                    first = triangle.first.x to triangle.first.y,
                    second = triangle.second.x to triangle.second.y,
                    third = triangle.third.x to triangle.third.y,
                    left = left,
                    top = top,
                    width = drawWidth,
                    height = drawHeight,
                    color = tertiary.copy(alpha = 0.22f)
                )
            }
            scene.polylines.forEach { polyline ->
                val path = Path()
                polyline.points.forEachIndexed { index, point ->
                    val x = left + point.x * drawWidth
                    val y = top + point.y * drawHeight
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                if (polyline.closed) path.close()
                drawPath(path, secondary, style = Stroke(width = 2f))
            }
            scene.points.forEach { point ->
                drawCircle(
                    color = primary,
                    radius = if (point.id.contains("mesh_")) 1.8f else 4f,
                    center = Offset(
                        x = left + point.position.x * drawWidth,
                        y = top + point.position.y * drawHeight
                    )
                )
            }
        }
    }
}

private fun DrawScope.drawTriangle(
    first: Pair<Float, Float>,
    second: Pair<Float, Float>,
    third: Pair<Float, Float>,
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    color: Color
) {
    val path = Path().apply {
        moveTo(left + first.first * width, top + first.second * height)
        lineTo(left + second.first * width, top + second.second * height)
        lineTo(left + third.first * width, top + third.second * height)
        close()
    }
    drawPath(path = path, color = color, style = Stroke(width = 1f))
}
