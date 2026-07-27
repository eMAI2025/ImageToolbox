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
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundRemovalPreviewPlanTest {

    @Test
    fun `blocked removal decision cannot create preview plan`() {
        val decision = BackgroundRemovalContract.evaluate(
            evidence(
                a1DevicePass = false,
                mask = mask(4, 4)
            )
        )

        val plan = BackgroundRemovalPreviewPlan.create(
            removalDecision = decision,
            sourceRaster = BackgroundRemovalPreviewPlan.SourceRaster(4, 4)
        )

        assertEquals(BackgroundRemovalPreviewPlan.Status.BLOCKED, plan.status)
        assertTrue(
            BackgroundRemovalPreviewPlan.Blocker.REMOVAL_DECISION_BLOCKED in plan.blockers
        )
        assertTrue(
            BackgroundRemovalPreviewPlan.Blocker.ALPHA_CANDIDATE_MISSING in plan.blockers
        )
        assertNull(plan.alphaCandidate)
        assertNull(plan.operation)
    }

    @Test
    fun `invalid source dimensions remain blocked without resampling`() {
        val decision = eligibleDecision(mask(4, 4))

        val plan = BackgroundRemovalPreviewPlan.create(
            removalDecision = decision,
            sourceRaster = BackgroundRemovalPreviewPlan.SourceRaster(0, 4)
        )

        assertEquals(BackgroundRemovalPreviewPlan.Status.BLOCKED, plan.status)
        assertTrue(
            BackgroundRemovalPreviewPlan.Blocker.SOURCE_DIMENSIONS_INVALID in plan.blockers
        )
        assertNull(plan.alphaCandidate)
    }

    @Test
    fun `mask and source raster mismatch is rejected instead of stretched`() {
        val decision = eligibleDecision(mask(4, 4))

        val plan = BackgroundRemovalPreviewPlan.create(
            removalDecision = decision,
            sourceRaster = BackgroundRemovalPreviewPlan.SourceRaster(8, 8)
        )

        assertEquals(BackgroundRemovalPreviewPlan.Status.BLOCKED, plan.status)
        assertTrue(
            BackgroundRemovalPreviewPlan.Blocker.MASK_SOURCE_DIMENSIONS_MISMATCH in plan.blockers
        )
        assertNull(plan.alphaCandidate)
        assertNull(plan.operation)
    }

    @Test
    fun `exact aligned accepted mask creates immutable adapter plan`() {
        val acceptedMask = mask(4, 4)
        val decision = eligibleDecision(acceptedMask)

        val plan = BackgroundRemovalPreviewPlan.create(
            removalDecision = decision,
            sourceRaster = BackgroundRemovalPreviewPlan.SourceRaster(4, 4)
        )

        assertEquals(BackgroundRemovalPreviewPlan.Status.READY_FOR_ADAPTER, plan.status)
        assertTrue(plan.blockers.isEmpty())
        assertSame(acceptedMask, plan.alphaCandidate)
        assertEquals(
            BackgroundRemovalPreviewPlan.Operation.KEEP_ACCEPTED_SUBJECT_CLEAR_BACKGROUND,
            plan.operation
        )
        assertEquals("feature/test", plan.provenanceBranch)
        assertEquals("abcdef123456", plan.provenanceCommit)
    }

    private fun eligibleDecision(mask: ConfidenceMask) = BackgroundRemovalContract.evaluate(
        evidence(
            a1DevicePass = true,
            mask = mask
        )
    )

    private fun evidence(
        a1DevicePass: Boolean,
        mask: ConfidenceMask
    ) = BackgroundRemovalContract.Evidence(
        rawMask = mask,
        filteredMask = mask,
        acceptedMask = mask,
        a1DevicePass = a1DevicePass,
        provenanceBranch = "feature/test",
        provenanceCommit = "abcdef123456"
    )

    private fun mask(width: Int, height: Int) = ConfidenceMask(
        width = width,
        height = height,
        confidenceValues = FloatArray(width * height) { 0.8f }
    )
}
