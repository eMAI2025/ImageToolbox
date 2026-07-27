/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis.derive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PostacMasterPipelineUnlockContractTest {

    @Test
    fun `missing device pass and iteration acceptance keep module locked`() {
        val decision = PostacMasterPipelineUnlockContract.evaluate(
            validEvidence().copy(a1DevicePass = false, iteration32Accepted = false)
        )

        assertEquals(PostacMasterPipelineUnlockContract.Status.LOCKED, decision.status)
        assertNull(decision.capability)
        assertTrue(
            PostacMasterPipelineUnlockContract.Blocker.A1_DEVICE_PASS_MISSING in decision.blockers
        )
        assertTrue(
            PostacMasterPipelineUnlockContract.Blocker.ITERATION_32_NOT_ACCEPTED in decision.blockers
        )
    }

    @Test
    fun `missing any diagnostic layer remains fail closed`() {
        val decision = PostacMasterPipelineUnlockContract.evaluate(
            validEvidence().copy(filteredEvidencePresent = false)
        )

        assertEquals(PostacMasterPipelineUnlockContract.Status.LOCKED, decision.status)
        assertTrue(
            PostacMasterPipelineUnlockContract.Blocker.FILTERED_EVIDENCE_MISSING in
                decision.blockers
        )
    }

    @Test
    fun `background replacement requires background removal capability first`() {
        val decision = PostacMasterPipelineUnlockContract.evaluate(
            validEvidence().copy(
                module = PostacMasterPipelineUnlockContract.Module.BACKGROUND_REPLACEMENT,
                upstreamReadyModules = emptySet()
            )
        )

        assertEquals(PostacMasterPipelineUnlockContract.Status.LOCKED, decision.status)
        assertTrue(
            PostacMasterPipelineUnlockContract.Blocker.UPSTREAM_MODULE_NOT_READY in
                decision.blockers
        )
    }

    @Test
    fun `production activation and deformation cannot be smuggled into capability`() {
        val decision = PostacMasterPipelineUnlockContract.evaluate(
            validEvidence().copy(
                requestsProductionActivation = true,
                requestsDeformation = true
            )
        )

        assertEquals(PostacMasterPipelineUnlockContract.Status.LOCKED, decision.status)
        assertTrue(
            PostacMasterPipelineUnlockContract.Blocker.PRODUCTION_ACTIVATION_REQUESTED in
                decision.blockers
        )
        assertTrue(
            PostacMasterPipelineUnlockContract.Blocker.DEFORMATION_REQUESTED in decision.blockers
        )
    }

    @Test
    fun `provenance mismatch blocks otherwise complete evidence`() {
        val decision = PostacMasterPipelineUnlockContract.evaluate(
            validEvidence().copy(expectedCommit = "other")
        )

        assertEquals(PostacMasterPipelineUnlockContract.Status.LOCKED, decision.status)
        assertTrue(
            PostacMasterPipelineUnlockContract.Blocker.PROVENANCE_MISMATCH in decision.blockers
        )
    }

    @Test
    fun `complete accepted evidence exposes isolated adapter capability only`() {
        val evidence = validEvidence()
        val decision = PostacMasterPipelineUnlockContract.evaluate(evidence)

        assertEquals(
            PostacMasterPipelineUnlockContract.Status.READY_FOR_ISOLATED_ADAPTER,
            decision.status
        )
        assertTrue(decision.blockers.isEmpty())
        val capability = requireNotNull(decision.capability)
        assertEquals(evidence.module, capability.module)
        assertEquals("feature/test", capability.provenanceBranch)
        assertEquals("abc123", capability.provenanceCommit)
    }

    private fun validEvidence() = PostacMasterPipelineUnlockContract.Evidence(
        module = PostacMasterPipelineUnlockContract.Module.BACKGROUND_REMOVAL,
        a1DevicePass = true,
        iteration32Accepted = true,
        rawEvidencePresent = true,
        filteredEvidencePresent = true,
        acceptedEvidencePresent = true,
        moduleContractCiGreen = true,
        upstreamReadyModules = emptySet(),
        provenanceBranch = "feature/test",
        provenanceCommit = "abc123",
        expectedBranch = "feature/test",
        expectedCommit = "abc123"
    )
}
