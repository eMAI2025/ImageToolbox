/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis.derive

import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ConfidenceMask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundRemovalBitmapAdapterContractTest {

    @Test
    fun `blocked alpha result cannot expose render request`() {
        val blocked = BackgroundRemovalAlphaMatteAdapter.Result(
            status = BackgroundRemovalAlphaMatteAdapter.Status.BLOCKED,
            blockers = setOf(BackgroundRemovalAlphaMatteAdapter.Blocker.PREVIEW_PLAN_BLOCKED),
            matte = null,
            provenanceBranch = "feature/background-contract",
            provenanceCommit = "commit-123"
        )

        val result = BackgroundRemovalBitmapAdapterContract.prepare(
            blocked,
            source(width = 2, height = 2)
        )

        assertEquals(BackgroundRemovalBitmapAdapterContract.Status.BLOCKED, result.status)
        assertNull(result.request)
        assertTrue(
            BackgroundRemovalBitmapAdapterContract.Blocker.ALPHA_MATTE_NOT_READY in
                result.blockers
        )
    }

    @Test
    fun `unoriented source is blocked without adapter request`() {
        val result = BackgroundRemovalBitmapAdapterContract.prepare(
            readyAlpha(2, 2),
            source(width = 2, height = 2, orientationApplied = false)
        )

        assertEquals(BackgroundRemovalBitmapAdapterContract.Status.BLOCKED, result.status)
        assertNull(result.request)
        assertTrue(
            BackgroundRemovalBitmapAdapterContract.Blocker.SOURCE_NOT_ORIENTED in result.blockers
        )
    }

    @Test
    fun `source and matte dimension mismatch remains blocked`() {
        val result = BackgroundRemovalBitmapAdapterContract.prepare(
            readyAlpha(2, 2),
            source(width = 4, height = 4)
        )

        assertEquals(BackgroundRemovalBitmapAdapterContract.Status.BLOCKED, result.status)
        assertNull(result.request)
        assertTrue(
            BackgroundRemovalBitmapAdapterContract.Blocker.SOURCE_MATTE_DIMENSIONS_MISMATCH in
                result.blockers
        )
    }

    @Test
    fun `provenance mismatch remains blocked`() {
        val result = BackgroundRemovalBitmapAdapterContract.prepare(
            readyAlpha(2, 2),
            source(width = 2, height = 2, commit = "other-commit")
        )

        assertEquals(BackgroundRemovalBitmapAdapterContract.Status.BLOCKED, result.status)
        assertNull(result.request)
        assertTrue(
            BackgroundRemovalBitmapAdapterContract.Blocker.PROVENANCE_MISMATCH in result.blockers
        )
    }

    @Test
    fun `consistent accepted matte produces immutable platform request only`() {
        val alpha = readyAlpha(2, 2)

        val result = BackgroundRemovalBitmapAdapterContract.prepare(
            alpha,
            source(width = 2, height = 2)
        )

        assertEquals(
            BackgroundRemovalBitmapAdapterContract.Status.READY_FOR_PLATFORM_ADAPTER,
            result.status
        )
        assertTrue(result.blockers.isEmpty())
        val request = assertNotNull(result.request).let { result.request!! }
        assertSame(alpha.matte, request.alphaMatte)
        assertEquals(
            BackgroundRemovalPreviewPlan.Operation.KEEP_ACCEPTED_SUBJECT_CLEAR_BACKGROUND,
            request.operation
        )
        assertEquals("feature/background-contract", request.provenanceBranch)
        assertEquals("commit-123", request.provenanceCommit)
    }

    private fun readyAlpha(
        width: Int,
        height: Int
    ): BackgroundRemovalAlphaMatteAdapter.Result {
        val values = FloatArray(width * height) { 1f }
        val mask = ConfidenceMask(width, height, values)
        val decision = BackgroundRemovalContract.evaluate(
            BackgroundRemovalContract.Evidence(
                rawMask = mask,
                filteredMask = mask,
                acceptedMask = mask,
                a1DevicePass = true,
                provenanceBranch = "feature/background-contract",
                provenanceCommit = "commit-123"
            )
        )
        val plan = BackgroundRemovalPreviewPlan.create(
            decision,
            BackgroundRemovalPreviewPlan.SourceRaster(width, height)
        )
        return BackgroundRemovalAlphaMatteAdapter.prepare(plan)
    }

    private fun source(
        width: Int,
        height: Int,
        orientationApplied: Boolean = true,
        branch: String = "feature/background-contract",
        commit: String = "commit-123"
    ) = BackgroundRemovalBitmapAdapterContract.SourceDescriptor(
        width = width,
        height = height,
        orientationApplied = orientationApplied,
        pixelFormat = BackgroundRemovalBitmapAdapterContract.PixelFormat.RGBA_8888,
        provenanceBranch = branch,
        provenanceCommit = commit
    )
}
