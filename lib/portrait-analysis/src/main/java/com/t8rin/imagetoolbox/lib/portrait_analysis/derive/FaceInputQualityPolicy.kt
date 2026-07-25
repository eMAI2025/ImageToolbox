/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis.derive

import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation

/**
 * Input-image quality gate used before active face geometry is exposed.
 *
 * Thresholds are versioned architectural limits covered by a regression matrix. They are not
 * calibrated from one photograph. The policy never repairs, mirrors or reconstructs geometry.
 */
object FaceInputQualityPolicy {

    const val FINGERPRINT = "POSTAC_MASTER_FACE_INPUT_QUALITY_V1"
    const val MINIMUM_FACE_EDGE_PIXELS = 100f
    const val MINIMUM_NORMALIZED_EDGE_ENERGY = 0.012f
    const val MAXIMUM_OUT_OF_FRAME_POINT_RATIO = 0.02f

    data class Evidence(
        val sourceWidth: Int,
        val sourceHeight: Int,
        val encodedWidth: Int,
        val encodedHeight: Int,
        val orientedWidth: Int,
        val orientedHeight: Int,
        val exifOrientation: Int,
        val orientationDegrees: Int,
        val mirrored: Boolean,
        val normalizedEdgeEnergy: Float?
    ) {
        init {
            require(sourceWidth > 0 && sourceHeight > 0)
            require(encodedWidth > 0 && encodedHeight > 0)
            require(orientedWidth > 0 && orientedHeight > 0)
            require(normalizedEdgeEnergy == null || normalizedEdgeEnergy.isFinite())
        }
    }

    data class Metrics(
        val faceWidthPixels: Float?,
        val faceHeightPixels: Float?,
        val normalizedEdgeEnergy: Float?,
        val outOfFramePointRatio: Float,
        val transformConsistent: Boolean
    )

    data class Assessment(
        val accepted: Boolean,
        val reasons: Set<FaceGeometryRejectionReason>,
        val metrics: Metrics
    )

    fun evaluate(
        observation: SubjectObservation,
        evidence: Evidence
    ): Assessment {
        val reasons = linkedSetOf<FaceGeometryRejectionReason>()
        val bounds = observation.faceBounds()
        val faceWidthPixels = bounds?.let { it.width * evidence.sourceWidth }
        val faceHeightPixels = bounds?.let { it.height * evidence.sourceHeight }

        if (
            faceWidthPixels == null ||
            faceHeightPixels == null ||
            minOf(faceWidthPixels, faceHeightPixels) < MINIMUM_FACE_EDGE_PIXELS
        ) {
            reasons += FaceGeometryRejectionReason.FACE_TOO_SMALL_IN_SOURCE_PIXELS
            reasons += FaceGeometryRejectionReason.IMAGE_QUALITY_RESELECT_REQUIRED
        }

        val edgeEnergy = evidence.normalizedEdgeEnergy
        if (edgeEnergy == null) {
            reasons += FaceGeometryRejectionReason.IMAGE_BLUR_EVIDENCE_UNAVAILABLE
            reasons += FaceGeometryRejectionReason.IMAGE_QUALITY_RESELECT_REQUIRED
        } else if (edgeEnergy < MINIMUM_NORMALIZED_EDGE_ENERGY) {
            reasons += FaceGeometryRejectionReason.IMAGE_BLUR_TOO_HIGH
            reasons += FaceGeometryRejectionReason.IMAGE_QUALITY_RESELECT_REQUIRED
        }

        val exifValid = evidence.exifOrientation in 1..8 &&
            evidence.orientationDegrees in setOf(0, 90, 180, 270)
        val swapsDimensions = evidence.exifOrientation in setOf(5, 6, 7, 8)
        val expectedOrientedWidth = if (swapsDimensions) evidence.encodedHeight else evidence.encodedWidth
        val expectedOrientedHeight = if (swapsDimensions) evidence.encodedWidth else evidence.encodedHeight
        val dimensionsConsistent = evidence.orientedWidth == expectedOrientedWidth &&
            evidence.orientedHeight == expectedOrientedHeight
        val expectedMirrored = evidence.exifOrientation in setOf(2, 4, 5, 7)
        val mirrorConsistent = evidence.mirrored == expectedMirrored

        if (!exifValid || !dimensionsConsistent) {
            reasons += FaceGeometryRejectionReason.EXIF_TRANSFORM_INCONSISTENT
        }
        if (!mirrorConsistent) {
            reasons += FaceGeometryRejectionReason.MIRROR_TRANSFORM_INCONSISTENT
        }

        val activePoints = observation.landmarks.values
            .filterNot { landmark -> landmark.id.isBoundingGeometry() }
            .map { it.point }
        val outOfFrameCount = activePoints.count { point ->
            !point.x.isFinite() || !point.y.isFinite() || point.x !in 0f..1f || point.y !in 0f..1f
        }
        val outOfFrameRatio = if (activePoints.isEmpty()) 1f else {
            outOfFrameCount.toFloat() / activePoints.size.toFloat()
        }
        if (outOfFrameRatio > MAXIMUM_OUT_OF_FRAME_POINT_RATIO) {
            reasons += FaceGeometryRejectionReason.GEOMETRY_OUT_OF_FRAME_RATIO_EXCEEDED
            reasons += FaceGeometryRejectionReason.IMAGE_QUALITY_RESELECT_REQUIRED
        }

        return Assessment(
            accepted = reasons.isEmpty(),
            reasons = reasons,
            metrics = Metrics(
                faceWidthPixels = faceWidthPixels,
                faceHeightPixels = faceHeightPixels,
                normalizedEdgeEnergy = edgeEnergy,
                outOfFramePointRatio = outOfFrameRatio,
                transformConsistent = exifValid && dimensionsConsistent && mirrorConsistent
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
    }

    private fun SubjectObservation.faceBounds(): Bounds? {
        val contour = contours.values.firstOrNull { it.id.isBoundingGeometry() } ?: return null
        val points = contour.vertexIds.mapNotNull(landmarks::get).map { it.point }
        if (points.isEmpty() || points.any { !it.x.isFinite() || !it.y.isFinite() }) return null
        return Bounds(
            left = points.minOf { it.x },
            top = points.minOf { it.y },
            right = points.maxOf { it.x },
            bottom = points.maxOf { it.y }
        ).takeIf { it.width > 0f && it.height > 0f }
    }

    private fun String.isBoundingGeometry(): Boolean {
        val value = lowercase()
        return value.contains("bounding_box") || value.contains("bbox")
    }
}