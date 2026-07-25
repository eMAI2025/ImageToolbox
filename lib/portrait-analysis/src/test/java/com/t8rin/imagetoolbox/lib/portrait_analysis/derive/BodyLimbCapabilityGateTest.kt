/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package com.t8rin.imagetoolbox.lib.portrait_analysis.derive

import com.t8rin.imagetoolbox.lib.portrait_analysis.catalog.PortraitLandmarkId
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ConfidenceSource
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ContourObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.LandmarkObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.NormalizedPoint3D
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ObservationBackend
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.VisibilityState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyLimbCapabilityGateTest {

    @Test
    fun `lowered arm with visible wrist and fingers exposes all capabilities`() {
        val result = BodyLimbCapabilityGate.evaluate(observation())
        assertTrue(result.capability(BodyLimbCapabilityGate.Side.LEFT, BodyLimbCapabilityGate.Part.UPPER_ARM).available)
        assertTrue(result.capability(BodyLimbCapabilityGate.Side.LEFT, BodyLimbCapabilityGate.Part.FOREARM).available)
        assertTrue(result.capability(BodyLimbCapabilityGate.Side.LEFT, BodyLimbCapabilityGate.Part.HAND).available)
    }

    @Test
    fun `crossed arm remains available when all direct landmarks are visible`() {
        val source = observation().replace(point(PortraitLandmarkId.LEFT_WRIST, 0.62f, 0.46f))
        val result = BodyLimbCapabilityGate.evaluate(source)
        assertTrue(result.capability(BodyLimbCapabilityGate.Side.LEFT, BodyLimbCapabilityGate.Part.FOREARM).available)
        assertTrue(result.capability(BodyLimbCapabilityGate.Side.LEFT, BodyLimbCapabilityGate.Part.HAND).available)
    }

    @Test
    fun `phone occlusion blocks hand but not upper arm`() {
        val source = observation()
            .replace(point("left_index", 0.52f, 0.50f, confidence = 0.20f, visibility = VisibilityState.OCCLUDED))
            .replace(point("left_pinky", 0.50f, 0.52f, confidence = 0.20f, visibility = VisibilityState.OCCLUDED))
            .replace(point("left_thumb", 0.54f, 0.51f, confidence = 0.20f, visibility = VisibilityState.OCCLUDED))
        val result = BodyLimbCapabilityGate.evaluate(source)
        assertTrue(result.capability(BodyLimbCapabilityGate.Side.LEFT, BodyLimbCapabilityGate.Part.UPPER_ARM).available)
        assertTrue(result.capability(BodyLimbCapabilityGate.Side.LEFT, BodyLimbCapabilityGate.Part.FOREARM).available)
        assertFalse(result.capability(BodyLimbCapabilityGate.Side.LEFT, BodyLimbCapabilityGate.Part.HAND).available)
    }

    @Test
    fun `cropped wrist blocks forearm and hand without synthetic endpoint`() {
        val source = observation().replace(
            point(PortraitLandmarkId.LEFT_WRIST, 0.15f, 0.98f, visibility = VisibilityState.OUTSIDE_FRAME)
        ).copy(
            contours = mapOf(
                "derived_left_hand_circle" to contour("derived_left_hand_circle", listOf(PortraitLandmarkId.LEFT_WRIST))
            )
        )
        val visibility = BodyLandmarkVisibilityGate.evaluate(source)
        val result = BodyLimbCapabilityGate.evaluate(source, visibility)
        val active = BodyLimbCapabilityGate.filterForActiveGeometry(source, result)
        assertTrue(result.capability(BodyLimbCapabilityGate.Side.LEFT, BodyLimbCapabilityGate.Part.UPPER_ARM).available)
        assertFalse(result.capability(BodyLimbCapabilityGate.Side.LEFT, BodyLimbCapabilityGate.Part.FOREARM).available)
        assertFalse(result.capability(BodyLimbCapabilityGate.Side.LEFT, BodyLimbCapabilityGate.Part.HAND).available)
        assertFalse("derived_left_hand_circle" in active.contours)
    }

    @Test
    fun `missing fingers block only hand capability`() {
        val source = observation().copy(
            landmarks = observation().landmarks - setOf("right_index", "right_pinky", "right_thumb")
        )
        val result = BodyLimbCapabilityGate.evaluate(source)
        assertTrue(result.capability(BodyLimbCapabilityGate.Side.RIGHT, BodyLimbCapabilityGate.Part.UPPER_ARM).available)
        assertTrue(result.capability(BodyLimbCapabilityGate.Side.RIGHT, BodyLimbCapabilityGate.Part.FOREARM).available)
        assertFalse(result.capability(BodyLimbCapabilityGate.Side.RIGHT, BodyLimbCapabilityGate.Part.HAND).available)
    }

    private fun observation(): SubjectObservation {
        val points = listOf(
            point(PortraitLandmarkId.LEFT_SHOULDER, 0.35f, 0.25f),
            point(PortraitLandmarkId.RIGHT_SHOULDER, 0.65f, 0.25f),
            point(PortraitLandmarkId.LEFT_ELBOW, 0.28f, 0.45f),
            point(PortraitLandmarkId.RIGHT_ELBOW, 0.72f, 0.45f),
            point(PortraitLandmarkId.LEFT_WRIST, 0.25f, 0.65f),
            point(PortraitLandmarkId.RIGHT_WRIST, 0.75f, 0.65f),
            point(PortraitLandmarkId.LEFT_HIP, 0.42f, 0.58f),
            point(PortraitLandmarkId.RIGHT_HIP, 0.58f, 0.58f),
            point(PortraitLandmarkId.LEFT_KNEE, 0.40f, 0.78f),
            point(PortraitLandmarkId.RIGHT_KNEE, 0.60f, 0.78f),
            point(PortraitLandmarkId.LEFT_ANKLE, 0.38f, 0.95f),
            point(PortraitLandmarkId.RIGHT_ANKLE, 0.62f, 0.95f),
            point("left_index", 0.22f, 0.69f), point("left_pinky", 0.24f, 0.70f), point("left_thumb", 0.27f, 0.68f),
            point("right_index", 0.78f, 0.69f), point("right_pinky", 0.76f, 0.70f), point("right_thumb", 0.73f, 0.68f)
        ).associateBy { it.id }
        return SubjectObservation(1, 0, 1, points, emptyMap())
    }

    private fun SubjectObservation.replace(value: LandmarkObservation) = copy(landmarks = landmarks + (value.id to value))

    private fun point(
        id: String,
        x: Float,
        y: Float,
        confidence: Float = 0.95f,
        visibility: VisibilityState = VisibilityState.VISIBLE
    ) = LandmarkObservation(
        id, NormalizedPoint3D(x, y), confidence, ConfidenceSource.DIRECT, visibility, ObservationBackend.ML_KIT_POSE
    )

    private fun contour(id: String, vertices: List<String>) = ContourObservation(
        id, vertices, false, 0.9f, ConfidenceSource.DERIVED, ObservationBackend.ML_KIT_POSE
    )
}
