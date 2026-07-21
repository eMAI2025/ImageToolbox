/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.feature.portrait_lab

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ObservationBackend
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.overlay.PortraitOverlaySceneBuilder
import com.t8rin.imagetoolbox.lib.portrait_analysis.visual.ImageRenderTransform
import com.t8rin.imagetoolbox.lib.portrait_analysis.visual.VisualProofEvaluator
import com.t8rin.imagetoolbox.lib.portrait_analysis_mediapipe.face.MediaPipeFaceLandmarkerConfig
import com.t8rin.imagetoolbox.lib.portrait_analysis_mediapipe.face.MediaPipeFaceLandmarkerObservationEngine
import com.t8rin.imagetoolbox.lib.portrait_analysis_mediapipe.withMediaPipeImageInput
import com.t8rin.imagetoolbox.lib.portrait_analysis_mlkit.MlKitImageInput
import com.t8rin.imagetoolbox.lib.portrait_analysis_mlkit.face.MlKitFaceDetectionObservationEngine
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt

private const val FACE_MODEL_ASSET = "models/face_landmarker.task"
private const val MAX_PREVIEW_DIMENSION = 1600
private const val ML_KIT_FACE_MESH_BLOCK_REASON =
    "Disabled in stable runtime: beta MediaPipe-internal binary incompatibility"

fun createPortraitLabRunner(context: Context): PortraitLabRunner =
    MarketPortraitLabRunner(context.applicationContext)

private class MarketPortraitLabRunner(
    private val context: Context
) : PortraitLabRunner {

    private val faceModelAvailable = context.assetExists(FACE_MODEL_ASSET)

    override val availability: List<PortraitBackendAvailability> = listOf(
        PortraitBackendAvailability(ObservationBackend.ML_KIT_FACE_DETECTION, true),
        PortraitBackendAvailability(
            ObservationBackend.ML_KIT_FACE_MESH,
            false,
            ML_KIT_FACE_MESH_BLOCK_REASON
        ),
        PortraitBackendAvailability(
            ObservationBackend.MEDIAPIPE_FACE_LANDMARKER,
            faceModelAvailable,
            if (faceModelAvailable) null else "Missing asset: $FACE_MODEL_ASSET"
        )
    )

    override suspend fun run(
        uri: Uri,
        backend: ObservationBackend
    ): PortraitLabRunResult {
        val backendAvailability = availability.firstOrNull { it.backend == backend }
        if (backendAvailability?.available != true) {
            return PortraitLabRunResult.Failure(
                backendAvailability?.reason ?: "Backend ${backend.name} is not available"
            )
        }

        val decoded = try {
            context.decodePortraitImage(uri)
        } catch (error: Exception) {
            return failure("Unable to decode the selected image", error)
        }

        val runtimeLog = mutableListOf(
            "mode=P1_VISUAL_PROOF",
            "backend=${backend.name}",
            "encoded=${decoded.metadata.encodedWidth}x${decoded.metadata.encodedHeight}",
            "oriented=${decoded.metadata.orientedWidth}x${decoded.metadata.orientedHeight}",
            "preview=${decoded.metadata.previewWidth}x${decoded.metadata.previewHeight}",
            "exif_orientation=${decoded.metadata.exifOrientation}",
            "orientation_degrees=${decoded.metadata.orientationDegrees}",
            "mirrored=${decoded.metadata.mirrored}",
            "run_count=1"
        )

        val observation = try {
            when (backend) {
                ObservationBackend.ML_KIT_FACE_DETECTION ->
                    observeMlKitFace(decoded.orientedBitmap)

                ObservationBackend.MEDIAPIPE_FACE_LANDMARKER ->
                    observeMediaPipeFace(decoded.orientedBitmap)

                else -> return PortraitLabRunResult.Failure(
                    "Backend ${backend.name} is blocked in P1 visual proof"
                )
            }
        } catch (error: LinkageError) {
            return failure("Backend linkage failed", error)
        } catch (error: Exception) {
            return failure("Backend execution failed", error)
        }

        val transform = ImageRenderTransform.fit(
            sourceWidth = decoded.metadata.orientedWidth,
            sourceHeight = decoded.metadata.orientedHeight,
            previewWidth = decoded.metadata.previewWidth,
            previewHeight = decoded.metadata.previewHeight
        )
        val visualProof = VisualProofEvaluator.evaluate(observation, transform)
        runtimeLog += "visual_status=${visualProof.status.name}"
        runtimeLog += "face_count=${visualProof.faceCount}"
        runtimeLog += "landmark_count=${visualProof.landmarkCount}"
        runtimeLog += "contour_count=${visualProof.contourCount}"
        runtimeLog += "triangle_count=${visualProof.triangleCount}"
        runtimeLog += "rendered_landmark_spread_px=${visualProof.renderedLandmarkSpreadPixels}"
        runtimeLog += visualProof.reasons.map { "visual_reason=$it" }
        if (visualProof.status.name.contains("FAILED")) {
            runtimeLog += visualProof.firstTwentyCoordinates.map {
                "raw_coordinate=${it.id},${it.x},${it.y},${it.backend}"
            }
        }

        return PortraitLabRunResult.Success(
            PortraitLabRunOutput(
                selectedBackend = backend,
                sourceBitmap = decoded.orientedBitmap,
                previewBitmap = decoded.previewBitmap,
                sourceMetadata = decoded.metadata,
                observation = observation,
                visualProof = visualProof,
                overlayScene = PortraitOverlaySceneBuilder.build(observation),
                runtimeLog = runtimeLog,
                warnings = emptyList()
            )
        )
    }

    override fun close() = Unit

    private suspend fun observeMlKitFace(bitmap: Bitmap): SubjectObservation {
        val engine = MlKitFaceDetectionObservationEngine()
        return try {
            engine.observe(MlKitImageInput.fromBitmap(bitmap))
        } finally {
            engine.close()
        }
    }

    private suspend fun observeMediaPipeFace(bitmap: Bitmap): SubjectObservation {
        val engine = MediaPipeFaceLandmarkerObservationEngine(
            context,
            MediaPipeFaceLandmarkerConfig(FACE_MODEL_ASSET)
        )
        return try {
            bitmap.withMediaPipeImageInput(engine::observe)
        } finally {
            engine.close()
        }
    }

    private fun failure(message: String, error: Throwable) = PortraitLabRunResult.Failure(
        message = error.message?.let { "$message: $it" } ?: message,
        exceptionType = error::class.qualifiedName
    )
}

