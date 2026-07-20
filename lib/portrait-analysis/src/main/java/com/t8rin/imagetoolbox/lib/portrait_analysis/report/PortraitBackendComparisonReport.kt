/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis.report

import com.t8rin.imagetoolbox.lib.portrait_analysis.engine.ObservationBenchmarkResult
import com.t8rin.imagetoolbox.lib.portrait_analysis.gate.ParameterGateSpec
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.LandmarkObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ObservationBackend
import kotlin.math.sqrt

data class CanonicalLandmarkDelta(
    val landmarkId: String,
    val normalizedDistance2d: Float
) {
    init {
        require(landmarkId.isNotBlank())
        require(normalizedDistance2d >= 0f && normalizedDistance2d.isFinite())
    }
}

data class ParameterAvailabilityDisagreement(
    val parameterId: String,
    val first: ParameterAvailability,
    val second: ParameterAvailability
)

data class BackendPairComparison(
    val firstBackend: ObservationBackend,
    val secondBackend: ObservationBackend,
    val subjectCountDelta: Int,
    val faceCountDelta: Int,
    val bodyCountDelta: Int,
    val landmarkDeltas: List<CanonicalLandmarkDelta>,
    val missingInFirst: Set<String>,
    val missingInSecond: Set<String>,
    val parameterDisagreements: List<ParameterAvailabilityDisagreement>
) {
    val meanLandmarkDistance2d: Float?
        get() = landmarkDeltas.takeIf(List<CanonicalLandmarkDelta>::isNotEmpty)
            ?.map(CanonicalLandmarkDelta::normalizedDistance2d)
            ?.average()
            ?.toFloat()

    val maximumLandmarkDistance2d: Float?
        get() = landmarkDeltas.maxOfOrNull(CanonicalLandmarkDelta::normalizedDistance2d)
}

data class PortraitBackendComparisonReport(
    val comparisons: List<BackendPairComparison>
)

object PortraitBackendComparisonReportBuilder {

    fun build(
        backendResults: Collection<ObservationBenchmarkResult>,
        gateSpecs: Collection<ParameterGateSpec>,
        canonicalLandmarkIds: Set<String> = gateSpecs
            .flatMap(ParameterGateSpec::requiredLandmarkIds)
            .toSortedSet()
    ): PortraitBackendComparisonReport {
        val selected = backendResults
            .groupBy { it.measurement.backend }
            .mapValues { (_, results) ->
                results.minBy { it.measurement.repeatedRunIndex }
            }
            .toSortedMap(compareBy(ObservationBackend::name))
            .values
            .toList()

        val comparisons = buildList {
            selected.indices.forEach { firstIndex ->
                ((firstIndex + 1) until selected.size).forEach { secondIndex ->
                    add(
                        compare(
                            first = selected[firstIndex],
                            second = selected[secondIndex],
                            gateSpecs = gateSpecs,
                            canonicalLandmarkIds = canonicalLandmarkIds
                        )
                    )
                }
            }
        }

        return PortraitBackendComparisonReport(comparisons)
    }

    private fun compare(
        first: ObservationBenchmarkResult,
        second: ObservationBenchmarkResult,
        gateSpecs: Collection<ParameterGateSpec>,
        canonicalLandmarkIds: Set<String>
    ): BackendPairComparison {
        val firstLandmarks = first.observation.landmarks
        val secondLandmarks = second.observation.landmarks
        val commonIds = canonicalLandmarkIds
            .filter { it in firstLandmarks && it in secondLandmarks }
            .sorted()

        val firstReport = ParameterGateReportBuilder.build(gateSpecs, first.observation)
        val secondReport = ParameterGateReportBuilder.build(gateSpecs, second.observation)

        return BackendPairComparison(
            firstBackend = first.measurement.backend,
            secondBackend = second.measurement.backend,
            subjectCountDelta = second.observation.subjectCount - first.observation.subjectCount,
            faceCountDelta = second.observation.faceCount - first.observation.faceCount,
            bodyCountDelta = second.observation.bodyCount - first.observation.bodyCount,
            landmarkDeltas = commonIds.map { landmarkId ->
                CanonicalLandmarkDelta(
                    landmarkId = landmarkId,
                    normalizedDistance2d = distance2d(
                        firstLandmarks.getValue(landmarkId),
                        secondLandmarks.getValue(landmarkId)
                    )
                )
            },
            missingInFirst = canonicalLandmarkIds
                .filterNot(firstLandmarks::containsKey)
                .toSortedSet(),
            missingInSecond = canonicalLandmarkIds
                .filterNot(secondLandmarks::containsKey)
                .toSortedSet(),
            parameterDisagreements = gateSpecs
                .map(ParameterGateSpec::parameterId)
                .distinct()
                .sorted()
                .mapNotNull { parameterId ->
                    val firstAvailability = firstReport.entry(parameterId)?.availability
                    val secondAvailability = secondReport.entry(parameterId)?.availability
                    if (
                        firstAvailability == null ||
                        secondAvailability == null ||
                        firstAvailability == secondAvailability
                    ) {
                        null
                    } else {
                        ParameterAvailabilityDisagreement(
                            parameterId = parameterId,
                            first = firstAvailability,
                            second = secondAvailability
                        )
                    }
                }
        )
    }

    private fun distance2d(
        first: LandmarkObservation,
        second: LandmarkObservation
    ): Float {
        val dx = second.point.x - first.point.x
        val dy = second.point.y - first.point.y
        return sqrt(dx * dx + dy * dy)
    }
}

object PortraitBackendComparisonRenderer {

    fun render(report: PortraitBackendComparisonReport): String = buildString {
        appendLine("comparisons=${report.comparisons.size}")
        report.comparisons.forEach { comparison ->
            appendLine("${comparison.firstBackend.name}->${comparison.secondBackend.name}")
            appendLine("subject_count_delta=${comparison.subjectCountDelta}")
            appendLine("face_count_delta=${comparison.faceCountDelta}")
            appendLine("body_count_delta=${comparison.bodyCountDelta}")
            appendLine("mean_landmark_distance=${comparison.meanLandmarkDistance2d ?: "NA"}")
            appendLine("max_landmark_distance=${comparison.maximumLandmarkDistance2d ?: "NA"}")
            appendLine("missing_in_first=${comparison.missingInFirst.joinToString(",")}")
            appendLine("missing_in_second=${comparison.missingInSecond.joinToString(",")}")
            appendLine(
                "parameter_disagreements=" + comparison.parameterDisagreements.joinToString(",") {
                    "${it.parameterId}:${it.first.name}/${it.second.name}"
                }
            )
        }
    }
}
