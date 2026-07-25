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
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.PoseObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.VisibilityState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MlKitFaceQualityGateTest {

    @Test
    fun `frontal face with bilateral eyes and nose passes ML Kit quality checks`() {
        val observation = frontalObservation()

        val result = MlKitFaceQualityGate.evaluate(
            rawObservation = observation,
            candidateObservation = observation,
            awareness = awareness(FacePoseMode.FRONTAL, FaceImageSide.BALANCED)
        )

        assertTrue(result.reasons.isEmpty())
    }

    @Test
    fun `small face candidate fails closed`() {
        val source = frontalObservation(
            bounds = listOf(
                point("bbox_tl", 0.45f, 0.45f),
                point("bbox_tr", 0.52f, 0.45f),
                point("bbox_br", 0.52f, 0.52f),
                point("bbox_bl", 0.45f, 0.52f)
            ),
            features = listOf(
                point("left_eye_center", 0.47f, 0.47f),
                point("right_eye_center", 0.50f, 0.47f),
                point("nose_base", 0.485f, 0.49f),
                point("mouth_center", 0.485f, 0.505f)
            )
        )

        val result = MlKitFaceQualityGate.evaluate(
            rawObservation = source,
            candidateObservation = source,
            awareness = awareness(FacePoseMode.FRONTAL, FaceImageSide.BALANCED)
        )

        assertTrue(FaceGeometryRejectionReason.FACE_TOO_SMALL_OR_BOUNDS_UNAVAILABLE in result.reasons)
    }

    @Test
    fun `non finite landmark is rejected`() {
        val source = frontalObservation().copy(
            landmarks = frontalObservation().landmarks +
                ("nose_base" to point("nose_base", Float.NaN, 0.50f))
        )

        val result = MlKitFaceQualityGate.evaluate(
            rawObservation = source,
            candidateObservation = source,
            awareness = awareness(FacePoseMode.FRONTAL, FaceImageSide.BALANCED)
        )

        assertTrue(FaceGeometryRejectionReason.NON_FINITE_GEOMETRY in result.reasons)
    }

    @Test
    fun `frontal face missing one eye is rejected`() {
        val source = frontalObservation().copy(
            landmarks = frontalObservation().landmarks - "right_eye_center"
        )

        val result = MlKitFaceQualityGate.evaluate(
            rawObservation = source,
            candidateObservation = source,
            awareness = awareness(FacePoseMode.FRONTAL, FaceImageSide.BALANCED)
        )

        assertTrue(FaceGeometryRejectionReason.MISSING_YAW_DEPENDENT_LANDMARKS in result.reasons)
    }

    @Test
    fun `left half profile rejects hidden right eye evidence`() {
        val source = frontalObservation()

        val result = MlKitFaceQualityGate.evaluate(
            rawObservation = source,
            candidateObservation = source,
            awareness = awareness(FacePoseMode.HALF_PROFILE, FaceImageSide.LEFT)
        )

        assertTrue(FaceGeometryRejectionReason.VISIBLE_SIDE_LANDMARK_CONTRADICTION in result.reasons)
    }

    @Test
    fun `left half profile with visible eye and nose is not rejected for yaw evidence`() {
        val raw = frontalObservation()
        val candidate = raw.copy(
            landmarks = raw.landmarks - "right_eye_center"
        )

        val result = MlKitFaceQualityGate.evaluate(
            rawObservation = raw,
            candidateObservation = candidate,
            awareness = awareness(FacePoseMode.HALF_PROFILE, FaceImageSide.LEFT)
        )

        assertFalse(FaceGeometryRejectionReason.MISSING_YAW_DEPENDENT_LANDMARKS in result.reasons)
        assertFalse(FaceGeometryRejectionReason.VISIBLE_SIDE_LANDMARK_CONTRADICTION in result.reasons)
    }

    @Test
    fun `collapsed candidate points are rejected`() {
        val raw = frontalObservation()
        val collapsed = raw.copy(
            landmarks = raw.landmarks.mapValues { (id, value) ->
                if (id.startsWith("bbox_")) value else value.copy(point = NormalizedPoint3D(0.5f, 0.5f))
            }
        )

        val result = MlKitFaceQualityGate.evaluate(
            rawObservation = raw,
            candidateObservation = collapsed,
            awareness = awareness(FacePoseMode.FRONTAL, FaceImageSide.BALANCED)
        )

        assertTrue(FaceGeometryRejectionReason.INSUFFICIENT_LANDMARK_SPREAD in result.reasons)
        assertTrue(FaceGeometryRejectionReason.COLLAPSED_GEOMETRY in result.reasons)
    }

    private fun awareness(
        poseMode: FacePoseMode,
        side: FaceImageSide
    ) = FaceRegionAwareness(
        poseMode = poseMode,
        dominantImageSide = side,
        yawDegrees = when (side) {
            FaceImageSide.LEFT -> 32f
            FaceImageSide.RIGHT -> -32f
            else -> 0f
        },
        pitchDegrees = 0f,
        rollDegrees = 0f,
        fullFaceGeometryAllowed = poseMode == FacePoseMode.FRONTAL,
        regions = emptyMap(),
        reasons = emptyList()
    )

    private fun frontalObservation(
        bounds: List<LandmarkObservation> = listOf(
            point("bbox_tl", 0.20f, 0.10f),
            point("bbox_tr", 0.80f, 0.10f),
            point("bbox_br", 0.80f, 0.90f),
            point("bbox_bl", 0.20f, 0.90f)
        ),
        features: List<LandmarkObservation> = listOf(
            point("left_eye_center", 0.35f, 0.35f),
            point("right_eye_center", 0.65f, 0.35f),
            point("nose_base", 0.50f, 0.52f),
            point("mouth_center", 0.50f, 0.70f),
            point("chin_center", 0.50f, 0.84f)
        )
    ): SubjectObservation {
        val landmarks = (bounds + features).associateBy { it.id }
        return SubjectObservation(
            subjectCount = 1,
            faceCount = 1,
            bodyCount = 0,
            landmarks = landmarks,
            regions = emptyMap(),
            pose = PoseObservation(yawDegrees = 0f, pitchDegrees = 0f, rollDegrees = 0f),
            facePoses = mapOf(0 to PoseObservation(yawDegrees = 0f, pitchDegrees = 0f, rollDegrees = 0f)),
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