private data class DecodedPortraitImage(
    val orientedBitmap: Bitmap,
    val previewBitmap: Bitmap,
    val metadata: PortraitSourceMetadata
)

private fun Context.decodePortraitImage(uri: Uri): DecodedPortraitImage {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    val decoded = contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
        ?: throw IOException("Unable to open image: $uri")
    val decodedWidth = decoded.width
    val decodedHeight = decoded.height

    val orientation = contentResolver.openInputStream(uri)?.use { stream ->
        ExifInterface(stream).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
    } ?: ExifInterface.ORIENTATION_NORMAL

    val transform = exifTransform(orientation)
    val oriented = if (transform.matrix.isIdentity) {
        decoded
    } else {
        Bitmap.createBitmap(
            decoded,
            0,
            0,
            decodedWidth,
            decodedHeight,
            transform.matrix,
            true
        ).also { if (it !== decoded) decoded.recycle() }
    }
    val argb = if (oriented.config == Bitmap.Config.ARGB_8888) {
        oriented
    } else {
        oriented.copy(Bitmap.Config.ARGB_8888, false) ?: oriented
    }
    val preview = argb.scaledPreview(MAX_PREVIEW_DIMENSION)
    return DecodedPortraitImage(
        orientedBitmap = argb,
        previewBitmap = preview,
        metadata = PortraitSourceMetadata(
            encodedWidth = bounds.outWidth.takeIf { it > 0 } ?: decodedWidth,
            encodedHeight = bounds.outHeight.takeIf { it > 0 } ?: decodedHeight,
            orientedWidth = argb.width,
            orientedHeight = argb.height,
            exifOrientation = orientation,
            orientationDegrees = transform.rotationDegrees,
            mirrored = transform.mirrored,
            previewWidth = preview.width,
            previewHeight = preview.height
        )
    )
}

private data class ExifBitmapTransform(
    val matrix: Matrix,
    val rotationDegrees: Int,
    val mirrored: Boolean
)

private fun exifTransform(orientation: Int): ExifBitmapTransform {
    val matrix = Matrix()
    var degrees = 0
    var mirrored = false
    when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> {
            matrix.setScale(-1f, 1f)
            mirrored = true
        }
        ExifInterface.ORIENTATION_ROTATE_180 -> {
            matrix.setRotate(180f)
            degrees = 180
        }
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
            matrix.setScale(1f, -1f)
            mirrored = true
        }
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.setRotate(90f)
            matrix.postScale(-1f, 1f)
            degrees = 90
            mirrored = true
        }
        ExifInterface.ORIENTATION_ROTATE_90 -> {
            matrix.setRotate(90f)
            degrees = 90
        }
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.setRotate(-90f)
            matrix.postScale(-1f, 1f)
            degrees = 270
            mirrored = true
        }
        ExifInterface.ORIENTATION_ROTATE_270 -> {
            matrix.setRotate(-90f)
            degrees = 270
        }
    }
    return ExifBitmapTransform(matrix, degrees, mirrored)
}

private fun Bitmap.scaledPreview(maxDimension: Int): Bitmap {
    val largest = max(width, height)
    if (largest <= maxDimension) return this
    val scale = maxDimension.toFloat() / largest.toFloat()
    return Bitmap.createScaledBitmap(
        this,
        (width * scale).roundToInt().coerceAtLeast(1),
        (height * scale).roundToInt().coerceAtLeast(1),
        true
    )
}

private fun Context.assetExists(path: String): Boolean = try {
    assets.open(path).use { Unit }
    true
} catch (_: IOException) {
    false
}
