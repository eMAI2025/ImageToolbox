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
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ConfidenceMask
import com.t8rin.imagetoolbox.lib.portrait_analysis.overlay.OverlayPolyline
import com.t8rin.imagetoolbox.lib.portrait_analysis.overlay.PortraitOverlayScene
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

data class PortraitOverlayVisibility(
    val boundingBox: Boolean = true,
    val points: Boolean = true,
    val contours: Boolean = true,
    val mesh: Boolean = true,
    val masks: Boolean = true
) {
    companion object {
        fun none() = PortraitOverlayVisibility(
            boundingBox = false,
            points = false,
            contours = false,
            mesh = false,
            masks = false
        )
    }
}

suspend fun exportPortraitDiagnostics(
    context: Context,
    output: PortraitLabRunOutput,
    visibility: PortraitOverlayVisibility,
    controlPointsOnly: Boolean = false
): Result<Uri> = withContext(Dispatchers.IO) {
    runCatching {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val root = "portrait_visual_proof_$timestamp"
        val target = context.createDiagnosticTarget("$root.zip")
        try {
            ZipOutputStream(target.output).use { zip ->
                zip.putBitmap(
                    "$root/01_source_oriented.png",
                    output.sourceBitmap,
                    Bitmap.CompressFormat.PNG,
                    100
                )
                zip.putBitmap(
                    "$root/02_stage_source.png",
                    renderDiagnosticBitmap(output, PortraitOverlayVisibility.none()),
                    Bitmap.CompressFormat.PNG,
                    100,
                    recycleAfter = true
                )
                zip.putBitmap(
                    "$root/03_stage_bounding_box.png",
                    renderDiagnosticBitmap(
                        output,
                        VisualProofStage.BOUNDING_BOX.visibility
                    ),
                    Bitmap.CompressFormat.PNG,
                    100,
                    recycleAfter = true
                )
                zip.putBitmap(
                    "$root/04_stage_five_points.png",
                    renderDiagnosticBitmap(
                        output,
                        VisualProofStage.FIVE_POINTS.visibility,
                        controlPointsOnly = true
                    ),
                    Bitmap.CompressFormat.PNG,
                    100,
                    recycleAfter = true
                )
                zip.putBitmap(
                    "$root/05_stage_contours.png",
                    renderDiagnosticBitmap(output, VisualProofStage.CONTOURS.visibility),
                    Bitmap.CompressFormat.PNG,
                    100,
                    recycleAfter = true
                )
                zip.putBitmap(
                    "$root/06_stage_full_mesh.png",
                    renderDiagnosticBitmap(output, VisualProofStage.FULL_MESH.visibility),
                    Bitmap.CompressFormat.PNG,
                    100,
                    recycleAfter = true
                )
                val masksOnly = PortraitOverlayVisibility.none().copy(masks = true)
                zip.putBitmap(
                    "$root/07_masks.png",
                    renderDiagnosticBitmap(output, masksOnly),
                    Bitmap.CompressFormat.PNG,
                    100,
                    recycleAfter = true
                )
                zip.putBitmap(
                    "$root/08_current_overlay.png",
                    renderDiagnosticBitmap(output, visibility, controlPointsOnly),
                    Bitmap.CompressFormat.PNG,
                    100,
                    recycleAfter = true
                )

                zip.putText("$root/detection_result.json", detectionResultJson(output).toString(2))
                zip.putText(
                    "$root/coordinate_transform.json",
                    transformJson(output).toString(2)
                )
                zip.putText("$root/visual_status.json", visualStatusJson(output).toString(2))
                zip.putText("$root/runtime_log.txt", output.runtimeLog.joinToString("\n") + "\n")
            }
            target.complete(context)
            target.uri
        } catch (error: Throwable) {
            target.abort(context)
            throw error
        }
    }
}

