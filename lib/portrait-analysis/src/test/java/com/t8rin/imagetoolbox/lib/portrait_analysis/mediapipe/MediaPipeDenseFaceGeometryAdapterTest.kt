/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis.mediapipe

import com.t8rin.imagetoolbox.lib.portrait_analysis.model.NormalizedPoint3D
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaPipeDenseFaceGeometryAdapterTest {

    private val roi = ActiveFaceRoi(
        left = 0.20f,
        top = 0.10f,
        right = 0.80f,
        bottom = 0.90f,
        sourceWidth = 1000,
        sourceHeight = 800,
        selectedFaceIndex = 0
    )

    @Test
    fun `maps ROI landmarks to source while preserving indices`() {
        val result = MediaPipeDenseFaceGeometryAdapter.adapt(
            candidate = candidate(
                points = listOf(
                    MediaPipeRawLandmark(0, NormalizedPoint3D(0f, 0f)),
                    MediaPipeRawLandmark(1, NormalizedPoint3D(0.5f, 0.5f)),
                    MediaPipeRawLandmark(2, NormalizedPoint3D(1f, 1f))
                ),
                triangles = listOf(MediaPipeRawTriangle(0, 1, 2))
            ),
            activeRoi = roi
        )

        assertEquals(setOf(0, 1, 2), result.pointsByIndex.keys)
        assertEquals(0.20f, result.pointsByIndex.getValue(0).activePointInSource!!.x, 0.0001f)
        assertEquals(0.50f, result.pointsByIndex.getValue(1).activePointInSource!!.x, 0.0001f)
        assertEquals(0.50f, result.pointsByIndex.getValue(1).activePointInSource!!.y, 0.0001f)
        assertEquals(0.80f, result.pointsByIndex.getValue(2).activePointInSource!!.x, 0.0001f)
        assertEquals(1, result.activeTriangles.size)
    }

    @Test
    fun `records explicit clipping without overwriting raw ROI evidence`() {
        val result = MediaPipeDenseFaceGeometryAdapter.adapt(
            candidate = candidate(
                points = listOf(
                    MediaPipeRawLandmark(0, NormalizedPoint3D(-1f, 0.5f)),
                    MediaPipeRawLandmark(1, NormalizedPoint3D(0.5f, 0.5f)),
                    MediaPipeRawLandmark(2, NormalizedPoint3D(2f, 0.5f))
                ),
                triangles = listOf(MediaPipeRawTriangle(0, 1, 2))
            ),
            activeRoi = roi,
            clipToSourceBounds = true
        )

        assertEquals(-1f, result.pointsByIndex.getValue(0).rawPointInRoi.x, 0.0001f)
        assertEquals(0f, result.pointsByIndex.getValue(0).activePointInSource!!.x, 0.0001f)
        assertEquals(1f, result.pointsByIndex.getValue(2).activePointInSource!!.x, 0.0001f)
        assertEquals(2, result.diagnostics.clippedLandmarkCount)
        assertEquals(1, result.activeTriangles.size)
    }

    @Test
    fun `rejects triangles that reference missing indices`() {
        val result = MediaPipeDenseFaceGeometryAdapter.adapt(
            candidate = candidate(
                points = listOf(
                    MediaPipeRawLandmark(0, NormalizedPoint3D(0.2f, 0.2f)),
                    MediaPipeRawLandmark(1, NormalizedPoint3D(0.5f, 0.5f))
                ),
                triangles = listOf(MediaPipeRawTriangle(0, 1, 2))
            ),
            activeRoi = roi
        )

        assertTrue(result.activeTriangles.isEmpty())
        assertEquals(1, result.diagnostics.rejectedTriangleMissingVertexCount)
    }

    @Test
    fun `does not activate non finite landmarks or dependent triangles`() {
        val result = MediaPipeDenseFaceGeometryAdapter.adapt(
            candidate = candidate(
                points = listOf(
                    MediaPipeRawLandmark(0, NormalizedPoint3D(0.2f, 0.2f)),
                    MediaPipeRawLandmark(1, NormalizedPoint3D(Float.NaN, 0.5f)),
                    MediaPipeRawLandmark(2, NormalizedPoint3D(0.8f, 0.8f))
                ),
                triangles = listOf(MediaPipeRawTriangle(0, 1, 2))
            ),
            activeRoi = roi
        )

        assertNull(result.pointsByIndex.getValue(1).mappedPointInSource)
        assertNull(result.pointsByIndex.getValue(1).activePointInSource)
        assertEquals(DenseFacePointState.NON_FINITE_REJECTED, result.pointsByIndex.getValue(1).state)
        assertTrue(result.activeTriangles.isEmpty())
        assertEquals(1, result.diagnostics.rejectedNonFiniteLandmarkCount)
        assertEquals(1, result.diagnostics.rejectedTriangleInactiveVertexCount)
    }

    @Test
    fun `preserves blendshape and matrix diagnostics without treating them as accepted geometry`() {
        val matrix = MediaPipeTransformationMatrix(
            listOf(
                1f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f,
                0f, 0f, 1f, 0f,
                0f, 0f, 0f, 1f
            )
        )
        val result = MediaPipeDenseFaceGeometryAdapter.adapt(
            candidate = candidate(
                points = listOf(MediaPipeRawLandmark(0, NormalizedPoint3D(0.5f, 0.5f))),
                triangles = emptyList(),
                blendshapes = listOf(MediaPipeBlendshapeScore("jawOpen", 0.25f)),
                matrices = listOf(matrix)
            ),
            activeRoi = roi
        )

        assertEquals(1, result.diagnostics.blendshapeCount)
        assertEquals(1, result.diagnostics.transformationMatrixCount)
        assertEquals("jawOpen", result.blendshapes.single().categoryName)
        assertEquals(matrix, result.transformationMatrices.single())
    }

    private fun candidate(
        points: List<MediaPipeRawLandmark>,
        triangles: List<MediaPipeRawTriangle>,
        blendshapes: List<MediaPipeBlendshapeScore> = emptyList(),
        matrices: List<MediaPipeTransformationMatrix> = emptyList()
    ) = MediaPipeDenseFaceCandidate(
        detectedFaceIndex = 0,
        landmarks = points,
        triangles = triangles,
        blendshapes = blendshapes,
        transformationMatrices = matrices
    )
}