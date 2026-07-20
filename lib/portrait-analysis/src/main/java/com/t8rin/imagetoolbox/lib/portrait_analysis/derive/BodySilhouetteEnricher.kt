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
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ConfidenceMask
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ConfidenceSource
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ContourObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.LandmarkObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.NormalizedPoint3D
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ObservationBackend
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.RegionObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.VisibilityState
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

data class BodySilhouetteDerivationPolicy(
    val foregroundThreshold: Float = 0.50f,
    val maximumRadiusFraction: Float = 0.45f,
    val maximumAnchorGapFraction: Float = 0.02f,
    val minimumRunPixels: Int = 3,
    val bandThicknessNormalized: Float = 0.03f,
    val waistInterpolation: Float = 0.65f
) {
    init {
        require(foregroundThreshold in 0f..1f)
        require(maximumRadiusFraction in 0f..1f)
        require(maximumAnchorGapFraction in 0f..1f)
        require(minimumRunPixels >= 2)
        require(bandThicknessNormalized in 0f..1f)
        require(waistInterpolation in 0f..1f)
    }
}

enum class BodySilhouetteSkipReason {
    BODY_COUNT_UNSUPPORTED,
    SUBJECT_MASK_MISSING,
    LANDMARK_MISSING,
    LANDMARK_CONFIDENCE_UNAVAILABLE,
    LANDMARK_NOT_VISIBLE,
    DEGENERATE_AXIS,
    MASK_CROSS_SECTION_NOT_FOUND,
    IDENTIFIER_COLLISION
}

data class BodySilhouetteSkippedSection(
    val sectionId: String,
    val reason: BodySilhouetteSkipReason,
    val itemId: String? = null
)

data class BodySilhouetteEnrichmentResult(
    val observation: SubjectObservation,
    val derivedRegionIds: Set<String>,
    val skippedSections: List<BodySilhouetteSkippedSection>
)

/**
 * Derives visible silhouette cross-sections from a person mask and pose landmarks.
 *
 * This is not an anatomy estimator. It measures only the continuous foreground run intersecting
 * a pose-derived anchor. Loose clothing remains part of the visible silhouette by design.
 */
object BodySilhouetteEnricher {

    private const val SHOULDER_SECTION = "derived_body_shoulder_section"
    private const val WAIST_SECTION = "derived_body_waist_section"
    private const val HIP_SECTION = "derived_body_hip_section"
    private const val LEFT_UPPER_ARM_SECTION = "derived_body_left_upper_arm_section"
    private const val LEFT_FOREARM_SECTION = "derived_body_left_forearm_section"
    private const val RIGHT_UPPER_ARM_SECTION = "derived_body_right_upper_arm_section"
    private const val RIGHT_FOREARM_SECTION = "derived_body_right_forearm_section"
    private const val LEFT_THIGH_SECTION = "derived_body_left_thigh_section"
    private const val LEFT_CALF_SECTION = "derived_body_left_calf_section"
    private const val RIGHT_THIGH_SECTION = "derived_body_right_thigh_section"
    private const val RIGHT_CALF_SECTION = "derived_body_right_calf_section"

