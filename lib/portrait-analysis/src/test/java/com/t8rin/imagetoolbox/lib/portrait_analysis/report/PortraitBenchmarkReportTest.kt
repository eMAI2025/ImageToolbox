/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis.report

import com.t8rin.imagetoolbox.lib.portrait_analysis.engine.BenchmarkMeasurement
import com.t8rin.imagetoolbox.lib.portrait_analysis.engine.ObservationBenchmarkResult
import com.t8rin.imagetoolbox.lib.portrait_analysis.engine.ObservationEngineFailure
import com.t8rin.imagetoolbox.lib.portrait_analysis.engine.ObservationPipelineResult
import com.t8rin.imagetoolbox.lib.portrait_analysis.gate.ParameterGateSpec
import com.t8rin.imagetoolbox.lib.portrait_analysis.merge.ObservationMergeConflict
import com.t8rin.imagetoolbox.lib.portrait_analysis.merge.ObservationMergeConflictType
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ObservationBackend
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PortraitBenchmarkReportTest {

    @Test
    fun `builds parameter report only for successful merged observation`() {
        val evaluation = PortraitBenchmarkReportBuilder.build(
            pipelineResult = ObservationPipelineResult.Success(
                backendResults = listOf(backendResult()),
                observation = observation()
            ),
            gateSpecs = listOf(
                ParameterGateSpec(
                    parameterId = "safe_observation",
                    minimumLandmarkConfidence = null,
                    minimumRegionConfidence = null,
                    maximumRegionOcclusion = null
                )
            )
        )

        assertTrue(evaluation is PortraitBenchmarkEvaluation.Ready)
        val report = (evaluation as PortraitBenchmarkEvaluation.Ready).report
        assertEquals(1, report.parameterReport.enabledCount)
        assertEquals(1, report.backendResults.size)
    }

    @Test
    fun `preserves merge conflict without creating parameter report`() {
        val conflict = ObservationMergeConflict(
            type = ObservationMergeConflictType.REGION_ID_COLLISION,
            itemId = "subject_mask"
        )
        val evaluation = PortraitBenchmarkReportBuilder.build(
            pipelineResult = ObservationPipelineResult.MergeConflict(
                backendResults = listOf(backendResult()),
                partialObservation = observation(),
                conflicts = listOf(conflict)
            ),
            gateSpecs = emptyList()
        )

        assertTrue(evaluation is PortraitBenchmarkEvaluation.MergeConflict)
        assertEquals(
            listOf(conflict),
            (evaluation as PortraitBenchmarkEvaluation.MergeConflict).conflicts
        )
    }

    @Test
    fun `preserves backend failure and completed measurements`() {
        val failure = ObservationEngineFailure(
            backend = ObservationBackend.ML_KIT_POSE,
            exceptionType = "java.lang.IllegalStateException",
            message = "failed"
        )
        val evaluation = PortraitBenchmarkReportBuilder.build(
            pipelineResult = ObservationPipelineResult.BackendFailure(
                backendResults = listOf(backendResult()),
                failure = failure
            ),
            gateSpecs = emptyList()
        )

        assertTrue(evaluation is PortraitBenchmarkEvaluation.BackendFailure)
        val result = evaluation as PortraitBenchmarkEvaluation.BackendFailure
        assertEquals(failure, result.failure)
        assertEquals(1, result.backendResults.size)
    }

    private fun backendResult() = ObservationBenchmarkResult(
        observation = observation(),
        measurement = BenchmarkMeasurement(
            backend = ObservationBackend.ML_KIT_FACE_MESH,
            processingTimeMillis = 10L
        )
    )

    private fun observation() = SubjectObservation(
        subjectCount = 1,
        faceCount = 1,
        bodyCount = 0,
        landmarks = emptyMap(),
        regions = emptyMap()
    )
}
