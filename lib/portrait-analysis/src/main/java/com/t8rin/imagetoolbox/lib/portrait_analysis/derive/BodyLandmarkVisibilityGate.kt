/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis.derive

import com.t8rin.imagetoolbox.lib.portrait_analysis.catalog.PortraitLandmarkId
import com.t8rin.imagetoolbox.lib.portrait_analysis.catalog.PortraitRegionId
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.LandmarkObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ObservationBackend
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.VisibilityState

/**
 * Fail-closed body-landmark visibility contract.
 *
 * ML Kit may return a nominal pose landmark outside the source image or with weak InFrameLikelihood.
 * Presence in the payload therefore does not authorize active geometry. Only landmarks classified as
 * [BodyLandmarkEvidenceState.VISIBLE] may participate in active segments or derived regions.
 */
object BodyLandmarkVisibilityGate {

    const val FINGERPRINT = "POSTAC_MASTER_BODY_LANDMARK_VISIBILITY_V1"

    /** Versioned evidence floor. It is covered by boundary tests and is not tuned to one image. */
    const val MINIMUM_IN_FRAME_CONFIDENCE = 0.50f

    enum class BodyLandmarkEvidenceState {
        VISIBLE,
        LOW_CONFIDENCE,
        OFF_FRAME,
        MISSING
    }

    enum class BodyVisibilityReason {
        LANDMARK_MISSING,
        LANDMARK_LOW_CONFIDENCE,
        LANDMARK_OFF_FRAME,
        LANDMARK_VISIBILITY_NOT_CONFIRMED
    }

    data class LandmarkEvidence(
        val landmarkId: String,
        val state: BodyLandmarkEvidenceState,
        val confidence: Float?,
        val reason: BodyVisibilityReason?,
        val backend: ObservationBackend?
    ) {
        val usable: Boolean get() = state == BodyLandmarkEvidenceState.VISIBLE
    }

    data class SegmentCapability(
        val segmentId: String,
        val requiredLandmarkIds: Set<String>,
        val available: Boolean,
        val blockingLandmarkIds: Set<String>,
        val reasons: Set<BodyVisibilityReason>
    )

    data class RegionCapability(
        val regionId: String,
        val requiredSegmentIds: Set<String>,
        val available: Boolean,
        val blockingSegmentIds: Set<String>,
        val reasons: Set<BodyVisibilityReason>
    )

    data class Assessment(
        val landmarks: Map<String, LandmarkEvidence>,
        val segments: Map<String, SegmentCapability>,
        val regions: Map<String, RegionCapability>,
        val fingerprint: String = FINGERPRINT
    ) {
        val visibleCount: Int get() = landmarks.values.count { it.state == BodyLandmarkEvidenceState.VISIBLE }
        val lowConfidenceCount: Int get() = landmarks.values.count { it.state == BodyLandmarkEvidenceState.LOW_CONFIDENCE }
        val offFrameCount: Int get() = landmarks.values.count { it.state == BodyLandmarkEvidenceState.OFF_FRAME }
        val missingCount: Int get() = landmarks.values.count { it.state == BodyLandmarkEvidenceState.MISSING }
    }

    private data class SegmentRule(
        val id: String,
        val landmarkIds: Set<String>
    )

    private val segmentRules = listOf(
        SegmentRule("shoulders", setOf(PortraitLandmarkId.LEFT_SHOULDER, PortraitLandmarkId.RIGHT_SHOULDER)),
        SegmentRule("left_upper_arm", setOf(PortraitLandmarkId.LEFT_SHOULDER, PortraitLandmarkId.LEFT_ELBOW)),
        SegmentRule("left_forearm", setOf(PortraitLandmarkId.LEFT_ELBOW, PortraitLandmarkId.LEFT_WRIST)),
        SegmentRule("right_upper_arm", setOf(PortraitLandmarkId.RIGHT_SHOULDER, PortraitLandmarkId.RIGHT_ELBOW)),
        SegmentRule("right_forearm", setOf(PortraitLandmarkId.RIGHT_ELBOW, PortraitLandmarkId.RIGHT_WRIST)),
        SegmentRule("left_torso", setOf(PortraitLandmarkId.LEFT_SHOULDER, PortraitLandmarkId.LEFT_HIP)),
        SegmentRule("right_torso", setOf(PortraitLandmarkId.RIGHT_SHOULDER, PortraitLandmarkId.RIGHT_HIP)),
        SegmentRule("hips", setOf(PortraitLandmarkId.LEFT_HIP, PortraitLandmarkId.RIGHT_HIP)),
        SegmentRule("left_thigh", setOf(PortraitLandmarkId.LEFT_HIP, PortraitLandmarkId.LEFT_KNEE)),
        SegmentRule("left_calf", setOf(PortraitLandmarkId.LEFT_KNEE, PortraitLandmarkId.LEFT_ANKLE)),
        SegmentRule("right_thigh", setOf(PortraitLandmarkId.RIGHT_HIP, PortraitLandmarkId.RIGHT_KNEE)),
        SegmentRule("right_calf", setOf(PortraitLandmarkId.RIGHT_KNEE, PortraitLandmarkId.RIGHT_ANKLE))
    )

