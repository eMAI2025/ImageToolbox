/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis.derive

import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ConfidenceSource
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.LandmarkObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.NormalizedPoint3D
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ObservationBackend
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.VisibilityState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PostacMasterDiagnosticLayersTest {

    @Test
    fun `rejected face retains raw and filtered but exposes no accepted geometry`() {
        val raw = observation(faceCount = 1, ids = listOf("raw_a", "raw_b"))
        val filtered = observation(faceCount = 1, ids = listOf("raw_a"))
        val decision = FaceGeometryAcceptanceDecision(
            status = FaceGeometryAcceptanceStatus.FACE_DETECTED_GEOMETRY_REJECTED,
            rawObservation = raw,
            activeObservation = null,
            reasons = setOf(FaceGeometryRejectionReason.CONTOUR_CROSSES_UNSUPPORTED_SPACE)
        )

        val layers = PostacMasterDiagnosticLayers.fromFace(raw, filtered, decision)

        assertSame(raw, layers.raw)
        assertSame(filtered, layers.filtered)
        assertNull(layers.accepted)
        assertEquals(PostacMasterDiagnosticLayers.State.DETECTED_REJECTED, layers.state)
        assertEquals(2, layers.counts.rawLandmarks)
        assertEquals(1, layers.counts.filteredLandmarks)
        assertEquals(0, layers.counts.acceptedLandmarks)
        assertTrue("CONTOUR_CROSSES_UNSUPPORTED_SPACE" in layers.rejectionCodes)
    }

    @Test
    fun `accepted body keeps distinct raw filtered and accepted counts`() {
        val raw = observation(bodyCount = 1, ids = listOf("a", "b", "c"))
        val filtered = observation(bodyCount = 1, ids = listOf("a", "b"))
        val accepted = observation(bodyCount = 1, ids = listOf("a"))

        val layers = PostacMasterDiagnosticLayers.fromBody(
            raw = raw,
            filtered = filtered,
            accepted = accepted,
            rejectionCodes = emptySet()
        )

        assertEquals(PostacMasterDiagnosticLayers.State.DETECTED_ACCEPTED, layers.state)
        assertSame(accepted, layers.accepted)
        assertEquals(3, layers.counts.rawLandmarks)
        assertEquals(2, layers.counts.filteredLandmarks)
        assertEquals(1, layers.counts.acceptedLandmarks)
        assertTrue(layers.rejectionCodes.isEmpty())
    }

    @Test
    fun `detected body without accepted evidence fails closed with explicit reason`() {
        val raw = observation(bodyCount = 1, ids = listOf("shoulder"))
        val filtered = observation(bodyCount = 1, ids = emptyList())

        val layers = PostacMasterDiagnosticLayers.fromBody(
            raw = raw,
            filtered = filtered,
            accepted = null,
            rejectionCodes = emptySet()
        )

        assertEquals(PostacMasterDiagnosticLayers.State.DETECTED_REJECTED, layers.state)
        assertNull(layers.accepted)
        assertEquals(setOf("BODY_GEOMETRY_EVIDENCE_REJECTED"), layers.rejectionCodes)
    }

    @Test
    fun `physical export contract requires all three geometry layers and both mask images`() {
        assertEquals(
            linkedSetOf(
                "diagnostic_manifest.json",
                "geometry_raw.json",
                "geometry_filtered.json",
                "geometry_accepted.json",
                "mask_raw.png",
                "mask_filtered.png",
                "runtime_log.txt"
            ),
            PostacMasterDiagnosticLayers.REQUIRED_EXPORT_FILES
        )
    }

    private fun observation(
        faceCount: Int = 0,
        bodyCount: Int = 0,
        ids: List<String>
    ): SubjectObservation {
        val landmarks = ids.associateWith { id ->
            LandmarkObservation(
                id = id,
                point = NormalizedPoint3D(0.5f, 0.5f),
                confidence = 0.9f,
                confidenceSource = ConfidenceSource.DIRECT,
                visibility = VisibilityState.VISIBLE,
                backend = if (bodyCount > 0) ObservationBackend.ML_KIT_POSE
                else ObservationBackend.ML_KIT_FACE_DETECTION
            )
        }
        val detected = faceCount > 0 || bodyCount > 0
        return SubjectObservation(
            subjectCount = if (detected) 1 else 0,
            faceCount = faceCount,
            bodyCount = bodyCount,
            landmarks = landmarks,
            regions = emptyMap()
        )
    }
}
