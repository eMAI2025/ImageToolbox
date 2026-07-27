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
 * Architecture-only hand-off between an accepted background-removal decision and a future bitmap
 * adapter.
 *
 * This type does not allocate an output bitmap, does not resample the mask and does not change UI.
 * It only proves that the immutable accepted mask is aligned with the exact oriented source raster.
 * Any mismatch remains blocked instead of being silently stretched to the image.
 */
object BackgroundRemovalPreviewPlan {

    const val FINGERPRINT = "POSTAC_MASTER_BACKGROUND_REMOVAL_PREVIEW_PLAN_V1"

    enum class Status {
        BLOCKED,
        READY_FOR_ADAPTER
    }

    enum class Blocker {
        REMOVAL_DECISION_BLOCKED,
        ALPHA_CANDIDATE_MISSING,
        SOURCE_DIMENSIONS_INVALID,
        MASK_SOURCE_DIMENSIONS_MISMATCH
    }

    enum class Operation {
        KEEP_ACCEPTED_SUBJECT_CLEAR_BACKGROUND
    }

    data class SourceRaster(
        val width: Int,
        val height: Int
    )

    data class Plan(
        val status: Status,
        val blockers: Set<Blocker>,
        val sourceRaster: SourceRaster,
        val alphaCandidate: ConfidenceMask?,
        val operation: Operation?,
        val provenanceBranch: String,
        val provenanceCommit: String,
        val fingerprint: String = FINGERPRINT
    ) {
        init {
            when (status) {
                Status.BLOCKED -> {
                    require(blockers.isNotEmpty())
                    require(operation == null)
                }
                Status.READY_FOR_ADAPTER -> {
                    require(blockers.isEmpty())
                    require(alphaCandidate != null)
                    require(operation == Operation.KEEP_ACCEPTED_SUBJECT_CLEAR_BACKGROUND)
                    require(sourceRaster.width > 0 && sourceRaster.height > 0)
                    require(alphaCandidate.width == sourceRaster.width)
                    require(alphaCandidate.height == sourceRaster.height)
                }
            }
        }
    }

    fun create(
        removalDecision: BackgroundRemovalContract.Decision,
        sourceRaster: SourceRaster
    ): Plan {
        val blockers = linkedSetOf<Blocker>()
        if (removalDecision.status != BackgroundRemovalContract.Status.ELIGIBLE_FOR_PREVIEW) {
            blockers += Blocker.REMOVAL_DECISION_BLOCKED
        }
        val alpha = removalDecision.alphaCandidate
        if (alpha == null) blockers += Blocker.ALPHA_CANDIDATE_MISSING
        if (sourceRaster.width <= 0 || sourceRaster.height <= 0) {
            blockers += Blocker.SOURCE_DIMENSIONS_INVALID
        }
        if (
            alpha != null &&
            sourceRaster.width > 0 &&
            sourceRaster.height > 0 &&
            (alpha.width != sourceRaster.width || alpha.height != sourceRaster.height)
        ) {
            blockers += Blocker.MASK_SOURCE_DIMENSIONS_MISMATCH
        }

        val ready = blockers.isEmpty()
        return Plan(
            status = if (ready) Status.READY_FOR_ADAPTER else Status.BLOCKED,
            blockers = blockers,
            sourceRaster = sourceRaster,
            alphaCandidate = alpha.takeIf { ready },
            operation = Operation.KEEP_ACCEPTED_SUBJECT_CLEAR_BACKGROUND.takeIf { ready },
            provenanceBranch = removalDecision.provenanceBranch,
            provenanceCommit = removalDecision.provenanceCommit
        )
    }
}
