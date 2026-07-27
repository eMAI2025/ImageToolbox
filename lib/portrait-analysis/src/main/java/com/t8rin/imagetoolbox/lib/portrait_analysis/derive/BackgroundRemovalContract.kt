/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis.derive

import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ConfidenceMask

/**
 * Architecture-only contract for future background removal.
 *
 * This contract does not render transparency and does not mutate pixels. It only decides whether an
 * already accepted subject mask may be promoted to an alpha candidate after the POSTAC_MASTER A1
 * device gate has passed. Raw and filtered masks remain separate diagnostic evidence.
 */
object BackgroundRemovalContract {

    const val FINGERPRINT = "POSTAC_MASTER_BACKGROUND_REMOVAL_CONTRACT_V1"

    enum class Status {
        BLOCKED,
        ELIGIBLE_FOR_PREVIEW
    }

    enum class Blocker {
        A1_DEVICE_PASS_MISSING,
        RAW_MASK_MISSING,
        FILTERED_MASK_MISSING,
        ACCEPTED_MASK_MISSING,
        MASK_DIMENSIONS_INCONSISTENT,
        ACCEPTED_MASK_EMPTY,
        ACCEPTED_MASK_NOT_SUBSET_OF_FILTERED
    }

    data class Evidence(
        val rawMask: ConfidenceMask?,
        val filteredMask: ConfidenceMask?,
        val acceptedMask: ConfidenceMask?,
        val a1DevicePass: Boolean,
        val provenanceBranch: String,
        val provenanceCommit: String
    ) {
        init {
            require(provenanceBranch.isNotBlank())
            require(provenanceCommit.isNotBlank())
        }
    }

    data class Decision(
        val status: Status,
        val blockers: Set<Blocker>,
        val rawMask: ConfidenceMask?,
        val filteredMask: ConfidenceMask?,
        val acceptedMask: ConfidenceMask?,
        /**
         * Null while blocked. When eligible, this points to the already accepted immutable mask;
         * no alpha conversion or image mutation occurs in this architecture stage.
         */
        val alphaCandidate: ConfidenceMask?,
        val provenanceBranch: String,
        val provenanceCommit: String,
        val fingerprint: String = FINGERPRINT
    ) {
        init {
            when (status) {
                Status.BLOCKED -> {
                    require(blockers.isNotEmpty())
                    require(alphaCandidate == null)
                }
                Status.ELIGIBLE_FOR_PREVIEW -> {
                    require(blockers.isEmpty())
                    require(alphaCandidate != null)
                    require(alphaCandidate === acceptedMask)
                }
            }
        }
    }

    fun evaluate(evidence: Evidence): Decision {
        val blockers = linkedSetOf<Blocker>()
        val raw = evidence.rawMask
        val filtered = evidence.filteredMask
        val accepted = evidence.acceptedMask

        if (!evidence.a1DevicePass) blockers += Blocker.A1_DEVICE_PASS_MISSING
        if (raw == null) blockers += Blocker.RAW_MASK_MISSING
        if (filtered == null) blockers += Blocker.FILTERED_MASK_MISSING
        if (accepted == null) blockers += Blocker.ACCEPTED_MASK_MISSING

        val masks = listOfNotNull(raw, filtered, accepted)
        if (masks.size > 1) {
            val first = masks.first()
            if (masks.any { it.width != first.width || it.height != first.height }) {
                blockers += Blocker.MASK_DIMENSIONS_INCONSISTENT
            }
        }

        if (accepted != null) {
            val acceptedValues = accepted.copyValues()
            if (acceptedValues.none { it > 0f }) {
                blockers += Blocker.ACCEPTED_MASK_EMPTY
            }
            if (filtered != null && sameDimensions(accepted, filtered)) {
                val filteredValues = filtered.copyValues()
                if (acceptedValues.indices.any { acceptedValues[it] > filteredValues[it] }) {
                    blockers += Blocker.ACCEPTED_MASK_NOT_SUBSET_OF_FILTERED
                }
            }
        }

        val status = if (blockers.isEmpty()) {
            Status.ELIGIBLE_FOR_PREVIEW
        } else {
            Status.BLOCKED
        }
        return Decision(
            status = status,
            blockers = blockers,
            rawMask = raw,
            filteredMask = filtered,
            acceptedMask = accepted,
            alphaCandidate = accepted.takeIf { status == Status.ELIGIBLE_FOR_PREVIEW },
            provenanceBranch = evidence.provenanceBranch,
            provenanceCommit = evidence.provenanceCommit
        )
    }

    private fun sameDimensions(first: ConfidenceMask, second: ConfidenceMask): Boolean =
        first.width == second.width && first.height == second.height
}
