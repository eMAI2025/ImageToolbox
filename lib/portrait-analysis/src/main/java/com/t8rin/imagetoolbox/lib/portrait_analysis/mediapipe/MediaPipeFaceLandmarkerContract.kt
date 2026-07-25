/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis.mediapipe

import com.t8rin.imagetoolbox.lib.portrait_analysis.derive.FaceGeometryAcceptanceDecision
import com.t8rin.imagetoolbox.lib.portrait_analysis.derive.FaceGeometryAcceptanceStatus
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.NormalizedPoint3D
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation

/**
 * SDK-independent contract for the future MediaPipe Face Landmarker runtime.
 *
 * The runtime remains blocked until a verified model asset is supplied. This contract intentionally
 * contains no MediaPipe classes and cannot silently fall back to fabricated dense geometry.
 */
const val MEDIAPIPE_FACE_LANDMARKER_CONTRACT_VERSION =
    "POSTAC_MASTER_MEDIAPIPE_FACE_LANDMARKER_V1"

const val REQUIRED_FACE_LANDMARKER_MODEL_ASSET =
    "face_landmarker.task"

enum class MediaPipeFaceLandmarkerRuntimeState {
    MODEL_NOT_CONFIGURED,
    MODEL_PROVENANCE_UNVERIFIED,
    READY_FOR_INITIALIZATION,
    INITIALIZATION_FAILED,
    READY
}

data class MediaPipeModelProvenance(
    val assetName: String,
    val sha256: String,
    val sourceUri: String,
    val licenseIdentifier: String,
    val verifiedBy: String,
    val verifiedAtUtc: String
) {
    init {
        require(assetName == REQUIRED_FACE_LANDMARKER_MODEL_ASSET)
        require(sha256.matches(Regex("[0-9a-fA-F]{64}")))
        require(sourceUri.isNotBlank())
        require(licenseIdentifier.isNotBlank())
        require(verifiedBy.isNotBlank())
        require(verifiedAtUtc.isNotBlank())
    }
}

data class ActiveFaceRoi(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val selectedFaceIndex: Int = 0
) {
    init {
        require(sourceWidth > 0)
        require(sourceHeight > 0)
        require(selectedFaceIndex >= 0)
        require(left in 0f..1f)
        require(top in 0f..1f)
        require(right in 0f..1f)
        require(bottom in 0f..1f)
        require(right > left)
        require(bottom > top)
    }

    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

/**
 * Maps normalized ROI coordinates to normalized source-image coordinates and back.
 * No clipping is performed here; out-of-range points remain observable evidence for the acceptance
 * gate and must not be hidden by the adapter.
 */
object MediaPipeFaceRoiTransform {

    fun roiToSource(point: NormalizedPoint3D, roi: ActiveFaceRoi): NormalizedPoint3D =
        NormalizedPoint3D(
            x = roi.left + point.x * roi.width,
            y = roi.top + point.y * roi.height,
            z = point.z
        )

    fun sourceToRoi(point: NormalizedPoint3D, roi: ActiveFaceRoi): NormalizedPoint3D =
        NormalizedPoint3D(
            x = (point.x - roi.left) / roi.width,
            y = (point.y - roi.top) / roi.height,
            z = point.z
        )
}

data class MediaPipeFaceLandmarkerDiagnostics(
    val contractVersion: String = MEDIAPIPE_FACE_LANDMARKER_CONTRACT_VERSION,
    val runtimeState: MediaPipeFaceLandmarkerRuntimeState,
    val modelProvenance: MediaPipeModelProvenance?,
    val activeRoi: ActiveFaceRoi?,
    val detectedFaceCount: Int,
    val selectedFaceIndex: Int?,
    val rawLandmarkCount: Int,
    val visibleLandmarkCount: Int,
    val acceptedLandmarkCount: Int,
    val rawTriangleCount: Int,
    val visibleTriangleCount: Int,
    val acceptedTriangleCount: Int,
    val blendshapeCount: Int,
    val transformationMatrixCount: Int,
    val warnings: List<String> = emptyList()
) {
    init {
        require(detectedFaceCount >= 0)
        require(rawLandmarkCount >= 0)
        require(visibleLandmarkCount in 0..rawLandmarkCount)
        require(acceptedLandmarkCount in 0..visibleLandmarkCount)
        require(rawTriangleCount >= 0)
        require(visibleTriangleCount in 0..rawTriangleCount)
        require(acceptedTriangleCount in 0..visibleTriangleCount)
        require(blendshapeCount >= 0)
        require(transformationMatrixCount >= 0)
        require(selectedFaceIndex == null || selectedFaceIndex >= 0)
        if (runtimeState == MediaPipeFaceLandmarkerRuntimeState.READY) {
            requireNotNull(modelProvenance)
            requireNotNull(activeRoi)
        }
    }
}

/**
 * The adapter must preserve three distinct geometry layers:
 *
 * 1. raw detector output;
 * 2. visibility-filtered candidate geometry;
 * 3. geometry accepted by the common fail-closed gate.
 *
 * A MediaPipe result never bypasses [FaceGeometryAcceptanceDecision].
 */
data class MediaPipeFaceLandmarkerOutput(
    val rawObservation: SubjectObservation,
    val visibleObservation: SubjectObservation?,
    val acceptance: FaceGeometryAcceptanceDecision,
    val diagnostics: MediaPipeFaceLandmarkerDiagnostics
) {
    init {
        if (acceptance.activeObservation == null) {
            require(
                acceptance.status !=
                    FaceGeometryAcceptanceStatus.FACE_DETECTED_GEOMETRY_ACCEPTED
            )
        }
        require(acceptance.rawObservation == rawObservation)
    }

    val acceptedObservation: SubjectObservation?
        get() = acceptance.activeObservation
}

interface MediaPipeFaceLandmarkerAdapter {
    val runtimeState: MediaPipeFaceLandmarkerRuntimeState
    val modelProvenance: MediaPipeModelProvenance?

    /**
     * Processes exactly one explicitly selected ROI. Implementations must reject calls without a
     * verified model and must not run whole-frame multi-face geometry implicitly.
     */
    suspend fun observeSingleFaceRoi(
        roi: ActiveFaceRoi,
        imageToken: String
    ): MediaPipeFaceLandmarkerOutput

    fun close()
}
