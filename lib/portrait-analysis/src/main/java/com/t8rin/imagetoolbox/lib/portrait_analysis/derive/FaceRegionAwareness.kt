/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis.derive

import com.t8rin.imagetoolbox.lib.portrait_analysis.catalog.PortraitLandmarkId
import com.t8rin.imagetoolbox.lib.portrait_analysis.catalog.PortraitRegionId
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ConfidenceSource
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.LandmarkObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.NormalizedPoint3D
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ObservationBackend
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.RegionObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.VisibilityState
import kotlin.math.abs

/** Coarse pose category used to decide whether full-face geometry is safe. */
enum class FacePoseMode {
    FRONTAL,
    HALF_PROFILE,
    PROFILE,
    UNKNOWN
}

/** Image-space side. It is intentionally independent from anatomical left/right naming. */
enum class FaceImageSide {
    LEFT,
    RIGHT,
    BALANCED,
    UNKNOWN
}

enum class FaceRegionAvailability {
    AVAILABLE,
    PARTIAL,
    UNRELIABLE,
    UNAVAILABLE
}

enum class FaceRegionKey(val regionId: String) {
    FULL_FACE(PortraitRegionId.FULL_FACE_REGION),
    IMAGE_LEFT_HALF(PortraitRegionId.IMAGE_LEFT_FACE_HALF),
    IMAGE_RIGHT_HALF(PortraitRegionId.IMAGE_RIGHT_FACE_HALF),
    LEFT_EYE(PortraitRegionId.LEFT_EYE_REGION),
    RIGHT_EYE(PortraitRegionId.RIGHT_EYE_REGION),
    LEFT_BROW(PortraitRegionId.LEFT_BROW_REGION),
    RIGHT_BROW(PortraitRegionId.RIGHT_BROW_REGION),
    NOSE(PortraitRegionId.NOSE_REGION),
    MOUTH(PortraitRegionId.MOUTH_REGION),
    LEFT_CHEEK(PortraitRegionId.LEFT_CHEEK_REGION),
    RIGHT_CHEEK(PortraitRegionId.RIGHT_CHEEK_REGION),
    LEFT_JAW(PortraitRegionId.LEFT_JAW_REGION),
    RIGHT_JAW(PortraitRegionId.RIGHT_JAW_REGION),
    CHIN(PortraitRegionId.CHIN_REGION),
    SUBCHIN(PortraitRegionId.SUBCHIN_REGION)
}

data class FaceRegionCapability(
    val region: FaceRegionKey,
    val availability: FaceRegionAvailability,
    val evidenceCount: Int,
    val confidence: Float,
    val estimatedPixelCoverage: Float,
    val geometryEditAllowed: Boolean
)

data class FaceRegionAwareness(
    val poseMode: FacePoseMode,
    val dominantImageSide: FaceImageSide,
    val yawDegrees: Float?,
    val pitchDegrees: Float?,
    val rollDegrees: Float?,
    val fullFaceGeometryAllowed: Boolean,
    val regions: Map<FaceRegionKey, FaceRegionCapability>,
    val reasons: List<String>
)

data class FaceRegionAnalysisResult(
    val observation: SubjectObservation,
    val awareness: FaceRegionAwareness
)

/**
 * P2 region-aware face analysis.
 *
 * The analyzer does not deform pixels. It converts the frozen detector result into explicit
 * full-face, image-side, jaw and chin capabilities. Profile images therefore no longer have to
 * pass a global full-face gate before a visible half or the chin can be edited later.
 */
object FaceRegionAwarenessAnalyzer {

    private const val FRONTAL_MAX_ABS_YAW = 15f
    private const val HALF_PROFILE_MAX_ABS_YAW = 45f
    private const val MAX_SUPPORTED_ABS_PITCH = 35f
    private const val MAX_SUPPORTED_ABS_ROLL = 40f
    private const val DOMINANT_SIDE_ASYMMETRY = 0.14f

