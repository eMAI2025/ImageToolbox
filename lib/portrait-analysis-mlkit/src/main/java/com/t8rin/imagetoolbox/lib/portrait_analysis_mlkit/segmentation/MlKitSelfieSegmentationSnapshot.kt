/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis_mlkit.segmentation

import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ConfidenceMask
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ConfidenceSource
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ObservationBackend
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.RegionObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SemanticMaskObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation

data class MlKitSelfieSegmentationSnapshot(
    val maskWidth: Int,
    val maskHeight: Int,
    val personConfidence: FloatArray
) {
    init {
        require(maskWidth > 0) { "maskWidth must be positive" }
        require(maskHeight > 0) { "maskHeight must be positive" }
        require(personConfidence.size == maskWidth * maskHeight) {
            "personConfidence must contain one value per mask pixel"
        }
        require(personConfidence.all { it in 0f..1f }) {
            "personConfidence values must be in 0f..1f"
        }
    }

    override fun equals(other: Any?): Boolean =
        other is MlKitSelfieSegmentationSnapshot &&
            maskWidth == other.maskWidth &&
            maskHeight == other.maskHeight &&
            personConfidence.contentEquals(other.personConfidence)

    override fun hashCode(): Int {
        var result = maskWidth
        result = 31 * result + maskHeight
        result = 31 * result + personConfidence.contentHashCode()
        return result
    }
}

data class MlKitSelfieSegmentationPolicy(
    val foregroundThreshold: Float = 0.50f,
    val minimumSubjectCoverage: Float = 0.005f
) {
    init {
        require(foregroundThreshold in 0f..1f)
        require(minimumSubjectCoverage in 0f..1f)
    }
}

object MlKitSelfieSegmentationObservationMapper {
    const val SUBJECT_MASK_REGION_ID = "subject_mask"

    fun map(
        snapshot: MlKitSelfieSegmentationSnapshot,
        policy: MlKitSelfieSegmentationPolicy = MlKitSelfieSegmentationPolicy()
    ): SubjectObservation {
        val foreground = snapshot.personConfidence.filter {
            it >= policy.foregroundThreshold
        }
        val coverage = foreground.size.toFloat() / snapshot.personConfidence.size
        val meanForegroundConfidence = foreground
            .takeIf { it.isNotEmpty() }
            ?.average()
            ?.toFloat()

        val subjectDetected = coverage >= policy.minimumSubjectCoverage
        val region = RegionObservation(
            id = SUBJECT_MASK_REGION_ID,
            confidence = meanForegroundConfidence,
            confidenceSource = if (meanForegroundConfidence == null) {
                ConfidenceSource.UNAVAILABLE
            } else {
                ConfidenceSource.DERIVED
            },
            occlusion = null,
            pixelCoverage = coverage,
            backend = ObservationBackend.ML_KIT_SELFIE_SEGMENTATION
        )
        val mask = SemanticMaskObservation(
            id = SUBJECT_MASK_REGION_ID,
            mask = ConfidenceMask(
                width = snapshot.maskWidth,
                height = snapshot.maskHeight,
                confidenceValues = snapshot.personConfidence
            ),
            backend = ObservationBackend.ML_KIT_SELFIE_SEGMENTATION
        )

        return SubjectObservation(
            subjectCount = if (subjectDetected) 1 else 0,
            faceCount = 0,
            bodyCount = 0,
            landmarks = emptyMap(),
            regions = mapOf(SUBJECT_MASK_REGION_ID to region),
            masks = mapOf(SUBJECT_MASK_REGION_ID to mask)
        )
    }
}
