/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.feature.portrait_lab

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.t8rin.imagetoolbox.lib.portrait_analysis.derive.PostacMasterDiagnosticLayers
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ConfidenceMask
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ObservationBackend
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.overlay.OverlayPolyline
import com.t8rin.imagetoolbox.lib.portrait_analysis.overlay.PortraitOverlayScene
import com.t8rin.imagetoolbox.lib.portrait_analysis.overlay.PortraitOverlaySceneBuilder
import com.t8rin.imagetoolbox.lib.portrait_analysis.visual.ImageRenderTransform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class SilhouetteOverlayVisibility(
    val mask: Boolean = true,
    val pose: Boolean = true,
    val sections: Boolean = true
)

suspend fun exportSilhouetteDiagnostics(
    context: Context,
    output: SilhouetteLabRunOutput,
    visibility: SilhouetteOverlayVisibility = SilhouetteOverlayVisibility()
): Result<Uri> = withContext(Dispatchers.IO) {
    runCatching {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val root = "silhouette_visual_proof_$timestamp"
        val target = context.createSilhouetteDiagnosticTarget("$root.zip")
        val layers = output.toDiagnosticLayers()
        try {
            ZipOutputStream(target.output).use { zip ->
                zip.putSilhouetteBitmap("$root/01_source_preview.png", output.previewBitmap)
                zip.putSilhouetteBitmap(
                    "$root/${PostacMasterDiagnosticLayers.RAW_MASK_FILE}",
                    renderMaskObservation(output, layers.raw),
                    recycleAfter = true
                )
                zip.putSilhouetteBitmap(
                    "$root/${PostacMasterDiagnosticLayers.FILTERED_MASK_FILE}",
                    renderMaskObservation(output, output.observation),
                    recycleAfter = true
                )
                zip.putSilhouetteBitmap(
                    "$root/03_pose_accepted.png",
                    renderSilhouetteDiagnosticBitmap(
                        output,
                        SilhouetteOverlayVisibility(mask = false, pose = true, sections = false)
                    ),
                    recycleAfter = true
                )
                zip.putSilhouetteBitmap(
                    "$root/04_sections_accepted.png",
                    renderSilhouetteDiagnosticBitmap(
                        output,
                        SilhouetteOverlayVisibility(mask = false, pose = false, sections = true)
                    ),
                    recycleAfter = true
                )
                zip.putSilhouetteBitmap(
                    "$root/05_combined_accepted.png",
                    renderSilhouetteDiagnosticBitmap(output, visibility),
                    recycleAfter = true
                )
                zip.putSilhouetteText(
                    "$root/${PostacMasterDiagnosticLayers.MANIFEST_FILE}",
                    diagnosticManifestJson(output, layers).toString(2)
                )
                zip.putSilhouetteText(
                    "$root/${PostacMasterDiagnosticLayers.RAW_GEOMETRY_FILE}",
                    observationLayerJson("RAW", layers.raw).toString(2)
                )
                zip.putSilhouetteText(
                    "$root/${PostacMasterDiagnosticLayers.FILTERED_GEOMETRY_FILE}",
                    observationLayerJson("FILTERED", layers.filtered).toString(2)
                )
                zip.putSilhouetteText(
                    "$root/${PostacMasterDiagnosticLayers.ACCEPTED_GEOMETRY_FILE}",
                    acceptedLayerJson(layers).toString(2)
                )
                // Legacy file retained for compatibility; it contains accepted geometry only.
                zip.putSilhouetteText(
                    "$root/silhouette_result.json",
                    silhouetteResultJson(output).toString(2)
                )
                zip.putSilhouetteText(
                    "$root/runtime_log.txt",
                    output.runtimeLog.joinToString("\n") + "\n"
                )
            }
            target.complete(context)
            target.uri
        } catch (error: Throwable) {
            target.abort(context)
            throw error
        }
    }
}

private fun SilhouetteLabRunOutput.toDiagnosticLayers(): PostacMasterDiagnosticLayers {
    val accepted = observation.takeUnless { status == SilhouetteVisualStatus.FAILED }
    val rejectionCodes = regionCapabilities
        .filterNot(SilhouetteRegionCapability::available)
        .mapTo(linkedSetOf()) { capability ->
            capability.reason ?: "REGION_BLOCKED:${capability.regionId}"
        }
    return PostacMasterDiagnosticLayers.fromBody(
        raw = rawObservation,
        filtered = filteredObservation,
        accepted = accepted,
        rejectionCodes = rejectionCodes
    )
}

