/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis.derive

/**
 * Static, platform-neutral integration contract for POSTAC_MASTER follow-on modules.
 *
 * It does not execute detection, filters, background operations, garment generation, deformation,
 * bitmap work or UI. It only determines whether a module may expose a later adapter capability.
 */
object PostacMasterPipelineUnlockContract {

    const val FINGERPRINT = "POSTAC_MASTER_PIPELINE_UNLOCK_CONTRACT_V3"

    enum class Module {
        BACKGROUND_REMOVAL,
        BACKGROUND_REPLACEMENT,
        CLOTHING_CHANGE,
        SILHOUETTE_EDIT,
        PORTRAIT_FILTERS
    }

    enum class Status {
        LOCKED,
        READY_FOR_ISOLATED_ADAPTER
    }

    enum class Blocker {
        A1_DEVICE_PASS_MISSING,
        ITERATION_32_NOT_ACCEPTED,
        RAW_EVIDENCE_MISSING,
        FILTERED_EVIDENCE_MISSING,
        ACCEPTED_EVIDENCE_MISSING,
        PROVENANCE_MISSING,
        PROVENANCE_MISMATCH,
        MODULE_CONTRACT_NOT_GREEN,
        MODULE_CONTRACT_FINGERPRINT_MISMATCH,
        UPSTREAM_MODULE_NOT_READY,
        UPSTREAM_PROVENANCE_MISSING,
        UPSTREAM_PROVENANCE_MISMATCH,
        UPSTREAM_CONTRACT_FINGERPRINT_MISMATCH,
        UNDECLARED_UPSTREAM_CAPABILITY,
        PRODUCTION_ACTIVATION_REQUESTED,
        FINAL_UI_REQUESTED,
        DEFORMATION_REQUESTED,
        CONTENT_GENERATION_REQUESTED
    }

    /**
     * Static proof that an upstream module passed its own contract on the same evidence lineage.
     * A bare module enum or green boolean is insufficient without its exact contract fingerprint.
     */
    data class UpstreamCapabilityEvidence(
        val module: Module,
        val contractCiGreen: Boolean,
        val contractFingerprint: String,
        val provenanceBranch: String,
        val provenanceCommit: String
    )

    data class Evidence(
        val module: Module,
        val a1DevicePass: Boolean,
        val iteration32Accepted: Boolean,
        val rawEvidencePresent: Boolean,
        val filteredEvidencePresent: Boolean,
        val acceptedEvidencePresent: Boolean,
        val moduleContractCiGreen: Boolean,
        val moduleContractFingerprint: String,
        val upstreamCapabilities: Set<UpstreamCapabilityEvidence>,
        val provenanceBranch: String,
        val provenanceCommit: String,
        val expectedBranch: String,
        val expectedCommit: String,
        val requestsProductionActivation: Boolean = false,
        val requestsFinalUi: Boolean = false,
        val requestsDeformation: Boolean = false,
        val requestsContentGeneration: Boolean = false
    )

    data class Capability internal constructor(
        val module: Module,
        val moduleContractFingerprint: String,
        val provenanceBranch: String,
        val provenanceCommit: String,
        val fingerprint: String = FINGERPRINT
    )

    data class Decision internal constructor(
        val status: Status,
        val blockers: Set<Blocker>,
        val capability: Capability?
    ) {
        init {
            when (status) {
                Status.LOCKED -> {
                    require(blockers.isNotEmpty())
                    require(capability == null)
                }
                Status.READY_FOR_ISOLATED_ADAPTER -> {
                    require(blockers.isEmpty())
                    require(capability != null)
                }
            }
        }
    }

