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
import kotlin.math.hypot

/**
 * Rejects silhouette cross-sections that are not local to the limb that produced them.
 *
 * A person mask is one connected foreground object. When an arm overlaps the torso or one leg
 * overlaps the other, an unrestricted perpendicular scan can cross unrelated foreground and still
 * look numerically plausible. Every active limb section must therefore satisfy both constraints:
 *
 * 1. its width is plausible relative to the local pose-bone length;
 * 2. its midpoint remains close to the expected local pose-bone segment.
 *
 * Missing or degenerate local pose evidence fails closed. No invisible anatomy is inferred.
 */
enum class BodySectionRejectionReason {
    WIDTH_OUTLIER,
    SECTION_NON_LOCAL,
    MISSING_LOCAL_EVIDENCE,
    DEGENERATE_LOCAL_BONE,
    MALFORMED_SECTION
}

data class BodySectionRejection(
    val sectionId: String,
    val aggregateRegionId: String,
    val reason: BodySectionRejectionReason,
    val measuredPixels: Float,
    val referenceBonePixels: Float,
    val measuredToBoneRatio: Float,
    val maximumAllowedRatio: Float,
    val midpointDistancePixels: Float,
    val midpointDistanceToBoneRatio: Float,
    val maximumMidpointDistanceToBoneRatio: Float
)

data class BodySectionValidationResult(
    val enrichment: BodySilhouetteEnrichmentResult,
    val rejectedSections: List<BodySectionRejection>
)

object BodySilhouetteSectionValidator {

    private data class Rule(
        val sectionId: String,
        val firstPoseLandmarkId: String,
        val secondPoseLandmarkId: String,
        val aggregateRegionId: String,
        val maximumMeasuredToBoneRatio: Float,
        val maximumMidpointDistanceToBoneRatio: Float = 0.35f
    )

    private val rules = listOf(
        Rule(
            sectionId = "derived_body_left_upper_arm_section",
            firstPoseLandmarkId = PortraitLandmarkId.LEFT_SHOULDER,
            secondPoseLandmarkId = PortraitLandmarkId.LEFT_ELBOW,
            aggregateRegionId = PortraitRegionId.ARM_CONTOURS,
            maximumMeasuredToBoneRatio = 0.75f
        ),
        Rule(
            sectionId = "derived_body_left_forearm_section",
            firstPoseLandmarkId = PortraitLandmarkId.LEFT_ELBOW,
            secondPoseLandmarkId = PortraitLandmarkId.LEFT_WRIST,
            aggregateRegionId = PortraitRegionId.ARM_CONTOURS,
            maximumMeasuredToBoneRatio = 0.75f
        ),
        Rule(
            sectionId = "derived_body_right_upper_arm_section",
            firstPoseLandmarkId = PortraitLandmarkId.RIGHT_SHOULDER,
            secondPoseLandmarkId = PortraitLandmarkId.RIGHT_ELBOW,
            aggregateRegionId = PortraitRegionId.ARM_CONTOURS,
            maximumMeasuredToBoneRatio = 0.75f
        ),
        Rule(
            sectionId = "derived_body_right_forearm_section",
            firstPoseLandmarkId = PortraitLandmarkId.RIGHT_ELBOW,
            secondPoseLandmarkId = PortraitLandmarkId.RIGHT_WRIST,
            aggregateRegionId = PortraitRegionId.ARM_CONTOURS,
            maximumMeasuredToBoneRatio = 0.75f
        ),
        Rule(
            sectionId = "derived_body_left_thigh_section",
            firstPoseLandmarkId = PortraitLandmarkId.LEFT_HIP,
            secondPoseLandmarkId = PortraitLandmarkId.LEFT_KNEE,
            aggregateRegionId = PortraitRegionId.LEG_CONTOURS,
            maximumMeasuredToBoneRatio = 0.85f
        ),
        Rule(
            sectionId = "derived_body_left_calf_section",
            firstPoseLandmarkId = PortraitLandmarkId.LEFT_KNEE,
            secondPoseLandmarkId = PortraitLandmarkId.LEFT_ANKLE,
            aggregateRegionId = PortraitRegionId.LEG_CONTOURS,
            maximumMeasuredToBoneRatio = 0.65f
        ),
        Rule(
            sectionId = "derived_body_right_thigh_section",
            firstPoseLandmarkId = PortraitLandmarkId.RIGHT_HIP,
            secondPoseLandmarkId = PortraitLandmarkId.RIGHT_KNEE,
            aggregateRegionId = PortraitRegionId.LEG_CONTOURS,
            maximumMeasuredToBoneRatio = 0.85f
        ),
        Rule(
            sectionId = "derived_body_right_calf_section",
            firstPoseLandmarkId = PortraitLandmarkId.RIGHT_KNEE,
            secondPoseLandmarkId = PortraitLandmarkId.RIGHT_ANKLE,
            aggregateRegionId = PortraitRegionId.LEG_CONTOURS,
            maximumMeasuredToBoneRatio = 0.65f
        )
    )

