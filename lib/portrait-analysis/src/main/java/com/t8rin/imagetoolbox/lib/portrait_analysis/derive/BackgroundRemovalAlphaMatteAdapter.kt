/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis.derive

import kotlin.math.roundToInt

/**
 * Architecture-only adapter that converts an already accepted, source-aligned confidence mask into
 * an immutable 8-bit alpha matte payload.
 *
 * It does not allocate or modify an image bitmap, does not composite pixels, does not resample the
 * mask and is not connected to runtime UI. Continuous confidence is preserved by deterministic
 * linear conversion; no additional foreground threshold or anatomy reconstruction is introduced.
 */
object BackgroundRemovalAlphaMatteAdapter {

    const val FINGERPRINT = "POSTAC_MASTER_BACKGROUND_REMOVAL_ALPHA_MATTE_ADAPTER_V1"

    enum class Status {
        BLOCKED,
        READY_FOR_BITMAP_ADAPTER
    }

    enum class Blocker {
        PREVIEW_PLAN_BLOCKED,
        ALPHA_CANDIDATE_MISSING,
        MASK_SOURCE_DIMENSIONS_MISMATCH
    }

    /** Immutable row-major 8-bit alpha payload. */
    class AlphaMatte(
        val width: Int,
        val height: Int,
        alphaValues: ByteArray
    ) {
        private val values = alphaValues.copyOf()

        init {
            require(width > 0)
            require(height > 0)
            require(values.size == width * height)
        }

        val size: Int get() = values.size

        /** Returns an unsigned alpha value in 0..255. */
        operator fun get(x: Int, y: Int): Int {
            require(x in 0 until width)
            require(y in 0 until height)
            return values[y * width + x].toInt() and 0xFF
        }

        fun copyValues(): ByteArray = values.copyOf()
    }

    data class Result(
        val status: Status,
        val blockers: Set<Blocker>,
        val matte: AlphaMatte?,
        val provenanceBranch: String,
        val provenanceCommit: String,
        val fingerprint: String = FINGERPRINT
    ) {
        init {
            when (status) {
                Status.BLOCKED -> {
                    require(blockers.isNotEmpty())
                    require(matte == null)
                }
                Status.READY_FOR_BITMAP_ADAPTER -> {
                    require(blockers.isEmpty())
                    require(matte != null)
                }
            }
        }
    }

    fun prepare(plan: BackgroundRemovalPreviewPlan.Plan): Result {
        val blockers = linkedSetOf<Blocker>()
        if (plan.status != BackgroundRemovalPreviewPlan.Status.READY_FOR_ADAPTER) {
            blockers += Blocker.PREVIEW_PLAN_BLOCKED
        }
        val candidate = plan.alphaCandidate
        if (candidate == null) {
            blockers += Blocker.ALPHA_CANDIDATE_MISSING
        }
        if (
            candidate != null &&
            (candidate.width != plan.sourceRaster.width ||
                candidate.height != plan.sourceRaster.height)
        ) {
            blockers += Blocker.MASK_SOURCE_DIMENSIONS_MISMATCH
        }

        val matte = if (blockers.isEmpty() && candidate != null) {
            val alpha = candidate.copyValues().mapToByteArray { confidence ->
                (confidence * 255f).roundToInt().coerceIn(0, 255).toByte()
            }
            AlphaMatte(candidate.width, candidate.height, alpha)
        } else {
            null
        }

        return Result(
            status = if (matte == null) Status.BLOCKED else Status.READY_FOR_BITMAP_ADAPTER,
            blockers = blockers,
            matte = matte,
            provenanceBranch = plan.provenanceBranch,
            provenanceCommit = plan.provenanceCommit
        )
    }

    private inline fun FloatArray.mapToByteArray(transform: (Float) -> Byte): ByteArray =
        ByteArray(size) { index -> transform(this[index]) }
}
