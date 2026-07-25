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
import com.t8rin.imagetoolbox.lib.portrait_analysis.derive.FaceGeometryAcceptanceDecision
import com.t8rin.imagetoolbox.lib.portrait_analysis.derive.FaceRegionAwareness
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ObservationBackend
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.overlay.PortraitOverlayScene
import com.t8rin.imagetoolbox.lib.portrait_analysis.visual.VisualProofEvaluation

data class PortraitBackendAvailability(
    val backend: ObservationBackend,
    val available: Boolean,
    val reason: String? = null
)

data class PortraitSourceMetadata(
    val encodedWidth: Int,
    val encodedHeight: Int,
    val orientedWidth: Int,
    val orientedHeight: Int,
    val exifOrientation: Int,
    val orientationDegrees: Int,
    val mirrored: Boolean,
    val previewWidth: Int,
    val previewHeight: Int
)

data class PortraitLabRunOutput(
    val selectedBackend: ObservationBackend,
    val sourceBitmap: Bitmap,
    val previewBitmap: Bitmap,
    val sourceMetadata: PortraitSourceMetadata,
    val observation: SubjectObservation,
    val visualProof: VisualProofEvaluation,
    val overlayScene: PortraitOverlayScene,
    val runtimeLog: List<String>,
    val warnings: List<String>,
    val faceRegionAwareness: FaceRegionAwareness? = null,
    val rawObservation: SubjectObservation? = null,
    val faceGeometryAcceptance: FaceGeometryAcceptanceDecision? = null
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
        backend: ObservationBackend = ObservationBackend.ML_KIT_FACE_DETECTION
    ): PortraitLabRunResult
}
