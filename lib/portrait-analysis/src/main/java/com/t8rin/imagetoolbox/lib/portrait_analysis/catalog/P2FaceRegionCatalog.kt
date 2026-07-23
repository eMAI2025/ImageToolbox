/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis.catalog

import com.t8rin.imagetoolbox.lib.portrait_analysis.gate.ParameterGateSpec

object P2FaceLandmarkId {
    const val IMAGE_LEFT_JAW_ANCHOR = "image_left_jaw_anchor"
    const val IMAGE_RIGHT_JAW_ANCHOR = "image_right_jaw_anchor"
    const val IMAGE_LEFT_CHIN_ANCHOR = "image_left_chin_anchor"
    const val IMAGE_RIGHT_CHIN_ANCHOR = "image_right_chin_anchor"
    const val CHIN_CENTER = "chin_center"
    const val LOWER_LIP_CENTER = "lower_lip_center"
    const val CHIN_DEPTH_REFERENCE = "chin_depth_reference"
}

object P2FaceRegionId {
    const val FULL_FACE_REGION = "full_face_region"
    const val IMAGE_LEFT_FACE_HALF = "image_left_face_half"
    const val IMAGE_RIGHT_FACE_HALF = "image_right_face_half"
    const val LEFT_EYE_REGION = "left_eye_region"
    const val RIGHT_EYE_REGION = "right_eye_region"
    const val LEFT_BROW_REGION = "left_brow_region"
    const val RIGHT_BROW_REGION = "right_brow_region"
    const val NOSE_REGION = "nose_region"
    const val MOUTH_REGION = "mouth_region"
    const val LEFT_CHEEK_REGION = "left_cheek_region"
    const val RIGHT_CHEEK_REGION = "right_cheek_region"
    const val LEFT_JAW_REGION = "left_jaw_region"
    const val RIGHT_JAW_REGION = "right_jaw_region"
    const val CHIN_REGION = "chin_region"
    const val SUBCHIN_REGION = "subchin_region"
}

object P2FaceParameterId {
    const val IMAGE_LEFT_JAW_WIDTH = "image_left_jaw_width"
    const val IMAGE_RIGHT_JAW_WIDTH = "image_right_jaw_width"
    const val CHIN_WIDTH = "chin_width"
    const val CHIN_HEIGHT = "chin_height"
    const val CHIN_SHIFT_X = "chin_shift_x"
    const val CHIN_SHIFT_Y = "chin_shift_y"
    const val CHIN_PROJECTION = "chin_projection"
    const val SUBCHIN_SOFTNESS = "subchin_softness"
}

enum class P2FaceControlScope {
    IMAGE_LEFT_SIDE,
    IMAGE_RIGHT_SIDE,
    CHIN,
    SUBCHIN
}

data class P2FaceParameterDefinition(
    val id: String,
    val scope: P2FaceControlScope,
    val gateSpec: ParameterGateSpec
) {
    init {
        require(id == gateSpec.parameterId)
    }
}

/**
 * Declarative P2 controls. These definitions only decide whether a future isolated operation may
 * be exposed. No deformation is implemented here.
 */
object P2FaceRegionParameterCatalog {