    fun enrich(
        observation: SubjectObservation,
        policy: BodySilhouetteDerivationPolicy = BodySilhouetteDerivationPolicy()
    ): BodySilhouetteEnrichmentResult {
        if (observation.bodyCount != 1) {
            return BodySilhouetteEnrichmentResult(
                observation = observation,
                derivedRegionIds = emptySet(),
                skippedSections = listOf(
                    BodySilhouetteSkippedSection(
                        sectionId = "body",
                        reason = BodySilhouetteSkipReason.BODY_COUNT_UNSUPPORTED
                    )
                )
            )
        }

        val mask = observation.masks[PortraitRegionId.SUBJECT_MASK]?.mask
            ?: return BodySilhouetteEnrichmentResult(
                observation = observation,
                derivedRegionIds = emptySet(),
                skippedSections = listOf(
                    BodySilhouetteSkippedSection(
                        sectionId = "body",
                        reason = BodySilhouetteSkipReason.SUBJECT_MASK_MISSING,
                        itemId = PortraitRegionId.SUBJECT_MASK
                    )
                )
            )

        val sections = mutableListOf<DerivedSection>()
        val skipped = mutableListOf<BodySilhouetteSkippedSection>()

        deriveTorsoSections(
            observation = observation,
            mask = mask,
            policy = policy,
            sections = sections,
            skipped = skipped
        )
        deriveLimbSection(
            sectionId = LEFT_UPPER_ARM_SECTION,
            startId = PortraitLandmarkId.LEFT_SHOULDER,
            endId = PortraitLandmarkId.LEFT_ELBOW,
            observation = observation,
            mask = mask,
            policy = policy,
            sections = sections,
            skipped = skipped
        )
        deriveLimbSection(
            sectionId = LEFT_FOREARM_SECTION,
            startId = PortraitLandmarkId.LEFT_ELBOW,
            endId = PortraitLandmarkId.LEFT_WRIST,
            observation = observation,
            mask = mask,
            policy = policy,
            sections = sections,
            skipped = skipped
        )
        deriveLimbSection(
            sectionId = RIGHT_UPPER_ARM_SECTION,
            startId = PortraitLandmarkId.RIGHT_SHOULDER,
            endId = PortraitLandmarkId.RIGHT_ELBOW,
            observation = observation,
            mask = mask,
            policy = policy,
            sections = sections,
            skipped = skipped
        )
        deriveLimbSection(
            sectionId = RIGHT_FOREARM_SECTION,
            startId = PortraitLandmarkId.RIGHT_ELBOW,
            endId = PortraitLandmarkId.RIGHT_WRIST,
            observation = observation,
            mask = mask,
            policy = policy,
            sections = sections,
            skipped = skipped
        )
        deriveLimbSection(
            sectionId = LEFT_THIGH_SECTION,
            startId = PortraitLandmarkId.LEFT_HIP,
            endId = PortraitLandmarkId.LEFT_KNEE,
            observation = observation,
            mask = mask,
            policy = policy,
            sections = sections,
            skipped = skipped
        )
        deriveLimbSection(
            sectionId = LEFT_CALF_SECTION,
            startId = PortraitLandmarkId.LEFT_KNEE,
            endId = PortraitLandmarkId.LEFT_ANKLE,
            observation = observation,
            mask = mask,
            policy = policy,
            sections = sections,
            skipped = skipped
        )
        deriveLimbSection(
            sectionId = RIGHT_THIGH_SECTION,
            startId = PortraitLandmarkId.RIGHT_HIP,
            endId = PortraitLandmarkId.RIGHT_KNEE,
            observation = observation,
            mask = mask,
            policy = policy,
            sections = sections,
            skipped = skipped
        )
        deriveLimbSection(
            sectionId = RIGHT_CALF_SECTION,
            startId = PortraitLandmarkId.RIGHT_KNEE,
            endId = PortraitLandmarkId.RIGHT_ANKLE,
            observation = observation,
            mask = mask,
            policy = policy,
            sections = sections,
            skipped = skipped
        )

        val newLandmarks = linkedMapOf<String, LandmarkObservation>()
        val newContours = linkedMapOf<String, ContourObservation>()
        sections.forEach { section ->
            listOf(section.first, section.second).forEach { endpoint ->
                newLandmarks[endpoint.id] = LandmarkObservation(
                    id = endpoint.id,
                    point = endpoint.point,
                    confidence = section.confidence,
                    confidenceSource = ConfidenceSource.DERIVED,
                    visibility = VisibilityState.VISIBLE,
                    backend = ObservationBackend.DERIVED_BODY_SILHOUETTE
                )
            }
            newContours[section.id] = ContourObservation(
                id = section.id,
                vertexIds = listOf(section.first.id, section.second.id),
                closed = false,
                confidence = section.confidence,
                confidenceSource = ConfidenceSource.DERIVED,
                backend = ObservationBackend.DERIVED_BODY_SILHOUETTE
            )
        }

        val newRegions = linkedMapOf<String, RegionObservation>()
        addRegion(
            regionId = PortraitRegionId.SHOULDER_CONTOUR,
            sectionIds = listOf(SHOULDER_SECTION),
            sections = sections,
            target = newRegions
        )
        addRegion(
            regionId = PortraitRegionId.WAIST_CONTOUR,
            sectionIds = listOf(WAIST_SECTION),
            sections = sections,
            target = newRegions
        )
        addRegion(
            regionId = PortraitRegionId.HIP_CONTOUR,
            sectionIds = listOf(HIP_SECTION),
            sections = sections,
            target = newRegions
        )
        addRegion(
            regionId = PortraitRegionId.ARM_CONTOURS,
            sectionIds = listOf(
                LEFT_UPPER_ARM_SECTION,
                LEFT_FOREARM_SECTION,
                RIGHT_UPPER_ARM_SECTION,
                RIGHT_FOREARM_SECTION
            ),
            sections = sections,
            target = newRegions
        )
        addRegion(
            regionId = PortraitRegionId.LEG_CONTOURS,
            sectionIds = listOf(
                LEFT_THIGH_SECTION,
                LEFT_CALF_SECTION,
                RIGHT_THIGH_SECTION,
                RIGHT_CALF_SECTION
            ),
            sections = sections,
            target = newRegions
        )

        val collisions = buildList {
            addAll(newLandmarks.keys.intersect(observation.landmarks.keys))
            addAll(newContours.keys.intersect(observation.contours.keys))
            addAll(newRegions.keys.intersect(observation.regions.keys))
        }.distinct().sorted()
        if (collisions.isNotEmpty()) {
            return BodySilhouetteEnrichmentResult(
                observation = observation,
                derivedRegionIds = emptySet(),
                skippedSections = skipped + collisions.map { id ->
                    BodySilhouetteSkippedSection(
                        sectionId = "body",
                        reason = BodySilhouetteSkipReason.IDENTIFIER_COLLISION,
                        itemId = id
                    )
                }
            )
        }

        return BodySilhouetteEnrichmentResult(
            observation = observation.copy(
                landmarks = observation.landmarks + newLandmarks,
                regions = observation.regions + newRegions,
                contours = observation.contours + newContours
            ),
            derivedRegionIds = newRegions.keys,
            skippedSections = skipped.toList()
        )
    }

