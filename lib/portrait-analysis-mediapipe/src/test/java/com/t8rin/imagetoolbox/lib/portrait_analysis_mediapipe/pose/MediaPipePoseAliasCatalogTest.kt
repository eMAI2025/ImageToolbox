/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis_mediapipe.pose

import com.t8rin.imagetoolbox.lib.portrait_analysis.catalog.PortraitLandmarkId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaPipePoseAliasCatalogTest {

    @Test
    fun `single body receives all 33 canonical aliases`() {
        val observation = MediaPipePoseObservationMapper.map(
            MediaPipePoseLandmarkerSnapshot(
                poses = listOf(pose())
            )
        )

        val enriched = MediaPipePoseAliasCatalog.enrichSingleBody(observation)

        assertEquals(66, enriched.landmarks.size)
        assertEquals(
            observation.landmarks.getValue(
                MediaPipePoseObservationMapper.rawPointId(0, 11)
            ).point,
            enriched.landmarks.getValue(PortraitLandmarkId.LEFT_SHOULDER).point
        )
        assertTrue(PortraitLandmarkId.RIGHT_ANKLE in enriched.landmarks)
        assertTrue("left_foot_index" in enriched.landmarks)
    }

    @Test
    fun `missing source point prevents only its alias`() {
        val observation = MediaPipePoseObservationMapper.map(
            MediaPipePoseLandmarkerSnapshot(
                poses = listOf(
                    pose().copy(
                        landmarks = pose().landmarks.filterNot { it.index == 27 }
                    )
                )
            )
        )

        val enriched = MediaPipePoseAliasCatalog.enrichSingleBody(observation)

        assertFalse(PortraitLandmarkId.LEFT_ANKLE in enriched.landmarks)
        assertTrue(PortraitLandmarkId.RIGHT_ANKLE in enriched.landmarks)
    }

    @Test
    fun `multiple bodies do not receive ambiguous aliases`() {
        val observation = MediaPipePoseObservationMapper.map(
            MediaPipePoseLandmarkerSnapshot(
                poses = listOf(pose(), pose())
            )
        )

        val enriched = MediaPipePoseAliasCatalog.enrichSingleBody(observation)

        assertEquals(observation, enriched)
        assertFalse(PortraitLandmarkId.LEFT_SHOULDER in enriched.landmarks)
    }

    private fun pose(): MediaPipePoseSnapshot = MediaPipePoseSnapshot(
        landmarks = (0..32).map { index ->
            MediaPipePoseLandmarkSnapshot(
                index = index,
                x = 0.10f + index * 0.01f,
                y = 0.20f + index * 0.01f,
                z = index * 0.001f,
                visibility = 0.95f,
                presence = 0.96f
            )
        }
    )
}