    fun validate(
        enrichment: BodySilhouetteEnrichmentResult,
        sourceWidth: Int,
        sourceHeight: Int
    ): BodySectionValidationResult {
        require(sourceWidth > 0)
        require(sourceHeight > 0)

        val observation = enrichment.observation
        val rejected = rules.mapNotNull { rule ->
            val contour = observation.contours[rule.sectionId] ?: return@mapNotNull null
            if (contour.vertexIds.size != 2) {
                return@mapNotNull rule.rejection(
                    reason = BodySectionRejectionReason.MALFORMED_SECTION
                )
            }

            val firstSectionPoint = observation.landmarks[contour.vertexIds.first()]?.point
            val secondSectionPoint = observation.landmarks[contour.vertexIds.last()]?.point
            val firstBonePoint = observation.landmarks[rule.firstPoseLandmarkId]?.point
            val secondBonePoint = observation.landmarks[rule.secondPoseLandmarkId]?.point
            if (
                firstSectionPoint == null ||
                secondSectionPoint == null ||
                firstBonePoint == null ||
                secondBonePoint == null
            ) {
                return@mapNotNull rule.rejection(
                    reason = BodySectionRejectionReason.MISSING_LOCAL_EVIDENCE
                )
            }

            val measuredPixels = pixelDistance(
                firstSectionPoint,
                secondSectionPoint,
                sourceWidth,
                sourceHeight
            )
            val referencePixels = pixelDistance(
                firstBonePoint,
                secondBonePoint,
                sourceWidth,
                sourceHeight
            )
            if (referencePixels <= 1f) {
                return@mapNotNull rule.rejection(
                    reason = BodySectionRejectionReason.DEGENERATE_LOCAL_BONE,
                    measuredPixels = measuredPixels,
                    referenceBonePixels = referencePixels
                )
            }

            val measuredRatio = measuredPixels / referencePixels
            val sectionMidpoint = NormalizedPoint3D(
                x = (firstSectionPoint.x + secondSectionPoint.x) / 2f,
                y = (firstSectionPoint.y + secondSectionPoint.y) / 2f,
                z = (firstSectionPoint.z + secondSectionPoint.z) / 2f
            )
            val midpointDistancePixels = pixelDistanceToSegment(
                point = sectionMidpoint,
                segmentStart = firstBonePoint,
                segmentEnd = secondBonePoint,
                sourceWidth = sourceWidth,
                sourceHeight = sourceHeight
            )
            val midpointDistanceRatio = midpointDistancePixels / referencePixels

            when {
                measuredRatio > rule.maximumMeasuredToBoneRatio -> rule.rejection(
                    reason = BodySectionRejectionReason.WIDTH_OUTLIER,
                    measuredPixels = measuredPixels,
                    referenceBonePixels = referencePixels,
                    measuredToBoneRatio = measuredRatio,
                    midpointDistancePixels = midpointDistancePixels,
                    midpointDistanceToBoneRatio = midpointDistanceRatio
                )

                midpointDistanceRatio > rule.maximumMidpointDistanceToBoneRatio -> rule.rejection(
                    reason = BodySectionRejectionReason.SECTION_NON_LOCAL,
                    measuredPixels = measuredPixels,
                    referenceBonePixels = referencePixels,
                    measuredToBoneRatio = measuredRatio,
                    midpointDistancePixels = midpointDistancePixels,
                    midpointDistanceToBoneRatio = midpointDistanceRatio
                )

                else -> null
            }
        }

        if (rejected.isEmpty()) {
            return BodySectionValidationResult(enrichment, emptyList())
        }

        val rejectedSectionIds = rejected.mapTo(linkedSetOf(), BodySectionRejection::sectionId)
        val rejectedRegionIds = rejected.mapTo(linkedSetOf(), BodySectionRejection::aggregateRegionId)
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
                    append(rejection.measuredToBoneRatio)
                    append("_MIDPOINT_RATIO_")
                    append(rejection.midpointDistanceToBoneRatio)
                }
            )
        }

        return BodySectionValidationResult(
            enrichment = BodySilhouetteEnrichmentResult(
                observation = filteredObservation,
                derivedRegionIds = enrichment.derivedRegionIds - rejectedRegionIds,
                skippedSections = enrichment.skippedSections + addedSkips
            ),
            rejectedSections = rejected
        )
    }

    private fun Rule.rejection(
        reason: BodySectionRejectionReason,
        measuredPixels: Float = Float.NaN,
        referenceBonePixels: Float = Float.NaN,
        measuredToBoneRatio: Float = Float.NaN,
        midpointDistancePixels: Float = Float.NaN,
        midpointDistanceToBoneRatio: Float = Float.NaN
    ) = BodySectionRejection(
        sectionId = sectionId,
        aggregateRegionId = aggregateRegionId,
        reason = reason,
        measuredPixels = measuredPixels,
        referenceBonePixels = referenceBonePixels,
        measuredToBoneRatio = measuredToBoneRatio,
        maximumAllowedRatio = maximumMeasuredToBoneRatio,
        midpointDistancePixels = midpointDistancePixels,
        midpointDistanceToBoneRatio = midpointDistanceToBoneRatio,
        maximumMidpointDistanceToBoneRatio = maximumMidpointDistanceToBoneRatio
    )

    private fun pixelDistance(
        first: NormalizedPoint3D,
        second: NormalizedPoint3D,
        sourceWidth: Int,
        sourceHeight: Int
    ): Float = hypot(
        (second.x - first.x) * sourceWidth,
        (second.y - first.y) * sourceHeight
    )

    private fun pixelDistanceToSegment(
        point: NormalizedPoint3D,
        segmentStart: NormalizedPoint3D,
        segmentEnd: NormalizedPoint3D,
        sourceWidth: Int,
        sourceHeight: Int
    ): Float {
        val pointX = point.x * sourceWidth
        val pointY = point.y * sourceHeight
        val startX = segmentStart.x * sourceWidth
        val startY = segmentStart.y * sourceHeight
        val endX = segmentEnd.x * sourceWidth
        val endY = segmentEnd.y * sourceHeight
        val segmentX = endX - startX
        val segmentY = endY - startY
        val segmentLengthSquared = segmentX * segmentX + segmentY * segmentY
        if (segmentLengthSquared <= 1f) return Float.POSITIVE_INFINITY

        val projection = (
            ((pointX - startX) * segmentX + (pointY - startY) * segmentY) /
                segmentLengthSquared
            ).coerceIn(0f, 1f)
        val closestX = startX + projection * segmentX
        val closestY = startY + projection * segmentY
        return hypot(pointX - closestX, pointY - closestY)
    }
}