    private fun deriveTorsoSections(
        observation: SubjectObservation,
        mask: ConfidenceMask,
        policy: BodySilhouetteDerivationPolicy,
        sections: MutableList<DerivedSection>,
        skipped: MutableList<BodySilhouetteSkippedSection>
    ) {
        val shoulders = landmarkPair(
            observation,
            PortraitLandmarkId.LEFT_SHOULDER,
            PortraitLandmarkId.RIGHT_SHOULDER,
            SHOULDER_SECTION,
            skipped
        )
        val hips = landmarkPair(
            observation,
            PortraitLandmarkId.LEFT_HIP,
            PortraitLandmarkId.RIGHT_HIP,
            HIP_SECTION,
            skipped
        )

        shoulders?.let { pair ->
            deriveHorizontalSection(
                sectionId = SHOULDER_SECTION,
                anchor = pair.center,
                sourceConfidence = pair.confidence,
                mask = mask,
                policy = policy,
                sections = sections,
                skipped = skipped
            )
        }
        hips?.let { pair ->
            deriveHorizontalSection(
                sectionId = HIP_SECTION,
                anchor = pair.center,
                sourceConfidence = pair.confidence,
                mask = mask,
                policy = policy,
                sections = sections,
                skipped = skipped
            )
        }
        if (shoulders != null && hips != null) {
            val waistAnchor = interpolate(
                shoulders.center,
                hips.center,
                policy.waistInterpolation
            )
            deriveHorizontalSection(
                sectionId = WAIST_SECTION,
                anchor = waistAnchor,
                sourceConfidence = minOf(shoulders.confidence, hips.confidence),
                mask = mask,
                policy = policy,
                sections = sections,
                skipped = skipped
            )
        } else {
            skipped += BodySilhouetteSkippedSection(
                sectionId = WAIST_SECTION,
                reason = BodySilhouetteSkipReason.LANDMARK_MISSING
            )
        }
    }

    private fun deriveLimbSection(
        sectionId: String,
        startId: String,
        endId: String,
        observation: SubjectObservation,
        mask: ConfidenceMask,
        policy: BodySilhouetteDerivationPolicy,
        sections: MutableList<DerivedSection>,
        skipped: MutableList<BodySilhouetteSkippedSection>
    ) {
        val pair = landmarkPair(
            observation = observation,
            firstId = startId,
            secondId = endId,
            sectionId = sectionId,
            skipped = skipped
        ) ?: return

        val axisX = (pair.second.point.x - pair.first.point.x) * mask.width
        val axisY = (pair.second.point.y - pair.first.point.y) * mask.height
        val axisLength = hypot(axisX, axisY)
        if (axisLength <= 0.0001f) {
            skipped += BodySilhouetteSkippedSection(
                sectionId = sectionId,
                reason = BodySilhouetteSkipReason.DEGENERATE_AXIS
            )
            return
        }
        val directionX = -axisY / axisLength
        val directionY = axisX / axisLength
        deriveSection(
            sectionId = sectionId,
            anchor = pair.center,
            directionX = directionX,
            directionY = directionY,
            sourceConfidence = pair.confidence,
            mask = mask,
            policy = policy,
            sections = sections,
            skipped = skipped
        )
    }

