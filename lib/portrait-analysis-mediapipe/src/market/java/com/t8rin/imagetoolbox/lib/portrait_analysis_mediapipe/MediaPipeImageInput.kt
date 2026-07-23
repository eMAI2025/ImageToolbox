/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis_mediapipe

import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage

/**
 * Opaque MediaPipe input for still-image Stage A1 benchmarks.
 *
 * Vendor SDK types remain internal to the adapter module so feature modules do not need direct
 * MediaPipe dependencies.
 */
class MediaPipeImageInput internal constructor(
    internal val image: MPImage
)

suspend fun <T> Bitmap.withMediaPipeImageInput(
    block: suspend (MediaPipeImageInput) -> T
): T {
    val image = BitmapImageBuilder(this).build()
    return try {
        block(MediaPipeImageInput(image))
    } finally {
        image.close()
    }
}
