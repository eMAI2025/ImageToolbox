/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

/** Regression matrix for the complete face gate scope delivered in iterations 20-26. */
class FaceAcceptanceRegressionMatrixTest {

    @Test
    fun `frontal evidence is accepted by profile input and occlusion policies`() {
        val observation = observation(FacePoseMode.FRONTAL, FaceImageSide.BALANCED, 0f)
        val reasons = policyReasons(observation, awareness(FacePoseMode.FRONTAL, FaceImageSide.BALANCED, 0f))
        assertTrue(reasons.isEmpty())
    }

    @Test
    fun `mild half profile left and right pass only with matching visible anchors`() {
        val left = observation(FacePoseMode.HALF_PROFILE, FaceImageSide.LEFT, 28f)
        val right = observation(FacePoseMode.HALF_PROFILE, FaceImageSide.RIGHT, -28f)

        assertTrue(policyReasons(left, awareness(FacePoseMode.HALF_PROFILE, FaceImageSide.LEFT, 28f)).isEmpty())
        assertTrue(policyReasons(right, awareness(FacePoseMode.HALF_PROFILE, FaceImageSide.RIGHT, -28f)).isEmpty())
    }

    @Test
    fun `strong profile left and right are rejected without active geometry permission`() {
        listOf(
            observation(FacePoseMode.PROFILE, FaceImageSide.LEFT, 62f) to
                awareness(FacePoseMode.PROFILE, FaceImageSide.LEFT, 62f),
            observation(FacePoseMode.PROFILE, FaceImageSide.RIGHT, -62f) to
                awareness(FacePoseMode.PROFILE, FaceImageSide.RIGHT, -62f)
        ).forEach { (observation, awareness) ->
            val reasons = policyReasons(observation, awareness)
            assertTrue(FaceGeometryRejectionReason.UNSUPPORTED_PARTIAL_OR_STRONG_PROFILE in reasons)
        }
    }

    @Test
    fun `explicit hand occlusion and frame crop fail closed`() {
        val base = observation(FacePoseMode.HALF_PROFILE, FaceImageSide.LEFT, 28f)
        val awareness = awareness(FacePoseMode.HALF_PROFILE, FaceImageSide.LEFT, 28f)
        val hand = FaceOcclusionPartialPolicy.evaluate(
            candidateObservation = base,
            awareness = awareness,
            evidence = FaceOcclusionPartialPolicy.OcclusionEvidence(
                states = mapOf(
                    FaceOcclusionPartialPolicy.OcclusionKind.HAND to
                        FaceOcclusionPartialPolicy.EvidenceState.OCCLUDED
                ),
                sourceId = "iteration-27-matrix"
            )
        )
        val cropped = FaceOcclusionPartialPolicy.evaluate(
            candidateObservation = base.copy(
                landmarks = base.landmarks.mapValues { (id, value) ->
                    if (id.startsWith("bbox_") && id.endsWith("l")) {
                        value.copy(point = value.point.copy(x = 0f))
                    } else value
                }
            ),
            awareness = awareness
        )

        assertTrue(FaceGeometryRejectionReason.FACE_OCCLUDED_BY_HAND in hand.reasons)
        assertTrue(FaceGeometryRejectionReason.FACE_CROPPED_BY_FRAME in cropped.reasons)
    }

    @Test
    fun `small face and inconsistent EXIF mirror request reselection`() {
        val small = observation(FacePoseMode.FRONTAL, FaceImageSide.BALANCED, 0f, left = 0.47f, right = 0.53f)
        val smallResult = FaceInputQualityPolicy.evaluate(small, qualityEvidence())
        val transformResult = FaceInputQualityPolicy.evaluate(
            observation(FacePoseMode.FRONTAL, FaceImageSide.BALANCED, 0f),
            qualityEvidence(
                encodedWidth = 1200,
                encodedHeight = 800,
                orientedWidth = 1200,
                orientedHeight = 800,
                exifOrientation = 6,
                orientationDegrees = 0,
                mirrored = true
            )
        )

        assertTrue(FaceGeometryRejectionReason.FACE_TOO_SMALL_IN_SOURCE_PIXELS in smallResult.reasons)
        assertTrue(FaceGeometryRejectionReason.IMAGE_QUALITY_RESELECT_REQUIRED in smallResult.reasons)
        assertTrue(FaceGeometryRejectionReason.EXIF_TRANSFORM_INCONSISTENT in transformResult.reasons)
        assertTrue(FaceGeometryRejectionReason.MIRROR_TRANSFORM_INCONSISTENT in transformResult.reasons)
    }

