/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis.derive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyMaskComponentGateTest {

    @Test
    fun `torso anchored person component is retained`() {
        val result = BodyMaskComponentGate.evaluate(listOf(primary()))

        assertEquals("person", result.primaryComponentId)
        assertEquals(listOf("person"), result.filteredComponents.map { it.id })
        assertTrue(result.rejectedComponents.isEmpty())
    }

    @Test
    fun `detached halo above head is rejected without morphology`() {
        val result = BodyMaskComponentGate.evaluate(
            listOf(
                primary(),
                component(
                    id = "halo",
                    pixels = 80,
                    top = 0.01f,
                    bottom = 0.08f,
                    connected = false,
                    aboveHead = true
                )
            )
        )
        val halo = result.decisions.single { it.component.id == "halo" }

        assertFalse(halo.accepted)
        assertTrue(BodyMaskComponentGate.RejectionReason.DISCONNECTED_HALO_ABOVE_HEAD in halo.reasons)
        assertTrue(BodyMaskComponentGate.RejectionReason.COMPONENT_NOT_TORSO_ANCHORED in halo.reasons)
        assertFalse("halo" in result.filteredComponents.map { it.id })
        assertEquals(2, result.rawComponents.size)
    }

    @Test
    fun `detached background smear is rejected`() {
        val result = BodyMaskComponentGate.evaluate(
            listOf(
                primary(),
                component(id = "smear", pixels = 200, connected = false, aboveHead = false)
            )
        )
        val smear = result.decisions.single { it.component.id == "smear" }

        assertFalse(smear.accepted)
        assertTrue(BodyMaskComponentGate.RejectionReason.DISCONNECTED_BACKGROUND_SMEAR in smear.reasons)
    }

    @Test
    fun `connected hair or loose clothing remains with verified person component`() {
        val result = BodyMaskComponentGate.evaluate(
            listOf(
                primary(),
                component(
                    id = "connected_hair_or_clothing",
                    pixels = 600,
                    connected = true,
                    aboveHead = true
                )
            )
        )

        assertTrue(result.decisions.single { it.component.id == "connected_hair_or_clothing" }.accepted)
        assertTrue("connected_hair_or_clothing" in result.filteredComponents.map { it.id })
    }

    @Test
    fun `second person component is ambiguous rather than joined`() {
        val result = BodyMaskComponentGate.evaluate(
            listOf(
                primary(),
                component(
                    id = "second_person",
                    pixels = 4000,
                    connected = false,
                    aboveHead = false,
                    independentPersonAnchors = 5
                )
            )
        )
        val second = result.decisions.single { it.component.id == "second_person" }

        assertFalse(second.accepted)
        assertTrue(BodyMaskComponentGate.RejectionReason.MULTI_PERSON_COMPONENT_AMBIGUOUS in second.reasons)
        assertFalse("second_person" in result.filteredComponents.map { it.id })
    }

    @Test
    fun `missing torso anchored primary fails closed`() {
        val result = BodyMaskComponentGate.evaluate(
            listOf(component(id = "floating", pixels = 500, connected = false, aboveHead = false))
        )

        assertEquals(null, result.primaryComponentId)
        assertTrue(result.filteredComponents.isEmpty())
        assertTrue(
            BodyMaskComponentGate.RejectionReason.PRIMARY_COMPONENT_MISSING in
                result.decisions.single().reasons
        )
    }

    private fun primary() = BodyMaskComponentGate.ComponentEvidence(
        id = "person",
        pixelCount = 20_000,
        bounds = BodyMaskComponentGate.NormalizedBounds(0.20f, 0.10f, 0.80f, 0.95f),
        torsoAnchorCount = 4,
        overlapsTorsoCorridor = true,
        connectedToPrimary = true,
        aboveVerifiedHead = false
    )

    private fun component(
        id: String,
        pixels: Int,
        top: Float = 0.20f,
        bottom: Float = 0.40f,
        connected: Boolean,
        aboveHead: Boolean,
        independentPersonAnchors: Int = 0
    ) = BodyMaskComponentGate.ComponentEvidence(
        id = id,
        pixelCount = pixels,
        bounds = BodyMaskComponentGate.NormalizedBounds(0.05f, top, 0.15f, bottom),
        torsoAnchorCount = 0,
        overlapsTorsoCorridor = false,
        connectedToPrimary = connected,
        aboveVerifiedHead = aboveHead,
        independentPersonAnchorCount = independentPersonAnchors
    )
}