    fun analyzeAndEnrich(observation: SubjectObservation): FaceRegionAnalysisResult {
        if (observation.faceCount != 1) {
            val awareness = FaceRegionAwareness(
                poseMode = FacePoseMode.UNKNOWN,
                dominantImageSide = FaceImageSide.UNKNOWN,
                yawDegrees = observation.pose.yawDegrees,
                pitchDegrees = observation.pose.pitchDegrees,
                rollDegrees = observation.pose.rollDegrees,
                fullFaceGeometryAllowed = false,
                regions = FaceRegionKey.entries.associateWith { key ->
                    capability(
                        key = key,
                        availability = FaceRegionAvailability.UNAVAILABLE,
                        evidenceCount = 0,
                        faceCoverage = 0f
                    )
                },
                reasons = listOf("Region-aware analysis requires exactly one active face")
            )
            return FaceRegionAnalysisResult(observation, awareness)
        }

        val backend = observation.landmarks.values
            .map { it.backend }
            .filter { it != ObservationBackend.UNKNOWN }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
            ?: ObservationBackend.UNKNOWN

        val bounds = observation.faceBounds()
        val faceCoverage = bounds?.area ?: 0f
        val poseMode = poseMode(observation.pose.yawDegrees)
        val lowerFaceGeometry = observation.lowerFaceGeometry(bounds)
        val dominantSide = observation.dominantImageSide(bounds)
        val poseQualitySupported = observation.pose.pitchDegrees.within(MAX_SUPPORTED_ABS_PITCH) &&
            observation.pose.rollDegrees.within(MAX_SUPPORTED_ABS_ROLL)

        val rawEvidence = mapOf(
            FaceRegionKey.IMAGE_LEFT_HALF to observation.imageHalfEvidence(bounds, FaceImageSide.LEFT),
            FaceRegionKey.IMAGE_RIGHT_HALF to observation.imageHalfEvidence(bounds, FaceImageSide.RIGHT),
            FaceRegionKey.LEFT_EYE to observation.tokenEvidence("left_eye"),
            FaceRegionKey.RIGHT_EYE to observation.tokenEvidence("right_eye"),
            FaceRegionKey.LEFT_BROW to observation.tokenEvidence("left_eyebrow", "left_brow"),
            FaceRegionKey.RIGHT_BROW to observation.tokenEvidence("right_eyebrow", "right_brow"),
            FaceRegionKey.NOSE to observation.tokenEvidence("nose"),
            FaceRegionKey.MOUTH to observation.tokenEvidence("mouth", "lip"),
            FaceRegionKey.LEFT_CHEEK to observation.tokenEvidence("left_cheek"),
            FaceRegionKey.RIGHT_CHEEK to observation.tokenEvidence("right_cheek"),
            FaceRegionKey.LEFT_JAW to observation.sideJawEvidence(bounds, FaceImageSide.LEFT),
            FaceRegionKey.RIGHT_JAW to observation.sideJawEvidence(bounds, FaceImageSide.RIGHT),
            FaceRegionKey.CHIN to observation.tokenEvidence("chin")
                .coerceAtLeast(if (lowerFaceGeometry?.chinCenter != null) 1 else 0),
            FaceRegionKey.SUBCHIN to observation.tokenEvidence("subchin", "under_chin", "neck_mask")
        )

        val capabilities = linkedMapOf<FaceRegionKey, FaceRegionCapability>()
        val leftHalfState = sideAdjusted(
            evidenceAvailability(rawEvidence.getValue(FaceRegionKey.IMAGE_LEFT_HALF)),
            side = FaceImageSide.LEFT,
            poseMode = poseMode,
            dominantSide = dominantSide
        )
        val rightHalfState = sideAdjusted(
            evidenceAvailability(rawEvidence.getValue(FaceRegionKey.IMAGE_RIGHT_HALF)),
            side = FaceImageSide.RIGHT,
            poseMode = poseMode,
            dominantSide = dominantSide
        )
        val fullFaceState = when {
            !poseQualitySupported -> FaceRegionAvailability.UNRELIABLE
            poseMode == FacePoseMode.FRONTAL &&
                leftHalfState == FaceRegionAvailability.AVAILABLE &&
                rightHalfState == FaceRegionAvailability.AVAILABLE -> FaceRegionAvailability.AVAILABLE
            poseMode == FacePoseMode.FRONTAL -> FaceRegionAvailability.PARTIAL
            poseMode == FacePoseMode.HALF_PROFILE -> FaceRegionAvailability.PARTIAL
            poseMode == FacePoseMode.PROFILE -> FaceRegionAvailability.UNRELIABLE
            else -> FaceRegionAvailability.UNRELIABLE
        }

        capabilities[FaceRegionKey.FULL_FACE] = capability(
            FaceRegionKey.FULL_FACE,
            fullFaceState,
            observation.semanticEvidenceCount(),
            faceCoverage
        )
        capabilities[FaceRegionKey.IMAGE_LEFT_HALF] = capability(
            FaceRegionKey.IMAGE_LEFT_HALF,
            leftHalfState,
            rawEvidence.getValue(FaceRegionKey.IMAGE_LEFT_HALF),
            faceCoverage
        )
        capabilities[FaceRegionKey.IMAGE_RIGHT_HALF] = capability(
            FaceRegionKey.IMAGE_RIGHT_HALF,
            rightHalfState,
            rawEvidence.getValue(FaceRegionKey.IMAGE_RIGHT_HALF),
            faceCoverage
        )

        listOf(
            Triple(FaceRegionKey.LEFT_EYE, FaceImageSide.LEFT, rawEvidence.getValue(FaceRegionKey.LEFT_EYE)),
            Triple(FaceRegionKey.RIGHT_EYE, FaceImageSide.RIGHT, rawEvidence.getValue(FaceRegionKey.RIGHT_EYE)),
            Triple(FaceRegionKey.LEFT_BROW, FaceImageSide.LEFT, rawEvidence.getValue(FaceRegionKey.LEFT_BROW)),
            Triple(FaceRegionKey.RIGHT_BROW, FaceImageSide.RIGHT, rawEvidence.getValue(FaceRegionKey.RIGHT_BROW)),
            Triple(FaceRegionKey.LEFT_CHEEK, FaceImageSide.LEFT, rawEvidence.getValue(FaceRegionKey.LEFT_CHEEK)),
            Triple(FaceRegionKey.RIGHT_CHEEK, FaceImageSide.RIGHT, rawEvidence.getValue(FaceRegionKey.RIGHT_CHEEK)),
            Triple(FaceRegionKey.LEFT_JAW, FaceImageSide.LEFT, rawEvidence.getValue(FaceRegionKey.LEFT_JAW)),
            Triple(FaceRegionKey.RIGHT_JAW, FaceImageSide.RIGHT, rawEvidence.getValue(FaceRegionKey.RIGHT_JAW))
        ).forEach { (key, side, evidence) ->
            capabilities[key] = capability(
                key,
                sideAdjusted(evidenceAvailability(evidence), side, poseMode, dominantSide),
                evidence,
                faceCoverage
            )
        }

        listOf(FaceRegionKey.NOSE, FaceRegionKey.MOUTH, FaceRegionKey.CHIN).forEach { key ->
            val evidence = rawEvidence.getValue(key)
            capabilities[key] = capability(
                key,
                centerAdjusted(evidenceAvailability(evidence), poseMode, poseQualitySupported),
                evidence,
                faceCoverage
            )
        }
        capabilities[FaceRegionKey.SUBCHIN] = capability(
            FaceRegionKey.SUBCHIN,
            evidenceAvailability(rawEvidence.getValue(FaceRegionKey.SUBCHIN)),
            rawEvidence.getValue(FaceRegionKey.SUBCHIN),
            faceCoverage
        )

        val reasons = buildList {
            add("pose_mode=${poseMode.name}")
            add("dominant_image_side=${dominantSide.name}")
            if (fullFaceState != FaceRegionAvailability.AVAILABLE) {
                add("Full-face geometry is blocked; use an available image-side or center region")
            }
            if (capabilities.getValue(FaceRegionKey.CHIN).geometryEditAllowed) {
                add("Chin geometry is independently available")
            }
            if (capabilities.getValue(FaceRegionKey.SUBCHIN).availability == FaceRegionAvailability.UNAVAILABLE) {
                add("Subchin shaping remains blocked until a semantic subchin or neck mask exists")
            }
        }

        val awareness = FaceRegionAwareness(
            poseMode = poseMode,
            dominantImageSide = dominantSide,
            yawDegrees = observation.pose.yawDegrees,
            pitchDegrees = observation.pose.pitchDegrees,
            rollDegrees = observation.pose.rollDegrees,
            fullFaceGeometryAllowed = fullFaceState == FaceRegionAvailability.AVAILABLE,
            regions = capabilities,
            reasons = reasons
        )

        val derivedLandmarks = lowerFaceGeometry?.toLandmarks(
            backend = backend,
            capabilities = capabilities,
            existingIds = observation.landmarks.keys,
            lowerLipCenter = observation.lowerLipCenter()
        ).orEmpty()
        val derivedRegions = capabilities.values.associate { item ->
            item.region.regionId to item.toRegionObservation(backend)
        }

        return FaceRegionAnalysisResult(
            observation = observation.copy(
                landmarks = observation.landmarks + derivedLandmarks,
                regions = observation.regions + derivedRegions
            ),
            awareness = awareness
        )
    }

