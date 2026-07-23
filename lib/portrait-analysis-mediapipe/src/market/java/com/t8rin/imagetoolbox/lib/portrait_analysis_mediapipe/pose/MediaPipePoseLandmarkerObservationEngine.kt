/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis_mediapipe.pose

import android.content.Context
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import com.t8rin.imagetoolbox.lib.portrait_analysis.engine.PortraitObservationEngine
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ObservationBackend
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis_mediapipe.MediaPipeImageInput

/** Explicit IMAGE-mode configuration for the MediaPipe Pose Landmarker adapter. */
data class MediaPipePoseLandmarkerConfig(
    val modelAssetPath: String,
    val maximumPoses: Int = 1,
    val minimumPoseDetectionConfidence: Float = 0.5f,
    val minimumPosePresenceConfidence: Float = 0.5f,
    val minimumTrackingConfidence: Float = 0.5f
) {
    init {
        require(modelAssetPath.isNotBlank())
        require(maximumPoses > 0)
        require(minimumPoseDetectionConfidence in 0f..1f)
        require(minimumPosePresenceConfidence in 0f..1f)
        require(minimumTrackingConfidence in 0f..1f)
    }
}

/** Reusable still-image Pose Landmarker adapter. */
class MediaPipePoseLandmarkerObservationEngine(
    context: Context,
    config: MediaPipePoseLandmarkerConfig
) : PortraitObservationEngine<MediaPipeImageInput>, AutoCloseable {

    override val backend: ObservationBackend = ObservationBackend.MEDIAPIPE_POSE

    private val landmarker: PoseLandmarker = PoseLandmarker.createFromOptions(
        context,
        PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(
                BaseOptions.builder()
                    .setModelAssetPath(config.modelAssetPath)
                    .build()
            )
            .setRunningMode(RunningMode.IMAGE)
            .setNumPoses(config.maximumPoses)
            .setMinPoseDetectionConfidence(config.minimumPoseDetectionConfidence)
            .setMinPosePresenceConfidence(config.minimumPosePresenceConfidence)
            .setMinTrackingConfidence(config.minimumTrackingConfidence)
            .setOutputSegmentationMasks(false)
            .build()
    )

    override suspend fun observe(input: MediaPipeImageInput): SubjectObservation =
        MediaPipePoseAliasCatalog.enrichSingleBody(
            MediaPipePoseObservationMapper.map(
                snapshot = landmarker.detect(input.image).toSnapshot()
            )
        )

    override fun close() {
        landmarker.close()
    }
}

private fun PoseLandmarkerResult.toSnapshot(): MediaPipePoseLandmarkerSnapshot {
    val worldLandmarksByPose = worldLandmarks()

    return MediaPipePoseLandmarkerSnapshot(
        poses = landmarks().mapIndexed { poseIndex, poseLandmarks ->
            val worldLandmarks = worldLandmarksByPose.getOrNull(poseIndex).orEmpty()
            MediaPipePoseSnapshot(
                landmarks = poseLandmarks.mapIndexed { pointIndex, point ->
                    val worldPoint = worldLandmarks.getOrNull(pointIndex)
                    MediaPipePoseLandmarkSnapshot(
                        index = pointIndex,
                        x = point.x(),
                        y = point.y(),
                        z = point.z(),
                        visibility = point.visibility().orElse(null),
                        presence = point.presence().orElse(null),
                        worldX = worldPoint?.x(),
                        worldY = worldPoint?.y(),
                        worldZ = worldPoint?.z()
                    )
                }
            )
        }
    )
}
