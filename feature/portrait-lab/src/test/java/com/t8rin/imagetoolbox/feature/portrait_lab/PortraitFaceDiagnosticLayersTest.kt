/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.feature.portrait_lab

import com.t8rin.imagetoolbox.lib.portrait_analysis.derive.FaceGeometryAcceptanceDecision
import com.t8rin.imagetoolbox.lib.portrait_analysis.derive.FaceGeometryAcceptanceStatus
import com.t8rin.imagetoolbox.lib.portrait_analysis.derive.FaceGeometryRejectionReason
import com.t8rin.imagetoolbox.lib.portrait_analysis.derive.FaceImageSide
import com.t8rin.imagetoolbox.lib.portrait_analysis.derive.FacePoseMode
import com.t8rin.imagetoolbox.lib.portrait_analysis.derive.FaceRegionAwareness
import com.t8rin.imagetoolbox.lib.portrait_analysis.derive.PostacMasterDiagnosticLayers
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ConfidenceSource
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.LandmarkObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.NormalizedPoint3D
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ObservationBackend
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.VisibilityState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class PortraitFaceDiagnosticLayersTest {

    @Test
    fun `rejected face preserves raw and filtered but never exposes accepted geometry`() {
        val raw = observation()
        val decision = FaceGeometryAcceptanceDecision(
            status = FaceGeometryAcceptanceStatus.FACE_DETECTED_GEOMETRY_REJECTED,
            rawObservation = raw,
            activeObservation = null,
            reasons = setOf(FaceGeometryRejectionReason.CONTOUR_CROSSES_UNSUPPORTED_SPACE)
        )

        val layers = PortraitFaceDiagnosticLayers.resolve(raw, frontalAwareness(), decision)

        assertSame(raw, layers.raw)
        assertEquals(PostacMasterDiagnosticLayers.State.DETECTED_REJECTED, layers.state)
        assertNull(layers.accepted)
        assertEquals(1, layers.filtered.landmarks.size)
    }

    @Test
    fun `accepted face exposes only gate approved observation`() {
        val raw = observation()
        val accepted = raw.copy(landmarks = raw.landmarks.filterKeys { it == "nose_base" })
        val decision = FaceGeometryAcceptanceDecision(
            status = FaceGeometryAcceptanceStatus.FACE_DETECTED_GEOMETRY_ACCEPTED,
            rawObservation = raw,
            activeObservation = accepted,
            reasons = emptySet()
        )

        val layers = PortraitFaceDiagnosticLayers.resolve(raw, frontalAwareness(), decision)

        assertEquals(PostacMasterDiagnosticLayers.State.DETECTED_ACCEPTED, layers.state)
        assertSame(accepted, layers.accepted)
        assertEquals(1, layers.counts.acceptedLandmarks)
    }

    private fun observation(): SubjectObservation {
        val nose = LandmarkObservation(
            id = "nose_base",
            point = NormalizedPoint3D(0.5f, 0.5f),
            confidence = null,
            confidenceSource = ConfidenceSource.UNAVAILABLE,
            visibility = VisibilityState.VISIBLE,
            backend = ObservationBackend.ML_KIT_FACE_DETECTION
        )
        return SubjectObservation(
            subjectCount = 1,
            faceCount = 1,
            bodyCount = 0,
            landmarks = mapOf(nose.id to nose),
            regions = emptyMap()
        )
    }

    private fun frontalAwareness() = FaceRegionAwareness(
        poseMode = FacePoseMode.FRONTAL,
        dominantImageSide = FaceImageSide.BALANCED,
        yawDegrees = 0f,
        pitchDegrees = 0f,
        rollDegrees = 0f,
        fullFaceGeometryAllowed = true,
        regions = emptyMap(),
        reasons = emptyList()
    )
}