    private fun capability(
        key: FaceRegionKey,
        availability: FaceRegionAvailability,
        evidenceCount: Int,
        faceCoverage: Float
    ): FaceRegionCapability {
        val confidence = when (availability) {
            FaceRegionAvailability.AVAILABLE -> 0.95f
            FaceRegionAvailability.PARTIAL -> 0.68f
            FaceRegionAvailability.UNRELIABLE -> 0.25f
            FaceRegionAvailability.UNAVAILABLE -> 0f
        }
        val coverageFactor = when (key) {
            FaceRegionKey.FULL_FACE -> 1f
            FaceRegionKey.IMAGE_LEFT_HALF,
            FaceRegionKey.IMAGE_RIGHT_HALF -> 0.5f
            FaceRegionKey.LEFT_EYE,
            FaceRegionKey.RIGHT_EYE,
            FaceRegionKey.LEFT_BROW,
            FaceRegionKey.RIGHT_BROW -> 0.05f
            FaceRegionKey.NOSE,
            FaceRegionKey.MOUTH -> 0.07f
            FaceRegionKey.LEFT_CHEEK,
            FaceRegionKey.RIGHT_CHEEK,
            FaceRegionKey.LEFT_JAW,
            FaceRegionKey.RIGHT_JAW -> 0.14f
            FaceRegionKey.CHIN,
            FaceRegionKey.SUBCHIN -> 0.09f
        }
        return FaceRegionCapability(
            region = key,
            availability = availability,
            evidenceCount = evidenceCount,
            confidence = confidence,
            estimatedPixelCoverage = (faceCoverage * coverageFactor).coerceIn(0f, 1f),
            geometryEditAllowed = availability == FaceRegionAvailability.AVAILABLE
        )
    }

