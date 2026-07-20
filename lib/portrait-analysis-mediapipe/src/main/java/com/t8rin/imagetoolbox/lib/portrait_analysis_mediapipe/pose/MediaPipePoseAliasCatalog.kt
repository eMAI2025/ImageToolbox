/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis_mediapipe.pose

import com.t8rin.imagetoolbox.lib.portrait_analysis.alias.LandmarkAliasEnricher
import com.t8rin.imagetoolbox.lib.portrait_analysis.alias.LandmarkAliasRule
import com.t8rin.imagetoolbox.lib.portrait_analysis.alias.LandmarkAliasStrategy
import com.t8rin.imagetoolbox.lib.portrait_analysis.catalog.PortraitLandmarkId
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation

/**
 * Canonical aliases for the documented 33-point MediaPipe Pose topology.
 *
 * Aliases are created only when exactly one body is present. Raw backend landmarks are retained,
 * so benchmark reports can compare both original and canonical coordinates.
 */
object MediaPipePoseAliasCatalog {

    private val canonicalIndices: Map<String, Int> = linkedMapOf(
        "nose" to 0,
        "left_eye_inner" to 1,
        "left_eye" to 2,
        "left_eye_outer" to 3,
        "right_eye_inner" to 4,
        "right_eye" to 5,
        "right_eye_outer" to 6,
        "left_ear" to 7,
        "right_ear" to 8,
        "left_mouth" to 9,
        "right_mouth" to 10,
        PortraitLandmarkId.LEFT_SHOULDER to 11,
        PortraitLandmarkId.RIGHT_SHOULDER to 12,
        PortraitLandmarkId.LEFT_ELBOW to 13,
        PortraitLandmarkId.RIGHT_ELBOW to 14,
        PortraitLandmarkId.LEFT_WRIST to 15,
        PortraitLandmarkId.RIGHT_WRIST to 16,
        "left_pinky" to 17,
        "right_pinky" to 18,
        "left_index" to 19,
        "right_index" to 20,
        "left_thumb" to 21,
        "right_thumb" to 22,
        PortraitLandmarkId.LEFT_HIP to 23,
        PortraitLandmarkId.RIGHT_HIP to 24,
        PortraitLandmarkId.LEFT_KNEE to 25,
        PortraitLandmarkId.RIGHT_KNEE to 26,
        PortraitLandmarkId.LEFT_ANKLE to 27,
        PortraitLandmarkId.RIGHT_ANKLE to 28,
        "left_heel" to 29,
        "right_heel" to 30,
        "left_foot_index" to 31,
        "right_foot_index" to 32
    )

    fun enrichSingleBody(observation: SubjectObservation): SubjectObservation {
        if (observation.bodyCount != 1) return observation
        return LandmarkAliasEnricher.enrich(
            observation = observation,
            rules = rules(poseIndex = 0)
        )
    }

    fun rules(poseIndex: Int): List<LandmarkAliasRule> = canonicalIndices.map { (alias, index) ->
        LandmarkAliasRule(
            aliasId = alias,
            sourceLandmarkIds = listOf(
                MediaPipePoseObservationMapper.rawPointId(poseIndex, index)
            ),
            strategy = LandmarkAliasStrategy.DIRECT
        )
    }
}
