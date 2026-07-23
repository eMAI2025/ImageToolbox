/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis_mlkit

import com.t8rin.imagetoolbox.lib.portrait_analysis_mlkit.face.MlKitFaceMeshObservationMapper
import com.t8rin.imagetoolbox.lib.portrait_analysis_mlkit.face.MlKitFaceMeshPointSnapshot
import com.t8rin.imagetoolbox.lib.portrait_analysis_mlkit.face.MlKitFaceMeshSnapshot
import com.t8rin.imagetoolbox.lib.portrait_analysis_mlkit.face.MlKitFaceMeshTriangleSnapshot
import com.t8rin.imagetoolbox.lib.portrait_analysis_mlkit.face.MlKitFaceSnapshot
import com.t8rin.imagetoolbox.lib.portrait_analysis_mlkit.segmentation.MlKitSelfieSegmentationObservationMapper
import com.t8rin.imagetoolbox.lib.portrait_analysis_mlkit.segmentation.MlKitSelfieSegmentationSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpatialMappingTest {

    @Test
    fun `face mesh mapper preserves triangle topology`() {
        val observation = MlKitFaceMeshObservationMapper.map(
            MlKitFaceMeshSnapshot(
                imageWidth = 100,
                imageHeight = 100,
                faces = listOf(
                    MlKitFaceSnapshot(
                        points = listOf(
                            MlKitFaceMeshPointSnapshot(0, 10f, 10f, 0f),
                            MlKitFaceMeshPointSnapshot(1, 20f, 10f, 0f),
                            MlKitFaceMeshPointSnapshot(2, 15f, 20f, 0f)
                        ),
                        triangles = listOf(
                            MlKitFaceMeshTriangleSnapshot(0, 1, 2)
                        )
                    )
                )
            )
        )

        val mesh = observation.meshes.getValue("face_0_mesh")
        assertEquals(3, mesh.vertexIds.size)
        assertEquals(1, mesh.triangles.size)
        assertEquals(
            setOf("face_0_mesh_0", "face_0_mesh_1", "face_0_mesh_2"),
            mesh.triangles.single().vertexIds
        )
    }

    @Test
    fun `segmentation mapper preserves every confidence pixel`() {
        val source = floatArrayOf(0.1f, 0.8f, 0.9f, 0.2f)
        val observation = MlKitSelfieSegmentationObservationMapper.map(
            MlKitSelfieSegmentationSnapshot(
                maskWidth = 2,
                maskHeight = 2,
                personConfidence = source
            )
        )

        val mask = observation.masks.getValue("subject_mask").mask
        assertEquals(4, mask.size)
        assertEquals(0.1f, mask[0, 0])
        assertEquals(0.8f, mask[1, 0])
        assertEquals(0.9f, mask[0, 1])
        assertEquals(0.2f, mask[1, 1])
        assertTrue(observation.regions.containsKey("subject_mask"))
    }
}
