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
import com.t8rin.imagetoolbox.lib.portrait_analysis.gate.ParameterGateSpec
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ConfidenceSource
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.LandmarkObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.NormalizedPoint3D
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ObservationBackend
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.VisibilityState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PortraitBackendComparisonReportTest {

    private val gate = ParameterGateSpec(
        parameterId = "test_parameter",
        requiredLandmarkIds = setOf("anchor"),
        minimumLandmarkConfidence = 0.90f
    )

    @Test
    fun `reports canonical drift missing evidence and gate disagreement`() {
        val first = result(
            backend = ObservationBackend.MEDIAPIPE_POSE,
            landmarks = mapOf("anchor" to landmark("anchor", 0.10f, 0.10f, 0.95f))
        )
        val second = result(
            backend = ObservationBackend.ML_KIT_POSE,
            landmarks = mapOf("anchor" to landmark("anchor", 0.13f, 0.14f, 0.20f))
        )

        val report = PortraitBackendComparisonReportBuilder.build(
            backendResults = listOf(first, second),
            gateSpecs = listOf(gate)
        )

        val comparison = report.comparisons.single()
        assertEquals(0.05f, comparison.landmarkDeltas.single().normalizedDistance2d, 0.0001f)
        assertEquals(1, comparison.parameterDisagreements.size)
        assertTrue(comparison.missingInFirst.isEmpty())
        assertTrue(comparison.missingInSecond.isEmpty())
    }

    @Test
    fun `uses the earliest repeated run for backend comparison`() {
        val results = listOf(
            result(
                backend = ObservationBackend.MEDIAPIPE_POSE,
                runIndex = 2,
                landmarks = mapOf("anchor" to landmark("anchor", 0.90f, 0.90f, 0.95f))
            ),
            result(
                backend = ObservationBackend.MEDIAPIPE_POSE,
                runIndex = 0,
                landmarks = mapOf("anchor" to landmark("anchor", 0.10f, 0.10f, 0.95f))
            ),
            result(
                backend = ObservationBackend.ML_KIT_POSE,
                landmarks = mapOf("anchor" to landmark("anchor", 0.10f, 0.10f, 0.95f))
            )
        )

        val comparison = PortraitBackendComparisonReportBuilder.build(
            backendResults = results,
            gateSpecs = listOf(gate)
        ).comparisons.single()

        assertEquals(0f, comparison.maximumLandmarkDistance2d ?: -1f, 0.0001f)
    }

    private fun result(
        backend: ObservationBackend,
        landmarks: Map<String, LandmarkObservation>,
        runIndex: Int = 0
    ) = ObservationBenchmarkResult(
        observation = SubjectObservation(
            subjectCount = 1,
            faceCount = 0,
            bodyCount = 1,
            landmarks = landmarks,
            regions = emptyMap()
        ),
        measurement = BenchmarkMeasurement(
            backend = backend,
            processingTimeMillis = 10,
            repeatedRunIndex = runIndex
        )
    )

    private fun landmark(
        id: String,
        x: Float,
        y: Float,
        confidence: Float
    ) = LandmarkObservation(
        id = id,
        point = NormalizedPoint3D(x, y),
        confidence = confidence,
        confidenceSource = ConfidenceSource.DIRECT,
        visibility = VisibilityState.VISIBLE,
        backend = ObservationBackend.UNKNOWN
    )
}
