/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis.derive

import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ConfidenceSource
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ContourObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.LandmarkObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.NormalizedPoint3D
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ObservationBackend
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.VisibilityState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceOcclusionPartialPolicyTest {

    @Test
    fun `mild half profile with central and visible side anchors passes without guessing occlusion`() {
        val result = FaceOcclusionPartialPolicy.evaluate(
            candidateObservation = observation(),
            awareness = awareness()
        )

        assertTrue(result.accepted)
        assertTrue(result.reasons.isEmpty())
    }

    @Test
    fun `missing required anchors with no semantic mask fails closed`() {
        val candidate = observation().copy(
            landmarks = observation().landmarks - "mouth_left" - "chin_center"
        )
        val result = FaceOcclusionPartialPolicy.evaluate(candidate, awareness())

        assertFalse(result.accepted)
        assertTrue(
            FaceGeometryRejectionReason.INSUFFICIENT_VISIBLE_FACE_ANCHORS in result.reasons
        )
        assertTrue(
            FaceGeometryRejectionReason.OCCLUSION_EVIDENCE_UNAVAILABLE in result.reasons
        )
    }

    @Test
    fun `explicit hair hand phone and shadow occlusion are independently rejected`() {
        val mapping = mapOf(
            FaceOcclusionPartialPolicy.OcclusionKind.HAIR to FaceGeometryRejectionReason.FACE_OCCLUDED_BY_HAIR,
            FaceOcclusionPartialPolicy.OcclusionKind.HAND to FaceGeometryRejectionReason.FACE_OCCLUDED_BY_HAND,
            FaceOcclusionPartialPolicy.OcclusionKind.PHONE to FaceGeometryRejectionReason.FACE_OCCLUDED_BY_PHONE,
            FaceOcclusionPartialPolicy.OcclusionKind.STRONG_SHADOW to
                FaceGeometryRejectionReason.FACE_OCCLUDED_BY_STRONG_SHADOW
        )

        mapping.forEach { (kind, expectedReason) ->
            val result = FaceOcclusionPartialPolicy.evaluate(
                candidateObservation = observation(),
                awareness = awareness(),
                evidence = FaceOcclusionPartialPolicy.OcclusionEvidence(
                    states = mapOf(kind to FaceOcclusionPartialPolicy.EvidenceState.OCCLUDED),
                    sourceId = "test-mask"
                )
            )
            assertFalse(result.accepted)
            assertTrue(expectedReason in result.reasons)
        }
    }

    @Test
    fun `face bounding box touching image edge is rejected as frame crop`() {
        val result = FaceOcclusionPartialPolicy.evaluate(
            candidateObservation = observation(
                bounds = listOf(
                    point("bbox_tl", 0f, 0.15f),
                    point("bbox_tr", 0.70f, 0.15f),
                    point("bbox_br", 0.70f, 0.90f),
                    point("bbox_bl", 0f, 0.90f)
                )
            ),
            awareness = awareness()
        )

        assertFalse(result.accepted)
        assertTrue(FaceGeometryRejectionReason.FACE_CROPPED_BY_FRAME in result.reasons)
    }

    @Test
    fun `clear semantic evidence does not replace missing landmark evidence`() {
        val candidate = observation().copy(
            landmarks = observation().landmarks - "left_eye_center" - "mouth_left"
        )
        val result = FaceOcclusionPartialPolicy.evaluate(
            candidateObservation = candidate,
            awareness = awareness(),
            evidence = FaceOcclusionPartialPolicy.OcclusionEvidence(
                states = FaceOcclusionPartialPolicy.OcclusionKind.entries.associateWith {
                    FaceOcclusionPartialPolicy.EvidenceState.CLEAR
                },
                sourceId = "explicit-occlusion-mask"
            )
        )

        assertFalse(result.accepted)
        assertTrue(
            FaceGeometryRejectionReason.INSUFFICIENT_VISIBLE_FACE_ANCHORS in result.reasons
        )
        assertFalse(
            FaceGeometryRejectionReason.OCCLUSION_EVIDENCE_UNAVAILABLE in result.reasons
        )
    }

    private fun awareness() = FaceRegionAwareness(
        poseMode = FacePoseMode.HALF_PROFILE,
        dominantImageSide = FaceImageSide.LEFT,
        yawDegrees = 28f,
        pitchDegrees = 0f,
        rollDegrees = 0f,
        fullFaceGeometryAllowed = false,
        regions = emptyMap(),
        reasons = emptyList()
    )

    private fun observation(
        bounds: List<LandmarkObservation> = listOf(
            point("bbox_tl", 0.20f, 0.10f),
            point("bbox_tr", 0.80f, 0.10f),
            point("bbox_br", 0.80f, 0.90f),
            point("bbox_bl", 0.20f, 0.90f)
        )
    ): SubjectObservation {
        val anchors = listOf(
            point("left_eye_center", 0.36f, 0.35f),
            point("nose_base", 0.50f, 0.52f),
            point("mouth_left", 0.43f, 0.69f),
            point("chin_center", 0.50f, 0.84f)
        )
        val landmarks = (bounds + anchors).associateBy { it.id }
        return SubjectObservation(
            subjectCount = 1,
            faceCount = 1,
            bodyCount = 0,
            landmarks = landmarks,
            contours = mapOf(
                "bounding_box" to ContourObservation(
                    id = "bounding_box",
                    vertexIds = bounds.map { it.id },
                    closed = true,
                    confidence = null,
                    confidenceSource = ConfidenceSource.UNAVAILABLE,
                    backend = ObservationBackend.ML_KIT_FACE_DETECTION
                )
            )
        )
    }

    private fun point(id: String, x: Float, y: Float) = LandmarkObservation(
        id = id,
        point = NormalizedPoint3D(x, y),
        confidence = null,
        confidenceSource = ConfidenceSource.UNAVAILABLE,
        visibility = VisibilityState.VISIBLE,
        backend = ObservationBackend.ML_KIT_FACE_DETECTION
    )
}
