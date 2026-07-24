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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceVisibleGeometryFilterTest {

    @Test
    fun `profile keeps visible side and opens the full face contour`() {
        val result = FaceVisibleGeometryFilter.filter(
            observation = profileObservation(),
            awareness = awareness(FacePoseMode.PROFILE, FaceImageSide.LEFT)
        )

        assertTrue(result.applied)
        assertTrue(result.removedLandmarkCount > 0)
        assertFalse("right_eye_center" in result.observation.landmarks)
        assertTrue("left_eye_center" in result.observation.landmarks)
        assertFalse("right_eye_contour" in result.observation.contours)
        assertTrue("left_eye_contour" in result.observation.contours)
        assertFalse("face_contour" in result.observation.contours)

        val visibleFaceSegments = result.observation.contours.values.filter {
            it.id.startsWith("face_contour_visible_segment_")
        }
        assertTrue(visibleFaceSegments.isNotEmpty())
        assertTrue(visibleFaceSegments.all { !it.closed })
        assertTrue(
            visibleFaceSegments.flatMap { it.vertexIds }.all { id ->
                result.observation.landmarks.getValue(id).point.x <= 0.57f
            }
        )
    }

    @Test
    fun `right profile mirrors hidden-side filtering`() {
        val result = FaceVisibleGeometryFilter.filter(
            observation = profileObservation(),
            awareness = awareness(FacePoseMode.PROFILE, FaceImageSide.RIGHT)
        )

        assertTrue(result.applied)
        assertFalse("left_eye_center" in result.observation.landmarks)
        assertTrue("right_eye_center" in result.observation.landmarks)
        assertFalse("left_eye_contour" in result.observation.contours)
        assertTrue("right_eye_contour" in result.observation.contours)
        assertFalse("face_contour" in result.observation.contours)
        assertTrue(
            result.observation.contours.values
                .filter { it.id.startsWith("face_contour_visible_segment_") }
                .flatMap { it.vertexIds }
                .all { id -> result.observation.landmarks.getValue(id).point.x >= 0.43f }
        )
    }

    @Test
    fun `half profile uses wider center corridor and preserves central geometry`() {
        val result = FaceVisibleGeometryFilter.filter(
            observation = profileObservation(),
            awareness = awareness(FacePoseMode.HALF_PROFILE, FaceImageSide.LEFT)
        )

        assertTrue(result.applied)
        assertTrue("nose_base" in result.observation.landmarks)
        assertTrue("mouth_left" in result.observation.landmarks)
        assertTrue("mouth_right" in result.observation.landmarks)
        assertTrue("chin_center" in result.observation.landmarks)
        assertFalse("right_eye_center" in result.observation.landmarks)
        assertFalse("face_contour" in result.observation.contours)
    }

    @Test
    fun `mesh drops hidden triangles and retains visible triangles`() {
        val source = profileObservation().copy(
            meshes = mapOf(
                "face_mesh" to MeshObservation(
                    id = "face_mesh",
                    vertexIds = setOf(
                        "left_eye_a",
                        "left_eye_b",
                        "nose_base",
                        "right_eye_a",
                        "right_eye_b"
                    ),
                    triangles = listOf(
                        MeshTriangleObservation("left_eye_a", "left_eye_b", "nose_base"),
                        MeshTriangleObservation("right_eye_a", "right_eye_b", "nose_base")
                    ),
                    backend = ObservationBackend.ML_KIT_FACE_DETECTION
                )
            )
        )

        val result = FaceVisibleGeometryFilter.filter(
            observation = source,
            awareness = awareness(FacePoseMode.PROFILE, FaceImageSide.LEFT)
        )

        val mesh = result.observation.meshes.getValue("face_mesh")
        assertEquals(2, result.rawTriangleCount)
        assertEquals(1, result.visibleTriangleCount)
        assertEquals(1, mesh.triangles.size)
        assertTrue(mesh.triangles.single().vertexIds.contains("left_eye_a"))
        assertFalse(mesh.vertexIds.contains("right_eye_a"))
    }

    @Test
    fun `closed contour can split into multiple open visible runs`() {
        val source = profileObservation().copy(
            contours = profileObservation().contours + (
                "alternating_contour" to contour(
                    "alternating_contour",
                    listOf("left_eye_a", "right_eye_a", "left_eye_b", "right_eye_b", "nose_base"),
                    true
                )
            )
        )

        val result = FaceVisibleGeometryFilter.filter(
            observation = source,
            awareness = awareness(FacePoseMode.PROFILE, FaceImageSide.LEFT)
        )

        val segments = result.observation.contours.values.filter {
            it.id.startsWith("alternating_contour_visible_segment_")
        }
        assertTrue(segments.size >= 2)
        assertTrue(segments.all { !it.closed })
        assertFalse("alternating_contour" in result.observation.contours)
    }

    @Test
    fun `missing nose landmarks falls back to face bounds center`() {
        val source = profileObservation().copy(
            landmarks = profileObservation().landmarks - "nose_base",
            contours = profileObservation().contours - "mouth_contour"
        )

        val result = FaceVisibleGeometryFilter.filter(
            observation = source,
            awareness = awareness(FacePoseMode.PROFILE, FaceImageSide.LEFT)
        )

        assertTrue(result.applied)
        assertFalse("right_eye_center" in result.observation.landmarks)
        assertTrue("left_eye_center" in result.observation.landmarks)
    }

    @Test
    fun `frontal geometry is not filtered`() {
        val observation = profileObservation()
        val result = FaceVisibleGeometryFilter.filter(
            observation = observation,
            awareness = awareness(FacePoseMode.FRONTAL, FaceImageSide.BALANCED)
        )

        assertFalse(result.applied)
        assertEquals(observation, result.observation)
    }

    private fun awareness(
        poseMode: FacePoseMode,
        side: FaceImageSide
    ) = FaceRegionAwareness(
        poseMode = poseMode,
        dominantImageSide = side,
        yawDegrees = when (side) {
            FaceImageSide.LEFT -> 58f
            FaceImageSide.RIGHT -> -58f
            else -> 0f
        },
        pitchDegrees = 0f,
        rollDegrees = 0f,
        fullFaceGeometryAllowed = poseMode == FacePoseMode.FRONTAL,
        regions = emptyMap(),
        reasons = emptyList()
    )

    private fun profileObservation(): SubjectObservation {
        val points = linkedMapOf(
            "bbox_tl" to point("bbox_tl", 0.20f, 0.10f),
            "bbox_tr" to point("bbox_tr", 0.80f, 0.10f),
            "bbox_br" to point("bbox_br", 0.80f, 0.90f),
            "bbox_bl" to point("bbox_bl", 0.20f, 0.90f),
            "face_0" to point("face_0", 0.50f, 0.12f),
            "face_1" to point("face_1", 0.68f, 0.16f),
            "face_2" to point("face_2", 0.78f, 0.35f),
            "face_3" to point("face_3", 0.76f, 0.70f),
            "face_4" to point("face_4", 0.50f, 0.88f),
            "face_5" to point("face_5", 0.28f, 0.70f),
            "face_6" to point("face_6", 0.22f, 0.35f),
            "left_eye_a" to point("left_eye_a", 0.31f, 0.34f),
            "left_eye_b" to point("left_eye_b", 0.39f, 0.34f),
            "right_eye_a" to point("right_eye_a", 0.64f, 0.34f),
            "right_eye_b" to point("right_eye_b", 0.72f, 0.34f),
            "left_eye_center" to point("left_eye_center", 0.35f, 0.34f),
            "right_eye_center" to point("right_eye_center", 0.68f, 0.34f),
            "nose_base" to point("nose_base", 0.50f, 0.52f),
            "mouth_left" to point("mouth_left", 0.43f, 0.67f),
            "mouth_right" to point("mouth_right", 0.56f, 0.67f),
            "chin_center" to point("chin_center", 0.50f, 0.86f)
        )
        val contours = listOf(
            contour("bounding_box", listOf("bbox_tl", "bbox_tr", "bbox_br", "bbox_bl"), true),
            contour(
                "face_contour",
                listOf("face_0", "face_1", "face_2", "face_3", "face_4", "face_5", "face_6"),
                true
            ),
            contour("left_eye_contour", listOf("left_eye_a", "left_eye_b"), false),
            contour("right_eye_contour", listOf("right_eye_a", "right_eye_b"), false),
            contour("mouth_contour", listOf("mouth_left", "mouth_right"), false)
        ).associateBy { it.id }

        return SubjectObservation(
            subjectCount = 1,
            faceCount = 1,
            bodyCount = 0,
            landmarks = points,
            regions = emptyMap(),
            pose = PoseObservation(yawDegrees = 58f, pitchDegrees = 0f, rollDegrees = 0f),
            facePoses = mapOf(0 to PoseObservation(yawDegrees = 58f, pitchDegrees = 0f, rollDegrees = 0f)),
            contours = contours
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
