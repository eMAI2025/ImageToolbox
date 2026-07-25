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

class FaceInputQualityPolicyTest {

    @Test
    fun `large sharp face with consistent EXIF passes`() {
        val result = FaceInputQualityPolicy.evaluate(observation(), evidence())
        assertTrue(result.accepted)
        assertTrue(result.reasons.isEmpty())
    }

    @Test
    fun `small face in large image requests reselection`() {
        val result = FaceInputQualityPolicy.evaluate(
            observation(left = 0.45f, top = 0.45f, right = 0.49f, bottom = 0.49f),
            evidence(sourceWidth = 2000, sourceHeight = 2000)
        )
        assertFalse(result.accepted)
        assertTrue(FaceGeometryRejectionReason.FACE_TOO_SMALL_IN_SOURCE_PIXELS in result.reasons)
        assertTrue(FaceGeometryRejectionReason.IMAGE_QUALITY_RESELECT_REQUIRED in result.reasons)
    }

    @Test
    fun `blur below versioned edge energy floor is rejected`() {
        val result = FaceInputQualityPolicy.evaluate(
            observation(),
            evidence(edgeEnergy = 0.004f)
        )
        assertTrue(FaceGeometryRejectionReason.IMAGE_BLUR_TOO_HIGH in result.reasons)
    }

    @Test
    fun `missing blur evidence fails closed`() {
        val result = FaceInputQualityPolicy.evaluate(
            observation(),
            evidence(edgeEnergy = null)
        )
        assertTrue(FaceGeometryRejectionReason.IMAGE_BLUR_EVIDENCE_UNAVAILABLE in result.reasons)
    }

    @Test
    fun `rotated EXIF requires swapped oriented dimensions and exact rotation`() {
        val invalidDimensions = FaceInputQualityPolicy.evaluate(
            observation(),
            evidence(
                encodedWidth = 1200,
                encodedHeight = 800,
                orientedWidth = 1200,
                orientedHeight = 800,
                exifOrientation = 6,
                orientationDegrees = 90
            )
        )
        val invalidRotation = FaceInputQualityPolicy.evaluate(
            observation(),
            evidence(
                encodedWidth = 1200,
                encodedHeight = 800,
                orientedWidth = 800,
                orientedHeight = 1200,
                sourceWidth = 800,
                sourceHeight = 1200,
                exifOrientation = 6,
                orientationDegrees = 270
            )
        )
        val valid = FaceInputQualityPolicy.evaluate(
            observation(),
            evidence(
                encodedWidth = 1200,
                encodedHeight = 800,
                orientedWidth = 800,
                orientedHeight = 1200,
                sourceWidth = 800,
                sourceHeight = 1200,
                exifOrientation = 6,
                orientationDegrees = 90
            )
        )
        assertTrue(FaceGeometryRejectionReason.EXIF_TRANSFORM_INCONSISTENT in invalidDimensions.reasons)
        assertTrue(FaceGeometryRejectionReason.EXIF_TRANSFORM_INCONSISTENT in invalidRotation.reasons)
        assertFalse(FaceGeometryRejectionReason.EXIF_TRANSFORM_INCONSISTENT in valid.reasons)
    }

    @Test
    fun `mirrored EXIF must preserve mirror provenance`() {
        val result = FaceInputQualityPolicy.evaluate(
            observation(),
            evidence(exifOrientation = 2, mirrored = false)
        )
        assertTrue(FaceGeometryRejectionReason.MIRROR_TRANSFORM_INCONSISTENT in result.reasons)
    }

    @Test
    fun `coordinate or visibility outside frame exceeds fail closed ratio`() {
        val source = observation()
        val outOfFrame = source.copy(
            landmarks = source.landmarks + mapOf(
                "point_out_coordinate" to point("point_out_coordinate", 1.10f, 0.50f),
                "point_out_visibility" to point(
                    "point_out_visibility",
                    0.55f,
                    0.55f,
                    VisibilityState.OUTSIDE_FRAME
                )
            )
        )
        val result = FaceInputQualityPolicy.evaluate(outOfFrame, evidence())

        assertFalse(result.accepted)
        assertTrue(
            FaceGeometryRejectionReason.GEOMETRY_OUT_OF_FRAME_RATIO_EXCEEDED in result.reasons
        )
        assertTrue(FaceGeometryRejectionReason.IMAGE_QUALITY_RESELECT_REQUIRED in result.reasons)
    }

    private fun evidence(
        sourceWidth: Int = 1200,
        sourceHeight: Int = 800,
        encodedWidth: Int = 1200,
        encodedHeight: Int = 800,
        orientedWidth: Int = 1200,
        orientedHeight: Int = 800,
        exifOrientation: Int = 1,
        orientationDegrees: Int = 0,
        mirrored: Boolean = false,
        edgeEnergy: Float? = 0.05f
    ) = FaceInputQualityPolicy.Evidence(
        sourceWidth = sourceWidth,
        sourceHeight = sourceHeight,
        encodedWidth = encodedWidth,
        encodedHeight = encodedHeight,
        orientedWidth = orientedWidth,
        orientedHeight = orientedHeight,
        exifOrientation = exifOrientation,
        orientationDegrees = orientationDegrees,
        mirrored = mirrored,
        normalizedEdgeEnergy = edgeEnergy
    )

    private fun observation(
        left: Float = 0.20f,
        top: Float = 0.10f,
        right: Float = 0.80f,
        bottom: Float = 0.90f
    ): SubjectObservation {
        val bounds = listOf(
            point("bbox_tl", left, top),
            point("bbox_tr", right, top),
            point("bbox_br", right, bottom),
            point("bbox_bl", left, bottom)
        )
        val features = listOf(
            point("left_eye_center", left + (right - left) * 0.30f, top + (bottom - top) * 0.30f),
            point("right_eye_center", left + (right - left) * 0.70f, top + (bottom - top) * 0.30f),
            point("nose_base", left + (right - left) * 0.50f, top + (bottom - top) * 0.52f),
            point("mouth_center", left + (right - left) * 0.50f, top + (bottom - top) * 0.70f),
            point("chin_center", left + (right - left) * 0.50f, top + (bottom - top) * 0.84f)
        )
        val landmarks = (bounds + features).associateBy { it.id }
        return SubjectObservation(
            subjectCount = 1,
            faceCount = 1,
            bodyCount = 0,
            landmarks = landmarks,
            regions = emptyMap(),
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

    private fun point(
        id: String,
        x: Float,
        y: Float,
        visibility: VisibilityState = VisibilityState.VISIBLE
    ) = LandmarkObservation(
        id = id,
        point = NormalizedPoint3D(x, y),
        confidence = null,
        confidenceSource = ConfidenceSource.UNAVAILABLE,
        visibility = visibility,
        backend = ObservationBackend.ML_KIT_FACE_DETECTION
    )
}