fun renderSilhouetteDiagnosticBitmap(
    output: SilhouetteLabRunOutput,
    visibility: SilhouetteOverlayVisibility
): Bitmap = renderSilhouetteScene(
    output = output,
    scene = output.overlayScene,
    visibility = visibility
)

private fun renderMaskObservation(
    output: SilhouetteLabRunOutput,
    observation: SubjectObservation
): Bitmap = renderSilhouetteScene(
    output = output,
    scene = PortraitOverlaySceneBuilder.build(observation),
    visibility = SilhouetteOverlayVisibility(mask = true, pose = false, sections = false)
)

private fun renderSilhouetteScene(
    output: SilhouetteLabRunOutput,
    scene: PortraitOverlayScene,
    visibility: SilhouetteOverlayVisibility
): Bitmap {
    val base = output.previewBitmap.copy(Bitmap.Config.ARGB_8888, true)
        ?: error("Unable to create mutable silhouette bitmap")
    val transform = ImageRenderTransform.fit(
        sourceWidth = output.sourceMetadata.orientedWidth,
        sourceHeight = output.sourceMetadata.orientedHeight,
        previewWidth = base.width,
        previewHeight = base.height
    )
    val canvas = Canvas(base)
    val stroke = (base.width / 420f).coerceIn(2f, 8f)

    if (visibility.mask) {
        scene.masks.forEach { overlay ->
            val bitmap = overlay.mask.toSilhouetteMaskBitmap()
            canvas.drawBitmap(
                bitmap,
                Rect(0, 0, bitmap.width, bitmap.height),
                RectF(
                    transform.offsetX,
                    transform.offsetY,
                    transform.offsetX + transform.renderedWidth,
                    transform.offsetY + transform.renderedHeight
                ),
                Paint(Paint.ANTI_ALIAS_FLAG).apply { alpha = 95 }
            )
            bitmap.recycle()
        }
    }

    if (visibility.pose) {
        scene.polylines
            .filter { it.id.startsWith("derived_body_skeleton_") }
            .forEach { polyline ->
                drawSilhouettePolyline(
                    canvas,
                    transform,
                    polyline,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.CYAN
                        style = Paint.Style.STROKE
                        strokeWidth = stroke
                    }
                )
            }
        scene.points
            .filter { it.backend == ObservationBackend.ML_KIT_POSE }
            .forEach { point ->
                val mapped = transform.normalizedToPreview(point.position)
                canvas.drawCircle(
                    mapped.x,
                    mapped.y,
                    stroke * 1.4f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = when (point.visibility.name) {
                            "VISIBLE" -> Color.YELLOW
                            "PARTIALLY_VISIBLE" -> Color.rgb(255, 140, 0)
                            else -> Color.GRAY
                        }
                        style = Paint.Style.FILL
                    }
                )
            }
    }

    if (visibility.sections) {
        scene.polylines
            .filter {
                it.id.startsWith("derived_body_") &&
                    !it.id.startsWith("derived_body_skeleton_")
            }
            .forEach { polyline ->
                drawSilhouettePolyline(
                    canvas,
                    transform,
                    polyline,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = sectionColor(polyline.id)
                        style = Paint.Style.STROKE
                        strokeWidth = stroke * 2.1f
                    }
                )
            }
    }
    return base
}

fun buildSilhouetteTextReport(output: SilhouetteLabRunOutput): String = buildString {
    appendLine("B1-SILHOUETTE-OBSERVATION")
    appendLine("status=${output.status.name}")
    appendLine("subject_count=${output.observation.subjectCount}")
    appendLine("body_count=${output.observation.bodyCount}")
    appendLine(
        "pose_landmarks=" + output.observation.landmarks.values.count {
            it.backend == ObservationBackend.ML_KIT_POSE
        }
    )
    appendLine("masks=${output.observation.masks.size}")
    appendLine("derived_regions=${output.enrichment.derivedRegionIds.size}")
    output.regionCapabilities.forEach { region ->
        appendLine(
            "region=${region.regionId};available=${region.available};reason=${region.reason.orEmpty()}"
        )
    }
    output.enrichment.skippedSections.forEach { skipped ->
        appendLine(
            "skipped=${skipped.sectionId};reason=${skipped.reason.name};item=${skipped.itemId.orEmpty()}"
        )
    }
    output.warnings.forEach { appendLine("warning=$it") }
    appendLine()
    appendLine("RUNTIME LOG")
    output.runtimeLog.forEach(::appendLine)
}

