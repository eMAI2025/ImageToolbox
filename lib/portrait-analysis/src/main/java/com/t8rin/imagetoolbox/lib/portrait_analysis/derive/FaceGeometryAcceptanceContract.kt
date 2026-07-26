/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis.derive

import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation

const val FACE_GEOMETRY_ACCEPTANCE_GATE_VERSION = "POSTAC_MASTER_FACE_GATE_V1"
const val FACE_GEOMETRY_ACCEPTANCE_RESULT_VERSION =
    "POSTAC_MASTER_FACE_GEOMETRY_ACCEPTANCE_RESULT_V1"

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
    FACE_TOO_SMALL_OR_BOUNDS_UNAVAILABLE,
    FACE_TOO_SMALL_IN_SOURCE_PIXELS,
    FACE_BOUNDS_OUTSIDE_IMAGE,
    NON_FINITE_GEOMETRY,
    MISSING_YAW_DEPENDENT_LANDMARKS,
    VISIBLE_SIDE_LANDMARK_CONTRADICTION,
    INSUFFICIENT_LANDMARK_SPREAD,
    GEOMETRY_OUTSIDE_FACE_BOUNDS,
    GEOMETRY_OUTSIDE_IMAGE,
    GEOMETRY_OUT_OF_FRAME_RATIO_EXCEEDED,
    COLLAPSED_GEOMETRY,
    BROKEN_LANDMARK_CONTOUR_REFERENCE,
    BROKEN_MESH_REFERENCE,
    CONTOUR_SELF_INTERSECTION,
    CONTOUR_CROSSES_UNSUPPORTED_SPACE,
    MESH_TRIANGLE_REFERENCES_REJECTED_VERTEX,
    FACE_OCCLUDED_BY_HAIR,
    FACE_OCCLUDED_BY_HAND,
    FACE_OCCLUDED_BY_PHONE,
    FACE_OCCLUDED_BY_STRONG_SHADOW,
    FACE_CROPPED_BY_FRAME,
    INSUFFICIENT_VISIBLE_FACE_ANCHORS,
    OCCLUSION_EVIDENCE_UNAVAILABLE,
    IMAGE_BLUR_TOO_HIGH,
    IMAGE_BLUR_EVIDENCE_UNAVAILABLE,
    EXIF_TRANSFORM_INCONSISTENT,
    MIRROR_TRANSFORM_INCONSISTENT,
    IMAGE_QUALITY_RESELECT_REQUIRED,
    DUAL_PASS_ROI_INCONSISTENT,
    RAW_VISIBLE_GEOMETRY_CONTRADICTION
}

enum class FaceGeometryReasonSeverity {
    INFO,
    WARNING,
    ERROR,
    CRITICAL
}

data class FaceGeometryReasonEvidence(
    val code: FaceGeometryRejectionReason,
    val severity: FaceGeometryReasonSeverity,
    val detail: String? = null
) {
    init {
        require(detail == null || detail.isNotBlank())
    }
}

data class FaceGeometryAcceptanceProvenance(
    val backendId: String,
    val backendVersion: String?,
    val modelId: String?,
    val modelSha256: String?,
    val gateVersion: String,
    val buildBranch: String,
    val buildCommit: String
) {
    init {
        require(backendId.isNotBlank())
        require(backendVersion == null || backendVersion.isNotBlank())
        require(modelId == null || modelId.isNotBlank())
        require(modelSha256 == null || modelSha256.matches(Regex("[0-9a-fA-F]{64}")))
        require(gateVersion.isNotBlank())
        require(buildBranch.isNotBlank())
        require(buildCommit.isNotBlank())
    }
}

data class FaceGeometryAcceptanceResult(
    val status: FaceGeometryAcceptanceStatus,
    val rawEvidence: SubjectObservation?,
    val activeGeometry: SubjectObservation?,
    val reasons: List<FaceGeometryReasonEvidence>,
    val provenance: FaceGeometryAcceptanceProvenance,
    val contractVersion: String = FACE_GEOMETRY_ACCEPTANCE_RESULT_VERSION
) {
    val faceDetected: Boolean
        get() = status != FaceGeometryAcceptanceStatus.NO_FACE

    val geometryUsable: Boolean
        get() = status == FaceGeometryAcceptanceStatus.FACE_DETECTED_GEOMETRY_ACCEPTED

    init {
        require(contractVersion.isNotBlank())
        require(reasons.distinctBy { it.code }.size == reasons.size)

        when (status) {
            FaceGeometryAcceptanceStatus.NO_FACE -> {
                require(rawEvidence == null)
                require(activeGeometry == null)
                require(reasons.size == 1)
                require(reasons.single().code == FaceGeometryRejectionReason.NO_FACE_CANDIDATE)
            }

            FaceGeometryAcceptanceStatus.FACE_DETECTED_GEOMETRY_ACCEPTED -> {
                require(rawEvidence != null)
                require(activeGeometry != null)
                require(reasons.isEmpty())
            }

            FaceGeometryAcceptanceStatus.FACE_DETECTED_GEOMETRY_REJECTED -> {
                require(rawEvidence != null)
                require(activeGeometry == null)
                require(reasons.isNotEmpty())
                require(reasons.none { it.code == FaceGeometryRejectionReason.NO_FACE_CANDIDATE })
                require(reasons.any {
                    it.severity == FaceGeometryReasonSeverity.ERROR ||
                        it.severity == FaceGeometryReasonSeverity.CRITICAL
                })
            }
        }
    }
}

