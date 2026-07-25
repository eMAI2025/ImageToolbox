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
    FACE_BOUNDS_OUTSIDE_IMAGE,
    NON_FINITE_GEOMETRY,
    MISSING_YAW_DEPENDENT_LANDMARKS,
    VISIBLE_SIDE_LANDMARK_CONTRADICTION,
    INSUFFICIENT_LANDMARK_SPREAD,
    GEOMETRY_OUTSIDE_FACE_BOUNDS,
    GEOMETRY_OUTSIDE_IMAGE,
    COLLAPSED_GEOMETRY,
    BROKEN_LANDMARK_CONTOUR_REFERENCE,
    BROKEN_MESH_REFERENCE,
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

/**
 * Provenance required to prove which backend, gate and build produced the decision.
 * Model fields remain nullable for detector-only backends that do not load an external model asset.
 */
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

/**
 * Canonical face result used by diagnostics, overlay routing and later edit gates.
 *
 * `rawEvidence` is detector evidence only. `activeGeometry` is the sole geometry that may be
 * rendered or edited. A rejected result is structurally unable to carry active geometry.
 */
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

/**
 * Legacy UI statuses remain diagnostic labels only. In particular, `PARTIAL` never grants geometry
 * usability. Usability is projected exclusively from the canonical fail-closed result.
 */
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

    /**
     * Compatibility bridge for the existing runtime gate. New consumers should use the canonical
     * result and must not infer usability from legacy visual statuses such as `PARTIAL`.
     */
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
    FaceGeometryRejectionReason.FACE_BOUNDS_OUTSIDE_IMAGE,
    FaceGeometryRejectionReason.NON_FINITE_GEOMETRY,
    FaceGeometryRejectionReason.MISSING_YAW_DEPENDENT_LANDMARKS,
    FaceGeometryRejectionReason.VISIBLE_SIDE_LANDMARK_CONTRADICTION,
    FaceGeometryRejectionReason.INSUFFICIENT_LANDMARK_SPREAD,
    FaceGeometryRejectionReason.GEOMETRY_OUTSIDE_FACE_BOUNDS,
    FaceGeometryRejectionReason.GEOMETRY_OUTSIDE_IMAGE,
    FaceGeometryRejectionReason.COLLAPSED_GEOMETRY,
    FaceGeometryRejectionReason.BROKEN_LANDMARK_CONTOUR_REFERENCE,
    FaceGeometryRejectionReason.BROKEN_MESH_REFERENCE,
    FaceGeometryRejectionReason.RAW_VISIBLE_GEOMETRY_CONTRADICTION ->
        FaceGeometryReasonSeverity.ERROR
}