private fun diagnosticManifestJson(
    output: SilhouetteLabRunOutput,
    layers: PostacMasterDiagnosticLayers
): JSONObject = JSONObject()
    .put("diagnosticSchema", "POSTAC_MASTER_DIAGNOSTICS_V2")
    .put("layerContract", layers.contractVersion)
    .put("state", layers.state.name)
    .put("rejectionCodes", JSONArray(layers.rejectionCodes.sorted()))
    .put("requiredFiles", JSONArray(PostacMasterDiagnosticLayers.REQUIRED_EXPORT_FILES.toList()))
    .put("buildBranch", BuildConfig.POSTAC_MASTER_BUILD_BRANCH)
    .put("buildCommit", BuildConfig.POSTAC_MASTER_BUILD_COMMIT)
    .put("status", output.status.name)
    .put("counts", countsJson(layers.counts))
    .put("deformationEnabled", false)

private fun countsJson(counts: PostacMasterDiagnosticLayers.Counts): JSONObject = JSONObject()
    .put("rawLandmarks", counts.rawLandmarks)
    .put("filteredLandmarks", counts.filteredLandmarks)
    .put("acceptedLandmarks", counts.acceptedLandmarks)
    .put("rawContours", counts.rawContours)
    .put("filteredContours", counts.filteredContours)
    .put("acceptedContours", counts.acceptedContours)
    .put("rawTriangles", counts.rawTriangles)
    .put("filteredTriangles", counts.filteredTriangles)
    .put("acceptedTriangles", counts.acceptedTriangles)
    .put("rawMasks", counts.rawMasks)
    .put("filteredMasks", counts.filteredMasks)
    .put("acceptedMasks", counts.acceptedMasks)

private fun acceptedLayerJson(layers: PostacMasterDiagnosticLayers): JSONObject = JSONObject()
    .put("layer", "ACCEPTED")
    .put("state", layers.state.name)
    .put("rejectionCodes", JSONArray(layers.rejectionCodes.sorted()))
    .put(
        "observation",
        layers.accepted?.let(::subjectObservationJson) ?: JSONObject.NULL
    )

private fun observationLayerJson(
    layer: String,
    observation: SubjectObservation
): JSONObject = JSONObject()
    .put("layer", layer)
    .put("observation", subjectObservationJson(observation))

private fun subjectObservationJson(observation: SubjectObservation): JSONObject {
    val landmarks = JSONArray()
    observation.landmarks.values.sortedBy { it.id }.forEach { landmark ->
        landmarks.put(
            JSONObject()
                .put("id", landmark.id)
                .put("x", landmark.point.x)
                .put("y", landmark.point.y)
                .put("z", landmark.point.z ?: JSONObject.NULL)
                .put("confidence", landmark.confidence ?: JSONObject.NULL)
                .put("confidenceSource", landmark.confidenceSource.name)
                .put("visibility", landmark.visibility.name)
                .put("backend", landmark.backend.name)
        )
    }
    val contours = JSONArray()
    observation.contours.values.sortedBy { it.id }.forEach { contour ->
        contours.put(
            JSONObject()
                .put("id", contour.id)
                .put("closed", contour.closed)
                .put("backend", contour.backend.name)
                .put("vertexIds", JSONArray(contour.vertexIds))
        )
    }
    val meshes = JSONArray()
    observation.meshes.values.sortedBy { it.id }.forEach { mesh ->
        val triangles = JSONArray()
        mesh.triangles.forEach { triangle ->
            triangles.put(
                JSONObject()
                    .put("first", triangle.firstVertexId)
                    .put("second", triangle.secondVertexId)
                    .put("third", triangle.thirdVertexId)
            )
        }
        meshes.put(
            JSONObject()
                .put("id", mesh.id)
                .put("backend", mesh.backend.name)
                .put("vertexIds", JSONArray(mesh.vertexIds.sorted()))
                .put("triangles", triangles)
        )
    }
    val masks = JSONArray()
    observation.masks.values.sortedBy { it.id }.forEach { mask ->
        masks.put(
            JSONObject()
                .put("id", mask.id)
                .put("backend", mask.backend.name)
                .put("width", mask.mask.width)
                .put("height", mask.mask.height)
        )
    }
    return JSONObject()
        .put("subjectCount", observation.subjectCount)
        .put("faceCount", observation.faceCount)
        .put("bodyCount", observation.bodyCount)
        .put("landmarks", landmarks)
        .put("contours", contours)
        .put("meshes", meshes)
        .put("masks", masks)
        .put("regionIds", JSONArray(observation.regions.keys.sorted()))
}

