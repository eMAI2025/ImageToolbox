/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis.derive

import com.t8rin.imagetoolbox.lib.portrait_analysis.catalog.P2FaceParameterId
import com.t8rin.imagetoolbox.lib.portrait_analysis.catalog.P2FaceRegionParameterCatalog
import com.t8rin.imagetoolbox.lib.portrait_analysis.gate.ParameterGateDecision
import com.t8rin.imagetoolbox.lib.portrait_analysis.gate.ParameterGateEvaluator
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ConfidenceSource
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ContourObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.LandmarkObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.NormalizedPoint3D
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ObservationBackend
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.PoseObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.VisibilityState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceRegionAwarenessAnalyzerTest {

    @Test
    fun `frontal face keeps full-face geometry available`() {
        val result = FaceRegionAwarenessAnalyzer.analyzeAndEnrich(
            observation(yaw = 5f, noseX = 0.50f)
        )

        assertEquals(FacePoseMode.FRONTAL, result.awareness.poseMode)
        assertEquals(FaceImageSide.BALANCED, result.awareness.dominantImageSide)
        assertTrue(result.awareness.fullFaceGeometryAllowed)
        assertEquals(
            FaceRegionAvailability.AVAILABLE,
            result.awareness.regions.getValue(FaceRegionKey.CHIN).availability
        )
    }

    @Test
    fun `profile blocks full-face fit but preserves dominant half jaw and chin`() {
        val result = FaceRegionAwarenessAnalyzer.analyzeAndEnrich(
            observation(yaw = 58f, noseX = 0.67f)
        )

        assertEquals(FacePoseMode.PROFILE, result.awareness.poseMode)
        assertEquals(FaceImageSide.LEFT, result.awareness.dominantImageSide)
        assertFalse(result.awareness.fullFaceGeometryAllowed)
        assertEquals(
            FaceRegionAvailability.AVAILABLE,
            result.awareness.regions.getValue(FaceRegionKey.LEFT_JAW).availability
        )
        assertEquals(
            FaceRegionAvailability.UNRELIABLE,
            result.awareness.regions.getValue(FaceRegionKey.RIGHT_JAW).availability
        )
        assertTrue(result.awareness.regions.getValue(FaceRegionKey.CHIN).geometryEditAllowed)
    }

    @Test
    fun `profile side gate enables only the reliable jaw side`() {
        val result = FaceRegionAwarenessAnalyzer.analyzeAndEnrich(
            observation(yaw = 58f, noseX = 0.67f)
        )
        val leftSpec = P2FaceRegionParameterCatalog.gateSpecs.first {
            it.parameterId == P2FaceParameterId.IMAGE_LEFT_JAW_WIDTH
        }
        val rightSpec = P2FaceRegionParameterCatalog.gateSpecs.first {
            it.parameterId == P2FaceParameterId.IMAGE_RIGHT_JAW_WIDTH
        }

        assertTrue(
            ParameterGateEvaluator.evaluate(leftSpec, result.observation) is
                ParameterGateDecision.Enabled
        )
        assertTrue(
            ParameterGateEvaluator.evaluate(rightSpec, result.observation) is
                ParameterGateDecision.Disabled
        )
    }

    @Test
    fun `subchin remains blocked without semantic mask evidence`() {
        val result = FaceRegionAwarenessAnalyzer.analyzeAndEnrich(
            observation(yaw = 0f, noseX = 0.50f)
        )
        val spec = P2FaceRegionParameterCatalog.gateSpecs.first {
            it.parameterId == P2FaceParameterId.SUBCHIN_SOFTNESS
        }

        assertEquals(
            FaceRegionAvailability.UNAVAILABLE,
            result.awareness.regions.getValue(FaceRegionKey.SUBCHIN).availability
        )
        assertTrue(
            ParameterGateEvaluator.evaluate(spec, result.observation) is
                ParameterGateDecision.Disabled
        )
    }

    private fun observation(yaw: Float, noseX: Float): SubjectObservation {
        val landmarks = linkedMapOf<String, LandmarkObservation>()
        fun add(id: String, x: Float, y: Float) {
            landmarks[id] = LandmarkObservation(
                id = id,
                point = NormalizedPoint3D(x, y),
                confidence = null,
                confidenceSource = ConfidenceSource.UNAVAILABLE,
                visibility = VisibilityState.VISIBLE,
                backend = ObservationBackend.ML_KIT_FACE_DETECTION
            )
        }

        val bounding = listOf(
            "face_0_detection_bounding_box_top_left" to NormalizedPoint3D(0.20f, 0.12f),
            "face_0_detection_bounding_box_top_right" to NormalizedPoint3D(0.80f, 0.12f),
            "face_0_detection_bounding_box_bottom_right" to NormalizedPoint3D(0.80f, 0.90f),
            "face_0_detection_bounding_box_bottom_left" to NormalizedPoint3D(0.20f, 0.90f)
        )
        bounding.forEach { (id, point) -> add(id, point.x, point.y) }

        val facePoints = listOf(
            NormalizedPoint3D(0.22f, 0.30f),
            NormalizedPoint3D(0.20f, 0.48f),
            NormalizedPoint3D(0.24f, 0.70f),
            NormalizedPoint3D(0.34f, 0.84f),
            NormalizedPoint3D(0.48f, 0.90f),
            NormalizedPoint3D(0.62f, 0.84f),
            NormalizedPoint3D(0.75f, 0.70f),
            NormalizedPoint3D(0.80f, 0.48f),
            NormalizedPoint3D(0.76f, 0.30f)
        )
        facePoints.forEachIndexed { index, point ->
            add("face_0_detection_contour_face_point_$index", point.x, point.y)
            add("face_0_detection_contour_jawline_point_$index", point.x, point.y)
        }
        facePoints.subList(3, 7).forEachIndexed { index, point ->
            add("face_0_detection_contour_chin_point_$index", point.x, point.y)
        }

        repeat(4) { index ->
            add("face_0_detection_contour_left_eye_point_$index", 0.34f + index * 0.025f, 0.40f)
            add("face_0_detection_contour_right_eye_point_$index", 0.57f + index * 0.025f, 0.40f)
            add("face_0_detection_contour_left_eyebrow_top_point_$index", 0.33f + index * 0.03f, 0.34f)
            add("face_0_detection_contour_right_eyebrow_top_point_$index", 0.56f + index * 0.03f, 0.34f)
            add("face_0_detection_contour_upper_lip_top_point_$index", 0.43f + index * 0.04f, 0.67f)
            add("face_0_detection_contour_lower_lip_bottom_point_$index", 0.43f + index * 0.04f, 0.71f)
        }
        repeat(3) { index ->
            add("face_0_detection_contour_nose_bridge_point_$index", noseX, 0.43f + index * 0.06f)
            add("face_0_detection_contour_left_cheek_point_$index", 0.30f, 0.54f + index * 0.03f)
            add("face_0_detection_contour_right_cheek_point_$index", 0.70f, 0.54f + index * 0.03f)
        }
        add("face_0_detection_landmark_nose_base", noseX, 0.58f)
        add("face_0_detection_landmark_mouth_bottom", 0.50f, 0.72f)

        val contours = linkedMapOf<String, ContourObservation>()
        fun contour(id: String, vertexIds: List<String>, closed: Boolean) {
            contours[id] = ContourObservation(
                id = id,
                vertexIds = vertexIds,
                closed = closed,
                confidence = null,
                confidenceSource = ConfidenceSource.UNAVAILABLE,
                backend = ObservationBackend.ML_KIT_FACE_DETECTION
            )
        }
        contour(
            "face_0_detection_bounding_box",
            bounding.map { it.first },
            closed = true
        )
        contour(
            "face_0_detection_contour_face",
            facePoints.indices.map { "face_0_detection_contour_face_point_$it" },
            closed = true
        )
        contour(
            "face_0_detection_contour_jawline",
            facePoints.indices.map { "face_0_detection_contour_jawline_point_$it" },
            closed = false
        )
        contour(
            "face_0_detection_contour_chin",
            (0..3).map { "face_0_detection_contour_chin_point_$it" },
            closed = false
        )

        return SubjectObservation(
            subjectCount = 1,
            faceCount = 1,
            bodyCount = 0,
            landmarks = landmarks,
            regions = emptyMap(),
            pose = PoseObservation(yawDegrees = yaw, pitchDegrees = 0f, rollDegrees = 0f),
            facePoses = mapOf(
                0 to PoseObservation(yawDegrees = yaw, pitchDegrees = 0f, rollDegrees = 0f)
            ),
            contours = contours
        )
    }
}
