/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis_mlkit.pose

import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ConfidenceSource
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.LandmarkObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.NormalizedPoint3D
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ObservationBackend
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.VisibilityState

/** Platform-neutral snapshot of one ML Kit pose. */
data class MlKitPoseLandmarkSnapshot(
    val id: String,
    val xPixels: Float,
    val yPixels: Float,
    val zPixels: Float,
    val inFrameLikelihood: Float
) {
    init {
        require(id.isNotBlank()) { "id cannot be blank" }
        require(inFrameLikelihood in 0f..1f) {
            "inFrameLikelihood must be in 0f..1f"
        }
    }
}

data class MlKitPoseSnapshot(
    val imageWidth: Int,
    val imageHeight: Int,
    val landmarks: List<MlKitPoseLandmarkSnapshot>
) {
    init {
        require(imageWidth > 0) { "imageWidth must be positive" }
        require(imageHeight > 0) { "imageHeight must be positive" }
    }
}

data class MlKitPoseMappingPolicy(
    val visibleThreshold: Float = 0.50f,
    val partialVisibilityThreshold: Float = 0.10f
) {
    init {
        require(visibleThreshold in 0f..1f)
        require(partialVisibilityThreshold in 0f..visibleThreshold)
    }
}

object MlKitPoseObservationMapper {

    fun map(
        snapshot: MlKitPoseSnapshot,
        policy: MlKitPoseMappingPolicy = MlKitPoseMappingPolicy()
    ): SubjectObservation {
        val landmarks = snapshot.landmarks.associate { point ->
            val x = point.xPixels / snapshot.imageWidth
            val y = point.yPixels / snapshot.imageHeight
            point.id to LandmarkObservation(
                id = point.id,
                point = NormalizedPoint3D(
                    x = x,
                    y = y,
                    z = point.zPixels / snapshot.imageWidth
                ),
                confidence = point.inFrameLikelihood,
                confidenceSource = ConfidenceSource.DIRECT,
                visibility = visibilityFor(
                    x = x,
                    y = y,
                    likelihood = point.inFrameLikelihood,
                    policy = policy
                ),
                backend = ObservationBackend.ML_KIT_POSE
            )
        }

        val bodyDetected = landmarks.isNotEmpty()
        return SubjectObservation(
            subjectCount = if (bodyDetected) 1 else 0,
            faceCount = 0,
            bodyCount = if (bodyDetected) 1 else 0,
            landmarks = landmarks,
            regions = emptyMap()
        )
    }

    private fun visibilityFor(
        x: Float,
        y: Float,
        likelihood: Float,
        policy: MlKitPoseMappingPolicy
    ): VisibilityState = when {
        x !in 0f..1f || y !in 0f..1f -> VisibilityState.OUTSIDE_FRAME
        likelihood >= policy.visibleThreshold -> VisibilityState.VISIBLE
        likelihood >= policy.partialVisibilityThreshold -> VisibilityState.PARTIALLY_VISIBLE
        else -> VisibilityState.AMBIGUOUS
    }
}
