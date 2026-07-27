/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis.derive

import com.t8rin.imagetoolbox.lib.portrait_analysis.catalog.PortraitRegionId
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation

/**
 * Architecture-only capability gate for future non-geometric filters.
 *
 * The contract never changes pixels, coordinates, contours, meshes or silhouette geometry. It only
 * proves whether accepted evidence may enter a later isolated platform adapter.
 */
object PortraitFilterContract {

    const val FINGERPRINT = "POSTAC_MASTER_PORTRAIT_FILTER_CONTRACT_V1"

    enum class Status {
        BLOCKED,
        READY_FOR_PLATFORM_ADAPTER
    }

    enum class Scope {
        GLOBAL_IMAGE,
        FACE_SKIN,
        EYES,
        TEETH
    }

    enum class Operation(
        val scope: Scope,
        val minimumValue: Float,
        val maximumValue: Float,
        val requiredRegionIds: Set<String>
    ) {
        EXPOSURE(Scope.GLOBAL_IMAGE, -1f, 1f, emptySet()),
        CONTRAST(Scope.GLOBAL_IMAGE, -1f, 1f, emptySet()),
        SATURATION(Scope.GLOBAL_IMAGE, -1f, 1f, emptySet()),
        SKIN_SMOOTHING(
            Scope.FACE_SKIN,
            0f,
            1f,
            setOf(PortraitRegionId.FACE_SKIN_REGION, PortraitRegionId.FACE_OCCLUSION_MASK)
        ),
        EYE_BRIGHTNESS(
            Scope.EYES,
            0f,
            1f,
            setOf(PortraitRegionId.LEFT_EYE_REGION, PortraitRegionId.RIGHT_EYE_REGION)
        ),
        TEETH_WHITENESS(
            Scope.TEETH,
            0f,
            1f,
            setOf(PortraitRegionId.VISIBLE_TEETH_REGION, PortraitRegionId.MOUTH_REGION)
        )
    }

    enum class Blocker {
        A1_DEVICE_PASS_MISSING,
        RAW_OBSERVATION_MISSING,
        FILTERED_OBSERVATION_MISSING,
        ACCEPTED_OBSERVATION_MISSING,
        SOURCE_DIMENSIONS_INVALID,
        SOURCE_ORIENTATION_NOT_APPLIED,
        SUBJECT_MASK_MISSING,
        REQUIRED_REGION_NOT_ACCEPTED,
        VALUE_NOT_FINITE,
        VALUE_OUTSIDE_DOMAIN,
        GEOMETRIC_OPERATION_REQUESTED,
        PROVENANCE_MISSING,
        PROVENANCE_MISMATCH
    }

    data class Evidence(
        val a1DevicePass: Boolean,
        val rawObservation: SubjectObservation?,
        val filteredObservation: SubjectObservation?,
        val acceptedObservation: SubjectObservation?,
        val acceptedRegionIds: Set<String>,
        val sourceWidth: Int,
        val sourceHeight: Int,
        val sourceOrientationApplied: Boolean,
        val operation: Operation,
        val value: Float,
        val requestsGeometryChange: Boolean,
        val provenanceBranch: String,
        val provenanceCommit: String
    )

    data class Request internal constructor(
        val acceptedObservation: SubjectObservation,
        val acceptedRegionIds: Set<String>,
        val sourceWidth: Int,
        val sourceHeight: Int,
        val operation: Operation,
        val value: Float,
        val provenanceBranch: String,
        val provenanceCommit: String
    )

    data class Decision internal constructor(
        val status: Status,
        val blockers: Set<Blocker>,
        val request: Request?,
        val fingerprint: String = FINGERPRINT
    ) {
        init {
            when (status) {
                Status.BLOCKED -> {
                    require(blockers.isNotEmpty())
                    require(request == null)
                }
                Status.READY_FOR_PLATFORM_ADAPTER -> {
                    require(blockers.isEmpty())
                    require(request != null)
                }
            }
        }
    }

    fun evaluate(evidence: Evidence): Decision {
        val blockers = linkedSetOf<Blocker>()
        if (!evidence.a1DevicePass) blockers += Blocker.A1_DEVICE_PASS_MISSING
        if (evidence.rawObservation == null) blockers += Blocker.RAW_OBSERVATION_MISSING
        if (evidence.filteredObservation == null) blockers += Blocker.FILTERED_OBSERVATION_MISSING
        val accepted = evidence.acceptedObservation
        if (accepted == null) blockers += Blocker.ACCEPTED_OBSERVATION_MISSING
        if (evidence.sourceWidth <= 0 || evidence.sourceHeight <= 0) {
            blockers += Blocker.SOURCE_DIMENSIONS_INVALID
        }
        if (!evidence.sourceOrientationApplied) blockers += Blocker.SOURCE_ORIENTATION_NOT_APPLIED
        if (!evidence.value.isFinite()) blockers += Blocker.VALUE_NOT_FINITE
        if (
            evidence.value.isFinite() &&
            evidence.value !in evidence.operation.minimumValue..evidence.operation.maximumValue
        ) {
            blockers += Blocker.VALUE_OUTSIDE_DOMAIN
        }
        if (evidence.requestsGeometryChange) blockers += Blocker.GEOMETRIC_OPERATION_REQUESTED
        if (evidence.provenanceBranch.isBlank() || evidence.provenanceCommit.isBlank()) {
            blockers += Blocker.PROVENANCE_MISSING
        }

        if (accepted != null && evidence.operation.scope != Scope.GLOBAL_IMAGE) {
            if (PortraitRegionId.SUBJECT_MASK !in accepted.masks) {
                blockers += Blocker.SUBJECT_MASK_MISSING
            }
            if (!evidence.acceptedRegionIds.containsAll(evidence.operation.requiredRegionIds)) {
                blockers += Blocker.REQUIRED_REGION_NOT_ACCEPTED
            }
        }

        val request = if (blockers.isEmpty() && accepted != null) {
            Request(
                acceptedObservation = accepted,
                acceptedRegionIds = evidence.acceptedRegionIds.toSet(),
                sourceWidth = evidence.sourceWidth,
                sourceHeight = evidence.sourceHeight,
                operation = evidence.operation,
                value = evidence.value,
                provenanceBranch = evidence.provenanceBranch,
                provenanceCommit = evidence.provenanceCommit
            )
        } else {
            null
        }

        return Decision(
            status = if (request == null) Status.BLOCKED else Status.READY_FOR_PLATFORM_ADAPTER,
            blockers = blockers,
            request = request
        )
    }

    fun verifyAdapterProvenance(
        request: Request,
        adapterBranch: String,
        adapterCommit: String
    ): Set<Blocker> = buildSet {
        if (adapterBranch.isBlank() || adapterCommit.isBlank()) {
            add(Blocker.PROVENANCE_MISSING)
        } else if (
            adapterBranch != request.provenanceBranch ||
            adapterCommit != request.provenanceCommit
        ) {
            add(Blocker.PROVENANCE_MISMATCH)
        }
    }
}
