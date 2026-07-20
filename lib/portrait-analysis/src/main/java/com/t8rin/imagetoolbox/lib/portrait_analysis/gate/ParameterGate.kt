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

package com.t8rin.imagetoolbox.lib.portrait_analysis.gate

import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.VisibilityState

/**
 * Declarative requirements for enabling one edit parameter.
 *
 * A parameter is never enabled by a general "face detected" or "body detected" flag.
 * Every required landmark and semantic region must pass independently.
 */
data class ParameterGateSpec(
    val parameterId: String,
    val requiredLandmarkIds: Set<String> = emptySet(),
    val requiredRegionIds: Set<String> = emptySet(),
    val minimumLandmarkConfidence: Float = 0.90f,
    val minimumRegionConfidence: Float = 0.90f,
    val maximumRegionOcclusion: Float = 0.15f,
    val minimumRegionPixelCoverage: Float = 0f,
    val minimumSubjects: Int = 1,
    val maximumSubjects: Int = 1,
    val maximumAbsoluteYawDegrees: Float? = null,
    val maximumAbsolutePitchDegrees: Float? = null,
    val maximumAbsoluteRollDegrees: Float? = null
) {
    init {
        require(parameterId.isNotBlank()) { "parameterId cannot be blank" }
        require(minimumLandmarkConfidence in 0f..1f)
        require(minimumRegionConfidence in 0f..1f)
        require(maximumRegionOcclusion in 0f..1f)
        require(minimumRegionPixelCoverage in 0f..1f)
        require(minimumSubjects >= 0)
        require(maximumSubjects >= minimumSubjects)
    }
}

enum class GateFailureReason {
    SUBJECT_COUNT_UNSUPPORTED,
    LANDMARK_MISSING,
    LANDMARK_LOW_CONFIDENCE,
    LANDMARK_NOT_VISIBLE,
    REGION_MISSING,
    REGION_LOW_CONFIDENCE,
    REGION_OCCLUDED,
    REGION_TOO_SMALL,
    POSE_UNSUPPORTED
}

data class GateFailure(
    val reason: GateFailureReason,
    val itemId: String? = null,
    val observedValue: Float? = null,
    val requiredValue: Float? = null
)

sealed interface ParameterGateDecision {
    val parameterId: String

    data class Enabled(
        override val parameterId: String
    ) : ParameterGateDecision

    data class Disabled(
        override val parameterId: String,
        val failures: List<GateFailure>
    ) : ParameterGateDecision
}

object ParameterGateEvaluator {

    fun evaluate(
        spec: ParameterGateSpec,
        observation: SubjectObservation
    ): ParameterGateDecision {
        val failures = buildList {
            if (observation.subjectCount !in spec.minimumSubjects..spec.maximumSubjects) {
                add(
                    GateFailure(
                        reason = GateFailureReason.SUBJECT_COUNT_UNSUPPORTED,
                        observedValue = observation.subjectCount.toFloat()
                    )
                )
            }

            spec.requiredLandmarkIds.forEach { landmarkId ->
                val landmark = observation.landmarks[landmarkId]
                when {
                    landmark == null -> add(
                        GateFailure(
                            reason = GateFailureReason.LANDMARK_MISSING,
                            itemId = landmarkId
                        )
                    )

                    landmark.confidence < spec.minimumLandmarkConfidence -> add(
                        GateFailure(
                            reason = GateFailureReason.LANDMARK_LOW_CONFIDENCE,
                            itemId = landmarkId,
                            observedValue = landmark.confidence,
                            requiredValue = spec.minimumLandmarkConfidence
                        )
                    )

                    landmark.visibility != VisibilityState.VISIBLE -> add(
                        GateFailure(
                            reason = GateFailureReason.LANDMARK_NOT_VISIBLE,
                            itemId = landmarkId
                        )
                    )
                }
            }

            spec.requiredRegionIds.forEach { regionId ->
                val region = observation.regions[regionId]
                when {
                    region == null -> add(
                        GateFailure(
                            reason = GateFailureReason.REGION_MISSING,
                            itemId = regionId
                        )
                    )

                    region.confidence < spec.minimumRegionConfidence -> add(
                        GateFailure(
                            reason = GateFailureReason.REGION_LOW_CONFIDENCE,
                            itemId = regionId,
                            observedValue = region.confidence,
                            requiredValue = spec.minimumRegionConfidence
                        )
                    )

                    region.occlusion > spec.maximumRegionOcclusion -> add(
                        GateFailure(
                            reason = GateFailureReason.REGION_OCCLUDED,
                            itemId = regionId,
                            observedValue = region.occlusion,
                            requiredValue = spec.maximumRegionOcclusion
                        )
                    )

                    region.pixelCoverage < spec.minimumRegionPixelCoverage -> add(
                        GateFailure(
                            reason = GateFailureReason.REGION_TOO_SMALL,
                            itemId = regionId,
                            observedValue = region.pixelCoverage,
                            requiredValue = spec.minimumRegionPixelCoverage
                        )
                    )
                }
            }

            validatePose(
                value = observation.pose.yawDegrees,
                limit = spec.maximumAbsoluteYawDegrees,
                axis = "yaw"
            )?.let(::add)
            validatePose(
                value = observation.pose.pitchDegrees,
                limit = spec.maximumAbsolutePitchDegrees,
                axis = "pitch"
            )?.let(::add)
            validatePose(
                value = observation.pose.rollDegrees,
                limit = spec.maximumAbsoluteRollDegrees,
                axis = "roll"
            )?.let(::add)
        }

        return if (failures.isEmpty()) {
            ParameterGateDecision.Enabled(spec.parameterId)
        } else {
            ParameterGateDecision.Disabled(
                parameterId = spec.parameterId,
                failures = failures
            )
        }
    }

    private fun validatePose(
        value: Float?,
        limit: Float?,
        axis: String
    ): GateFailure? {
        if (limit == null) return null
        if (value == null || kotlin.math.abs(value) > limit) {
            return GateFailure(
                reason = GateFailureReason.POSE_UNSUPPORTED,
                itemId = axis,
                observedValue = value,
                requiredValue = limit
            )
        }
        return null
    }
}
