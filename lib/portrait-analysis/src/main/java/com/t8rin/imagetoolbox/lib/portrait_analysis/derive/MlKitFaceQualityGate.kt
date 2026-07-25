/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis.derive

import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ContourObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.NormalizedPoint3D
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ObservationBackend
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation
import kotlin.math.max

/**
 * Backend-specific precondition gate for ML Kit Face Detection geometry.
 *
 * ML Kit may return a face candidate while the available contour/landmark evidence is too small,
 * incomplete, non-finite or inconsistent with the reported yaw. This gate never repairs, mirrors or
 * synthesizes evidence. It only reports reasons that the common fail-closed acceptance gate must use.
 */
object MlKitFaceQualityGate {

    const val FINGERPRINT = "POSTAC_MASTER_MLKIT_FACE_QUALITY_GATE_V1"

    /**
     * A normalized minimum is used because this module receives source-normalized observations.
     * The value is an architectural input-quality floor, not a calibration from one photograph.
     * Runtime integrations that know source pixels may add the stricter official pixel-size check.
     */
    private const val MINIMUM_NORMALIZED_FACE_SIDE = 0.10f
    private const val FACE_BOUNDS_TOLERANCE_FACTOR = 0.02f
    private const val MINIMUM_RELATIVE_SPREAD = 0.05f

    data class Assessment(
        val reasons: Set<FaceGeometryRejectionReason>,
        val faceBoundsWidth: Float?,
        val faceBoundsHeight: Float?,
        val activePointCount: Int,
        val finitePointCount: Int,
        val distinctPointCount: Int
    )

    fun evaluate(
        rawObservation: SubjectObservation,
        candidateObservation: SubjectObservation,
        awareness: FaceRegionAwareness
    ): Assessment {
        if (!rawObservation.usesMlKitFaceDetection()) {
            return Assessment(
                reasons = emptySet(),
                faceBoundsWidth = null,
                faceBoundsHeight = null,
                activePointCount = candidateObservation.landmarks.size,
                finitePointCount = candidateObservation.landmarks.size,
                distinctPointCount = candidateObservation.landmarks.values
                    .map { it.point.xyKey() }
                    .distinct()
                    .size
            )
        }

        val reasons = linkedSetOf<FaceGeometryRejectionReason>()
        val bounds = rawObservation.faceBounds()
        val activePoints = candidateObservation.landmarks.values
            .filterNot { isBoundingGeometry(it.id) }
            .map { it.point }
        val finitePoints = activePoints.filter { it.x.isFinite() && it.y.isFinite() }
        val distinctPointCount = finitePoints.map { it.xyKey() }.distinct().size

        if (bounds == null ||
            bounds.width < MINIMUM_NORMALIZED_FACE_SIDE ||
            bounds.height < MINIMUM_NORMALIZED_FACE_SIDE
        ) {
            reasons += FaceGeometryRejectionReason.FACE_TOO_SMALL_OR_BOUNDS_UNAVAILABLE
        }

        if (finitePoints.size != activePoints.size) {
            reasons += FaceGeometryRejectionReason.NON_FINITE_GEOMETRY
        }

        if (bounds != null && !bounds.isInsideNormalizedImage()) {
            reasons += FaceGeometryRejectionReason.FACE_BOUNDS_OUTSIDE_IMAGE
        }

        if (finitePoints.any { point -> !point.isInsideNormalizedImage() }) {
            reasons += FaceGeometryRejectionReason.GEOMETRY_OUTSIDE_IMAGE
        }

        if (bounds != null && finitePoints.any { point -> !bounds.containsWithTolerance(point) }) {
            reasons += FaceGeometryRejectionReason.GEOMETRY_OUTSIDE_FACE_BOUNDS
        }

        if (bounds != null && !finitePoints.hasRequiredSpread(bounds)) {
            reasons += FaceGeometryRejectionReason.INSUFFICIENT_LANDMARK_SPREAD
        }

        if (distinctPointCount < 3) {
            reasons += FaceGeometryRejectionReason.COLLAPSED_GEOMETRY
        }

        val ids = candidateObservation.landmarks.keys + candidateObservation.contours.keys
        when (awareness.poseMode) {
            FacePoseMode.FRONTAL -> {
                if (!ids.containsSemantic("left_eye") ||
                    !ids.containsSemantic("right_eye") ||
                    !ids.containsSemantic("nose")
                ) {
                    reasons += FaceGeometryRejectionReason.MISSING_YAW_DEPENDENT_LANDMARKS
                }
            }

            FacePoseMode.HALF_PROFILE,
            FacePoseMode.PROFILE -> {
                val visibleEyeToken = when (awareness.dominantImageSide) {
                    FaceImageSide.LEFT -> "left_eye"
                    FaceImageSide.RIGHT -> "right_eye"
                    else -> null
                }
                val hiddenEyeToken = when (awareness.dominantImageSide) {
                    FaceImageSide.LEFT -> "right_eye"
                    FaceImageSide.RIGHT -> "left_eye"
                    else -> null
                }
                if (visibleEyeToken == null ||
                    !ids.containsSemantic(visibleEyeToken) ||
                    !ids.containsSemantic("nose")
                ) {
                    reasons += FaceGeometryRejectionReason.MISSING_YAW_DEPENDENT_LANDMARKS
                }
                if (hiddenEyeToken != null && ids.containsSemantic(hiddenEyeToken)) {
                    reasons += FaceGeometryRejectionReason.VISIBLE_SIDE_LANDMARK_CONTRADICTION
                }
            }

            FacePoseMode.UNKNOWN -> {
                reasons += FaceGeometryRejectionReason.MISSING_YAW_DEPENDENT_LANDMARKS
            }
        }

        val landmarkIds = candidateObservation.landmarks.keys
        if (candidateObservation.contours.values.any { contour ->
                contour.vertexIds.any { it !in landmarkIds }
            }
        ) {
            reasons += FaceGeometryRejectionReason.BROKEN_LANDMARK_CONTOUR_REFERENCE
        }
        if (candidateObservation.meshes.values.any { mesh ->
                mesh.vertexIds.any { it !in landmarkIds } ||
                    mesh.triangles.any { triangle -> triangle.vertexIds.any { it !in landmarkIds } }
            }
        ) {
            reasons += FaceGeometryRejectionReason.BROKEN_MESH_REFERENCE
        }

        return Assessment(
            reasons = reasons,
            faceBoundsWidth = bounds?.width,
            faceBoundsHeight = bounds?.height,
            activePointCount = activePoints.size,
            finitePointCount = finitePoints.size,
            distinctPointCount = distinctPointCount
        )
    }

