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
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.MeshObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.MeshTriangleObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.NormalizedPoint3D
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ObservationBackend
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.PoseObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.VisibilityState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceTopologyVisibilityValidatorTest {

    @Test
    fun `closed full face oval is rejected for partial pose`() {
        val candidate = observation(
            contours = baseContours() + contour(
                "face_oval",
                listOf("left_eye", "nose", "right_eye", "chin"),
                closed = true
            )
        )

        val result = FaceTopologyVisibilityValidator.evaluate(
            rawObservation = candidate,
            candidateObservation = candidate,
            awareness = awareness(FacePoseMode.HALF_PROFILE, FaceImageSide.LEFT)
        )

        assertFalse(result.accepted)
        assertTrue(
            FaceGeometryRejectionReason.CLOSED_FULL_OVAL_IN_PARTIAL_POSE in result.reasons
        )
    }

    @Test
    fun `hidden eye and brow are rejected for both visible sides`() {
        val leftVisible = observation(
            extraPoints = listOf(point("right_eyebrow_outer", 0.67f, 0.29f)),
            contours = baseContours() + contour(
                "right_eye_contour",
                listOf("right_eye", "right_eyebrow_outer"),
                closed = false
            )
        )
        val rightVisible = observation(
            extraPoints = listOf(point("left_eyebrow_outer", 0.33f, 0.29f)),
            contours = baseContours() + contour(
                "left_eye_contour",
                listOf("left_eye", "left_eyebrow_outer"),
                closed = false
            )
        )

        val leftResult = FaceTopologyVisibilityValidator.evaluate(
            leftVisible,
            leftVisible,
            awareness(FacePoseMode.HALF_PROFILE, FaceImageSide.LEFT)
        )
        val rightResult = FaceTopologyVisibilityValidator.evaluate(
            rightVisible,
            rightVisible,
            awareness(FacePoseMode.HALF_PROFILE, FaceImageSide.RIGHT)
        )

        assertTrue(
            FaceGeometryRejectionReason.HIDDEN_SIDE_EYE_OR_BROW_PRESENT in leftResult.reasons
        )
        assertTrue(
            FaceGeometryRejectionReason.HIDDEN_SIDE_EYE_OR_BROW_PRESENT in rightResult.reasons
        )
    }

    @Test
    fun `self intersecting contour is rejected`() {
        val candidate = observation(
            extraPoints = listOf(
                point("bow_a", 0.30f, 0.30f),
                point("bow_b", 0.70f, 0.70f),
                point("bow_c", 0.70f, 0.30f),
                point("bow_d", 0.30f, 0.70f)
            ),
            contours = baseContours() + contour(
                "bow_tie",
                listOf("bow_a", "bow_b", "bow_c", "bow_d"),
                closed = true
            )
        )

        val result = FaceTopologyVisibilityValidator.evaluate(
            candidate,
            candidate,
            awareness(FacePoseMode.FRONTAL, FaceImageSide.BALANCED)
        )

        assertFalse(result.accepted)
        assertTrue(FaceGeometryRejectionReason.CONTOUR_SELF_INTERSECTION in result.reasons)
        assertTrue(result.metrics.selfIntersectionCount > 0)
    }

    @Test
    fun `contour jump through unsupported hidden space is rejected`() {
        val candidate = observation(
            extraPoints = listOf(
                point("visible_a", 0.24f, 0.25f),
                point("hidden_jump", 0.78f, 0.75f)
            ),
            contours = baseContours() + contour(
                "profile_jump",
                listOf("visible_a", "hidden_jump"),
                closed = false
            )
        )

        val result = FaceTopologyVisibilityValidator.evaluate(
            candidate,
            candidate,
            awareness(FacePoseMode.HALF_PROFILE, FaceImageSide.LEFT)
        )

        assertFalse(result.accepted)
        assertTrue(
            FaceGeometryRejectionReason.CONTOUR_CROSSES_UNSUPPORTED_SPACE in result.reasons
        )
    }

    @Test
    fun `mesh triangle depending on rejected vertex is rejected`() {
        val candidate = observation().copy(
            meshes = mapOf(
                "face_mesh" to MeshObservation(
                    id = "face_mesh",
                    vertexIds = setOf("left_eye", "nose", "rejected_vertex"),
                    triangles = listOf(
                        MeshTriangleObservation("left_eye", "nose", "rejected_vertex")
                    ),
                    backend = ObservationBackend.ML_KIT_FACE_DETECTION
                )
            )
        )

        val result = FaceTopologyVisibilityValidator.evaluate(
            candidate,
            candidate,
            awareness(FacePoseMode.FRONTAL, FaceImageSide.BALANCED)
        )

        assertFalse(result.accepted)
        assertTrue(FaceGeometryRejectionReason.BROKEN_MESH_REFERENCE in result.reasons)
        assertTrue(
            FaceGeometryRejectionReason.MESH_TRIANGLE_REFERENCES_REJECTED_VERTEX in result.reasons
        )
    }

    @Test
    fun `valid visible open contour passes topology validation`() {
        val candidate = observation(
            contours = baseContours() + contour(
                "left_visible_cheek",
                listOf("left_eye", "nose", "chin"),
                closed = false
            )
        )

        val result = FaceTopologyVisibilityValidator.evaluate(
            candidate,
            candidate,
            awareness(FacePoseMode.HALF_PROFILE, FaceImageSide.LEFT)
        )

        assertTrue(result.accepted)
        assertTrue(result.reasons.isEmpty())
    }

    private fun awareness(
        poseMode: FacePoseMode,
        side: FaceImageSide
    ) = FaceRegionAwareness(
        poseMode = poseMode,
        dominantImageSide = side,
        yawDegrees = when (side) {
            FaceImageSide.LEFT -> 30f
            FaceImageSide.RIGHT -> -30f
            else -> 0f
        },
        pitchDegrees = 0f,
        rollDegrees = 0f,
        fullFaceGeometryAllowed = poseMode == FacePoseMode.FRONTAL,
        regions = emptyMap(),
        reasons = emptyList()
    )

    private fun observation(
        extraPoints: List<LandmarkObservation> = emptyList(),
        contours: Map<String, ContourObservation> = baseContours()
    ): SubjectObservation {
        val points = (
            listOf(
                point("bbox_tl", 0.20f, 0.10f),
                point("bbox_tr", 0.80f, 0.10f),
                point("bbox_br", 0.80f, 0.90f),
                point("bbox_bl", 0.20f, 0.90f),
                point("left_eye", 0.35f, 0.35f),
                point("right_eye", 0.65f, 0.35f),
                point("nose", 0.50f, 0.52f),
                point("chin", 0.50f, 0.82f)
            ) + extraPoints
            ).associateBy { it.id }
        val pose = PoseObservation(yawDegrees = 30f, pitchDegrees = 0f, rollDegrees = 0f)
        return SubjectObservation(
            subjectCount = 1,
            faceCount = 1,
            bodyCount = 0,
            landmarks = points,
            pose = pose,
            facePoses = mapOf(0 to pose),
            contours = contours
        )
    }

    private fun baseContours(): Map<String, ContourObservation> = mapOf(
        "bounding_box" to contour(
            "bounding_box",
            listOf("bbox_tl", "bbox_tr", "bbox_br", "bbox_bl"),
            closed = true
        )
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
        vertices: List<String>,
        closed: Boolean
    ) = ContourObservation(
        id = id,
        vertexIds = vertices,
        closed = closed,
        confidence = null,
        confidenceSource = ConfidenceSource.UNAVAILABLE,
        backend = ObservationBackend.ML_KIT_FACE_DETECTION
    )
}
