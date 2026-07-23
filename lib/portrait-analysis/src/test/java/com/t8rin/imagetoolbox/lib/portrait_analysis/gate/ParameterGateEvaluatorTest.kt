/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis.gate

import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ConfidenceSource
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.LandmarkObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.NormalizedPoint3D
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ObservationBackend
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.PoseObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.RegionObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.VisibilityState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParameterGateEvaluatorTest {

    private val mouthCornerSpec = ParameterGateSpec(
        parameterId = "mouth_corner_curve",
        requiredLandmarkIds = setOf(
            "mouth_left_corner",
            "mouth_right_corner",
            "upper_lip_mid",
            "lower_lip_mid",
            "mouth_center"
        ),
        requiredRegionIds = setOf("mouth_region", "lips_occlusion_mask"),
        minimumLandmarkConfidence = 0.93f,
        minimumRegionConfidence = 0.90f,
        maximumRegionOcclusion = 0.15f,
        minimumRegionPixelCoverage = 0.002f,
        maximumAbsoluteYawDegrees = 30f
    )

    @Test
    fun `enables parameter when every requirement passes`() {
        val decision = ParameterGateEvaluator.evaluate(
            spec = mouthCornerSpec,
            observation = validObservation()
        )

        assertTrue(decision is ParameterGateDecision.Enabled)
    }

    @Test
    fun `missing landmark disables parameter instead of guessing`() {
        val observation = validObservation().let {
            it.copy(landmarks = it.landmarks - "mouth_right_corner")
        }

        val decision = ParameterGateEvaluator.evaluate(mouthCornerSpec, observation)

        assertTrue(decision is ParameterGateDecision.Disabled)
        val failures = (decision as ParameterGateDecision.Disabled).failures
        assertTrue(
            failures.any {
                it.reason == GateFailureReason.LANDMARK_MISSING &&
                    it.itemId == "mouth_right_corner"
            }
        )
    }

    @Test
    fun `unavailable landmark confidence disables strict parameter`() {
        val observation = validObservation().let {
            it.copy(
                landmarks = it.landmarks + (
                    "mouth_right_corner" to it.landmarks.getValue("mouth_right_corner").copy(
                        confidence = null,
                        confidenceSource = ConfidenceSource.UNAVAILABLE
                    )
                )
            )
        }

        val decision = ParameterGateEvaluator.evaluate(mouthCornerSpec, observation)

        assertTrue(decision is ParameterGateDecision.Disabled)
        val failures = (decision as ParameterGateDecision.Disabled).failures
        assertTrue(
            failures.any {
                it.reason == GateFailureReason.LANDMARK_CONFIDENCE_UNAVAILABLE &&
                    it.itemId == "mouth_right_corner"
            }
        )
    }

    @Test
    fun `occluded region disables only the affected parameter`() {
        val observation = validObservation().let {
            it.copy(
                regions = it.regions + (
                    "mouth_region" to it.regions.getValue("mouth_region").copy(
                        occlusion = 0.40f
                    )
                )
            )
        }

        val decision = ParameterGateEvaluator.evaluate(mouthCornerSpec, observation)

        assertTrue(decision is ParameterGateDecision.Disabled)
        val failures = (decision as ParameterGateDecision.Disabled).failures
        assertTrue(failures.any { it.reason == GateFailureReason.REGION_OCCLUDED })
    }

    @Test
    fun `unavailable region occlusion disables strict parameter`() {
        val observation = validObservation().let {
            it.copy(
                regions = it.regions + (
                    "lips_occlusion_mask" to it.regions.getValue("lips_occlusion_mask").copy(
                        occlusion = null
                    )
                )
            )
        }

        val decision = ParameterGateEvaluator.evaluate(mouthCornerSpec, observation)

        assertTrue(decision is ParameterGateDecision.Disabled)
        val failures = (decision as ParameterGateDecision.Disabled).failures
        assertTrue(
            failures.any {
                it.reason == GateFailureReason.REGION_OCCLUSION_UNAVAILABLE &&
                    it.itemId == "lips_occlusion_mask"
            }
        )
    }

    @Test
    fun `unsupported pose disables parameter`() {
        val observation = validObservation().copy(
            pose = PoseObservation(yawDegrees = 42f)
        )

        val decision = ParameterGateEvaluator.evaluate(mouthCornerSpec, observation)

        assertTrue(decision is ParameterGateDecision.Disabled)
        val failures = (decision as ParameterGateDecision.Disabled).failures
        assertEquals(GateFailureReason.POSE_UNSUPPORTED, failures.last().reason)
    }

    private fun validObservation(): SubjectObservation {
        val landmarkIds = mouthCornerSpec.requiredLandmarkIds
        val landmarks = landmarkIds.associateWith { id ->
            LandmarkObservation(
                id = id,
                point = NormalizedPoint3D(x = 0.5f, y = 0.5f),
                confidence = 0.98f,
                confidenceSource = ConfidenceSource.DIRECT,
                visibility = VisibilityState.VISIBLE,
                backend = ObservationBackend.ML_KIT_FACE_MESH
            )
        }
        val regions = mapOf(
            "mouth_region" to RegionObservation(
                id = "mouth_region",
                confidence = 0.98f,
                confidenceSource = ConfidenceSource.DIRECT,
                occlusion = 0.02f,
                pixelCoverage = 0.02f,
                backend = ObservationBackend.ML_KIT_FACE_MESH
            ),
            "lips_occlusion_mask" to RegionObservation(
                id = "lips_occlusion_mask",
                confidence = 0.96f,
                confidenceSource = ConfidenceSource.DIRECT,
                occlusion = 0.02f,
                pixelCoverage = 0.02f,
                backend = ObservationBackend.ML_KIT_FACE_MESH
            )
        )
        return SubjectObservation(
            subjectCount = 1,
            faceCount = 1,
            bodyCount = 1,
            landmarks = landmarks,
            regions = regions,
            pose = PoseObservation(yawDegrees = 5f, pitchDegrees = 0f, rollDegrees = 0f)
        )
    }
}
