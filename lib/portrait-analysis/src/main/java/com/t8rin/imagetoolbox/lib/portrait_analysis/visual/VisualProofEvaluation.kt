/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis.visual

import com.t8rin.imagetoolbox.lib.portrait_analysis.model.LandmarkObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.NormalizedPoint3D
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation
import kotlin.math.max
import kotlin.math.min

enum class VisualProofStatus {
    NO_DATA,
    FAIL,
    VISUAL_VALIDATION_FAILED,
    PARTIAL,
    READY_FOR_VISUAL_REVIEW,
    PASS
}

data class VisualProofEvaluation(
    val status: VisualProofStatus,
    val faceCount: Int,
    val landmarkCount: Int,
    val contourCount: Int,
    val triangleCount: Int,
    val inFrameLandmarkRatio: Float,
    val normalizedLandmarkSpread: Float,
    val renderedLandmarkSpreadPixels: Float,
    val boundingBox: PixelRect?,
    val renderedBoundingBox: PixelRect?,
    val controlPointIds: Set<String>,
    val semanticContourGroups: Set<String>,
    val reasons: List<String>,
    val firstTwentyCoordinates: List<DiagnosticCoordinate>
)

data class DiagnosticCoordinate(
    val id: String,
    val x: Float,
    val y: Float,
    val backend: String
)

object VisualProofEvaluator {

    private const val MIN_IN_FRAME_RATIO = 0.80f
    private const val MIN_NORMALIZED_SPREAD = 0.0025f
    private const val MIN_RENDERED_SPREAD_PIXELS = 64f
    private const val MIN_RENDERED_BOUNDING_AREA_PIXELS = 256f
    private const val MIN_MESH_VERTICES_FOR_REVIEW = 100

    private val requiredControlPointSuffixes = linkedMapOf(
        "left_eye" to listOf("detection_landmark_left_eye", "left_eye"),
        "right_eye" to listOf("detection_landmark_right_eye", "right_eye"),
        "nose" to listOf("detection_landmark_nose_base", "nose_base", "nose"),
        "mouth_left" to listOf("detection_landmark_mouth_left", "mouth_left"),
        "mouth_right" to listOf("detection_landmark_mouth_right", "mouth_right")
    )

    private val semanticContourCandidates = linkedMapOf(
        "eyes" to listOf("left_eye", "right_eye"),
        "eyebrows" to listOf("eyebrow", "eye_brow", "brow"),
        "nose" to listOf("nose"),
        "mouth" to listOf("mouth", "lip", "lips"),
        "jawline" to listOf("jawline", "face_oval", "detection_contour_face"),
        "chin" to listOf("chin")
    )

