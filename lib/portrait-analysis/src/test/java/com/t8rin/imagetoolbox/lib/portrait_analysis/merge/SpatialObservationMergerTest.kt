/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis.merge

import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ConfidenceMask
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.MeshObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.MeshTriangleObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ObservationBackend
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SemanticMaskObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpatialObservationMergerTest {

    @Test
    fun `merges independent mask and mesh records`() {
        val result = SubjectObservationMerger.merge(
            listOf(
                observation(
                    masks = mapOf("subject_mask" to mask(floatArrayOf(0.2f, 0.8f)))
                ),
                observation(
                    meshes = mapOf("face_0_mesh" to mesh("face_0_mesh", "a", "b", "c"))
                )
            )
        )

        assertTrue(result is ObservationMergeResult.Success)
        val merged = (result as ObservationMergeResult.Success).observation
        assertEquals(1, merged.masks.size)
        assertEquals(1, merged.meshes.size)
    }

    @Test
    fun `rejects different masks using the same id`() {
        val result = SubjectObservationMerger.merge(
            listOf(
                observation(
                    masks = mapOf("subject_mask" to mask(floatArrayOf(0.2f, 0.8f)))
                ),
                observation(
                    masks = mapOf("subject_mask" to mask(floatArrayOf(0.3f, 0.7f)))
                )
            )
        )

        assertTrue(result is ObservationMergeResult.Conflict)
        assertTrue(
            (result as ObservationMergeResult.Conflict).conflicts.any {
                it.type == ObservationMergeConflictType.MASK_ID_COLLISION
            }
        )
    }

    @Test
    fun `rejects different meshes using the same id`() {
        val result = SubjectObservationMerger.merge(
            listOf(
                observation(
                    meshes = mapOf("face_0_mesh" to mesh("face_0_mesh", "a", "b", "c"))
                ),
                observation(
                    meshes = mapOf("face_0_mesh" to mesh("face_0_mesh", "a", "b", "d"))
                )
            )
        )

        assertTrue(result is ObservationMergeResult.Conflict)
        assertTrue(
            (result as ObservationMergeResult.Conflict).conflicts.any {
                it.type == ObservationMergeConflictType.MESH_ID_COLLISION
            }
        )
    }

    private fun observation(
        masks: Map<String, SemanticMaskObservation> = emptyMap(),
        meshes: Map<String, MeshObservation> = emptyMap()
    ) = SubjectObservation(
        subjectCount = 1,
        faceCount = 0,
        bodyCount = 0,
        landmarks = emptyMap(),
        regions = emptyMap(),
        masks = masks,
        meshes = meshes
    )

    private fun mask(values: FloatArray) = SemanticMaskObservation(
        id = "subject_mask",
        mask = ConfidenceMask(width = 2, height = 1, confidenceValues = values),
        backend = ObservationBackend.ML_KIT_SELFIE_SEGMENTATION
    )

    private fun mesh(
        id: String,
        first: String,
        second: String,
        third: String
    ) = MeshObservation(
        id = id,
        vertexIds = setOf(first, second, third),
        triangles = listOf(MeshTriangleObservation(first, second, third)),
        backend = ObservationBackend.ML_KIT_FACE_MESH
    )
}
