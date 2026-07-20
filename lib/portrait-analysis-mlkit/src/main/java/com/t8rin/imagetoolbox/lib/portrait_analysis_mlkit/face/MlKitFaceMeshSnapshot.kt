/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis_mlkit.face

import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ConfidenceSource
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.LandmarkObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.MeshObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.MeshTriangleObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.NormalizedPoint3D
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ObservationBackend
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.RegionObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.VisibilityState

/** Platform-neutral snapshot of one ML Kit Face Mesh result. */
data class MlKitFaceMeshPointSnapshot(
    val index: Int,
    val xPixels: Float,
    val yPixels: Float,
    val zPixels: Float
) {
    init {
        require(index >= 0) { "index cannot be negative" }
        require(xPixels.isFinite()) { "xPixels must be finite" }
        require(yPixels.isFinite()) { "yPixels must be finite" }
        require(zPixels.isFinite()) { "zPixels must be finite" }
    }
}

data class MlKitFaceMeshTriangleSnapshot(
    val firstPointIndex: Int,
    val secondPointIndex: Int,
    val thirdPointIndex: Int
) {
    init {
        require(
            setOf(firstPointIndex, secondPointIndex, thirdPointIndex).size == 3
        ) {
            "A triangle must reference three different point indices"
        }
        require(firstPointIndex >= 0)
        require(secondPointIndex >= 0)
        require(thirdPointIndex >= 0)
    }

    val pointIndices: Set<Int>
        get() = setOf(firstPointIndex, secondPointIndex, thirdPointIndex)
}

data class MlKitFaceSnapshot(
    val points: List<MlKitFaceMeshPointSnapshot>,
    val triangles: List<MlKitFaceMeshTriangleSnapshot> = emptyList()
) {
    init {
        val pointIndices = points.map { it.index }
        require(pointIndices.distinct().size == pointIndices.size) {
            "Face mesh point indices must be unique"
        }
        val unknownIndices = triangles
            .flatMap { it.pointIndices }
            .filterNot { it in pointIndices }
            .distinct()
            .sorted()
        require(unknownIndices.isEmpty()) {
            "Triangles reference unknown face mesh points: ${unknownIndices.joinToString()}"
        }
    }
}

data class MlKitFaceMeshSnapshot(
    val imageWidth: Int,
    val imageHeight: Int,
    val faces: List<MlKitFaceSnapshot>
) {
    init {
        require(imageWidth > 0) { "imageWidth must be positive" }
        require(imageHeight > 0) { "imageHeight must be positive" }
    }
}

/**
 * Converts ML Kit output without inventing confidence, visibility or semantic aliases.
 *
 * ML Kit Face Mesh exposes a stable point index and geometry, but no per-point confidence.
 * Therefore confidence remains unavailable and strict parameter gates stay disabled until
 * another detector or validated derived metric supplies the missing evidence.
 */
object MlKitFaceMeshObservationMapper {

    fun map(snapshot: MlKitFaceMeshSnapshot): SubjectObservation {
        val landmarks = buildMap {
            snapshot.faces.forEachIndexed { faceIndex, face ->
                face.points.forEach { point ->
                    val x = point.xPixels / snapshot.imageWidth
                    val y = point.yPixels / snapshot.imageHeight
                    val pointId = rawPointId(faceIndex, point.index)
                    put(
                        pointId,
                        LandmarkObservation(
                            id = pointId,
                            point = NormalizedPoint3D(
                                x = x,
                                y = y,
                                z = point.zPixels / snapshot.imageWidth
                            ),
                            confidence = null,
                            confidenceSource = ConfidenceSource.UNAVAILABLE,
                            visibility = visibilityFor(x, y),
                            backend = ObservationBackend.ML_KIT_FACE_MESH
                        )
                    )
                }
            }
        }

        val regions = buildMap {
            snapshot.faces.forEachIndexed { faceIndex, face ->
                val normalizedPoints = face.points.map {
                    Pair(
                        it.xPixels / snapshot.imageWidth,
                        it.yPixels / snapshot.imageHeight
                    )
                }
                if (normalizedPoints.isNotEmpty()) {
                    val minX = normalizedPoints.minOf { it.first }.coerceIn(0f, 1f)
                    val maxX = normalizedPoints.maxOf { it.first }.coerceIn(0f, 1f)
                    val minY = normalizedPoints.minOf { it.second }.coerceIn(0f, 1f)
                    val maxY = normalizedPoints.maxOf { it.second }.coerceIn(0f, 1f)
                    val coverage = ((maxX - minX) * (maxY - minY)).coerceIn(0f, 1f)
                    val regionId = rawRegionId(faceIndex)
                    put(
                        regionId,
                        RegionObservation(
                            id = regionId,
                            confidence = null,
                            confidenceSource = ConfidenceSource.UNAVAILABLE,
                            occlusion = null,
                            pixelCoverage = coverage,
                            backend = ObservationBackend.ML_KIT_FACE_MESH
                        )
                    )
                }
            }
        }

        val meshes = buildMap {
            snapshot.faces.forEachIndexed { faceIndex, face ->
                if (face.points.isNotEmpty()) {
                    val meshId = rawMeshId(faceIndex)
                    val vertexIds = face.points
                        .map { rawPointId(faceIndex, it.index) }
                        .toSet()
                    val triangles = face.triangles.map { triangle ->
                        MeshTriangleObservation(
                            firstVertexId = rawPointId(faceIndex, triangle.firstPointIndex),
                            secondVertexId = rawPointId(faceIndex, triangle.secondPointIndex),
                            thirdVertexId = rawPointId(faceIndex, triangle.thirdPointIndex)
                        )
                    }
                    put(
                        meshId,
                        MeshObservation(
                            id = meshId,
                            vertexIds = vertexIds,
                            triangles = triangles,
                            backend = ObservationBackend.ML_KIT_FACE_MESH
                        )
                    )
                }
            }
        }

        return SubjectObservation(
            subjectCount = snapshot.faces.size,
            faceCount = snapshot.faces.size,
            bodyCount = 0,
            landmarks = landmarks,
            regions = regions,
            meshes = meshes
        )
    }

    fun rawPointId(faceIndex: Int, pointIndex: Int): String =
        "face_${faceIndex}_mesh_$pointIndex"

    fun rawRegionId(faceIndex: Int): String = "face_${faceIndex}_mesh_region"

    fun rawMeshId(faceIndex: Int): String = "face_${faceIndex}_mesh"

    private fun visibilityFor(x: Float, y: Float): VisibilityState = when {
        x in 0f..1f && y in 0f..1f -> VisibilityState.VISIBLE
        x.isFinite() && y.isFinite() -> VisibilityState.OUTSIDE_FRAME
        else -> VisibilityState.AMBIGUOUS
    }
}
