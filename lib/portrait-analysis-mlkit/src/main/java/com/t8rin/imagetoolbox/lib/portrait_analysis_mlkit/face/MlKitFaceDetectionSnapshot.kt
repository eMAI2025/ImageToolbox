/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis_mlkit.face

import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ClassificationObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ConfidenceSource
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ContourObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.LandmarkObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.NormalizedPoint3D
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ObservationBackend
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.PoseObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.RegionObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.VisibilityState

data class MlKitFaceDetectionPointSnapshot(
    val xPixels: Float,
    val yPixels: Float
) {
    init {
        require(xPixels.isFinite())
        require(yPixels.isFinite())
    }
}

data class MlKitFaceDetectionLandmarkSnapshot(
    val id: String,
    val point: MlKitFaceDetectionPointSnapshot
) {
    init {
        require(id.isNotBlank())
    }
}

data class MlKitFaceDetectionContourSnapshot(
    val id: String,
    val points: List<MlKitFaceDetectionPointSnapshot>,
    val closed: Boolean
) {
    init {
        require(id.isNotBlank())
        require(points.isNotEmpty())
        require(!closed || points.size >= 3)
    }
}

data class MlKitDetectedFaceSnapshot(
    val boundingLeftPixels: Float,
    val boundingTopPixels: Float,
    val boundingRightPixels: Float,
    val boundingBottomPixels: Float,
    val yawDegrees: Float,
    val pitchDegrees: Float,
    val rollDegrees: Float,
    val smilingProbability: Float? = null,
    val leftEyeOpenProbability: Float? = null,
    val rightEyeOpenProbability: Float? = null,
    val landmarks: List<MlKitFaceDetectionLandmarkSnapshot> = emptyList(),
    val contours: List<MlKitFaceDetectionContourSnapshot> = emptyList()
) {
    init {
        require(boundingLeftPixels.isFinite())
        require(boundingTopPixels.isFinite())
        require(boundingRightPixels.isFinite())
        require(boundingBottomPixels.isFinite())
        require(boundingRightPixels >= boundingLeftPixels)
        require(boundingBottomPixels >= boundingTopPixels)
        require(yawDegrees.isFinite())
        require(pitchDegrees.isFinite())
        require(rollDegrees.isFinite())
        require(smilingProbability == null || smilingProbability in 0f..1f)
        require(leftEyeOpenProbability == null || leftEyeOpenProbability in 0f..1f)
        require(rightEyeOpenProbability == null || rightEyeOpenProbability in 0f..1f)
        require(landmarks.map { it.id }.distinct().size == landmarks.size)
        require(contours.map { it.id }.distinct().size == contours.size)
    }
}

data class MlKitFaceDetectionSnapshot(
    val imageWidth: Int,
    val imageHeight: Int,
    val faces: List<MlKitDetectedFaceSnapshot>
) {
    init {
        require(imageWidth > 0)
        require(imageHeight > 0)
    }
}

/** Maps ML Kit Face Detection into normalized, backend-independent geometry. */
object MlKitFaceDetectionObservationMapper {

    fun map(snapshot: MlKitFaceDetectionSnapshot): SubjectObservation {
        val landmarks = linkedMapOf<String, LandmarkObservation>()
        val contours = linkedMapOf<String, ContourObservation>()
        val regions = linkedMapOf<String, RegionObservation>()
        val classifications = linkedMapOf<String, ClassificationObservation>()

        snapshot.faces.forEachIndexed { faceIndex, face ->
            addBoundingGeometry(
                faceIndex = faceIndex,
                face = face,
                imageWidth = snapshot.imageWidth,
                imageHeight = snapshot.imageHeight,
                landmarks = landmarks,
                contours = contours
            )

            face.landmarks.forEach { landmark ->
                val id = rawLandmarkId(faceIndex, landmark.id)
                val point = normalize(landmark.point, snapshot.imageWidth, snapshot.imageHeight)
                landmarks[id] = observation(id, point)
            }

            face.contours.forEach { contour ->
                val contourVertexIds = contour.points.mapIndexed { pointIndex, point ->
                    val id = rawContourPointId(faceIndex, contour.id, pointIndex)
                    landmarks[id] = observation(
                        id,
                        normalize(point, snapshot.imageWidth, snapshot.imageHeight)
                    )
                    id
                }
                val contourId = rawContourId(faceIndex, contour.id)
                contours[contourId] = ContourObservation(
                    id = contourId,
                    vertexIds = contourVertexIds,
                    closed = contour.closed,
                    confidence = null,
                    confidenceSource = ConfidenceSource.UNAVAILABLE,
                    backend = ObservationBackend.ML_KIT_FACE_DETECTION
                )
            }

            val regionId = rawFaceRegionId(faceIndex)
            regions[regionId] = RegionObservation(
                id = regionId,
                confidence = null,
                confidenceSource = ConfidenceSource.UNAVAILABLE,
                occlusion = null,
                pixelCoverage = normalizedBoundingCoverage(
                    face,
                    snapshot.imageWidth,
                    snapshot.imageHeight
                ),
                backend = ObservationBackend.ML_KIT_FACE_DETECTION
            )

            face.smilingProbability?.let {
                addClassification(classifications, rawClassificationId(faceIndex, SMILING_PROBABILITY), it)
            }
            face.leftEyeOpenProbability?.let {
                addClassification(classifications, rawClassificationId(faceIndex, LEFT_EYE_OPEN_PROBABILITY), it)
            }
            face.rightEyeOpenProbability?.let {
                addClassification(classifications, rawClassificationId(faceIndex, RIGHT_EYE_OPEN_PROBABILITY), it)
            }
        }

        val facePoses = snapshot.faces.mapIndexed { faceIndex, face ->
            faceIndex to PoseObservation(face.yawDegrees, face.pitchDegrees, face.rollDegrees)
        }.toMap()
        val pose = facePoses.values.singleOrNull() ?: PoseObservation()

        return SubjectObservation(
            subjectCount = snapshot.faces.size,
            faceCount = snapshot.faces.size,
            bodyCount = 0,
            landmarks = landmarks,
            regions = regions,
            pose = pose,
            facePoses = facePoses,
            classifications = classifications,
            contours = contours
        )
    }

