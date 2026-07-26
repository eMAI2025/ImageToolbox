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
 * Architecture-only capability gate for future clothing replacement.
 *
 * This contract does not generate garments, deform the subject, rasterize pixels, infer hidden body
 * parts or connect to final UI. It only proves whether an already accepted body observation and an
 * explicitly verified garment source are eligible to enter a later platform adapter.
 */
object ClothingChangeContract {

    const val FINGERPRINT = "POSTAC_MASTER_CLOTHING_CHANGE_CONTRACT_V1"

    enum class Status {
        BLOCKED,
        READY_FOR_PLATFORM_ADAPTER
    }

    enum class TargetZone {
        UPPER_BODY,
        LOWER_BODY,
        FULL_BODY
    }

    enum class GarmentSourceKind {
        VERIFIED_RASTER_REFERENCE,
        VERIFIED_ASSET_REFERENCE,
        PROCEDURAL_DESCRIPTOR
    }

    enum class Blocker {
        A1_DEVICE_PASS_MISSING,
        RAW_OBSERVATION_MISSING,
        FILTERED_OBSERVATION_MISSING,
        ACCEPTED_OBSERVATION_MISSING,
        BODY_NOT_DETECTED,
        SUBJECT_MASK_MISSING,
        REQUIRED_BODY_REGION_NOT_ACCEPTED,
        GARMENT_SOURCE_MISSING,
        GARMENT_SOURCE_UNVERIFIED,
        GARMENT_DIMENSIONS_INVALID,
        PROVENANCE_MISSING,
        PROVENANCE_MISMATCH
    }

    data class GarmentSource(
        val sourceId: String,
        val kind: GarmentSourceKind,
        val verified: Boolean,
        val width: Int? = null,
        val height: Int? = null
    ) {
        init {
            require(sourceId.isNotBlank())
            require((width == null) == (height == null))
        }
    }

    data class Evidence(
        val a1DevicePass: Boolean,
        val rawObservation: SubjectObservation?,
        val filteredObservation: SubjectObservation?,
        val acceptedObservation: SubjectObservation?,
        val acceptedRegionIds: Set<String>,
        val targetZone: TargetZone,
        val garmentSource: GarmentSource?,
        val provenanceBranch: String,
        val provenanceCommit: String
    )

    data class Request internal constructor(
        val acceptedObservation: SubjectObservation,
        val acceptedRegionIds: Set<String>,
        val targetZone: TargetZone,
        val garmentSource: GarmentSource,
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
        if (accepted == null) {
            blockers += Blocker.ACCEPTED_OBSERVATION_MISSING
        } else {
            if (accepted.bodyCount != 1) blockers += Blocker.BODY_NOT_DETECTED
            if (PortraitRegionId.SUBJECT_MASK !in accepted.masks) {
                blockers += Blocker.SUBJECT_MASK_MISSING
            }
        }

        val requiredRegions = requiredRegions(evidence.targetZone)
        if (!evidence.acceptedRegionIds.containsAll(requiredRegions)) {
            blockers += Blocker.REQUIRED_BODY_REGION_NOT_ACCEPTED
        }

        val garment = evidence.garmentSource
        if (garment == null) {
            blockers += Blocker.GARMENT_SOURCE_MISSING
        } else {
            if (!garment.verified) blockers += Blocker.GARMENT_SOURCE_UNVERIFIED
            if (
                (garment.width != null && garment.width <= 0) ||
                (garment.height != null && garment.height <= 0)
            ) {
                blockers += Blocker.GARMENT_DIMENSIONS_INVALID
            }
        }

        if (evidence.provenanceBranch.isBlank() || evidence.provenanceCommit.isBlank()) {
            blockers += Blocker.PROVENANCE_MISSING
        }

        val request = if (blockers.isEmpty() && accepted != null && garment != null) {
            Request(
                acceptedObservation = accepted,
                acceptedRegionIds = evidence.acceptedRegionIds.toSet(),
                targetZone = evidence.targetZone,
                garmentSource = garment,
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

    fun requiredRegions(targetZone: TargetZone): Set<String> = when (targetZone) {
        TargetZone.UPPER_BODY -> setOf(
            PortraitRegionId.SHOULDER_CONTOUR,
            PortraitRegionId.ARM_CONTOURS,
            PortraitRegionId.WAIST_CONTOUR
        )
        TargetZone.LOWER_BODY -> setOf(
            PortraitRegionId.WAIST_CONTOUR,
            PortraitRegionId.HIP_CONTOUR,
            PortraitRegionId.LEG_CONTOURS
        )
        TargetZone.FULL_BODY -> setOf(
            PortraitRegionId.SHOULDER_CONTOUR,
            PortraitRegionId.ARM_CONTOURS,
            PortraitRegionId.WAIST_CONTOUR,
            PortraitRegionId.HIP_CONTOUR,
            PortraitRegionId.LEG_CONTOURS
        )
    }
}
