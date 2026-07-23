/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis_mlkit.segmentation

import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.Segmenter
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import com.t8rin.imagetoolbox.lib.portrait_analysis.engine.PortraitObservationEngine
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ObservationBackend
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis_mlkit.MlKitImageInput
import com.t8rin.imagetoolbox.lib.portrait_analysis_mlkit.awaitResult
import java.nio.ByteOrder

/** Observation-only selfie segmentation backend for the Market build. */
class MlKitSelfieSegmentationObservationEngine(
    private val segmenter: Segmenter = Segmentation.getClient(
        SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
            .enableRawSizeMask()
            .build()
    ),
    private val mappingPolicy: MlKitSelfieSegmentationPolicy =
        MlKitSelfieSegmentationPolicy()
) : PortraitObservationEngine<MlKitImageInput>, AutoCloseable {

    override val backend: ObservationBackend =
        ObservationBackend.ML_KIT_SELFIE_SEGMENTATION

    override suspend fun observe(input: MlKitImageInput): SubjectObservation {
        val mask = segmenter.process(input.image).awaitResult()
        val floatBuffer = mask.buffer
            .duplicate()
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        val confidence = FloatArray(floatBuffer.remaining())
        floatBuffer.get(confidence)

        return MlKitSelfieSegmentationObservationMapper.map(
            snapshot = MlKitSelfieSegmentationSnapshot(
                maskWidth = mask.width,
                maskHeight = mask.height,
                personConfidence = confidence
            ),
            policy = mappingPolicy
        )
    }

    override fun close() {
        segmenter.close()
    }
}
