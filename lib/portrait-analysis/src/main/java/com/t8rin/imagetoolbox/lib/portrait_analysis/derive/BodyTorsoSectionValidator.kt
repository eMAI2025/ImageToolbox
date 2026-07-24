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
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.NormalizedPoint3D
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.VisibilityState
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Validates mask-derived shoulder, waist and hip sections against a pose-derived torso corridor.
 *
 * The corridor is derived independently from the left and right shoulder-to-hip edges. This keeps
 * the validation tied to directly observed pose evidence when the torso is rotated or the shoulder
 * and hip landmarks are not level. Missing, reversed or degenerate side evidence fails closed.
 *
 * It does not estimate anatomy under clothing and it does not repair a rejected section.
 */
enum class BodyTorsoRejectionReason {
    TORSO_WIDTH_OUTLIER,
    TORSO_OCCLUDED_BY_LIMB,
    MISSING_TORSO_EVIDENCE,
    DEGENERATE_TORSO_AXIS,
    MALFORMED_TORSO_SECTION
}

data class BodyTorsoSectionRejection(
    val sectionId: String,
    val regionId: String,
    val reason: BodyTorsoRejectionReason,
    val measuredWidthNormalized: Float,
    val expectedWidthNormalized: Float,
    val measuredToExpectedRatio: Float,
    val leftOverflowNormalized: Float,
    val rightOverflowNormalized: Float,
    val intersectingLimbSegment: String? = null
)

data class BodyTorsoSectionValidationResult(
    val enrichment: BodySilhouetteEnrichmentResult,
    val rejectedSections: List<BodyTorsoSectionRejection>
)

object BodyTorsoSectionValidator {

    private data class Rule(
        val sectionId: String,
        val regionId: String,
        val maximumMeasuredToExpectedRatio: Float,
        val maximumSingleSideOverflowRatio: Float,
        val checkUpperArms: Boolean
    )

    private data class TorsoCorridor(
        val left: Float,
        val right: Float,
        val width: Float
    )

    private data class LimbSegment(
        val id: String,
        val start: NormalizedPoint3D,
        val end: NormalizedPoint3D
    )

    private val rules = listOf(
        Rule(
            sectionId = "derived_body_shoulder_section",
            regionId = PortraitRegionId.SHOULDER_CONTOUR,
            maximumMeasuredToExpectedRatio = 1.55f,
            maximumSingleSideOverflowRatio = 0.30f,
            checkUpperArms = false
        ),
        Rule(
            sectionId = "derived_body_waist_section",
            regionId = PortraitRegionId.WAIST_CONTOUR,
            maximumMeasuredToExpectedRatio = 1.45f,
            maximumSingleSideOverflowRatio = 0.22f,
            checkUpperArms = true
        ),
        Rule(
            sectionId = "derived_body_hip_section",
            regionId = PortraitRegionId.HIP_CONTOUR,
            maximumMeasuredToExpectedRatio = 1.50f,
            maximumSingleSideOverflowRatio = 0.25f,
            checkUpperArms = true
        )
    )

    fun validate(
        enrichment: BodySilhouetteEnrichmentResult
    ): BodyTorsoSectionValidationResult {
        val observation = enrichment.observation
        val rejected = rules.mapNotNull { rule ->
            validateRule(rule, observation)
        }

        if (rejected.isEmpty()) {
            return BodyTorsoSectionValidationResult(enrichment, emptyList())
        }

        val rejectedSectionIds = rejected.mapTo(linkedSetOf(), BodyTorsoSectionRejection::sectionId)
        val rejectedRegionIds = rejected.mapTo(linkedSetOf(), BodyTorsoSectionRejection::regionId)
        val rejectedEndpointIds = rejectedSectionIds.flatMapTo(linkedSetOf()) { sectionId ->
            observation.contours[sectionId]?.vertexIds.orEmpty()
        }

        val filteredObservation = observation.copy(
            landmarks = observation.landmarks - rejectedEndpointIds,
            regions = observation.regions - rejectedRegionIds,
            contours = observation.contours - rejectedSectionIds
        )
        val addedSkips = rejected.map { rejection ->
            BodySilhouetteSkippedSection(
                sectionId = rejection.sectionId,
                reason = BodySilhouetteSkipReason.MASK_CROSS_SECTION_NOT_FOUND,
                itemId = buildString {
                    append(rejection.reason.name)
                    append("_WIDTH_RATIO_")
                    append(rejection.measuredToExpectedRatio)
                    append("_LEFT_OVERFLOW_")
                    append(rejection.leftOverflowNormalized)
                    append("_RIGHT_OVERFLOW_")
                    append(rejection.rightOverflowNormalized)
                    rejection.intersectingLimbSegment?.let {
                        append("_LIMB_")
                        append(it)
                    }
                }
            )
        }

        return BodyTorsoSectionValidationResult(
            enrichment = BodySilhouetteEnrichmentResult(
                observation = filteredObservation,
                derivedRegionIds = enrichment.derivedRegionIds - rejectedRegionIds,
                skippedSections = enrichment.skippedSections + addedSkips
            ),
            rejectedSections = rejected
        )
    }

