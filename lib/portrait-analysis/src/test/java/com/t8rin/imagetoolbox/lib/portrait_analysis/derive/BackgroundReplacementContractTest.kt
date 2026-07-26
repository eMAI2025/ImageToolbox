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
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundReplacementContractTest {

    @Test
    fun `blocked foreground cannot create replacement request`() {
        val decision = BackgroundReplacementContract.evaluate(
            foreground = blockedForeground(),
            backgroundSource = solid()
        )

        assertEquals(BackgroundReplacementContract.Status.BLOCKED, decision.status)
        assertNull(decision.request)
        assertTrue(
            BackgroundReplacementContract.Blocker.FOREGROUND_MATTE_BLOCKED in decision.blockers
        )
        assertTrue(
            BackgroundReplacementContract.Blocker.FOREGROUND_MATTE_MISSING in decision.blockers
        )
    }

    @Test
    fun `missing background source blocks request`() {
        val decision = BackgroundReplacementContract.evaluate(
            foreground = readyForeground(),
            backgroundSource = null
        )

        assertEquals(BackgroundReplacementContract.Status.BLOCKED, decision.status)
        assertTrue(
            BackgroundReplacementContract.Blocker.BACKGROUND_SOURCE_MISSING in decision.blockers
        )
    }

    @Test
    fun `unoriented raster background is rejected without implicit transform`() {
        val decision = BackgroundReplacementContract.evaluate(
            foreground = readyForeground(),
            backgroundSource = BackgroundReplacementContract.BackgroundSource.RasterReference(
                sourceId = "replacement-1",
                width = 4,
                height = 4,
                orientationApplied = false
            )
        )

        assertEquals(BackgroundReplacementContract.Status.BLOCKED, decision.status)
        assertTrue(
            BackgroundReplacementContract.Blocker.ORIENTATION_NOT_APPLIED in decision.blockers
        )
    }

    @Test
    fun `background dimensions mismatch blocks request without implicit resampling`() {
        val decision = BackgroundReplacementContract.evaluate(
            foreground = readyForeground(),
            backgroundSource = BackgroundReplacementContract.BackgroundSource.RasterReference(
                sourceId = "replacement-2",
                width = 8,
                height = 4,
                orientationApplied = true
            )
        )

        assertEquals(BackgroundReplacementContract.Status.BLOCKED, decision.status)
        assertNull(decision.request)
        assertTrue(
            BackgroundReplacementContract.Blocker.BACKGROUND_FOREGROUND_DIMENSIONS_MISMATCH in
                decision.blockers
        )
    }

    @Test
    fun `accepted matte and explicit background create architecture request only`() {
        val foreground = readyForeground()
        val background = solid()

        val decision = BackgroundReplacementContract.evaluate(foreground, background)

        assertEquals(
            BackgroundReplacementContract.Status.READY_FOR_PLATFORM_ADAPTER,
            decision.status
        )
        assertTrue(decision.blockers.isEmpty())
        assertSame(foreground.matte, decision.request!!.foregroundMatte)
        assertSame(background, decision.request!!.backgroundSource)
        assertEquals("feature/test", decision.request!!.provenanceBranch)
        assertEquals("abc123", decision.request!!.provenanceCommit)
    }

    @Test
    fun `adapter provenance must match foreground lineage`() {
        val request = BackgroundReplacementContract.evaluate(
            readyForeground(),
            solid()
        ).request!!

        assertTrue(
            BackgroundReplacementContract.verifyAdapterProvenance(
                request,
                adapterBranch = "feature/test",
                adapterCommit = "abc123"
            ).isEmpty()
        )
        assertEquals(
            setOf(BackgroundReplacementContract.Blocker.PROVENANCE_MISMATCH),
            BackgroundReplacementContract.verifyAdapterProvenance(
                request,
                adapterBranch = "feature/other",
                adapterCommit = "abc123"
            )
        )
    }

    private fun readyForeground() = BackgroundRemovalAlphaMatteAdapter.Result(
        status = BackgroundRemovalAlphaMatteAdapter.Status.READY_FOR_BITMAP_ADAPTER,
        blockers = emptySet(),
        matte = BackgroundRemovalAlphaMatteAdapter.AlphaMatte(
            width = 4,
            height = 4,
            alphaValues = ByteArray(16) { 0xFF.toByte() }
        ),
        provenanceBranch = "feature/test",
        provenanceCommit = "abc123"
    )

    private fun blockedForeground() = BackgroundRemovalAlphaMatteAdapter.Result(
        status = BackgroundRemovalAlphaMatteAdapter.Status.BLOCKED,
        blockers = setOf(BackgroundRemovalAlphaMatteAdapter.Blocker.PREVIEW_PLAN_BLOCKED),
        matte = null,
        provenanceBranch = "feature/test",
        provenanceCommit = "abc123"
    )

    private fun solid() = BackgroundReplacementContract.BackgroundSource.SolidColor(
        argb = 0xFF202020u,
        width = 4,
        height = 4
    )
}