    private fun FaceRegionCapability.toRegionObservation(
        backend: ObservationBackend
    ) = RegionObservation(
        id = region.regionId,
        confidence = confidence,
        confidenceSource = ConfidenceSource.DERIVED,
        occlusion = when (availability) {
            FaceRegionAvailability.AVAILABLE -> 0.05f
            FaceRegionAvailability.PARTIAL -> 0.25f
            FaceRegionAvailability.UNRELIABLE -> 0.70f
            FaceRegionAvailability.UNAVAILABLE -> 1f
        },
        pixelCoverage = estimatedPixelCoverage,
        backend = backend
    )

    private fun poseMode(yaw: Float?): FacePoseMode = when {
        yaw == null -> FacePoseMode.UNKNOWN
        abs(yaw) <= FRONTAL_MAX_ABS_YAW -> FacePoseMode.FRONTAL
        abs(yaw) <= HALF_PROFILE_MAX_ABS_YAW -> FacePoseMode.HALF_PROFILE
        else -> FacePoseMode.PROFILE
    }

    private fun Float?.within(limit: Float): Boolean = this == null || abs(this) <= limit

    private fun evidenceAvailability(evidenceCount: Int): FaceRegionAvailability = when {
        evidenceCount >= 4 -> FaceRegionAvailability.AVAILABLE
        evidenceCount > 0 -> FaceRegionAvailability.PARTIAL
        else -> FaceRegionAvailability.UNAVAILABLE
    }

    private fun centerAdjusted(
        availability: FaceRegionAvailability,
        poseMode: FacePoseMode,
        poseQualitySupported: Boolean
    ): FaceRegionAvailability = when {
        !poseQualitySupported && availability == FaceRegionAvailability.AVAILABLE ->
            FaceRegionAvailability.PARTIAL
        poseMode == FacePoseMode.UNKNOWN && availability == FaceRegionAvailability.AVAILABLE ->
            FaceRegionAvailability.PARTIAL
        else -> availability
    }

    private fun sideAdjusted(
        availability: FaceRegionAvailability,
        side: FaceImageSide,
        poseMode: FacePoseMode,
        dominantSide: FaceImageSide
    ): FaceRegionAvailability {
        if (availability == FaceRegionAvailability.UNAVAILABLE) return availability
        return when (poseMode) {
            FacePoseMode.FRONTAL -> availability
            FacePoseMode.HALF_PROFILE -> when {
                dominantSide == FaceImageSide.BALANCED || dominantSide == FaceImageSide.UNKNOWN ->
                    availability.atMostPartial()
                side == dominantSide -> availability
                else -> availability.atMostPartial()
            }
            FacePoseMode.PROFILE -> when {
                dominantSide == FaceImageSide.BALANCED || dominantSide == FaceImageSide.UNKNOWN ->
                    FaceRegionAvailability.UNRELIABLE
                side == dominantSide -> availability
                else -> FaceRegionAvailability.UNRELIABLE
            }
            FacePoseMode.UNKNOWN -> availability.atMostPartial()
        }
    }