    private fun validateRule(
        rule: Rule,
        observation: SubjectObservation
    ): BodyTorsoSectionRejection? {
        val contour = observation.contours[rule.sectionId] ?: return null
        if (contour.vertexIds.size != 2) {
            return rule.rejection(BodyTorsoRejectionReason.MALFORMED_TORSO_SECTION)
        }

        val first = observation.landmarks[contour.vertexIds.first()]?.point
        val second = observation.landmarks[contour.vertexIds.last()]?.point
        val leftShoulder = visiblePoint(observation, PortraitLandmarkId.LEFT_SHOULDER)
        val rightShoulder = visiblePoint(observation, PortraitLandmarkId.RIGHT_SHOULDER)
        val leftHip = visiblePoint(observation, PortraitLandmarkId.LEFT_HIP)
        val rightHip = visiblePoint(observation, PortraitLandmarkId.RIGHT_HIP)
        if (
            first == null || second == null || leftShoulder == null || rightShoulder == null ||
            leftHip == null || rightHip == null
        ) {
            return rule.rejection(BodyTorsoRejectionReason.MISSING_TORSO_EVIDENCE)
        }

        val sectionY = (first.y + second.y) / 2f
        val corridor = torsoCorridor(
            sectionY = sectionY,
            leftShoulder = leftShoulder,
            rightShoulder = rightShoulder,
            leftHip = leftHip,
            rightHip = rightHip
        ) ?: return rule.rejection(BodyTorsoRejectionReason.DEGENERATE_TORSO_AXIS)

        val measuredLeft = min(first.x, second.x)
        val measuredRight = max(first.x, second.x)
        val measuredWidth = (measuredRight - measuredLeft).coerceAtLeast(0f)
        val leftOverflow = (corridor.left - measuredLeft).coerceAtLeast(0f)
        val rightOverflow = (measuredRight - corridor.right).coerceAtLeast(0f)
        val measuredRatio = measuredWidth / corridor.width

        val limbIntersection = firstIntersectingLimb(
            observation = observation,
            sectionY = sectionY,
            measuredLeft = measuredLeft,
            measuredRight = measuredRight,
            corridor = corridor,
            includeUpperArms = rule.checkUpperArms
        )
        if (limbIntersection != null) {
            return rule.rejection(
                reason = BodyTorsoRejectionReason.TORSO_OCCLUDED_BY_LIMB,
                measuredWidth = measuredWidth,
                expectedWidth = corridor.width,
                measuredRatio = measuredRatio,
                leftOverflow = leftOverflow,
                rightOverflow = rightOverflow,
                limbSegment = limbIntersection
            )
        }

        val maximumOverflow = corridor.width * rule.maximumSingleSideOverflowRatio
        if (
            measuredRatio > rule.maximumMeasuredToExpectedRatio ||
            leftOverflow > maximumOverflow ||
            rightOverflow > maximumOverflow
        ) {
            return rule.rejection(
                reason = BodyTorsoRejectionReason.TORSO_WIDTH_OUTLIER,
                measuredWidth = measuredWidth,
                expectedWidth = corridor.width,
                measuredRatio = measuredRatio,
                leftOverflow = leftOverflow,
                rightOverflow = rightOverflow
            )
        }

        return null
    }

