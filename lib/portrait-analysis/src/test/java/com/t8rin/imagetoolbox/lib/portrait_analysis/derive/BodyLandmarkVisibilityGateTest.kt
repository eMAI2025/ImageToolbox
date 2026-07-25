/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis.derive

import com.t8rin.imagetoolbox.lib.portrait_analysis.catalog.PortraitLandmarkId
import com.t8rin.imagetoolbox.lib.portrait_analysis.catalog.PortraitRegionId
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ConfidenceSource
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ContourObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.LandmarkObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.NormalizedPoint3D
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ObservationBackend
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.VisibilityState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyLandmarkVisibilityGateTest {

    @Test
    fun `complete in-frame pose exposes required segments and regions`() {
        val result = BodyLandmarkVisibilityGate.evaluate(observation())

        assertEquals(12, result.visibleCount)
        assertTrue(result.segments.values.all { it.available })
        assertTrue(result.regions.values.all { it.available })
    }

    @Test
    fun `confidence below floor blocks only dependent segments and regions`() {
        val source = observation().replace(
            point(PortraitLandmarkId.LEFT_WRIST, 0.25f, 0.58f, confidence = 0.49f)
        )
        val result = BodyLandmarkVisibilityGate.evaluate(source)

        assertEquals(
            BodyLandmarkVisibilityGate.BodyLandmarkEvidenceState.LOW_CONFIDENCE,
            result.landmarks.getValue(PortraitLandmarkId.LEFT_WRIST).state
        )
        assertFalse(result.segments.getValue("left_forearm").available)
        assertTrue(result.segments.getValue("left_upper_arm").available)
        assertFalse(result.regions.getValue(PortraitRegionId.ARM_CONTOURS).available)
        assertTrue(result.regions.getValue(PortraitRegionId.LEG_CONTOURS).available)
    }

    @Test
    fun `confidence exactly at floor remains visible`() {
        val source = observation().replace(
            point(PortraitLandmarkId.RIGHT_WRIST, 0.75f, 0.58f, confidence = 0.50f)
        )
        val result = BodyLandmarkVisibilityGate.evaluate(source)

        assertEquals(
            BodyLandmarkVisibilityGate.BodyLandmarkEvidenceState.VISIBLE,
            result.landmarks.getValue(PortraitLandmarkId.RIGHT_WRIST).state
        )
        assertTrue(result.segments.getValue("right_forearm").available)
    }

    @Test
    fun `outside-frame visibility blocks dependent geometry`() {
        val source = observation().replace(
            point(
                PortraitLandmarkId.LEFT_ANKLE,
                0.35f,
                0.95f,
                visibility = VisibilityState.OUTSIDE_FRAME
            )
        )
        val result = BodyLandmarkVisibilityGate.evaluate(source)
        val active = BodyLandmarkVisibilityGate.filterForActiveGeometry(source, result)

        assertEquals(
            BodyLandmarkVisibilityGate.BodyLandmarkEvidenceState.OFF_FRAME,
            result.landmarks.getValue(PortraitLandmarkId.LEFT_ANKLE).state
        )
        assertFalse(result.segments.getValue("left_calf").available)
        assertFalse(PortraitLandmarkId.LEFT_ANKLE in active.landmarks)
        assertFalse("left_calf_raw" in active.contours)
    }

    @Test
    fun `missing landmark is not reconstructed`() {
        val source = observation().copy(
            landmarks = observation().landmarks - PortraitLandmarkId.RIGHT_KNEE
        )
        val result = BodyLandmarkVisibilityGate.evaluate(source)

        assertEquals(
            BodyLandmarkVisibilityGate.BodyLandmarkEvidenceState.MISSING,
            result.landmarks.getValue(PortraitLandmarkId.RIGHT_KNEE).state
        )
        assertFalse(result.segments.getValue("right_thigh").available)
        assertFalse(result.segments.getValue("right_calf").available)
    }

    @Test
    fun `partially visible landmark fails closed`() {
        val source = observation().replace(
            point(
                PortraitLandmarkId.LEFT_ELBOW,
                0.28f,
                0.42f,
                visibility = VisibilityState.PARTIALLY_VISIBLE
            )
        )
        val result = BodyLandmarkVisibilityGate.evaluate(source)

        assertEquals(
            BodyLandmarkVisibilityGate.BodyLandmarkEvidenceState.LOW_CONFIDENCE,
            result.landmarks.getValue(PortraitLandmarkId.LEFT_ELBOW).state
        )
        assertFalse(result.segments.getValue("left_upper_arm").available)
        assertFalse(result.segments.getValue("left_forearm").available)
    }

    private fun observation(): SubjectObservation {
        val landmarks = listOf(
            point(PortraitLandmarkId.LEFT_SHOULDER, 0.35f, 0.25f),
            point(PortraitLandmarkId.RIGHT_SHOULDER, 0.65f, 0.25f),
            point(PortraitLandmarkId.LEFT_ELBOW, 0.28f, 0.42f),
            point(PortraitLandmarkId.RIGHT_ELBOW, 0.72f, 0.42f),
            point(PortraitLandmarkId.LEFT_WRIST, 0.25f, 0.58f),
            point(PortraitLandmarkId.RIGHT_WRIST, 0.75f, 0.58f),
            point(PortraitLandmarkId.LEFT_HIP, 0.42f, 0.55f),
            point(PortraitLandmarkId.RIGHT_HIP, 0.58f, 0.55f),
            point(PortraitLandmarkId.LEFT_KNEE, 0.40f, 0.75f),
            point(PortraitLandmarkId.RIGHT_KNEE, 0.60f, 0.75f),
            point(PortraitLandmarkId.LEFT_ANKLE, 0.38f, 0.94f),
            point(PortraitLandmarkId.RIGHT_ANKLE, 0.62f, 0.94f)
        ).associateBy { it.id }
        return SubjectObservation(
            subjectCount = 1,
            faceCount = 0,
            bodyCount = 1,
            landmarks = landmarks,
            regions = emptyMap(),
            contours = mapOf(
                "left_calf_raw" to ContourObservation(
                    id = "left_calf_raw",
                    vertexIds = listOf(PortraitLandmarkId.LEFT_KNEE, PortraitLandmarkId.LEFT_ANKLE),
                    closed = false,
                    confidence = 0.95f,
                    confidenceSource = ConfidenceSource.DERIVED,
                    backend = ObservationBackend.ML_KIT_POSE
                )
            )
        )
    }

    private fun SubjectObservation.replace(landmark: LandmarkObservation): SubjectObservation =
        copy(landmarks = landmarks + (landmark.id to landmark))

    private fun point(
        id: String,
        x: Float,
        y: Float,
        confidence: Float = 0.95f,
        visibility: VisibilityState = VisibilityState.VISIBLE
    ) = LandmarkObservation(
        id = id,
        point = NormalizedPoint3D(x, y),
        confidence = confidence,
        confidenceSource = ConfidenceSource.DIRECT,
        visibility = visibility,
        backend = ObservationBackend.ML_KIT_POSE
    )
}
