/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.feature.portrait_lab

import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ContourObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.LandmarkObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation

/**
 * Frozen full-image face-detection baseline.
 *
 * This layer inventories every face returned by one full-image detector result. It does not select
 * an active face, crop the source, filter detector points, run a mesh, accept edit geometry or
 * invoke any deformation. Detailed analysis is a separate downstream operation.
 */
object FaceDetectionBaseline {

    const val FINGERPRINT = "FACE_DETECTION_BASELINE_V1"

    private val faceIndexRegex = Regex("^face_(\\d+)_")

    data class FaceRecord(
        val faceIndex: Int,
        val boundingBox: PortraitNormalizedRect,
        val landmarks: List<LandmarkObservation>,
        val contours: List<ContourObservation>,
        val confidence: Float? = null
    )

    data class Result internal constructor(
        val faces: List<FaceRecord>,
        val activeFaceIndex: Int? = null,
        val fullImageObservation: SubjectObservation,
        val fingerprint: String = FINGERPRINT
    ) {
        init {
            require(activeFaceIndex == null) {
                "FACE_DETECTION_BASELINE_V1 must not select an active face"
            }
            require(faces.map(FaceRecord::faceIndex).distinct().size == faces.size)
            require(faces.zipWithNext().all { (left, right) -> left.faceIndex < right.faceIndex })
        }
    }

    fun evaluate(fullImageObservation: SubjectObservation): Result {
        val groupedContours = fullImageObservation.contours.values
            .mapNotNull { contour ->
                val index = faceIndex(contour.id) ?: return@mapNotNull null
                index to contour
            }
            .groupBy(keySelector = { it.first }, valueTransform = { it.second })

        val groupedLandmarks = fullImageObservation.landmarks.values
            .mapNotNull { landmark ->
                val index = faceIndex(landmark.id) ?: return@mapNotNull null
                index to landmark
            }
            .groupBy(keySelector = { it.first }, valueTransform = { it.second })

        val faces = groupedContours.keys
            .asSequence()
            .mapNotNull { faceIndex ->
                val contours = groupedContours.getValue(faceIndex).sortedBy(ContourObservation::id)
                val boundingBoxContour = contours.firstOrNull {
                    it.id.contains("bounding_box") || it.id.contains("bbox")
                } ?: return@mapNotNull null
                val boundingBox = boundingBoxContour.vertexIds
                    .mapNotNull(fullImageObservation.landmarks::get)
                    .map(LandmarkObservation::point)
                    .toBoundsOrNull()
                    ?: return@mapNotNull null

                FaceRecord(
                    faceIndex = faceIndex,
                    boundingBox = boundingBox,
                    landmarks = groupedLandmarks[faceIndex].orEmpty()
                        .sortedBy(LandmarkObservation::id),
                    contours = contours,
                    confidence = null
                )
            }
            .sortedBy(FaceRecord::faceIndex)
            .toList()

        return Result(
            faces = faces,
            activeFaceIndex = null,
            fullImageObservation = fullImageObservation
        )
    }

    private fun faceIndex(id: String): Int? = faceIndexRegex.find(id)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()

    private fun List<com.t8rin.imagetoolbox.lib.portrait_analysis.model.NormalizedPoint3D>
        .toBoundsOrNull(): PortraitNormalizedRect? {
        if (isEmpty()) return null
        val left = minOf { it.x }
        val top = minOf { it.y }
        val right = maxOf { it.x }
        val bottom = maxOf { it.y }
        if (right <= left || bottom <= top) return null
        return PortraitNormalizedRect(left, top, right, bottom)
    }
}
