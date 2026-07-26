/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis.mediapipe

import com.t8rin.imagetoolbox.lib.portrait_analysis.model.NormalizedPoint3D

const val MEDIAPIPE_DENSE_FACE_ADAPTER_VERSION =
    "POSTAC_MASTER_MEDIAPIPE_DENSE_FACE_ADAPTER_V1"

enum class DenseFacePointState {
    INSIDE_SOURCE,
    CLIPPED_TO_SOURCE,
    NON_FINITE_REJECTED
}

data class MediaPipeRawLandmark(
    val index: Int,
    val pointInRoi: NormalizedPoint3D,
    val presence: Float? = null,
    val visibility: Float? = null
) {
    init {
        require(index >= 0)
        require(presence == null || presence in 0f..1f)
        require(visibility == null || visibility in 0f..1f)
    }
}

data class MediaPipeRawTriangle(
    val firstIndex: Int,
    val secondIndex: Int,
    val thirdIndex: Int
) {
    init {
        require(firstIndex >= 0)
        require(secondIndex >= 0)
        require(thirdIndex >= 0)
        require(setOf(firstIndex, secondIndex, thirdIndex).size == 3)
    }

    val indices: Set<Int> get() = setOf(firstIndex, secondIndex, thirdIndex)
}

data class MediaPipeBlendshapeScore(
    val categoryName: String,
    val score: Float
) {
    init {
        require(categoryName.isNotBlank())
        require(score.isFinite())
    }
}

data class MediaPipeTransformationMatrix(
    val rowMajorValues: List<Float>
) {
    init {
        require(rowMajorValues.size == 16)
        require(rowMajorValues.all(Float::isFinite))
    }
}

data class MediaPipeDenseFaceCandidate(
    val detectedFaceIndex: Int,
    val landmarks: List<MediaPipeRawLandmark>,
    val triangles: List<MediaPipeRawTriangle>,
    val blendshapes: List<MediaPipeBlendshapeScore> = emptyList(),
    val transformationMatrices: List<MediaPipeTransformationMatrix> = emptyList()
) {
    init {
        require(detectedFaceIndex >= 0)
        require(landmarks.map(MediaPipeRawLandmark::index).distinct().size == landmarks.size)
    }
}

data class AdaptedDenseFacePoint(
    val index: Int,
    val rawPointInRoi: NormalizedPoint3D,
    val mappedPointInSource: NormalizedPoint3D?,
    val activePointInSource: NormalizedPoint3D?,
    val state: DenseFacePointState,
    val presence: Float?,
    val visibility: Float?
)

data class MediaPipeDenseFaceAdapterDiagnostics(
    val adapterVersion: String = MEDIAPIPE_DENSE_FACE_ADAPTER_VERSION,
    val selectedFaceIndex: Int,
    val rawLandmarkCount: Int,
    val finiteLandmarkCount: Int,
    val activeLandmarkCount: Int,
    val clippedLandmarkCount: Int,
    val rejectedNonFiniteLandmarkCount: Int,
    val rawTriangleCount: Int,
    val activeTriangleCount: Int,
    val rejectedTriangleMissingVertexCount: Int,
    val rejectedTriangleInactiveVertexCount: Int,
    val blendshapeCount: Int,
    val transformationMatrixCount: Int
)

data class MediaPipeDenseFaceAdaptationResult(
    val pointsByIndex: Map<Int, AdaptedDenseFacePoint>,
    val activeTriangles: List<MediaPipeRawTriangle>,
    val blendshapes: List<MediaPipeBlendshapeScore>,
    val transformationMatrices: List<MediaPipeTransformationMatrix>,
    val diagnostics: MediaPipeDenseFaceAdapterDiagnostics
)

/**
 * Converts one explicitly selected MediaPipe ROI result into source-image coordinates.
 *
 * Raw ROI coordinates and original indices are always preserved. Source clipping is explicit and
 * separately counted. A triangle becomes active only when all three referenced indices exist and
 * all three vertices remain active after mapping. No missing vertex or hidden geometry is inferred.
 */
object MediaPipeDenseFaceGeometryAdapter {

    fun adapt(
        candidate: MediaPipeDenseFaceCandidate,
        activeRoi: ActiveFaceRoi,
        clipToSourceBounds: Boolean = true
    ): MediaPipeDenseFaceAdaptationResult {
        require(candidate.detectedFaceIndex == activeRoi.selectedFaceIndex)

        val points = candidate.landmarks.associate { landmark ->
            val raw = landmark.pointInRoi
            val finite = raw.x.isFinite() && raw.y.isFinite() && (raw.z?.isFinite() != false)
            val mapped = if (finite) MediaPipeFaceRoiTransform.roiToSource(raw, activeRoi) else null
            val inside = mapped != null && mapped.x in 0f..1f && mapped.y in 0f..1f
            val active = when {
                mapped == null -> null
                inside -> mapped
                clipToSourceBounds -> mapped.copy(
                    x = mapped.x.coerceIn(0f, 1f),
                    y = mapped.y.coerceIn(0f, 1f)
                )
                else -> null
            }
            val state = when {
                mapped == null -> DenseFacePointState.NON_FINITE_REJECTED
                inside -> DenseFacePointState.INSIDE_SOURCE
                clipToSourceBounds -> DenseFacePointState.CLIPPED_TO_SOURCE
                else -> DenseFacePointState.CLIPPED_TO_SOURCE
            }
            landmark.index to AdaptedDenseFacePoint(
                index = landmark.index,
                rawPointInRoi = raw,
                mappedPointInSource = mapped,
                activePointInSource = active,
                state = state,
                presence = landmark.presence,
                visibility = landmark.visibility
            )
        }

        var missingVertexTriangles = 0
        var inactiveVertexTriangles = 0
        val activeTriangles = candidate.triangles.filter { triangle ->
            val referenced = triangle.indices.map(points::get)
            when {
                referenced.any { it == null } -> {
                    missingVertexTriangles += 1
                    false
                }
                referenced.any { it?.activePointInSource == null } -> {
                    inactiveVertexTriangles += 1
                    false
                }
                else -> true
            }
        }

        val finiteCount = points.values.count { it.mappedPointInSource != null }
        val activeCount = points.values.count { it.activePointInSource != null }
        val clippedCount = points.values.count { it.state == DenseFacePointState.CLIPPED_TO_SOURCE }
        val nonFiniteCount = points.values.count {
            it.state == DenseFacePointState.NON_FINITE_REJECTED
        }

        return MediaPipeDenseFaceAdaptationResult(
            pointsByIndex = points,
            activeTriangles = activeTriangles,
            blendshapes = candidate.blendshapes,
            transformationMatrices = candidate.transformationMatrices,
            diagnostics = MediaPipeDenseFaceAdapterDiagnostics(
                selectedFaceIndex = candidate.detectedFaceIndex,
                rawLandmarkCount = candidate.landmarks.size,
                finiteLandmarkCount = finiteCount,
                activeLandmarkCount = activeCount,
                clippedLandmarkCount = clippedCount,
                rejectedNonFiniteLandmarkCount = nonFiniteCount,
                rawTriangleCount = candidate.triangles.size,
                activeTriangleCount = activeTriangles.size,
                rejectedTriangleMissingVertexCount = missingVertexTriangles,
                rejectedTriangleInactiveVertexCount = inactiveVertexTriangles,
                blendshapeCount = candidate.blendshapes.size,
                transformationMatrixCount = candidate.transformationMatrices.size
            )
        )
    }
}