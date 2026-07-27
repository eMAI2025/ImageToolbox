/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.feature.portrait_lab

import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation

/**
 * Boundary between the frozen full-image detector baseline and optional active-face analysis.
 *
 * Resolving this boundary never selects a face, crops the source or executes geometry gates. It
 * only converts the complete detector observation into the canonical baseline and candidate list.
 */
object FaceDetectionBaselineRouting {

    const val FINGERPRINT = "FACE_DETECTION_BASELINE_ROUTING_V1"

    data class Snapshot internal constructor(
        val baseline: FaceDetectionBaseline.Result,
        val candidates: List<PortraitFaceCandidate>,
        val activeFaceIndex: Int? = null,
        val fingerprint: String = FINGERPRINT
    ) {
        init {
            require(activeFaceIndex == null) {
                "Baseline routing must not select an active face"
            }
            require(candidates.map(PortraitFaceCandidate::faceIndex) ==
                baseline.faces.map(FaceDetectionBaseline.FaceRecord::faceIndex))
        }
    }

    fun resolve(
        rawObservation: SubjectObservation?,
        activeObservation: SubjectObservation
    ): Snapshot {
        val fullImageObservation = PortraitFaceDiagnosticRouting.candidateSource(
            rawObservation = rawObservation,
            activeObservation = activeObservation
        )
        val baseline = FaceDetectionBaseline.evaluate(fullImageObservation)
        val candidates = baseline.faces.map { face ->
            PortraitFaceCandidate(
                faceIndex = face.faceIndex,
                bounds = face.boundingBox
            )
        }
        return Snapshot(
            baseline = baseline,
            candidates = candidates,
            activeFaceIndex = null
        )
    }
}
