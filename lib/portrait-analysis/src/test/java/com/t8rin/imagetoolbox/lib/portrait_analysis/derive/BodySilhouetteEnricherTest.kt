/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis.derive

import com.t8rin.imagetoolbox.lib.portrait_analysis.catalog.PortraitLandmarkId
import com.t8rin.imagetoolbox.lib.portrait_analysis.catalog.PortraitRegionId
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ConfidenceMask
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ConfidenceSource
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.LandmarkObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.NormalizedPoint3D
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ObservationBackend
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SemanticMaskObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.VisibilityState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BodySilhouetteEnricherTest {

    @Test
    fun `derives torso and limb regions from rectangular foreground`() {
        val result = BodySilhouetteEnricher.enrich(completeObservation())

        assertEquals(
            setOf(
                PortraitRegionId.SHOULDER_CONTOUR,
                PortraitRegionId.WAIST_CONTOUR,
                PortraitRegionId.HIP_CONTOUR,
                PortraitRegionId.ARM_CONTOURS,
                PortraitRegionId.LEG_CONTOURS
            ),
            result.derivedRegionIds
        )
        assertTrue(result.skippedSections.isEmpty())
        assertTrue(result.observation.contours.containsKey("derived_body_shoulder_section"))
        assertTrue(result.observation.regions.getValue(
            PortraitRegionId.SHOULDER_CONTOUR
        ).confidence!! >= 0.9f)

        val shoulder = result.observation.contours.getValue(
            "derived_body_shoulder_section"
        )
        val left = result.observation.landmarks.getValue(shoulder.vertexIds.first()).point
        val right = result.observation.landmarks.getValue(shoulder.vertexIds.last()).point
        assertEquals(5f / 19f, left.x, 0.0001f)
        assertEquals(14f / 19f, right.x, 0.0001f)
    }

    @Test
    fun `does not derive anything without subject mask`() {
        val source = completeObservation().copy(masks = emptyMap())

        val result = BodySilhouetteEnricher.enrich(source)

        assertTrue(result.derivedRegionIds.isEmpty())
        assertEquals(source, result.observation)
        assertEquals(
            BodySilhouetteSkipReason.SUBJECT_MASK_MISSING,
            result.skippedSections.single().reason
        )
    }

    @Test
    fun `does not derive body contours for multiple bodies`() {
        val source = completeObservation().copy(bodyCount = 2, subjectCount = 2)

        val result = BodySilhouetteEnricher.enrich(source)

        assertTrue(result.derivedRegionIds.isEmpty())
        assertEquals(
            BodySilhouetteSkipReason.BODY_COUNT_UNSUPPORTED,
            result.skippedSections.single().reason
        )
    }

    @Test
    fun `missing wrist prevents aggregate arm region without blocking torso`() {
        val source = completeObservation().copy(
            landmarks = completeObservation().landmarks - PortraitLandmarkId.LEFT_WRIST
        )

        val result = BodySilhouetteEnricher.enrich(source)

        assertTrue(result.derivedRegionIds.contains(PortraitRegionId.SHOULDER_CONTOUR))
        assertFalse(result.derivedRegionIds.contains(PortraitRegionId.ARM_CONTOURS))
        assertTrue(
            result.skippedSections.any {
                it.sectionId == "derived_body_left_forearm_section" &&
                    it.reason == BodySilhouetteSkipReason.LANDMARK_MISSING
            }
        )
    }

    @Test
    fun `unavailable joint confidence prevents dependent section`() {
        val source = completeObservation()
        val leftShoulder = source.landmarks.getValue(PortraitLandmarkId.LEFT_SHOULDER)
        val changed = source.copy(
            landmarks = source.landmarks + (
                PortraitLandmarkId.LEFT_SHOULDER to leftShoulder.copy(
                    confidence = null,
                    confidenceSource = ConfidenceSource.UNAVAILABLE
                )
            )
        )

        val result = BodySilhouetteEnricher.enrich(changed)

        assertFalse(result.derivedRegionIds.contains(PortraitRegionId.SHOULDER_CONTOUR))
        assertTrue(
            result.skippedSections.any {
                it.sectionId == "derived_body_shoulder_section" &&
                    it.reason == BodySilhouetteSkipReason.LANDMARK_CONFIDENCE_UNAVAILABLE
            }
        )
    }

    @Test
    fun `does not overwrite an existing derived identifier`() {
        val source = completeObservation()
        val colliding = source.copy(
            landmarks = source.landmarks + (
                "derived_body_shoulder_section_a" to landmark(
                    "derived_body_shoulder_section_a",
                    0.1f,
                    0.1f
                )
            )
        )

        val result = BodySilhouetteEnricher.enrich(colliding)

        assertTrue(result.derivedRegionIds.isEmpty())
        assertEquals(colliding, result.observation)
        assertTrue(
            result.skippedSections.any {
                it.reason == BodySilhouetteSkipReason.IDENTIFIER_COLLISION
            }
        )
    }

    private fun completeObservation(): SubjectObservation {
        val points = mapOf(
            PortraitLandmarkId.LEFT_SHOULDER to Pair(0.35f, 0.25f),
            PortraitLandmarkId.RIGHT_SHOULDER to Pair(0.65f, 0.25f),
            PortraitLandmarkId.LEFT_ELBOW to Pair(0.35f, 0.40f),
            PortraitLandmarkId.RIGHT_ELBOW to Pair(0.65f, 0.40f),
            PortraitLandmarkId.LEFT_WRIST to Pair(0.35f, 0.55f),
            PortraitLandmarkId.RIGHT_WRIST to Pair(0.65f, 0.55f),
            PortraitLandmarkId.LEFT_HIP to Pair(0.40f, 0.55f),
            PortraitLandmarkId.RIGHT_HIP to Pair(0.60f, 0.55f),
            PortraitLandmarkId.LEFT_KNEE to Pair(0.42f, 0.72f),
            PortraitLandmarkId.RIGHT_KNEE to Pair(0.58f, 0.72f),
            PortraitLandmarkId.LEFT_ANKLE to Pair(0.43f, 0.90f),
            PortraitLandmarkId.RIGHT_ANKLE to Pair(0.57f, 0.90f)
        )
        val values = FloatArray(20 * 20)
        for (y in 2..18) {
            for (x in 5..14) {
                values[y * 20 + x] = 1f
            }
        }
        return SubjectObservation(
            subjectCount = 1,
            faceCount = 0,
            bodyCount = 1,
            landmarks = points.mapValues { (id, point) ->
                landmark(id, point.first, point.second)
            },
            regions = emptyMap(),
            masks = mapOf(
                PortraitRegionId.SUBJECT_MASK to SemanticMaskObservation(
                    id = PortraitRegionId.SUBJECT_MASK,
                    mask = ConfidenceMask(20, 20, values),
                    backend = ObservationBackend.ML_KIT_SELFIE_SEGMENTATION
                )
            )
        )
    }

    private fun landmark(id: String, x: Float, y: Float) = LandmarkObservation(
        id = id,
        point = NormalizedPoint3D(x, y),
        confidence = 0.95f,
        confidenceSource = ConfidenceSource.DIRECT,
        visibility = VisibilityState.VISIBLE,
        backend = ObservationBackend.ML_KIT_POSE
    )
}
