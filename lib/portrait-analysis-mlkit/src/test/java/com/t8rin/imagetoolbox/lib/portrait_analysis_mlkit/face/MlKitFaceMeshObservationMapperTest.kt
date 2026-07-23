/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis_mlkit.face

import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ConfidenceSource
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.VisibilityState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MlKitFaceMeshObservationMapperTest {

    @Test
    fun `maps raw point index without inventing confidence`() {
        val result = MlKitFaceMeshObservationMapper.map(
            MlKitFaceMeshSnapshot(
                imageWidth = 1000,
                imageHeight = 500,
                faces = listOf(
                    MlKitFaceSnapshot(
                        points = listOf(
                            MlKitFaceMeshPointSnapshot(
                                index = 42,
                                xPixels = 250f,
                                yPixels = 125f,
                                zPixels = -25f
                            )
                        )
                    )
                )
            )
        )

        val point = result.landmarks.getValue("face_0_mesh_42")
        assertEquals(0.25f, point.point.x)
        assertEquals(0.25f, point.point.y)
        assertEquals(-0.025f, point.point.z)
        assertNull(point.confidence)
        assertEquals(ConfidenceSource.UNAVAILABLE, point.confidenceSource)
        assertEquals(VisibilityState.VISIBLE, point.visibility)
    }

    @Test
    fun `keeps multiple faces in separate namespaces`() {
        val result = MlKitFaceMeshObservationMapper.map(
            MlKitFaceMeshSnapshot(
                imageWidth = 100,
                imageHeight = 100,
                faces = listOf(
                    MlKitFaceSnapshot(
                        points = listOf(
                            MlKitFaceMeshPointSnapshot(0, 10f, 10f, 0f)
                        )
                    ),
                    MlKitFaceSnapshot(
                        points = listOf(
                            MlKitFaceMeshPointSnapshot(0, 90f, 90f, 0f)
                        )
                    )
                )
            )
        )

        assertEquals(2, result.faceCount)
        assertTrue(result.landmarks.containsKey("face_0_mesh_0"))
        assertTrue(result.landmarks.containsKey("face_1_mesh_0"))
    }

    @Test
    fun `preserves semantic contour order and closure`() {
        val result = MlKitFaceMeshObservationMapper.map(
            MlKitFaceMeshSnapshot(
                imageWidth = 100,
                imageHeight = 100,
                faces = listOf(
                    MlKitFaceSnapshot(
                        points = listOf(
                            MlKitFaceMeshPointSnapshot(10, 10f, 10f, 0f),
                            MlKitFaceMeshPointSnapshot(11, 20f, 10f, 0f),
                            MlKitFaceMeshPointSnapshot(12, 30f, 10f, 0f)
                        ),
                        contours = listOf(
                            MlKitFaceMeshContourSnapshot(
                                id = "upper_lip_top",
                                pointIndices = listOf(12, 10, 11),
                                closed = false
                            )
                        )
                    )
                )
            )
        )

        val contour = result.contours.getValue(
            "face_0_mesh_contour_upper_lip_top"
        )
        assertEquals(
            listOf("face_0_mesh_12", "face_0_mesh_10", "face_0_mesh_11"),
            contour.vertexIds
        )
        assertFalse(contour.closed)
        assertNull(contour.confidence)
        assertEquals(ConfidenceSource.UNAVAILABLE, contour.confidenceSource)
    }
}
