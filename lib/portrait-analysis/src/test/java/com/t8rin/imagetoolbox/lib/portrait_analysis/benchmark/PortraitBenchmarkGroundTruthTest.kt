/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis.benchmark

import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ConfidenceSource
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.LandmarkObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.NormalizedPoint3D
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ObservationBackend
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.RegionObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.VisibilityState
import com.t8rin.imagetoolbox.lib.portrait_analysis.report.ParameterAvailability
import com.t8rin.imagetoolbox.lib.portrait_analysis.report.ParameterGateReport
import com.t8rin.imagetoolbox.lib.portrait_analysis.report.ParameterGateReportEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PortraitBenchmarkGroundTruthTest {

    @Test
    fun `passes when observation and parameter availability match ground truth`() {
        val observation = observation(
            subjectCount = 1,
            faceCount = 1,
            bodyCount = 0,
            landmarkIds = setOf("mouth_left_corner"),
            regionIds = setOf("mouth_region")
        )
        val report = report(
            "mouth_corner_curve" to ParameterAvailability.ENABLED,
            "waist_width" to ParameterAvailability.DISABLED
        )

        val evaluation = PortraitBenchmarkGroundTruthEvaluator.evaluate(
            groundTruth = PortraitBenchmarkGroundTruth(
                caseId = "FACE_01_NEUTRAL_FRONT",
                expectedSubjectCount = 1..1,
                expectedFaceCount = 1..1,
                expectedBodyCount = 0..0,
                requiredLandmarkIds = setOf("mouth_left_corner"),
                requiredRegionIds = setOf("mouth_region"),
                expectedEnabledParameterIds = setOf("mouth_corner_curve"),
                expectedDisabledParameterIds = setOf("waist_width")
            ),
            observation = observation,
            parameterReport = report
        )

        assertTrue(evaluation.passed)
        assertTrue(evaluation.mismatches.isEmpty())
    }

    @Test
    fun `reports count evidence and parameter mismatches deterministically`() {
        val evaluation = PortraitBenchmarkGroundTruthEvaluator.evaluate(
            groundTruth = PortraitBenchmarkGroundTruth(
                caseId = "FACE_07_PARTIAL_OCCLUSION",
                expectedSubjectCount = 1..1,
                expectedFaceCount = 1..1,
                expectedBodyCount = 0..0,
                requiredLandmarkIds = setOf("mouth_right_corner"),
                forbiddenRegionIds = setOf("visible_teeth_region"),
                expectedEnabledParameterIds = setOf("mouth_corner_curve"),
                expectedDisabledParameterIds = setOf("teeth_whiteness"),
                annotations = setOf(PortraitBenchmarkAnnotation.PARTIAL_FACE_OCCLUSION)
            ),
            observation = observation(
                subjectCount = 2,
                faceCount = 1,
                bodyCount = 1,
                landmarkIds = emptySet(),
                regionIds = setOf("visible_teeth_region")
            ),
            parameterReport = report(
                "mouth_corner_curve" to ParameterAvailability.DISABLED,
                "teeth_whiteness" to ParameterAvailability.ENABLED
            )
        )

        assertFalse(evaluation.passed)
        assertEquals(
            listOf(
                PortraitGroundTruthMismatchReason.BODY_COUNT_OUT_OF_RANGE,
                PortraitGroundTruthMismatchReason.FORBIDDEN_REGION_PRESENT,
                PortraitGroundTruthMismatchReason.PARAMETER_EXPECTED_DISABLED,
                PortraitGroundTruthMismatchReason.PARAMETER_EXPECTED_ENABLED,
                PortraitGroundTruthMismatchReason.REQUIRED_LANDMARK_MISSING,
                PortraitGroundTruthMismatchReason.SUBJECT_COUNT_OUT_OF_RANGE
            ),
            evaluation.mismatches.map { it.reason }
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects contradictory parameter expectations`() {
        PortraitBenchmarkGroundTruth(
            caseId = "invalid",
            expectedEnabledParameterIds = setOf("jawline_shape"),
            expectedDisabledParameterIds = setOf("jawline_shape")
        )
    }

    private fun observation(
        subjectCount: Int,
        faceCount: Int,
        bodyCount: Int,
        landmarkIds: Set<String>,
        regionIds: Set<String>
    ) = SubjectObservation(
        subjectCount = subjectCount,
        faceCount = faceCount,
        bodyCount = bodyCount,
        landmarks = landmarkIds.associateWith { id ->
            LandmarkObservation(
                id = id,
                point = NormalizedPoint3D(x = 0.5f, y = 0.5f),
                confidence = 1f,
                confidenceSource = ConfidenceSource.DIRECT,
                visibility = VisibilityState.VISIBLE,
                backend = ObservationBackend.UNKNOWN
            )
        },
        regions = regionIds.associateWith { id ->
            RegionObservation(
                id = id,
                confidence = 1f,
                confidenceSource = ConfidenceSource.DIRECT,
                occlusion = 0f,
                pixelCoverage = 0.1f,
                backend = ObservationBackend.UNKNOWN
            )
        }
    )

    private fun report(
        vararg entries: Pair<String, ParameterAvailability>
    ) = ParameterGateReport(
        entries = entries.map { (id, availability) ->
            ParameterGateReportEntry(
                parameterId = id,
                availability = availability,
                failures = emptyList()
            )
        }
    )
}