    private data class Bounds(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float
    ) {
        val width: Float get() = (right - left).coerceAtLeast(0f)
        val height: Float get() = (bottom - top).coerceAtLeast(0f)

        fun isInsideNormalizedImage(): Boolean =
            left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite() &&
                left in 0f..1f && top in 0f..1f && right in 0f..1f && bottom in 0f..1f &&
                right > left && bottom > top

        fun containsWithTolerance(point: NormalizedPoint3D): Boolean {
            val tolerance = max(width, height) * FACE_BOUNDS_TOLERANCE_FACTOR
            return point.x in (left - tolerance)..(right + tolerance) &&
                point.y in (top - tolerance)..(bottom + tolerance)
        }
    }

    private fun SubjectObservation.usesMlKitFaceDetection(): Boolean =
        landmarks.values.any { it.backend == ObservationBackend.ML_KIT_FACE_DETECTION } ||
            contours.values.any { it.backend == ObservationBackend.ML_KIT_FACE_DETECTION } ||
            meshes.values.any { it.backend == ObservationBackend.ML_KIT_FACE_DETECTION }

    private fun SubjectObservation.faceBounds(): Bounds? {
        val contour = contours.values.firstOrNull { isBoundingGeometry(it.id) } ?: return null
        val points = contour.vertexIds.mapNotNull(landmarks::get).map { it.point }
        if (points.isEmpty() || points.any { !it.x.isFinite() || !it.y.isFinite() }) return null
        return Bounds(
            left = points.minOf { it.x },
            top = points.minOf { it.y },
            right = points.maxOf { it.x },
            bottom = points.maxOf { it.y }
        ).takeIf { it.width > 0f && it.height > 0f }
    }

    private fun List<NormalizedPoint3D>.hasRequiredSpread(bounds: Bounds): Boolean {
        if (size < 3 || bounds.width <= 0f || bounds.height <= 0f) return false
        val spreadX = maxOf { it.x } - minOf { it.x }
        val spreadY = maxOf { it.y } - minOf { it.y }
        return spreadX / bounds.width >= MINIMUM_RELATIVE_SPREAD &&
            spreadY / bounds.height >= MINIMUM_RELATIVE_SPREAD
    }

    private fun Set<String>.containsSemantic(token: String): Boolean =
        any { id -> id.lowercase().contains(token) }

    private fun NormalizedPoint3D.isInsideNormalizedImage(): Boolean =
        x.isFinite() && y.isFinite() && x in 0f..1f && y in 0f..1f

    private fun NormalizedPoint3D.xyKey(): Pair<Float, Float> = x to y

    private fun isBoundingGeometry(id: String): Boolean {
        val value = id.lowercase()
        return value.contains("bounding_box") || value.contains("bbox")
    }
}