    fun evaluate(
        observation: SubjectObservation,
        transform: ImageRenderTransform? = null
    ): VisualProofEvaluation {
        val landmarks = observation.landmarks.values.sortedBy(LandmarkObservation::id)
        val inFrame = landmarks.filter { it.point.x in 0f..1f && it.point.y in 0f..1f }
        val inFrameRatio = if (landmarks.isEmpty()) 0f else inFrame.size.toFloat() / landmarks.size
        val spreadRect = boundsOfLandmarks(inFrame)
        val spread = spreadRect?.area ?: 0f
        val boundingBox = findBoundingBox(observation)
        val renderedBoundingBox = if (transform != null && boundingBox != null) {
            transform.normalizedRectToPreview(
                boundingBox.left,
                boundingBox.top,
                boundingBox.right,
                boundingBox.bottom
            )
        } else {
            null
        }
        val renderedSpread = if (transform != null && spreadRect != null) {
            transform.normalizedRectToPreview(
                spreadRect.left,
                spreadRect.top,
                spreadRect.right,
                spreadRect.bottom
            ).area
        } else {
            0f
        }
        val controlPoints = findControlPoints(observation)
        val semanticContours = findSemanticContourGroups(observation)
        val triangleCount = observation.meshes.values.sumOf { it.triangles.size }
        val meshVertexCount = observation.meshes.values.sumOf { it.vertexIds.size }
        val reasons = mutableListOf<String>()

        val status = when {
            observation.faceCount <= 0 -> {
                reasons += "No face detected"
                VisualProofStatus.FAIL
            }
            landmarks.isEmpty() -> {
                reasons += "Detector returned no landmark or contour vertices"
                VisualProofStatus.FAIL
            }
            inFrameRatio < MIN_IN_FRAME_RATIO -> {
                reasons += "Too many points are outside the normalized image frame"
                VisualProofStatus.VISUAL_VALIDATION_FAILED
            }
            spread < MIN_NORMALIZED_SPREAD -> {
                reasons += "Landmarks collapse into an implausibly small normalized area"
                VisualProofStatus.VISUAL_VALIDATION_FAILED
            }
            boundingBox == null -> {
                reasons += "Face geometry exists but no bounding box was preserved"
                VisualProofStatus.VISUAL_VALIDATION_FAILED
            }
            transform == null -> {
                reasons += "No bitmap-to-preview transform was supplied"
                VisualProofStatus.PARTIAL
            }
            renderedBoundingBox == null || renderedBoundingBox.area < MIN_RENDERED_BOUNDING_AREA_PIXELS -> {
                reasons += "Rendered face bounding box is missing or too small"
                VisualProofStatus.VISUAL_VALIDATION_FAILED
            }
            !renderedBoundingBox.intersectsPreview(transform) -> {
                reasons += "Rendered face bounding box is outside the diagnostic preview"
                VisualProofStatus.VISUAL_VALIDATION_FAILED
            }
            renderedSpread < MIN_RENDERED_SPREAD_PIXELS -> {
                reasons += "Rendered landmarks collapse into one visible point"
                VisualProofStatus.VISUAL_VALIDATION_FAILED
            }
            triangleCount > 0 && meshVertexCount >= MIN_MESH_VERTICES_FOR_REVIEW -> {
                if (semanticContours.size < semanticContourCandidates.size) {
                    reasons += "Dense mesh is present but semantic contour groups are incomplete"
                }
                reasons += "Automatic PASS is disabled; inspect the frozen diagnostic image"
                VisualProofStatus.READY_FOR_VISUAL_REVIEW
            }
            else -> {
                if (controlPoints.size < requiredControlPointSuffixes.size) {
                    reasons += "Only ${controlPoints.size}/${requiredControlPointSuffixes.size} control points are available"
                }
                if (semanticContours.isEmpty()) reasons += "No semantic face contours are available"
                if (triangleCount == 0) reasons += "No dense mesh triangles are available"
                VisualProofStatus.PARTIAL
            }
        }

        return VisualProofEvaluation(
            status = status,
            faceCount = observation.faceCount,
            landmarkCount = landmarks.size,
            contourCount = observation.contours.size,
            triangleCount = triangleCount,
            inFrameLandmarkRatio = inFrameRatio,
            normalizedLandmarkSpread = spread,
            renderedLandmarkSpreadPixels = renderedSpread,
            boundingBox = boundingBox,
            renderedBoundingBox = renderedBoundingBox,
            controlPointIds = controlPoints,
            semanticContourGroups = semanticContours,
            reasons = reasons,
            firstTwentyCoordinates = landmarks.take(20).map {
                DiagnosticCoordinate(
                    id = it.id,
                    x = it.point.x,
                    y = it.point.y,
                    backend = it.backend.name
                )
            }
        )
    }

    private fun PixelRect.intersectsPreview(transform: ImageRenderTransform): Boolean =
        right > 0f && bottom > 0f && left < transform.previewWidth && top < transform.previewHeight

    private fun findControlPoints(observation: SubjectObservation): Set<String> =
        requiredControlPointSuffixes.mapNotNullTo(linkedSetOf()) { (semanticId, candidates) ->
            observation.landmarks.keys.firstOrNull { id ->
                candidates.any { candidate -> id.endsWith(candidate) || id == candidate }
            }?.let { semanticId }
        }

    private fun findSemanticContourGroups(observation: SubjectObservation): Set<String> =
        semanticContourCandidates.mapNotNullTo(linkedSetOf()) { (group, candidates) ->
            observation.contours.keys.firstOrNull { id ->
                candidates.any(id::contains)
            }?.let { group }
        }

    private fun findBoundingBox(observation: SubjectObservation): PixelRect? {
        val contour = observation.contours.values.firstOrNull {
            it.id.contains("bounding_box") || it.id.contains("bbox")
        } ?: return null
        val points = contour.vertexIds.mapNotNull(observation.landmarks::get).map { it.point }
        return boundsOfPoints(points)
    }

    private fun boundsOfLandmarks(points: Collection<LandmarkObservation>): PixelRect? =
        boundsOfPoints(points.map { it.point })

    private fun boundsOfPoints(points: Collection<NormalizedPoint3D>): PixelRect? {
        if (points.isEmpty()) return null
        var left = Float.POSITIVE_INFINITY
        var top = Float.POSITIVE_INFINITY
        var right = Float.NEGATIVE_INFINITY
        var bottom = Float.NEGATIVE_INFINITY
        points.forEach { point ->
            left = min(left, point.x)
            top = min(top, point.y)
            right = max(right, point.x)
            bottom = max(bottom, point.y)
        }
        return PixelRect(left, top, right, bottom)
    }
}
