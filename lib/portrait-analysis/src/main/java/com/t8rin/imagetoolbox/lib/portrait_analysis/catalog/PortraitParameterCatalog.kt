/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis.catalog

import com.t8rin.imagetoolbox.lib.portrait_analysis.gate.ParameterGateSpec

enum class PortraitParameterCategory {
    FACE_GEOMETRY,
    FACE_APPEARANCE,
    BODY_GEOMETRY,
    BODY_PROPORTION
}

enum class PortraitParameterDirection {
    UNIDIRECTIONAL,
    BIDIRECTIONAL,
    LOCAL_OPERATION
}

enum class PortraitParameterRisk {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

data class PortraitParameterDefinition(
    val id: String,
    val displayNameKey: String,
    val category: PortraitParameterCategory,
    val direction: PortraitParameterDirection,
    val risk: PortraitParameterRisk,
    val gateSpec: ParameterGateSpec
) {
    init {
        require(id.isNotBlank()) { "id cannot be blank" }
        require(displayNameKey.isNotBlank()) { "displayNameKey cannot be blank" }
        require(id == gateSpec.parameterId) {
            "Definition id and gate parameter id must match"
        }
    }
}

object PortraitLandmarkId {
    const val MOUTH_LEFT_CORNER = "mouth_left_corner"
    const val MOUTH_RIGHT_CORNER = "mouth_right_corner"
    const val UPPER_LIP_MID = "upper_lip_mid"
    const val LOWER_LIP_MID = "lower_lip_mid"
    const val MOUTH_CENTER = "mouth_center"
    const val LEFT_EYE_CENTER = "left_eye_center"
    const val RIGHT_EYE_CENTER = "right_eye_center"
    const val LEFT_MANDIBLE_ANGLE = "left_mandible_angle"
    const val RIGHT_MANDIBLE_ANGLE = "right_mandible_angle"
    const val CHIN_LEFT = "chin_left"
    const val CHIN_CENTER = "chin_center"
    const val CHIN_RIGHT = "chin_right"
    const val LOWER_LIP_CENTER = "lower_lip_center"

    const val LEFT_SHOULDER = "left_shoulder"
    const val RIGHT_SHOULDER = "right_shoulder"
    const val LEFT_ELBOW = "left_elbow"
    const val RIGHT_ELBOW = "right_elbow"
    const val LEFT_WRIST = "left_wrist"
    const val RIGHT_WRIST = "right_wrist"
    const val LEFT_HIP = "left_hip"
    const val RIGHT_HIP = "right_hip"
    const val LEFT_KNEE = "left_knee"
    const val RIGHT_KNEE = "right_knee"
    const val LEFT_ANKLE = "left_ankle"
    const val RIGHT_ANKLE = "right_ankle"
}

object PortraitRegionId {
    const val SUBJECT_MASK = "subject_mask"
    const val MOUTH_REGION = "mouth_region"
    const val LIPS_OCCLUSION_MASK = "lips_occlusion_mask"
    const val VISIBLE_TEETH_REGION = "visible_teeth_region"
    const val LEFT_EYE_REGION = "left_eye_region"
    const val RIGHT_EYE_REGION = "right_eye_region"
    const val FACE_SKIN_REGION = "face_skin_region"
    const val FACE_OCCLUSION_MASK = "face_occlusion_mask"
    const val FACE_CONTOUR = "face_contour"
    const val CHIN_REGION = "chin_region"
    const val SHOULDER_CONTOUR = "shoulder_contour"
    const val ARM_CONTOURS = "arm_contours"
    const val WAIST_CONTOUR = "waist_contour"
    const val HIP_CONTOUR = "hip_contour"
    const val LEG_CONTOURS = "leg_contours"
}

object PortraitParameterId {
    const val MOUTH_CORNER_CURVE = "mouth_corner_curve"
    const val EYE_BRIGHTNESS = "eye_brightness"
    const val TEETH_WHITENESS = "teeth_whiteness"
    const val SKIN_SMOOTHING = "skin_smoothing"
    const val LOWER_JAW_WIDTH = "lower_jaw_width"
    const val JAWLINE_SHAPE = "jawline_shape"
    const val CHIN_V_SHAPE = "chin_v_shape"
    const val SHOULDER_WIDTH = "shoulder_width"
    const val ARM_WIDTH = "arm_width"
    const val WAIST_WIDTH = "waist_width"
    const val HIP_WIDTH = "hip_width"
    const val LEG_WIDTH = "leg_width"
    const val LEG_LENGTH = "leg_length"
}

/**
 * Initial non-personal parameter catalog for Stage A1.
 *
 * Thresholds are benchmark defaults, not personal calibration values. Geometry execution remains
 * blocked until Stage A is accepted and every parameter passes an isolated Stage B test.
 */
object DefaultPortraitParameterCatalog {

