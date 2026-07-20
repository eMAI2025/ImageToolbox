/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis_mlkit.segmentation

import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ConfidenceSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MlKitSelfieSegmentationObservationMapperTest {

    @Test
    fun `derives subject coverage and foreground confidence`() {
        val result = MlKitSelfieSegmentationObservationMapper.map(
            MlKitSelfieSegmentationSnapshot(
                maskWidth = 2,
                maskHeight = 2,
                personConfidence = floatArrayOf(0.1f, 0.8f, 0.9f, 0.2f)
            )
        )

        val region = result.regions.getValue(
            MlKitSelfieSegmentationObservationMapper.SUBJECT_MASK_REGION_ID
        )
        assertEquals(0.5f, region.pixelCoverage)
        assertEquals(0.85f, region.confidence!!, 0.0001f)
        assertEquals(ConfidenceSource.DERIVED, region.confidenceSource)
        assertNull(region.occlusion)
        assertEquals(1, result.subjectCount)
    }

    @Test
    fun `empty foreground does not invent confidence or subject`() {
        val result = MlKitSelfieSegmentationObservationMapper.map(
            MlKitSelfieSegmentationSnapshot(
                maskWidth = 2,
                maskHeight = 2,
                personConfidence = floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f)
            )
        )

        val region = result.regions.getValue(
            MlKitSelfieSegmentationObservationMapper.SUBJECT_MASK_REGION_ID
        )
        assertNull(region.confidence)
        assertEquals(ConfidenceSource.UNAVAILABLE, region.confidenceSource)
        assertEquals(0, result.subjectCount)
    }
}
