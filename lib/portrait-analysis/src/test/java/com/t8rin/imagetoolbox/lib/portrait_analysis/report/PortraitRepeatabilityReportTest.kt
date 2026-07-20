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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PortraitRepeatabilityReportTest {

    private val gate = ParameterGateSpec(
        parameterId = "test_parameter",
        requiredLandmarkIds = setOf("anchor"),
        minimumLandmarkConfidence = 0.90f
    )

    @Test
    fun `three stable runs pass repeatability`() {
        val runs = listOf(
            result(0, 0.5000f, 0.5000f, 0.95f),
            result(1, 0.5005f, 0.5000f, 0.95f),
            result(2, 0.4995f, 0.5000f, 0.95f)
        )

        val report = PortraitRepeatabilityReportBuilder.build(
            backendResults = runs,
            gateSpecs = listOf(gate),
            policy = RepeatabilityPolicy(
                minimumRuns = 3,
                maximumNormalizedLandmarkDrift = 0.002f
            )
        )

        assertTrue(report.allDeterministic)
        assertTrue(report.entries.single().deterministic)
    }

    @Test
    fun `gate disagreement and excessive drift fail repeatability`() {
        val runs = listOf(
            result(0, 0.50f, 0.50f, 0.95f),
            result(1, 0.54f, 0.50f, 0.20f),
            result(2, 0.50f, 0.50f, 0.95f)
        )

        val entry = PortraitRepeatabilityReportBuilder.build(
            backendResults = runs,
            gateSpecs = listOf(gate),
            policy = RepeatabilityPolicy(
                minimumRuns = 3,
                maximumNormalizedLandmarkDrift = 0.002f
            )
        ).entries.single()

        assertFalse(entry.deterministic)
        assertTrue("anchor" in entry.unstableLandmarkIds)
        assertTrue("test_parameter" in entry.unstableParameterIds)
    }

    @Test
    fun `two runs remain insufficient when policy requires three`() {
        val report = PortraitRepeatabilityReportBuilder.build(
            backendResults = listOf(
                result(0, 0.50f, 0.50f, 0.95f),
                result(1, 0.50f, 0.50f, 0.95f)
            ),
            gateSpecs = listOf(gate)
        )

        assertFalse(report.allDeterministic)
        assertFalse(report.entries.single().sufficientRuns)
    }

    private fun result(
        runIndex: Int,
        x: Float,
        y: Float,
        confidence: Float
    ) = ObservationBenchmarkResult(
        observation = SubjectObservation(
            subjectCount = 1,
            faceCount = 0,
            bodyCount = 1,
            landmarks = mapOf(
                "anchor" to LandmarkObservation(
                    id = "anchor",
                    point = NormalizedPoint3D(x, y),
                    confidence = confidence,
                    confidenceSource = ConfidenceSource.DIRECT,
                    visibility = VisibilityState.VISIBLE,
                    backend = ObservationBackend.MEDIAPIPE_POSE
                )
            ),
            regions = emptyMap()
        ),
        measurement = BenchmarkMeasurement(
            backend = ObservationBackend.MEDIAPIPE_POSE,
            processingTimeMillis = 10,
            repeatedRunIndex = runIndex
        )
    )
}
