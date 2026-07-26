/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis.derive

/**
 * Architecture-only gate for future background replacement.
 *
 * The contract consumes only an already accepted foreground alpha matte. It does not allocate a
 * bitmap, decode a replacement image, resample pixels, composite layers or connect to runtime UI.
 * A blocked or provenance-inconsistent foreground request cannot be promoted into a replacement
 * operation.
 */
object BackgroundReplacementContract {

    const val FINGERPRINT = "POSTAC_MASTER_BACKGROUND_REPLACEMENT_CONTRACT_V1"

    enum class Status {
        BLOCKED,
        READY_FOR_PLATFORM_ADAPTER
    }

    enum class Blocker {
        FOREGROUND_MATTE_BLOCKED,
        FOREGROUND_MATTE_MISSING,
        FOREGROUND_DIMENSIONS_INVALID,
        BACKGROUND_SOURCE_MISSING,
        BACKGROUND_DIMENSIONS_INVALID,
        ORIENTATION_NOT_APPLIED,
        PROVENANCE_MISSING,
        PROVENANCE_MISMATCH
    }

    sealed interface BackgroundSource {
        val width: Int
        val height: Int

        data class SolidColor(
            val argb: UInt,
            override val width: Int,
            override val height: Int
        ) : BackgroundSource

        data class RasterReference(
            val sourceId: String,
            override val width: Int,
            override val height: Int,
            val orientationApplied: Boolean
        ) : BackgroundSource {
            init {
                require(sourceId.isNotBlank())
            }
        }
    }

    data class Request(
        val foregroundMatte: BackgroundRemovalAlphaMatteAdapter.AlphaMatte,
        val backgroundSource: BackgroundSource,
        val provenanceBranch: String,
        val provenanceCommit: String
    )

    data class Decision(
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

    fun evaluate(
        foreground: BackgroundRemovalAlphaMatteAdapter.Result,
        backgroundSource: BackgroundSource?
    ): Decision {
        val blockers = linkedSetOf<Blocker>()
        if (foreground.status != BackgroundRemovalAlphaMatteAdapter.Status.READY_FOR_BITMAP_ADAPTER) {
            blockers += Blocker.FOREGROUND_MATTE_BLOCKED
        }
        val matte = foreground.matte
        if (matte == null) blockers += Blocker.FOREGROUND_MATTE_MISSING
        if (matte != null && (matte.width <= 0 || matte.height <= 0)) {
            blockers += Blocker.FOREGROUND_DIMENSIONS_INVALID
        }
        if (backgroundSource == null) {
            blockers += Blocker.BACKGROUND_SOURCE_MISSING
        } else {
            if (backgroundSource.width <= 0 || backgroundSource.height <= 0) {
                blockers += Blocker.BACKGROUND_DIMENSIONS_INVALID
            }
            if (
                backgroundSource is BackgroundSource.RasterReference &&
                !backgroundSource.orientationApplied
            ) {
                blockers += Blocker.ORIENTATION_NOT_APPLIED
            }
        }
        if (foreground.provenanceBranch.isBlank() || foreground.provenanceCommit.isBlank()) {
            blockers += Blocker.PROVENANCE_MISSING
        }

        val request = if (blockers.isEmpty() && matte != null && backgroundSource != null) {
            Request(
                foregroundMatte = matte,
                backgroundSource = backgroundSource,
                provenanceBranch = foreground.provenanceBranch,
                provenanceCommit = foreground.provenanceCommit
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