    private fun torsoCorridor(
        sectionY: Float,
        leftShoulder: NormalizedPoint3D,
        rightShoulder: NormalizedPoint3D,
        leftHip: NormalizedPoint3D,
        rightHip: NormalizedPoint3D
    ): TorsoCorridor? {
        val leftAxisHeight = leftHip.y - leftShoulder.y
        val rightAxisHeight = rightHip.y - rightShoulder.y
        if (leftAxisHeight <= 0.01f || rightAxisHeight <= 0.01f) return null

        val leftRatio = (sectionY - leftShoulder.y) / leftAxisHeight
        val rightRatio = (sectionY - rightShoulder.y) / rightAxisHeight
        if (leftRatio !in 0f..1f || rightRatio !in 0f..1f) return null

        val observedLeftEdge = lerp(leftShoulder.x, leftHip.x, leftRatio)
        val observedRightEdge = lerp(rightShoulder.x, rightHip.x, rightRatio)
        if (observedLeftEdge >= observedRightEdge) return null

        val rawWidth = observedRightEdge - observedLeftEdge
        if (rawWidth <= 0.01f) return null

        val margin = rawWidth * 0.12f
        val corridorLeft = (observedLeftEdge - margin).coerceIn(0f, 1f)
        val corridorRight = (observedRightEdge + margin).coerceIn(0f, 1f)
        val corridorWidth = corridorRight - corridorLeft
        if (corridorWidth <= 0.01f) return null

        return TorsoCorridor(
            left = corridorLeft,
            right = corridorRight,
            width = corridorWidth
        )
    }

    private fun firstIntersectingLimb(
        observation: SubjectObservation,
        sectionY: Float,
        measuredLeft: Float,
        measuredRight: Float,
        corridor: TorsoCorridor,
        includeUpperArms: Boolean
    ): String? {
        val segments = buildList {
            if (includeUpperArms) {
                limbSegment(
                    observation,
                    "left_upper_arm",
                    PortraitLandmarkId.LEFT_SHOULDER,
                    PortraitLandmarkId.LEFT_ELBOW
                )?.let(::add)
                limbSegment(
                    observation,
                    "right_upper_arm",
                    PortraitLandmarkId.RIGHT_SHOULDER,
                    PortraitLandmarkId.RIGHT_ELBOW
                )?.let(::add)
            }
            limbSegment(
                observation,
                "left_forearm",
                PortraitLandmarkId.LEFT_ELBOW,
                PortraitLandmarkId.LEFT_WRIST
            )?.let(::add)
            limbSegment(
                observation,
                "right_forearm",
                PortraitLandmarkId.RIGHT_ELBOW,
                PortraitLandmarkId.RIGHT_WRIST
            )?.let(::add)
        }

        val innerMargin = corridor.width * 0.10f
        val innerLeft = corridor.left + innerMargin
        val innerRight = corridor.right - innerMargin
        return segments.firstNotNullOfOrNull { segment ->
            val crossingX = horizontalIntersectionX(segment, sectionY)
                ?: return@firstNotNullOfOrNull null
            if (
                crossingX in innerLeft..innerRight &&
                crossingX in measuredLeft..measuredRight
            ) {
                segment.id
            } else {
                null
            }
        }
    }

    private fun limbSegment(
        observation: SubjectObservation,
        id: String,
        startId: String,
        endId: String
    ): LimbSegment? {
        val start = visiblePoint(observation, startId) ?: return null
        val end = visiblePoint(observation, endId) ?: return null
        return LimbSegment(id, start, end)
    }

    private fun visiblePoint(
        observation: SubjectObservation,
        id: String
    ): NormalizedPoint3D? = observation.landmarks[id]
        ?.takeIf { it.visibility == VisibilityState.VISIBLE }
        ?.point

    private fun horizontalIntersectionX(
        segment: LimbSegment,
        y: Float
    ): Float? {
        val minimumY = min(segment.start.y, segment.end.y)
        val maximumY = max(segment.start.y, segment.end.y)
        if (y < minimumY || y > maximumY) return null

        val deltaY = segment.end.y - segment.start.y
        if (abs(deltaY) <= 0.0001f) return null
        val ratio = (y - segment.start.y) / deltaY
        if (ratio !in 0f..1f) return null
        return lerp(segment.start.x, segment.end.x, ratio)
    }

    private fun Rule.rejection(
        reason: BodyTorsoRejectionReason,
        measuredWidth: Float = Float.NaN,
        expectedWidth: Float = Float.NaN,
        measuredRatio: Float = Float.NaN,
        leftOverflow: Float = Float.NaN,
        rightOverflow: Float = Float.NaN,
        limbSegment: String? = null
    ) = BodyTorsoSectionRejection(
        sectionId = sectionId,
        regionId = regionId,
        reason = reason,
        measuredWidthNormalized = measuredWidth,
        expectedWidthNormalized = expectedWidth,
        measuredToExpectedRatio = measuredRatio,
        leftOverflowNormalized = leftOverflow,
        rightOverflowNormalized = rightOverflow,
        intersectingLimbSegment = limbSegment
    )

    private fun lerp(start: Float, end: Float, ratio: Float): Float =
        start + (end - start) * ratio
}
