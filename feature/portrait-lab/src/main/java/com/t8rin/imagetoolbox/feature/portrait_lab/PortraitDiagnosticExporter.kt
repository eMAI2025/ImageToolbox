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
import com.t8rin.imagetoolbox.lib.portrait_analysis.overlay.OverlayPoint
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
)

suspend fun exportPortraitDiagnostics(
    context: Context,
    output: PortraitLabRunOutput,
    visibility: PortraitOverlayVisibility
): Result<Uri> = withContext(Dispatchers.IO) {
    runCatching {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val root = "portrait_debug_$timestamp"
        val target = context.createDiagnosticTarget("$root.zip")
        try {
            ZipOutputStream(target.output).use { zip ->
                zip.putBitmap("$root/01_source.jpg", output.sourceBitmap, Bitmap.CompressFormat.JPEG, 96)
                zip.putBitmap("$root/02_source_oriented.png", output.sourceBitmap, Bitmap.CompressFormat.PNG, 100)

                val boundsOnly = renderDiagnosticBitmap(
                    output,
                    PortraitOverlayVisibility(
                        boundingBox = true,
                        points = true,
                        contours = false,
                        mesh = false,
                        masks = false
                    ),
                    controlPointsOnly = true
                )
                zip.putBitmap("$root/03_face_detection.png", boundsOnly, Bitmap.CompressFormat.PNG, 100)
                boundsOnly.recycle()

                val pointsOnly = renderDiagnosticBitmap(
                    output,
                    PortraitOverlayVisibility(
                        boundingBox = false,
                        points = true,
                        contours = false,
                        mesh = false,
                        masks = false
                    )
                )
                zip.putBitmap("$root/04_landmark_points.png", pointsOnly, Bitmap.CompressFormat.PNG, 100)
                pointsOnly.recycle()

                val contoursOnly = renderDiagnosticBitmap(
                    output,
                    PortraitOverlayVisibility(
                        boundingBox = false,
                        points = false,
                        contours = true,
                        mesh = false,
                        masks = false
                    )
                )
                zip.putBitmap("$root/05_contours.png", contoursOnly, Bitmap.CompressFormat.PNG, 100)
                contoursOnly.recycle()

                val meshOnly = renderDiagnosticBitmap(
                    output,
                    PortraitOverlayVisibility(
                        boundingBox = false,
                        points = false,
                        contours = false,
                        mesh = true,
                        masks = false
                    )
                )
                zip.putBitmap("$root/06_mesh_triangles.png", meshOnly, Bitmap.CompressFormat.PNG, 100)
                meshOnly.recycle()

                val masksOnly = renderDiagnosticBitmap(
                    output,
                    PortraitOverlayVisibility(
                        boundingBox = false,
                        points = false,
                        contours = false,
                        mesh = false,
                        masks = true
                    )
                )
                zip.putBitmap("$root/07_masks.png", masksOnly, Bitmap.CompressFormat.PNG, 100)
                masksOnly.recycle()

                val combined = renderDiagnosticBitmap(output, visibility)
                zip.putBitmap("$root/08_combined_overlay.png", combined, Bitmap.CompressFormat.PNG, 100)
                val sideBySide = createSideBySide(output.previewBitmap, combined)
                zip.putBitmap("$root/09_side_by_side.png", sideBySide, Bitmap.CompressFormat.PNG, 100)
                sideBySide.recycle()
                combined.recycle()

                zip.putText("$root/detection_result.json", detectionResultJson(output).toString(2))
                zip.putText("$root/coordinate_transform.json", transformJson(output).toString(2))
                zip.putText("$root/backend_status.json", backendStatusJson(output).toString(2))
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
    val canvas = Canvas(base)
    val transform = ImageRenderTransform.fit(
        sourceWidth = output.sourceMetadata.orientedWidth,
        sourceHeight = output.sourceMetadata.orientedHeight,
        previewWidth = base.width,
        previewHeight = base.height
    )
    drawDiagnosticScene(
        canvas = canvas,
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
            color = Color.argb(150, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = (stroke * 0.55f).coerceAtLeast(1f)
        }
        scene.triangles.forEach { triangle ->
            val a = transform.normalizedToPreview(triangle.first)
            val b = transform.normalizedToPreview(triangle.second)
            val c = transform.normalizedToPreview(triangle.third)
            val path = Path().apply {
                moveTo(a.x, a.y)
                lineTo(b.x, b.y)
                lineTo(c.x, c.y)
                close()
            }
            canvas.drawPath(path, paint)
        }
    }
    scene.polylines.forEach { polyline ->
        val isBounding = polyline.id.contains("bounding_box") || polyline.id.contains("bbox")
        if ((isBounding && !visibility.boundingBox) || (!isBounding && !visibility.contours)) {
            return@forEach
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorForId(polyline.id)
            style = Paint.Style.STROKE
            strokeWidth = if (isBounding) stroke * 1.8f else stroke
        }
        drawPolyline(canvas, transform, polyline, paint)
    }
    if (visibility.points) {
        scene.points.forEach { point ->
            if (point.id.contains("bounding_box")) return@forEach
            if (controlPointsOnly && !isControlPoint(point.id) && !point.id.endsWith("detection_center")) {
                return@forEach
            }
            val mapped = transform.normalizedToPreview(point.position)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = colorForId(point.id)
                style = Paint.Style.FILL
            }
            val radius = when {
                point.id.endsWith("detection_center") -> stroke * 2.2f
                isControlPoint(point.id) -> stroke * 1.7f
                point.id.contains("mesh_") -> (stroke * 0.55f).coerceAtLeast(1.5f)
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

private fun colorForId(id: String): Int = when {
    id.contains("bounding_box") || id.contains("bbox") -> Color.WHITE
    id.contains("face") && !id.contains("surface") -> Color.RED
    id.contains("left_eye") -> Color.GREEN
    id.contains("right_eye") -> Color.BLUE
    id.contains("nose") -> Color.YELLOW
    id.contains("lip") || id.contains("mouth") -> Color.MAGENTA
    id.contains("eyebrow") || id.contains("brow") -> Color.CYAN
    id.contains("center") -> Color.WHITE
    else -> Color.rgb(0, 255, 190)
}

private fun isControlPoint(id: String): Boolean =
    id.endsWith("detection_landmark_left_eye") ||
        id.endsWith("detection_landmark_right_eye") ||
        id.endsWith("detection_landmark_nose_base") ||
        id.endsWith("detection_landmark_mouth_left") ||
        id.endsWith("detection_landmark_mouth_right")

private fun ConfidenceMask.toColorBitmap(): Bitmap {
    val pixels = IntArray(size)
    val values = copyValues()
    values.indices.forEach { index ->
        val confidence = values[index]
        pixels[index] = Color.argb((confidence * 180f).toInt(), 0, 220, 190)
    }
    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
}

private fun createSideBySide(source: Bitmap, overlay: Bitmap): Bitmap {
    val result = Bitmap.createBitmap(
        source.width + overlay.width,
        maxOf(source.height, overlay.height),
        Bitmap.Config.ARGB_8888
    )
    val canvas = Canvas(result)
    canvas.drawColor(Color.BLACK)
    canvas.drawBitmap(source, 0f, 0f, null)
    canvas.drawBitmap(overlay, source.width.toFloat(), 0f, null)
    return result
}

private fun detectionResultJson(output: PortraitLabRunOutput): JSONObject {
    val proof = output.visualProof
    val bounding = proof.boundingBox
    val face = JSONObject()
        .put("index", 0)
        .put("landmarkCount", proof.landmarkCount)
        .put("contourCount", proof.contourCount)
        .put("triangleCount", proof.triangleCount)
    if (bounding != null) {
        face.put(
            "boundingBox",
            JSONObject()
                .put("left", bounding.left * output.sourceMetadata.orientedWidth)
                .put("top", bounding.top * output.sourceMetadata.orientedHeight)
                .put("right", bounding.right * output.sourceMetadata.orientedWidth)
                .put("bottom", bounding.bottom * output.sourceMetadata.orientedHeight)
        )
    }
    return JSONObject()
        .put("backend", output.selectedBackend.name)
        .put("status", proof.status.name)
        .put(
            "sourceImage",
            JSONObject()
                .put("width", output.sourceMetadata.orientedWidth)
                .put("height", output.sourceMetadata.orientedHeight)
                .put("orientationDegrees", output.sourceMetadata.orientationDegrees)
                .put("mirrored", output.sourceMetadata.mirrored)
        )
        .put("faces", JSONArray().put(face))
        .put("inFrameLandmarkRatio", proof.inFrameLandmarkRatio)
        .put("normalizedLandmarkSpread", proof.normalizedLandmarkSpread)
        .put("controlPoints", JSONArray(proof.controlPointIds.toList()))
        .put("reasons", JSONArray(proof.reasons))
        .put(
            "firstTwentyCoordinates",
            JSONArray().apply {
                proof.firstTwentyCoordinates.forEach { coordinate ->
                    put(
                        JSONObject()
                            .put("id", coordinate.id)
                            .put("x", coordinate.x)
                            .put("y", coordinate.y)
                            .put("backend", coordinate.backend)
                    )
                }
            }
        )
}

private fun transformJson(output: PortraitLabRunOutput): JSONObject {
    val transform = ImageRenderTransform.fit(
        output.sourceMetadata.orientedWidth,
        output.sourceMetadata.orientedHeight,
        output.previewBitmap.width,
        output.previewBitmap.height
    )
    return JSONObject()
        .put("sourceWidth", transform.sourceWidth)
        .put("sourceHeight", transform.sourceHeight)
        .put("orientedWidth", output.sourceMetadata.orientedWidth)
        .put("orientedHeight", output.sourceMetadata.orientedHeight)
        .put("previewWidth", transform.previewWidth)
        .put("previewHeight", transform.previewHeight)
        .put("contentScale", transform.contentScale.name)
        .put("scale", transform.scale)
        .put("renderedWidth", transform.renderedWidth)
        .put("renderedHeight", transform.renderedHeight)
        .put("offsetX", transform.offsetX)
        .put("offsetY", transform.offsetY)
}

private fun backendStatusJson(output: PortraitLabRunOutput): JSONObject = JSONObject()
    .put("backend", output.selectedBackend.name)
    .put("status", output.visualProof.status.name)
    .put("warnings", JSONArray(output.warnings))
    .put("runtimeResultCount", output.backendResults.size)

private fun ZipOutputStream.putText(path: String, value: String) {
    putNextEntry(ZipEntry(path))
    write(value.toByteArray(Charsets.UTF_8))
    closeEntry()
}

private fun ZipOutputStream.putBitmap(
    path: String,
    bitmap: Bitmap,
    format: Bitmap.CompressFormat,
    quality: Int
) {
    val buffer = ByteArrayOutputStream()
    check(bitmap.compress(format, quality, buffer)) { "Bitmap compression failed for $path" }
    putNextEntry(ZipEntry(path))
    buffer.writeTo(this)
    closeEntry()
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
