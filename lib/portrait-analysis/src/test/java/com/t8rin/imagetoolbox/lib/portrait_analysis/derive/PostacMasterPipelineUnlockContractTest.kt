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
    fun `missing module CI attestation cannot be replaced by contract fingerprint`() {
        val decision = PostacMasterPipelineUnlockContract.evaluate(
            validEvidence().copy(moduleCiAttestation = null)
        )
        assertEquals(PostacMasterPipelineUnlockContract.Status.LOCKED, decision.status)
        assertTrue(
            PostacMasterPipelineUnlockContract.Blocker.MODULE_CI_ATTESTATION_MISSING in
                decision.blockers
        )
    }

    @Test
    fun `successful CI from wrong workflow is not accepted`() {
        val decision = PostacMasterPipelineUnlockContract.evaluate(
            validEvidence().copy(
                moduleCiAttestation = ciAttestation(workflowName = "Unrelated CI")
            )
        )
        assertEquals(PostacMasterPipelineUnlockContract.Status.LOCKED, decision.status)
        assertTrue(
            PostacMasterPipelineUnlockContract.Blocker.MODULE_CI_ATTESTATION_INVALID in
                decision.blockers
        )
    }

    @Test
    fun `successful CI for stale head cannot unlock current module`() {
        val decision = PostacMasterPipelineUnlockContract.evaluate(
            validEvidence().copy(
                moduleCiAttestation = ciAttestation(headCommit = "stale")
            )
        )
        assertEquals(PostacMasterPipelineUnlockContract.Status.LOCKED, decision.status)
        assertTrue(
            PostacMasterPipelineUnlockContract.Blocker.MODULE_CI_HEAD_MISMATCH in
                decision.blockers
        )
    }

    @Test
    fun `non successful CI conclusion remains locked`() {
        val decision = PostacMasterPipelineUnlockContract.evaluate(
            validEvidence().copy(
                moduleCiAttestation = ciAttestation(
                    conclusion = PostacMasterPipelineUnlockContract.CiConclusion.FAILURE
                )
            )
        )
        assertEquals(PostacMasterPipelineUnlockContract.Status.LOCKED, decision.status)
        assertTrue(
            PostacMasterPipelineUnlockContract.Blocker.MODULE_CI_ATTESTATION_INVALID in
                decision.blockers
        )
    }

    @Test
    fun `green CI cannot substitute current module contract fingerprint`() {
        val decision = PostacMasterPipelineUnlockContract.evaluate(
            validEvidence().copy(moduleContractFingerprint = "obsolete-contract")
        )
        assertEquals(PostacMasterPipelineUnlockContract.Status.LOCKED, decision.status)
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
                    upstream(PostacMasterPipelineUnlockContract.Module.BACKGROUND_REMOVAL, commit = "stale")
                )
            )
        )
        assertEquals(PostacMasterPipelineUnlockContract.Status.LOCKED, decision.status)
        assertTrue(PostacMasterPipelineUnlockContract.Blocker.UPSTREAM_PROVENANCE_MISMATCH in decision.blockers)
    }

    @Test
    fun `upstream CI for stale head remains locked`() {
        val module = PostacMasterPipelineUnlockContract.Module.BACKGROUND_REMOVAL
        val decision = PostacMasterPipelineUnlockContract.evaluate(
            validEvidence(PostacMasterPipelineUnlockContract.Module.BACKGROUND_REPLACEMENT).copy(
                upstreamCapabilities = setOf(
                    upstream(module, ciHeadCommit = "stale")
                )
            )
        )
        assertEquals(PostacMasterPipelineUnlockContract.Status.LOCKED, decision.status)
        assertTrue(
            PostacMasterPipelineUnlockContract.Blocker.UPSTREAM_CI_HEAD_MISMATCH in
                decision.blockers
        )
    }

    @Test
    fun `upstream successful boolean equivalent from wrong workflow remains locked`() {
        val module = PostacMasterPipelineUnlockContract.Module.BACKGROUND_REMOVAL
        val decision = PostacMasterPipelineUnlockContract.evaluate(
            validEvidence(PostacMasterPipelineUnlockContract.Module.BACKGROUND_REPLACEMENT).copy(
                upstreamCapabilities = setOf(
                    upstream(module, workflowName = "Unrelated CI")
                )
            )
        )
        assertEquals(PostacMasterPipelineUnlockContract.Status.LOCKED, decision.status)
        assertTrue(
            PostacMasterPipelineUnlockContract.Blocker.UPSTREAM_CI_ATTESTATION_INVALID in
                decision.blockers
        )
    }

    @Test
    fun `upstream stale contract fingerprint remains locked`() {
        val module = PostacMasterPipelineUnlockContract.Module.BACKGROUND_REMOVAL
        val decision = PostacMasterPipelineUnlockContract.evaluate(
            validEvidence(PostacMasterPipelineUnlockContract.Module.BACKGROUND_REPLACEMENT).copy(
                upstreamCapabilities = setOf(
                    upstream(module, contractFingerprint = "obsolete-background-removal-contract")
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
    fun `matching upstream capability with exact CI head unlocks declared downstream module`() {
        val upstreamModule = PostacMasterPipelineUnlockContract.Module.BACKGROUND_REMOVAL
        val decision = PostacMasterPipelineUnlockContract.evaluate(
            validEvidence(PostacMasterPipelineUnlockContract.Module.BACKGROUND_REPLACEMENT).copy(
                upstreamCapabilities = setOf(upstream(upstreamModule))
            )
        )
        assertEquals(PostacMasterPipelineUnlockContract.Status.READY_FOR_ISOLATED_ADAPTER, decision.status)
        val capability = requireNotNull(decision.capability)
        assertEquals(mapOf(upstreamModule to 2002L), capability.upstreamCiRunIds)
    }

    @Test
    fun `downstream capability preserves exact upstream contract and CI lineage`() {
        val module = PostacMasterPipelineUnlockContract.Module.BACKGROUND_REPLACEMENT
        val upstreamModule = PostacMasterPipelineUnlockContract.Module.BACKGROUND_REMOVAL
        val capability = requireNotNull(
            PostacMasterPipelineUnlockContract.evaluate(
                validEvidence(module).copy(upstreamCapabilities = setOf(upstream(upstreamModule)))
            ).capability
        )
        assertEquals(
            mapOf(
                upstreamModule to
                    PostacMasterPipelineUnlockContract.expectedContractFingerprint(upstreamModule)
            ),
            capability.upstreamContractFingerprints
        )
        assertEquals(1001L, capability.moduleCiRunId)
        assertEquals(2002L, capability.upstreamCiRunIds.getValue(upstreamModule))
        assertEquals(PostacMasterPipelineUnlockContract.FINGERPRINT, capability.fingerprint)
    }

    @Test
    fun `root capabilities contain explicit empty upstream lineage`() {
        val capability = requireNotNull(
            PostacMasterPipelineUnlockContract.evaluate(validEvidence()).capability
        )
        assertTrue(capability.upstreamContractFingerprints.isEmpty())
        assertTrue(capability.upstreamCiRunIds.isEmpty())
    }

    @Test
    fun `static dependency graph is complete acyclic and self dependency free`() {
        val assessment = PostacMasterPipelineUnlockContract.dependencyGraphAssessment()
        assertTrue(assessment.valid)
        assertTrue(assessment.cyclicModules.isEmpty())
        assertTrue(assessment.selfDependentModules.isEmpty())
        assertTrue(assessment.unknownDependencyModules.isEmpty())
        assertEquals(
            PostacMasterPipelineUnlockContract.Module.entries.toSet(),
            PostacMasterPipelineUnlockContract.Module.entries
                .associateWith(PostacMasterPipelineUnlockContract::requiredUpstream)
                .keys
        )
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
        assertEquals(1001L, capability.moduleCiRunId)
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
        moduleContractFingerprint =
            PostacMasterPipelineUnlockContract.expectedContractFingerprint(module),
        moduleCiAttestation = ciAttestation(runId = 1001L),
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
            PostacMasterPipelineUnlockContract.expectedContractFingerprint(module),
        ciHeadCommit: String = commit,
        workflowName: String = PostacMasterPipelineUnlockContract.EXPECTED_WORKFLOW_NAME
    ) = PostacMasterPipelineUnlockContract.UpstreamCapabilityEvidence(
        module = module,
        contractFingerprint = contractFingerprint,
        ciAttestation = ciAttestation(
            runId = 2002L,
            headCommit = ciHeadCommit,
            workflowName = workflowName
        ),
        provenanceBranch = "feature/test",
        provenanceCommit = commit
    )

    private fun ciAttestation(
        runId: Long = 1001L,
        headCommit: String = "abc123",
        workflowName: String = PostacMasterPipelineUnlockContract.EXPECTED_WORKFLOW_NAME,
        conclusion: PostacMasterPipelineUnlockContract.CiConclusion =
            PostacMasterPipelineUnlockContract.CiConclusion.SUCCESS
    ) = PostacMasterPipelineUnlockContract.CiAttestation(
        repository = PostacMasterPipelineUnlockContract.EXPECTED_REPOSITORY,
        workflowName = workflowName,
        workflowId = 316505691L,
        runId = runId,
        runNumber = 413L,
        headCommit = headCommit,
        conclusion = conclusion
    )
}
