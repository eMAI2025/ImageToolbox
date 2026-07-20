/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis.model

/**
 * Common coordinate space used by all detector adapters.
 *
 * Coordinates are normalized to the source image in the 0f..1f range.
 * Z is optional because not every backend exposes a meaningful depth value.
 */
data class NormalizedPoint3D(
    val x: Float,
    val y: Float,
    val z: Float? = null
) {
    init {
        require(x.isFinite()) { "x must be finite" }
        require(y.isFinite()) { "y must be finite" }
        require(z == null || z.isFinite()) { "z must be finite when present" }
    }
}

enum class VisibilityState {
    VISIBLE,
    PARTIALLY_VISIBLE,
    OCCLUDED,
    OUTSIDE_FRAME,
    AMBIGUOUS
}

enum class ConfidenceSource {
    DIRECT,
    DERIVED,
    UNAVAILABLE
}

enum class ObservationBackend {
    MEDIAPIPE_FACE_LANDMARKER,
    ML_KIT_FACE_MESH,
    MEDIAPIPE_POSE,
    ML_KIT_POSE,
    ML_KIT_SELFIE_SEGMENTATION,
    DENSEPOSE_DESKTOP,
    UNKNOWN
}

data class LandmarkObservation(
    val id: String,
    val point: NormalizedPoint3D,
    val confidence: Float?,
    val confidenceSource: ConfidenceSource,
    val visibility: VisibilityState,
    val backend: ObservationBackend
) {
    init {
        require(id.isNotBlank()) { "Landmark id cannot be blank" }
        require(confidence == null || confidence in 0f..1f) {
            "confidence must be null or in 0f..1f"
        }
        require(
            (confidence == null && confidenceSource == ConfidenceSource.UNAVAILABLE) ||
                (confidence != null && confidenceSource != ConfidenceSource.UNAVAILABLE)
        ) {
            "confidence and confidenceSource must describe the same evidence state"
        }
    }
}

data class RegionObservation(
    val id: String,
    val confidence: Float?,
    val confidenceSource: ConfidenceSource,
    val occlusion: Float?,
    val pixelCoverage: Float,
    val backend: ObservationBackend
) {
    init {
        require(id.isNotBlank()) { "Region id cannot be blank" }
        require(confidence == null || confidence in 0f..1f) {
            "confidence must be null or in 0f..1f"
        }
        require(
            (confidence == null && confidenceSource == ConfidenceSource.UNAVAILABLE) ||
                (confidence != null && confidenceSource != ConfidenceSource.UNAVAILABLE)
        ) {
            "confidence and confidenceSource must describe the same evidence state"
        }
        require(occlusion == null || occlusion in 0f..1f) {
            "occlusion must be null or in 0f..1f"
        }
        require(pixelCoverage in 0f..1f) { "pixelCoverage must be in 0f..1f" }
    }
}

data class PoseObservation(
    val yawDegrees: Float? = null,
    val pitchDegrees: Float? = null,
    val rollDegrees: Float? = null
)

data class SubjectObservation(
    val subjectCount: Int,
    val faceCount: Int,
    val bodyCount: Int,
    val landmarks: Map<String, LandmarkObservation>,
    val regions: Map<String, RegionObservation>,
    val pose: PoseObservation = PoseObservation()
) {
    init {
        require(subjectCount >= 0) { "subjectCount cannot be negative" }
        require(faceCount >= 0) { "faceCount cannot be negative" }
        require(bodyCount >= 0) { "bodyCount cannot be negative" }
    }
}