data class FaceGeometryLegacyProjection(
    val legacyVisualStatus: String?,
    val canonicalStatus: FaceGeometryAcceptanceStatus,
    val geometryUsable: Boolean
) {
    companion object {
        fun from(
            legacyVisualStatus: String?,
            result: FaceGeometryAcceptanceResult
        ) = FaceGeometryLegacyProjection(
            legacyVisualStatus = legacyVisualStatus,
            canonicalStatus = result.status,
            geometryUsable = result.geometryUsable
        )
    }
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

    fun toCanonicalResult(
        provenance: FaceGeometryAcceptanceProvenance
    ): FaceGeometryAcceptanceResult = FaceGeometryAcceptanceResult(
        status = status,
        rawEvidence = rawObservation.takeUnless {
            status == FaceGeometryAcceptanceStatus.NO_FACE
        },
        activeGeometry = activeObservation,
        reasons = reasons
            .sortedBy { it.name }
            .map { reason ->
                FaceGeometryReasonEvidence(
                    code = reason,
                    severity = reason.defaultSeverity()
                )
            },
        provenance = provenance
    )
}

private fun FaceGeometryRejectionReason.defaultSeverity(): FaceGeometryReasonSeverity = when (this) {
    FaceGeometryRejectionReason.NO_FACE_CANDIDATE -> FaceGeometryReasonSeverity.INFO
    FaceGeometryRejectionReason.MISSING_REQUIRED_POSE_OR_AXIS,
    FaceGeometryRejectionReason.VISIBLE_SIDE_CONTRADICTION,
    FaceGeometryRejectionReason.UNSUPPORTED_PARTIAL_OR_STRONG_PROFILE,
    FaceGeometryRejectionReason.CLOSED_FULL_OVAL_IN_PARTIAL_POSE,
    FaceGeometryRejectionReason.HIDDEN_SIDE_EYE_OR_BROW_PRESENT,
    FaceGeometryRejectionReason.FACE_TOO_SMALL_OR_BOUNDS_UNAVAILABLE,
    FaceGeometryRejectionReason.FACE_TOO_SMALL_IN_SOURCE_PIXELS,
    FaceGeometryRejectionReason.FACE_BOUNDS_OUTSIDE_IMAGE,
    FaceGeometryRejectionReason.NON_FINITE_GEOMETRY,
    FaceGeometryRejectionReason.MISSING_YAW_DEPENDENT_LANDMARKS,
    FaceGeometryRejectionReason.VISIBLE_SIDE_LANDMARK_CONTRADICTION,
    FaceGeometryRejectionReason.INSUFFICIENT_LANDMARK_SPREAD,
    FaceGeometryRejectionReason.GEOMETRY_OUTSIDE_FACE_BOUNDS,
    FaceGeometryRejectionReason.GEOMETRY_OUTSIDE_IMAGE,
    FaceGeometryRejectionReason.GEOMETRY_OUT_OF_FRAME_RATIO_EXCEEDED,
    FaceGeometryRejectionReason.COLLAPSED_GEOMETRY,
    FaceGeometryRejectionReason.BROKEN_LANDMARK_CONTOUR_REFERENCE,
    FaceGeometryRejectionReason.BROKEN_MESH_REFERENCE,
    FaceGeometryRejectionReason.CONTOUR_SELF_INTERSECTION,
    FaceGeometryRejectionReason.CONTOUR_CROSSES_UNSUPPORTED_SPACE,
    FaceGeometryRejectionReason.MESH_TRIANGLE_REFERENCES_REJECTED_VERTEX,
    FaceGeometryRejectionReason.FACE_OCCLUDED_BY_HAIR,
    FaceGeometryRejectionReason.FACE_OCCLUDED_BY_HAND,
    FaceGeometryRejectionReason.FACE_OCCLUDED_BY_PHONE,
    FaceGeometryRejectionReason.FACE_OCCLUDED_BY_STRONG_SHADOW,
    FaceGeometryRejectionReason.FACE_CROPPED_BY_FRAME,
    FaceGeometryRejectionReason.INSUFFICIENT_VISIBLE_FACE_ANCHORS,
    FaceGeometryRejectionReason.OCCLUSION_EVIDENCE_UNAVAILABLE,
    FaceGeometryRejectionReason.IMAGE_BLUR_TOO_HIGH,
    FaceGeometryRejectionReason.IMAGE_BLUR_EVIDENCE_UNAVAILABLE,
    FaceGeometryRejectionReason.EXIF_TRANSFORM_INCONSISTENT,
    FaceGeometryRejectionReason.MIRROR_TRANSFORM_INCONSISTENT,
    FaceGeometryRejectionReason.IMAGE_QUALITY_RESELECT_REQUIRED,
    FaceGeometryRejectionReason.DUAL_PASS_ROI_INCONSISTENT,
    FaceGeometryRejectionReason.RAW_VISIBLE_GEOMETRY_CONTRADICTION ->
        FaceGeometryReasonSeverity.ERROR
}