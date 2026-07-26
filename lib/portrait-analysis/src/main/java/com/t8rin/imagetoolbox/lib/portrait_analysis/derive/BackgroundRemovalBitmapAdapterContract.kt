/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis.derive

/**
 * Platform-neutral contract for a future bitmap adapter.
 *
 * This file deliberately contains no Android Bitmap dependency and no rendering implementation.
 * It only proves that an oriented source raster and an accepted alpha matte are dimensionally and
 * provenance-consistent before a platform adapter may be invoked. A blocked preparation never
 * exposes a render request.
 */
object BackgroundRemovalBitmapAdapterContract {

    const val FINGERPRINT = "POSTAC_MASTER_BACKGROUND_REMOVAL_BITMAP_ADAPTER_CONTRACT_V1"

    enum class Status {
        BLOCKED,
        READY_FOR_PLATFORM_ADAPTER
    }

    enum class Blocker {
        ALPHA_MATTE_NOT_READY,
        SOURCE_DIMENSIONS_INVALID,
        SOURCE_NOT_ORIENTED,
        SOURCE_MATTE_DIMENSIONS_MISMATCH,
        PROVENANCE_MISSING,
        PROVENANCE_MISMATCH
    }

    enum class PixelFormat {
        RGBA_8888
    }

    data class SourceDescriptor(
        val width: Int,
        val height: Int,
        val orientationApplied: Boolean,
        val pixelFormat: PixelFormat,
        val provenanceBranch: String,
        val provenanceCommit: String
    )

    data class RenderRequest(
        val source: SourceDescriptor,
        val alphaMatte: BackgroundRemovalAlphaMatteAdapter.AlphaMatte,
        val operation: BackgroundRemovalPreviewPlan.Operation,
        val provenanceBranch: String,
        val provenanceCommit: String,
        val fingerprint: String = FINGERPRINT
    ) {
        init {
            require(source.width > 0 && source.height > 0)
            require(source.orientationApplied)
            require(alphaMatte.width == source.width)
            require(alphaMatte.height == source.height)
            require(provenanceBranch.isNotBlank())
            require(provenanceCommit.isNotBlank())
            require(provenanceBranch == source.provenanceBranch)
            require(provenanceCommit == source.provenanceCommit)
        }
    }

    data class Preparation(
        val status: Status,
        val blockers: Set<Blocker>,
        val request: RenderRequest?,
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

    /**
     * Future platform boundary. No implementation is provided or wired to runtime in this PR.
     */
    fun interface PlatformAdapter<Output> {
        suspend fun render(request: RenderRequest): Output
    }

    fun prepare(
        alphaResult: BackgroundRemovalAlphaMatteAdapter.Result,
        source: SourceDescriptor
    ): Preparation {
        val blockers = linkedSetOf<Blocker>()
        if (
            alphaResult.status !=
            BackgroundRemovalAlphaMatteAdapter.Status.READY_FOR_BITMAP_ADAPTER ||
            alphaResult.matte == null
        ) {
            blockers += Blocker.ALPHA_MATTE_NOT_READY
        }
        if (source.width <= 0 || source.height <= 0) {
            blockers += Blocker.SOURCE_DIMENSIONS_INVALID
        }
        if (!source.orientationApplied) {
            blockers += Blocker.SOURCE_NOT_ORIENTED
        }
        val matte = alphaResult.matte
        if (
            matte != null &&
            (matte.width != source.width || matte.height != source.height)
        ) {
            blockers += Blocker.SOURCE_MATTE_DIMENSIONS_MISMATCH
        }
        if (
            source.provenanceBranch.isBlank() ||
            source.provenanceCommit.isBlank() ||
            alphaResult.provenanceBranch.isBlank() ||
            alphaResult.provenanceCommit.isBlank()
        ) {
            blockers += Blocker.PROVENANCE_MISSING
        }
        if (
            source.provenanceBranch.isNotBlank() &&
            source.provenanceCommit.isNotBlank() &&
            alphaResult.provenanceBranch.isNotBlank() &&
            alphaResult.provenanceCommit.isNotBlank() &&
            (source.provenanceBranch != alphaResult.provenanceBranch ||
                source.provenanceCommit != alphaResult.provenanceCommit)
        ) {
            blockers += Blocker.PROVENANCE_MISMATCH
        }

        val request = if (blockers.isEmpty() && matte != null) {
            RenderRequest(
                source = source,
                alphaMatte = matte,
                operation =
                    BackgroundRemovalPreviewPlan.Operation.KEEP_ACCEPTED_SUBJECT_CLEAR_BACKGROUND,
                provenanceBranch = alphaResult.provenanceBranch,
                provenanceCommit = alphaResult.provenanceCommit
            )
        } else {
            null
        }

        return Preparation(
            status = if (request == null) Status.BLOCKED else Status.READY_FOR_PLATFORM_ADAPTER,
            blockers = blockers,
            request = request
        )
    }
}
