/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.feature.portrait_lab

import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ConfidenceSource
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ContourObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.LandmarkObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.NormalizedPoint3D
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ObservationBackend
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.VisibilityState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class FaceDetectionBaselineRoutingTest {

    @Test
    fun `raw full-image observation remains source of truth when active geometry is empty`() {
        val raw = observation(faceCount = 3)
        val active = emptyObservation()

        val snapshot = FaceDetectionBaselineRouting.resolve(raw, active)

        assertEquals(listOf(0, 1, 2), snapshot.candidates.map { it.faceIndex })
        assertEquals(3, snapshot.baseline.faces.size)
        assertSame(raw, snapshot.baseline.fullImageObservation)
        assertNull(snapshot.activeFaceIndex)
    }

    @Test
    fun `fallback to active observation preserves baseline behavior when raw is unavailable`() {
        val active = observation(faceCount = 2)

        val snapshot = FaceDetectionBaselineRouting.resolve(null, active)

        assertEquals(listOf(0, 1), snapshot.candidates.map { it.faceIndex })
        assertSame(active, snapshot.baseline.fullImageObservation)
        assertNull(snapshot.activeFaceIndex)
    }

    @Test
    fun `resolving candidates does not crop select or discard group faces`() {
        val raw = observation(faceCount = 4)

        val snapshot = FaceDetectionBaselineRouting.resolve(raw, emptyObservation())

        assertEquals(4, snapshot.candidates.size)
        assertEquals(
            snapshot.baseline.faces.map { it.boundingBox },
            snapshot.candidates.map { it.bounds }
        )
        assertNull(snapshot.baseline.activeFaceIndex)
        assertNull(snapshot.activeFaceIndex)
    }

    @Test
    fun `no face produces empty candidate inventory without synthetic index zero`() {
        val snapshot = FaceDetectionBaselineRouting.resolve(
            rawObservation = emptyObservation(),
            activeObservation = emptyObservation()
        )

        assertEquals(emptyList<PortraitFaceCandidate>(), snapshot.candidates)
        assertNull(snapshot.activeFaceIndex)
    }

    private fun observation(faceCount: Int): SubjectObservation {
        val landmarks = linkedMapOf<String, LandmarkObservation>()
        val contours = linkedMapOf<String, ContourObservation>()
        repeat(faceCount) { faceIndex ->
            val left = 0.05f + faceIndex * 0.2f
            val right = left + 0.14f
            val top = 0.2f
            val bottom = 0.6f
            val prefix = "face_${faceIndex}_"
            val ids = listOf("bbox_tl", "bbox_tr", "bbox_br", "bbox_bl").map(prefix::plus)
            landmarks[ids[0]] = point(ids[0], left, top)
            landmarks[ids[1]] = point(ids[1], right, top)
            landmarks[ids[2]] = point(ids[2], right, bottom)
            landmarks[ids[3]] = point(ids[3], left, bottom)
            contours[prefix + "bounding_box"] = ContourObservation(
                id = prefix + "bounding_box",
                vertexIds = ids,
                closed = true,
                confidence = null,
                confidenceSource = ConfidenceSource.UNAVAILABLE,
                backend = ObservationBackend.ML_KIT_FACE_DETECTION
            )
        }
        return SubjectObservation(
            subjectCount = faceCount,
            faceCount = faceCount,
            bodyCount = 0,
            landmarks = landmarks,
            regions = emptyMap(),
            contours = contours
        )
    }

    private fun emptyObservation() = SubjectObservation(
        subjectCount = 0,
        faceCount = 0,
        bodyCount = 0,
        landmarks = emptyMap(),
        regions = emptyMap()
    )

    private fun point(id: String, x: Float, y: Float) = LandmarkObservation(
        id = id,
        point = NormalizedPoint3D(x, y),
        confidence = null,
        confidenceSource = ConfidenceSource.UNAVAILABLE,
        visibility = VisibilityState.VISIBLE,
        backend = ObservationBackend.ML_KIT_FACE_DETECTION
    )
}
