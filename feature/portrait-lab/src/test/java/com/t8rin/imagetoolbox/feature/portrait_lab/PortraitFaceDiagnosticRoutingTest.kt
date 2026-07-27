/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.feature.portrait_lab

import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation
import org.junit.Assert.assertSame
import org.junit.Test

class PortraitFaceDiagnosticRoutingTest {

    @Test
    fun `raw detector evidence remains candidate source when active geometry is empty`() {
        val raw = observation(faceCount = 2)
        val active = observation(faceCount = 0)

        assertSame(
            raw,
            PortraitFaceDiagnosticRouting.candidateSource(
                rawObservation = raw,
                activeObservation = active
            )
        )
    }

    @Test
    fun `active observation remains fallback when raw evidence is unavailable`() {
        val active = observation(faceCount = 1)

        assertSame(
            active,
            PortraitFaceDiagnosticRouting.candidateSource(
                rawObservation = null,
                activeObservation = active
            )
        )
    }

    @Test
    fun `frontal visual proof keeps original detector layout`() {
        val raw = observation(faceCount = 1)
        val visible = observation(faceCount = 1)

        assertSame(
            raw,
            PortraitFaceDiagnosticRouting.visualProofObservation(
                rawObservation = raw,
                visibleObservation = visible,
                fullFaceGeometryAllowed = true
            )
        )
    }

    @Test
    fun `partial pose visual proof uses visible side without granting edit geometry`() {
        val raw = observation(faceCount = 1)
        val visible = observation(faceCount = 1)

        assertSame(
            visible,
            PortraitFaceDiagnosticRouting.visualProofObservation(
                rawObservation = raw,
                visibleObservation = visible,
                fullFaceGeometryAllowed = false
            )
        )
    }

    private fun observation(faceCount: Int) = SubjectObservation(
        subjectCount = faceCount,
        faceCount = faceCount,
        bodyCount = 0,
        landmarks = emptyMap(),
        regions = emptyMap()
    )
}
