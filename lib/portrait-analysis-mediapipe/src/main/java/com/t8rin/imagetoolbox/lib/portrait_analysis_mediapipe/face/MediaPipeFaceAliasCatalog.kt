/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis_mediapipe.face

import com.t8rin.imagetoolbox.lib.portrait_analysis.alias.LandmarkAliasEnricher
import com.t8rin.imagetoolbox.lib.portrait_analysis.alias.LandmarkAliasRule
import com.t8rin.imagetoolbox.lib.portrait_analysis.alias.LandmarkAliasStrategy
import com.t8rin.imagetoolbox.lib.portrait_analysis.catalog.PortraitLandmarkId
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation

/**
 * Canonical aliases backed by the documented MediaPipe face topology.
 *
 * Jaw-angle and lateral-chin aliases are intentionally absent until their anatomical meaning is
 * validated on the A1 reference set. Missing aliases keep high-risk geometry parameters disabled.
 */
object MediaPipeFaceAliasCatalog {

    fun enrichSingleFace(observation: SubjectObservation): SubjectObservation {
        if (observation.faceCount != 1) return observation
        return LandmarkAliasEnricher.enrich(
            observation = observation,
            rules = rules(faceIndex = 0)
        )
    }

    fun rules(faceIndex: Int): List<LandmarkAliasRule> = listOf(
        direct(PortraitLandmarkId.MOUTH_LEFT_CORNER, faceIndex, 61),
        direct(PortraitLandmarkId.MOUTH_RIGHT_CORNER, faceIndex, 291),
        direct(PortraitLandmarkId.UPPER_LIP_MID, faceIndex, 13),
        direct(PortraitLandmarkId.LOWER_LIP_MID, faceIndex, 14),
        direct(PortraitLandmarkId.LOWER_LIP_CENTER, faceIndex, 14),
        centroid(PortraitLandmarkId.MOUTH_CENTER, faceIndex, 13, 14),
        centroid(PortraitLandmarkId.LEFT_EYE_CENTER, faceIndex, 263, 362),
        centroid(PortraitLandmarkId.RIGHT_EYE_CENTER, faceIndex, 33, 133),
        direct(PortraitLandmarkId.CHIN_CENTER, faceIndex, 152)
    )

    private fun direct(
        aliasId: String,
        faceIndex: Int,
        pointIndex: Int
    ) = LandmarkAliasRule(
        aliasId = aliasId,
        sourceLandmarkIds = listOf(
            MediaPipeFaceObservationMapper.rawPointId(faceIndex, pointIndex)
        ),
        strategy = LandmarkAliasStrategy.DIRECT
    )

    private fun centroid(
        aliasId: String,
        faceIndex: Int,
        vararg pointIndices: Int
    ) = LandmarkAliasRule(
        aliasId = aliasId,
        sourceLandmarkIds = pointIndices.map { pointIndex ->
            MediaPipeFaceObservationMapper.rawPointId(faceIndex, pointIndex)
        },
        strategy = LandmarkAliasStrategy.CENTROID
    )
}
