/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis.alias

import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ConfidenceSource
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.LandmarkObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.NormalizedPoint3D
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ObservationBackend
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.VisibilityState

/** How a canonical landmark is derived from backend-specific landmarks. */
enum class LandmarkAliasStrategy {
    DIRECT,
    CENTROID
}

data class LandmarkAliasRule(
    val aliasId: String,
    val sourceLandmarkIds: List<String>,
    val strategy: LandmarkAliasStrategy
) {
    init {
        require(aliasId.isNotBlank())
        require(sourceLandmarkIds.isNotEmpty())
        require(sourceLandmarkIds.none(String::isBlank))
        require(sourceLandmarkIds.distinct().size == sourceLandmarkIds.size)
        require(strategy != LandmarkAliasStrategy.DIRECT || sourceLandmarkIds.size == 1) {
            "DIRECT alias must reference exactly one source landmark"
        }
    }
}

/** Adds canonical aliases without deleting or renaming the original detector output. */
object LandmarkAliasEnricher {

    fun enrich(
        observation: SubjectObservation,
        rules: Collection<LandmarkAliasRule>
    ): SubjectObservation {
        require(rules.map { it.aliasId }.distinct().size == rules.size) {
            "Alias ids must be unique"
        }

        val aliases = rules.mapNotNull { rule ->
            if (rule.aliasId in observation.landmarks) return@mapNotNull null

            val sources = rule.sourceLandmarkIds.map { sourceId ->
                observation.landmarks[sourceId] ?: return@mapNotNull null
            }
            rule.aliasId to derive(rule, sources)
        }.toMap()

        return observation.copy(landmarks = observation.landmarks + aliases)
    }

    private fun derive(
        rule: LandmarkAliasRule,
        sources: List<LandmarkObservation>
    ): LandmarkObservation {
        val point = when (rule.strategy) {
            LandmarkAliasStrategy.DIRECT -> sources.single().point
            LandmarkAliasStrategy.CENTROID -> centroid(sources.map { it.point })
        }
        val confidenceValues = sources.map { it.confidence }
        val confidence = confidenceValues
            .takeIf { values -> values.all { it != null } }
            ?.filterNotNull()
            ?.minOrNull()
        val backend = sources.map { it.backend }.distinct().singleOrNull()
            ?: ObservationBackend.UNKNOWN

        return LandmarkObservation(
            id = rule.aliasId,
            point = point,
            confidence = confidence,
            confidenceSource = if (confidence == null) {
                ConfidenceSource.UNAVAILABLE
            } else {
                ConfidenceSource.DERIVED
            },
            visibility = sources.map { it.visibility }.worstVisibility(),
            backend = backend
        )
    }

    private fun centroid(points: List<NormalizedPoint3D>): NormalizedPoint3D {
        val zValues = points.map { it.z }
        val z = zValues
            .takeIf { values -> values.all { it != null } }
            ?.filterNotNull()
            ?.average()
            ?.toFloat()

        return NormalizedPoint3D(
            x = points.map { it.x }.average().toFloat(),
            y = points.map { it.y }.average().toFloat(),
            z = z
        )
    }

    private fun List<VisibilityState>.worstVisibility(): VisibilityState =
        maxByOrNull(::visibilitySeverity) ?: VisibilityState.AMBIGUOUS

    private fun visibilitySeverity(state: VisibilityState): Int = when (state) {
        VisibilityState.VISIBLE -> 0
        VisibilityState.PARTIALLY_VISIBLE -> 1
        VisibilityState.AMBIGUOUS -> 2
        VisibilityState.OCCLUDED -> 3
        VisibilityState.OUTSIDE_FRAME -> 4
    }
}
