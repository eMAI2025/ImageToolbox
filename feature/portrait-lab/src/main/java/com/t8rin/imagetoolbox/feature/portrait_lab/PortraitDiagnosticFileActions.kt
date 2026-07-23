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
import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

fun portraitDiagnosticSuggestedFileName(): String =
    "portrait_visual_proof_" +
        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) +
        ".zip"

suspend fun copyPortraitDiagnosticArchive(
    context: Context,
    source: Uri,
    destination: Uri
): Result<Uri> = withContext(Dispatchers.IO) {
    runCatching {
        val resolver = context.contentResolver
        resolver.openInputStream(source)?.use { input ->
            resolver.openOutputStream(destination, "w")?.use { output ->
                input.copyTo(output)
            } ?: error("Unable to open selected destination")
        } ?: error("Unable to open the generated diagnostic archive")
        destination
    }
}

fun openPortraitDiagnosticArchive(context: Context, uri: Uri): Result<Unit> = runCatching {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/zip")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(intent)
}

fun copyPortraitTextReport(context: Context, output: PortraitLabRunOutput) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(
        ClipData.newPlainText("Portrait Lab diagnostic report", buildPortraitTextReport(output))
    )
}

fun buildPortraitTextReport(output: PortraitLabRunOutput): String = buildString {
    val proof = output.visualProof
    val metadata = output.sourceMetadata
    appendLine("P1-VISUAL-PROOF / P2-REGION-AWARE")
    appendLine("backend=${output.selectedBackend.name}")
    appendLine("status=${proof.status.name}")
    appendLine("encoded=${metadata.encodedWidth}x${metadata.encodedHeight}")
    appendLine("oriented=${metadata.orientedWidth}x${metadata.orientedHeight}")
    appendLine("preview=${metadata.previewWidth}x${metadata.previewHeight}")
    appendLine("faces=${proof.faceCount}")
    appendLine("points=${proof.landmarkCount}")
    appendLine("contours=${proof.contourCount}")
    appendLine("triangles=${proof.triangleCount}")
    appendLine("rendered_spread=${proof.renderedLandmarkSpreadPixels}")
    output.faceRegionAwareness?.let { awareness ->
        appendLine("pose_mode=${awareness.poseMode.name}")
        appendLine("dominant_image_side=${awareness.dominantImageSide.name}")
        appendLine("yaw=${awareness.yawDegrees}")
        appendLine("pitch=${awareness.pitchDegrees}")
        appendLine("roll=${awareness.rollDegrees}")
        appendLine("full_face_geometry_allowed=${awareness.fullFaceGeometryAllowed}")
        awareness.regions.values.forEach { region ->
            appendLine(
                "region=${region.region.name};" +
                    "availability=${region.availability.name};" +
                    "evidence=${region.evidenceCount};" +
                    "geometry_allowed=${region.geometryEditAllowed}"
            )
        }
    }
    proof.reasons.forEach { appendLine("reason=$it") }
    output.warnings.forEach { appendLine("warning=$it") }
    appendLine()
    appendLine("RUNTIME LOG")
    output.runtimeLog.forEach(::appendLine)
}
