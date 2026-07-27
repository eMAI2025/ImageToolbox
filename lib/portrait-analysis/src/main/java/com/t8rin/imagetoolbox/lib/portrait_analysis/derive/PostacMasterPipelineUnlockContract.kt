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

    const val FINGERPRINT = "POSTAC_MASTER_PIPELINE_UNLOCK_CONTRACT_V1"

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
        UPSTREAM_MODULE_NOT_READY,
        PRODUCTION_ACTIVATION_REQUESTED,
        FINAL_UI_REQUESTED,
        DEFORMATION_REQUESTED,
        CONTENT_GENERATION_REQUESTED
    }

    data class Evidence(
        val module: Module,
        val a1DevicePass: Boolean,
        val iteration32Accepted: Boolean,
        val rawEvidencePresent: Boolean,
        val filteredEvidencePresent: Boolean,
        val acceptedEvidencePresent: Boolean,
        val moduleContractCiGreen: Boolean,
        val upstreamReadyModules: Set<Module>,
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
        if (!evidence.upstreamReadyModules.containsAll(requiredUpstream(evidence.module))) {
            blockers += Blocker.UPSTREAM_MODULE_NOT_READY
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

    fun requiredUpstream(module: Module): Set<Module> = when (module) {
        Module.BACKGROUND_REMOVAL -> emptySet()
        Module.BACKGROUND_REPLACEMENT -> setOf(Module.BACKGROUND_REMOVAL)
        Module.CLOTHING_CHANGE -> setOf(Module.BACKGROUND_REMOVAL)
        Module.SILHOUETTE_EDIT -> setOf(Module.BACKGROUND_REMOVAL)
        Module.PORTRAIT_FILTERS -> emptySet()
    }
}