    private fun FaceRegionAvailability.atMostPartial(): FaceRegionAvailability = when (this) {
        FaceRegionAvailability.AVAILABLE -> FaceRegionAvailability.PARTIAL
        else -> this
    }

    private data class Bounds(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float
    ) {
        val width: Float get() = (right - left).coerceAtLeast(0f)
        val height: Float get() = (bottom - top).coerceAtLeast(0f)
        val centerX: Float get() = (left + right) / 2f
        val area: Float get() = (width * height).coerceIn(0f, 1f)
    }

    private data class LowerFaceGeometry(
        val imageLeftJaw: NormalizedPoint3D,
        val imageRightJaw: NormalizedPoint3D,
        val imageLeftChin: NormalizedPoint3D,
        val chinCenter: NormalizedPoint3D,
        val imageRightChin: NormalizedPoint3D
    ) {
        fun toLandmarks(
            backend: ObservationBackend,
            capabilities: Map<FaceRegionKey, FaceRegionCapability>,
            existingIds: Set<String>,
            lowerLipCenter: NormalizedPoint3D?
        ): Map<String, LandmarkObservation> = buildMap {
            addDerived(
                PortraitLandmarkId.IMAGE_LEFT_JAW_ANCHOR,
                imageLeftJaw,
                capabilities.getValue(FaceRegionKey.LEFT_JAW),
                backend,
                existingIds
            )
            addDerived(
                PortraitLandmarkId.IMAGE_RIGHT_JAW_ANCHOR,
                imageRightJaw,
                capabilities.getValue(FaceRegionKey.RIGHT_JAW),
                backend,
                existingIds
            )
            addDerived(
                PortraitLandmarkId.IMAGE_LEFT_CHIN_ANCHOR,
                imageLeftChin,
                capabilities.getValue(FaceRegionKey.CHIN),
                backend,
                existingIds
            )
            addDerived(
                PortraitLandmarkId.CHIN_CENTER,
                chinCenter,
                capabilities.getValue(FaceRegionKey.CHIN),
                backend,
                existingIds
            )
            addDerived(
                PortraitLandmarkId.IMAGE_RIGHT_CHIN_ANCHOR,
                imageRightChin,
                capabilities.getValue(FaceRegionKey.CHIN),
                backend,
                existingIds
            )
            lowerLipCenter?.let {
                addDerived(
                    PortraitLandmarkId.LOWER_LIP_CENTER,
                    it,
                    capabilities.getValue(FaceRegionKey.MOUTH),
                    backend,
                    existingIds
                )
            }
        }

        private fun MutableMap<String, LandmarkObservation>.addDerived(
            id: String,
            point: NormalizedPoint3D,
            capability: FaceRegionCapability,
            backend: ObservationBackend,
            existingIds: Set<String>
        ) {
            if (id in existingIds) return
            put(
                id,
                LandmarkObservation(
                    id = id,
                    point = point,
                    confidence = capability.confidence,
                    confidenceSource = ConfidenceSource.DERIVED,
                    visibility = capability.availability.toVisibility(),
                    backend = backend
                )
            )
        }
    }

    private fun FaceRegionAvailability.toVisibility(): VisibilityState = when (this) {
        FaceRegionAvailability.AVAILABLE -> VisibilityState.VISIBLE
        FaceRegionAvailability.PARTIAL -> VisibilityState.PARTIALLY_VISIBLE
        FaceRegionAvailability.UNRELIABLE -> VisibilityState.AMBIGUOUS
        FaceRegionAvailability.UNAVAILABLE -> VisibilityState.OCCLUDED
    }

    private fun SubjectObservation.faceBounds(): Bounds? {
        val boundingContour = contours.values.firstOrNull {
            it.id.contains("bounding_box") || it.id.contains("bbox")
        }
        val points = boundingContour?.vertexIds?.mapNotNull(landmarks::get)?.map { it.point }
            ?.takeIf { it.isNotEmpty() }
            ?: landmarks.values
                .filterNot { it.id.contains("bounding_box") }
                .map { it.point }
                .takeIf { it.isNotEmpty() }
            ?: return null
        return Bounds(
            left = points.minOf { it.x }.coerceIn(0f, 1f),
            top = points.minOf { it.y }.coerceIn(0f, 1f),
            right = points.maxOf { it.x }.coerceIn(0f, 1f),
            bottom = points.maxOf { it.y }.coerceIn(0f, 1f)
        )
    }

