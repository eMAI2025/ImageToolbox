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
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.LandmarkObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.NormalizedPoint3D
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ObservationBackend
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.VisibilityState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyMaskPoseConsistencyGateTest {

    @Test
    fun `full body inside verified component keeps all regions available`() {
        val result = evaluate(observation(), components(primary()))

        assertTrue(result.regions.values.all { it.available })
        assertTrue(result.segments.values.all { it.available })
    }

    @Test
    fun `landmark outside component blocks only dependent regions`() {
        val source = observation().replace(point(PortraitLandmarkId.LEFT_WRIST, 0.05f, 0.58f))
        val result = evaluate(source, components(primary()))

        assertFalse(result.segments.getValue("left_forearm").available)
        assertFalse(result.regions.getValue(PortraitRegionId.ARM_CONTOURS).available)
        assertTrue(result.regions.getValue(PortraitRegionId.LEG_CONTOURS).available)
        assertTrue(result.regions.getValue(PortraitRegionId.WAIST_CONTOUR).available)
    }

    @Test
    fun `crossed arms block torso region without disabling legs`() {
        val result = evaluate(
            observation(),
            components(primary()),
            BodyMaskPoseConsistencyGate.LocalConsistencyEvidence(
                crossedArmContaminatedRegionIds = setOf(PortraitRegionId.WAIST_CONTOUR)
            )
        )

        assertFalse(result.regions.getValue(PortraitRegionId.WAIST_CONTOUR).available)
        assertTrue(result.regions.getValue(PortraitRegionId.HIP_CONTOUR).available)
        assertTrue(result.regions.getValue(PortraitRegionId.LEG_CONTOURS).available)
    }

    @Test
    fun `phone contamination blocks only attributed arm region`() {
        val result = evaluate(
            observation(),
            components(primary()),
            BodyMaskPoseConsistencyGate.LocalConsistencyEvidence(
                phoneContaminatedRegionIds = setOf(PortraitRegionId.ARM_CONTOURS)
            )
        )

        assertFalse(result.regions.getValue(PortraitRegionId.ARM_CONTOURS).available)
        assertTrue(result.regions.getValue(PortraitRegionId.WAIST_CONTOUR).available)
    }

    @Test
    fun `legs touching ambiguity blocks legs but preserves torso`() {
        val result = evaluate(
            observation(),
            components(primary()),
            BodyMaskPoseConsistencyGate.LocalConsistencyEvidence(legsTouchingAmbiguous = true)
        )

        assertFalse(result.regions.getValue(PortraitRegionId.LEG_CONTOURS).available)
        assertTrue(result.regions.getValue(PortraitRegionId.WAIST_CONTOUR).available)
        assertTrue(result.regions.getValue(PortraitRegionId.HIP_CONTOUR).available)
    }

    @Test
    fun `localized second person contamination blocks only affected region`() {
        val result = evaluate(
            observation(),
            components(primary(), secondPerson()),
            BodyMaskPoseConsistencyGate.LocalConsistencyEvidence(
                multiPersonContaminatedRegionIds = setOf(PortraitRegionId.SHOULDER_CONTOUR)
            )
        )

        assertFalse(result.regions.getValue(PortraitRegionId.SHOULDER_CONTOUR).available)
        assertTrue(result.regions.getValue(PortraitRegionId.LEG_CONTOURS).available)
        assertTrue(result.rawMaskComponents.size == 2)
        assertTrue(result.filteredMaskComponents.size == 1)
    }

    @Test
    fun `missing mask evidence fails closed for every body region`() {
        val visibility = BodyLandmarkVisibilityGate.evaluate(observation())
        val result = BodyMaskPoseConsistencyGate.evaluate(
            observation = observation(),
            visibility = visibility,
            maskComponents = BodyMaskComponentGate.Assessment.empty()
        )

        assertTrue(result.regions.values.none { it.available })
        assertTrue(result.regions.values.all {
            BodyMaskPoseConsistencyGate.RejectionReason.SEGMENT_MASK_SUPPORT_MISSING in it.reasons
        })
    }

    private fun evaluate(
        source: SubjectObservation,
        components: BodyMaskComponentGate.Assessment,
        evidence: BodyMaskPoseConsistencyGate.LocalConsistencyEvidence =
            BodyMaskPoseConsistencyGate.LocalConsistencyEvidence()
    ): BodyMaskPoseConsistencyGate.Assessment = BodyMaskPoseConsistencyGate.evaluate(
        observation = source,
        visibility = BodyLandmarkVisibilityGate.evaluate(source),
        maskComponents = components,
        evidence = evidence
    )

    private fun components(vararg evidence: BodyMaskComponentGate.ComponentEvidence) =
        BodyMaskComponentGate.evaluate(evidence.toList())

    private fun primary() = BodyMaskComponentGate.ComponentEvidence(
        id = "person",
        pixelCount = 20_000,
        bounds = BodyMaskComponentGate.NormalizedBounds(0.18f, 0.10f, 0.82f, 0.98f),
        torsoAnchorCount = 4,
        overlapsTorsoCorridor = true,
        connectedToPrimary = true,
        aboveVerifiedHead = false
    )

    private fun secondPerson() = BodyMaskComponentGate.ComponentEvidence(
        id = "second_person",
        pixelCount = 5_000,
        bounds = BodyMaskComponentGate.NormalizedBounds(0.72f, 0.10f, 0.98f, 0.90f),
        torsoAnchorCount = 0,
        overlapsTorsoCorridor = false,
        connectedToPrimary = false,
        aboveVerifiedHead = false,
        independentPersonAnchorCount = 5
    )

    private fun observation(): SubjectObservation = SubjectObservation(
        subjectCount = 1,
        faceCount = 0,
        bodyCount = 1,
        landmarks = listOf(
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
        ).associateBy { it.id },
        regions = emptyMap()
    )

    private fun SubjectObservation.replace(landmark: LandmarkObservation): SubjectObservation =
        copy(landmarks = landmarks + (landmark.id to landmark))

    private fun point(id: String, x: Float, y: Float) = LandmarkObservation(
        id = id,
        point = NormalizedPoint3D(x, y),
        confidence = 0.95f,
        confidenceSource = ConfidenceSource.DIRECT,
        visibility = VisibilityState.VISIBLE,
        backend = ObservationBackend.ML_KIT_POSE
    )
}
