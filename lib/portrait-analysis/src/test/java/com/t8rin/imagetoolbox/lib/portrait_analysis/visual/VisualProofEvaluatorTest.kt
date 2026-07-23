package com.t8rin.imagetoolbox.lib.portrait_analysis.visual

import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ConfidenceSource
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ContourObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.LandmarkObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.MeshObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.MeshTriangleObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.NormalizedPoint3D
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ObservationBackend
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.VisibilityState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
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

        val result = VisualProofEvaluator.evaluate(
            observation,
            ImageRenderTransform.fit(1000, 1000, 500, 500)
        )

        assertEquals(VisualProofStatus.PARTIAL, result.status)
        assertEquals(5, result.controlPointIds.size)
        assertNotEquals(VisualProofStatus.PASS, result.status)
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

        val result = VisualProofEvaluator.evaluate(
            observation,
            ImageRenderTransform.fit(1000, 1000, 500, 500)
        )

        assertEquals(VisualProofStatus.VISUAL_VALIDATION_FAILED, result.status)
        assertTrue(result.firstTwentyCoordinates.isNotEmpty())
    }

    @Test
    fun denseMeshIsReadyForHumanReviewButNeverAutomaticPass() {
        val points = linkedMapOf<String, NormalizedPoint3D>()
        points["face_0_mediapipe_bounding_box_top_left"] = point(0.2f, 0.2f)
        points["face_0_mediapipe_bounding_box_top_right"] = point(0.8f, 0.2f)
        points["face_0_mediapipe_bounding_box_bottom_right"] = point(0.8f, 0.9f)
        points["face_0_mediapipe_bounding_box_bottom_left"] = point(0.2f, 0.9f)
        repeat(120) { index ->
            val column = index % 12
            val row = index / 12
            points["face_0_mediapipe_$index"] = point(
                0.25f + column * 0.04f,
                0.25f + row * 0.055f
            )
        }
        val landmarks = points.mapValues { (id, value) -> landmark(id, value) }
        val contours = linkedMapOf(
            "face_0_mediapipe_bounding_box" to contour(
                "face_0_mediapipe_bounding_box",
                listOf(
                    "face_0_mediapipe_bounding_box_top_left",
                    "face_0_mediapipe_bounding_box_top_right",
                    "face_0_mediapipe_bounding_box_bottom_right",
                    "face_0_mediapipe_bounding_box_bottom_left"
                ),
                closed = true
            ),
            "face_0_mediapipe_contour_left_eye" to contour(
                "face_0_mediapipe_contour_left_eye",
                listOf("face_0_mediapipe_0", "face_0_mediapipe_1", "face_0_mediapipe_2"),
                closed = true
            ),
            "face_0_mediapipe_contour_right_eye" to contour(
                "face_0_mediapipe_contour_right_eye",
                listOf("face_0_mediapipe_3", "face_0_mediapipe_4", "face_0_mediapipe_5"),
                closed = true
            ),
            "face_0_mediapipe_contour_left_eyebrow" to contour(
                "face_0_mediapipe_contour_left_eyebrow",
                listOf("face_0_mediapipe_6", "face_0_mediapipe_7"),
                closed = false
            ),
            "face_0_mediapipe_contour_nose" to contour(
                "face_0_mediapipe_contour_nose",
                listOf("face_0_mediapipe_8", "face_0_mediapipe_9"),
                closed = false
            ),
            "face_0_mediapipe_contour_lips" to contour(
                "face_0_mediapipe_contour_lips",
                listOf("face_0_mediapipe_10", "face_0_mediapipe_11", "face_0_mediapipe_12"),
                closed = true
            ),
            "face_0_mediapipe_contour_jawline" to contour(
                "face_0_mediapipe_contour_jawline",
                listOf("face_0_mediapipe_13", "face_0_mediapipe_14", "face_0_mediapipe_15"),
                closed = false
            ),
            "face_0_mediapipe_contour_chin" to contour(
                "face_0_mediapipe_contour_chin",
                listOf("face_0_mediapipe_16", "face_0_mediapipe_17", "face_0_mediapipe_18"),
                closed = false
            )
        )
        val triangles = (0 until 118).map { index ->
            MeshTriangleObservation(
                "face_0_mediapipe_$index",
                "face_0_mediapipe_${index + 1}",
                "face_0_mediapipe_${index + 2}"
            )
        }
        val observation = SubjectObservation(
            subjectCount = 1,
            faceCount = 1,
            bodyCount = 0,
            landmarks = landmarks,
            regions = emptyMap(),
            contours = contours,
            meshes = mapOf(
                "face_0_mediapipe_mesh" to MeshObservation(
                    id = "face_0_mediapipe_mesh",
                    vertexIds = (0 until 120).map { "face_0_mediapipe_$it" }.toSet(),
                    triangles = triangles,
                    backend = ObservationBackend.MEDIAPIPE_FACE_LANDMARKER
                )
            )
        )

        val result = VisualProofEvaluator.evaluate(
            observation,
            ImageRenderTransform.fit(1000, 1000, 500, 500)
        )

        assertEquals(VisualProofStatus.READY_FOR_VISUAL_REVIEW, result.status)
        assertNotEquals(VisualProofStatus.PASS, result.status)
        assertEquals(6, result.semanticContourGroups.size)
    }

    private fun observation(points: Map<String, NormalizedPoint3D>): SubjectObservation {
        val landmarks = points.mapValues { (id, value) -> landmark(id, value) }
        val boxIds = points.keys.filter { it.contains("bounding_box") }
        val contours = if (boxIds.size >= 4) {
            mapOf(
                "face_0_detection_bounding_box" to contour(
                    "face_0_detection_bounding_box",
                    boxIds.take(4),
                    closed = true
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

    private fun landmark(id: String, value: NormalizedPoint3D) = LandmarkObservation(
        id = id,
        point = value,
        confidence = null,
        confidenceSource = ConfidenceSource.UNAVAILABLE,
        visibility = VisibilityState.VISIBLE,
        backend = ObservationBackend.ML_KIT_FACE_DETECTION
    )

    private fun contour(
        id: String,
        ids: List<String>,
        closed: Boolean
    ) = ContourObservation(
        id = id,
        vertexIds = ids,
        closed = closed,
        confidence = null,
        confidenceSource = ConfidenceSource.UNAVAILABLE,
        backend = ObservationBackend.ML_KIT_FACE_DETECTION
    )

    private fun point(x: Float, y: Float) = NormalizedPoint3D(x, y)
}
