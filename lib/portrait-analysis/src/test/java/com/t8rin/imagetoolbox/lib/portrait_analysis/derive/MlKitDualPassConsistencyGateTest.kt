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

class MlKitDualPassConsistencyGateTest {

    @Test
    fun `consistent full image and ROI observations are accepted`() {
        val full = observation()
        val roi = observation(
            bounds = listOf(
                point("bbox_tl", 0.205f, 0.105f),
                point("bbox_tr", 0.805f, 0.105f),
                point("bbox_br", 0.805f, 0.905f),
                point("bbox_bl", 0.205f, 0.905f)
            ),
            anchorOffsetX = 0.005f
        )

        val result = MlKitDualPassConsistencyGate.evaluate(
            fullPassObservation = full,
            fullPassAwareness = awareness(yaw = 28f, roll = 3f),
            roiPassObservation = roi,
            roiPassAwareness = awareness(yaw = 30f, roll = 4f)
        )

        assertTrue(result.accepted)
        assertTrue(result.reasons.isEmpty())
        assertTrue((result.metrics.boundsIou ?: 0f) >= MlKitDualPassConsistencyGate.MINIMUM_BOUNDS_IOU)
    }

    @Test
    fun `low bounding box overlap is rejected without merging passes`() {
        val result = MlKitDualPassConsistencyGate.evaluate(
            fullPassObservation = observation(),
            fullPassAwareness = awareness(),
            roiPassObservation = observation(
                bounds = listOf(
                    point("bbox_tl", 0.62f, 0.10f),
                    point("bbox_tr", 0.95f, 0.10f),
                    point("bbox_br", 0.95f, 0.72f),
                    point("bbox_bl", 0.62f, 0.72f)
                ),
                anchorOffsetX = 0.32f
            ),
            roiPassAwareness = awareness()
        )

        assertFalse(result.accepted)
        assertTrue(
            MlKitDualPassConsistencyGate.RejectionReason.BOUNDS_MISSING_OR_INCONSISTENT in
                result.reasons
        )
    }

    @Test
    fun `yaw drift beyond versioned ceiling is rejected`() {
        val result = MlKitDualPassConsistencyGate.evaluate(
            fullPassObservation = observation(),
            fullPassAwareness = awareness(yaw = 22f),
            roiPassObservation = observation(),
            roiPassAwareness = awareness(yaw = 34.5f)
        )

        assertFalse(result.accepted)
        assertTrue(
            MlKitDualPassConsistencyGate.RejectionReason.POSE_EVIDENCE_MISSING_OR_INCONSISTENT in
                result.reasons
        )
    }

    @Test
    fun `central anchor drift is rejected`() {
        val result = MlKitDualPassConsistencyGate.evaluate(
            fullPassObservation = observation(),
            fullPassAwareness = awareness(),
            roiPassObservation = observation(anchorOffsetX = 0.24f),
            roiPassAwareness = awareness()
        )

        assertFalse(result.accepted)
        assertTrue(
            MlKitDualPassConsistencyGate.RejectionReason.CENTRAL_ANCHORS_MISSING_OR_INCONSISTENT in
                result.reasons
        )
    }

    @Test
    fun `missing or ambiguous ROI pass fails closed`() {
        val missing = MlKitDualPassConsistencyGate.evaluate(
            fullPassObservation = observation(),
            fullPassAwareness = awareness(),
            roiPassObservation = null,
            roiPassAwareness = null
        )
        val ambiguous = MlKitDualPassConsistencyGate.evaluate(
            fullPassObservation = observation(),
            fullPassAwareness = awareness(),
            roiPassObservation = observation().copy(faceCount = 2),
            roiPassAwareness = awareness()
        )

        assertFalse(missing.accepted)
        assertFalse(ambiguous.accepted)
        assertTrue(
            MlKitDualPassConsistencyGate.RejectionReason.SECOND_PASS_MISSING_OR_AMBIGUOUS in
                missing.reasons
        )
        assertTrue(
            MlKitDualPassConsistencyGate.RejectionReason.SECOND_PASS_MISSING_OR_AMBIGUOUS in
                ambiguous.reasons
        )
    }

    private fun awareness(
        yaw: Float = 28f,
        roll: Float = 3f,
        side: FaceImageSide = FaceImageSide.LEFT
    ) = FaceRegionAwareness(
        poseMode = FacePoseMode.HALF_PROFILE,
        dominantImageSide = side,
        yawDegrees = yaw,
        pitchDegrees = 0f,
        rollDegrees = roll,
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
        ),
        anchorOffsetX: Float = 0f
    ): SubjectObservation {
        val anchors = listOf(
            point("left_eye_center", 0.36f + anchorOffsetX, 0.36f),
            point("right_eye_center", 0.64f + anchorOffsetX, 0.36f),
            point("nose_base", 0.50f + anchorOffsetX, 0.52f),
            point("mouth_left", 0.42f + anchorOffsetX, 0.69f),
            point("mouth_right", 0.58f + anchorOffsetX, 0.69f),
            point("chin_center", 0.50f + anchorOffsetX, 0.83f)
        )
        val landmarks = (bounds + anchors).associateBy { it.id }
        val pose = PoseObservation(yawDegrees = 28f, pitchDegrees = 0f, rollDegrees = 3f)
        return SubjectObservation(
            subjectCount = 1,
            faceCount = 1,
            bodyCount = 0,
            landmarks = landmarks,
            pose = pose,
            facePoses = mapOf(0 to pose),
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
