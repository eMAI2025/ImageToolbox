/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis_mlkit

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage

/**
 * Opaque ML Kit input plus authoritative dimensions of the detector coordinate space.
 *
 * Vendor SDK types remain internal to the adapter module so feature modules do not need direct
 * ML Kit dependencies.
 */
class MlKitImageInput internal constructor(
    internal val image: InputImage,
    val width: Int,
    val height: Int
) {
    init {
        require(width > 0) { "width must be positive" }
        require(height > 0) { "height must be positive" }
    }

    companion object {
        fun fromBitmap(bitmap: Bitmap): MlKitImageInput = MlKitImageInput(
            image = InputImage.fromBitmap(bitmap, 0),
            width = bitmap.width,
            height = bitmap.height
        )
    }
}
