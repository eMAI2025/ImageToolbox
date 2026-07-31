/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.feature.portrait_lab

import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ContourObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.LandmarkObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.NormalizedPoint3D
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class FaceDetectionBaselineRuntimePolicyTest {

    @Test
    fun `five detector candidates remain full-image evidence and do not block baseline`() {
        val snapshot = FaceDetectionBaselineRouting.resolve(
            rawObservation = observationWithFaces(5),
            activeObservation = SubjectObservation()
        )

        val decision = FaceDetectionBaselineRuntimePolicy.evaluate(snapshot)

        assertEquals(
            FaceDetectionBaselineRuntimePolicy.State.FULL_IMAGE_EVIDENCE_READY,
            decision.state
        )
        assertEquals(5, decision.detectorCandidateCount)
        assertEquals(listOf(0, 1, 2, 3, 4), decision.candidates.map { it.faceIndex })
        assertFalse(decision.blocksBaseline)
        assertFalse(decision.detectorCandidatesVerifiedAsFaces)
        assertNull(decision.activeFaceIndex)
    }

    @Test
    fun `two detector candidates remain separate without automatic active face`() {
        val snapshot = FaceDetectionBaselineRouting.resolve(
            rawObservation = observationWithFaces(2),
            activeObservation = SubjectObservation()
        )

        val decision = FaceDetectionBaselineRuntimePolicy.evaluate(snapshot)

        assertEquals(2, decision.candidates.size)
        assertEquals(listOf(0, 1), decision.candidates.map { it.faceIndex })
        assertNull(decision.activeFaceIndex)
        assertFalse(decision.blocksBaseline)
    }

    @Test
    fun `no detector candidate remains a valid zero-face baseline result`() {
        val snapshot = FaceDetectionBaselineRouting.resolve(
            rawObservation = SubjectObservation(),
            activeObservation = SubjectObservation()
        )

        val decision = FaceDetectionBaselineRuntimePolicy.evaluate(snapshot)

        assertEquals(FaceDetectionBaselineRuntimePolicy.State.NO_DETECTIONS, decision.state)
        assertEquals(0, decision.detectorCandidateCount)
        assertEquals(emptyList(), decision.candidates)
        assertFalse(decision.blocksBaseline)
        assertNull(decision.activeFaceIndex)
    }

    private fun observationWithFaces(count: Int): SubjectObservation {
        val landmarks = buildMap {
            repeat(count) { faceIndex ->
                val left = 0.02f + faceIndex * 0.16f
                val top = 0.20f
                val right = left + 0.10f
                val bottom = 0.42f
                put(
                    "face_${faceIndex}_bbox_top_left",
                    LandmarkObservation(
                        id = "face_${faceIndex}_bbox_top_left",
                        point = NormalizedPoint3D(left, top)
                    )
                )
                put(
                    "face_${faceIndex}_bbox_top_right",
                    LandmarkObservation(
                        id = "face_${faceIndex}_bbox_top_right",
                        point = NormalizedPoint3D(right, top)
                    )
                )
                put(
                    "face_${faceIndex}_bbox_bottom_right",
                    LandmarkObservation(
                        id = "face_${faceIndex}_bbox_bottom_right",
                        point = NormalizedPoint3D(right, bottom)
                    )
                )
                put(
                    "face_${faceIndex}_bbox_bottom_left",
                    LandmarkObservation(
                        id = "face_${faceIndex}_bbox_bottom_left",
                        point = NormalizedPoint3D(left, bottom)
                    )
                )
            }
        }
        val contours = buildMap {
            repeat(count) { faceIndex ->
                put(
                    "face_${faceIndex}_bounding_box",
                    ContourObservation(
                        id = "face_${faceIndex}_bounding_box",
                        vertexIds = listOf(
                            "face_${faceIndex}_bbox_top_left",
                            "face_${faceIndex}_bbox_top_right",
                            "face_${faceIndex}_bbox_bottom_right",
                            "face_${faceIndex}_bbox_bottom_left"
                        ),
                        closed = true
                    )
                )
            }
        }
        return SubjectObservation(
            subjectCount = count,
            faceCount = count,
            landmarks = landmarks,
            contours = contours
        )
    }
}
