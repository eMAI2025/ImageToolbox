/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis_mlkit.face

import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ConfidenceSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MlKitFaceDetectionObservationMapperTest {

    @Test
    fun `maps semantic landmark contour pose and classifications`() {
        val result = MlKitFaceDetectionObservationMapper.map(
            MlKitFaceDetectionSnapshot(
                imageWidth = 1000,
                imageHeight = 500,
                faces = listOf(
                    face(
                        landmarks = listOf(
                            MlKitFaceDetectionLandmarkSnapshot(
                                id = "mouth_left",
                                point = MlKitFaceDetectionPointSnapshot(250f, 125f)
                            )
                        ),
                        contours = listOf(
                            MlKitFaceDetectionContourSnapshot(
                                id = "upper_lip_top",
                                points = listOf(
                                    MlKitFaceDetectionPointSnapshot(240f, 120f),
                                    MlKitFaceDetectionPointSnapshot(250f, 118f),
                                    MlKitFaceDetectionPointSnapshot(260f, 120f)
                                ),
                                closed = false
                            )
                        )
                    )
                )
            )
        )

        val landmark = result.landmarks.getValue(
            "face_0_detection_landmark_mouth_left"
        )
        assertEquals(0.25f, landmark.point.x)
        assertEquals(0.25f, landmark.point.y)
        assertNull(landmark.confidence)
        assertEquals(ConfidenceSource.UNAVAILABLE, landmark.confidenceSource)

        val contour = result.contours.getValue(
            "face_0_detection_contour_upper_lip_top"
        )
        assertEquals(
            listOf(
                "face_0_detection_contour_upper_lip_top_point_0",
                "face_0_detection_contour_upper_lip_top_point_1",
                "face_0_detection_contour_upper_lip_top_point_2"
            ),
            contour.vertexIds
        )

        assertEquals(12f, result.pose.yawDegrees)
        assertEquals(-3f, result.pose.pitchDegrees)
        assertEquals(5f, result.pose.rollDegrees)
        assertEquals(
            0.8f,
            result.classifications.getValue(
                "face_0_detection_smiling_probability"
            ).probability
        )
        assertEquals(
            0.02f,
            result.regions.getValue("face_0_detection_region").pixelCoverage,
            0.0001f
        )
    }

    @Test
    fun `does not expose ambiguous aggregate pose for multiple faces`() {
        val result = MlKitFaceDetectionObservationMapper.map(
            MlKitFaceDetectionSnapshot(
                imageWidth = 1000,
                imageHeight = 500,
                faces = listOf(face(), face())
            )
        )

        assertEquals(2, result.faceCount)
        assertNull(result.pose.yawDegrees)
        assertNull(result.pose.pitchDegrees)
        assertNull(result.pose.rollDegrees)
        assertTrue(
            result.classifications.containsKey("face_0_detection_smiling_probability")
        )
        assertTrue(
            result.classifications.containsKey("face_1_detection_smiling_probability")
        )
    }

    @Test
    fun `clips a partially outside bounding box to source coverage`() {
        val result = MlKitFaceDetectionObservationMapper.map(
            MlKitFaceDetectionSnapshot(
                imageWidth = 100,
                imageHeight = 100,
                faces = listOf(
                    face(
                        left = -20f,
                        top = -10f,
                        right = 40f,
                        bottom = 50f
                    )
                )
            )
        )

        assertEquals(
            0.2f,
            result.regions.getValue("face_0_detection_region").pixelCoverage,
            0.0001f
        )
    }

    private fun face(
        left: Float = 100f,
        top: Float = 50f,
        right: Float = 200f,
        bottom: Float = 150f,
        landmarks: List<MlKitFaceDetectionLandmarkSnapshot> = emptyList(),
        contours: List<MlKitFaceDetectionContourSnapshot> = emptyList()
    ) = MlKitDetectedFaceSnapshot(
        boundingLeftPixels = left,
        boundingTopPixels = top,
        boundingRightPixels = right,
        boundingBottomPixels = bottom,
        yawDegrees = 12f,
        pitchDegrees = -3f,
        rollDegrees = 5f,
        smilingProbability = 0.8f,
        leftEyeOpenProbability = 0.9f,
        rightEyeOpenProbability = 0.7f,
        landmarks = landmarks,
        contours = contours
    )
}
