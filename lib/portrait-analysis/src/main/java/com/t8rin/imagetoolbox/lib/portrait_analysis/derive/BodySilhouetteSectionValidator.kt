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
 * overlaps the other, an unrestricted perpendicular scan can cross the whole person and still look
 * numerically valid. Such a line is not a limb width. This validator compares every derived limb
 * cross-section with the local pose-bone length in source-pixel space and removes implausibly long
 * sections before they can be reported as available.
 */
data class BodySectionRejection(
    val sectionId: String,
    val aggregateRegionId: String,
    val measuredPixels: Float,
    val referenceBonePixels: Float,
    val measuredToBoneRatio: Float,
    val maximumAllowedRatio: Float
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
        val maximumMeasuredToBoneRatio: Float
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
            if (contour.vertexIds.size != 2) return@mapNotNull null
            val firstSectionPoint = observation.landmarks[contour.vertexIds.first()]?.point
                ?: return@mapNotNull null
            val secondSectionPoint = observation.landmarks[contour.vertexIds.last()]?.point
                ?: return@mapNotNull null
            val firstBonePoint = observation.landmarks[rule.firstPoseLandmarkId]?.point
                ?: return@mapNotNull null
            val secondBonePoint = observation.landmarks[rule.secondPoseLandmarkId]?.point
                ?: return@mapNotNull null

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
            if (referencePixels <= 1f) return@mapNotNull null

            val ratio = measuredPixels / referencePixels
            if (ratio <= rule.maximumMeasuredToBoneRatio) {
                null
            } else {
                BodySectionRejection(
                    sectionId = rule.sectionId,
                    aggregateRegionId = rule.aggregateRegionId,
                    measuredPixels = measuredPixels,
                    referenceBonePixels = referencePixels,
                    measuredToBoneRatio = ratio,
                    maximumAllowedRatio = rule.maximumMeasuredToBoneRatio
                )
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
                itemId = "LOCAL_SECTION_RATIO_${rejection.measuredToBoneRatio}"
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

    private fun pixelDistance(
        first: NormalizedPoint3D,
        second: NormalizedPoint3D,
        sourceWidth: Int,
        sourceHeight: Int
    ): Float = hypot(
        (second.x - first.x) * sourceWidth,
        (second.y - first.y) * sourceHeight
    )
}
