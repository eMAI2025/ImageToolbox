/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.feature.portrait_lab

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.t8rin.imagetoolbox.lib.portrait_analysis.catalog.PortraitRegionId
import com.t8rin.imagetoolbox.lib.portrait_analysis.derive.BodyPoseSkeletonEnricher
import com.t8rin.imagetoolbox.lib.portrait_analysis.derive.BodySilhouetteEnricher
import com.t8rin.imagetoolbox.lib.portrait_analysis.derive.BodySilhouetteSectionValidator
import com.t8rin.imagetoolbox.lib.portrait_analysis.derive.BodySilhouetteSkippedSection
import com.t8rin.imagetoolbox.lib.portrait_analysis.merge.ObservationMergeResult
import com.t8rin.imagetoolbox.lib.portrait_analysis.merge.SubjectObservationMerger
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ObservationBackend
import com.t8rin.imagetoolbox.lib.portrait_analysis.overlay.PortraitOverlaySceneBuilder
import com.t8rin.imagetoolbox.lib.portrait_analysis_mlkit.MlKitImageInput
import com.t8rin.imagetoolbox.lib.portrait_analysis_mlkit.pose.MlKitPoseObservationEngine
import com.t8rin.imagetoolbox.lib.portrait_analysis_mlkit.segmentation.MlKitSelfieSegmentationObservationEngine
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt

private const val SILHOUETTE_MAX_PREVIEW_DIMENSION = 1600

fun createSilhouetteLabRunner(context: Context): SilhouetteLabRunner =
    MarketSilhouetteLabRunner(context.applicationContext)

