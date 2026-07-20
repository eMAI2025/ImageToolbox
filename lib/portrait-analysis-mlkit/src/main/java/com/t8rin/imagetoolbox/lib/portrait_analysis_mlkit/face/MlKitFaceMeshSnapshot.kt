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
)

data class MlKitFaceSnapshot(
    val points: List<MlKitFaceMeshPointSnapshot>
)

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
                    put(
                        rawPointId(faceIndex, point.index),
                        LandmarkObservation(
                            id = rawPointId(faceIndex, point.index),
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

        return SubjectObservation(
            subjectCount = snapshot.faces.size,
            faceCount = snapshot.faces.size,
            bodyCount = 0,
            landmarks = landmarks,
            regions = regions
        )
    }

    fun rawPointId(faceIndex: Int, pointIndex: Int): String =
        "face_${faceIndex}_mesh_$pointIndex"

    fun rawRegionId(faceIndex: Int): String = "face_${faceIndex}_mesh_region"

    private fun visibilityFor(x: Float, y: Float): VisibilityState = when {
        x in 0f..1f && y in 0f..1f -> VisibilityState.VISIBLE
        x.isFinite() && y.isFinite() -> VisibilityState.OUTSIDE_FRAME
        else -> VisibilityState.AMBIGUOUS
    }
}
