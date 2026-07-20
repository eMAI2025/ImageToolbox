/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis_mediapipe.face

import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ClassificationObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ConfidenceSource
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.LandmarkObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.NormalizedPoint3D
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ObservationBackend
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.PoseObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.RegionObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.VisibilityState

/** Platform-neutral Face Landmarker result used by tests and runtime adapters. */
data class MediaPipeFaceLandmarkSnapshot(
    val index: Int,
    val x: Float,
    val y: Float,
    val z: Float,
    val visibility: Float? = null,
    val presence: Float? = null
) {
    init {
        require(index >= 0)
        require(x.isFinite() && y.isFinite() && z.isFinite())
        require(visibility == null || visibility in 0f..1f)
        require(presence == null || presence in 0f..1f)
    }
}

data class MediaPipeBlendshapeSnapshot(
    val name: String,
    val score: Float
) {
    init {
        require(name.isNotBlank())
        require(score in 0f..1f)
    }
}

data class MediaPipeFaceSnapshot(
    val landmarks: List<MediaPipeFaceLandmarkSnapshot>,
    val blendshapes: List<MediaPipeBlendshapeSnapshot> = emptyList(),
    val transformationMatrix: List<Float> = emptyList(),
    val yawDegrees: Float? = null,
    val pitchDegrees: Float? = null,
    val rollDegrees: Float? = null,
    val detectionConfidence: Float? = null
) {
    init {
        require(landmarks.map { it.index }.distinct().size == landmarks.size)
        require(blendshapes.map { it.name }.distinct().size == blendshapes.size)
        require(transformationMatrix.isEmpty() || transformationMatrix.size == 16)
        require(transformationMatrix.all(Float::isFinite))
        require(detectionConfidence == null || detectionConfidence in 0f..1f)
    }
}

data class MediaPipeFaceLandmarkerSnapshot(
    val faces: List<MediaPipeFaceSnapshot>
)

object MediaPipeFaceObservationMapper {

    fun map(snapshot: MediaPipeFaceLandmarkerSnapshot): SubjectObservation {
        val landmarks = buildMap {
            snapshot.faces.forEachIndexed { faceIndex, face ->
                face.landmarks.forEach { point ->
                    val id = rawPointId(faceIndex, point.index)
                    val confidence = point.presence ?: point.visibility
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
                            backend = ObservationBackend.MEDIAPIPE_FACE_LANDMARKER
                        )
                    )
                }
            }
        }

        val regions = buildMap {
            snapshot.faces.forEachIndexed { faceIndex, face ->
                if (face.landmarks.isNotEmpty()) {
                    val minX = face.landmarks.minOf { it.x }.coerceIn(0f, 1f)
                    val maxX = face.landmarks.maxOf { it.x }.coerceIn(0f, 1f)
                    val minY = face.landmarks.minOf { it.y }.coerceIn(0f, 1f)
                    val maxY = face.landmarks.maxOf { it.y }.coerceIn(0f, 1f)
                    val id = rawRegionId(faceIndex)
                    put(
                        id,
                        RegionObservation(
                            id = id,
                            confidence = face.detectionConfidence,
                            confidenceSource = if (face.detectionConfidence == null) {
                                ConfidenceSource.UNAVAILABLE
                            } else {
                                ConfidenceSource.DIRECT
                            },
                            occlusion = null,
                            pixelCoverage = ((maxX - minX) * (maxY - minY)).coerceIn(0f, 1f),
                            backend = ObservationBackend.MEDIAPIPE_FACE_LANDMARKER
                        )
                    )
                }
            }
        }

        val classifications = buildMap {
            snapshot.faces.forEachIndexed { faceIndex, face ->
                face.blendshapes.forEach { blendshape ->
                    val id = blendshapeId(faceIndex, blendshape.name)
                    put(
                        id,
                        ClassificationObservation(
                            id = id,
                            probability = blendshape.score,
                            backend = ObservationBackend.MEDIAPIPE_FACE_LANDMARKER
                        )
                    )
                }
            }
        }

        val firstFace = snapshot.faces.firstOrNull()
        return SubjectObservation(
            subjectCount = snapshot.faces.size,
            faceCount = snapshot.faces.size,
            bodyCount = 0,
            landmarks = landmarks,
            regions = regions,
            classifications = classifications,
            pose = PoseObservation(
                yawDegrees = firstFace?.yawDegrees,
                pitchDegrees = firstFace?.pitchDegrees,
                rollDegrees = firstFace?.rollDegrees
            )
        )
    }

    fun rawPointId(faceIndex: Int, pointIndex: Int): String =
        "face_${faceIndex}_mediapipe_$pointIndex"

    fun rawRegionId(faceIndex: Int): String = "face_${faceIndex}_mediapipe_region"

    fun blendshapeId(faceIndex: Int, name: String): String =
        "face_${faceIndex}_blendshape_$name"

    private fun visibilityFor(point: MediaPipeFaceLandmarkSnapshot): VisibilityState {
        if (point.x !in 0f..1f || point.y !in 0f..1f) return VisibilityState.OUTSIDE_FRAME
        val evidence = point.visibility ?: point.presence ?: return VisibilityState.AMBIGUOUS
        return when {
            evidence >= 0.75f -> VisibilityState.VISIBLE
            evidence >= 0.35f -> VisibilityState.PARTIALLY_VISIBLE
            else -> VisibilityState.OCCLUDED
        }
    }
}
