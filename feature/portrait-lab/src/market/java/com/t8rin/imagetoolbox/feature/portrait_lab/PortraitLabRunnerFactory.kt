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
import android.net.Uri
import com.t8rin.imagetoolbox.lib.portrait_analysis.catalog.DefaultPortraitParameterCatalog
import com.t8rin.imagetoolbox.lib.portrait_analysis.derive.BodySilhouetteEnricher
import com.t8rin.imagetoolbox.lib.portrait_analysis.engine.ObservationBenchmarkResult
import com.t8rin.imagetoolbox.lib.portrait_analysis.engine.ObservationPipelineResult
import com.t8rin.imagetoolbox.lib.portrait_analysis.engine.PortraitObservationEngine
import com.t8rin.imagetoolbox.lib.portrait_analysis.engine.SequentialPortraitObservationPipeline
import com.t8rin.imagetoolbox.lib.portrait_analysis.merge.ObservationMergeResult
import com.t8rin.imagetoolbox.lib.portrait_analysis.merge.SubjectObservationMerger
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ObservationBackend
import com.t8rin.imagetoolbox.lib.portrait_analysis.overlay.PortraitOverlaySceneBuilder
import com.t8rin.imagetoolbox.lib.portrait_analysis.report.ParameterGateReportBuilder
import com.t8rin.imagetoolbox.lib.portrait_analysis.report.PortraitBackendComparisonReportBuilder
import com.t8rin.imagetoolbox.lib.portrait_analysis.report.PortraitRepeatabilityReportBuilder
import com.t8rin.imagetoolbox.lib.portrait_analysis_mediapipe.MediaPipeImageInput
import com.t8rin.imagetoolbox.lib.portrait_analysis_mediapipe.face.MediaPipeFaceLandmarkerConfig
import com.t8rin.imagetoolbox.lib.portrait_analysis_mediapipe.face.MediaPipeFaceLandmarkerObservationEngine
import com.t8rin.imagetoolbox.lib.portrait_analysis_mediapipe.pose.MediaPipePoseLandmarkerConfig
import com.t8rin.imagetoolbox.lib.portrait_analysis_mediapipe.pose.MediaPipePoseLandmarkerObservationEngine
import com.t8rin.imagetoolbox.lib.portrait_analysis_mediapipe.withMediaPipeImageInput
import com.t8rin.imagetoolbox.lib.portrait_analysis_mlkit.MlKitImageInput
import com.t8rin.imagetoolbox.lib.portrait_analysis_mlkit.MlKitPortraitObservationPipeline
import java.io.IOException

private const val FACE_MODEL_ASSET = "models/face_landmarker.task"
private const val POSE_MODEL_ASSET = "models/pose_landmarker_lite.task"
private const val ML_KIT_FACE_MESH_BLOCK_REASON =
    "Disabled in stable runtime: beta MediaPipe-internal binary incompatibility"

fun createPortraitLabRunner(context: Context): PortraitLabRunner =
    MarketPortraitLabRunner(context.applicationContext)

