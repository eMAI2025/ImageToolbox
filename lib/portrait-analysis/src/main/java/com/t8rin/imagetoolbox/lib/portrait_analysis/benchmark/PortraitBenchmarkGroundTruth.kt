/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis.benchmark

import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.report.ParameterAvailability
import com.t8rin.imagetoolbox.lib.portrait_analysis.report.ParameterGateReport

/** Manual facts describing one benchmark image without prescribing a detector implementation. */
enum class PortraitBenchmarkAnnotation {
    STRAIGHT_BACKGROUND_LINES,
    PARTIAL_FACE_OCCLUSION,
    LOW_CONTRAST,
    VISIBLE_TEETH,
    LOOSE_CLOTHING,
    ARMS_CROSSING_TORSO
}

/**
 * Expected observable state for one A1 benchmark image.
 *
 * Ground truth is intentionally expressed through canonical landmark, region and parameter ids.
 * It must not contain backend-specific point numbers or assumptions about hidden anatomy.
 */
data class PortraitBenchmarkGroundTruth(
    val caseId: String,
    val expectedSubjectCount: IntRange = 1..1,
    val expectedFaceCount: IntRange = 0..Int.MAX_VALUE,
    val expectedBodyCount: IntRange = 0..Int.MAX_VALUE,
    val requiredLandmarkIds: Set<String> = emptySet(),
    val forbiddenLandmarkIds: Set<String> = emptySet(),
    val requiredRegionIds: Set<String> = emptySet(),
    val forbiddenRegionIds: Set<String> = emptySet(),
    val expectedEnabledParameterIds: Set<String> = emptySet(),
    val expectedDisabledParameterIds: Set<String> = emptySet(),
    val annotations: Set<PortraitBenchmarkAnnotation> = emptySet()
) {
    init {
        require(caseId.isNotBlank()) { "caseId cannot be blank" }
        require(!expectedSubjectCount.isEmpty() && expectedSubjectCount.first >= 0) {
            "expectedSubjectCount must be a non-negative, non-empty range"
        }
        require(!expectedFaceCount.isEmpty() && expectedFaceCount.first >= 0) {
            "expectedFaceCount must be a non-negative, non-empty range"
        }
        require(!expectedBodyCount.isEmpty() && expectedBodyCount.first >= 0) {
            "expectedBodyCount must be a non-negative, non-empty range"
        }
        require(requiredLandmarkIds.none { it.isBlank() }) {
            "requiredLandmarkIds cannot contain blank ids"
        }
        require(forbiddenLandmarkIds.none { it.isBlank() }) {
            "forbiddenLandmarkIds cannot contain blank ids"
        }
        require(requiredRegionIds.none { it.isBlank() }) {
            "requiredRegionIds cannot contain blank ids"
        }
        require(forbiddenRegionIds.none { it.isBlank() }) {
            "forbiddenRegionIds cannot contain blank ids"
        }
        require(expectedEnabledParameterIds.none { it.isBlank() }) {
            "expectedEnabledParameterIds cannot contain blank ids"
        }
        require(expectedDisabledParameterIds.none { it.isBlank() }) {
            "expectedDisabledParameterIds cannot contain blank ids"
        }
        require(requiredLandmarkIds.intersect(forbiddenLandmarkIds).isEmpty()) {
            "A landmark cannot be both required and forbidden"
        }
        require(requiredRegionIds.intersect(forbiddenRegionIds).isEmpty()) {
            "A region cannot be both required and forbidden"
        }
        require(expectedEnabledParameterIds.intersect(expectedDisabledParameterIds).isEmpty()) {
            "A parameter cannot be expected as both enabled and disabled"
        }
    }
}

enum class PortraitGroundTruthMismatchReason {
    SUBJECT_COUNT_OUT_OF_RANGE,
    FACE_COUNT_OUT_OF_RANGE,
    BODY_COUNT_OUT_OF_RANGE,
    REQUIRED_LANDMARK_MISSING,
    FORBIDDEN_LANDMARK_PRESENT,
    REQUIRED_REGION_MISSING,
    FORBIDDEN_REGION_PRESENT,
    PARAMETER_REPORT_ENTRY_MISSING,
    PARAMETER_EXPECTED_ENABLED,
    PARAMETER_EXPECTED_DISABLED
}

data class PortraitGroundTruthMismatch(
    val reason: PortraitGroundTruthMismatchReason,
    val itemId: String? = null,
    val observedValue: Int? = null,
    val expectedRange: IntRange? = null
)

data class PortraitGroundTruthEvaluation(
    val caseId: String,
    val mismatches: List<PortraitGroundTruthMismatch>
) {
    val passed: Boolean get() = mismatches.isEmpty()
}

