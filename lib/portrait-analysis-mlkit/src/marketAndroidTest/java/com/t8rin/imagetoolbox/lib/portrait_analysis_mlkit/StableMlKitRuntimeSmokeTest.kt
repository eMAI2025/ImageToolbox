/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis_mlkit

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import java.util.concurrent.TimeUnit
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device-only smoke test for the stable P0 detector set.
 *
 * ML Kit Face Mesh is intentionally excluded until its beta MediaPipe-internal dependency is
 * proven compatible with the complete application runtime.
 */
@RunWith(AndroidJUnit4::class)
class StableMlKitRuntimeSmokeTest {

    @Test
    fun initializesProcessesClosesAndRepeatsFiveTimes() {
        val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val input = InputImage.fromBitmap(bitmap, 0)

        repeat(5) {
            val faceDetector = FaceDetection.getClient(
                FaceDetectorOptions.Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                    .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                    .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
                    .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                    .build()
            )
            val poseDetector = PoseDetection.getClient(
                AccuratePoseDetectorOptions.Builder()
                    .setDetectorMode(AccuratePoseDetectorOptions.SINGLE_IMAGE_MODE)
                    .build()
            )
            val segmenter = Segmentation.getClient(
                SelfieSegmenterOptions.Builder()
                    .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
                    .enableRawSizeMask()
                    .build()
            )

            try {
                Tasks.await(faceDetector.process(input), TIMEOUT_SECONDS, TimeUnit.SECONDS)
                Tasks.await(poseDetector.process(input), TIMEOUT_SECONDS, TimeUnit.SECONDS)
                Tasks.await(segmenter.process(input), TIMEOUT_SECONDS, TimeUnit.SECONDS)
            } finally {
                faceDetector.close()
                poseDetector.close()
                segmenter.close()
            }
        }

        bitmap.recycle()
    }

    private companion object {
        const val TIMEOUT_SECONDS = 30L
    }
}
