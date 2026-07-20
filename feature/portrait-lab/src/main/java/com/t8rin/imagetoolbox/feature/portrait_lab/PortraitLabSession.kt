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
import com.t8rin.imagetoolbox.lib.portrait_analysis.engine.ObservationBenchmarkResult
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ObservationBackend
import com.t8rin.imagetoolbox.lib.portrait_analysis.overlay.PortraitOverlayScene
import com.t8rin.imagetoolbox.lib.portrait_analysis.report.ParameterGateReport
import com.t8rin.imagetoolbox.lib.portrait_analysis.report.PortraitBackendComparisonReport
import com.t8rin.imagetoolbox.lib.portrait_analysis.report.PortraitRepeatabilityReport

data class PortraitBackendAvailability(
    val backend: ObservationBackend,
    val available: Boolean,
    val reason: String? = null
)

data class PortraitLabRunOutput(
    val sourceBitmap: Bitmap,
    val overlayScene: PortraitOverlayScene,
    val parameterReport: ParameterGateReport,
    val backendComparisonReport: PortraitBackendComparisonReport,
    val repeatabilityReport: PortraitRepeatabilityReport,
    val backendResults: List<ObservationBenchmarkResult>,
    val warnings: List<String>
)

sealed interface PortraitLabRunResult {
    data class Success(val output: PortraitLabRunOutput) : PortraitLabRunResult

    data class Failure(
        val message: String,
        val exceptionType: String? = null
    ) : PortraitLabRunResult
}

interface PortraitLabRunner : AutoCloseable {
    val availability: List<PortraitBackendAvailability>

    suspend fun run(
        uri: Uri,
        repeatedRuns: Int = 3
    ): PortraitLabRunResult
}
