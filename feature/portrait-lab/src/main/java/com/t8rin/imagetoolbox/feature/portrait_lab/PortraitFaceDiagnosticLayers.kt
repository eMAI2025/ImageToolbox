/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.feature.portrait_lab

import com.t8rin.imagetoolbox.lib.portrait_analysis.derive.FaceGeometryAcceptanceDecision
import com.t8rin.imagetoolbox.lib.portrait_analysis.derive.FaceRegionAwareness
import com.t8rin.imagetoolbox.lib.portrait_analysis.derive.FaceVisibleGeometryFilter
import com.t8rin.imagetoolbox.lib.portrait_analysis.derive.PostacMasterDiagnosticLayers
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation

/** Resolves the face RAW/FILTERED/ACCEPTED layers without any raw-to-active fallback. */
object PortraitFaceDiagnosticLayers {

    const val FINGERPRINT = "POSTAC_MASTER_FACE_DIAGNOSTIC_EXPORT_V1"

    fun resolve(
        raw: SubjectObservation,
        awareness: FaceRegionAwareness,
        decision: FaceGeometryAcceptanceDecision
    ): PostacMasterDiagnosticLayers {
        val filtered = FaceVisibleGeometryFilter.filter(raw, awareness).observation
        return PostacMasterDiagnosticLayers.fromFace(
            raw = raw,
            filtered = filtered,
            decision = decision
        )
    }
}
