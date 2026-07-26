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

/** Regression coverage for the 2026-07-26 frontal device false rejection. */
class FaceTopologyDeviceRegressionTest {

    @Test
    fun `frontal contour midpoint within shared two percent bbox tolerance is supported`() {
        val observation = frontalObservation(
            firstX = 0.190f,
            secondX = 0.198f
        )

        val result = FaceTopologyVisibilityValidator.evaluate(
            rawObservation = observation,
            candidateObservation = observation,
            awareness = frontalAwareness()
        )

        assertTrue(result.accepted)
        assertFalse(
            FaceGeometryRejectionReason.CONTOUR_CROSSES_UNSUPPORTED_SPACE in result.reasons
        )
    }

    @Test
    fun `frontal contour midpoint outside shared two percent bbox tolerance is rejected`() {
        val observation = frontalObservation(
            firstX = 0.170f,
            secondX = 0.180f
        )

        val result = FaceTopologyVisibilityValidator.evaluate(
            rawObservation = observation,
            candidateObservation = observation,
            awareness = frontalAwareness()
        )

        assertFalse(result.accepted)
        assertTrue(
            FaceGeometryRejectionReason.CONTOUR_CROSSES_UNSUPPORTED_SPACE in result.reasons
        )
    }

    private fun frontalObservation(
        firstX: Float,
        secondX: Float
    ): SubjectObservation {
        val landmarks = listOf(
            point("bbox_tl", 0.20f, 0.10f),
            point("bbox_tr", 0.80f, 0.10f),
            point("bbox_br", 0.80f, 0.90f),
            point("bbox_bl", 0.20f, 0.90f),
            point("contour_a", firstX, 0.40f),
            point("contour_b", secondX, 0.50f),
            point("left_eye", 0.35f, 0.35f),
            point("right_eye", 0.65f, 0.35f),
            point("nose", 0.50f, 0.52f)
        ).associateBy { it.id }
        val pose = PoseObservation(yawDegrees = 0f, pitchDegrees = 0f, rollDegrees = 0f)
        return SubjectObservation(
            subjectCount = 1,
            faceCount = 1,
            bodyCount = 0,
            landmarks = landmarks,
            regions = emptyMap(),
            pose = pose,
            facePoses = mapOf(0 to pose),
            contours = mapOf(
                "bounding_box" to contour(
                    "bounding_box",
                    listOf("bbox_tl", "bbox_tr", "bbox_br", "bbox_bl"),
                    closed = true
                ),
                "left_cheek_contour" to contour(
                    "left_cheek_contour",
                    listOf("contour_a", "contour_b"),
                    closed = false
                )
            )
        )
    }

    private fun frontalAwareness() = FaceRegionAwareness(
        poseMode = FacePoseMode.FRONTAL,
        dominantImageSide = FaceImageSide.BALANCED,
        yawDegrees = 0f,
        pitchDegrees = 0f,
        rollDegrees = 0f,
        fullFaceGeometryAllowed = true,
        regions = emptyMap(),
        reasons = emptyList()
    )

    private fun point(id: String, x: Float, y: Float) = LandmarkObservation(
        id = id,
        point = NormalizedPoint3D(x, y),
        confidence = null,
        confidenceSource = ConfidenceSource.UNAVAILABLE,
        visibility = VisibilityState.VISIBLE,
        backend = ObservationBackend.ML_KIT_FACE_DETECTION
    )

    private fun contour(
        id: String,
        vertexIds: List<String>,
        closed: Boolean
    ) = ContourObservation(
        id = id,
        vertexIds = vertexIds,
        closed = closed,
        confidence = null,
        confidenceSource = ConfidenceSource.UNAVAILABLE,
        backend = ObservationBackend.ML_KIT_FACE_DETECTION
    )
}
