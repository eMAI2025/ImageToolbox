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
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ContourObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.LandmarkObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.MeshObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.MeshTriangleObservation
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

data class MediaPipeFaceContourSnapshot(
    val id: String,
    val pointIndices: List<Int>,
    val closed: Boolean
) {
    init {
        require(id.isNotBlank())
        require(pointIndices.size >= 2)
        require(pointIndices.all { it >= 0 })
        require(pointIndices.distinct().size == pointIndices.size)
        require(!closed || pointIndices.size >= 3)
    }
}

data class MediaPipeFaceTriangleSnapshot(
    val firstPointIndex: Int,
    val secondPointIndex: Int,
    val thirdPointIndex: Int
) {
    init {
        require(firstPointIndex >= 0)
        require(secondPointIndex >= 0)
        require(thirdPointIndex >= 0)
        require(setOf(firstPointIndex, secondPointIndex, thirdPointIndex).size == 3)
    }
}

data class MediaPipeFaceSnapshot(
    val landmarks: List<MediaPipeFaceLandmarkSnapshot>,
    val blendshapes: List<MediaPipeBlendshapeSnapshot> = emptyList(),
    val transformationMatrix: List<Float> = emptyList(),
    val yawDegrees: Float? = null,
    val pitchDegrees: Float? = null,
    val rollDegrees: Float? = null,
    val detectionConfidence: Float? = null,
    val contours: List<MediaPipeFaceContourSnapshot> = emptyList(),
    val triangles: List<MediaPipeFaceTriangleSnapshot> = emptyList()
) {
    init {
        val pointIndices = landmarks.map { it.index }.toSet()
        require(pointIndices.size == landmarks.size)
        require(blendshapes.map { it.name }.distinct().size == blendshapes.size)
        require(transformationMatrix.isEmpty() || transformationMatrix.size == 16)
        require(transformationMatrix.all(Float::isFinite))
        require(detectionConfidence == null || detectionConfidence in 0f..1f)
        require(contours.map { it.id }.distinct().size == contours.size)
        val referenced = buildList {
            contours.forEach { addAll(it.pointIndices) }
            triangles.forEach {
                add(it.firstPointIndex)
                add(it.secondPointIndex)
                add(it.thirdPointIndex)
            }
        }
        require(referenced.all { it in pointIndices }) {
            "MediaPipe topology references an unknown landmark"
        }
    }
}

data class MediaPipeFaceLandmarkerSnapshot(
    val faces: List<MediaPipeFaceSnapshot>
)

object MediaPipeFaceObservationMapper {

