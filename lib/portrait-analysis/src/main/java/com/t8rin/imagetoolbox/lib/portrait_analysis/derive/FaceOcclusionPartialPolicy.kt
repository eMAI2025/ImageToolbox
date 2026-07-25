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
 * Fail-closed policy for partial, cropped or explicitly occluded faces.
 *
 * The policy never infers that hair, a hand, a phone or a strong shadow is absent. Semantic
 * occlusion information must be supplied explicitly by an upstream mask/classifier. When such
 * evidence is unavailable, acceptance is based only on directly observed required anchors; missing
 * anchors are rejected instead of reconstructed.
 */
object FaceOcclusionPartialPolicy {

    const val FINGERPRINT = "POSTAC_MASTER_FACE_OCCLUSION_PARTIAL_POLICY_V1"

    enum class OcclusionKind {
        HAIR,
        HAND,
        PHONE,
        STRONG_SHADOW,
        FRAME_CROP
    }

    enum class EvidenceState {
        CLEAR,
        OCCLUDED,
        UNKNOWN
    }

    data class OcclusionEvidence(
        val states: Map<OcclusionKind, EvidenceState> = emptyMap(),
        val sourceId: String? = null
    ) {
        fun state(kind: OcclusionKind): EvidenceState = states[kind] ?: EvidenceState.UNKNOWN

        companion object {
            fun unavailable() = OcclusionEvidence()
        }
    }

    data class Metrics(
        val centralAnchorIds: Set<String>,
        val visibleSideAnchorIds: Set<String>,
        val requiredCentralAnchorCount: Int,
        val requiredVisibleSideAnchorCount: Int,
        val bboxTouchesFrame: Boolean,
        val explicitOcclusionEvidenceAvailable: Boolean
    )

    data class Assessment(
        val accepted: Boolean,
        val reasons: Set<FaceGeometryRejectionReason>,
        val metrics: Metrics
    )

    fun evaluate(
        candidateObservation: SubjectObservation,
        awareness: FaceRegionAwareness,
        evidence: OcclusionEvidence = OcclusionEvidence.unavailable()
    ): Assessment {
        val reasons = linkedSetOf<FaceGeometryRejectionReason>()
        val ids = candidateObservation.landmarks.keys.map { it.lowercase() }.toSet()

        val centralAnchors = ids.filterTo(linkedSetOf()) { id ->
            id.contains("nose") || id.contains("mouth") || id.contains("lip") ||
                id.contains("chin")
        }
        val visibleSideAnchors = ids.filterTo(linkedSetOf()) { id ->
            when (awareness.dominantImageSide) {
                FaceImageSide.LEFT -> id.contains("left_eye") || id.contains("left_cheek") ||
                    id.contains("left_jaw") || id.contains("mouth_left")
                FaceImageSide.RIGHT -> id.contains("right_eye") || id.contains("right_cheek") ||
                    id.contains("right_jaw") || id.contains("mouth_right")
                else -> false
            }
        }

        val requiredCentral = if (awareness.poseMode == FacePoseMode.FRONTAL) 3 else 2
        val requiredVisibleSide = when (awareness.poseMode) {
            FacePoseMode.HALF_PROFILE, FacePoseMode.PROFILE -> 1
            else -> 0
        }

        val bboxTouchesFrame = candidateObservation.boundingBoxTouchesFrame()
        if (bboxTouchesFrame || evidence.state(OcclusionKind.FRAME_CROP) == EvidenceState.OCCLUDED) {
            reasons += FaceGeometryRejectionReason.FACE_CROPPED_BY_FRAME
        }

        evidence.states.forEach { (kind, state) ->
            if (state == EvidenceState.OCCLUDED) {
                reasons += when (kind) {
                    OcclusionKind.HAIR -> FaceGeometryRejectionReason.FACE_OCCLUDED_BY_HAIR
                    OcclusionKind.HAND -> FaceGeometryRejectionReason.FACE_OCCLUDED_BY_HAND
                    OcclusionKind.PHONE -> FaceGeometryRejectionReason.FACE_OCCLUDED_BY_PHONE
                    OcclusionKind.STRONG_SHADOW -> FaceGeometryRejectionReason.FACE_OCCLUDED_BY_STRONG_SHADOW
                    OcclusionKind.FRAME_CROP -> FaceGeometryRejectionReason.FACE_CROPPED_BY_FRAME
                }
            }
        }

        val anchorsComplete = centralAnchors.size >= requiredCentral &&
            visibleSideAnchors.size >= requiredVisibleSide
        if (!anchorsComplete) {
            reasons += FaceGeometryRejectionReason.INSUFFICIENT_VISIBLE_FACE_ANCHORS
            if (evidence.states.isEmpty() || evidence.states.values.all { it == EvidenceState.UNKNOWN }) {
                reasons += FaceGeometryRejectionReason.OCCLUSION_EVIDENCE_UNAVAILABLE
            }
        }

        return Assessment(
            accepted = reasons.isEmpty(),
            reasons = reasons,
            metrics = Metrics(
                centralAnchorIds = centralAnchors,
                visibleSideAnchorIds = visibleSideAnchors,
                requiredCentralAnchorCount = requiredCentral,
                requiredVisibleSideAnchorCount = requiredVisibleSide,
                bboxTouchesFrame = bboxTouchesFrame,
                explicitOcclusionEvidenceAvailable = evidence.states.values.any {
                    it != EvidenceState.UNKNOWN
                }
            )
        )
    }

    private fun SubjectObservation.boundingBoxTouchesFrame(): Boolean {
        val contour = contours.values.firstOrNull { contour ->
            val id = contour.id.lowercase()
            id.contains("bounding_box") || id.contains("bbox")
        } ?: return false
        val points = contour.vertexIds.mapNotNull(landmarks::get).map { it.point }
        if (points.isEmpty()) return false
        val epsilon = 0.005f
        return points.any { point ->
            point.x <= epsilon || point.x >= 1f - epsilon ||
                point.y <= epsilon || point.y >= 1f - epsilon
        }
    }
}
