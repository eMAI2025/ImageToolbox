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
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ObservationBackend
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.RegionObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SemanticMaskObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.VisibilityState
import kotlin.math.floor

/**
 * Runtime connected-component extraction for a person-confidence mask.
 *
 * The extractor is deliberately non-generative: it thresholds the raw confidence field, labels
 * existing 8-connected foreground only, and never performs dilation, closing or component joining.
 * Raw confidence values remain available in [Extraction.rawMask]. [Extraction.filteredMask] keeps
 * only pixels belonging to components accepted by [BodyMaskComponentGate].
 */
object BodyMaskComponentExtractor {

    const val FINGERPRINT = "POSTAC_MASTER_BODY_MASK_COMPONENT_EXTRACTOR_V1"

    /** Shared with the ML Kit segmentation mapping and silhouette measurement contract. */
    const val FOREGROUND_THRESHOLD = 0.50f

    /** Small raster-only lookup radius; it does not bridge or modify mask components. */
    const val ANCHOR_LOOKUP_RADIUS_PIXELS = 2

    data class ComponentPixels(
        val id: String,
        val label: Int,
        val pixelIndices: IntArray
    )

    data class Extraction(
        val maskId: String,
        val rawMask: ConfidenceMask,
        val filteredMask: ConfidenceMask,
        val assessment: BodyMaskComponentGate.Assessment,
        val components: List<ComponentPixels>,
        val acceptedPixelCount: Int,
        val fingerprint: String = FINGERPRINT
    ) {
        fun applyTo(observation: SubjectObservation): SubjectObservation {
            val originalMask = observation.masks[maskId] ?: return observation
            val values = filteredMask.copyValues()
            val foreground = values.filter { it >= FOREGROUND_THRESHOLD }
            val coverage = foreground.size.toFloat() / values.size
            val meanConfidence = foreground
                .takeIf { it.isNotEmpty() }
                ?.average()
                ?.toFloat()
            val region = observation.regions[maskId]?.copy(
                confidence = meanConfidence,
                confidenceSource = if (meanConfidence == null) {
                    ConfidenceSource.UNAVAILABLE
                } else {
                    ConfidenceSource.DERIVED
                },
                pixelCoverage = coverage
            ) ?: RegionObservation(
                id = maskId,
                confidence = meanConfidence,
                confidenceSource = if (meanConfidence == null) {
                    ConfidenceSource.UNAVAILABLE
                } else {
                    ConfidenceSource.DERIVED
                },
                occlusion = null,
                pixelCoverage = coverage,
                backend = originalMask.backend
            )
            return observation.copy(
                masks = observation.masks + (
                    maskId to SemanticMaskObservation(
                        id = maskId,
                        mask = filteredMask,
                        backend = originalMask.backend
                    )
                    ),
                regions = observation.regions + (maskId to region)
            )
        }
    }

