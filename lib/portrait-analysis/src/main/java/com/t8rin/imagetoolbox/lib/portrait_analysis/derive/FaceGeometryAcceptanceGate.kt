/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis.derive

import com.t8rin.imagetoolbox.lib.portrait_analysis.model.NormalizedPoint3D
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation
import kotlin.math.max

/**
 * Final fail-closed decision between detector/visible-filter output and active overlay geometry.
 *
 * Detection is never treated as permission to render. The detector proposal is retained as raw
 * evidence, while the active geometry is present only when all structural checks pass.
 */
object FaceGeometryAcceptanceGate {

    const val FINGERPRINT = "POSTAC_MASTER_FACE_GATE_V1_RUNTIME"

    private const val MINIMUM_GEOMETRY_SPREAD = 0.01f
    private const val FACE_BOUNDS_TOLERANCE_FACTOR = 0.02f

    fun evaluate(
        rawObservation: SubjectObservation,
        visibleGeometry: FaceVisibleGeometryResult,
        awareness: FaceRegionAwareness
    ): FaceGeometryAcceptanceDecision {
        if (rawObservation.faceCount <= 0) {
            return FaceGeometryAcceptanceDecision(
                status = FaceGeometryAcceptanceStatus.NO_FACE,
                rawObservation = rawObservation,
                activeObservation = null,
                reasons = setOf(FaceGeometryRejectionReason.NO_FACE_CANDIDATE)
            )
        }

        val visibleObservation = visibleGeometry.observation
        val reasons = linkedSetOf<FaceGeometryRejectionReason>()
        val partialPose = awareness.poseMode == FacePoseMode.HALF_PROFILE ||
            awareness.poseMode == FacePoseMode.PROFILE

        // Backend-specific evidence is evaluated before generic topology checks. These policies
        // return reasons only; they never modify, mirror or synthesize detector geometry.
        val mlKitQuality = MlKitFaceQualityGate.evaluate(
            rawObservation = rawObservation,
            candidateObservation = visibleObservation,
            awareness = awareness
        )
        reasons += mlKitQuality.reasons
        reasons += MlKitFaceProfilePolicy.evaluate(
            rawObservation = rawObservation,
            awareness = awareness
        ).reasons

        if (awareness.poseMode == FacePoseMode.UNKNOWN) {
            reasons += FaceGeometryRejectionReason.MISSING_REQUIRED_POSE_OR_AXIS
        }
        if (partialPose && awareness.dominantImageSide !in setOf(FaceImageSide.LEFT, FaceImageSide.RIGHT)) {
            reasons += FaceGeometryRejectionReason.MISSING_REQUIRED_POSE_OR_AXIS
        }
        if (partialPose && !visibleGeometry.applied) {
            reasons += FaceGeometryRejectionReason.VISIBLE_SIDE_CONTRADICTION
        }

        // One dedicated topology validator owns contour/mesh visibility integrity. It never mutates
        // raw or candidate geometry; all returned reasons feed the same fail-closed decision.
        val topology = FaceTopologyVisibilityValidator.evaluate(
            rawObservation = rawObservation,
            candidateObservation = visibleObservation,
            awareness = awareness
        )
        reasons += topology.reasons

        val activePoints = visibleObservation.landmarks.values
            .filterNot { isBoundingGeometry(it.id) }
            .map { it.point }

        if (activePoints.any { !it.isInsideNormalizedImage() }) {
            reasons += FaceGeometryRejectionReason.GEOMETRY_OUTSIDE_IMAGE
        }

        val bounds = rawObservation.faceBounds()
        if (bounds == null && partialPose) {
            reasons += FaceGeometryRejectionReason.MISSING_REQUIRED_POSE_OR_AXIS
        } else if (bounds != null && activePoints.any { !bounds.containsWithTolerance(it) }) {
            reasons += FaceGeometryRejectionReason.GEOMETRY_OUTSIDE_FACE_BOUNDS
        }

        if (activePoints.size < 3 || activePoints.isCollapsed()) {
            reasons += FaceGeometryRejectionReason.COLLAPSED_GEOMETRY
        }

        // The awareness stage may append explicitly derived center/chin evidence. Therefore the
        // filter's own raw/visible counters are the authoritative contradiction check; comparing
        // every post-analysis ID against the detector-only payload would reject valid derived IDs.
        if (visibleGeometry.visibleLandmarkCount > visibleGeometry.rawLandmarkCount ||
            visibleGeometry.visibleContourCount > visibleGeometry.rawContourCount +
                visibleGeometry.splitContourCount ||
            visibleGeometry.visibleTriangleCount > visibleGeometry.rawTriangleCount
        ) {
            reasons += FaceGeometryRejectionReason.RAW_VISIBLE_GEOMETRY_CONTRADICTION
        }

        return if (reasons.isEmpty()) {
            FaceGeometryAcceptanceDecision(
                status = FaceGeometryAcceptanceStatus.FACE_DETECTED_GEOMETRY_ACCEPTED,
                rawObservation = rawObservation,
                activeObservation = visibleObservation,
                reasons = emptySet()
            )
        } else {
            FaceGeometryAcceptanceDecision(
                status = FaceGeometryAcceptanceStatus.FACE_DETECTED_GEOMETRY_REJECTED,
                rawObservation = rawObservation,
                activeObservation = null,
                reasons = reasons
            )
        }
    }

    /**
     * Observation used by the active overlay on rejection. It deliberately carries no geometry.
     * Raw detector evidence remains available on [FaceGeometryAcceptanceDecision.rawObservation].
     */
    fun renderObservation(decision: FaceGeometryAcceptanceDecision): SubjectObservation =
        decision.activeObservation ?: decision.rawObservation.copy(
            landmarks = emptyMap(),
            regions = emptyMap(),
            classifications = emptyMap(),
            masks = emptyMap(),
            contours = emptyMap(),
            meshes = emptyMap()
        )

    private data class Bounds(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float
    ) {
        val width: Float get() = (right - left).coerceAtLeast(0f)
        val height: Float get() = (bottom - top).coerceAtLeast(0f)

        fun containsWithTolerance(point: NormalizedPoint3D): Boolean {
            val tolerance = max(width, height) * FACE_BOUNDS_TOLERANCE_FACTOR
            return point.x in (left - tolerance)..(right + tolerance) &&
                point.y in (top - tolerance)..(bottom + tolerance)
        }
    }

    private fun SubjectObservation.faceBounds(): Bounds? {
        val contour = contours.values.firstOrNull { isBoundingGeometry(it.id) } ?: return null
        val points = contour.vertexIds.mapNotNull(landmarks::get).map { it.point }
        if (points.isEmpty()) return null
        return Bounds(
            left = points.minOf { it.x },
            top = points.minOf { it.y },
            right = points.maxOf { it.x },
            bottom = points.maxOf { it.y }
        ).takeIf { it.width > 0f && it.height > 0f }
    }

    private fun List<NormalizedPoint3D>.isCollapsed(): Boolean {
        if (isEmpty()) return true
        val spreadX = maxOf { it.x } - minOf { it.x }
        val spreadY = maxOf { it.y } - minOf { it.y }
        return spreadX < MINIMUM_GEOMETRY_SPREAD || spreadY < MINIMUM_GEOMETRY_SPREAD
    }

    private fun NormalizedPoint3D.isInsideNormalizedImage(): Boolean =
        x.isFinite() && y.isFinite() && x in 0f..1f && y in 0f..1f

    private fun isBoundingGeometry(id: String): Boolean {
        val value = id.lowercase()
        return value.contains("bounding_box") || value.contains("bbox")
    }
}