private class MarketPortraitLabRunner(
    private val context: Context
) : PortraitLabRunner {

    private val mlKitPipelineDelegate = lazy(LazyThreadSafetyMode.NONE) {
        MlKitPortraitObservationPipeline.stable()
    }
    private val mlKitPipeline by mlKitPipelineDelegate
    private val faceModelAvailable = context.assetExists(FACE_MODEL_ASSET)
    private val poseModelAvailable = context.assetExists(POSE_MODEL_ASSET)

    override val availability: List<PortraitBackendAvailability> = listOf(
        PortraitBackendAvailability(
            backend = ObservationBackend.ML_KIT_FACE_DETECTION,
            available = true
        ),
        PortraitBackendAvailability(
            backend = ObservationBackend.ML_KIT_FACE_MESH,
            available = false,
            reason = ML_KIT_FACE_MESH_BLOCK_REASON
        ),
        PortraitBackendAvailability(
            backend = ObservationBackend.ML_KIT_POSE,
            available = true
        ),
        PortraitBackendAvailability(
            backend = ObservationBackend.ML_KIT_SELFIE_SEGMENTATION,
            available = true
        ),
        PortraitBackendAvailability(
            backend = ObservationBackend.MEDIAPIPE_FACE_LANDMARKER,
            available = faceModelAvailable,
            reason = if (faceModelAvailable) null else "Missing asset: $FACE_MODEL_ASSET"
        ),
        PortraitBackendAvailability(
            backend = ObservationBackend.MEDIAPIPE_POSE,
            available = poseModelAvailable,
            reason = if (poseModelAvailable) null else "Missing asset: $POSE_MODEL_ASSET"
        )
    )

    override suspend fun run(
        uri: Uri,
        repeatedRuns: Int
    ): PortraitLabRunResult {
        if (repeatedRuns < 2) {
            return PortraitLabRunResult.Failure("At least two repeated runs are required")
        }

        val bitmap = try {
            context.decodeBitmap(uri)
        } catch (error: Exception) {
            return failure("Unable to decode the selected image", error)
        }

        val pipeline = try {
            mlKitPipeline
        } catch (error: LinkageError) {
            return failure("ML Kit stable runtime initialization failed", error)
        } catch (error: Exception) {
            return failure("ML Kit stable runtime initialization failed", error)
        }

        val warnings = mutableListOf(
            "ML Kit Face Mesh is disabled in the stable runtime"
        )
        if (!faceModelAvailable) {
            warnings += "Face fallback active: ML Kit Face Detection"
        }

        val backendResults = mutableListOf<ObservationBenchmarkResult>()
        val mlKitInput = MlKitImageInput.fromBitmap(bitmap)

        repeat(repeatedRuns) { repeatedRunIndex ->
            collect(
                result = pipeline.observe(mlKitInput),
                repeatedRunIndex = repeatedRunIndex,
                source = "ML Kit stable",
                target = backendResults,
                warnings = warnings
            )
        }

        val mediaPipeEngines = createMediaPipeEngines(warnings)
        if (mediaPipeEngines.isNotEmpty()) {
            val mediaPipePipeline = SequentialPortraitObservationPipeline(mediaPipeEngines)
            try {
                repeat(repeatedRuns) { repeatedRunIndex ->
                    bitmap.withMediaPipeImageInput { mediaPipeInput ->
                        collect(
                            result = mediaPipePipeline.observe(mediaPipeInput),
                            repeatedRunIndex = repeatedRunIndex,
                            source = "MediaPipe",
                            target = backendResults,
                            warnings = warnings
                        )
                    }
                }
            } finally {
                mediaPipeEngines.forEach { engine ->
                    runCatching { (engine as? AutoCloseable)?.close() }
                        .exceptionOrNull()
                        ?.let { warnings += "MediaPipe close failed: ${it.message}" }
                }
            }
        }

        if (backendResults.isEmpty()) {
            return PortraitLabRunResult.Failure("No detector produced a benchmark result")
        }

        val firstRunObservations = backendResults
            .filter { it.measurement.repeatedRunIndex == 0 }
            .map(ObservationBenchmarkResult::observation)
        val merged = SubjectObservationMerger.merge(firstRunObservations)
        val mergedObservation = when (merged) {
            is ObservationMergeResult.Success -> merged.observation
            is ObservationMergeResult.Conflict -> {
                warnings += "Observation merge conflicts: ${merged.conflicts.size}"
                merged.partialObservation
            }
        }
        val silhouette = BodySilhouetteEnricher.enrich(mergedObservation)
        if (silhouette.skippedSections.isNotEmpty()) {
            warnings += "Body silhouette sections skipped: ${silhouette.skippedSections.size}"
        }
        val finalObservation = silhouette.observation
        val gateSpecs = DefaultPortraitParameterCatalog.gateSpecs

        return PortraitLabRunResult.Success(
            PortraitLabRunOutput(
                sourceBitmap = bitmap,
                overlayScene = PortraitOverlaySceneBuilder.build(finalObservation),
                parameterReport = ParameterGateReportBuilder.build(
                    specs = gateSpecs,
                    observation = finalObservation
                ),
                backendComparisonReport = PortraitBackendComparisonReportBuilder.build(
                    backendResults = backendResults,
                    gateSpecs = gateSpecs
                ),
                repeatabilityReport = PortraitRepeatabilityReportBuilder.build(
                    backendResults = backendResults,
                    gateSpecs = gateSpecs
                ),
                backendResults = backendResults.toList(),
                warnings = warnings.distinct()
            )
        )
    }

    override fun close() {
        if (mlKitPipelineDelegate.isInitialized()) {
            mlKitPipeline.close()
        }
    }

    private fun createMediaPipeEngines(
        warnings: MutableList<String>
    ): List<PortraitObservationEngine<MediaPipeImageInput>> = buildList {
        if (faceModelAvailable) {
            try {
                add(
                    MediaPipeFaceLandmarkerObservationEngine(
                        context = context,
                        config = MediaPipeFaceLandmarkerConfig(
                            modelAssetPath = FACE_MODEL_ASSET
                        )
                    )
                )
            } catch (error: LinkageError) {
                warnings += "MediaPipe face unavailable: ${error.message ?: error::class.simpleName}"
            } catch (error: Exception) {
                warnings += "MediaPipe face unavailable: ${error.message ?: error::class.simpleName}"
            }
        }
        if (poseModelAvailable) {
            try {
                add(
                    MediaPipePoseLandmarkerObservationEngine(
                        context = context,
                        config = MediaPipePoseLandmarkerConfig(
                            modelAssetPath = POSE_MODEL_ASSET
                        )
                    )
                )
            } catch (error: LinkageError) {
                warnings += "MediaPipe pose unavailable: ${error.message ?: error::class.simpleName}"
            } catch (error: Exception) {
                warnings += "MediaPipe pose unavailable: ${error.message ?: error::class.simpleName}"
            }
        }
    }

    private fun collect(
        result: ObservationPipelineResult,
        repeatedRunIndex: Int,
        source: String,
        target: MutableList<ObservationBenchmarkResult>,
        warnings: MutableList<String>
    ) {
        target += result.backendResults.map { benchmark ->
            benchmark.copy(
                measurement = benchmark.measurement.copy(
                    repeatedRunIndex = repeatedRunIndex
                )
            )
        }
        when (result) {
            is ObservationPipelineResult.Success -> Unit
            is ObservationPipelineResult.MergeConflict ->
                warnings += "$source merge conflicts: ${result.conflicts.size}"
            is ObservationPipelineResult.BackendFailure ->
                warnings += "$source ${result.failure.backend.name} failed: " +
                    (result.failure.message ?: result.failure.exceptionType)
        }
    }

    private fun failure(message: String, error: Throwable): PortraitLabRunResult.Failure =
        PortraitLabRunResult.Failure(
            message = error.message?.let { "$message: $it" } ?: message,
            exceptionType = error::class.qualifiedName
        )
}

private fun Context.assetExists(path: String): Boolean = try {
    assets.open(path).use { Unit }
    true
} catch (_: IOException) {
    false
}

private fun Context.decodeBitmap(uri: Uri): Bitmap {
    val decoded = contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
        ?: throw IOException("Unable to open image: $uri")
    return if (decoded.config == Bitmap.Config.ARGB_8888) {
        decoded
    } else {
        decoded.copy(Bitmap.Config.ARGB_8888, false) ?: decoded
    }
}
