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

class PortraitFilterContractTest {

    @Test
    fun `missing device pass blocks every filter capability`() {
        val decision = PortraitFilterContract.evaluate(
            globalEvidence().copy(a1DevicePass = false)
        )

        assertEquals(PortraitFilterContract.Status.BLOCKED, decision.status)
        assertNull(decision.request)
        assertTrue(
            PortraitFilterContract.Blocker.A1_DEVICE_PASS_MISSING in decision.blockers
        )
    }

    @Test
    fun `global exposure is ready only with complete oriented evidence`() {
        val evidence = globalEvidence()
        val decision = PortraitFilterContract.evaluate(evidence)

        assertEquals(
            PortraitFilterContract.Status.READY_FOR_PLATFORM_ADAPTER,
            decision.status
        )
        val request = requireNotNull(decision.request)
        assertSame(evidence.acceptedObservation, request.acceptedObservation)
        assertEquals(PortraitFilterContract.Operation.EXPOSURE, request.operation)
        assertEquals(0.25f, request.value)
    }

    @Test
    fun `subject local filter requires accepted mask and exact regions`() {
        val decision = PortraitFilterContract.evaluate(
            globalEvidence().copy(
                operation = PortraitFilterContract.Operation.EYE_BRIGHTNESS,
                value = 0.4f,
                acceptedRegionIds = setOf(PortraitRegionId.LEFT_EYE_REGION)
            )
        )

        assertEquals(PortraitFilterContract.Status.BLOCKED, decision.status)
        assertTrue(
            PortraitFilterContract.Blocker.REQUIRED_REGION_NOT_ACCEPTED in decision.blockers
        )
    }

    @Test
    fun `geometric operation request is rejected even for accepted evidence`() {
        val decision = PortraitFilterContract.evaluate(
            globalEvidence().copy(requestsGeometryChange = true)
        )

        assertEquals(PortraitFilterContract.Status.BLOCKED, decision.status)
        assertTrue(
            PortraitFilterContract.Blocker.GEOMETRIC_OPERATION_REQUESTED in decision.blockers
        )
    }

    @Test
    fun `non finite and out of domain values are rejected`() {
        val nonFinite = PortraitFilterContract.evaluate(
            globalEvidence().copy(value = Float.NaN)
        )
        val outOfRange = PortraitFilterContract.evaluate(
            globalEvidence().copy(value = 1.01f)
        )

        assertTrue(PortraitFilterContract.Blocker.VALUE_NOT_FINITE in nonFinite.blockers)
        assertTrue(PortraitFilterContract.Blocker.VALUE_OUTSIDE_DOMAIN in outOfRange.blockers)
    }

    @Test
    fun `adapter provenance must match exactly`() {
        val request = requireNotNull(
            PortraitFilterContract.evaluate(globalEvidence()).request
        )

        assertTrue(
            PortraitFilterContract.verifyAdapterProvenance(
                request,
                adapterBranch = "feature/test",
                adapterCommit = "abc123"
            ).isEmpty()
        )
        assertEquals(
            setOf(PortraitFilterContract.Blocker.PROVENANCE_MISMATCH),
            PortraitFilterContract.verifyAdapterProvenance(
                request,
                adapterBranch = "feature/other",
                adapterCommit = "abc123"
            )
        )
    }

    private fun globalEvidence() = PortraitFilterContract.Evidence(
        a1DevicePass = true,
        rawObservation = acceptedObservation(),
        filteredObservation = acceptedObservation(),
        acceptedObservation = acceptedObservation(),
        acceptedRegionIds = emptySet(),
        sourceWidth = 1080,
        sourceHeight = 1920,
        sourceOrientationApplied = true,
        operation = PortraitFilterContract.Operation.EXPOSURE,
        value = 0.25f,
        requestsGeometryChange = false,
        provenanceBranch = "feature/test",
        provenanceCommit = "abc123"
    )

    private fun acceptedObservation(): SubjectObservation {
        val mask = SemanticMaskObservation(
            id = PortraitRegionId.SUBJECT_MASK,
            mask = ConfidenceMask(2, 2, floatArrayOf(1f, 1f, 1f, 1f)),
            backend = ObservationBackend.ML_KIT_SELFIE_SEGMENTATION
        )
        return SubjectObservation(
            subjectCount = 1,
            faceCount = 1,
            bodyCount = 1,
            landmarks = emptyMap(),
            regions = emptyMap(),
            masks = mapOf(PortraitRegionId.SUBJECT_MASK to mask)
        )
    }
}
