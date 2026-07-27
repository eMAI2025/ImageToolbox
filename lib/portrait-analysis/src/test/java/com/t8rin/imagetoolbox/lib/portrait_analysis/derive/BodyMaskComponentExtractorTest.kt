/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis.derive

import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ConfidenceMask
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ConfidenceSource
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.LandmarkObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.NormalizedPoint3D
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ObservationBackend
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.RegionObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SemanticMaskObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.VisibilityState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyMaskComponentExtractorTest {

    @Test
    fun `detached halo is preserved in raw evidence and removed from filtered mask`() {
        val observation = observationWithDetachedHalo()
        val visibility = BodyLandmarkVisibilityGate.evaluate(observation)

        val result = BodyMaskComponentExtractor.extract(observation, visibility)

        assertNotNull(result)
        result!!
        assertEquals(2, result.assessment.rawComponents.size)
        assertEquals(1, result.assessment.filteredComponents.size)
        assertTrue(
            result.assessment.rejectedComponents.any { decision ->
                BodyMaskComponentGate.RejectionReason.DISCONNECTED_HALO_ABOVE_HEAD in
                    decision.reasons
            }
        )
        assertEquals(0.9f, result.rawMask[1, 0])
        assertEquals(0f, result.filteredMask[1, 0])
        assertEquals(0.9f, result.filteredMask[3, 4])
    }

    @Test
    fun `subthreshold smear is removed even when close to accepted person`() {
        val observation = observationWithDetachedHalo(subthresholdSmear = true)
        val visibility = BodyLandmarkVisibilityGate.evaluate(observation)

        val result = BodyMaskComponentExtractor.extract(observation, visibility)!!

        assertEquals(0.49f, result.rawMask[7, 2])
        assertEquals(0f, result.filteredMask[7, 2])
        assertTrue(result.acceptedPixelCount > 0)
    }

    @Test
    fun `filtered mask is applied without modifying raw observation`() {
        val observation = observationWithDetachedHalo()
        val visibility = BodyLandmarkVisibilityGate.evaluate(observation)
        val result = BodyMaskComponentExtractor.extract(observation, visibility)!!

        val active = result.applyTo(observation)

        assertEquals(0.9f, observation.masks.getValue("subject_mask").mask[1, 0])
        assertEquals(0f, active.masks.getValue("subject_mask").mask[1, 0])
        assertTrue(active.regions.getValue("subject_mask").pixelCoverage < 1f)
    }

    private fun observationWithDetachedHalo(
        subthresholdSmear: Boolean = false
    ): SubjectObservation {
        val width = 8
        val height = 8
        val values = FloatArray(width * height)
        // Detached two-pixel halo above the verified head.
        values[index(1, 0, width)] = 0.9f
        values[index(2, 0, width)] = 0.9f
        // Main person component containing shoulders and hips.
        for (y in 3..7) {
            for (x in 2..5) values[index(x, y, width)] = 0.9f
        }
        if (subthresholdSmear) values[index(7, 2, width)] = 0.49f
        val mask = SemanticMaskObservation(
            id = "subject_mask",
            mask = ConfidenceMask(width, height, values),
            backend = ObservationBackend.ML_KIT_SELFIE_SEGMENTATION
        )
        val landmarks = listOf(
            posePoint("nose", 0.50f, 0.25f),
            posePoint("left_shoulder", 0.37f, 0.50f),
            posePoint("right_shoulder", 0.62f, 0.50f),
            posePoint("left_hip", 0.37f, 0.75f),
            posePoint("right_hip", 0.62f, 0.75f),
            posePoint("left_elbow", 0.30f, 0.62f),
            posePoint("right_elbow", 0.68f, 0.62f),
            posePoint("left_wrist", 0.30f, 0.75f),
            posePoint("right_wrist", 0.68f, 0.75f),
            posePoint("left_knee", 0.40f, 0.87f),
            posePoint("right_knee", 0.60f, 0.87f),
            posePoint("left_ankle", 0.40f, 0.98f),
            posePoint("right_ankle", 0.60f, 0.98f)
        ).associateBy { it.id }
        return SubjectObservation(
            subjectCount = 1,
            faceCount = 0,
            bodyCount = 1,
            landmarks = landmarks,
            regions = mapOf(
                "subject_mask" to RegionObservation(
                    id = "subject_mask",
                    confidence = 0.9f,
                    confidenceSource = ConfidenceSource.DERIVED,
                    occlusion = null,
                    pixelCoverage = values.count { it >= 0.5f }.toFloat() / values.size,
                    backend = ObservationBackend.ML_KIT_SELFIE_SEGMENTATION
                )
            ),
            masks = mapOf("subject_mask" to mask)
        )
    }

    private fun posePoint(id: String, x: Float, y: Float) = LandmarkObservation(
        id = id,
        point = NormalizedPoint3D(x, y),
        confidence = 0.95f,
        confidenceSource = ConfidenceSource.DIRECT,
        visibility = VisibilityState.VISIBLE,
        backend = ObservationBackend.ML_KIT_POSE
    )

    private fun index(x: Int, y: Int, width: Int): Int = y * width + x
}
