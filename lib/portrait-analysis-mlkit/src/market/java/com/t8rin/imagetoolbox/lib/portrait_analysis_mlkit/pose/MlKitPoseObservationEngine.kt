/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis_mlkit.pose

import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions
import com.t8rin.imagetoolbox.lib.portrait_analysis.engine.PortraitObservationEngine
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ObservationBackend
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis_mlkit.MlKitImageInput
import com.t8rin.imagetoolbox.lib.portrait_analysis_mlkit.awaitResult

/** Observation-only accurate ML Kit pose backend for the Market build. */
class MlKitPoseObservationEngine(
    private val detector: PoseDetector = PoseDetection.getClient(
        AccuratePoseDetectorOptions.Builder()
            .setDetectorMode(AccuratePoseDetectorOptions.SINGLE_IMAGE_MODE)
            .build()
    ),
    private val mappingPolicy: MlKitPoseMappingPolicy = MlKitPoseMappingPolicy()
) : PortraitObservationEngine<MlKitImageInput>, AutoCloseable {

    override val backend: ObservationBackend = ObservationBackend.ML_KIT_POSE

    override suspend fun observe(input: MlKitImageInput): SubjectObservation {
        val pose = detector.process(input.image).awaitResult()
        return MlKitPoseObservationMapper.map(
            snapshot = MlKitPoseSnapshot(
                imageWidth = input.width,
                imageHeight = input.height,
                landmarks = pose.allPoseLandmarks.map { landmark ->
                    MlKitPoseLandmarkSnapshot(
                        id = MlKitPoseLandmarkId.fromType(landmark.landmarkType),
                        xPixels = landmark.position.x,
                        yPixels = landmark.position.y,
                        zPixels = landmark.position3D.z,
                        inFrameLikelihood = landmark.inFrameLikelihood
                    )
                }
            ),
            policy = mappingPolicy
        )
    }

    override fun close() {
        detector.close()
    }
}

internal object MlKitPoseLandmarkId {
    fun fromType(type: Int): String = when (type) {
        PoseLandmark.NOSE -> "nose"
        PoseLandmark.LEFT_EYE_INNER -> "left_eye_inner"
        PoseLandmark.LEFT_EYE -> "left_eye"
        PoseLandmark.LEFT_EYE_OUTER -> "left_eye_outer"
        PoseLandmark.RIGHT_EYE_INNER -> "right_eye_inner"
        PoseLandmark.RIGHT_EYE -> "right_eye"
        PoseLandmark.RIGHT_EYE_OUTER -> "right_eye_outer"
        PoseLandmark.LEFT_EAR -> "left_ear"
        PoseLandmark.RIGHT_EAR -> "right_ear"
        PoseLandmark.LEFT_MOUTH -> "left_mouth"
        PoseLandmark.RIGHT_MOUTH -> "right_mouth"
        PoseLandmark.LEFT_SHOULDER -> "left_shoulder"
        PoseLandmark.RIGHT_SHOULDER -> "right_shoulder"
        PoseLandmark.LEFT_ELBOW -> "left_elbow"
        PoseLandmark.RIGHT_ELBOW -> "right_elbow"
        PoseLandmark.LEFT_WRIST -> "left_wrist"
        PoseLandmark.RIGHT_WRIST -> "right_wrist"
        PoseLandmark.LEFT_PINKY -> "left_pinky"
        PoseLandmark.RIGHT_PINKY -> "right_pinky"
        PoseLandmark.LEFT_INDEX -> "left_index"
        PoseLandmark.RIGHT_INDEX -> "right_index"
        PoseLandmark.LEFT_THUMB -> "left_thumb"
        PoseLandmark.RIGHT_THUMB -> "right_thumb"
        PoseLandmark.LEFT_HIP -> "left_hip"
        PoseLandmark.RIGHT_HIP -> "right_hip"
        PoseLandmark.LEFT_KNEE -> "left_knee"
        PoseLandmark.RIGHT_KNEE -> "right_knee"
        PoseLandmark.LEFT_ANKLE -> "left_ankle"
        PoseLandmark.RIGHT_ANKLE -> "right_ankle"
        PoseLandmark.LEFT_HEEL -> "left_heel"
        PoseLandmark.RIGHT_HEEL -> "right_heel"
        PoseLandmark.LEFT_FOOT_INDEX -> "left_foot_index"
        PoseLandmark.RIGHT_FOOT_INDEX -> "right_foot_index"
        else -> "pose_landmark_$type"
    }
}
