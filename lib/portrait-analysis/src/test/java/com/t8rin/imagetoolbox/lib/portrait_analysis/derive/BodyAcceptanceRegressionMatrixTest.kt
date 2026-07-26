/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyAcceptanceRegressionMatrixTest {

    @Test
    fun `full body with coherent pose and mask keeps every region available`() {
        val result = gate(fullBody(), components(primary()))

        assertTrue(result.regions.values.all { it.available })
        assertEquals(1, result.filteredMaskComponents.size)
    }

    @Test
    fun `upper body blocks legs without blocking torso or arms`() {
        val source = fullBody().copy(
            landmarks = fullBody().landmarks - setOf(
                PortraitLandmarkId.LEFT_KNEE,
                PortraitLandmarkId.RIGHT_KNEE,
                PortraitLandmarkId.LEFT_ANKLE,
                PortraitLandmarkId.RIGHT_ANKLE
            )
        )
        val result = gate(source, components(primary()))

        assertFalse(result.regions.getValue(PortraitRegionId.LEG_CONTOURS).available)
        assertTrue(result.regions.getValue(PortraitRegionId.WAIST_CONTOUR).available)
        assertTrue(result.regions.getValue(PortraitRegionId.ARM_CONTOURS).available)
    }

    @Test
    fun `cropped legs are off frame and cannot produce active leg geometry`() {
        val source = fullBody()
            .replace(point(PortraitLandmarkId.LEFT_ANKLE, 0.38f, 0.94f, VisibilityState.OUTSIDE_FRAME))
            .replace(point(PortraitLandmarkId.RIGHT_ANKLE, 0.62f, 0.94f, VisibilityState.OUTSIDE_FRAME))
        val result = gate(source, components(primary()))

        assertFalse(result.regions.getValue(PortraitRegionId.LEG_CONTOURS).available)
        assertTrue(result.regions.getValue(PortraitRegionId.HIP_CONTOUR).available)
    }

    @Test
    fun `one invisible hand blocks arms but preserves independent torso and legs`() {
        val source = fullBody().replace(
            point(PortraitLandmarkId.LEFT_WRIST, 0.25f, 0.58f, VisibilityState.PARTIALLY_VISIBLE)
        )
        val result = gate(source, components(primary()))

        assertFalse(result.regions.getValue(PortraitRegionId.ARM_CONTOURS).available)
        assertTrue(result.regions.getValue(PortraitRegionId.WAIST_CONTOUR).available)
        assertTrue(result.regions.getValue(PortraitRegionId.LEG_CONTOURS).available)
    }

    @Test
    fun `crossed arms and phone contamination block only attributed regions`() {
        val result = gate(
            fullBody(),
            components(primary()),
            BodyMaskPoseConsistencyGate.LocalConsistencyEvidence(
                crossedArmContaminatedRegionIds = setOf(PortraitRegionId.WAIST_CONTOUR),
                phoneContaminatedRegionIds = setOf(PortraitRegionId.ARM_CONTOURS)
            )
        )

        assertFalse(result.regions.getValue(PortraitRegionId.WAIST_CONTOUR).available)
        assertFalse(result.regions.getValue(PortraitRegionId.ARM_CONTOURS).available)
        assertTrue(result.regions.getValue(PortraitRegionId.HIP_CONTOUR).available)
        assertTrue(result.regions.getValue(PortraitRegionId.LEG_CONTOURS).available)
    }

    @Test
    fun `detached halo and background smear remain raw but never active`() {
        val result = gate(fullBody(), components(primary(), halo(), smear()))

        assertEquals(3, result.rawMaskComponents.size)
        assertEquals(listOf("person"), result.filteredMaskComponents.map { it.id })
        assertTrue(result.regions.values.all { it.available })
    }

    @Test
    fun `unlocalized second person contamination fails closed for all regions`() {
        val result = gate(fullBody(), components(primary(), secondPerson()))

        assertTrue(result.regions.values.none { it.available })
        assertTrue(result.regions.values.all {
            BodyMaskPoseConsistencyGate.RejectionReason.MULTI_PERSON_CONTAMINATION_UNLOCALIZED in it.reasons
        })
        assertEquals(2, result.rawMaskComponents.size)
        assertEquals(1, result.filteredMaskComponents.size)
    }

    @Test
    fun `detached background element cannot become editable silhouette`() {
        val mask = components(primary(), smear())
        val result = gate(fullBody(), mask)

        assertTrue(mask.rejectedComponents.any {
            BodyMaskComponentGate.RejectionReason.DISCONNECTED_BACKGROUND_SMEAR in it.reasons
        })
        assertFalse(result.filteredMaskComponents.any { it.id == "background_smear" })
    }

    private fun gate(
        observation: SubjectObservation,
        components: BodyMaskComponentGate.Assessment,
        evidence: BodyMaskPoseConsistencyGate.LocalConsistencyEvidence =
            BodyMaskPoseConsistencyGate.LocalConsistencyEvidence()
    ): BodyMaskPoseConsistencyGate.Assessment = BodyMaskPoseConsistencyGate.evaluate(
        observation = observation,
        visibility = BodyLandmarkVisibilityGate.evaluate(observation),
        maskComponents = components,
        evidence = evidence
    )

    private fun components(vararg items: BodyMaskComponentGate.ComponentEvidence) =
        BodyMaskComponentGate.evaluate(items.toList())

    private fun primary() = component("person", 20_000, 0.18f, 0.10f, 0.82f, 0.98f, 4, true, true)

    private fun halo() = component("halo", 120, 0.40f, 0.01f, 0.60f, 0.08f, 0, false, false, true)

    private fun smear() = component("background_smear", 160, 0.02f, 0.35f, 0.10f, 0.65f, 0, false, false)

    private fun secondPerson() = component(
        id = "second_person",
        pixels = 5_000,
        left = 0.72f,
        top = 0.10f,
        right = 0.98f,
        bottom = 0.90f,
        torsoAnchors = 0,
        overlapsTorso = false,
        connected = false,
        independentPersonAnchors = 5
    )

    private fun component(
        id: String,
        pixels: Int,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        torsoAnchors: Int,
        overlapsTorso: Boolean,
        connected: Boolean,
        aboveHead: Boolean = false,
        independentPersonAnchors: Int = 0
    ) = BodyMaskComponentGate.ComponentEvidence(
        id = id,
        pixelCount = pixels,
        bounds = BodyMaskComponentGate.NormalizedBounds(left, top, right, bottom),
        torsoAnchorCount = torsoAnchors,
        overlapsTorsoCorridor = overlapsTorso,
        connectedToPrimary = connected,
        aboveVerifiedHead = aboveHead,
        independentPersonAnchorCount = independentPersonAnchors
    )

    private fun fullBody(): SubjectObservation = SubjectObservation(
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

    private fun point(
        id: String,
        x: Float,
        y: Float,
        visibility: VisibilityState = VisibilityState.VISIBLE
    ) = LandmarkObservation(
        id = id,
        point = NormalizedPoint3D(x, y),
        confidence = 0.95f,
        confidenceSource = ConfidenceSource.DIRECT,
        visibility = visibility,
        backend = ObservationBackend.ML_KIT_POSE
    )
}