private class MarketSilhouetteLabRunner(
    private val context: Context
) : SilhouetteLabRunner {

    override suspend fun run(uri: Uri): SilhouetteLabRunResult {
        val decoded = try {
            context.decodeSilhouetteImage(uri)
        } catch (error: Exception) {
            return failure("Unable to decode the selected image", error)
        }

        val poseEngine = MlKitPoseObservationEngine()
        val segmentationEngine = MlKitSelfieSegmentationObservationEngine()
        return try {
            val input = MlKitImageInput.fromBitmap(decoded.orientedBitmap)
            val poseStarted = System.nanoTime()
            val poseObservation = poseEngine.observe(input)
            val poseMillis = (System.nanoTime() - poseStarted).coerceAtLeast(0L) / 1_000_000L

            val maskStarted = System.nanoTime()
            val maskObservation = segmentationEngine.observe(input)
            val maskMillis = (System.nanoTime() - maskStarted).coerceAtLeast(0L) / 1_000_000L

            val merged = when (
                val result = SubjectObservationMerger.merge(
                    listOf(poseObservation, maskObservation)
                )
            ) {
                is ObservationMergeResult.Success -> result.observation
                is ObservationMergeResult.Conflict -> return SilhouetteLabRunResult.Failure(
                    message = "Pose and segmentation merge conflict: " +
                        result.conflicts.joinToString { conflict ->
                            "${conflict.type}:${conflict.itemId.orEmpty()}"
                        }
                )
            }

            val withSkeleton = BodyPoseSkeletonEnricher.enrich(merged)
            val rawEnrichment = BodySilhouetteEnricher.enrich(withSkeleton)
            val sectionValidation = BodySilhouetteSectionValidator.validate(
                enrichment = rawEnrichment,
                sourceWidth = decoded.metadata.orientedWidth,
                sourceHeight = decoded.metadata.orientedHeight
            )
            val enrichment = sectionValidation.enrichment
            val observation = enrichment.observation
            val capabilities = bodyRegionCapabilities(observation, enrichment)
            val availableCount = capabilities.count { it.available }
            val poseLandmarkCount = observation.landmarks.values.count {
                it.backend == ObservationBackend.ML_KIT_POSE
            }
            val status = when {
                observation.bodyCount != 1 ||
                    PortraitRegionId.SUBJECT_MASK !in observation.masks -> SilhouetteVisualStatus.FAILED
                availableCount == capabilities.size -> SilhouetteVisualStatus.PASS
                availableCount > 0 -> SilhouetteVisualStatus.PARTIAL
                else -> SilhouetteVisualStatus.FAILED
            }
            val runtimeLog = buildList {
                add("mode=B1_SILHOUETTE_OBSERVATION")
                add("backend_pose=ML_KIT_POSE")
                add("backend_mask=ML_KIT_SELFIE_SEGMENTATION")
                add("encoded=${decoded.metadata.encodedWidth}x${decoded.metadata.encodedHeight}")
                add("oriented=${decoded.metadata.orientedWidth}x${decoded.metadata.orientedHeight}")
                add("preview=${decoded.metadata.previewWidth}x${decoded.metadata.previewHeight}")
                add("pose_processing_ms=$poseMillis")
                add("segmentation_processing_ms=$maskMillis")
                add("subject_count=${observation.subjectCount}")
                add("body_count=${observation.bodyCount}")
                add("pose_landmark_count=$poseLandmarkCount")
                add("mask_count=${observation.masks.size}")
                add("derived_region_count=${enrichment.derivedRegionIds.size}")
                add("local_section_rejection_count=${sectionValidation.rejectedSections.size}")
                add("silhouette_status=${status.name}")
                capabilities.forEach { capability ->
                    add(
                        "body_region=${capability.regionId},available=${capability.available}," +
                            "reason=${capability.reason.orEmpty()}"
                    )
                }
                sectionValidation.rejectedSections.forEach { rejected ->
                    add(
                        "rejected_section=${rejected.sectionId}," +
                            "measured_px=${rejected.measuredPixels}," +
                            "bone_px=${rejected.referenceBonePixels}," +
                            "ratio=${rejected.measuredToBoneRatio}," +
                            "maximum_ratio=${rejected.maximumAllowedRatio}"
                    )
                }
                enrichment.skippedSections.forEach { skipped ->
                    add(
                        "skipped_section=${skipped.sectionId}," +
                            "reason=${skipped.reportedReason()}," +
                            "raw_reason=${skipped.reason.name}," +
                            "item=${skipped.itemId.orEmpty()}"
                    )
                }
            }
            val warnings = buildList {
                if (status != SilhouetteVisualStatus.PASS) {
                    add(
                        "Only visible, mask-supported body regions are available. " +
                            "Missing, cropped or non-local regions remain disabled; no anatomy is inferred."
                    )
                }
                if (sectionValidation.rejectedSections.isNotEmpty()) {
                    add(
                        "${sectionValidation.rejectedSections.size} limb cross-sections were removed " +
                            "because they spanned unrelated foreground instead of the local limb."
                    )
                }
                add("Visible clothing is part of the measured silhouette by design.")
                add("No body deformation is performed in B1.")
            }

            SilhouetteLabRunResult.Success(
                SilhouetteLabRunOutput(
                    sourceBitmap = decoded.orientedBitmap,
                    previewBitmap = decoded.previewBitmap,
                    sourceMetadata = decoded.metadata,
                    observation = observation,
                    enrichment = enrichment,
                    overlayScene = PortraitOverlaySceneBuilder.build(observation),
                    status = status,
                    regionCapabilities = capabilities,
                    runtimeLog = runtimeLog,
                    warnings = warnings
                )
            )
        } catch (error: LinkageError) {
            failure("Silhouette backend linkage failed", error)
        } catch (error: Exception) {
            failure("Silhouette backend execution failed", error)
        } finally {
            runCatching { poseEngine.close() }
            runCatching { segmentationEngine.close() }
        }
    }

    override fun close() = Unit

    private fun failure(message: String, error: Throwable) = SilhouetteLabRunResult.Failure(
        message = error.message?.let { "$message: $it" } ?: message,
        exceptionType = error::class.qualifiedName
    )
}

private fun bodyRegionCapabilities(
    observation: com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation,
    enrichment: com.t8rin.imagetoolbox.lib.portrait_analysis.derive.BodySilhouetteEnrichmentResult
): List<SilhouetteRegionCapability> {
    val regionIds = listOf(
        PortraitRegionId.SHOULDER_CONTOUR,
        PortraitRegionId.ARM_CONTOURS,
        PortraitRegionId.WAIST_CONTOUR,
        PortraitRegionId.HIP_CONTOUR,
        PortraitRegionId.LEG_CONTOURS
    )
    return regionIds.map { regionId ->
        val available = regionId in observation.regions && regionId in enrichment.derivedRegionIds
        val relatedSkip = enrichment.skippedSections.firstOrNull { skipped ->
            when (regionId) {
                PortraitRegionId.SHOULDER_CONTOUR -> skipped.sectionId.contains("shoulder")
                PortraitRegionId.ARM_CONTOURS -> skipped.sectionId.contains("arm") ||
                    skipped.sectionId.contains("forearm")
                PortraitRegionId.WAIST_CONTOUR -> skipped.sectionId.contains("waist")
                PortraitRegionId.HIP_CONTOUR -> skipped.sectionId.contains("hip")
                PortraitRegionId.LEG_CONTOURS -> skipped.sectionId.contains("thigh") ||
                    skipped.sectionId.contains("calf")
                else -> false
            }
        }
        SilhouetteRegionCapability(
            regionId = regionId,
            available = available,
            reason = if (available) null else relatedSkip?.reportedReason() ?: "NOT_DERIVED"
        )
    }
}