    fun map(snapshot: MediaPipeFaceLandmarkerSnapshot): SubjectObservation {
        val landmarks = linkedMapOf<String, LandmarkObservation>()
        val contours = linkedMapOf<String, ContourObservation>()
        val meshes = linkedMapOf<String, MeshObservation>()
        val regions = linkedMapOf<String, RegionObservation>()
        val classifications = linkedMapOf<String, ClassificationObservation>()

        snapshot.faces.forEachIndexed { faceIndex, face ->
            face.landmarks.forEach { point ->
                val id = rawPointId(faceIndex, point.index)
                val confidence = point.presence ?: point.visibility
                landmarks[id] = LandmarkObservation(
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
            }

            addBoundingGeometry(faceIndex, face, landmarks, contours)

            face.contours.forEach { contour ->
                val id = rawContourId(faceIndex, contour.id)
                contours[id] = ContourObservation(
                    id = id,
                    vertexIds = contour.pointIndices.map { rawPointId(faceIndex, it) },
                    closed = contour.closed,
                    confidence = face.detectionConfidence,
                    confidenceSource = confidenceSource(face.detectionConfidence),
                    backend = ObservationBackend.MEDIAPIPE_FACE_LANDMARKER
                )
            }

            if (face.landmarks.isNotEmpty()) {
                val meshId = rawMeshId(faceIndex)
                meshes[meshId] = MeshObservation(
                    id = meshId,
                    vertexIds = face.landmarks.map { rawPointId(faceIndex, it.index) }.toSet(),
                    triangles = face.triangles.map {
                        MeshTriangleObservation(
                            rawPointId(faceIndex, it.firstPointIndex),
                            rawPointId(faceIndex, it.secondPointIndex),
                            rawPointId(faceIndex, it.thirdPointIndex)
                        )
                    },
                    backend = ObservationBackend.MEDIAPIPE_FACE_LANDMARKER
                )
            }

            if (face.landmarks.isNotEmpty()) {
                val minX = face.landmarks.minOf { it.x }.coerceIn(0f, 1f)
                val maxX = face.landmarks.maxOf { it.x }.coerceIn(0f, 1f)
                val minY = face.landmarks.minOf { it.y }.coerceIn(0f, 1f)
                val maxY = face.landmarks.maxOf { it.y }.coerceIn(0f, 1f)
                val id = rawRegionId(faceIndex)
                regions[id] = RegionObservation(
                    id = id,
                    confidence = face.detectionConfidence,
                    confidenceSource = confidenceSource(face.detectionConfidence),
                    occlusion = null,
                    pixelCoverage = ((maxX - minX) * (maxY - minY)).coerceIn(0f, 1f),
                    backend = ObservationBackend.MEDIAPIPE_FACE_LANDMARKER
                )
            }

            face.blendshapes.forEach { blendshape ->
                val id = blendshapeId(faceIndex, blendshape.name)
                classifications[id] = ClassificationObservation(
                    id = id,
                    probability = blendshape.score,
                    backend = ObservationBackend.MEDIAPIPE_FACE_LANDMARKER
                )
            }
        }

        val facePoses = snapshot.faces.mapIndexed { faceIndex, face ->
            faceIndex to PoseObservation(
                yawDegrees = face.yawDegrees,
                pitchDegrees = face.pitchDegrees,
                rollDegrees = face.rollDegrees
            )
        }.toMap()

        return SubjectObservation(
            subjectCount = snapshot.faces.size,
            faceCount = snapshot.faces.size,
            bodyCount = 0,
            landmarks = landmarks,
            regions = regions,
            classifications = classifications,
            contours = contours,
            meshes = meshes,
            pose = facePoses.values.singleOrNull() ?: PoseObservation(),
            facePoses = facePoses
        )
    }

    private fun addBoundingGeometry(
        faceIndex: Int,
        face: MediaPipeFaceSnapshot,
        landmarks: MutableMap<String, LandmarkObservation>,
        contours: MutableMap<String, ContourObservation>
    ) {
        if (face.landmarks.isEmpty()) return
        val left = face.landmarks.minOf { it.x }
        val top = face.landmarks.minOf { it.y }
        val right = face.landmarks.maxOf { it.x }
        val bottom = face.landmarks.maxOf { it.y }
        val corners = listOf(
            "top_left" to NormalizedPoint3D(left, top),
            "top_right" to NormalizedPoint3D(right, top),
            "bottom_right" to NormalizedPoint3D(right, bottom),
            "bottom_left" to NormalizedPoint3D(left, bottom)
        )
        val vertexIds = corners.map { (corner, point) ->
            val id = rawBoundingPointId(faceIndex, corner)
            landmarks[id] = LandmarkObservation(
                id = id,
                point = point,
                confidence = face.detectionConfidence,
                confidenceSource = confidenceSource(face.detectionConfidence),
                visibility = VisibilityState.VISIBLE,
                backend = ObservationBackend.MEDIAPIPE_FACE_LANDMARKER
            )
            id
        }
        val contourId = rawBoundingContourId(faceIndex)
        contours[contourId] = ContourObservation(
            id = contourId,
            vertexIds = vertexIds,
            closed = true,
            confidence = face.detectionConfidence,
            confidenceSource = confidenceSource(face.detectionConfidence),
            backend = ObservationBackend.MEDIAPIPE_FACE_LANDMARKER
        )
    }

    fun rawPointId(faceIndex: Int, pointIndex: Int): String =
        "face_${faceIndex}_mediapipe_$pointIndex"

    fun rawRegionId(faceIndex: Int): String = "face_${faceIndex}_mediapipe_region"

    fun rawContourId(faceIndex: Int, contourId: String): String =
        "face_${faceIndex}_mediapipe_contour_$contourId"

    fun rawBoundingPointId(faceIndex: Int, corner: String): String =
        "face_${faceIndex}_mediapipe_bounding_box_$corner"

    fun rawBoundingContourId(faceIndex: Int): String =
        "face_${faceIndex}_mediapipe_bounding_box"

    fun rawMeshId(faceIndex: Int): String = "face_${faceIndex}_mediapipe_mesh"

    fun blendshapeId(faceIndex: Int, name: String): String =
        "face_${faceIndex}_blendshape_$name"

    private fun confidenceSource(confidence: Float?): ConfidenceSource =
        if (confidence == null) ConfidenceSource.UNAVAILABLE else ConfidenceSource.DIRECT

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
