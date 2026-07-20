/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis_mediapipe

import com.google.mediapipe.framework.image.MPImage

/** Immutable MediaPipe image input for still-image Stage A1 benchmarks. */
data class MediaPipeImageInput(
    val image: MPImage
)
