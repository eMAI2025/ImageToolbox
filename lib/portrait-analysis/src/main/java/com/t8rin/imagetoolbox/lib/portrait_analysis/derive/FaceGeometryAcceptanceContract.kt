/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis.derive

import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation

/**
 * Fail-closed contract separating face detection from permission to use face geometry.
 *
 * A detector bounding box is evidence that a face candidate exists. It is not evidence that
 * landmarks, contours or mesh are safe to render or edit. The active observation is therefore
 * present only after a later acceptance gate explicitly accepts the candidate geometry.
 * Raw detector evidence is always retained separately for diagnostics.
 */
const val FACE_GEOMETRY_ACCEPTANCE_GATE_VERSION = "POSTAC_MASTER_FACE_GATE_V1"

enum class FaceGeometryAcceptanceStatus {
    NO_FACE,
    FACE_DETECTED_GEOMETRY_ACCEPTED,
    FACE_DETECTED_GEOMETRY_REJECTED
}

enum class FaceGeometryRejectionReason {
    NO_FACE_CANDIDATE,
    MISSING_REQUIRED_POSE_OR_AXIS,
    VISIBLE_SIDE_CONTRADICTION,
    UNSUPPORTED_PARTIAL_OR_STRONG_PROFILE,
    CLOSED_FULL_OVAL_IN_PARTIAL_POSE,
    HIDDEN_SIDE_EYE_OR_BROW_PRESENT,
    GEOMETRY_OUTSIDE_FACE_BOUNDS,
    GEOMETRY_OUTSIDE_IMAGE,
    COLLAPSED_GEOMETRY,
    BROKEN_LANDMARK_CONTOUR_REFERENCE,
    BROKEN_MESH_REFERENCE,
    RAW_VISIBLE_GEOMETRY_CONTRADICTION
}

data class FaceGeometryAcceptanceDecision(
    val status: FaceGeometryAcceptanceStatus,
    val rawObservation: SubjectObservation,
    val activeObservation: SubjectObservation?,
    val reasons: Set<FaceGeometryRejectionReason>,
    val gateVersion: String = FACE_GEOMETRY_ACCEPTANCE_GATE_VERSION
) {
    init {
        when (status) {
            FaceGeometryAcceptanceStatus.NO_FACE -> {
                require(activeObservation == null)
                require(FaceGeometryRejectionReason.NO_FACE_CANDIDATE in reasons)
            }

            FaceGeometryAcceptanceStatus.FACE_DETECTED_GEOMETRY_ACCEPTED -> {
                require(activeObservation != null)
                require(reasons.isEmpty())
            }

            FaceGeometryAcceptanceStatus.FACE_DETECTED_GEOMETRY_REJECTED -> {
                require(activeObservation == null)
                require(reasons.isNotEmpty())
                require(FaceGeometryRejectionReason.NO_FACE_CANDIDATE !in reasons)
            }
        }
    }
}
