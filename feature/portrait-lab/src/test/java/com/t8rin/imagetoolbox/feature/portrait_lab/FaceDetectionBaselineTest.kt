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

class FaceDetectionBaselineTest {

    @Test
    fun `one clear face produces exactly one full-image record`() {
        val raw = observation(faceCount = 1)

        val result = FaceDetectionBaseline.evaluate(raw)

        assertEquals(listOf(0), result.faces.map { it.faceIndex })
        assertEquals(1, result.faces.single().landmarks.count { it.id.contains("control") })
        assertNull(result.activeFaceIndex)
        assertSame(raw, result.fullImageObservation)
    }

    @Test
    fun `two faces produce exactly two independent bounding boxes`() {
        val result = FaceDetectionBaseline.evaluate(observation(faceCount = 2))

        assertEquals(listOf(0, 1), result.faces.map { it.faceIndex })
        assertEquals(2, result.faces.map { it.boundingBox }.distinct().size)
        assertTrue(result.faces.all { it.contours.any { contour -> contour.id.contains("bounding_box") } })
        assertNull(result.activeFaceIndex)
    }

    @Test
    fun `group image preserves every clear detector face without filtering`() {
        val raw = observation(faceCount = 4)

        val result = FaceDetectionBaseline.evaluate(raw)

        assertEquals(listOf(0, 1, 2, 3), result.faces.map { it.faceIndex })
        assertEquals(4, result.faces.sumOf { face -> face.landmarks.count { it.id.contains("control") } })
        assertEquals(8, result.faces.sumOf { it.contours.size })
        assertSame(raw, result.fullImageObservation)
        assertNull(result.activeFaceIndex)
    }

    @Test
    fun `image without a face produces zero records and no false active face`() {
        val raw = SubjectObservation(
            subjectCount = 0,
            faceCount = 0,
            bodyCount = 0,
            landmarks = emptyMap(),
            regions = emptyMap()
        )

        val result = FaceDetectionBaseline.evaluate(raw)

        assertTrue(result.faces.isEmpty())
        assertNull(result.activeFaceIndex)
        assertSame(raw, result.fullImageObservation)
    }

    @Test
    fun `baseline does not discard weak or partial detector points`() {
        val raw = observation(faceCount = 1, controlVisibility = VisibilityState.PARTIALLY_VISIBLE)

        val face = FaceDetectionBaseline.evaluate(raw).faces.single()

        assertTrue(face.landmarks.any {
            it.id == "face_0_control" && it.visibility == VisibilityState.PARTIALLY_VISIBLE
        })
        assertNull(face.confidence)
    }

    private fun observation(
        faceCount: Int,
        controlVisibility: VisibilityState = VisibilityState.VISIBLE
    ): SubjectObservation {
        val landmarks = linkedMapOf<String, LandmarkObservation>()
        val contours = linkedMapOf<String, ContourObservation>()

        repeat(faceCount) { faceIndex ->
            val left = 0.04f + faceIndex * 0.22f
            val right = left + 0.16f
            val top = 0.15f
            val bottom = 0.65f
            val prefix = "face_${faceIndex}_"
            val bboxIds = listOf("bbox_tl", "bbox_tr", "bbox_br", "bbox_bl")
                .map { prefix + it }
            landmarks[bboxIds[0]] = point(bboxIds[0], left, top)
            landmarks[bboxIds[1]] = point(bboxIds[1], right, top)
            landmarks[bboxIds[2]] = point(bboxIds[2], right, bottom)
            landmarks[bboxIds[3]] = point(bboxIds[3], left, bottom)
            landmarks[prefix + "control"] = point(
                id = prefix + "control",
                x = (left + right) / 2f,
                y = (top + bottom) / 2f,
                visibility = controlVisibility
            )
            contours[prefix + "bounding_box"] = contour(
                id = prefix + "bounding_box",
                vertexIds = bboxIds,
                closed = true
            )
            contours[prefix + "face_contour"] = contour(
                id = prefix + "face_contour",
                vertexIds = listOf(bboxIds[0], prefix + "control", bboxIds[2]),
                closed = false
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

    private fun point(
        id: String,
        x: Float,
        y: Float,
        visibility: VisibilityState = VisibilityState.VISIBLE
    ) = LandmarkObservation(
        id = id,
        point = NormalizedPoint3D(x, y),
        confidence = null,
        confidenceSource = ConfidenceSource.UNAVAILABLE,
        visibility = visibility,
        backend = ObservationBackend.ML_KIT_FACE_DETECTION
    )

    private fun contour(
        id: String,
        vertexIds: List<String>,
        closed: Boolean
    ) = ContourObservation(
        id = id,
        vertexIds = vertexIds,
        closed = closed,
        confidence = null,
        confidenceSource = ConfidenceSource.UNAVAILABLE,
        backend = ObservationBackend.ML_KIT_FACE_DETECTION
    )
}
