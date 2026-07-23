/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis_mlkit.face

import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import com.t8rin.imagetoolbox.lib.portrait_analysis.engine.PortraitObservationEngine
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ObservationBackend
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis_mlkit.MlKitImageInput
import com.t8rin.imagetoolbox.lib.portrait_analysis_mlkit.awaitResult

/** Observation-only accurate face detector for the Market build. */
class MlKitFaceDetectionObservationEngine(
    private val detector: FaceDetector = FaceDetection.getClient(DEFAULT_OPTIONS)
) : PortraitObservationEngine<MlKitImageInput>, AutoCloseable {

    override val backend: ObservationBackend = ObservationBackend.ML_KIT_FACE_DETECTION

    override suspend fun observe(input: MlKitImageInput): SubjectObservation {
        val faces = detector.process(input.image).awaitResult()
        return MlKitFaceDetectionObservationMapper.map(
            MlKitFaceDetectionSnapshot(
                imageWidth = input.width,
                imageHeight = input.height,
                faces = faces.map(::toSnapshot)
            )
        )
    }

    override fun close() {
        detector.close()
    }

    private fun toSnapshot(face: Face): MlKitDetectedFaceSnapshot {
        val contours = CONTOUR_SPECS.mapNotNull { spec ->
            face.getContour(spec.type)?.points
                ?.takeIf { it.isNotEmpty() }
                ?.let { points ->
                    MlKitFaceDetectionContourSnapshot(
                        id = spec.id,
                        points = points.map { point ->
                            MlKitFaceDetectionPointSnapshot(point.x, point.y)
                        },
                        closed = spec.closed
                    )
                }
        }
        return MlKitDetectedFaceSnapshot(
            boundingLeftPixels = face.boundingBox.left.toFloat(),
            boundingTopPixels = face.boundingBox.top.toFloat(),
            boundingRightPixels = face.boundingBox.right.toFloat(),
            boundingBottomPixels = face.boundingBox.bottom.toFloat(),
            yawDegrees = face.headEulerAngleY,
            pitchDegrees = face.headEulerAngleX,
            rollDegrees = face.headEulerAngleZ,
            smilingProbability = face.smilingProbability,
            leftEyeOpenProbability = face.leftEyeOpenProbability,
            rightEyeOpenProbability = face.rightEyeOpenProbability,
            landmarks = LANDMARK_SPECS.mapNotNull { spec ->
                face.getLandmark(spec.type)?.let { landmark ->
                    MlKitFaceDetectionLandmarkSnapshot(
                        id = spec.id,
                        point = MlKitFaceDetectionPointSnapshot(
                            landmark.position.x,
                            landmark.position.y
                        )
                    )
                }
            },
            contours = contours + deriveLowerFaceContours(contours)
        )
    }

    private fun deriveLowerFaceContours(
        contours: List<MlKitFaceDetectionContourSnapshot>
    ): List<MlKitFaceDetectionContourSnapshot> {
        val face = contours.firstOrNull { it.id == "face" && it.points.size >= 7 }
            ?: return emptyList()
        val points = face.points
        val leftIndex = points.indices.minBy { points[it].xPixels }
        val rightIndex = points.indices.maxBy { points[it].xPixels }
        val chinIndex = points.indices.maxBy { points[it].yPixels }
        val forward = cyclicPath(points, leftIndex, rightIndex, step = 1)
        val backward = cyclicPath(points, leftIndex, rightIndex, step = -1)
        val jawline = if (chinIndex in forward.indicesFromSource(points, leftIndex, step = 1)) {
            forward
        } else {
            backward
        }
        val chinRadius = (points.size / 18).coerceIn(2, 4)
        val chin = (-chinRadius..chinRadius)
            .map { offset -> points[(chinIndex + offset).floorMod(points.size)] }
            .distinct()

        return buildList {
            if (jawline.size >= 3) {
                add(MlKitFaceDetectionContourSnapshot("jawline", jawline, closed = false))
            }
            if (chin.size >= 3) {
                add(MlKitFaceDetectionContourSnapshot("chin", chin, closed = false))
            }
        }
    }

    private fun cyclicPath(
        points: List<MlKitFaceDetectionPointSnapshot>,
        startIndex: Int,
        endIndex: Int,
        step: Int
    ): List<MlKitFaceDetectionPointSnapshot> {
        val result = mutableListOf<MlKitFaceDetectionPointSnapshot>()
        var index = startIndex
        repeat(points.size) {
            result += points[index]
            if (index == endIndex) return result
            index = (index + step).floorMod(points.size)
        }
        return result
    }

    private fun List<MlKitFaceDetectionPointSnapshot>.indicesFromSource(
        source: List<MlKitFaceDetectionPointSnapshot>,
        startIndex: Int,
        step: Int
    ): Set<Int> {
        val selected = toSet()
        return buildSet {
            var index = startIndex
            repeat(source.size) {
                if (source[index] in selected) add(index)
                index = (index + step).floorMod(source.size)
            }
        }
    }

    private fun Int.floorMod(modulus: Int): Int = ((this % modulus) + modulus) % modulus

    private data class LandmarkSpec(val id: String, val type: Int)
    private data class ContourSpec(val id: String, val type: Int, val closed: Boolean)

    private companion object {
        val DEFAULT_OPTIONS: FaceDetectorOptions = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.10f)
            .build()

        val LANDMARK_SPECS = listOf(
            LandmarkSpec("mouth_bottom", FaceLandmark.MOUTH_BOTTOM),
            LandmarkSpec("left_cheek", FaceLandmark.LEFT_CHEEK),
            LandmarkSpec("left_ear", FaceLandmark.LEFT_EAR),
            LandmarkSpec("left_eye", FaceLandmark.LEFT_EYE),
            LandmarkSpec("mouth_left", FaceLandmark.MOUTH_LEFT),
            LandmarkSpec("nose_base", FaceLandmark.NOSE_BASE),
            LandmarkSpec("right_cheek", FaceLandmark.RIGHT_CHEEK),
            LandmarkSpec("right_ear", FaceLandmark.RIGHT_EAR),
            LandmarkSpec("right_eye", FaceLandmark.RIGHT_EYE),
            LandmarkSpec("mouth_right", FaceLandmark.MOUTH_RIGHT)
        )

        val CONTOUR_SPECS = listOf(
            ContourSpec("face", FaceContour.FACE, closed = true),
            ContourSpec("left_eyebrow_top", FaceContour.LEFT_EYEBROW_TOP, closed = false),
            ContourSpec("left_eyebrow_bottom", FaceContour.LEFT_EYEBROW_BOTTOM, closed = false),
            ContourSpec("right_eyebrow_top", FaceContour.RIGHT_EYEBROW_TOP, closed = false),
            ContourSpec("right_eyebrow_bottom", FaceContour.RIGHT_EYEBROW_BOTTOM, closed = false),
            ContourSpec("left_eye", FaceContour.LEFT_EYE, closed = true),
            ContourSpec("right_eye", FaceContour.RIGHT_EYE, closed = true),
            ContourSpec("upper_lip_top", FaceContour.UPPER_LIP_TOP, closed = false),
            ContourSpec("upper_lip_bottom", FaceContour.UPPER_LIP_BOTTOM, closed = false),
            ContourSpec("lower_lip_top", FaceContour.LOWER_LIP_TOP, closed = false),
            ContourSpec("lower_lip_bottom", FaceContour.LOWER_LIP_BOTTOM, closed = false),
            ContourSpec("nose_bridge", FaceContour.NOSE_BRIDGE, closed = false),
            ContourSpec("nose_bottom", FaceContour.NOSE_BOTTOM, closed = false),
            ContourSpec("left_cheek", FaceContour.LEFT_CHEEK, closed = false),
            ContourSpec("right_cheek", FaceContour.RIGHT_CHEEK, closed = false)
        )
    }
}
