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
import com.t8rin.imagetoolbox.lib.portrait_analysis.catalog.DefaultPortraitParameterCatalog
import com.t8rin.imagetoolbox.lib.portrait_analysis.engine.ObservationBenchmarkResult
import com.t8rin.imagetoolbox.lib.portrait_analysis.engine.ObservationPipelineResult
import com.t8rin.imagetoolbox.lib.portrait_analysis.engine.PortraitObservationEngine
import com.t8rin.imagetoolbox.lib.portrait_analysis.engine.SequentialPortraitObservationPipeline
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ObservationBackend
import com.t8rin.imagetoolbox.lib.portrait_analysis.overlay.PortraitOverlaySceneBuilder
import com.t8rin.imagetoolbox.lib.portrait_analysis.report.ParameterGateReportBuilder
import com.t8rin.imagetoolbox.lib.portrait_analysis.report.PortraitBackendComparisonReportBuilder
import com.t8rin.imagetoolbox.lib.portrait_analysis.report.PortraitRepeatabilityReportBuilder
import com.t8rin.imagetoolbox.lib.portrait_analysis.visual.VisualProofEvaluator
import com.t8rin.imagetoolbox.lib.portrait_analysis_mediapipe.MediaPipeImageInput
import com.t8rin.imagetoolbox.lib.portrait_analysis_mediapipe.face.MediaPipeFaceLandmarkerConfig
import com.t8rin.imagetoolbox.lib.portrait_analysis_mediapipe.face.MediaPipeFaceLandmarkerObservationEngine
import com.t8rin.imagetoolbox.lib.portrait_analysis_mediapipe.pose.MediaPipePoseLandmarkerConfig
import com.t8rin.imagetoolbox.lib.portrait_analysis_mediapipe.pose.MediaPipePoseLandmarkerObservationEngine
import com.t8rin.imagetoolbox.lib.portrait_analysis_mediapipe.withMediaPipeImageInput
import com.t8rin.imagetoolbox.lib.portrait_analysis_mlkit.MlKitImageInput
import com.t8rin.imagetoolbox.lib.portrait_analysis_mlkit.face.MlKitFaceDetectionObservationEngine
import com.t8rin.imagetoolbox.lib.portrait_analysis_mlkit.pose.MlKitPoseObservationEngine
import com.t8rin.imagetoolbox.lib.portrait_analysis_mlkit.segmentation.MlKitSelfieSegmentationObservationEngine
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt

private const val FACE_MODEL_ASSET = "models/face_landmarker.task"
private const val POSE_MODEL_ASSET = "models/pose_landmarker_lite.task"
private const val MAX_PREVIEW_DIMENSION = 1600
private const val ML_KIT_FACE_MESH_BLOCK_REASON =
    "Disabled in stable runtime: beta MediaPipe-internal binary incompatibility"

fun createPortraitLabRunner(context: Context): PortraitLabRunner =
    MarketPortraitLabRunner(context.applicationContext)

