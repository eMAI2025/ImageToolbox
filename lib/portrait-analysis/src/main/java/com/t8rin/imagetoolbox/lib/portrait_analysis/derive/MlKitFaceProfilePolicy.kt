/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis.derive

import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ObservationBackend
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation
import kotlin.math.abs

/**
 * Fail-closed ML Kit pose policy for frontal, mild half-profile and strong-profile candidates.
 *
 * The yaw boundaries intentionally reuse the coarse pose contract already owned by
 * [FaceRegionAwarenessAnalyzer]: frontal through 15 degrees, mild half-profile above 15 and through
 * 45 degrees, and strong profile above 45 degrees. They are architectural boundaries, not values
 * calibrated from one photograph.
 *
 * This policy never mirrors, repairs or synthesizes geometry. It only reports rejection reasons.
 */
object MlKitFaceProfilePolicy {

    const val FINGERPRINT = "POSTAC_MASTER_MLKIT_FACE_PROFILE_POLICY_V1"

    const val FRONTAL_MAX_ABS_YAW_DEGREES = 15f
    const val HALF_PROFILE_MAX_ABS_YAW_DEGREES = 45f
    const val MAX_SUPPORTED_ABS_PITCH_DEGREES = 35f
    const val MAX_SUPPORTED_ABS_ROLL_DEGREES = 40f

    data class Assessment(
        val reasons: Set<FaceGeometryRejectionReason>,
        val yawDegrees: Float?,
        val absoluteYawDegrees: Float?,
        val poseMode: FacePoseMode,
        val dominantImageSide: FaceImageSide
    )

    fun evaluate(
        rawObservation: SubjectObservation,
        awareness: FaceRegionAwareness
    ): Assessment {
        if (!rawObservation.usesMlKitFaceDetection()) {
            return Assessment(
                reasons = emptySet(),
                yawDegrees = awareness.yawDegrees,
                absoluteYawDegrees = awareness.yawDegrees?.takeIf(Float::isFinite)?.let(::abs),
                poseMode = awareness.poseMode,
                dominantImageSide = awareness.dominantImageSide
            )
        }

        val reasons = linkedSetOf<FaceGeometryRejectionReason>()
        val yaw = awareness.yawDegrees
        val absoluteYaw = yaw?.takeIf(Float::isFinite)?.let(::abs)
        val pitchSupported = awareness.pitchDegrees
            ?.takeIf(Float::isFinite)
            ?.let { abs(it) <= MAX_SUPPORTED_ABS_PITCH_DEGREES }
            ?: false
        val rollSupported = awareness.rollDegrees
            ?.takeIf(Float::isFinite)
            ?.let { abs(it) <= MAX_SUPPORTED_ABS_ROLL_DEGREES }
            ?: false

        if (absoluteYaw == null) {
            reasons += FaceGeometryRejectionReason.MISSING_REQUIRED_POSE_OR_AXIS
        }
        if (!pitchSupported || !rollSupported) {
            reasons += FaceGeometryRejectionReason.UNSUPPORTED_PARTIAL_OR_STRONG_PROFILE
        }

        when (awareness.poseMode) {
            FacePoseMode.FRONTAL -> {
                if (absoluteYaw != null && absoluteYaw > FRONTAL_MAX_ABS_YAW_DEGREES) {
                    reasons += FaceGeometryRejectionReason.VISIBLE_SIDE_CONTRADICTION
                }
            }

            FacePoseMode.HALF_PROFILE -> {
                when {
                    absoluteYaw == null -> Unit
                    absoluteYaw <= FRONTAL_MAX_ABS_YAW_DEGREES ->
                        reasons += FaceGeometryRejectionReason.VISIBLE_SIDE_CONTRADICTION
                    absoluteYaw > HALF_PROFILE_MAX_ABS_YAW_DEGREES ->
                        reasons += FaceGeometryRejectionReason.UNSUPPORTED_PARTIAL_OR_STRONG_PROFILE
                }

                if (yaw != null && yaw.isFinite() && !yawMatchesDominantImageSide(
                        yawDegrees = yaw,
                        dominantImageSide = awareness.dominantImageSide
                    )
                ) {
                    reasons += FaceGeometryRejectionReason.VISIBLE_SIDE_CONTRADICTION
                }
            }

            FacePoseMode.PROFILE -> {
                // ML Kit Face Detection does not provide directly observed dense geometry for the
                // hidden side. A strong profile is therefore never active ML Kit geometry.
                reasons += FaceGeometryRejectionReason.UNSUPPORTED_PARTIAL_OR_STRONG_PROFILE

                if (yaw != null && yaw.isFinite() && !yawMatchesDominantImageSide(
                        yawDegrees = yaw,
                        dominantImageSide = awareness.dominantImageSide
                    )
                ) {
                    reasons += FaceGeometryRejectionReason.VISIBLE_SIDE_CONTRADICTION
                }
            }

            FacePoseMode.UNKNOWN ->
                reasons += FaceGeometryRejectionReason.MISSING_REQUIRED_POSE_OR_AXIS
        }

        return Assessment(
            reasons = reasons,
            yawDegrees = yaw,
            absoluteYawDegrees = absoluteYaw,
            poseMode = awareness.poseMode,
            dominantImageSide = awareness.dominantImageSide
        )
    }

    private fun yawMatchesDominantImageSide(
        yawDegrees: Float,
        dominantImageSide: FaceImageSide
    ): Boolean = when {
        yawDegrees > 0f -> dominantImageSide == FaceImageSide.LEFT
        yawDegrees < 0f -> dominantImageSide == FaceImageSide.RIGHT
        else -> dominantImageSide == FaceImageSide.BALANCED
    }

    private fun SubjectObservation.usesMlKitFaceDetection(): Boolean =
        landmarks.values.any { it.backend == ObservationBackend.ML_KIT_FACE_DETECTION } ||
            contours.values.any { it.backend == ObservationBackend.ML_KIT_FACE_DETECTION } ||
            meshes.values.any { it.backend == ObservationBackend.ML_KIT_FACE_DETECTION }
}
