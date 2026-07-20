/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis_mlkit.pose

import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ConfidenceSource
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.VisibilityState
import org.junit.Assert.assertEquals
import org.junit.Test

class MlKitPoseObservationMapperTest {

    @Test
    fun `uses in-frame likelihood as direct evidence`() {
        val result = MlKitPoseObservationMapper.map(
            MlKitPoseSnapshot(
                imageWidth = 1000,
                imageHeight = 500,
                landmarks = listOf(
                    MlKitPoseLandmarkSnapshot(
                        id = "left_shoulder",
                        xPixels = 250f,
                        yPixels = 100f,
                        zPixels = -50f,
                        inFrameLikelihood = 0.92f
                    )
                )
            )
        )

        val point = result.landmarks.getValue("left_shoulder")
        assertEquals(0.92f, point.confidence)
        assertEquals(ConfidenceSource.DIRECT, point.confidenceSource)
        assertEquals(VisibilityState.VISIBLE, point.visibility)
        assertEquals(1, result.bodyCount)
    }

    @Test
    fun `marks low-likelihood in-frame landmark as ambiguous`() {
        val result = MlKitPoseObservationMapper.map(
            MlKitPoseSnapshot(
                imageWidth = 100,
                imageHeight = 100,
                landmarks = listOf(
                    MlKitPoseLandmarkSnapshot(
                        id = "left_wrist",
                        xPixels = 50f,
                        yPixels = 50f,
                        zPixels = 0f,
                        inFrameLikelihood = 0.05f
                    )
                )
            )
        )

        assertEquals(
            VisibilityState.AMBIGUOUS,
            result.landmarks.getValue("left_wrist").visibility
        )
    }

    @Test
    fun `marks coordinates outside source image as outside frame`() {
        val result = MlKitPoseObservationMapper.map(
            MlKitPoseSnapshot(
                imageWidth = 100,
                imageHeight = 100,
                landmarks = listOf(
                    MlKitPoseLandmarkSnapshot(
                        id = "right_ankle",
                        xPixels = 120f,
                        yPixels = 80f,
                        zPixels = 0f,
                        inFrameLikelihood = 0.95f
                    )
                )
            )
        )

        assertEquals(
            VisibilityState.OUTSIDE_FRAME,
            result.landmarks.getValue("right_ankle").visibility
        )
    }
}
