/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis_mediapipe.face

import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ConfidenceSource
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ObservationBackend
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.VisibilityState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaPipeFaceObservationMapperTest {

    @Test
    fun `maps blendshapes and direct landmark evidence`() {
        val result = MediaPipeFaceObservationMapper.map(
            MediaPipeFaceLandmarkerSnapshot(
                faces = listOf(
                    MediaPipeFaceSnapshot(
                        landmarks = listOf(
                            MediaPipeFaceLandmarkSnapshot(
                                index = 61,
                                x = 0.4f,
                                y = 0.6f,
                                z = -0.02f,
                                visibility = 0.94f,
                                presence = 0.98f
                            )
                        ),
                        blendshapes = listOf(
                            MediaPipeBlendshapeSnapshot("mouthSmileLeft", 0.72f),
                            MediaPipeBlendshapeSnapshot("mouthFrownLeft", 0.08f)
                        ),
                        yawDegrees = 4f,
                        detectionConfidence = 0.97f
                    )
                )
            )
        )

        val point = result.landmarks.getValue("face_0_mediapipe_61")
        assertEquals(0.98f, point.confidence)
        assertEquals(ConfidenceSource.DIRECT, point.confidenceSource)
        assertEquals(VisibilityState.VISIBLE, point.visibility)
        assertEquals(ObservationBackend.MEDIAPIPE_FACE_LANDMARKER, point.backend)
        assertEquals(
            0.72f,
            result.classifications.getValue("face_0_blendshape_mouthSmileLeft").probability
        )
        assertEquals(4f, result.pose.yawDegrees)
        assertTrue("face_0_mediapipe_bounding_box" in result.contours)
    }

    @Test
    fun `preserves semantic contours and mesh triangles`() {
        val points = listOf(
            MediaPipeFaceLandmarkSnapshot(0, 0.20f, 0.20f, 0f, presence = 0.9f),
            MediaPipeFaceLandmarkSnapshot(1, 0.80f, 0.20f, 0f, presence = 0.9f),
            MediaPipeFaceLandmarkSnapshot(2, 0.50f, 0.80f, 0f, presence = 0.9f)
        )
        val result = MediaPipeFaceObservationMapper.map(
            MediaPipeFaceLandmarkerSnapshot(
                faces = listOf(
                    MediaPipeFaceSnapshot(
                        landmarks = points,
                        contours = listOf(
                            MediaPipeFaceContourSnapshot(
                                id = "chin",
                                pointIndices = listOf(0, 2, 1),
                                closed = false
                            )
                        ),
                        triangles = listOf(MediaPipeFaceTriangleSnapshot(0, 1, 2))
                    )
                )
            )
        )

        assertEquals(
            listOf(
                "face_0_mediapipe_0",
                "face_0_mediapipe_2",
                "face_0_mediapipe_1"
            ),
            result.contours.getValue("face_0_mediapipe_contour_chin").vertexIds
        )
        val mesh = result.meshes.getValue("face_0_mediapipe_mesh")
        assertEquals(1, mesh.triangles.size)
        assertEquals(3, mesh.vertexIds.size)
    }

    @Test
    fun `does not invent confidence when backend snapshot has none`() {
        val result = MediaPipeFaceObservationMapper.map(
            MediaPipeFaceLandmarkerSnapshot(
                faces = listOf(
                    MediaPipeFaceSnapshot(
                        landmarks = listOf(
                            MediaPipeFaceLandmarkSnapshot(
                                index = 1,
                                x = 0.5f,
                                y = 0.5f,
                                z = 0f
                            )
                        )
                    )
                )
            )
        )

        val point = result.landmarks.getValue("face_0_mediapipe_1")
        assertNull(point.confidence)
        assertEquals(ConfidenceSource.UNAVAILABLE, point.confidenceSource)
        assertEquals(VisibilityState.AMBIGUOUS, point.visibility)
    }

    @Test
    fun `marks finite point outside source frame as outside frame`() {
        val result = MediaPipeFaceObservationMapper.map(
            MediaPipeFaceLandmarkerSnapshot(
                faces = listOf(
                    MediaPipeFaceSnapshot(
                        landmarks = listOf(
                            MediaPipeFaceLandmarkSnapshot(
                                index = 2,
                                x = 1.1f,
                                y = 0.5f,
                                z = 0f,
                                presence = 0.99f
                            )
                        )
                    )
                )
            )
        )

        assertEquals(
            VisibilityState.OUTSIDE_FRAME,
            result.landmarks.getValue("face_0_mediapipe_2").visibility
        )
        assertTrue(result.faceCount == 1 && result.subjectCount == 1)
    }
}
