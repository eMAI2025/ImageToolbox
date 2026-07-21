/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.feature.portrait_lab

import android.content.Context
import android.net.Uri
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ObservationBackend

fun createPortraitLabRunner(context: Context): PortraitLabRunner = FossPortraitLabRunner()

private class FossPortraitLabRunner : PortraitLabRunner {
    override val availability: List<PortraitBackendAvailability> = listOf(
        PortraitBackendAvailability(
            backend = ObservationBackend.ML_KIT_FACE_DETECTION,
            available = false,
            reason = "ML Kit runtime is not included in the FOSS build"
        ),
        PortraitBackendAvailability(
            backend = ObservationBackend.MEDIAPIPE_FACE_LANDMARKER,
            available = false,
            reason = "MediaPipe runtime models are not bundled in the FOSS build"
        )
    )

    override suspend fun run(
        uri: Uri,
        backend: ObservationBackend,
        repeatedRuns: Int
    ): PortraitLabRunResult = PortraitLabRunResult.Failure(
        message = "Portrait Lab runtime detectors are available in the Market build only"
    )

    override fun close() = Unit
}
