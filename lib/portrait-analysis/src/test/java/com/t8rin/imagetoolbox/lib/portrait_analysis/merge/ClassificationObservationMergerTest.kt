/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis.merge

import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ClassificationObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ObservationBackend
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassificationObservationMergerTest {

    @Test
    fun `merges independent face classifications`() {
        val result = SubjectObservationMerger.merge(
            listOf(
                observation(
                    "face_0_detection_smiling_probability",
                    0.8f
                ),
                observation(
                    "face_0_detection_left_eye_open_probability",
                    0.9f
                )
            )
        )

        assertTrue(result is ObservationMergeResult.Success)
        val merged = (result as ObservationMergeResult.Success).observation
        assertEquals(2, merged.classifications.size)
    }

    @Test
    fun `rejects different probabilities using the same id`() {
        val result = SubjectObservationMerger.merge(
            listOf(
                observation("shared_probability", 0.2f),
                observation("shared_probability", 0.8f)
            )
        )

        assertTrue(result is ObservationMergeResult.Conflict)
        assertTrue(
            (result as ObservationMergeResult.Conflict).conflicts.any {
                it.type == ObservationMergeConflictType.CLASSIFICATION_ID_COLLISION &&
                    it.itemId == "shared_probability"
            }
        )
    }

    private fun observation(id: String, probability: Float) = SubjectObservation(
        subjectCount = 1,
        faceCount = 1,
        bodyCount = 0,
        landmarks = emptyMap(),
        regions = emptyMap(),
        classifications = mapOf(
            id to ClassificationObservation(
                id = id,
                probability = probability,
                backend = ObservationBackend.ML_KIT_FACE_DETECTION
            )
        )
    )
}
