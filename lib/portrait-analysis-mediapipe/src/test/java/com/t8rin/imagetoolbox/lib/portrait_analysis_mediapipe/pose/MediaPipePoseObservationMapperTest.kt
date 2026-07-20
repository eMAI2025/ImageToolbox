/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis_mediapipe.pose

import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ConfidenceSource
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ObservationBackend
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.VisibilityState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaPipePoseObservationMapperTest {

    @Test
    fun `maps direct pose confidence and person mask`() {
        val result = MediaPipePoseObservationMapper.map(
            MediaPipePoseLandmarkerSnapshot(
                poses = listOf(
                    MediaPipePoseSnapshot(
                        landmarks = listOf(
                            MediaPipePoseLandmarkSnapshot(
                                index = 11,
                                x = 0.35f,
                                y = 0.4f,
                                z = -0.1f,
                                visibility = 0.92f,
                                presence = 0.88f,
                                worldX = -0.2f,
                                worldY = 0.1f,
                                worldZ = 0.3f
                            ),
                            MediaPipePoseLandmarkSnapshot(
                                index = 12,
                                x = 0.65f,
                                y = 0.4f,
                                z = -0.1f,
                                visibility = 0.90f,
                                presence = 0.94f,
                                worldX = 0.2f,
                                worldY = 0.1f,
                                worldZ = 0.3f
                            )
                        ),
                        segmentationMask = MediaPipeSegmentationMaskSnapshot(
                            width = 2,
                            height = 2,
                            confidenceValues = floatArrayOf(0f, 0.8f, 0.9f, 1f)
                        )
                    )
                )
            )
        )

        val leftShoulder = result.landmarks.getValue("body_0_mediapipe_pose_11")
        assertEquals(0.88f, leftShoulder.confidence)
        assertEquals(ConfidenceSource.DIRECT, leftShoulder.confidenceSource)
        assertEquals(VisibilityState.VISIBLE, leftShoulder.visibility)
        assertEquals(ObservationBackend.MEDIAPIPE_POSE, leftShoulder.backend)
        assertEquals(1, result.masks.size)
        assertEquals(
            0.9f,
            result.masks.getValue("body_0_mediapipe_person_mask").mask[0, 1]
        )
    }

    @Test
    fun `uses weaker presence or visibility evidence`() {
        val result = MediaPipePoseObservationMapper.map(
            MediaPipePoseLandmarkerSnapshot(
                poses = listOf(
                    MediaPipePoseSnapshot(
                        landmarks = listOf(
                            MediaPipePoseLandmarkSnapshot(
                                index = 15,
                                x = 0.2f,
                                y = 0.5f,
                                z = 0f,
                                visibility = 0.95f,
                                presence = 0.30f
                            )
                        )
                    )
                )
            )
        )

        val wrist = result.landmarks.getValue("body_0_mediapipe_pose_15")
        assertEquals(0.30f, wrist.confidence)
        assertEquals(VisibilityState.OCCLUDED, wrist.visibility)
        assertTrue(result.bodyCount == 1 && result.subjectCount == 1)
    }
}
