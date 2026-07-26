/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis.derive

import com.t8rin.imagetoolbox.lib.portrait_analysis.catalog.PortraitRegionId
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation

/**
 * Local, fail-closed consistency gate between accepted person-mask components and visible pose.
 *
 * Detection of a pose landmark or a foreground mask component is not permission to expose body
 * geometry. A segment is available only when its complete visibility contract is satisfied and all
 * required landmarks lie in or immediately adjacent to the verified primary person component.
 * Contradictions block dependent regions rather than reconstructing anatomy or disabling unrelated
 * regions. Raw and filtered component evidence remain separate in [Assessment].
 */
object BodyMaskPoseConsistencyGate {

    const val FINGERPRINT = "POSTAC_MASTER_BODY_MASK_POSE_CONSISTENCY_V1"

    /** Coordinate tolerance for mask raster quantisation, covered by boundary tests. */
    const val COMPONENT_BOUNDS_TOLERANCE = 0.02f

    enum class RejectionReason {
        MASK_COMPONENT_EVIDENCE_MISSING,
        PRIMARY_COMPONENT_MISSING,
        LANDMARK_OUTSIDE_PRIMARY_COMPONENT,
        SEGMENT_VISIBILITY_BLOCKED,
        SEGMENT_MASK_SUPPORT_MISSING,
        TORSO_CORRIDOR_INCONSISTENT,
        CROSSED_ARM_CONTAMINATION,
        PHONE_CONTAMINATION,
        LEGS_TOUCHING_AMBIGUITY,
        MULTI_PERSON_CONTAMINATION,
        MULTI_PERSON_CONTAMINATION_UNLOCALIZED
    }

    data class LocalConsistencyEvidence(
        val torsoCorridorConsistent: Boolean = true,
        val crossedArmContaminatedRegionIds: Set<String> = emptySet(),
        val phoneContaminatedRegionIds: Set<String> = emptySet(),
        val legsTouchingAmbiguous: Boolean = false,
        val multiPersonContaminatedRegionIds: Set<String> = emptySet()
    )

    data class LandmarkDecision(
        val landmarkId: String,
        val maskSupported: Boolean,
        val reasons: Set<RejectionReason>
    )

    data class SegmentDecision(
        val segmentId: String,
        val available: Boolean,
        val blockingLandmarkIds: Set<String>,
        val reasons: Set<RejectionReason>
    )

    data class RegionDecision(
        val regionId: String,
        val available: Boolean,
        val blockingSegmentIds: Set<String>,
        val reasons: Set<RejectionReason>
    )

    data class Assessment(
        val rawMaskComponents: List<BodyMaskComponentGate.ComponentEvidence>,
        val filteredMaskComponents: List<BodyMaskComponentGate.ComponentEvidence>,
        val primaryComponentId: String?,
        val landmarks: Map<String, LandmarkDecision>,
        val segments: Map<String, SegmentDecision>,
        val regions: Map<String, RegionDecision>,
        val fingerprint: String = FINGERPRINT
    ) {
        val acceptedRegionIds: Set<String>
            get() = regions.values.filter(RegionDecision::available).mapTo(linkedSetOf(), RegionDecision::regionId)
    }