    fun evaluate(evidence: Evidence): Decision {
        val blockers = linkedSetOf<Blocker>()
        if (!evidence.a1DevicePass) blockers += Blocker.A1_DEVICE_PASS_MISSING
        if (!evidence.iteration32Accepted) blockers += Blocker.ITERATION_32_NOT_ACCEPTED
        if (!evidence.rawEvidencePresent) blockers += Blocker.RAW_EVIDENCE_MISSING
        if (!evidence.filteredEvidencePresent) blockers += Blocker.FILTERED_EVIDENCE_MISSING
        if (!evidence.acceptedEvidencePresent) blockers += Blocker.ACCEPTED_EVIDENCE_MISSING
        if (!evidence.moduleContractCiGreen) blockers += Blocker.MODULE_CONTRACT_NOT_GREEN
        if (evidence.moduleContractFingerprint != expectedContractFingerprint(evidence.module)) {
            blockers += Blocker.MODULE_CONTRACT_FINGERPRINT_MISMATCH
        }
        if (
            evidence.provenanceBranch.isBlank() || evidence.provenanceCommit.isBlank() ||
            evidence.expectedBranch.isBlank() || evidence.expectedCommit.isBlank()
        ) {
            blockers += Blocker.PROVENANCE_MISSING
        } else if (
            evidence.provenanceBranch != evidence.expectedBranch ||
            evidence.provenanceCommit != evidence.expectedCommit
        ) {
            blockers += Blocker.PROVENANCE_MISMATCH
        }

        val requiredUpstream = requiredUpstream(evidence.module)
        val capabilitiesByModule = evidence.upstreamCapabilities.groupBy { it.module }
        if (!capabilitiesByModule.keys.containsAll(requiredUpstream)) {
            blockers += Blocker.UPSTREAM_MODULE_NOT_READY
        }
        if (evidence.upstreamCapabilities.any { it.module !in requiredUpstream }) {
            blockers += Blocker.UNDECLARED_UPSTREAM_CAPABILITY
        }
        requiredUpstream.forEach { module ->
            val candidates = capabilitiesByModule[module].orEmpty()
            if (candidates.size != 1 || candidates.none { it.contractCiGreen }) {
                blockers += Blocker.UPSTREAM_MODULE_NOT_READY
            }
            candidates.forEach { capability ->
                if (capability.contractFingerprint != expectedContractFingerprint(module)) {
                    blockers += Blocker.UPSTREAM_CONTRACT_FINGERPRINT_MISMATCH
                }
                if (
                    capability.provenanceBranch.isBlank() ||
                    capability.provenanceCommit.isBlank()
                ) {
                    blockers += Blocker.UPSTREAM_PROVENANCE_MISSING
                } else if (
                    capability.provenanceBranch != evidence.provenanceBranch ||
                    capability.provenanceCommit != evidence.provenanceCommit
                ) {
                    blockers += Blocker.UPSTREAM_PROVENANCE_MISMATCH
                }
            }
        }

        if (evidence.requestsProductionActivation) {
            blockers += Blocker.PRODUCTION_ACTIVATION_REQUESTED
        }
        if (evidence.requestsFinalUi) blockers += Blocker.FINAL_UI_REQUESTED
        if (evidence.requestsDeformation) blockers += Blocker.DEFORMATION_REQUESTED
        if (evidence.requestsContentGeneration) {
            blockers += Blocker.CONTENT_GENERATION_REQUESTED
        }

        val capability = if (blockers.isEmpty()) {
            Capability(
                module = evidence.module,
                moduleContractFingerprint = evidence.moduleContractFingerprint,
                provenanceBranch = evidence.provenanceBranch,
                provenanceCommit = evidence.provenanceCommit
            )
        } else {
            null
        }
        return Decision(
            status = if (capability == null) Status.LOCKED else Status.READY_FOR_ISOLATED_ADAPTER,
            blockers = blockers,
            capability = capability
        )
    }

    fun expectedContractFingerprint(module: Module): String = when (module) {
        Module.BACKGROUND_REMOVAL -> BackgroundRemovalContract.FINGERPRINT
        Module.BACKGROUND_REPLACEMENT -> BackgroundReplacementContract.FINGERPRINT
        Module.CLOTHING_CHANGE -> ClothingChangeContract.FINGERPRINT
        Module.SILHOUETTE_EDIT -> SilhouetteEditContract.FINGERPRINT
        Module.PORTRAIT_FILTERS -> PortraitFilterContract.FINGERPRINT
    }

    fun requiredUpstream(module: Module): Set<Module> = when (module) {
        Module.BACKGROUND_REMOVAL -> emptySet()
        Module.BACKGROUND_REPLACEMENT -> setOf(Module.BACKGROUND_REMOVAL)
        Module.CLOTHING_CHANGE -> setOf(Module.BACKGROUND_REMOVAL)
        Module.SILHOUETTE_EDIT -> setOf(Module.BACKGROUND_REMOVAL)
        Module.PORTRAIT_FILTERS -> emptySet()
    }
}
