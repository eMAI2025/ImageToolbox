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
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ConfidenceSource
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ContourObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.LandmarkObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.NormalizedPoint3D
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ObservationBackend
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.RegionObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.VisibilityState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BodySilhouetteSectionValidatorTest {

    @Test
    fun `rejects arm section that spans the torso instead of the local arm`() {
        val source = enrichment(
            sectionId = "derived_body_left_upper_arm_section",
            sectionStart = point("section_a", 0.20f, 0.40f),
            sectionEnd = point("section_b", 0.80f, 0.40f),
            firstPoseId = PortraitLandmarkId.LEFT_SHOULDER,
            firstPose = point(PortraitLandmarkId.LEFT_SHOULDER, 0.30f, 0.30f),
            secondPoseId = PortraitLandmarkId.LEFT_ELBOW,
            secondPose = point(PortraitLandmarkId.LEFT_ELBOW, 0.35f, 0.50f),
            regionId = PortraitRegionId.ARM_CONTOURS
        )

        val result = BodySilhouetteSectionValidator.validate(source, 1000, 1000)

        assertEquals(1, result.rejectedSections.size)
        assertEquals(
            BodySectionRejectionReason.WIDTH_OUTLIER,
            result.rejectedSections.single().reason
        )
        assertFalse("derived_body_left_upper_arm_section" in result.enrichment.observation.contours)
        assertFalse(PortraitRegionId.ARM_CONTOURS in result.enrichment.observation.regions)
        assertFalse(PortraitRegionId.ARM_CONTOURS in result.enrichment.derivedRegionIds)
        assertTrue(
            result.enrichment.skippedSections.any {
                it.sectionId == "derived_body_left_upper_arm_section" &&
                    it.itemId.orEmpty().startsWith("WIDTH_OUTLIER_")
            }
        )
    }

    @Test
    fun `rejects a short section displaced onto unrelated foreground`() {
        val source = enrichment(
            sectionId = "derived_body_left_forearm_section",
            sectionStart = point("section_a", 0.70f, 0.46f),
            sectionEnd = point("section_b", 0.78f, 0.46f),
            firstPoseId = PortraitLandmarkId.LEFT_ELBOW,
            firstPose = point(PortraitLandmarkId.LEFT_ELBOW, 0.28f, 0.42f),
            secondPoseId = PortraitLandmarkId.LEFT_WRIST,
            secondPose = point(PortraitLandmarkId.LEFT_WRIST, 0.34f, 0.62f),
            regionId = PortraitRegionId.ARM_CONTOURS
        )

        val result = BodySilhouetteSectionValidator.validate(source, 1000, 1000)

        assertEquals(1, result.rejectedSections.size)
        assertEquals(
            BodySectionRejectionReason.SECTION_NON_LOCAL,
            result.rejectedSections.single().reason
        )
        assertFalse("derived_body_left_forearm_section" in result.enrichment.observation.contours)
        assertFalse("section_a" in result.enrichment.observation.landmarks)
        assertFalse("section_b" in result.enrichment.observation.landmarks)
        assertFalse(PortraitRegionId.ARM_CONTOURS in result.enrichment.observation.regions)
    }

    @Test
    fun `fails closed when an active section has missing local pose evidence`() {
        val source = enrichment(
            sectionId = "derived_body_right_thigh_section",
            sectionStart = point("section_a", 0.52f, 0.66f),
            sectionEnd = point("section_b", 0.64f, 0.66f),
            firstPoseId = PortraitLandmarkId.RIGHT_HIP,
            firstPose = point(PortraitLandmarkId.RIGHT_HIP, 0.58f, 0.52f),
            secondPoseId = PortraitLandmarkId.RIGHT_KNEE,
            secondPose = point(PortraitLandmarkId.RIGHT_KNEE, 0.58f, 0.74f),
            regionId = PortraitRegionId.LEG_CONTOURS
        ).let { enrichment ->
            enrichment.copy(
                observation = enrichment.observation.copy(
                    landmarks = enrichment.observation.landmarks - PortraitLandmarkId.RIGHT_KNEE
                )
            )
        }

        val result = BodySilhouetteSectionValidator.validate(source, 1000, 1000)

        assertEquals(1, result.rejectedSections.size)
        assertEquals(
            BodySectionRejectionReason.MISSING_LOCAL_EVIDENCE,
            result.rejectedSections.single().reason
        )
        assertFalse("derived_body_right_thigh_section" in result.enrichment.observation.contours)
        assertFalse(PortraitRegionId.LEG_CONTOURS in result.enrichment.observation.regions)
    }

    @Test
    fun `keeps a local calf section within width and locality thresholds`() {
        val source = enrichment(
            sectionId = "derived_body_right_calf_section",
            sectionStart = point("section_a", 0.52f, 0.70f),
            sectionEnd = point("section_b", 0.60f, 0.70f),
            firstPoseId = PortraitLandmarkId.RIGHT_KNEE,
            firstPose = point(PortraitLandmarkId.RIGHT_KNEE, 0.56f, 0.60f),
            secondPoseId = PortraitLandmarkId.RIGHT_ANKLE,
            secondPose = point(PortraitLandmarkId.RIGHT_ANKLE, 0.56f, 0.85f),
            regionId = PortraitRegionId.LEG_CONTOURS
        )

        val result = BodySilhouetteSectionValidator.validate(source, 1000, 1000)

        assertTrue(result.rejectedSections.isEmpty())
        assertTrue("derived_body_right_calf_section" in result.enrichment.observation.contours)
        assertTrue(PortraitRegionId.LEG_CONTOURS in result.enrichment.observation.regions)
    }

    private fun enrichment(
        sectionId: String,
        sectionStart: LandmarkObservation,
        sectionEnd: LandmarkObservation,
        firstPoseId: String,
        firstPose: LandmarkObservation,
        secondPoseId: String,
        secondPose: LandmarkObservation,
        regionId: String
    ): BodySilhouetteEnrichmentResult {
        val contour = ContourObservation(
            id = sectionId,
            vertexIds = listOf(sectionStart.id, sectionEnd.id),
            closed = false,
            confidence = 0.95f,
            confidenceSource = ConfidenceSource.DERIVED,
            backend = ObservationBackend.DERIVED_BODY_SILHOUETTE
        )
        val region = RegionObservation(
            id = regionId,
            confidence = 0.95f,
            confidenceSource = ConfidenceSource.DERIVED,
            occlusion = null,
            pixelCoverage = 0.02f,
            backend = ObservationBackend.DERIVED_BODY_SILHOUETTE
        )
        val observation = SubjectObservation(
            subjectCount = 1,
            faceCount = 0,
            bodyCount = 1,
            landmarks = mapOf(
                sectionStart.id to sectionStart,
                sectionEnd.id to sectionEnd,
                firstPoseId to firstPose,
                secondPoseId to secondPose
            ),
            regions = mapOf(regionId to region),
            contours = mapOf(sectionId to contour)
        )
        return BodySilhouetteEnrichmentResult(
            observation = observation,
            derivedRegionIds = setOf(regionId),
            skippedSections = emptyList()
        )
    }

    private fun point(id: String, x: Float, y: Float) = LandmarkObservation(
        id = id,
        point = NormalizedPoint3D(x, y),
        confidence = 0.95f,
        confidenceSource = ConfidenceSource.DIRECT,
        visibility = VisibilityState.VISIBLE,
        backend = if (id.startsWith("section_")) {
            ObservationBackend.DERIVED_BODY_SILHOUETTE
        } else {
            ObservationBackend.ML_KIT_POSE
        }
    )
}
