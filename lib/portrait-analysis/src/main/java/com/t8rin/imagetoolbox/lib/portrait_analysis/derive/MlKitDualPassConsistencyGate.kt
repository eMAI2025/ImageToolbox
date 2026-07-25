/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis.derive

import com.t8rin.imagetoolbox.lib.portrait_analysis.model.NormalizedPoint3D
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ObservationBackend
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

/**
 * Compares one ML Kit face proposal from the full source image with a second proposal obtained from
 * the explicitly selected high-resolution face ROI.
 *
 * The two passes are never averaged or merged. The ROI pass is corroborating evidence only. Any
 * missing or contradictory evidence fails closed and is later mapped to an empty active overlay.
 */
object MlKitDualPassConsistencyGate {

    const val FINGERPRINT = "POSTAC_MASTER_MLKIT_DUAL_PASS_CONSISTENCY_V1"

    // Versioned architectural drift ceilings. They are not calibrated from one photograph.
    const val MINIMUM_BOUNDS_IOU = 0.60f
    const val MAXIMUM_YAW_DELTA_DEGREES = 10f
    const val MAXIMUM_ROLL_DELTA_DEGREES = 12f
    const val MAXIMUM_ANCHOR_DISTANCE_TO_FACE_DIAGONAL = 0.15f
    const val MINIMUM_COMPARABLE_ANCHORS = 2

    enum class RejectionReason {
        SECOND_PASS_MISSING_OR_AMBIGUOUS,
        BOUNDS_MISSING_OR_INCONSISTENT,
        POSE_EVIDENCE_MISSING_OR_INCONSISTENT,
        CENTRAL_ANCHORS_MISSING_OR_INCONSISTENT,
        BACKEND_MISMATCH
    }

    data class Metrics(
        val boundsIou: Float?,
        val yawDeltaDegrees: Float?,
        val rollDeltaDegrees: Float?,
        val comparedAnchorIds: Set<String>,
        val maximumAnchorDistanceRatio: Float?,
        val meanAnchorDistanceRatio: Float?
    )

    data class Assessment(
        val accepted: Boolean,
        val reasons: Set<RejectionReason>,
        val metrics: Metrics
    )