    private fun deriveHorizontalSection(
        sectionId: String,
        anchor: NormalizedPoint3D,
        sourceConfidence: Float,
        mask: ConfidenceMask,
        policy: BodySilhouetteDerivationPolicy,
        sections: MutableList<DerivedSection>,
        skipped: MutableList<BodySilhouetteSkippedSection>
    ) {
        deriveSection(
            sectionId = sectionId,
            anchor = anchor,
            directionX = 1f,
            directionY = 0f,
            sourceConfidence = sourceConfidence,
            mask = mask,
            policy = policy,
            sections = sections,
            skipped = skipped
        )
    }

    private fun deriveSection(
        sectionId: String,
        anchor: NormalizedPoint3D,
        directionX: Float,
        directionY: Float,
        sourceConfidence: Float,
        mask: ConfidenceMask,
        policy: BodySilhouetteDerivationPolicy,
        sections: MutableList<DerivedSection>,
        skipped: MutableList<BodySilhouetteSkippedSection>
    ) {
        val measurement = extractCrossSection(
            mask = mask,
            anchor = anchor,
            directionX = directionX,
            directionY = directionY,
            policy = policy
        )
        if (measurement == null) {
            skipped += BodySilhouetteSkippedSection(
                sectionId = sectionId,
                reason = BodySilhouetteSkipReason.MASK_CROSS_SECTION_NOT_FOUND
            )
            return
        }
        sections += DerivedSection(
            id = sectionId,
            first = DerivedEndpoint(
                id = "${sectionId}_a",
                point = measurement.first
            ),
            second = DerivedEndpoint(
                id = "${sectionId}_b",
                point = measurement.second
            ),
            confidence = minOf(sourceConfidence, measurement.meanConfidence),
            pixelCoverage = (
                measurement.normalizedLength * policy.bandThicknessNormalized
                ).coerceIn(0f, 1f)
        )
    }

    private fun extractCrossSection(
        mask: ConfidenceMask,
        anchor: NormalizedPoint3D,
        directionX: Float,
        directionY: Float,
        policy: BodySilhouetteDerivationPolicy
    ): CrossSectionMeasurement? {
        val anchorX = anchor.x * (mask.width - 1)
        val anchorY = anchor.y * (mask.height - 1)
        val radius = (
            max(mask.width, mask.height) * policy.maximumRadiusFraction
            ).roundToInt().coerceAtLeast(policy.minimumRunPixels)
        val maximumGap = (
            max(mask.width, mask.height) * policy.maximumAnchorGapFraction
            ).roundToInt().coerceAtLeast(1)

        val samples = (-radius..radius).mapNotNull { offset ->
            val x = (anchorX + directionX * offset).roundToInt()
            val y = (anchorY + directionY * offset).roundToInt()
            if (x !in 0 until mask.width || y !in 0 until mask.height) {
                null
            } else {
                CrossSectionSample(
                    offset = offset,
                    x = x,
                    y = y,
                    confidence = mask[x, y]
                )
            }
        }.distinctBy { Pair(it.x, it.y) }

        val runs = mutableListOf<MutableList<CrossSectionSample>>()
        var current: MutableList<CrossSectionSample>? = null
        samples.forEach { sample ->
            if (sample.confidence >= policy.foregroundThreshold) {
                val active = current ?: mutableListOf<CrossSectionSample>().also {
                    current = it
                    runs += it
                }
                active += sample
            } else {
                current = null
            }
        }

        val selected = runs
            .filter { it.size >= policy.minimumRunPixels }
            .minByOrNull { run -> run.minOf { abs(it.offset) } }
            ?.takeIf { run -> run.minOf { abs(it.offset) } <= maximumGap }
            ?: return null

        val first = selected.first()
        val last = selected.last()
        val firstPoint = NormalizedPoint3D(
            x = first.x.toFloat() / (mask.width - 1).coerceAtLeast(1),
            y = first.y.toFloat() / (mask.height - 1).coerceAtLeast(1)
        )
        val secondPoint = NormalizedPoint3D(
            x = last.x.toFloat() / (mask.width - 1).coerceAtLeast(1),
            y = last.y.toFloat() / (mask.height - 1).coerceAtLeast(1)
        )
        return CrossSectionMeasurement(
            first = firstPoint,
            second = secondPoint,
            meanConfidence = selected.map { it.confidence }.average().toFloat(),
            normalizedLength = hypot(
                secondPoint.x - firstPoint.x,
                secondPoint.y - firstPoint.y
            )
        )
    }

