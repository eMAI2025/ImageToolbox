/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis_mlkit

import com.t8rin.imagetoolbox.lib.portrait_analysis.catalog.DefaultPortraitParameterCatalog
import com.t8rin.imagetoolbox.lib.portrait_analysis.engine.ObservationPipelineResult
import com.t8rin.imagetoolbox.lib.portrait_analysis.engine.PortraitObservationEngine
import com.t8rin.imagetoolbox.lib.portrait_analysis.engine.SequentialPortraitObservationPipeline
import com.t8rin.imagetoolbox.lib.portrait_analysis.report.PortraitBenchmarkEvaluation
import com.t8rin.imagetoolbox.lib.portrait_analysis.report.PortraitBenchmarkReportBuilder
import com.t8rin.imagetoolbox.lib.portrait_analysis_mlkit.face.MlKitFaceDetectionObservationEngine
import com.t8rin.imagetoolbox.lib.portrait_analysis_mlkit.face.MlKitFaceMeshObservationEngine
import com.t8rin.imagetoolbox.lib.portrait_analysis_mlkit.pose.MlKitPoseObservationEngine
import com.t8rin.imagetoolbox.lib.portrait_analysis_mlkit.segmentation.MlKitSelfieSegmentationObservationEngine

/**
 * Market-build observation pipeline using only on-device ML Kit detectors.
 *
 * Detectors execute sequentially to provide independent timing and bounded memory pressure.
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
            faceMeshEngine,
            poseEngine,
            segmentationEngine
        )
    )

    suspend fun observe(input: MlKitImageInput): ObservationPipelineResult =
        pipeline.observe(input)

    suspend fun observeAndBuildReport(
        input: MlKitImageInput
    ): PortraitBenchmarkEvaluation = PortraitBenchmarkReportBuilder.build(
        pipelineResult = observe(input),
        gateSpecs = DefaultPortraitParameterCatalog.gateSpecs
    )

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
