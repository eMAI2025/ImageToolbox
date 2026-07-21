package com.t8rin.imagetoolbox.lib.portrait_analysis.visual

import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ConfidenceSource
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ContourObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.LandmarkObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.NormalizedPoint3D
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ObservationBackend
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.VisibilityState
import org.junit.Assert.assertEquals
import org.junit.Test

class VisualProofEvaluatorTest {

    @Test
    fun faceDetectionGeometryIsPartialWithoutDenseMesh() {
        val observation = observation(
            points = mapOf(
                "face_0_detection_bounding_box_top_left" to point(0.2f, 0.2f),
                "face_0_detection_bounding_box_top_right" to point(0.8f, 0.2f),
                "face_0_detection_bounding_box_bottom_right" to point(0.8f, 0.9f),
                "face_0_detection_bounding_box_bottom_left" to point(0.2f, 0.9f),
                "face_0_detection_landmark_left_eye" to point(0.38f, 0.42f),
                "face_0_detection_landmark_right_eye" to point(0.62f, 0.42f),
                "face_0_detection_landmark_nose_base" to point(0.50f, 0.58f),
                "face_0_detection_landmark_mouth_left" to point(0.42f, 0.72f),
                "face_0_detection_landmark_mouth_right" to point(0.58f, 0.72f)
            )
        )

        val result = VisualProofEvaluator.evaluate(observation)

        assertEquals(VisualProofStatus.PARTIAL, result.status)
        assertEquals(5, result.controlPointIds.size)
    }

    @Test
    fun collapsedPointsFailVisualValidation() {
        val collapsed = (0 until 8).associate { index ->
            val id = if (index < 4) {
                "face_0_detection_bounding_box_$index"
            } else {
                "face_0_detection_landmark_$index"
            }
            id to point(0.5f, 0.5f)
        }
        val observation = observation(collapsed)

        val result = VisualProofEvaluator.evaluate(observation)

        assertEquals(VisualProofStatus.VISUAL_VALIDATION_FAILED, result.status)
    }

    private fun observation(points: Map<String, NormalizedPoint3D>): SubjectObservation {
        val landmarks = points.mapValues { (id, point) ->
            LandmarkObservation(
                id = id,
                point = point,
                confidence = null,
                confidenceSource = ConfidenceSource.UNAVAILABLE,
                visibility = VisibilityState.VISIBLE,
                backend = ObservationBackend.ML_KIT_FACE_DETECTION
            )
        }
        val boxIds = points.keys.filter { it.contains("bounding_box") }
        val contours = if (boxIds.size >= 4) {
            mapOf(
                "face_0_detection_bounding_box" to ContourObservation(
                    id = "face_0_detection_bounding_box",
                    vertexIds = boxIds.take(4),
                    closed = true,
                    confidence = null,
                    confidenceSource = ConfidenceSource.UNAVAILABLE,
                    backend = ObservationBackend.ML_KIT_FACE_DETECTION
                )
            )
        } else {
            emptyMap()
        }
        return SubjectObservation(
            subjectCount = 1,
            faceCount = 1,
            bodyCount = 0,
            landmarks = landmarks,
            regions = emptyMap(),
            contours = contours
        )
    }

    private fun point(x: Float, y: Float) = NormalizedPoint3D(x, y)
}