    private fun SubjectObservation.dominantImageSide(bounds: Bounds?): FaceImageSide {
        bounds ?: return FaceImageSide.UNKNOWN
        val nosePoints = landmarks.values.filter {
            val id = it.id.lowercase()
            id.contains("nose_base") || id.contains("nose_bottom") || id.contains("nose_bridge")
        }
        val noseX = nosePoints.map { it.point.x }.average().takeIf { !it.isNaN() }?.toFloat()
            ?: return FaceImageSide.UNKNOWN
        val leftSpan = (noseX - bounds.left).coerceAtLeast(0f)
        val rightSpan = (bounds.right - noseX).coerceAtLeast(0f)
        val total = leftSpan + rightSpan
        if (total <= 0f) return FaceImageSide.UNKNOWN
        if (abs(leftSpan - rightSpan) / total < DOMINANT_SIDE_ASYMMETRY) {
            return FaceImageSide.BALANCED
        }
        return if (leftSpan > rightSpan) FaceImageSide.LEFT else FaceImageSide.RIGHT
    }

    private fun SubjectObservation.semanticEvidenceCount(): Int = landmarks.values.count {
        val id = it.id.lowercase()
        !id.contains("bounding_box") && !id.endsWith("_center")
    }

    private fun SubjectObservation.tokenEvidence(vararg tokens: String): Int = landmarks.values.count {
        val id = it.id.lowercase()
        !id.contains("bounding_box") && tokens.any(id::contains)
    }

    private fun SubjectObservation.imageHalfEvidence(bounds: Bounds?, side: FaceImageSide): Int {
        val centerX = bounds?.centerX ?: 0.5f
        return landmarks.values.count { landmark ->
            val id = landmark.id.lowercase()
            if (id.contains("bounding_box") || id.endsWith("_center")) return@count false
            when (side) {
                FaceImageSide.LEFT -> landmark.point.x <= centerX
                FaceImageSide.RIGHT -> landmark.point.x > centerX
                else -> false
            }
        }
    }

    private fun SubjectObservation.sideJawEvidence(bounds: Bounds?, side: FaceImageSide): Int {
        val centerX = bounds?.centerX ?: 0.5f
        return landmarks.values.count { landmark ->
            val id = landmark.id.lowercase()
            val jawEvidence = id.contains("jawline") || id.contains("chin") ||
                (id.contains("contour_face") && landmark.point.y >= (bounds?.top ?: 0f) +
                    (bounds?.height ?: 1f) * 0.45f)
            jawEvidence && when (side) {
                FaceImageSide.LEFT -> landmark.point.x <= centerX
                FaceImageSide.RIGHT -> landmark.point.x > centerX
                else -> false
            }
        }
    }

    private fun SubjectObservation.lowerFaceGeometry(bounds: Bounds?): LowerFaceGeometry? {
        bounds ?: return null
        val contour = contours.values.firstOrNull { it.id.lowercase().contains("jawline") }
            ?: contours.values.firstOrNull {
                val id = it.id.lowercase()
                (id.contains("contour_face") || id.contains("face_oval")) &&
                    !id.contains("bounding_box")
            }
            ?: return null
        val points = contour.vertexIds.mapNotNull(landmarks::get).map { it.point }
        if (points.size < 5 || bounds.width <= 0f) return null

        val chin = points.maxBy { it.y }
        fun nearestX(target: Float): NormalizedPoint3D = points.minWith(
            compareBy<NormalizedPoint3D> { abs(it.x - target) }
                .thenByDescending { it.y }
        )

        return LowerFaceGeometry(
            imageLeftJaw = nearestX(bounds.left + bounds.width * 0.18f),
            imageRightJaw = nearestX(bounds.left + bounds.width * 0.82f),
            imageLeftChin = nearestX(chin.x - bounds.width * 0.10f),
            chinCenter = chin,
            imageRightChin = nearestX(chin.x + bounds.width * 0.10f)
        )
    }

    private fun SubjectObservation.lowerLipCenter(): NormalizedPoint3D? {
        val candidates = landmarks.values.filter {
            val id = it.id.lowercase()
            id.contains("mouth_bottom") || id.contains("lower_lip_bottom") ||
                id.contains("lower_lip_mid")
        }
        return candidates.maxByOrNull { it.point.y }?.point
    }
}