/** Deterministically compares one merged observation and gate report with manual ground truth. */
object PortraitBenchmarkGroundTruthEvaluator {

    fun evaluate(
        groundTruth: PortraitBenchmarkGroundTruth,
        observation: SubjectObservation,
        parameterReport: ParameterGateReport
    ): PortraitGroundTruthEvaluation {
        val mismatches = buildList {
            addCountMismatchIfNeeded(
                observed = observation.subjectCount,
                expected = groundTruth.expectedSubjectCount,
                reason = PortraitGroundTruthMismatchReason.SUBJECT_COUNT_OUT_OF_RANGE
            )
            addCountMismatchIfNeeded(
                observed = observation.faceCount,
                expected = groundTruth.expectedFaceCount,
                reason = PortraitGroundTruthMismatchReason.FACE_COUNT_OUT_OF_RANGE
            )
            addCountMismatchIfNeeded(
                observed = observation.bodyCount,
                expected = groundTruth.expectedBodyCount,
                reason = PortraitGroundTruthMismatchReason.BODY_COUNT_OUT_OF_RANGE
            )

            groundTruth.requiredLandmarkIds
                .filterNot(observation.landmarks::containsKey)
                .forEach { id ->
                    add(
                        PortraitGroundTruthMismatch(
                            reason = PortraitGroundTruthMismatchReason.REQUIRED_LANDMARK_MISSING,
                            itemId = id
                        )
                    )
                }

            groundTruth.forbiddenLandmarkIds
                .filter(observation.landmarks::containsKey)
                .forEach { id ->
                    add(
                        PortraitGroundTruthMismatch(
                            reason = PortraitGroundTruthMismatchReason.FORBIDDEN_LANDMARK_PRESENT,
                            itemId = id
                        )
                    )
                }

            groundTruth.requiredRegionIds
                .filterNot(observation.regions::containsKey)
                .forEach { id ->
                    add(
                        PortraitGroundTruthMismatch(
                            reason = PortraitGroundTruthMismatchReason.REQUIRED_REGION_MISSING,
                            itemId = id
                        )
                    )
                }

            groundTruth.forbiddenRegionIds
                .filter(observation.regions::containsKey)
                .forEach { id ->
                    add(
                        PortraitGroundTruthMismatch(
                            reason = PortraitGroundTruthMismatchReason.FORBIDDEN_REGION_PRESENT,
                            itemId = id
                        )
                    )
                }

            groundTruth.expectedEnabledParameterIds.forEach { parameterId ->
                when (parameterReport.entry(parameterId)?.availability) {
                    null -> add(
                        PortraitGroundTruthMismatch(
                            reason = PortraitGroundTruthMismatchReason.PARAMETER_REPORT_ENTRY_MISSING,
                            itemId = parameterId
                        )
                    )

                    ParameterAvailability.DISABLED -> add(
                        PortraitGroundTruthMismatch(
                            reason = PortraitGroundTruthMismatchReason.PARAMETER_EXPECTED_ENABLED,
                            itemId = parameterId
                        )
                    )

                    ParameterAvailability.ENABLED -> Unit
                }
            }

            groundTruth.expectedDisabledParameterIds.forEach { parameterId ->
                when (parameterReport.entry(parameterId)?.availability) {
                    null -> add(
                        PortraitGroundTruthMismatch(
                            reason = PortraitGroundTruthMismatchReason.PARAMETER_REPORT_ENTRY_MISSING,
                            itemId = parameterId
                        )
                    )

                    ParameterAvailability.ENABLED -> add(
                        PortraitGroundTruthMismatch(
                            reason = PortraitGroundTruthMismatchReason.PARAMETER_EXPECTED_DISABLED,
                            itemId = parameterId
                        )
                    )

                    ParameterAvailability.DISABLED -> Unit
                }
            }
        }.sortedWith(
            compareBy<PortraitGroundTruthMismatch>(
                { it.reason.name },
                { it.itemId.orEmpty() },
                { it.observedValue ?: Int.MIN_VALUE }
            )
        )

        return PortraitGroundTruthEvaluation(
            caseId = groundTruth.caseId,
            mismatches = mismatches
        )
    }

    private fun MutableList<PortraitGroundTruthMismatch>.addCountMismatchIfNeeded(
        observed: Int,
        expected: IntRange,
        reason: PortraitGroundTruthMismatchReason
    ) {
        if (observed !in expected) {
            add(
                PortraitGroundTruthMismatch(
                    reason = reason,
                    observedValue = observed,
                    expectedRange = expected
                )
            )
        }
    }
}