fun renderDiagnosticBitmap(
    output: PortraitLabRunOutput,
    visibility: PortraitOverlayVisibility,
    controlPointsOnly: Boolean = false
): Bitmap {
    val base = output.previewBitmap.copy(Bitmap.Config.ARGB_8888, true)
        ?: error("Unable to create mutable diagnostic bitmap")
    val transform = ImageRenderTransform.fit(
        sourceWidth = output.sourceMetadata.orientedWidth,
        sourceHeight = output.sourceMetadata.orientedHeight,
        previewWidth = base.width,
        previewHeight = base.height
    )
    drawDiagnosticScene(
        canvas = Canvas(base),
        scene = output.overlayScene,
        transform = transform,
        visibility = visibility,
        controlPointsOnly = controlPointsOnly
    )
    return base
}

private fun drawDiagnosticScene(
    canvas: Canvas,
    scene: PortraitOverlayScene,
    transform: ImageRenderTransform,
    visibility: PortraitOverlayVisibility,
    controlPointsOnly: Boolean
) {
    val stroke = (transform.previewWidth / 420f).coerceIn(2f, 8f)

    if (visibility.masks) {
        scene.masks.forEach { overlay ->
            val bitmap = overlay.mask.toColorBitmap()
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { alpha = 110 }
            canvas.drawBitmap(
                bitmap,
                Rect(0, 0, bitmap.width, bitmap.height),
                RectF(
                    transform.offsetX,
                    transform.offsetY,
                    transform.offsetX + transform.renderedWidth,
                    transform.offsetY + transform.renderedHeight
                ),
                paint
            )
            bitmap.recycle()
        }
    }

    if (visibility.mesh) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(145, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = (stroke * 0.45f).coerceAtLeast(1f)
        }
        scene.triangles.forEach { triangle ->
            val first = transform.normalizedToPreview(triangle.first)
            val second = transform.normalizedToPreview(triangle.second)
            val third = transform.normalizedToPreview(triangle.third)
            val path = Path().apply {
                moveTo(first.x, first.y)
                lineTo(second.x, second.y)
                lineTo(third.x, third.y)
                close()
            }
            canvas.drawPath(path, paint)
        }
    }

    scene.polylines.forEach { polyline ->
        val bounding = isBoundingBox(polyline.id)
        if ((bounding && !visibility.boundingBox) || (!bounding && !visibility.contours)) {
            return@forEach
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorForId(polyline.id)
            style = Paint.Style.STROKE
            strokeWidth = if (bounding) stroke * 1.8f else stroke
        }
        drawPolyline(canvas, transform, polyline, paint)
    }

    if (visibility.points) {
        scene.points.forEach { point ->
            if (point.id.contains("bounding_box")) return@forEach
            if (controlPointsOnly && !isControlPoint(point.id)) return@forEach
            val mapped = transform.normalizedToPreview(point.position)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = colorForId(point.id)
                style = Paint.Style.FILL
            }
            val radius = when {
                isControlPoint(point.id) -> stroke * 1.9f
                point.id.contains("mesh_") || point.id.contains("mediapipe_") ->
                    (stroke * 0.55f).coerceAtLeast(1.5f)
                else -> stroke
            }
            canvas.drawCircle(mapped.x, mapped.y, radius, paint)
        }
    }
}

