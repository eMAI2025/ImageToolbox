/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis.merge

import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ConfidenceSource
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.LandmarkObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.NormalizedPoint3D
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ObservationBackend
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.PoseObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.RegionObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.VisibilityState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubjectObservationMergerTest {

    @Test
    fun `merges detector records and uses maximum counts`() {
        val face = SubjectObservation(
            subjectCount = 1,
            faceCount = 1,
            bodyCount = 0,
            landmarks = mapOf("face_0_mesh_1" to landmark("face_0_mesh_1")),
            regions = emptyMap()
        )
        val body = SubjectObservation(
            subjectCount = 1,
            faceCount = 0,
            bodyCount = 1,
            landmarks = mapOf("left_shoulder" to landmark("left_shoulder")),
            regions = mapOf("subject_mask" to region("subject_mask"))
        )

        val result = SubjectObservationMerger.merge(listOf(face, body))

        assertTrue(result is ObservationMergeResult.Success)
        val merged = (result as ObservationMergeResult.Success).observation
        assertEquals(1, merged.subjectCount)
        assertEquals(1, merged.faceCount)
        assertEquals(1, merged.bodyCount)
        assertEquals(2, merged.landmarks.size)
        assertEquals(1, merged.regions.size)
    }

    @Test
    fun `accepts identical duplicate record`() {
        val point = landmark("left_shoulder")
        val result = SubjectObservationMerger.merge(
            listOf(
                observation(landmarks = mapOf(point.id to point)),
                observation(landmarks = mapOf(point.id to point))
            )
        )

        assertTrue(result is ObservationMergeResult.Success)
    }

    @Test
    fun `rejects different records with the same id`() {
        val first = landmark("left_shoulder")
        val second = first.copy(point = NormalizedPoint3D(0.7f, 0.5f))

        val result = SubjectObservationMerger.merge(
            listOf(
                observation(landmarks = mapOf(first.id to first)),
                observation(landmarks = mapOf(second.id to second))
            )
        )

        assertTrue(result is ObservationMergeResult.Conflict)
        val conflict = (result as ObservationMergeResult.Conflict).conflicts.single()
        assertEquals(
            ObservationMergeConflictType.LANDMARK_ID_COLLISION,
            conflict.type
        )
        assertEquals("left_shoulder", conflict.itemId)
    }

    @Test
    fun `rejects conflicting pose axes`() {
        val result = SubjectObservationMerger.merge(
            listOf(
                observation(pose = PoseObservation(yawDegrees = 5f)),
                observation(pose = PoseObservation(yawDegrees = 12f))
            )
        )

        assertTrue(result is ObservationMergeResult.Conflict)
        val conflicts = (result as ObservationMergeResult.Conflict).conflicts
        assertTrue(
            conflicts.any { it.type == ObservationMergeConflictType.POSE_YAW_CONFLICT }
        )
    }

    private fun observation(
        landmarks: Map<String, LandmarkObservation> = emptyMap(),
        pose: PoseObservation = PoseObservation()
    ) = SubjectObservation(
        subjectCount = 1,
        faceCount = 0,
        bodyCount = 1,
        landmarks = landmarks,
        regions = emptyMap(),
        pose = pose
    )

    private fun landmark(id: String) = LandmarkObservation(
        id = id,
        point = NormalizedPoint3D(0.5f, 0.5f),
        confidence = 0.95f,
        confidenceSource = ConfidenceSource.DIRECT,
        visibility = VisibilityState.VISIBLE,
        backend = ObservationBackend.ML_KIT_POSE
    )

    private fun region(id: String) = RegionObservation(
        id = id,
        confidence = 0.95f,
        confidenceSource = ConfidenceSource.DERIVED,
        occlusion = null,
        pixelCoverage = 0.4f,
        backend = ObservationBackend.ML_KIT_SELFIE_SEGMENTATION
    )
}