    val definitions: List<P2FaceParameterDefinition> = listOf(
        sideJawDefinition(
            id = P2FaceParameterId.IMAGE_LEFT_JAW_WIDTH,
            scope = P2FaceControlScope.IMAGE_LEFT_SIDE,
            jawAnchor = P2FaceLandmarkId.IMAGE_LEFT_JAW_ANCHOR,
            region = P2FaceRegionId.LEFT_JAW_REGION
        ),
        sideJawDefinition(
            id = P2FaceParameterId.IMAGE_RIGHT_JAW_WIDTH,
            scope = P2FaceControlScope.IMAGE_RIGHT_SIDE,
            jawAnchor = P2FaceLandmarkId.IMAGE_RIGHT_JAW_ANCHOR,
            region = P2FaceRegionId.RIGHT_JAW_REGION
        ),
        chinDefinition(
            id = P2FaceParameterId.CHIN_WIDTH,
            landmarks = setOf(
                P2FaceLandmarkId.IMAGE_LEFT_CHIN_ANCHOR,
                P2FaceLandmarkId.CHIN_CENTER,
                P2FaceLandmarkId.IMAGE_RIGHT_CHIN_ANCHOR
            )
        ),
        chinDefinition(
            id = P2FaceParameterId.CHIN_HEIGHT,
            landmarks = setOf(
                P2FaceLandmarkId.LOWER_LIP_CENTER,
                P2FaceLandmarkId.CHIN_CENTER
            )
        ),
        chinDefinition(
            id = P2FaceParameterId.CHIN_SHIFT_X,
            landmarks = setOf(
                P2FaceLandmarkId.IMAGE_LEFT_CHIN_ANCHOR,
                P2FaceLandmarkId.CHIN_CENTER,
                P2FaceLandmarkId.IMAGE_RIGHT_CHIN_ANCHOR
            )
        ),
        chinDefinition(
            id = P2FaceParameterId.CHIN_SHIFT_Y,
            landmarks = setOf(
                P2FaceLandmarkId.LOWER_LIP_CENTER,
                P2FaceLandmarkId.CHIN_CENTER
            )
        ),
        chinDefinition(
            id = P2FaceParameterId.CHIN_PROJECTION,
            landmarks = setOf(
                P2FaceLandmarkId.CHIN_CENTER,
                P2FaceLandmarkId.CHIN_DEPTH_REFERENCE
            )
        ),
        P2FaceParameterDefinition(
            id = P2FaceParameterId.SUBCHIN_SOFTNESS,
            scope = P2FaceControlScope.SUBCHIN,
            gateSpec = ParameterGateSpec(
                parameterId = P2FaceParameterId.SUBCHIN_SOFTNESS,
                requiredRegionIds = setOf(P2FaceRegionId.SUBCHIN_REGION),
                minimumRegionConfidence = 0.90f,
                maximumRegionOcclusion = 0.10f,
                minimumRegionPixelCoverage = 0.003f,
                minimumSubjects = 1,
                maximumSubjects = 1,
                minimumFaces = 1,
                maximumFaces = 1,
                maximumAbsoluteYawDegrees = 75f,
                maximumAbsolutePitchDegrees = 35f,
                maximumAbsoluteRollDegrees = 40f
            )
        )
    ).also { definitions ->
        require(definitions.map { it.id }.distinct().size == definitions.size)
    }

    val gateSpecs: List<ParameterGateSpec>
        get() = definitions.map { it.gateSpec }

    private fun sideJawDefinition(
        id: String,
        scope: P2FaceControlScope,
        jawAnchor: String,
        region: String
    ) = P2FaceParameterDefinition(
        id = id,
        scope = scope,
        gateSpec = ParameterGateSpec(
            parameterId = id,
            requiredLandmarkIds = setOf(jawAnchor, P2FaceLandmarkId.CHIN_CENTER),
            requiredRegionIds = setOf(region),
            minimumLandmarkConfidence = 0.60f,
            minimumRegionConfidence = 0.70f,
            maximumRegionOcclusion = 0.45f,
            minimumRegionPixelCoverage = 0.004f,
            minimumSubjects = 1,
            maximumSubjects = 1,
            minimumFaces = 1,
            maximumFaces = 1,
            maximumAbsoluteYawDegrees = 75f,
            maximumAbsolutePitchDegrees = 35f,
            maximumAbsoluteRollDegrees = 40f
        )
    )

    private fun chinDefinition(
        id: String,
        landmarks: Set<String>
    ) = P2FaceParameterDefinition(
        id = id,
        scope = P2FaceControlScope.CHIN,
        gateSpec = ParameterGateSpec(
            parameterId = id,
            requiredLandmarkIds = landmarks,
            requiredRegionIds = setOf(P2FaceRegionId.CHIN_REGION),
            minimumLandmarkConfidence = 0.60f,
            minimumRegionConfidence = 0.65f,
            maximumRegionOcclusion = 0.45f,
            minimumRegionPixelCoverage = 0.003f,
            minimumSubjects = 1,
            maximumSubjects = 1,
            minimumFaces = 1,
            maximumFaces = 1,
            maximumAbsoluteYawDegrees = 75f,
            maximumAbsolutePitchDegrees = 35f,
            maximumAbsoluteRollDegrees = 40f
        )
    )
}