private class MarketPortraitLabRunner(
    private val context: Context
) : PortraitLabRunner {

    private val faceModelAvailable = context.assetExists(FACE_MODEL_ASSET)
    private val poseModelAvailable = context.assetExists(POSE_MODEL_ASSET)

    override val availability: List<PortraitBackendAvailability> = listOf(
        PortraitBackendAvailability(ObservationBackend.ML_KIT_FACE_DETECTION, true),
        PortraitBackendAvailability(
            ObservationBackend.ML_KIT_FACE_MESH,
            false,
            ML_KIT_FACE_MESH_BLOCK_REASON
        ),
        PortraitBackendAvailability(ObservationBackend.ML_KIT_POSE, true),
        PortraitBackendAvailability(ObservationBackend.ML_KIT_SELFIE_SEGMENTATION, true),
        PortraitBackendAvailability(
            ObservationBackend.MEDIAPIPE_FACE_LANDMARKER,
            faceModelAvailable,
            if (faceModelAvailable) null else "Missing asset: $FACE_MODEL_ASSET"
        ),
        PortraitBackendAvailability(
            ObservationBackend.MEDIAPIPE_POSE,
            poseModelAvailable,
            if (poseModelAvailable) null else "Missing asset: $POSE_MODEL_ASSET"
        )
    )

    override suspend fun run(
        uri: Uri,
        backend: ObservationBackend,
        repeatedRuns: Int
    ): PortraitLabRunResult {
        if (repeatedRuns !in 1..10) {
            return PortraitLabRunResult.Failure("repeatedRuns must be in 1..10")
        }
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

        val warnings = mutableListOf<String>()
        val runtimeLog = mutableListOf(
            "backend=${backend.name}",
            "encoded=${decoded.metadata.encodedWidth}x${decoded.metadata.encodedHeight}",
            "oriented=${decoded.metadata.orientedWidth}x${decoded.metadata.orientedHeight}",
            "preview=${decoded.metadata.previewWidth}x${decoded.metadata.previewHeight}",
            "orientation_degrees=${decoded.metadata.orientationDegrees}",
            "mirrored=${decoded.metadata.mirrored}",
            "repeated_runs=$repeatedRuns"
        )

        val backendResults = try {
            when (backend) {
                ObservationBackend.ML_KIT_FACE_DETECTION -> runMlKit(
                    MlKitFaceDetectionObservationEngine(),
                    decoded.orientedBitmap,
                    repeatedRuns,
                    warnings,
                    runtimeLog
                )
                ObservationBackend.ML_KIT_POSE -> runMlKit(
                    MlKitPoseObservationEngine(),
                    decoded.orientedBitmap,
                    repeatedRuns,
                    warnings,
                    runtimeLog
                )
                ObservationBackend.ML_KIT_SELFIE_SEGMENTATION -> runMlKit(
                    MlKitSelfieSegmentationObservationEngine(),
                    decoded.orientedBitmap,
                    repeatedRuns,
                    warnings,
                    runtimeLog
                )
                ObservationBackend.MEDIAPIPE_FACE_LANDMARKER -> runMediaPipe(
                    MediaPipeFaceLandmarkerObservationEngine(
                        context,
                        MediaPipeFaceLandmarkerConfig(FACE_MODEL_ASSET)
                    ),
                    decoded.orientedBitmap,
                    repeatedRuns,
                    warnings,
                    runtimeLog
                )
                ObservationBackend.MEDIAPIPE_POSE -> runMediaPipe(
                    MediaPipePoseLandmarkerObservationEngine(
                        context,
                        MediaPipePoseLandmarkerConfig(POSE_MODEL_ASSET)
                    ),
                    decoded.orientedBitmap,
                    repeatedRuns,
                    warnings,
                    runtimeLog
                )
                else -> return PortraitLabRunResult.Failure(
                    "Backend ${backend.name} is blocked in P1 visual proof"
                )
            }
        } catch (error: LinkageError) {
            return failure("Backend linkage failed", error)
        } catch (error: Exception) {
            return failure("Backend execution failed", error)
        }

        if (backendResults.isEmpty()) {
            return PortraitLabRunResult.Failure("Backend produced no benchmark result")
        }

        val observation = backendResults.minBy { it.measurement.repeatedRunIndex }.observation
        val visualProof = VisualProofEvaluator.evaluate(observation)
        runtimeLog += "visual_status=${visualProof.status.name}"
        runtimeLog += "face_count=${visualProof.faceCount}"
        runtimeLog += "landmark_count=${visualProof.landmarkCount}"
        runtimeLog += "contour_count=${visualProof.contourCount}"
        runtimeLog += "triangle_count=${visualProof.triangleCount}"
        runtimeLog += "in_frame_ratio=${visualProof.inFrameLandmarkRatio}"
        runtimeLog += "landmark_spread=${visualProof.normalizedLandmarkSpread}"
        runtimeLog += visualProof.reasons.map { "visual_reason=$it" }

        val gateSpecs = DefaultPortraitParameterCatalog.gateSpecs
        return PortraitLabRunResult.Success(
            PortraitLabRunOutput(
                selectedBackend = backend,
                sourceBitmap = decoded.orientedBitmap,
                previewBitmap = decoded.previewBitmap,
                sourceMetadata = decoded.metadata,
                observation = observation,
                visualProof = visualProof,
                overlayScene = PortraitOverlaySceneBuilder.build(observation),
                parameterReport = ParameterGateReportBuilder.build(gateSpecs, observation),
                backendComparisonReport = PortraitBackendComparisonReportBuilder.build(
                    backendResults,
                    gateSpecs
                ),
                repeatabilityReport = PortraitRepeatabilityReportBuilder.build(
                    backendResults,
                    gateSpecs
                ),
                backendResults = backendResults,
                runtimeLog = runtimeLog,
                warnings = warnings.distinct()
            )
        )
    }

    override fun close() = Unit

    private suspend fun runMlKit(
        engine: PortraitObservationEngine<MlKitImageInput>,
        bitmap: Bitmap,
        repeatedRuns: Int,
        warnings: MutableList<String>,
        runtimeLog: MutableList<String>
    ): List<ObservationBenchmarkResult> {
        val pipeline = SequentialPortraitObservationPipeline(listOf(engine))
        val input = MlKitImageInput.fromBitmap(bitmap)
        return try {
            collectRepeated(
                repeatedRuns,
                observe = { pipeline.observe(input) },
                warnings,
                runtimeLog
            )
        } finally {
            (engine as? AutoCloseable)?.close()
        }
    }

    private suspend fun runMediaPipe(
        engine: PortraitObservationEngine<MediaPipeImageInput>,
        bitmap: Bitmap,
        repeatedRuns: Int,
        warnings: MutableList<String>,
        runtimeLog: MutableList<String>
    ): List<ObservationBenchmarkResult> {
        val pipeline = SequentialPortraitObservationPipeline(listOf(engine))
        return try {
            collectRepeated(
                repeatedRuns,
                observe = {
                    bitmap.withMediaPipeImageInput { input -> pipeline.observe(input) }
                },
                warnings,
                runtimeLog
            )
        } finally {
            (engine as? AutoCloseable)?.close()
        }
    }

    private suspend fun collectRepeated(
        repeatedRuns: Int,
        observe: suspend () -> ObservationPipelineResult,
        warnings: MutableList<String>,
        runtimeLog: MutableList<String>
    ): List<ObservationBenchmarkResult> {
        val collected = mutableListOf<ObservationBenchmarkResult>()
        repeat(repeatedRuns) { runIndex ->
            when (val result = observe()) {
                is ObservationPipelineResult.Success -> {
                    result.backendResults.forEach { benchmark ->
                        val indexed = benchmark.copy(
                            measurement = benchmark.measurement.copy(repeatedRunIndex = runIndex)
                        )
                        collected += indexed
                        runtimeLog += "run_${runIndex}_time_ms=${indexed.measurement.processingTimeMillis}"
                    }
                }
                is ObservationPipelineResult.MergeConflict -> {
                    warnings += "Unexpected single-backend merge conflict: ${result.conflicts.size}"
                    result.backendResults.forEach { benchmark ->
                        collected += benchmark.copy(
                            measurement = benchmark.measurement.copy(repeatedRunIndex = runIndex)
                        )
                    }
                }
                is ObservationPipelineResult.BackendFailure -> {
                    val detail = result.failure.message ?: result.failure.exceptionType
                    warnings += "${result.failure.backend.name} failed: $detail"
                }
            }
        }
        return collected
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