private fun drawPolyline(
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

private fun isBoundingBox(id: String): Boolean =
    id.contains("bounding_box") || id.contains("bbox")

private fun colorForId(id: String): Int = when {
    isBoundingBox(id) -> Color.WHITE
    id.contains("left_eye") -> Color.GREEN
    id.contains("right_eye") -> Color.BLUE
    id.contains("eyebrow") || id.contains("eye_brow") || id.contains("brow") -> Color.CYAN
    id.contains("nose") -> Color.YELLOW
    id.contains("lip") || id.contains("mouth") || id.contains("lips") -> Color.MAGENTA
    id.contains("jawline") -> Color.rgb(255, 128, 0)
    id.contains("chin") -> Color.rgb(255, 64, 64)
    id.contains("face_oval") || id.contains("contour_face") -> Color.RED
    id.contains("center") -> Color.WHITE
    else -> Color.rgb(0, 255, 190)
}

private fun isControlPoint(id: String): Boolean =
    id.endsWith("detection_landmark_left_eye") ||
        id.endsWith("detection_landmark_right_eye") ||
        id.endsWith("detection_landmark_nose_base") ||
        id.endsWith("detection_landmark_mouth_left") ||
        id.endsWith("detection_landmark_mouth_right") ||
        id == "left_eye_center" ||
        id == "right_eye_center" ||
        id == "nose_base" ||
        id == "mouth_left_corner" ||
        id == "mouth_right_corner"

private fun ConfidenceMask.toColorBitmap(): Bitmap {
    val pixels = IntArray(size)
    val values = copyValues()
    values.indices.forEach { index ->
        pixels[index] = Color.argb((values[index] * 180f).toInt(), 0, 220, 190)
    }
    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
}

private fun detectionResultJson(output: PortraitLabRunOutput): JSONObject {
    val transform = ImageRenderTransform.fit(
        output.sourceMetadata.orientedWidth,
        output.sourceMetadata.orientedHeight,
        output.previewBitmap.width,
        output.previewBitmap.height
    )
    val landmarks = JSONArray()
    output.observation.landmarks.values.sortedBy { it.id }.forEach { landmark ->
        val source = transform.normalizedToSource(landmark.point)
        val preview = transform.normalizedToPreview(landmark.point)
        landmarks.put(
            JSONObject()
                .put("id", landmark.id)
                .put("xNormalized", landmark.point.x)
                .put("yNormalized", landmark.point.y)
                .put("zNormalized", landmark.point.z)
                .put("xSourcePixels", source.x)
                .put("ySourcePixels", source.y)
                .put("xPreviewPixels", preview.x)
                .put("yPreviewPixels", preview.y)
                .put("confidence", landmark.confidence ?: JSONObject.NULL)
                .put("confidenceSource", landmark.confidenceSource.name)
                .put("visibility", landmark.visibility.name)
                .put("backend", landmark.backend.name)
        )
    }

    val contours = JSONArray()
    output.observation.contours.values.sortedBy { it.id }.forEach { contour ->
        contours.put(
            JSONObject()
                .put("id", contour.id)
                .put("closed", contour.closed)
                .put("backend", contour.backend.name)
                .put("vertexIds", JSONArray(contour.vertexIds))
        )
    }

    val meshes = JSONArray()
    output.observation.meshes.values.sortedBy { it.id }.forEach { mesh ->
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
                .put("vertexCount", mesh.vertexIds.size)
                .put("triangleCount", mesh.triangles.size)
                .put("triangles", triangles)
        )
    }

    val masks = JSONArray()
    output.observation.masks.values.sortedBy { it.id }.forEach { mask ->
        masks.put(
            JSONObject()
                .put("id", mask.id)
                .put("backend", mask.backend.name)
                .put("width", mask.mask.width)
                .put("height", mask.mask.height)
        )
    }

    return JSONObject()
        .put("mode", "P1_VISUAL_PROOF")
        .put("backend", output.selectedBackend.name)
        .put("status", output.visualProof.status.name)
        .put("sourceImage", sourceMetadataJson(output))
        .put("faceCount", output.observation.faceCount)
        .put("landmarks", landmarks)
        .put("contours", contours)
        .put("meshes", meshes)
        .put("masks", masks)
        .put("firstTwentyCoordinates", firstTwentyJson(output))
}

