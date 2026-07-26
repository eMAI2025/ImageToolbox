/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis.derive

import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ConfidenceMask
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundRemovalAlphaMatteAdapterTest {

    @Test
    fun `blocked preview plan cannot produce alpha matte`() {
        val mask = ConfidenceMask(2, 2, floatArrayOf(1f, 1f, 1f, 1f))
        val decision = BackgroundRemovalContract.evaluate(
            BackgroundRemovalContract.Evidence(
                rawMask = mask,
                filteredMask = mask,
                acceptedMask = mask,
                a1DevicePass = false,
                provenanceBranch = "feature/test",
                provenanceCommit = "abc123"
            )
        )
        val plan = BackgroundRemovalPreviewPlan.create(
            decision,
            BackgroundRemovalPreviewPlan.SourceRaster(2, 2)
        )

        val result = BackgroundRemovalAlphaMatteAdapter.prepare(plan)

        assertEquals(BackgroundRemovalAlphaMatteAdapter.Status.BLOCKED, result.status)
        assertNull(result.matte)
        assertTrue(
            BackgroundRemovalAlphaMatteAdapter.Blocker.PREVIEW_PLAN_BLOCKED in result.blockers
        )
        assertTrue(
            BackgroundRemovalAlphaMatteAdapter.Blocker.ALPHA_CANDIDATE_MISSING in result.blockers
        )
    }

    @Test
    fun `ready plan converts continuous confidence deterministically without thresholding`() {
        val mask = ConfidenceMask(2, 2, floatArrayOf(0f, 0.5f, 1f, 0.25f))

        val result = BackgroundRemovalAlphaMatteAdapter.prepare(readyPlan(mask))

        assertEquals(
            BackgroundRemovalAlphaMatteAdapter.Status.READY_FOR_BITMAP_ADAPTER,
            result.status
        )
        val matte = requireNotNull(result.matte)
        assertEquals(0, matte[0, 0])
        assertEquals(128, matte[1, 0])
        assertEquals(255, matte[0, 1])
        assertEquals(64, matte[1, 1])
        assertTrue(result.blockers.isEmpty())
    }

    @Test
    fun `alpha matte owns an immutable copy of converted values`() {
        val sourceValues = floatArrayOf(0f, 0.5f, 1f, 0.25f)
        val mask = ConfidenceMask(2, 2, sourceValues)
        sourceValues.fill(0f)

        val matte = requireNotNull(
            BackgroundRemovalAlphaMatteAdapter.prepare(readyPlan(mask)).matte
        )
        val exported = matte.copyValues()
        exported.fill(0)

        assertEquals(128, matte[1, 0])
        assertEquals(255, matte[0, 1])
        assertArrayEquals(
            byteArrayOf(0, 128.toByte(), 255.toByte(), 64),
            matte.copyValues()
        )
    }

    @Test
    fun `adapter forwards exact plan provenance`() {
        val result = BackgroundRemovalAlphaMatteAdapter.prepare(
            readyPlan(ConfidenceMask(1, 1, floatArrayOf(1f)))
        )

        assertEquals("feature/background-contract", result.provenanceBranch)
        assertEquals("commit-123", result.provenanceCommit)
    }

    private fun readyPlan(mask: ConfidenceMask): BackgroundRemovalPreviewPlan.Plan {
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
        return BackgroundRemovalPreviewPlan.create(
            decision,
            BackgroundRemovalPreviewPlan.SourceRaster(mask.width, mask.height)
        )
    }
}