    val definitions: List<PortraitParameterDefinition> = listOf(
        faceDefinition(
            id = PortraitParameterId.MOUTH_CORNER_CURVE,
            category = PortraitParameterCategory.FACE_GEOMETRY,
            direction = PortraitParameterDirection.BIDIRECTIONAL,
            risk = PortraitParameterRisk.HIGH,
            landmarks = setOf(
                PortraitLandmarkId.MOUTH_LEFT_CORNER,
                PortraitLandmarkId.MOUTH_RIGHT_CORNER,
                PortraitLandmarkId.UPPER_LIP_MID,
                PortraitLandmarkId.LOWER_LIP_MID,
                PortraitLandmarkId.MOUTH_CENTER
            ),
            regions = setOf(
                PortraitRegionId.MOUTH_REGION,
                PortraitRegionId.LIPS_OCCLUSION_MASK
            ),
            maximumYaw = 30f,
            maximumPitch = 20f,
            maximumRoll = 25f,
            minimumCoverage = 0.002f
        ),
        faceDefinition(
            id = PortraitParameterId.EYE_BRIGHTNESS,
            category = PortraitParameterCategory.FACE_APPEARANCE,
            direction = PortraitParameterDirection.UNIDIRECTIONAL,
            risk = PortraitParameterRisk.MEDIUM,
            landmarks = setOf(
                PortraitLandmarkId.LEFT_EYE_CENTER,
                PortraitLandmarkId.RIGHT_EYE_CENTER
            ),
            regions = setOf(
                PortraitRegionId.LEFT_EYE_REGION,
                PortraitRegionId.RIGHT_EYE_REGION
            ),
            maximumYaw = 35f,
            maximumPitch = 25f,
            maximumRoll = 30f,
            minimumCoverage = 0.001f
        ),
        faceDefinition(
            id = PortraitParameterId.TEETH_WHITENESS,
            category = PortraitParameterCategory.FACE_APPEARANCE,
            direction = PortraitParameterDirection.UNIDIRECTIONAL,
            risk = PortraitParameterRisk.MEDIUM,
            landmarks = setOf(
                PortraitLandmarkId.MOUTH_LEFT_CORNER,
                PortraitLandmarkId.MOUTH_RIGHT_CORNER
            ),
            regions = setOf(
                PortraitRegionId.VISIBLE_TEETH_REGION,
                PortraitRegionId.MOUTH_REGION,
                PortraitRegionId.LIPS_OCCLUSION_MASK
            ),
            maximumYaw = 35f,
            maximumPitch = 25f,
            maximumRoll = 30f,
            minimumCoverage = 0.0005f,
            maximumOcclusion = 0.10f
        ),
        faceDefinition(
            id = PortraitParameterId.SKIN_SMOOTHING,
            category = PortraitParameterCategory.FACE_APPEARANCE,
            direction = PortraitParameterDirection.UNIDIRECTIONAL,
            risk = PortraitParameterRisk.MEDIUM,
            landmarks = emptySet(),
            regions = setOf(
                PortraitRegionId.FACE_SKIN_REGION,
                PortraitRegionId.FACE_OCCLUSION_MASK
            ),
            maximumYaw = 45f,
            maximumPitch = 35f,
            maximumRoll = 35f,
            minimumCoverage = 0.02f,
            minimumRegionConfidence = 0.85f
        ),
        faceDefinition(
            id = PortraitParameterId.LOWER_JAW_WIDTH,
            category = PortraitParameterCategory.FACE_GEOMETRY,
            direction = PortraitParameterDirection.BIDIRECTIONAL,
            risk = PortraitParameterRisk.CRITICAL,
            landmarks = setOf(
                PortraitLandmarkId.LEFT_MANDIBLE_ANGLE,
                PortraitLandmarkId.RIGHT_MANDIBLE_ANGLE,
                PortraitLandmarkId.CHIN_CENTER
            ),
            regions = setOf(
                PortraitRegionId.FACE_CONTOUR,
                PortraitRegionId.FACE_OCCLUSION_MASK
            ),
            maximumYaw = 20f,
            maximumPitch = 20f,
            maximumRoll = 20f,
            minimumCoverage = 0.03f,
            minimumLandmarkConfidence = 0.94f
        ),
        faceDefinition(
            id = PortraitParameterId.JAWLINE_SHAPE,
            category = PortraitParameterCategory.FACE_GEOMETRY,
            direction = PortraitParameterDirection.BIDIRECTIONAL,
            risk = PortraitParameterRisk.CRITICAL,
            landmarks = setOf(
                PortraitLandmarkId.LEFT_MANDIBLE_ANGLE,
                PortraitLandmarkId.RIGHT_MANDIBLE_ANGLE,
                PortraitLandmarkId.CHIN_LEFT,
                PortraitLandmarkId.CHIN_CENTER,
                PortraitLandmarkId.CHIN_RIGHT
            ),
            regions = setOf(
                PortraitRegionId.FACE_CONTOUR,
                PortraitRegionId.CHIN_REGION,
                PortraitRegionId.FACE_OCCLUSION_MASK
            ),
            maximumYaw = 20f,
            maximumPitch = 20f,
            maximumRoll = 20f,
            minimumCoverage = 0.03f,
            minimumLandmarkConfidence = 0.94f
        ),
        faceDefinition(
            id = PortraitParameterId.CHIN_V_SHAPE,
            category = PortraitParameterCategory.FACE_GEOMETRY,
            direction = PortraitParameterDirection.UNIDIRECTIONAL,
            risk = PortraitParameterRisk.CRITICAL,
            landmarks = setOf(
                PortraitLandmarkId.CHIN_LEFT,
                PortraitLandmarkId.CHIN_CENTER,
                PortraitLandmarkId.CHIN_RIGHT,
                PortraitLandmarkId.LOWER_LIP_CENTER
            ),
            regions = setOf(
                PortraitRegionId.CHIN_REGION,
                PortraitRegionId.FACE_OCCLUSION_MASK
            ),
            maximumYaw = 20f,
            maximumPitch = 20f,
            maximumRoll = 20f,
            minimumCoverage = 0.01f,
            minimumLandmarkConfidence = 0.94f
        ),
        bodyDefinition(
            id = PortraitParameterId.SHOULDER_WIDTH,
            category = PortraitParameterCategory.BODY_GEOMETRY,
            direction = PortraitParameterDirection.BIDIRECTIONAL,
            risk = PortraitParameterRisk.HIGH,
            landmarks = setOf(
                PortraitLandmarkId.LEFT_SHOULDER,
                PortraitLandmarkId.RIGHT_SHOULDER
            ),
            regions = setOf(
                PortraitRegionId.SUBJECT_MASK,
                PortraitRegionId.SHOULDER_CONTOUR
            )
        ),
        bodyDefinition(
            id = PortraitParameterId.ARM_WIDTH,
            category = PortraitParameterCategory.BODY_GEOMETRY,
            direction = PortraitParameterDirection.BIDIRECTIONAL,
            risk = PortraitParameterRisk.HIGH,
            landmarks = setOf(
                PortraitLandmarkId.LEFT_SHOULDER,
                PortraitLandmarkId.RIGHT_SHOULDER,
                PortraitLandmarkId.LEFT_ELBOW,
                PortraitLandmarkId.RIGHT_ELBOW,
                PortraitLandmarkId.LEFT_WRIST,
                PortraitLandmarkId.RIGHT_WRIST
            ),
            regions = setOf(
                PortraitRegionId.SUBJECT_MASK,
                PortraitRegionId.ARM_CONTOURS
            )
        ),
        bodyDefinition(
            id = PortraitParameterId.WAIST_WIDTH,
            category = PortraitParameterCategory.BODY_GEOMETRY,
            direction = PortraitParameterDirection.BIDIRECTIONAL,
            risk = PortraitParameterRisk.HIGH,
            landmarks = setOf(
                PortraitLandmarkId.LEFT_SHOULDER,
                PortraitLandmarkId.RIGHT_SHOULDER,
                PortraitLandmarkId.LEFT_HIP,
                PortraitLandmarkId.RIGHT_HIP
            ),
            regions = setOf(
                PortraitRegionId.SUBJECT_MASK,
                PortraitRegionId.WAIST_CONTOUR
            )
        ),
        bodyDefinition(
            id = PortraitParameterId.HIP_WIDTH,
            category = PortraitParameterCategory.BODY_GEOMETRY,
            direction = PortraitParameterDirection.BIDIRECTIONAL,
            risk = PortraitParameterRisk.HIGH,
            landmarks = setOf(
                PortraitLandmarkId.LEFT_HIP,
                PortraitLandmarkId.RIGHT_HIP
            ),
            regions = setOf(
                PortraitRegionId.SUBJECT_MASK,
                PortraitRegionId.HIP_CONTOUR
            )
        ),
        bodyDefinition(
            id = PortraitParameterId.LEG_WIDTH,
            category = PortraitParameterCategory.BODY_GEOMETRY,
            direction = PortraitParameterDirection.BIDIRECTIONAL,
            risk = PortraitParameterRisk.HIGH,
            landmarks = setOf(
                PortraitLandmarkId.LEFT_HIP,
                PortraitLandmarkId.RIGHT_HIP,
                PortraitLandmarkId.LEFT_KNEE,
                PortraitLandmarkId.RIGHT_KNEE,
                PortraitLandmarkId.LEFT_ANKLE,
                PortraitLandmarkId.RIGHT_ANKLE
            ),
            regions = setOf(
                PortraitRegionId.SUBJECT_MASK,
                PortraitRegionId.LEG_CONTOURS
            )
        ),
        bodyDefinition(
            id = PortraitParameterId.LEG_LENGTH,
            category = PortraitParameterCategory.BODY_PROPORTION,
            direction = PortraitParameterDirection.UNIDIRECTIONAL,
            risk = PortraitParameterRisk.CRITICAL,
            landmarks = setOf(
                PortraitLandmarkId.LEFT_HIP,
                PortraitLandmarkId.RIGHT_HIP,
                PortraitLandmarkId.LEFT_KNEE,
                PortraitLandmarkId.RIGHT_KNEE,
                PortraitLandmarkId.LEFT_ANKLE,
                PortraitLandmarkId.RIGHT_ANKLE
            ),
            regions = setOf(
                PortraitRegionId.SUBJECT_MASK,
                PortraitRegionId.LEG_CONTOURS
            ),
            minimumLandmarkConfidence = 0.80f
        )
    ).also { catalog ->
        require(catalog.map { it.id }.distinct().size == catalog.size) {
            "Default portrait parameter ids must be unique"
        }
    }

