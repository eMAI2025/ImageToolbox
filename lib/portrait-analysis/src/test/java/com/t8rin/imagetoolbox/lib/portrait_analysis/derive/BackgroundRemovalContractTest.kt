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

class BackgroundRemovalContractTest {

    @Test
    fun `missing A1 device pass blocks alpha candidate even with valid masks`() {
        val mask = mask(1f, 1f, 0f, 0f)

        val decision = BackgroundRemovalContract.evaluate(
            evidence(
                raw = mask,
                filtered = mask,
                accepted = mask,
                a1Pass = false
            )
        )

        assertEquals(BackgroundRemovalContract.Status.BLOCKED, decision.status)
        assertTrue(
            BackgroundRemovalContract.Blocker.A1_DEVICE_PASS_MISSING in decision.blockers
        )
        assertNull(decision.alphaCandidate)
    }

    @Test
    fun `accepted mask must be a subset of filtered mask`() {
        val raw = mask(1f, 1f, 1f, 1f)
        val filtered = mask(1f, 0.5f, 0f, 0f)
        val accepted = mask(1f, 0.7f, 0f, 0f)

        val decision = BackgroundRemovalContract.evaluate(
            evidence(raw, filtered, accepted, a1Pass = true)
        )

        assertEquals(BackgroundRemovalContract.Status.BLOCKED, decision.status)
        assertTrue(
            BackgroundRemovalContract.Blocker.ACCEPTED_MASK_NOT_SUBSET_OF_FILTERED in
                decision.blockers
        )
        assertNull(decision.alphaCandidate)
    }

    @Test
    fun `empty accepted mask blocks preview`() {
        val raw = mask(1f, 1f, 1f, 1f)
        val filtered = mask(1f, 1f, 0f, 0f)
        val accepted = mask(0f, 0f, 0f, 0f)

        val decision = BackgroundRemovalContract.evaluate(
            evidence(raw, filtered, accepted, a1Pass = true)
        )

        assertTrue(BackgroundRemovalContract.Blocker.ACCEPTED_MASK_EMPTY in decision.blockers)
        assertNull(decision.alphaCandidate)
    }

    @Test
    fun `dimension mismatch fails closed`() {
        val decision = BackgroundRemovalContract.evaluate(
            BackgroundRemovalContract.Evidence(
                rawMask = ConfidenceMask(2, 2, floatArrayOf(1f, 1f, 1f, 1f)),
                filteredMask = ConfidenceMask(1, 2, floatArrayOf(1f, 1f)),
                acceptedMask = ConfidenceMask(2, 2, floatArrayOf(1f, 1f, 0f, 0f)),
                a1DevicePass = true,
                provenanceBranch = "feature/test",
                provenanceCommit = "0123456789abcdef"
            )
        )

        assertTrue(
            BackgroundRemovalContract.Blocker.MASK_DIMENSIONS_INCONSISTENT in decision.blockers
        )
        assertNull(decision.alphaCandidate)
    }

    @Test
    fun `valid accepted mask becomes preview candidate only after A1 pass`() {
        val raw = mask(1f, 1f, 1f, 0.2f)
        val filtered = mask(1f, 0.9f, 0.8f, 0f)
        val accepted = mask(1f, 0.8f, 0.7f, 0f)

        val decision = BackgroundRemovalContract.evaluate(
            evidence(raw, filtered, accepted, a1Pass = true)
        )

        assertEquals(
            BackgroundRemovalContract.Status.ELIGIBLE_FOR_PREVIEW,
            decision.status
        )
        assertTrue(decision.blockers.isEmpty())
        assertSame(accepted, decision.alphaCandidate)
        assertSame(raw, decision.rawMask)
        assertSame(filtered, decision.filteredMask)
    }

    private fun evidence(
        raw: ConfidenceMask?,
        filtered: ConfidenceMask?,
        accepted: ConfidenceMask?,
        a1Pass: Boolean
    ) = BackgroundRemovalContract.Evidence(
        rawMask = raw,
        filteredMask = filtered,
        acceptedMask = accepted,
        a1DevicePass = a1Pass,
        provenanceBranch = "feature/postac-master-background-removal-contract",
        provenanceCommit = "bf9a31e1ed6e863ea6900826a49ec2d471553b8a"
    )

    private fun mask(vararg values: Float) = ConfidenceMask(
        width = 2,
        height = 2,
        confidenceValues = values
    )
}