private fun transformJson(output: PortraitLabRunOutput): JSONObject {
    val transform = ImageRenderTransform.fit(
        output.sourceMetadata.orientedWidth,
        output.sourceMetadata.orientedHeight,
        output.previewBitmap.width,
        output.previewBitmap.height
    )
    return JSONObject()
        .put("encodedWidth", output.sourceMetadata.encodedWidth)
        .put("encodedHeight", output.sourceMetadata.encodedHeight)
        .put("exifOrientation", output.sourceMetadata.exifOrientation)
        .put("exifRotationDegrees", output.sourceMetadata.orientationDegrees)
        .put("mirrored", output.sourceMetadata.mirrored)
        .put("sourceWidth", transform.sourceWidth)
        .put("sourceHeight", transform.sourceHeight)
        .put("previewWidth", transform.previewWidth)
        .put("previewHeight", transform.previewHeight)
        .put("contentScale", transform.contentScale.name)
        .put("scale", transform.scale)
        .put("renderedWidth", transform.renderedWidth)
        .put("renderedHeight", transform.renderedHeight)
        .put("offsetX", transform.offsetX)
        .put("offsetY", transform.offsetY)
        .put(
            "sourceToPreviewMatrix3x3",
            JSONArray(
                listOf(
                    transform.scale, 0f, transform.offsetX,
                    0f, transform.scale, transform.offsetY,
                    0f, 0f, 1f
                )
            )
        )
        .put("detectorCoordinateSpace", "normalized_oriented_bitmap")
        .put("exifAppliedBeforeDetection", true)
        .put("bitmapAndOverlayShareTransform", true)
}

private fun sourceMetadataJson(output: PortraitLabRunOutput): JSONObject = JSONObject()
    .put("encodedWidth", output.sourceMetadata.encodedWidth)
    .put("encodedHeight", output.sourceMetadata.encodedHeight)
    .put("orientedWidth", output.sourceMetadata.orientedWidth)
    .put("orientedHeight", output.sourceMetadata.orientedHeight)
    .put("previewWidth", output.sourceMetadata.previewWidth)
    .put("previewHeight", output.sourceMetadata.previewHeight)
    .put("exifOrientation", output.sourceMetadata.exifOrientation)
    .put("orientationDegrees", output.sourceMetadata.orientationDegrees)
    .put("mirrored", output.sourceMetadata.mirrored)

private fun visualStatusJson(output: PortraitLabRunOutput): JSONObject = JSONObject()
    .put("backend", output.selectedBackend.name)
    .put("status", output.visualProof.status.name)
    .put("automaticPassAllowed", false)
    .put("reasons", JSONArray(output.visualProof.reasons))
    .put("landmarkCount", output.visualProof.landmarkCount)
    .put("contourCount", output.visualProof.contourCount)
    .put("triangleCount", output.visualProof.triangleCount)
    .put("renderedLandmarkSpreadPixels", output.visualProof.renderedLandmarkSpreadPixels)
    .put("semanticContourGroups", JSONArray(output.visualProof.semanticContourGroups.toList()))

private fun firstTwentyJson(output: PortraitLabRunOutput): JSONArray = JSONArray().apply {
    output.visualProof.firstTwentyCoordinates.forEach { coordinate ->
        put(
            JSONObject()
                .put("id", coordinate.id)
                .put("x", coordinate.x)
                .put("y", coordinate.y)
                .put("backend", coordinate.backend)
        )
    }
}

private fun ZipOutputStream.putText(path: String, value: String) {
    putNextEntry(ZipEntry(path))
    write(value.toByteArray(Charsets.UTF_8))
    closeEntry()
}

private fun ZipOutputStream.putBitmap(
    path: String,
    bitmap: Bitmap,
    format: Bitmap.CompressFormat,
    quality: Int,
    recycleAfter: Boolean = false
) {
    try {
        val buffer = ByteArrayOutputStream()
        check(bitmap.compress(format, quality, buffer)) { "Bitmap compression failed for $path" }
        putNextEntry(ZipEntry(path))
        buffer.writeTo(this)
        closeEntry()
    } finally {
        if (recycleAfter && !bitmap.isRecycled) bitmap.recycle()
    }
}

private data class DiagnosticTarget(
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

private fun Context.createDiagnosticTarget(fileName: String): DiagnosticTarget {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/ImageToolbox")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Unable to create diagnostic package in Downloads")
        val output = contentResolver.openOutputStream(uri)
            ?: error("Unable to open diagnostic package output")
        return DiagnosticTarget(uri, output, mediaStorePending = true)
    }

    val directory = File(
        getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
        "ImageToolbox"
    ).apply { mkdirs() }
    val file = File(directory, fileName)
    return DiagnosticTarget(Uri.fromFile(file), FileOutputStream(file), mediaStorePending = false)
}