    fun evaluate(
        observation: SubjectObservation,
        visibility: BodyLandmarkVisibilityGate.Assessment,
        maskComponents: BodyMaskComponentGate.Assessment,
        evidence: LocalConsistencyEvidence = LocalConsistencyEvidence()
    ): Assessment {
        val primary = maskComponents.primaryComponentId?.let { id ->
            maskComponents.filteredComponents.firstOrNull { it.id == id }
        }
        val noMaskEvidence = maskComponents.rawComponents.isEmpty()
        val hasUnlocalizedMultiPerson = maskComponents.rejectedComponents.any { decision ->
            BodyMaskComponentGate.RejectionReason.MULTI_PERSON_COMPONENT_AMBIGUOUS in decision.reasons
        } && evidence.multiPersonContaminatedRegionIds.isEmpty()

        val landmarkDecisions = visibility.landmarks.mapValues { (id, landmarkEvidence) ->
            val reasons = linkedSetOf<RejectionReason>()
            when {
                noMaskEvidence -> reasons += RejectionReason.MASK_COMPONENT_EVIDENCE_MISSING
                primary == null -> reasons += RejectionReason.PRIMARY_COMPONENT_MISSING
                landmarkEvidence.usable && landmarkEvidence.rawLandmark != null &&
                    !primary.bounds.containsWithTolerance(
                        landmarkEvidence.rawLandmark.point.x,
                        landmarkEvidence.rawLandmark.point.y
                    ) -> reasons += RejectionReason.LANDMARK_OUTSIDE_PRIMARY_COMPONENT
            }
            LandmarkDecision(id, maskSupported = reasons.isEmpty(), reasons = reasons)
        }

        val segmentDecisions = visibility.segments.mapValues { (segmentId, segment) ->
            val blockingLandmarks = segment.requiredLandmarkIds.filterTo(linkedSetOf()) { id ->
                !visibility.landmarks.getValue(id).usable || !landmarkDecisions.getValue(id).maskSupported
            }
            val reasons = linkedSetOf<RejectionReason>()
            if (!segment.available) reasons += RejectionReason.SEGMENT_VISIBILITY_BLOCKED
            if (blockingLandmarks.any { !landmarkDecisions.getValue(it).maskSupported }) {
                reasons += RejectionReason.SEGMENT_MASK_SUPPORT_MISSING
            }
            SegmentDecision(segmentId, reasons.isEmpty(), blockingLandmarks, reasons)
        }

        val torsoRegions = setOf(
            PortraitRegionId.SHOULDER_CONTOUR,
            PortraitRegionId.WAIST_CONTOUR,
            PortraitRegionId.HIP_CONTOUR
        )
        val regionDecisions = visibility.regions.mapValues { (regionId, region) ->
            val blockingSegments = region.requiredSegmentIds.filterTo(linkedSetOf()) { id ->
                !segmentDecisions.getValue(id).available
            }
            val reasons = linkedSetOf<RejectionReason>()
            if (blockingSegments.isNotEmpty()) reasons += RejectionReason.SEGMENT_MASK_SUPPORT_MISSING
            if (!evidence.torsoCorridorConsistent && regionId in torsoRegions) {
                reasons += RejectionReason.TORSO_CORRIDOR_INCONSISTENT
            }
            if (regionId in evidence.crossedArmContaminatedRegionIds) {
                reasons += RejectionReason.CROSSED_ARM_CONTAMINATION
            }
            if (regionId in evidence.phoneContaminatedRegionIds) {
                reasons += RejectionReason.PHONE_CONTAMINATION
            }
            if (evidence.legsTouchingAmbiguous && regionId == PortraitRegionId.LEG_CONTOURS) {
                reasons += RejectionReason.LEGS_TOUCHING_AMBIGUITY
            }
            if (regionId in evidence.multiPersonContaminatedRegionIds) {
                reasons += RejectionReason.MULTI_PERSON_CONTAMINATION
            }
            if (hasUnlocalizedMultiPerson) {
                reasons += RejectionReason.MULTI_PERSON_CONTAMINATION_UNLOCALIZED
            }
            RegionDecision(regionId, reasons.isEmpty(), blockingSegments, reasons)
        }

        return Assessment(
            rawMaskComponents = maskComponents.rawComponents,
            filteredMaskComponents = maskComponents.filteredComponents,
            primaryComponentId = primary?.id,
            landmarks = landmarkDecisions,
            segments = segmentDecisions,
            regions = regionDecisions
        )
    }

    private fun BodyMaskComponentGate.NormalizedBounds.containsWithTolerance(
        x: Float,
        y: Float
    ): Boolean = x.isFinite() && y.isFinite() &&
        x in (left - COMPONENT_BOUNDS_TOLERANCE)..(right + COMPONENT_BOUNDS_TOLERANCE) &&
        y in (top - COMPONENT_BOUNDS_TOLERANCE)..(bottom + COMPONENT_BOUNDS_TOLERANCE)
}
