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

data class RepeatabilityPolicy(
    val minimumRuns: Int = 3,
    val maximumNormalizedLandmarkDrift: Float = 0.002f
) {
    init {
        require(minimumRuns >= 2)
        require(maximumNormalizedLandmarkDrift >= 0f)
        require(maximumNormalizedLandmarkDrift.isFinite())
    }
}

data class BackendRepeatabilityEntry(
    val backend: ObservationBackend,
    val runCount: Int,
    val sufficientRuns: Boolean,
    val countsStable: Boolean,
    val landmarkPresenceStable: Boolean,
    val parameterAvailabilityStable: Boolean,
    val maximumNormalizedLandmarkDrift: Float?,
    val unstableLandmarkIds: Set<String>,
    val unstableParameterIds: Set<String>
) {
    val deterministic: Boolean
        get() = sufficientRuns &&
            countsStable &&
            landmarkPresenceStable &&
            parameterAvailabilityStable &&
            unstableLandmarkIds.isEmpty() &&
            unstableParameterIds.isEmpty()
}

data class PortraitRepeatabilityReport(
    val entries: List<BackendRepeatabilityEntry>
) {
    val allDeterministic: Boolean
        get() = entries.isNotEmpty() && entries.all(BackendRepeatabilityEntry::deterministic)
}

object PortraitRepeatabilityReportBuilder {

    fun build(
        backendResults: Collection<ObservationBenchmarkResult>,
        gateSpecs: Collection<ParameterGateSpec>,
        policy: RepeatabilityPolicy = RepeatabilityPolicy(),
        canonicalLandmarkIds: Set<String> = gateSpecs
            .flatMap(ParameterGateSpec::requiredLandmarkIds)
            .toSortedSet()
    ): PortraitRepeatabilityReport {
        val entries = backendResults
            .groupBy { it.measurement.backend }
            .toSortedMap(compareBy(ObservationBackend::name))
            .map { (backend, unsortedRuns) ->
                evaluateBackend(
                    backend = backend,
                    runs = unsortedRuns.sortedBy { it.measurement.repeatedRunIndex },
                    gateSpecs = gateSpecs,
                    canonicalLandmarkIds = canonicalLandmarkIds,
                    policy = policy
                )
            }

        return PortraitRepeatabilityReport(entries)
    }

    private fun evaluateBackend(
        backend: ObservationBackend,
        runs: List<ObservationBenchmarkResult>,
        gateSpecs: Collection<ParameterGateSpec>,
        canonicalLandmarkIds: Set<String>,
        policy: RepeatabilityPolicy
    ): BackendRepeatabilityEntry {
        val baseline = runs.first()
        val baselineObservation = baseline.observation
        val baselineGateReport = ParameterGateReportBuilder.build(gateSpecs, baselineObservation)

        var countsStable = true
        var landmarkPresenceStable = true
        var parameterAvailabilityStable = true
        var maximumDrift: Float? = null
        val unstableLandmarkIds = sortedSetOf<String>()
        val unstableParameterIds = sortedSetOf<String>()

        runs.drop(1).forEach { run ->
            val observation = run.observation
            if (
                observation.subjectCount != baselineObservation.subjectCount ||
                observation.faceCount != baselineObservation.faceCount ||
                observation.bodyCount != baselineObservation.bodyCount
            ) {
                countsStable = false
            }

            canonicalLandmarkIds.forEach { landmarkId ->
                val baselineLandmark = baselineObservation.landmarks[landmarkId]
                val currentLandmark = observation.landmarks[landmarkId]
                when {
                    baselineLandmark == null && currentLandmark == null -> Unit
                    baselineLandmark == null || currentLandmark == null -> {
                        landmarkPresenceStable = false
                        unstableLandmarkIds += landmarkId
                    }
                    else -> {
                        val drift = distance2d(baselineLandmark, currentLandmark)
                        maximumDrift = maxOf(maximumDrift ?: 0f, drift)
                        if (drift > policy.maximumNormalizedLandmarkDrift) {
                            unstableLandmarkIds += landmarkId
                        }
                    }
                }
            }

            val currentGateReport = ParameterGateReportBuilder.build(gateSpecs, observation)
            gateSpecs.map(ParameterGateSpec::parameterId).distinct().forEach { parameterId ->
                val baselineAvailability = baselineGateReport.entry(parameterId)?.availability
                val currentAvailability = currentGateReport.entry(parameterId)?.availability
                if (baselineAvailability != currentAvailability) {
                    parameterAvailabilityStable = false
                    unstableParameterIds += parameterId
                }
            }
        }

        return BackendRepeatabilityEntry(
            backend = backend,
            runCount = runs.size,
            sufficientRuns = runs.size >= policy.minimumRuns,
            countsStable = countsStable,
            landmarkPresenceStable = landmarkPresenceStable,
            parameterAvailabilityStable = parameterAvailabilityStable,
            maximumNormalizedLandmarkDrift = maximumDrift,
            unstableLandmarkIds = unstableLandmarkIds,
            unstableParameterIds = unstableParameterIds
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

object PortraitRepeatabilityRenderer {

    fun render(report: PortraitRepeatabilityReport): String = buildString {
        appendLine("all_deterministic=${report.allDeterministic}")
        report.entries.forEach { entry ->
            appendLine("backend=${entry.backend.name}")
            appendLine("runs=${entry.runCount}")
            appendLine("sufficient_runs=${entry.sufficientRuns}")
            appendLine("counts_stable=${entry.countsStable}")
            appendLine("landmark_presence_stable=${entry.landmarkPresenceStable}")
            appendLine("parameter_availability_stable=${entry.parameterAvailabilityStable}")
            appendLine("maximum_landmark_drift=${entry.maximumNormalizedLandmarkDrift ?: "NA"}")
            appendLine("unstable_landmarks=${entry.unstableLandmarkIds.joinToString(",")}")
            appendLine("unstable_parameters=${entry.unstableParameterIds.joinToString(",")}")
        }
    }
}
