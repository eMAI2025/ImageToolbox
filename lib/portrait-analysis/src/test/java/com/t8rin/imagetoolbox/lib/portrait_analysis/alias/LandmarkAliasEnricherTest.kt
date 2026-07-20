/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis.alias

import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ConfidenceSource
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.LandmarkObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.NormalizedPoint3D
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ObservationBackend
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.VisibilityState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class LandmarkAliasEnricherTest {

    @Test
    fun `direct alias preserves point and derives confidence evidence`() {
        val source = landmark("raw_61", x = 0.2f, confidence = 0.94f)
        val enriched = LandmarkAliasEnricher.enrich(
            observation = observation(source),
            rules = listOf(
                LandmarkAliasRule(
                    aliasId = "mouth_left_corner",
                    sourceLandmarkIds = listOf("raw_61"),
                    strategy = LandmarkAliasStrategy.DIRECT
                )
            )
        )

        val alias = enriched.landmarks.getValue("mouth_left_corner")
        assertEquals(source.point, alias.point)
        assertEquals(0.94f, alias.confidence)
        assertEquals(ConfidenceSource.DERIVED, alias.confidenceSource)
        assertEquals(source.backend, alias.backend)
    }

    @Test
    fun `centroid uses weakest source confidence and worst visibility`() {
        val left = landmark("upper", x = 0.4f, confidence = 0.97f)
        val right = landmark(
            id = "lower",
            x = 0.6f,
            confidence = 0.81f,
            visibility = VisibilityState.PARTIALLY_VISIBLE
        )
        val enriched = LandmarkAliasEnricher.enrich(
            observation = observation(left, right),
            rules = listOf(
                LandmarkAliasRule(
                    aliasId = "mouth_center",
                    sourceLandmarkIds = listOf("upper", "lower"),
                    strategy = LandmarkAliasStrategy.CENTROID
                )
            )
        )

        val alias = enriched.landmarks.getValue("mouth_center")
        assertEquals(0.5f, alias.point.x)
        assertEquals(0.81f, alias.confidence)
        assertEquals(VisibilityState.PARTIALLY_VISIBLE, alias.visibility)
    }

    @Test
    fun `missing source prevents alias creation`() {
        val enriched = LandmarkAliasEnricher.enrich(
            observation = observation(landmark("upper", x = 0.4f, confidence = 0.9f)),
            rules = listOf(
                LandmarkAliasRule(
                    aliasId = "mouth_center",
                    sourceLandmarkIds = listOf("upper", "lower"),
                    strategy = LandmarkAliasStrategy.CENTROID
                )
            )
        )

        assertFalse("mouth_center" in enriched.landmarks)
    }

    @Test
    fun `unavailable confidence remains unavailable`() {
        val source = landmark("raw", x = 0.5f, confidence = null)
        val enriched = LandmarkAliasEnricher.enrich(
            observation = observation(source),
            rules = listOf(
                LandmarkAliasRule(
                    aliasId = "alias",
                    sourceLandmarkIds = listOf("raw"),
                    strategy = LandmarkAliasStrategy.DIRECT
                )
            )
        )

        val alias = enriched.landmarks.getValue("alias")
        assertNull(alias.confidence)
        assertEquals(ConfidenceSource.UNAVAILABLE, alias.confidenceSource)
    }

    private fun landmark(
        id: String,
        x: Float,
        confidence: Float?,
        visibility: VisibilityState = VisibilityState.VISIBLE
    ) = LandmarkObservation(
        id = id,
        point = NormalizedPoint3D(x = x, y = 0.5f, z = 0f),
        confidence = confidence,
        confidenceSource = if (confidence == null) {
            ConfidenceSource.UNAVAILABLE
        } else {
            ConfidenceSource.DIRECT
        },
        visibility = visibility,
        backend = ObservationBackend.MEDIAPIPE_FACE_LANDMARKER
    )

    private fun observation(vararg landmarks: LandmarkObservation) = SubjectObservation(
        subjectCount = 1,
        faceCount = 1,
        bodyCount = 0,
        landmarks = landmarks.associateBy { it.id },
        regions = emptyMap()
    )
}
