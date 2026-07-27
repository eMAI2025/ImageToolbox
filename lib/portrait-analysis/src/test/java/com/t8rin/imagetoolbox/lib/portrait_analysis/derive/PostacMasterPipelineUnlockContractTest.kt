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
        assertTrue(PostacMasterPipelineUnlockContract.Blocker.A1_DEVICE_PASS_MISSING in decision.blockers)
        assertTrue(PostacMasterPipelineUnlockContract.Blocker.ITERATION_32_NOT_ACCEPTED in decision.blockers)
    }

    @Test
    fun `missing any diagnostic layer remains fail closed`() {
        val decision = PostacMasterPipelineUnlockContract.evaluate(
            validEvidence().copy(filteredEvidencePresent = false)
        )

        assertEquals(PostacMasterPipelineUnlockContract.Status.LOCKED, decision.status)
        assertTrue(PostacMasterPipelineUnlockContract.Blocker.FILTERED_EVIDENCE_MISSING in decision.blockers)
    }

    @Test
    fun `green boolean cannot substitute current module contract fingerprint`() {
        val decision = PostacMasterPipelineUnlockContract.evaluate(
            validEvidence().copy(moduleContractFingerprint = "obsolete-contract")
        )

        assertEquals(PostacMasterPipelineUnlockContract.Status.LOCKED, decision.status)
        assertNull(decision.capability)
        assertTrue(
            PostacMasterPipelineUnlockContract.Blocker.MODULE_CONTRACT_FINGERPRINT_MISMATCH in
                decision.blockers
        )
    }

    @Test
    fun `background replacement requires background removal capability first`() {
        val decision = PostacMasterPipelineUnlockContract.evaluate(
            validEvidence(PostacMasterPipelineUnlockContract.Module.BACKGROUND_REPLACEMENT).copy(
                upstreamCapabilities = emptySet()
            )
        )

        assertEquals(PostacMasterPipelineUnlockContract.Status.LOCKED, decision.status)
        assertTrue(PostacMasterPipelineUnlockContract.Blocker.UPSTREAM_MODULE_NOT_READY in decision.blockers)
    }

    @Test
    fun `upstream capability from another source lineage cannot unlock downstream module`() {
        val decision = PostacMasterPipelineUnlockContract.evaluate(
            validEvidence(PostacMasterPipelineUnlockContract.Module.BACKGROUND_REPLACEMENT).copy(
                upstreamCapabilities = setOf(
                    upstream(
                        PostacMasterPipelineUnlockContract.Module.BACKGROUND_REMOVAL,
                        commit = "stale"
                    )
                )
            )
        )

        assertEquals(PostacMasterPipelineUnlockContract.Status.LOCKED, decision.status)
        assertTrue(PostacMasterPipelineUnlockContract.Blocker.UPSTREAM_PROVENANCE_MISMATCH in decision.blockers)
    }

    @Test
    fun `upstream green boolean with stale contract fingerprint remains locked`() {
        val decision = PostacMasterPipelineUnlockContract.evaluate(
            validEvidence(PostacMasterPipelineUnlockContract.Module.BACKGROUND_REPLACEMENT).copy(
                upstreamCapabilities = setOf(
                    upstream(
                        PostacMasterPipelineUnlockContract.Module.BACKGROUND_REMOVAL,
                        contractFingerprint = "obsolete-background-removal-contract"
                    )
                )
            )
        )

        assertEquals(PostacMasterPipelineUnlockContract.Status.LOCKED, decision.status)
        assertTrue(
            PostacMasterPipelineUnlockContract.Blocker.UPSTREAM_CONTRACT_FINGERPRINT_MISMATCH in
                decision.blockers
        )
    }

    @Test
    fun `undeclared upstream capability cannot be smuggled into dependency proof`() {
        val decision = PostacMasterPipelineUnlockContract.evaluate(
            validEvidence().copy(
                upstreamCapabilities = setOf(
                    upstream(PostacMasterPipelineUnlockContract.Module.PORTRAIT_FILTERS)
                )
            )
        )

        assertEquals(PostacMasterPipelineUnlockContract.Status.LOCKED, decision.status)
        assertTrue(PostacMasterPipelineUnlockContract.Blocker.UNDECLARED_UPSTREAM_CAPABILITY in decision.blockers)
    }

    @Test
    fun `matching green upstream capability unlocks only declared downstream module`() {
        val decision = PostacMasterPipelineUnlockContract.evaluate(
            validEvidence(PostacMasterPipelineUnlockContract.Module.BACKGROUND_REPLACEMENT).copy(
                upstreamCapabilities = setOf(
                    upstream(PostacMasterPipelineUnlockContract.Module.BACKGROUND_REMOVAL)
                )
            )
        )

        assertEquals(PostacMasterPipelineUnlockContract.Status.READY_FOR_ISOLATED_ADAPTER, decision.status)
        assertTrue(decision.blockers.isEmpty())
    }

    @Test
    fun `production activation and deformation cannot be smuggled into capability`() {
        val decision = PostacMasterPipelineUnlockContract.evaluate(
            validEvidence().copy(requestsProductionActivation = true, requestsDeformation = true)
        )

        assertEquals(PostacMasterPipelineUnlockContract.Status.LOCKED, decision.status)
        assertTrue(PostacMasterPipelineUnlockContract.Blocker.PRODUCTION_ACTIVATION_REQUESTED in decision.blockers)
        assertTrue(PostacMasterPipelineUnlockContract.Blocker.DEFORMATION_REQUESTED in decision.blockers)
    }

    @Test
    fun `provenance mismatch blocks otherwise complete evidence`() {
        val decision = PostacMasterPipelineUnlockContract.evaluate(
            validEvidence().copy(expectedCommit = "other")
        )

        assertEquals(PostacMasterPipelineUnlockContract.Status.LOCKED, decision.status)
        assertTrue(PostacMasterPipelineUnlockContract.Blocker.PROVENANCE_MISMATCH in decision.blockers)
    }

    @Test
    fun `complete accepted evidence exposes isolated adapter capability only`() {
        val evidence = validEvidence()
        val decision = PostacMasterPipelineUnlockContract.evaluate(evidence)

        assertEquals(PostacMasterPipelineUnlockContract.Status.READY_FOR_ISOLATED_ADAPTER, decision.status)
        assertTrue(decision.blockers.isEmpty())
        val capability = requireNotNull(decision.capability)
        assertEquals(evidence.module, capability.module)
        assertEquals(evidence.moduleContractFingerprint, capability.moduleContractFingerprint)
        assertEquals("feature/test", capability.provenanceBranch)
        assertEquals("abc123", capability.provenanceCommit)
    }

    private fun validEvidence(
        module: PostacMasterPipelineUnlockContract.Module =
            PostacMasterPipelineUnlockContract.Module.BACKGROUND_REMOVAL
    ) = PostacMasterPipelineUnlockContract.Evidence(
        module = module,
        a1DevicePass = true,
        iteration32Accepted = true,
        rawEvidencePresent = true,
        filteredEvidencePresent = true,
        acceptedEvidencePresent = true,
        moduleContractCiGreen = true,
        moduleContractFingerprint =
            PostacMasterPipelineUnlockContract.expectedContractFingerprint(module),
        upstreamCapabilities = emptySet(),
        provenanceBranch = "feature/test",
        provenanceCommit = "abc123",
        expectedBranch = "feature/test",
        expectedCommit = "abc123"
    )

    private fun upstream(
        module: PostacMasterPipelineUnlockContract.Module,
        commit: String = "abc123",
        contractFingerprint: String =
            PostacMasterPipelineUnlockContract.expectedContractFingerprint(module)
    ) = PostacMasterPipelineUnlockContract.UpstreamCapabilityEvidence(
        module = module,
        contractCiGreen = true,
        contractFingerprint = contractFingerprint,
        provenanceBranch = "feature/test",
        provenanceCommit = commit
    )
}