private fun silhouetteResultJson(output: SilhouetteLabRunOutput): JSONObject = JSONObject()
    .put("mode", "B1_SILHOUETTE_OBSERVATION")
    .put("status", output.status.name)
    .put("sourceWidth", output.sourceMetadata.orientedWidth)
    .put("sourceHeight", output.sourceMetadata.orientedHeight)
    .put("acceptedObservation", subjectObservationJson(output.observation))
    .put("derivedRegionIds", JSONArray(output.enrichment.derivedRegionIds.sorted()))
    .put(
        "regionCapabilities",
        JSONArray().apply {
            output.regionCapabilities.forEach { region ->
                put(
                    JSONObject()
                        .put("regionId", region.regionId)
                        .put("available", region.available)
                        .put("reason", region.reason ?: JSONObject.NULL)
                )
            }
        }
    )
    .put("visibleSilhouetteIncludesClothing", true)
    .put("deformationEnabled", false)

private fun drawSilhouettePolyline(
    canvas: Canvas,
    transform: ImageRenderTransform,
    polyline: OverlayPolyline,
    paint: Paint
) {
    val path = Path()
    polyline.points.forEachIndexed { index, point ->
        val mapped = transform.normalizedToPreview(point)
        if (index == 0) path.moveTo(mapped.x, mapped.y) else path.lineTo(mapped.x, mapped.y)
    }
    if (polyline.closed) path.close()
    canvas.drawPath(path, paint)
}

private fun sectionColor(id: String): Int = when {
    id.contains("shoulder") -> Color.GREEN
    id.contains("waist") -> Color.MAGENTA
    id.contains("hip") -> Color.BLUE
    id.contains("arm") || id.contains("forearm") -> Color.rgb(255, 128, 0)
    id.contains("thigh") || id.contains("calf") -> Color.RED
    else -> Color.WHITE
}

private fun ConfidenceMask.toSilhouetteMaskBitmap(): Bitmap {
    val values = copyValues()
    val pixels = IntArray(values.size)
    values.indices.forEach { index ->
        pixels[index] = Color.argb((values[index] * 170f).toInt(), 0, 220, 190)
    }
    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
}

private fun ZipOutputStream.putSilhouetteText(path: String, value: String) {
    putNextEntry(ZipEntry(path))
    write(value.toByteArray(Charsets.UTF_8))
    closeEntry()
}

private fun ZipOutputStream.putSilhouetteBitmap(
    path: String,
    bitmap: Bitmap,
    recycleAfter: Boolean = false
) {
    try {
        val buffer = ByteArrayOutputStream()
        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, buffer)) {
            "Bitmap compression failed for $path"
        }
        putNextEntry(ZipEntry(path))
        buffer.writeTo(this)
        closeEntry()
    } finally {
        if (recycleAfter && !bitmap.isRecycled) bitmap.recycle()
    }
}

private data class SilhouetteDiagnosticTarget(
    val uri: Uri,
    val output: OutputStream,
    val mediaStorePending: Boolean
) {
    fun complete(context: Context) {
        if (mediaStorePending && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.contentResolver.update(
                uri,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null
            )
        }
    }

    fun abort(context: Context) {
        runCatching { output.close() }
        if (mediaStorePending) {
            context.contentResolver.delete(uri, null, null)
        } else {
            runCatching { File(uri.path.orEmpty()).delete() }
        }
    }
}

private fun Context.createSilhouetteDiagnosticTarget(fileName: String): SilhouetteDiagnosticTarget {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/ImageToolbox")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Unable to create silhouette package in Downloads")
        val output = contentResolver.openOutputStream(uri)
            ?: error("Unable to open silhouette package output")
        return SilhouetteDiagnosticTarget(uri, output, mediaStorePending = true)
    }
    val directory = File(
        getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
        "ImageToolbox"
    ).apply { mkdirs() }
    val file = File(directory, fileName)
    return SilhouetteDiagnosticTarget(
        Uri.fromFile(file),
        FileOutputStream(file),
        mediaStorePending = false
    )
}
