/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

@file:Suppress("PropertyName")

package com.t8rin.imagetoolbox.lib.portrait_analysis.derive

import com.t8rin.imagetoolbox.lib.portrait_analysis.catalog.P2FaceLandmarkId
import com.t8rin.imagetoolbox.lib.portrait_analysis.catalog.P2FaceRegionId
import com.t8rin.imagetoolbox.lib.portrait_analysis.catalog.PortraitLandmarkId
import com.t8rin.imagetoolbox.lib.portrait_analysis.catalog.PortraitRegionId

val PortraitRegionId.FULL_FACE_REGION: String get() = P2FaceRegionId.FULL_FACE_REGION
val PortraitRegionId.IMAGE_LEFT_FACE_HALF: String get() = P2FaceRegionId.IMAGE_LEFT_FACE_HALF
val PortraitRegionId.IMAGE_RIGHT_FACE_HALF: String get() = P2FaceRegionId.IMAGE_RIGHT_FACE_HALF
val PortraitRegionId.LEFT_BROW_REGION: String get() = P2FaceRegionId.LEFT_BROW_REGION
val PortraitRegionId.RIGHT_BROW_REGION: String get() = P2FaceRegionId.RIGHT_BROW_REGION
val PortraitRegionId.NOSE_REGION: String get() = P2FaceRegionId.NOSE_REGION
val PortraitRegionId.LEFT_CHEEK_REGION: String get() = P2FaceRegionId.LEFT_CHEEK_REGION
val PortraitRegionId.RIGHT_CHEEK_REGION: String get() = P2FaceRegionId.RIGHT_CHEEK_REGION
val PortraitRegionId.LEFT_JAW_REGION: String get() = P2FaceRegionId.LEFT_JAW_REGION
val PortraitRegionId.RIGHT_JAW_REGION: String get() = P2FaceRegionId.RIGHT_JAW_REGION
val PortraitRegionId.SUBCHIN_REGION: String get() = P2FaceRegionId.SUBCHIN_REGION

val PortraitLandmarkId.IMAGE_LEFT_JAW_ANCHOR: String
    get() = P2FaceLandmarkId.IMAGE_LEFT_JAW_ANCHOR
val PortraitLandmarkId.IMAGE_RIGHT_JAW_ANCHOR: String
    get() = P2FaceLandmarkId.IMAGE_RIGHT_JAW_ANCHOR
val PortraitLandmarkId.IMAGE_LEFT_CHIN_ANCHOR: String
    get() = P2FaceLandmarkId.IMAGE_LEFT_CHIN_ANCHOR
val PortraitLandmarkId.IMAGE_RIGHT_CHIN_ANCHOR: String
    get() = P2FaceLandmarkId.IMAGE_RIGHT_CHIN_ANCHOR