    fun evaluate(
        fullPassObservation: SubjectObservation,
        fullPassAwareness: FaceRegionAwareness,
        roiPassObservation: SubjectObservation?,
        roiPassAwareness: FaceRegionAwareness?
    ): Assessment {
        val reasons = linkedSetOf<RejectionReason>()
        if (!fullPassObservation.usesMlKitFaceDetection()) {
            reasons += RejectionReason.BACKEND_MISMATCH
        }
        if (
            roiPassObservation == null ||
            roiPassAwareness == null ||
            roiPassObservation.faceCount != 1
        ) {
            reasons += RejectionReason.SECOND_PASS_MISSING_OR_AMBIGUOUS
            return Assessment(
                accepted = false,
                reasons = reasons,
                metrics = Metrics(null, null, null, emptySet(), null, null)
            )
        }
        if (!roiPassObservation.usesMlKitFaceDetection()) {
            reasons += RejectionReason.BACKEND_MISMATCH
        }

        val fullBounds = fullPassObservation.faceBounds()
        val roiBounds = roiPassObservation.faceBounds()
        val boundsIou = if (fullBounds != null && roiBounds != null) {
            fullBounds.iou(roiBounds)
        } else {
            null
        }
        if (boundsIou == null || boundsIou < MINIMUM_BOUNDS_IOU) {
            reasons += RejectionReason.BOUNDS_MISSING_OR_INCONSISTENT
        }

        val yawDelta = finiteDelta(fullPassAwareness.yawDegrees, roiPassAwareness.yawDegrees)
        val rollDelta = finiteDelta(fullPassAwareness.rollDegrees, roiPassAwareness.rollDegrees)
        if (
            yawDelta == null ||
            rollDelta == null ||
            yawDelta > MAXIMUM_YAW_DELTA_DEGREES ||
            rollDelta > MAXIMUM_ROLL_DELTA_DEGREES ||
            fullPassAwareness.dominantImageSide != roiPassAwareness.dominantImageSide
        ) {
            reasons += RejectionReason.POSE_EVIDENCE_MISSING_OR_INCONSISTENT
        }

        val fullAnchors = fullPassObservation.centralAnchors()
        val roiAnchors = roiPassObservation.centralAnchors()
        val comparedAnchorIds = fullAnchors.keys.intersect(roiAnchors.keys)
        val referenceDiagonal = max(
            fullBounds?.diagonal.orZero(),
            roiBounds?.diagonal.orZero()
        )
        val anchorDistanceRatios = if (
            comparedAnchorIds.size >= MINIMUM_COMPARABLE_ANCHORS &&
            referenceDiagonal > 0f
        ) {
            comparedAnchorIds.map { id ->
                distance(fullAnchors.getValue(id), roiAnchors.getValue(id)) / referenceDiagonal
            }
        } else {
            emptyList()
        }
        val maxAnchorRatio = anchorDistanceRatios.maxOrNull()
        val meanAnchorRatio = anchorDistanceRatios.takeIf { it.isNotEmpty() }
            ?.average()
            ?.toFloat()
        if (
            comparedAnchorIds.size < MINIMUM_COMPARABLE_ANCHORS ||
            maxAnchorRatio == null ||
            maxAnchorRatio > MAXIMUM_ANCHOR_DISTANCE_TO_FACE_DIAGONAL
        ) {
            reasons += RejectionReason.CENTRAL_ANCHORS_MISSING_OR_INCONSISTENT
        }

        return Assessment(
            accepted = reasons.isEmpty(),
            reasons = reasons,
            metrics = Metrics(
                boundsIou = boundsIou,
                yawDeltaDegrees = yawDelta,
                rollDeltaDegrees = rollDelta,
                comparedAnchorIds = comparedAnchorIds,
                maximumAnchorDistanceRatio = maxAnchorRatio,
                meanAnchorDistanceRatio = meanAnchorRatio
            )
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
        val area: Float get() = width * height
        val diagonal: Float get() = hypot(width, height)

        fun iou(other: Bounds): Float {
            val intersectionWidth = (minOf(right, other.right) - maxOf(left, other.left))
                .coerceAtLeast(0f)
            val intersectionHeight = (minOf(bottom, other.bottom) - maxOf(top, other.top))
                .coerceAtLeast(0f)
            val intersection = intersectionWidth * intersectionHeight
            val union = area + other.area - intersection
            return if (union > 0f) intersection / union else 0f
        }
    }

    private fun SubjectObservation.faceBounds(): Bounds? {
        val contour = contours.values.firstOrNull { contour ->
            val id = contour.id.lowercase()
            id.contains("bounding_box") || id.contains("bbox")
        } ?: return null
        val points = contour.vertexIds.mapNotNull(landmarks::get).map { it.point }
        if (points.isEmpty() || points.any { !it.x.isFinite() || !it.y.isFinite() }) return null
        return Bounds(
            left = points.minOf { it.x },
            top = points.minOf { it.y },
            right = points.maxOf { it.x },
            bottom = points.maxOf { it.y }
        ).takeIf { it.width > 0f && it.height > 0f }
    }

    private fun SubjectObservation.centralAnchors(): Map<String, NormalizedPoint3D> =
        landmarks.values
            .mapNotNull { landmark ->
                canonicalAnchorId(landmark.id)?.let { key -> key to landmark.point }
            }
            .filter { (_, point) -> point.x.isFinite() && point.y.isFinite() }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, points) ->
                NormalizedPoint3D(
                    x = points.map { it.x }.average().toFloat(),
                    y = points.map { it.y }.average().toFloat(),
                    z = null
                )
            }

    private fun canonicalAnchorId(id: String): String? {
        val value = id.lowercase()
        return when {
            value.contains("brow") || value.contains("eyebrow") -> null
            value.contains("nose_base") || value.contains("nose_tip") || value.endsWith("_nose") -> "nose"
            value.contains("left_eye") -> "left_eye"
            value.contains("right_eye") -> "right_eye"
            value.contains("mouth_left") -> "mouth_left"
            value.contains("mouth_right") -> "mouth_right"
            value.contains("mouth_center") || value.contains("mouth_bottom") -> "mouth_center"
            value.contains("chin_center") -> "chin"
            else -> null
        }
    }

    private fun finiteDelta(first: Float?, second: Float?): Float? =
        if (first != null && second != null && first.isFinite() && second.isFinite()) {
            abs(first - second)
        } else {
            null
        }

    private fun distance(first: NormalizedPoint3D, second: NormalizedPoint3D): Float =
        hypot(first.x - second.x, first.y - second.y)

    private fun Float?.orZero(): Float = this ?: 0f

    private fun SubjectObservation.usesMlKitFaceDetection(): Boolean =
        landmarks.values.any { it.backend == ObservationBackend.ML_KIT_FACE_DETECTION } ||
            contours.values.any { it.backend == ObservationBackend.ML_KIT_FACE_DETECTION } ||
            meshes.values.any { it.backend == ObservationBackend.ML_KIT_FACE_DETECTION }
}
