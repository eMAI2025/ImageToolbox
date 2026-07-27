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
 * Routes detector evidence for P1 visual proof without promoting it to usable edit geometry.
 *
 * The active observation remains governed by [com.t8rin.imagetoolbox.lib.portrait_analysis.derive.FaceGeometryAcceptanceGate].
 * This router only decides which already-existing detector layer is visible in the diagnostic UI.
 */
object PortraitFaceDiagnosticRouting {

    const val FINGERPRINT = "POSTAC_MASTER_FACE_DIAGNOSTIC_ROUTING_V1"

    fun candidateSource(
        rawObservation: SubjectObservation?,
        activeObservation: SubjectObservation
    ): SubjectObservation = rawObservation ?: activeObservation

    fun visualProofObservation(
        rawObservation: SubjectObservation,
        visibleObservation: SubjectObservation,
        fullFaceGeometryAllowed: Boolean
    ): SubjectObservation = if (fullFaceGeometryAllowed) {
        rawObservation
    } else {
        visibleObservation
    }
}
