/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.feature.portrait_lab

import android.graphics.Bitmap
import android.net.Uri
import com.t8rin.imagetoolbox.lib.portrait_analysis.derive.BodyLandmarkVisibilityGate
import com.t8rin.imagetoolbox.lib.portrait_analysis.derive.BodySilhouetteEnrichmentResult
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.overlay.PortraitOverlayScene

enum class SilhouetteVisualStatus {
    PASS,
    PARTIAL,
    FAILED
}

data class SilhouetteRegionCapability(
    val regionId: String,
    val available: Boolean,
    val reason: String? = null
)

data class SilhouetteLabRunOutput(
    val sourceBitmap: Bitmap,
    val previewBitmap: Bitmap,
    val sourceMetadata: PortraitSourceMetadata,
    /** Visibility-filtered and locality-validated geometry used by overlay/export. */
    val observation: SubjectObservation,
    val enrichment: BodySilhouetteEnrichmentResult,
    val overlayScene: PortraitOverlayScene,
    val status: SilhouetteVisualStatus,
    val regionCapabilities: List<SilhouetteRegionCapability>,
    val runtimeLog: List<String>,
    val warnings: List<String>,
    /** Separate diagnostic evidence; callers may supply the unmodified merged backend payload. */
    val rawObservation: SubjectObservation = observation,
    val bodyVisibility: BodyLandmarkVisibilityGate.Assessment =
        BodyLandmarkVisibilityGate.evaluate(rawObservation)
)

sealed interface SilhouetteLabRunResult {
    data class Success(val output: SilhouetteLabRunOutput) : SilhouetteLabRunResult

    data class Failure(
        val message: String,
        val exceptionType: String? = null
    ) : SilhouetteLabRunResult
}

interface SilhouetteLabRunner : AutoCloseable {
    suspend fun run(uri: Uri): SilhouetteLabRunResult
}
