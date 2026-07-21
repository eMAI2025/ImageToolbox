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
 * Market-build observation pipeline using only on-device ML Kit detectors.
 *
 * Detectors execute sequentially to provide independent timing and bounded memory pressure.
 * Stable body/segmentation backends execute before the optional beta face-mesh backend so a
 * contained face-mesh compatibility failure still preserves the earlier measurements.
 */
class MlKitPortraitObservationPipeline(
    private val faceDetectionEngine: MlKitFaceDetectionObservationEngine =
        MlKitFaceDetectionObservationEngine(),
    private val faceMeshEngine: MlKitFaceMeshObservationEngine =
        MlKitFaceMeshObservationEngine(),
    private val poseEngine: MlKitPoseObservationEngine =
        MlKitPoseObservationEngine(),
    private val segmentationEngine: MlKitSelfieSegmentationObservationEngine =
        MlKitSelfieSegmentationObservationEngine()
) : AutoCloseable {

    private val pipeline = SequentialPortraitObservationPipeline(
        engines = listOf<PortraitObservationEngine<MlKitImageInput>>(
            faceDetectionEngine,
            poseEngine,
            segmentationEngine,
            faceMeshEngine
        )
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
        val failures = listOf(
            runCatching { faceDetectionEngine.close() }.exceptionOrNull(),
            runCatching { faceMeshEngine.close() }.exceptionOrNull(),
            runCatching { poseEngine.close() }.exceptionOrNull(),
            runCatching { segmentationEngine.close() }.exceptionOrNull()
        ).filterNotNull()

        if (failures.isNotEmpty()) {
            val primary = failures.first()
            failures.drop(1).forEach(primary::addSuppressed)
            throw primary
        }
    }
}