    fun extract(
        observation: SubjectObservation,
        visibility: BodyLandmarkVisibilityGate.Assessment,
        maskId: String = PortraitRegionId.SUBJECT_MASK,
        foregroundThreshold: Float = FOREGROUND_THRESHOLD
    ): Extraction? {
        require(foregroundThreshold in 0f..1f)
        val mask = observation.masks[maskId]?.mask ?: return null
        val values = mask.copyValues()
        val width = mask.width
        val height = mask.height
        val labels = IntArray(values.size) { BACKGROUND_LABEL }
        val componentIndices = mutableListOf<IntArray>()

        values.indices.forEach { start ->
            if (values[start] < foregroundThreshold || labels[start] != BACKGROUND_LABEL) {
                return@forEach
            }
            val label = componentIndices.size
            val queue = IntArray(values.size)
            val collected = IntArray(values.size)
            var read = 0
            var write = 0
            var count = 0
            queue[write++] = start
            labels[start] = label
            while (read < write) {
                val index = queue[read++]
                collected[count++] = index
                val x = index % width
                val y = index / width
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        if (dx == 0 && dy == 0) continue
                        val nextX = x + dx
                        val nextY = y + dy
                        if (nextX !in 0 until width || nextY !in 0 until height) continue
                        val next = nextY * width + nextX
                        if (
                            labels[next] == BACKGROUND_LABEL &&
                            values[next] >= foregroundThreshold
                        ) {
                            labels[next] = label
                            queue[write++] = next
                        }
                    }
                }
            }
            componentIndices += collected.copyOf(count)
        }

        val verifiedHeadTop = observation.landmarks.values
            .filter { landmark ->
                landmark.backend == ObservationBackend.ML_KIT_POSE &&
                    landmark.id in HEAD_LANDMARK_IDS &&
                    landmark.visibility == VisibilityState.VISIBLE &&
                    landmark.confidence != null &&
                    landmark.confidence >= BodyLandmarkVisibilityGate.MINIMUM_IN_FRAME_CONFIDENCE &&
                    landmark.point.x in 0f..1f && landmark.point.y in 0f..1f
            }
            .minOfOrNull { it.point.y }

        val componentEvidence = componentIndices.mapIndexed { label, indices ->
            val id = "mask_component_$label"
            var minX = width
            var minY = height
            var maxX = 0
            var maxY = 0
            indices.forEach { index ->
                val x = index % width
                val y = index / width
                minX = minOf(minX, x)
                minY = minOf(minY, y)
                maxX = maxOf(maxX, x)
                maxY = maxOf(maxY, y)
            }
            val bounds = BodyMaskComponentGate.NormalizedBounds(
                left = minX.toFloat() / width,
                top = minY.toFloat() / height,
                right = (maxX + 1).toFloat() / width,
                bottom = (maxY + 1).toFloat() / height
            )
            val torsoAnchorCount = TORSO_LANDMARK_IDS.count { landmarkId ->
                val evidence = visibility.landmarks[landmarkId]
                evidence?.usable == true && evidence.rawLandmark?.point?.let { point ->
                    labelAt(point.x, point.y, labels, width, height) == label
                } == true
            }
            val independentPersonAnchorCount = observation.landmarks.values.count { landmark ->
                landmark.backend == ObservationBackend.ML_KIT_POSE &&
                    landmark.visibility == VisibilityState.VISIBLE &&
                    landmark.confidence != null &&
                    landmark.confidence >= BodyLandmarkVisibilityGate.MINIMUM_IN_FRAME_CONFIDENCE &&
                    labelAt(landmark.point.x, landmark.point.y, labels, width, height) == label
            }
            BodyMaskComponentGate.ComponentEvidence(
                id = id,
                pixelCount = indices.size,
                bounds = bounds,
                torsoAnchorCount = torsoAnchorCount,
                overlapsTorsoCorridor = torsoAnchorCount >= MINIMUM_TORSO_ANCHORS,
                connectedToPrimary = false,
                aboveVerifiedHead = verifiedHeadTop != null && bounds.bottom <= verifiedHeadTop,
                independentPersonAnchorCount = independentPersonAnchorCount
            )
        }

        val assessment = BodyMaskComponentGate.evaluate(componentEvidence)
        val acceptedIds = assessment.filteredComponents.mapTo(hashSetOf()) { it.id }
        val components = componentIndices.mapIndexed { label, indices ->
            ComponentPixels(
                id = "mask_component_$label",
                label = label,
                pixelIndices = indices
            )
        }
        val acceptedLabels = components
            .filter { it.id in acceptedIds }
            .mapTo(hashSetOf()) { it.label }
        val filteredValues = FloatArray(values.size) { index ->
            if (labels[index] in acceptedLabels) values[index] else 0f
        }
        return Extraction(
            maskId = maskId,
            rawMask = mask,
            filteredMask = ConfidenceMask(width, height, filteredValues),
            assessment = assessment,
            components = components,
            acceptedPixelCount = filteredValues.count { it >= foregroundThreshold }
        )
    }

    private fun labelAt(
        normalizedX: Float,
        normalizedY: Float,
        labels: IntArray,
        width: Int,
        height: Int
    ): Int? {
        if (!normalizedX.isFinite() || !normalizedY.isFinite()) return null
        if (normalizedX !in 0f..1f || normalizedY !in 0f..1f) return null
        val centerX = floor(normalizedX * width).toInt().coerceIn(0, width - 1)
        val centerY = floor(normalizedY * height).toInt().coerceIn(0, height - 1)
        for (radius in 0..ANCHOR_LOOKUP_RADIUS_PIXELS) {
            for (dy in -radius..radius) {
                for (dx in -radius..radius) {
                    val x = centerX + dx
                    val y = centerY + dy
                    if (x !in 0 until width || y !in 0 until height) continue
                    val label = labels[y * width + x]
                    if (label != BACKGROUND_LABEL) return label
                }
            }
        }
        return null
    }

    private const val BACKGROUND_LABEL = -1
    private const val MINIMUM_TORSO_ANCHORS = 2

    private val TORSO_LANDMARK_IDS = setOf(
        PortraitLandmarkId.LEFT_SHOULDER,
        PortraitLandmarkId.RIGHT_SHOULDER,
        PortraitLandmarkId.LEFT_HIP,
        PortraitLandmarkId.RIGHT_HIP
    )

    private val HEAD_LANDMARK_IDS = setOf(
        "nose",
        "left_eye_inner",
        "left_eye",
        "left_eye_outer",
        "right_eye_inner",
        "right_eye",
        "right_eye_outer",
        "left_ear",
        "right_ear",
        "left_mouth",
        "right_mouth"
    )
}
