/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis_mediapipe.face

import android.content.Context
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import com.t8rin.imagetoolbox.lib.portrait_analysis.engine.PortraitObservationEngine
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ObservationBackend
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis_mediapipe.MediaPipeImageInput

/** Explicit IMAGE-mode configuration for the MediaPipe Face Landmarker adapter. */
data class MediaPipeFaceLandmarkerConfig(
    val modelAssetPath: String,
    val maximumFaces: Int = 5,
    val minimumFaceDetectionConfidence: Float = 0.5f,
    val minimumFacePresenceConfidence: Float = 0.5f,
    val minimumTrackingConfidence: Float = 0.5f,
    val outputBlendshapes: Boolean = true,
    val outputTransformationMatrices: Boolean = true
) {
    init {
        require(modelAssetPath.isNotBlank())
        require(maximumFaces > 0)
        require(minimumFaceDetectionConfidence in 0f..1f)
        require(minimumFacePresenceConfidence in 0f..1f)
        require(minimumTrackingConfidence in 0f..1f)
    }
}

/** Still-image MediaPipe Face Landmarker adapter. */
class MediaPipeFaceLandmarkerObservationEngine(
    context: Context,
    config: MediaPipeFaceLandmarkerConfig
) : PortraitObservationEngine<MediaPipeImageInput>, AutoCloseable {

    override val backend: ObservationBackend = ObservationBackend.MEDIAPIPE_FACE_LANDMARKER

    private val landmarker: FaceLandmarker = FaceLandmarker.createFromOptions(
        context,
        FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(
                BaseOptions.builder()
                    .setModelAssetPath(config.modelAssetPath)
                    .build()
            )
            .setRunningMode(RunningMode.IMAGE)
            .setNumFaces(config.maximumFaces)
            .setMinFaceDetectionConfidence(config.minimumFaceDetectionConfidence)
            .setMinFacePresenceConfidence(config.minimumFacePresenceConfidence)
            .setMinTrackingConfidence(config.minimumTrackingConfidence)
            .setOutputFaceBlendshapes(config.outputBlendshapes)
            .setOutputFacialTransformationMatrixes(config.outputTransformationMatrices)
            .build()
    )

    override suspend fun observe(input: MediaPipeImageInput): SubjectObservation {
        val rawObservation = MediaPipeFaceObservationMapper.map(
            snapshot = landmarker.detect(input.image).toSnapshot()
        )
        return MediaPipeFaceAliasCatalog.enrichSingleFace(rawObservation)
    }

    override fun close() {
        landmarker.close()
    }
}

private fun FaceLandmarkerResult.toSnapshot(): MediaPipeFaceLandmarkerSnapshot {
    val blendshapesByFace = faceBlendshapes().orElse(emptyList())

    return MediaPipeFaceLandmarkerSnapshot(
        faces = faceLandmarks().mapIndexed { faceIndex, faceLandmarks ->
            val pointCount = faceLandmarks.size
            MediaPipeFaceSnapshot(
                landmarks = faceLandmarks.mapIndexed { pointIndex, point ->
                    MediaPipeFaceLandmarkSnapshot(
                        index = pointIndex,
                        x = point.x(),
                        y = point.y(),
                        z = point.z(),
                        visibility = point.visibility().orElse(null),
                        presence = point.presence().orElse(null)
                    )
                },
                blendshapes = blendshapesByFace.getOrNull(faceIndex)
                    .orEmpty()
                    .map { category ->
                        MediaPipeBlendshapeSnapshot(
                            name = category.categoryName(),
                            score = category.score()
                        )
                    },
                contours = MediaPipeFaceTopology.contours(pointCount),
                triangles = MediaPipeFaceTopology.triangles(pointCount)
            )
        }
    )
}