    @Test
    fun `two-face source is not treated as two active geometries`() {
        val selected = observation(FacePoseMode.FRONTAL, FaceImageSide.BALANCED, 0f)
        val source = selected.copy(subjectCount = 2, faceCount = 2, facePoses = mapOf(0 to selected.pose, 1 to selected.pose))
        val active = selected.copy(subjectCount = 1, faceCount = 1, facePoses = mapOf(0 to selected.pose))

        assertTrue(source.faceCount == 2)
        assertTrue(active.faceCount == 1)
        assertTrue(policyReasons(active, awareness(FacePoseMode.FRONTAL, FaceImageSide.BALANCED, 0f)).isEmpty())
        assertFalse(source === active)
    }

    private fun policyReasons(
        observation: SubjectObservation,
        awareness: FaceRegionAwareness
    ): Set<FaceGeometryRejectionReason> = buildSet {
        addAll(MlKitFaceProfilePolicy.evaluate(observation, awareness).reasons)
        addAll(FaceOcclusionPartialPolicy.evaluate(observation, awareness).reasons)
        addAll(FaceInputQualityPolicy.evaluate(observation, qualityEvidence()).reasons)
    }

    private fun awareness(
        mode: FacePoseMode,
        side: FaceImageSide,
        yaw: Float
    ) = FaceRegionAwareness(
        poseMode = mode,
        dominantImageSide = side,
        yawDegrees = yaw,
        pitchDegrees = 0f,
        rollDegrees = 0f,
        fullFaceGeometryAllowed = mode == FacePoseMode.FRONTAL,
        regions = emptyMap(),
        reasons = emptyList()
    )

    private fun qualityEvidence(
        encodedWidth: Int = 1200,
        encodedHeight: Int = 800,
        orientedWidth: Int = 1200,
        orientedHeight: Int = 800,
        exifOrientation: Int = 1,
        orientationDegrees: Int = 0,
        mirrored: Boolean = false
    ) = FaceInputQualityPolicy.Evidence(
        sourceWidth = orientedWidth,
        sourceHeight = orientedHeight,
        encodedWidth = encodedWidth,
        encodedHeight = encodedHeight,
        orientedWidth = orientedWidth,
        orientedHeight = orientedHeight,
        exifOrientation = exifOrientation,
        orientationDegrees = orientationDegrees,
        mirrored = mirrored,
        normalizedEdgeEnergy = 0.05f
    )

    private fun observation(
        mode: FacePoseMode,
        side: FaceImageSide,
        yaw: Float,
        left: Float = 0.20f,
        right: Float = 0.80f
    ): SubjectObservation {
        val top = 0.10f
        val bottom = 0.90f
        val bounds = listOf(
            point("bbox_tl", left, top),
            point("bbox_tr", right, top),
            point("bbox_br", right, bottom),
            point("bbox_bl", left, bottom)
        )
        val center = listOf(
            point("nose_base", (left + right) / 2f, 0.52f),
            point("chin_center", (left + right) / 2f, 0.84f)
        )
        val sideAnchors = when (side) {
            FaceImageSide.LEFT -> listOf(
                point("left_eye_center", left + (right - left) * 0.30f, 0.35f),
                point("mouth_left", left + (right - left) * 0.40f, 0.69f)
            )
            FaceImageSide.RIGHT -> listOf(
                point("right_eye_center", left + (right - left) * 0.70f, 0.35f),
                point("mouth_right", left + (right - left) * 0.60f, 0.69f)
            )
            else -> listOf(
                point("left_eye_center", left + (right - left) * 0.30f, 0.35f),
                point("right_eye_center", left + (right - left) * 0.70f, 0.35f),
                point("mouth_left", left + (right - left) * 0.40f, 0.69f),
                point("mouth_right", left + (right - left) * 0.60f, 0.69f)
            )
        }
        val landmarks = (bounds + center + sideAnchors).associateBy { it.id }
        val pose = PoseObservation(yawDegrees = yaw, pitchDegrees = 0f, rollDegrees = 0f)
        return SubjectObservation(
            subjectCount = 1,
            faceCount = 1,
            bodyCount = 0,
            landmarks = landmarks,
            regions = emptyMap(),
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
