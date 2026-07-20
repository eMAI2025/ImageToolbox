/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis_mediapipe.pose

import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ConfidenceMask
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ConfidenceSource
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.LandmarkObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.NormalizedPoint3D
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ObservationBackend
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.RegionObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SemanticMaskObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.VisibilityState

/** One normalized pose landmark returned for an image. */
data class MediaPipePoseLandmarkSnapshot(
    val index: Int,
    val x: Float,
    val y: Float,
    val z: Float,
    val visibility: Float? = null,
    val presence: Float? = null,
    val worldX: Float? = null,
    val worldY: Float? = null,
    val worldZ: Float? = null
) {
    init {
        require(index >= 0)
        require(x.isFinite() && y.isFinite() && z.isFinite())
        require(visibility == null || visibility in 0f..1f)
        require(presence == null || presence in 0f..1f)
        require(listOf(worldX, worldY, worldZ).all { it == null || it.isFinite() })
        require(
            listOf(worldX, worldY, worldZ).all { it == null } ||
                listOf(worldX, worldY, worldZ).all { it != null }
        ) {
            "World coordinates must be either complete or unavailable"
        }
    }
}

data class MediaPipeSegmentationMaskSnapshot(
    val width: Int,
    val height: Int,
    val confidenceValues: FloatArray
) {
    init {
        require(width > 0 && height > 0)
        require(confidenceValues.size == width * height)
        require(confidenceValues.all { it in 0f..1f })
    }
}

data class MediaPipePoseSnapshot(
    val landmarks: List<MediaPipePoseLandmarkSnapshot>,
    val segmentationMask: MediaPipeSegmentationMaskSnapshot? = null
) {
    init {
        require(landmarks.map { it.index }.distinct().size == landmarks.size)
    }
}

data class MediaPipePoseLandmarkerSnapshot(
    val poses: List<MediaPipePoseSnapshot>
)

object MediaPipePoseObservationMapper {

    fun map(snapshot: MediaPipePoseLandmarkerSnapshot): SubjectObservation {
        val landmarks = buildMap {
            snapshot.poses.forEachIndexed { poseIndex, pose ->
                pose.landmarks.forEach { point ->
                    val id = rawPointId(poseIndex, point.index)
                    val confidence = combinedConfidence(point)
                    put(
                        id,
                        LandmarkObservation(
                            id = id,
                            point = NormalizedPoint3D(point.x, point.y, point.z),
                            confidence = confidence,
                            confidenceSource = if (confidence == null) {
                                ConfidenceSource.UNAVAILABLE
                            } else {
                                ConfidenceSource.DIRECT
                            },
                            visibility = visibilityFor(point),
                            backend = ObservationBackend.MEDIAPIPE_POSE
                        )
                    )
                }
            }
        }

        val regions = buildMap {
            snapshot.poses.forEachIndexed { poseIndex, pose ->
                if (pose.landmarks.isNotEmpty()) {
                    val minX = pose.landmarks.minOf { it.x }.coerceIn(0f, 1f)
                    val maxX = pose.landmarks.maxOf { it.x }.coerceIn(0f, 1f)
                    val minY = pose.landmarks.minOf { it.y }.coerceIn(0f, 1f)
                    val maxY = pose.landmarks.maxOf { it.y }.coerceIn(0f, 1f)
                    val confidenceValues = pose.landmarks.mapNotNull(::combinedConfidence)
                    val confidence = confidenceValues.takeIf(List<Float>::isNotEmpty)?.average()?.toFloat()
                    val id = rawRegionId(poseIndex)
                    put(
                        id,
                        RegionObservation(
                            id = id,
                            confidence = confidence,
                            confidenceSource = if (confidence == null) {
                                ConfidenceSource.UNAVAILABLE
                            } else {
                                ConfidenceSource.DERIVED
                            },
                            occlusion = null,
                            pixelCoverage = ((maxX - minX) * (maxY - minY)).coerceIn(0f, 1f),
                            backend = ObservationBackend.MEDIAPIPE_POSE
                        )
                    )
                }
            }
        }

        val masks = buildMap {
            snapshot.poses.forEachIndexed { poseIndex, pose ->
                pose.segmentationMask?.let { mask ->
                    val id = rawMaskId(poseIndex)
                    put(
                        id,
                        SemanticMaskObservation(
                            id = id,
                            mask = ConfidenceMask(
                                width = mask.width,
                                height = mask.height,
                                confidenceValues = mask.confidenceValues
                            ),
                            backend = ObservationBackend.MEDIAPIPE_POSE
                        )
                    )
                }
            }
        }

        return SubjectObservation(
            subjectCount = snapshot.poses.size,
            faceCount = 0,
            bodyCount = snapshot.poses.size,
            landmarks = landmarks,
            regions = regions,
            masks = masks
        )
    }

    fun rawPointId(poseIndex: Int, pointIndex: Int): String =
        "body_${poseIndex}_mediapipe_pose_$pointIndex"

    fun rawRegionId(poseIndex: Int): String = "body_${poseIndex}_mediapipe_pose_region"

    fun rawMaskId(poseIndex: Int): String = "body_${poseIndex}_mediapipe_person_mask"

    private fun combinedConfidence(point: MediaPipePoseLandmarkSnapshot): Float? = when {
        point.visibility != null && point.presence != null -> minOf(point.visibility, point.presence)
        point.visibility != null -> point.visibility
        else -> point.presence
    }

    private fun visibilityFor(point: MediaPipePoseLandmarkSnapshot): VisibilityState {
        if (point.x !in 0f..1f || point.y !in 0f..1f) return VisibilityState.OUTSIDE_FRAME
        val confidence = combinedConfidence(point) ?: return VisibilityState.AMBIGUOUS
        return when {
            confidence >= 0.75f -> VisibilityState.VISIBLE
            confidence >= 0.35f -> VisibilityState.PARTIALLY_VISIBLE
            else -> VisibilityState.OCCLUDED
        }
    }
}
