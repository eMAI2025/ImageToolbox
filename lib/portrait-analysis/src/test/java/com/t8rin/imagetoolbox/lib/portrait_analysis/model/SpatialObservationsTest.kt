/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SpatialObservationsTest {

    @Test
    fun `confidence mask copies input and output arrays`() {
        val source = floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f)
        val mask = ConfidenceMask(width = 2, height = 2, confidenceValues = source)

        source[0] = 1f
        val exported = mask.copyValues()
        exported[1] = 1f

        assertEquals(0.1f, mask[0, 0])
        assertEquals(0.2f, mask[1, 0])
    }

    @Test
    fun `confidence masks use content equality`() {
        val first = ConfidenceMask(2, 1, floatArrayOf(0.2f, 0.8f))
        val equal = ConfidenceMask(2, 1, floatArrayOf(0.2f, 0.8f))
        val different = ConfidenceMask(2, 1, floatArrayOf(0.2f, 0.7f))

        assertEquals(first, equal)
        assertEquals(first.hashCode(), equal.hashCode())
        assertNotEquals(first, different)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `mesh rejects triangles that reference unknown vertices`() {
        MeshObservation(
            id = "face_0_mesh",
            vertexIds = setOf("a", "b", "c"),
            triangles = listOf(
                MeshTriangleObservation("a", "b", "missing")
            ),
            backend = ObservationBackend.ML_KIT_FACE_MESH
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `subject observation rejects mismatched map keys`() {
        val mask = SemanticMaskObservation(
            id = "subject_mask",
            mask = ConfidenceMask(1, 1, floatArrayOf(1f)),
            backend = ObservationBackend.ML_KIT_SELFIE_SEGMENTATION
        )
        SubjectObservation(
            subjectCount = 1,
            faceCount = 0,
            bodyCount = 0,
            landmarks = emptyMap(),
            regions = emptyMap(),
            masks = mapOf("wrong_key" to mask)
        )
    }
}
