/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.feature.portrait_lab

enum class VisualProofStage(
    val label: String,
    val visibility: PortraitOverlayVisibility,
    val controlPointsOnly: Boolean = false
) {
    SOURCE(
        label = "1. Image",
        visibility = PortraitOverlayVisibility.none()
    ),
    BOUNDING_BOX(
        label = "2. Bounding box",
        visibility = PortraitOverlayVisibility(
            boundingBox = true,
            points = false,
            contours = false,
            mesh = false,
            masks = false
        )
    ),
    FIVE_POINTS(
        label = "3. Five points",
        visibility = PortraitOverlayVisibility(
            boundingBox = true,
            points = true,
            contours = false,
            mesh = false,
            masks = false
        ),
        controlPointsOnly = true
    ),
    CONTOURS(
        label = "4. Contours",
        visibility = PortraitOverlayVisibility(
            boundingBox = true,
            points = true,
            contours = true,
            mesh = false,
            masks = false
        )
    ),
    FULL_MESH(
        label = "5. Full mesh",
        visibility = PortraitOverlayVisibility(
            boundingBox = true,
            points = true,
            contours = true,
            mesh = true,
            masks = false
        )
    );

    companion object {
        fun fromVisibility(visibility: PortraitOverlayVisibility): VisualProofStage? =
            entries.firstOrNull { it.visibility == visibility && !it.controlPointsOnly }
    }
}