    private fun landmarkPair(
        observation: SubjectObservation,
        firstId: String,
        secondId: String,
        sectionId: String,
        skipped: MutableList<BodySilhouetteSkippedSection>
    ): LandmarkPair? {
        val first = observation.landmarks[firstId]
        val second = observation.landmarks[secondId]
        if (first == null || second == null) {
            skipped += BodySilhouetteSkippedSection(
                sectionId = sectionId,
                reason = BodySilhouetteSkipReason.LANDMARK_MISSING,
                itemId = if (first == null) firstId else secondId
            )
            return null
        }
        if (first.confidence == null || second.confidence == null) {
            skipped += BodySilhouetteSkippedSection(
                sectionId = sectionId,
                reason = BodySilhouetteSkipReason.LANDMARK_CONFIDENCE_UNAVAILABLE,
                itemId = if (first.confidence == null) firstId else secondId
            )
            return null
        }
        if (
            first.visibility != VisibilityState.VISIBLE ||
            second.visibility != VisibilityState.VISIBLE
        ) {
            skipped += BodySilhouetteSkippedSection(
                sectionId = sectionId,
                reason = BodySilhouetteSkipReason.LANDMARK_NOT_VISIBLE,
                itemId = if (first.visibility != VisibilityState.VISIBLE) firstId else secondId
            )
            return null
        }
        return LandmarkPair(
            first = first,
            second = second,
            center = midpoint(first.point, second.point),
            confidence = minOf(first.confidence, second.confidence)
        )
    }

    private fun addRegion(
        regionId: String,
        sectionIds: List<String>,
        sections: List<DerivedSection>,
        target: MutableMap<String, RegionObservation>
    ) {
        val selected = sectionIds.mapNotNull { id -> sections.firstOrNull { it.id == id } }
        if (selected.size != sectionIds.size) return
        target[regionId] = RegionObservation(
            id = regionId,
            confidence = selected.minOf { it.confidence },
            confidenceSource = ConfidenceSource.DERIVED,
            occlusion = null,
            pixelCoverage = selected.sumOf { it.pixelCoverage.toDouble() }
                .toFloat()
                .coerceIn(0f, 1f),
            backend = ObservationBackend.DERIVED_BODY_SILHOUETTE
        )
    }

    private fun midpoint(
        first: NormalizedPoint3D,
        second: NormalizedPoint3D
    ) = NormalizedPoint3D(
        x = (first.x + second.x) / 2f,
        y = (first.y + second.y) / 2f,
        z = null
    )

    private fun interpolate(
        first: NormalizedPoint3D,
        second: NormalizedPoint3D,
        ratio: Float
    ) = NormalizedPoint3D(
        x = first.x + (second.x - first.x) * ratio,
        y = first.y + (second.y - first.y) * ratio,
        z = null
    )

    private data class LandmarkPair(
        val first: LandmarkObservation,
        val second: LandmarkObservation,
        val center: NormalizedPoint3D,
        val confidence: Float
    )

    private data class DerivedEndpoint(
        val id: String,
        val point: NormalizedPoint3D
    )

    private data class DerivedSection(
        val id: String,
        val first: DerivedEndpoint,
        val second: DerivedEndpoint,
        val confidence: Float,
        val pixelCoverage: Float
    )

    private data class CrossSectionSample(
        val offset: Int,
        val x: Int,
        val y: Int,
        val confidence: Float
    )

    private data class CrossSectionMeasurement(
        val first: NormalizedPoint3D,
        val second: NormalizedPoint3D,
        val meanConfidence: Float,
        val normalizedLength: Float
    )
}