private fun BodySilhouetteSkippedSection.reportedReason(): String {
    val semanticPrefix = itemId.orEmpty().substringBefore("_WIDTH_RATIO_")
    return when (semanticPrefix) {
        "TORSO_OCCLUDED_BY_LIMB",
        "TORSO_WIDTH_OUTLIER",
        "SECTION_NON_LOCAL",
        "MISSING_TORSO_EVIDENCE",
        "DEGENERATE_TORSO_AXIS",
        "MALFORMED_TORSO_SECTION",
        "WIDTH_OUTLIER",
        "MISSING_LOCAL_EVIDENCE",
        "DEGENERATE_LOCAL_BONE",
        "MALFORMED_SECTION" -> semanticPrefix
        else -> reason.name
    }
}

private data class DecodedSilhouetteImage(
    val orientedBitmap: Bitmap,
    val previewBitmap: Bitmap,
    val metadata: PortraitSourceMetadata
)

private fun Context.decodeSilhouetteImage(uri: Uri): DecodedSilhouetteImage {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    contentResolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, bounds)
    }
    val decoded = contentResolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream)
    } ?: throw IOException("Unable to open image: $uri")
    val decodedWidth = decoded.width
    val decodedHeight = decoded.height

    val orientation = contentResolver.openInputStream(uri)?.use { stream ->
        ExifInterface(stream).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
    } ?: ExifInterface.ORIENTATION_NORMAL

    val transform = silhouetteExifTransform(orientation)
    val oriented = if (transform.matrix.isIdentity) {
        decoded
    } else {
        Bitmap.createBitmap(
            decoded,
            0,
            0,
            decodedWidth,
            decodedHeight,
            transform.matrix,
            true
        ).also { if (it !== decoded) decoded.recycle() }
    }
    val argb = if (oriented.config == Bitmap.Config.ARGB_8888) {
        oriented
    } else {
        oriented.copy(Bitmap.Config.ARGB_8888, false) ?: oriented
    }
    val preview = argb.silhouetteScaledPreview(SILHOUETTE_MAX_PREVIEW_DIMENSION)
    return DecodedSilhouetteImage(
        orientedBitmap = argb,
        previewBitmap = preview,
        metadata = PortraitSourceMetadata(
            encodedWidth = bounds.outWidth.takeIf { it > 0 } ?: decodedWidth,
            encodedHeight = bounds.outHeight.takeIf { it > 0 } ?: decodedHeight,
            orientedWidth = argb.width,
            orientedHeight = argb.height,
            exifOrientation = orientation,
            orientationDegrees = transform.rotationDegrees,
            mirrored = transform.mirrored,
            previewWidth = preview.width,
            previewHeight = preview.height
        )
    )
}

private data class SilhouetteExifTransform(
    val matrix: Matrix,
    val rotationDegrees: Int,
    val mirrored: Boolean
)

private fun silhouetteExifTransform(orientation: Int): SilhouetteExifTransform {
    val matrix = Matrix()
    var degrees = 0
    var mirrored = false
    when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> {
            matrix.setScale(-1f, 1f)
            mirrored = true
        }
        ExifInterface.ORIENTATION_ROTATE_180 -> {
            matrix.setRotate(180f)
            degrees = 180
        }
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
            matrix.setScale(1f, -1f)
            mirrored = true
        }
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.setRotate(90f)
            matrix.postScale(-1f, 1f)
            degrees = 90
            mirrored = true
        }
        ExifInterface.ORIENTATION_ROTATE_90 -> {
            matrix.setRotate(90f)
            degrees = 90
        }
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.setRotate(-90f)
            matrix.postScale(-1f, 1f)
            degrees = 270
            mirrored = true
        }
        ExifInterface.ORIENTATION_ROTATE_270 -> {
            matrix.setRotate(-90f)
            degrees = 270
        }
    }
    return SilhouetteExifTransform(matrix, degrees, mirrored)
}

private fun Bitmap.silhouetteScaledPreview(maxDimension: Int): Bitmap {
    val largest = max(width, height)
    if (largest <= maxDimension) return this
    val scale = maxDimension.toFloat() / largest.toFloat()
    return Bitmap.createScaledBitmap(
        this,
        (width * scale).roundToInt().coerceAtLeast(1),
        (height * scale).roundToInt().coerceAtLeast(1),
        true
    )
}
