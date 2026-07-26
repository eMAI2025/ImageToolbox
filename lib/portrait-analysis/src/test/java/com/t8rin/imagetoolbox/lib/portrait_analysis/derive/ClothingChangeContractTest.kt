/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis.derive

import com.t8rin.imagetoolbox.lib.portrait_analysis.catalog.PortraitRegionId
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ConfidenceMask
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ObservationBackend
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SemanticMaskObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ClothingChangeContractTest {

    @Test
    fun `missing A1 device pass blocks clothing capability`() {
        val decision = ClothingChangeContract.evaluate(
            validEvidence().copy(a1DevicePass = false)
        )

        assertEquals(ClothingChangeContract.Status.BLOCKED, decision.status)
        assertNull(decision.request)
        assertTrue(
            ClothingChangeContract.Blocker.A1_DEVICE_PASS_MISSING in decision.blockers
        )
    }

    @Test
    fun `missing accepted body geometry blocks clothing capability`() {
        val decision = ClothingChangeContract.evaluate(
            validEvidence().copy(acceptedObservation = null)
        )

        assertEquals(ClothingChangeContract.Status.BLOCKED, decision.status)
        assertNull(decision.request)
        assertTrue(
            ClothingChangeContract.Blocker.ACCEPTED_OBSERVATION_MISSING in decision.blockers
        )
    }

    @Test
    fun `upper body request requires shoulder arms and waist regions`() {
        val evidence = validEvidence().copy(
            acceptedRegionIds = setOf(
                PortraitRegionId.SHOULDER_CONTOUR,
                PortraitRegionId.WAIST_CONTOUR
            )
        )

        val decision = ClothingChangeContract.evaluate(evidence)

        assertEquals(ClothingChangeContract.Status.BLOCKED, decision.status)
        assertTrue(
            ClothingChangeContract.Blocker.REQUIRED_BODY_REGION_NOT_ACCEPTED in decision.blockers
        )
    }

    @Test
    fun `unverified garment source is never promoted to adapter request`() {
        val decision = ClothingChangeContract.evaluate(
            validEvidence().copy(
                garmentSource = garment(verified = false)
            )
        )

        assertEquals(ClothingChangeContract.Status.BLOCKED, decision.status)
        assertNull(decision.request)
        assertTrue(
            ClothingChangeContract.Blocker.GARMENT_SOURCE_UNVERIFIED in decision.blockers
        )
    }

    @Test
    fun `accepted upper body evidence creates capability token without deformation`() {
        val evidence = validEvidence()

        val decision = ClothingChangeContract.evaluate(evidence)

        assertEquals(
            ClothingChangeContract.Status.READY_FOR_PLATFORM_ADAPTER,
            decision.status
        )
        assertTrue(decision.blockers.isEmpty())
        val request = requireNotNull(decision.request)
        assertSame(evidence.acceptedObservation, request.acceptedObservation)
        assertEquals(ClothingChangeContract.TargetZone.UPPER_BODY, request.targetZone)
        assertEquals("verified-shirt", request.garmentSource.sourceId)
        assertEquals("feature/test", request.provenanceBranch)
        assertEquals("abc123", request.provenanceCommit)
    }

    @Test
    fun `full body request fails when any accepted body region is absent`() {
        val evidence = validEvidence().copy(
            targetZone = ClothingChangeContract.TargetZone.FULL_BODY,
            acceptedRegionIds = allBodyRegions() - PortraitRegionId.LEG_CONTOURS
        )

        val decision = ClothingChangeContract.evaluate(evidence)

        assertEquals(ClothingChangeContract.Status.BLOCKED, decision.status)
        assertTrue(
            ClothingChangeContract.Blocker.REQUIRED_BODY_REGION_NOT_ACCEPTED in decision.blockers
        )
    }

    @Test
    fun `adapter provenance must match capability token exactly`() {
        val request = requireNotNull(
            ClothingChangeContract.evaluate(validEvidence()).request
        )

        assertTrue(
            ClothingChangeContract.verifyAdapterProvenance(
                request = request,
                adapterBranch = "feature/test",
                adapterCommit = "abc123"
            ).isEmpty()
        )
        assertEquals(
            setOf(ClothingChangeContract.Blocker.PROVENANCE_MISMATCH),
            ClothingChangeContract.verifyAdapterProvenance(
                request = request,
                adapterBranch = "feature/other",
                adapterCommit = "abc123"
            )
        )
    }

    private fun validEvidence() = ClothingChangeContract.Evidence(
        a1DevicePass = true,
        rawObservation = acceptedBody(),
        filteredObservation = acceptedBody(),
        acceptedObservation = acceptedBody(),
        acceptedRegionIds = setOf(
            PortraitRegionId.SHOULDER_CONTOUR,
            PortraitRegionId.ARM_CONTOURS,
            PortraitRegionId.WAIST_CONTOUR
        ),
        targetZone = ClothingChangeContract.TargetZone.UPPER_BODY,
        garmentSource = garment(verified = true),
        provenanceBranch = "feature/test",
        provenanceCommit = "abc123"
    )

    private fun acceptedBody(): SubjectObservation {
        val mask = SemanticMaskObservation(
            id = PortraitRegionId.SUBJECT_MASK,
            mask = ConfidenceMask(2, 2, floatArrayOf(1f, 1f, 1f, 1f)),
            backend = ObservationBackend.ML_KIT_SELFIE_SEGMENTATION
        )
        return SubjectObservation(
            subjectCount = 1,
            faceCount = 0,
            bodyCount = 1,
            landmarks = emptyMap(),
            regions = emptyMap(),
            masks = mapOf(PortraitRegionId.SUBJECT_MASK to mask)
        )
    }

    private fun garment(verified: Boolean) = ClothingChangeContract.GarmentSource(
        sourceId = "verified-shirt",
        kind = ClothingChangeContract.GarmentSourceKind.VERIFIED_ASSET_REFERENCE,
        verified = verified,
        width = null,
        height = null
    )

    private fun allBodyRegions() = setOf(
        PortraitRegionId.SHOULDER_CONTOUR,
        PortraitRegionId.ARM_CONTOURS,
        PortraitRegionId.WAIST_CONTOUR,
        PortraitRegionId.HIP_CONTOUR,
        PortraitRegionId.LEG_CONTOURS
    )
}