    val gateSpecs: List<ParameterGateSpec>
        get() = definitions.map { it.gateSpec }

    private fun faceDefinition(
        id: String,
        category: PortraitParameterCategory,
        direction: PortraitParameterDirection,
        risk: PortraitParameterRisk,
        landmarks: Set<String>,
        regions: Set<String>,
        maximumYaw: Float,
        maximumPitch: Float,
        maximumRoll: Float,
        minimumCoverage: Float,
        minimumLandmarkConfidence: Float = 0.90f,
        minimumRegionConfidence: Float = 0.90f,
        maximumOcclusion: Float = 0.15f
    ) = PortraitParameterDefinition(
        id = id,
        displayNameKey = "portrait_parameter_$id",
        category = category,
        direction = direction,
        risk = risk,
        gateSpec = ParameterGateSpec(
            parameterId = id,
            requiredLandmarkIds = landmarks,
            requiredRegionIds = regions,
            minimumLandmarkConfidence = minimumLandmarkConfidence,
            minimumRegionConfidence = minimumRegionConfidence,
            maximumRegionOcclusion = maximumOcclusion,
            minimumRegionPixelCoverage = minimumCoverage,
            minimumSubjects = 1,
            maximumSubjects = 1,
            minimumFaces = 1,
            maximumFaces = 1,
            maximumAbsoluteYawDegrees = maximumYaw,
            maximumAbsolutePitchDegrees = maximumPitch,
            maximumAbsoluteRollDegrees = maximumRoll
        )
    )

    private fun bodyDefinition(
        id: String,
        category: PortraitParameterCategory,
        direction: PortraitParameterDirection,
        risk: PortraitParameterRisk,
        landmarks: Set<String>,
        regions: Set<String>,
        minimumLandmarkConfidence: Float = 0.70f
    ) = PortraitParameterDefinition(
        id = id,
        displayNameKey = "portrait_parameter_$id",
        category = category,
        direction = direction,
        risk = risk,
        gateSpec = ParameterGateSpec(
            parameterId = id,
            requiredLandmarkIds = landmarks,
            requiredRegionIds = regions,
            minimumLandmarkConfidence = minimumLandmarkConfidence,
            minimumRegionConfidence = 0.60f,
            maximumRegionOcclusion = null,
            minimumRegionPixelCoverage = 0.01f,
            minimumSubjects = 1,
            maximumSubjects = 1,
            minimumBodies = 1,
            maximumBodies = 1
        )
    )
}
