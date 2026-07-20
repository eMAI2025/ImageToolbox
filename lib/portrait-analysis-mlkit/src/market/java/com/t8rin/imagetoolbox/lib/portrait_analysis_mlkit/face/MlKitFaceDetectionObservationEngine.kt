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

    private fun toSnapshot(face: Face): MlKitDetectedFaceSnapshot = MlKitDetectedFaceSnapshot(
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
                        xPixels = landmark.position.x,
                        yPixels = landmark.position.y
                    )
                )
            }
        },
        contours = CONTOUR_SPECS.mapNotNull { spec ->
            face.getContour(spec.type)?.points
                ?.takeIf { it.isNotEmpty() }
                ?.let { points ->
                    MlKitFaceDetectionContourSnapshot(
                        id = spec.id,
                        points = points.map { point ->
                            MlKitFaceDetectionPointSnapshot(
                                xPixels = point.x,
                                yPixels = point.y
                            )
                        },
                        closed = spec.closed
                    )
                }
        }
    )

    private data class LandmarkSpec(
        val id: String,
        val type: Int
    )

    private data class ContourSpec(
        val id: String,
        val type: Int,
        val closed: Boolean
    )

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
            ContourSpec(
                "left_eyebrow_bottom",
                FaceContour.LEFT_EYEBROW_BOTTOM,
                closed = false
            ),
            ContourSpec("right_eyebrow_top", FaceContour.RIGHT_EYEBROW_TOP, closed = false),
            ContourSpec(
                "right_eyebrow_bottom",
                FaceContour.RIGHT_EYEBROW_BOTTOM,
                closed = false
            ),
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
