/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis_mlkit

import com.google.mlkit.vision.common.InputImage

/**
 * ML Kit input plus authoritative dimensions of the coordinate space returned by detectors.
 */
data class MlKitImageInput(
    val image: InputImage,
    val width: Int,
    val height: Int
) {
    init {
        require(width > 0) { "width must be positive" }
        require(height > 0) { "height must be positive" }
    }
}
