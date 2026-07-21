/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis_mlkit

import com.t8rin.imagetoolbox.lib.portrait_analysis.catalog.DefaultPortraitParameterCatalog
import com.t8rin.imagetoolbox.lib.portrait_analysis.derive.BodySilhouetteEnricher
import com.t8rin.imagetoolbox.lib.portrait_analysis.derive.BodySilhouetteEnrichmentResult
import com.t8rin.imagetoolbox.lib.portrait_analysis.engine.ObservationPipelineResult
import com.t8rin.imagetoolbox.lib.portrait_analysis.engine.PortraitObservationEngine
import com.t8rin.imagetoolbox.lib.portrait_analysis.engine.SequentialPortraitObservationPipeline
import com.t8rin.imagetoolbox.lib.portrait_analysis.report.PortraitBenchmarkEvaluation
import com.t8rin.imagetoolbox.lib.portrait_analysis.report.PortraitBenchmarkReportBuilder
import com.t8rin.imagetoolbox.lib.portrait_analysis_mlkit.face.MlKitFaceDetectionObservationEngine
import com.t8rin.imagetoolbox.lib.portrait_analysis_mlkit.face.MlKitFaceMeshObservationEngine
import com.t8rin.imagetoolbox.lib.portrait_analysis_mlkit.pose.MlKitPoseObservationEngine
import com.t8rin.imagetoolbox.lib.portrait_analysis_mlkit.segmentation.MlKitSelfieSegmentationObservationEngine

data class MlKitPortraitBenchmarkResult(
    val evaluation: PortraitBenchmarkEvaluation,
    val bodySilhouetteEnrichment: BodySilhouetteEnrichmentResult?
)

/**
 * Market-build observation pipeline using on-device ML Kit detectors.
 *
 * The stable runtime deliberately excludes ML Kit Face Mesh because its beta MediaPipe-internal
 * dependency has caused binary incompatibility with the rest of the detector stack on the target
 * device. Detailed face landmarks are supplied by the isolated MediaPipe adapter when its model is
 * available. The experimental face-mesh path remains explicit and opt-in.
 */
class MlKitPortraitObservationPipeline(
    private val faceDetectionEngine: MlKitFaceDetectionObservationEngine =
        MlKitFaceDetectionObservationEngine(),
    private val poseEngine: MlKitPoseObservationEngine =
        MlKitPoseObservationEngine(),
    private val segmentationEngine: MlKitSelfieSegmentationObservationEngine =
        MlKitSelfieSegmentationObservationEngine(),
    private val faceMeshEngine: MlKitFaceMeshObservationEngine? = null
) : AutoCloseable {

    val experimentalFaceMeshEnabled: Boolean
        get() = faceMeshEngine != null

    private val pipeline = SequentialPortraitObservationPipeline(
        engines = buildList<PortraitObservationEngine<MlKitImageInput>> {
            add(faceDetectionEngine)
            add(poseEngine)
            add(segmentationEngine)
            faceMeshEngine?.let(::add)
        }
    )

    suspend fun observe(input: MlKitImageInput): ObservationPipelineResult =
        pipeline.observe(input)

    suspend fun observeAndBuildReport(
        input: MlKitImageInput
    ): PortraitBenchmarkEvaluation = observeAndBuildDetailedReport(input).evaluation

    suspend fun observeAndBuildDetailedReport(
        input: MlKitImageInput
    ): MlKitPortraitBenchmarkResult {
        val rawResult = observe(input)
        val enrichment = when (rawResult) {
            is ObservationPipelineResult.Success -> BodySilhouetteEnricher.enrich(
                rawResult.observation
            )

            is ObservationPipelineResult.BackendFailure,
            is ObservationPipelineResult.MergeConflict -> null
        }
        val resultForReport = if (
            rawResult is ObservationPipelineResult.Success && enrichment != null
        ) {
            rawResult.copy(observation = enrichment.observation)
        } else {
            rawResult
        }
        return MlKitPortraitBenchmarkResult(
            evaluation = PortraitBenchmarkReportBuilder.build(
                pipelineResult = resultForReport,
                gateSpecs = DefaultPortraitParameterCatalog.gateSpecs
            ),
            bodySilhouetteEnrichment = enrichment
        )
    }

    override fun close() {
        val failures = buildList {
            runCatching { faceDetectionEngine.close() }.exceptionOrNull()?.let(::add)
            runCatching { poseEngine.close() }.exceptionOrNull()?.let(::add)
            runCatching { segmentationEngine.close() }.exceptionOrNull()?.let(::add)
            faceMeshEngine?.let { engine ->
                runCatching { engine.close() }.exceptionOrNull()?.let(::add)
            }
        }

        if (failures.isNotEmpty()) {
            val primary = failures.first()
            failures.drop(1).forEach(primary::addSuppressed)
            throw primary
        }
    }

    companion object {
        fun stable(): MlKitPortraitObservationPipeline = MlKitPortraitObservationPipeline()

        fun experimentalWithFaceMesh(): MlKitPortraitObservationPipeline =
            MlKitPortraitObservationPipeline(
                faceMeshEngine = MlKitFaceMeshObservationEngine()
            )
    }
}