    private fun addBoundingGeometry(
        faceIndex: Int,
        face: MlKitDetectedFaceSnapshot,
        imageWidth: Int,
        imageHeight: Int,
        landmarks: MutableMap<String, LandmarkObservation>,
        contours: MutableMap<String, ContourObservation>
    ) {
        val cornerPoints = listOf(
            "top_left" to MlKitFaceDetectionPointSnapshot(
                face.boundingLeftPixels,
                face.boundingTopPixels
            ),
            "top_right" to MlKitFaceDetectionPointSnapshot(
                face.boundingRightPixels,
                face.boundingTopPixels
            ),
            "bottom_right" to MlKitFaceDetectionPointSnapshot(
                face.boundingRightPixels,
                face.boundingBottomPixels
            ),
            "bottom_left" to MlKitFaceDetectionPointSnapshot(
                face.boundingLeftPixels,
                face.boundingBottomPixels
            )
        )
        val vertexIds = cornerPoints.map { (corner, point) ->
            val id = rawBoundingPointId(faceIndex, corner)
            landmarks[id] = observation(id, normalize(point, imageWidth, imageHeight))
            id
        }
        val centerId = rawFaceCenterId(faceIndex)
        landmarks[centerId] = observation(
            centerId,
            normalize(
                MlKitFaceDetectionPointSnapshot(
                    xPixels = (face.boundingLeftPixels + face.boundingRightPixels) / 2f,
                    yPixels = (face.boundingTopPixels + face.boundingBottomPixels) / 2f
                ),
                imageWidth,
                imageHeight
            )
        )
        val contourId = rawBoundingContourId(faceIndex)
        contours[contourId] = ContourObservation(
            id = contourId,
            vertexIds = vertexIds,
            closed = true,
            confidence = null,
            confidenceSource = ConfidenceSource.UNAVAILABLE,
            backend = ObservationBackend.ML_KIT_FACE_DETECTION
        )
    }

    fun rawLandmarkId(faceIndex: Int, landmarkId: String): String =
        "face_${faceIndex}_detection_landmark_$landmarkId"

    fun rawContourPointId(faceIndex: Int, contourId: String, pointIndex: Int): String =
        "face_${faceIndex}_detection_contour_${contourId}_point_$pointIndex"

    fun rawContourId(faceIndex: Int, contourId: String): String =
        "face_${faceIndex}_detection_contour_$contourId"

    fun rawBoundingPointId(faceIndex: Int, corner: String): String =
        "face_${faceIndex}_detection_bounding_box_$corner"

    fun rawBoundingContourId(faceIndex: Int): String =
        "face_${faceIndex}_detection_bounding_box"

    fun rawFaceCenterId(faceIndex: Int): String = "face_${faceIndex}_detection_center"

    fun rawFaceRegionId(faceIndex: Int): String = "face_${faceIndex}_detection_region"

    fun rawClassificationId(faceIndex: Int, classificationId: String): String =
        "face_${faceIndex}_detection_$classificationId"

    private fun observation(id: String, point: NormalizedPoint3D) = LandmarkObservation(
        id = id,
        point = point,
        confidence = null,
        confidenceSource = ConfidenceSource.UNAVAILABLE,
        visibility = visibilityFor(point),
        backend = ObservationBackend.ML_KIT_FACE_DETECTION
    )

    private fun normalize(
        point: MlKitFaceDetectionPointSnapshot,
        imageWidth: Int,
        imageHeight: Int
    ) = NormalizedPoint3D(
        x = point.xPixels / imageWidth,
        y = point.yPixels / imageHeight
    )

    private fun visibilityFor(point: NormalizedPoint3D): VisibilityState = when {
        point.x in 0f..1f && point.y in 0f..1f -> VisibilityState.VISIBLE
        point.x.isFinite() && point.y.isFinite() -> VisibilityState.OUTSIDE_FRAME
        else -> VisibilityState.AMBIGUOUS
    }

    private fun normalizedBoundingCoverage(
        face: MlKitDetectedFaceSnapshot,
        imageWidth: Int,
        imageHeight: Int
    ): Float {
        val left = (face.boundingLeftPixels / imageWidth).coerceIn(0f, 1f)
        val top = (face.boundingTopPixels / imageHeight).coerceIn(0f, 1f)
        val right = (face.boundingRightPixels / imageWidth).coerceIn(0f, 1f)
        val bottom = (face.boundingBottomPixels / imageHeight).coerceIn(0f, 1f)
        return ((right - left).coerceAtLeast(0f) * (bottom - top).coerceAtLeast(0f))
            .coerceIn(0f, 1f)
    }

    private fun addClassification(
        classifications: MutableMap<String, ClassificationObservation>,
        id: String,
        probability: Float
    ) {
        classifications[id] = ClassificationObservation(
            id = id,
            probability = probability,
            backend = ObservationBackend.ML_KIT_FACE_DETECTION
        )
    }

    const val SMILING_PROBABILITY = "smiling_probability"
    const val LEFT_EYE_OPEN_PROBABILITY = "left_eye_open_probability"
    const val RIGHT_EYE_OPEN_PROBABILITY = "right_eye_open_probability"
}
