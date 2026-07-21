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
    val boundingBox: PixelRect?,
    val controlPointIds: Set<String>,
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
    private const val MIN_MESH_VERTICES_FOR_PASS = 100

    private val requiredControlPointSuffixes = linkedMapOf(
        "left_eye" to listOf("detection_landmark_left_eye", "left_eye"),
        "right_eye" to listOf("detection_landmark_right_eye", "right_eye"),
        "nose" to listOf("detection_landmark_nose_base", "nose_base", "nose"),
        "mouth_left" to listOf("detection_landmark_mouth_left", "mouth_left"),
        "mouth_right" to listOf("detection_landmark_mouth_right", "mouth_right")
    )

    fun evaluate(observation: SubjectObservation): VisualProofEvaluation {
        val landmarks = observation.landmarks.values.sortedBy(LandmarkObservation::id)
        val inFrame = landmarks.filter { it.point.x in 0f..1f && it.point.y in 0f..1f }
        val inFrameRatio = if (landmarks.isEmpty()) 0f else inFrame.size.toFloat() / landmarks.size
        val spreadRect = boundsOfLandmarks(inFrame)
        val spread = spreadRect?.area ?: 0f
        val boundingBox = findBoundingBox(observation)
        val controlPoints = findControlPoints(observation)
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
                reasons += "Landmarks collapse into an implausibly small area"
                VisualProofStatus.VISUAL_VALIDATION_FAILED
            }
            boundingBox == null -> {
                reasons += "Face geometry exists but no bounding box was preserved"
                VisualProofStatus.VISUAL_VALIDATION_FAILED
            }
            triangleCount > 0 && meshVertexCount >= MIN_MESH_VERTICES_FOR_PASS -> {
                if (controlPoints.size < requiredControlPointSuffixes.size) {
                    reasons += "Dense mesh is present but not all semantic control points are mapped"
                }
                VisualProofStatus.PASS
            }
            else -> {
                if (controlPoints.size < requiredControlPointSuffixes.size) {
                    reasons += "Only ${controlPoints.size}/${requiredControlPointSuffixes.size} control points are available"
                }
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
            boundingBox = boundingBox,
            controlPointIds = controlPoints,
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

    private fun findControlPoints(observation: SubjectObservation): Set<String> =
        requiredControlPointSuffixes.mapNotNullTo(linkedSetOf()) { (semanticId, candidates) ->
            observation.landmarks.keys.firstOrNull { id ->
                candidates.any { candidate -> id.endsWith(candidate) || id == candidate }
            }?.let { semanticId }
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