    private val regionRules = mapOf(
        PortraitRegionId.SHOULDER_CONTOUR to setOf("shoulders"),
        PortraitRegionId.ARM_CONTOURS to setOf(
            "left_upper_arm", "left_forearm", "right_upper_arm", "right_forearm"
        ),
        PortraitRegionId.WAIST_CONTOUR to setOf("left_torso", "right_torso"),
        PortraitRegionId.HIP_CONTOUR to setOf("left_torso", "right_torso", "hips"),
        PortraitRegionId.LEG_CONTOURS to setOf(
            "left_thigh", "left_calf", "right_thigh", "right_calf"
        )
    )

    val requiredPoseLandmarkIds: Set<String> = segmentRules
        .flatMapTo(linkedSetOf(), SegmentRule::landmarkIds)

    fun evaluate(
        observation: SubjectObservation,
        minimumConfidence: Float = MINIMUM_IN_FRAME_CONFIDENCE
    ): Assessment {
        require(minimumConfidence in 0f..1f)

        val landmarkEvidence = requiredPoseLandmarkIds.associateWith { id ->
            classify(id, observation.landmarks[id], minimumConfidence)
        }
        val segmentCapabilities = segmentRules.associate { rule ->
            val blocked = rule.landmarkIds.filterTo(linkedSetOf()) { id ->
                landmarkEvidence.getValue(id).state != BodyLandmarkEvidenceState.VISIBLE
            }
            val reasons = blocked.mapNotNullTo(linkedSetOf()) { id ->
                landmarkEvidence.getValue(id).reason
            }
            rule.id to SegmentCapability(
                segmentId = rule.id,
                requiredLandmarkIds = rule.landmarkIds,
                available = blocked.isEmpty(),
                blockingLandmarkIds = blocked,
                reasons = reasons
            )
        }
        val regionCapabilities = regionRules.mapValues { (regionId, requiredSegments) ->
            val blocked = requiredSegments.filterTo(linkedSetOf()) { segmentId ->
                segmentCapabilities.getValue(segmentId).available.not()
            }
            RegionCapability(
                regionId = regionId,
                requiredSegmentIds = requiredSegments,
                available = blocked.isEmpty(),
                blockingSegmentIds = blocked,
                reasons = blocked.flatMapTo(linkedSetOf()) { segmentId ->
                    segmentCapabilities.getValue(segmentId).reasons
                }
            )
        }
        return Assessment(landmarkEvidence, segmentCapabilities, regionCapabilities)
    }

    /**
     * Produces the active pose input without modifying raw evidence.
     * Non-visible ML Kit pose landmarks and every contour depending on them are removed.
     */
    fun filterForActiveGeometry(
        rawObservation: SubjectObservation,
        assessment: Assessment
    ): SubjectObservation {
        val rejectedPoseIds = assessment.landmarks.values
            .filterNot(LandmarkEvidence::usable)
            .mapTo(linkedSetOf(), LandmarkEvidence::landmarkId)
        val filteredContours = rawObservation.contours.filterValues { contour ->
            contour.vertexIds.none { it in rejectedPoseIds }
        }
        val blockedRegionIds = assessment.regions.values
            .filterNot(RegionCapability::available)
            .mapTo(linkedSetOf(), RegionCapability::regionId)
        return rawObservation.copy(
            landmarks = rawObservation.landmarks - rejectedPoseIds,
            regions = rawObservation.regions - blockedRegionIds,
            contours = filteredContours
        )
    }

    private fun classify(
        id: String,
        landmark: LandmarkObservation?,
        minimumConfidence: Float
    ): LandmarkEvidence {
        if (landmark == null) {
            return LandmarkEvidence(
                landmarkId = id,
                state = BodyLandmarkEvidenceState.MISSING,
                confidence = null,
                reason = BodyVisibilityReason.LANDMARK_MISSING,
                backend = null
            )
        }
        val point = landmark.point
        if (
            landmark.visibility == VisibilityState.OUTSIDE_FRAME ||
            point.x !in 0f..1f || point.y !in 0f..1f
        ) {
            return LandmarkEvidence(
                landmarkId = id,
                state = BodyLandmarkEvidenceState.OFF_FRAME,
                confidence = landmark.confidence,
                reason = BodyVisibilityReason.LANDMARK_OFF_FRAME,
                backend = landmark.backend
            )
        }
        if (landmark.visibility != VisibilityState.VISIBLE) {
            return LandmarkEvidence(
                landmarkId = id,
                state = BodyLandmarkEvidenceState.LOW_CONFIDENCE,
                confidence = landmark.confidence,
                reason = BodyVisibilityReason.LANDMARK_VISIBILITY_NOT_CONFIRMED,
                backend = landmark.backend
            )
        }
        if (landmark.confidence == null || landmark.confidence < minimumConfidence) {
            return LandmarkEvidence(
                landmarkId = id,
                state = BodyLandmarkEvidenceState.LOW_CONFIDENCE,
                confidence = landmark.confidence,
                reason = BodyVisibilityReason.LANDMARK_LOW_CONFIDENCE,
                backend = landmark.backend
            )
        }
        return LandmarkEvidence(
            landmarkId = id,
            state = BodyLandmarkEvidenceState.VISIBLE,
            confidence = landmark.confidence,
            reason = null,
            backend = landmark.backend
        )
    }
}
