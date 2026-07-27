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
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveFaceDetailAnalysisContractTest {

    @Test
    fun `multiple faces never imply automatic index zero selection`() {
        val baseline = baseline(faceCount = 2)

        val decision = ActiveFaceDetailAnalysisContract.evaluate(
            ActiveFaceDetailAnalysisContract.Evidence(
                baseline = baseline,
                explicitlySelectedFaceIndex = null
            )
        )

        assertEquals(
            ActiveFaceDetailAnalysisContract.Status.WAITING_FOR_EXPLICIT_SELECTION,
            decision.status
        )
        assertEquals(2, decision.baselineFaceCount)
        assertNull(decision.activeFaceIndex)
        assertNull(decision.request)
        assertTrue(decision.blockers.isEmpty())
    }

    @Test
    fun `explicit second face selection creates detail request without changing baseline count`() {
        val baseline = baseline(faceCount = 3)

        val decision = ActiveFaceDetailAnalysisContract.evaluate(
            ActiveFaceDetailAnalysisContract.Evidence(
                baseline = baseline,
                explicitlySelectedFaceIndex = 1
            )
        )

        assertEquals(
            ActiveFaceDetailAnalysisContract.Status.READY_FOR_OPTIONAL_CROP_AND_DETAIL,
            decision.status
        )
        assertEquals(1, decision.activeFaceIndex)
        assertEquals(3, decision.baselineFaceCount)
        val request = requireNotNull(decision.request)
        assertEquals(1, request.selectedFace.faceIndex)
        assertEquals(3, request.baselineFaceCount)
        assertEquals(request.selectedFace.boundingBox, request.optionalCropBounds)
        assertSame(
            baseline.faces.single { it.faceIndex == 1 },
            request.selectedFace
        )
        assertEquals(3, baseline.faces.size)
    }

    @Test
    fun `selection outside frozen baseline is blocked without fallback`() {
        val decision = ActiveFaceDetailAnalysisContract.evaluate(
            ActiveFaceDetailAnalysisContract.Evidence(
                baseline = baseline(faceCount = 1),
                explicitlySelectedFaceIndex = 4
            )
        )

        assertEquals(ActiveFaceDetailAnalysisContract.Status.BLOCKED, decision.status)
        assertNull(decision.activeFaceIndex)
        assertNull(decision.request)
        assertEquals(
            setOf(ActiveFaceDetailAnalysisContract.Blocker.SELECTED_FACE_NOT_IN_BASELINE),
            decision.blockers
        )
    }

    @Test
    fun `empty baseline cannot start detail analysis`() {
        val decision = ActiveFaceDetailAnalysisContract.evaluate(
            ActiveFaceDetailAnalysisContract.Evidence(
                baseline = baseline(faceCount = 0),
                explicitlySelectedFaceIndex = null
            )
        )

        assertEquals(ActiveFaceDetailAnalysisContract.Status.BLOCKED, decision.status)
        assertNull(decision.activeFaceIndex)
        assertNull(decision.request)
        assertEquals(0, decision.baselineFaceCount)
        assertEquals(
            setOf(ActiveFaceDetailAnalysisContract.Blocker.NO_FACES_DETECTED),
            decision.blockers
        )
    }

    @Test
    fun `optional crop seed is downstream metadata and cannot redefine detected face inventory`() {
        val baseline = baseline(faceCount = 4)

        val request = requireNotNull(
            ActiveFaceDetailAnalysisContract.evaluate(
                ActiveFaceDetailAnalysisContract.Evidence(
                    baseline = baseline,
                    explicitlySelectedFaceIndex = 3
                )
            ).request
        )

        assertEquals(4, request.baselineFaceCount)
        assertEquals(4, baseline.faces.size)
        assertEquals(3, request.selectedFace.faceIndex)
        assertEquals(request.selectedFace.boundingBox, request.optionalCropBounds)
    }

    private fun baseline(faceCount: Int): FaceDetectionBaseline.Result =
        FaceDetectionBaseline.evaluate(observation(faceCount))

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

    private fun point(id: String, x: Float, y: Float) = LandmarkObservation(
        id = id,
        point = NormalizedPoint3D(x, y),
        confidence = null,
        confidenceSource = ConfidenceSource.UNAVAILABLE,
        visibility = VisibilityState.VISIBLE,
        backend = ObservationBackend.ML_KIT_FACE_DETECTION
    )
}