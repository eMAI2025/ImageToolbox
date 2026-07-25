/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.feature.portrait_lab

import android.graphics.Bitmap
import com.t8rin.imagetoolbox.lib.portrait_analysis.derive.FaceRegionAwarenessAnalyzer
import com.t8rin.imagetoolbox.lib.portrait_analysis.derive.FaceVisibleGeometryFilter
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ClassificationObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ContourObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.LandmarkObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.MeshObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.MeshTriangleObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.NormalizedPoint3D
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.PoseObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.RegionObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.overlay.PortraitOverlaySceneBuilder
import com.t8rin.imagetoolbox.lib.portrait_analysis.visual.ImageRenderTransform
import com.t8rin.imagetoolbox.lib.portrait_analysis.visual.VisualProofEvaluator
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

const val PORTRAIT_MAX_FACE_CANDIDATES = 2

private val faceIndexRegex = Regex("^face_(\\d+)_")

data class PortraitNormalizedRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    init {
        require(left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite())
        require(right > left)
        require(bottom > top)
    }

    fun contains(x: Float, y: Float): Boolean = x in left..right && y in top..bottom
}

data class PortraitFaceCandidate(
    val faceIndex: Int,
    val bounds: PortraitNormalizedRect
) {
    val area: Float get() = bounds.width * bounds.height
}

fun extractPortraitFaceCandidates(output: PortraitLabRunOutput): List<PortraitFaceCandidate> =
    output.observation.contours.values
        .asSequence()
        .filter { it.id.contains("bounding_box") || it.id.contains("bbox") }
        .mapNotNull { contour ->
            val faceIndex = faceIndexRegex.find(contour.id)?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: return@mapNotNull null
            val points = contour.vertexIds.mapNotNull(output.observation.landmarks::get)
                .map(LandmarkObservation::point)
            val bounds = points.toNormalizedBounds() ?: return@mapNotNull null
            PortraitFaceCandidate(faceIndex, bounds)
        }
        .distinctBy(PortraitFaceCandidate::faceIndex)
        .sortedBy(PortraitFaceCandidate::faceIndex)
        .toList()

fun focusPortraitOutput(
    output: PortraitLabRunOutput,
    candidate: PortraitFaceCandidate
): Result<PortraitLabRunOutput> = runCatching {
    require(candidate.faceIndex in extractPortraitFaceCandidates(output).map { it.faceIndex }) {
        "Selected face is not present in the frozen detector result"
    }

    val crop = candidate.bounds.expandedForPortrait()
    val sourceCrop = output.sourceBitmap.cropNormalized(crop)
    val previewCrop = output.previewBitmap.cropNormalized(crop)
    val selectedObservation = output.observation.focusOnFace(candidate.faceIndex, crop)
    val regionAnalysis = FaceRegionAwarenessAnalyzer.analyzeAndEnrich(selectedObservation)
    val awareness = regionAnalysis.awareness
    val visibleGeometry = FaceVisibleGeometryFilter.filter(
        observation = regionAnalysis.observation,
        awareness = awareness
    )
    val focusedObservation = visibleGeometry.observation
    val transform = ImageRenderTransform.fit(
        sourceWidth = sourceCrop.width,
        sourceHeight = sourceCrop.height,
        previewWidth = previewCrop.width,
        previewHeight = previewCrop.height
    )
    val visualProof = VisualProofEvaluator.evaluate(focusedObservation, transform)

    output.copy(
        sourceBitmap = sourceCrop,
        previewBitmap = previewCrop,
        sourceMetadata = PortraitSourceMetadata(
            encodedWidth = sourceCrop.width,
            encodedHeight = sourceCrop.height,
            orientedWidth = sourceCrop.width,
            orientedHeight = sourceCrop.height,
            exifOrientation = 1,
            orientationDegrees = 0,
            mirrored = false,
            previewWidth = previewCrop.width,
            previewHeight = previewCrop.height
        ),
        observation = focusedObservation,
        visualProof = visualProof,
        overlayScene = PortraitOverlaySceneBuilder.build(focusedObservation),
        faceRegionAwareness = awareness,
        runtimeLog = output.runtimeLog + buildList {
            add("source_face_count=${output.observation.faceCount}")
            add("active_face_index=${candidate.faceIndex}")
            add("active_face_mode=single_face_crop")
            add("analysis_crop_normalized=${crop.left},${crop.top},${crop.right},${crop.bottom}")
            add("focused_source=${sourceCrop.width}x${sourceCrop.height}")
            add("focused_preview=${previewCrop.width}x${previewCrop.height}")
            add("p2_pose_mode=${awareness.poseMode.name}")
            add("p2_dominant_image_side=${awareness.dominantImageSide.name}")
            add("p2_full_face_geometry_allowed=${awareness.fullFaceGeometryAllowed}")
            add("p2_visible_filter_applied=${visibleGeometry.applied}")
            add("p2_visible_filter_side=${visibleGeometry.activeImageSide.name}")
            add("p2_raw_landmarks=${visibleGeometry.rawLandmarkCount}")
            add("p2_visible_landmarks=${visibleGeometry.visibleLandmarkCount}")
            add("p2_removed_landmarks=${visibleGeometry.removedLandmarkCount}")
            add("p2_raw_contours=${visibleGeometry.rawContourCount}")
            add("p2_visible_contours=${visibleGeometry.visibleContourCount}")
            add("p2_split_contours=${visibleGeometry.splitContourCount}")
            add("p2_raw_triangles=${visibleGeometry.rawTriangleCount}")
            add("p2_visible_triangles=${visibleGeometry.visibleTriangleCount}")
            awareness.regions.values.forEach { region ->
                add(
                    "p2_region=${region.region.name}," +
                        "availability=${region.availability.name}," +
                        "evidence=${region.evidenceCount}," +
                        "geometry_allowed=${region.geometryEditAllowed}"
                )
            }
        },
        warnings = output.warnings + buildList {
            if (output.observation.faceCount > 1) {
                add(
                    "The source contains ${output.observation.faceCount} faces. " +
                        "Only face ${candidate.faceIndex + 1} is active; all other faces are ignored."
                )
            }
            if (output.observation.masks.isNotEmpty()) {
                add("Semantic masks are omitted from the focused crop until mask cropping is implemented.")
            }
            if (!awareness.fullFaceGeometryAllowed) {
                add(
                    "Full-face geometry is blocked for this pose. " +
                        "Only trusted visible-side and center geometry is retained."
                )
            }
            if (visibleGeometry.applied) {
                add(
                    "Hidden-side detector proposals were removed from editable geometry: " +
                        "landmarks=${visibleGeometry.removedLandmarkCount}, " +
                        "triangles=${visibleGeometry.removedTriangleCount}."
                )
            }
        }
    )
}

private fun SubjectObservation.focusOnFace(
    faceIndex: Int,
    crop: PortraitNormalizedRect
): SubjectObservation {
    val selectedPrefix = "face_${faceIndex}_"

    val selectedLandmarks = landmarks.values
        .filter { it.id.startsWith(selectedPrefix) }
        .associate { landmark ->
            val renamedId = landmark.id.renameFacePrefix(faceIndex)
            renamedId to landmark.copy(
                id = renamedId,
                point = landmark.point.remapInto(crop)
            )
        }
    val selectedLandmarkIds = selectedLandmarks.keys

    val selectedContours = contours.values
        .filter { it.id.startsWith(selectedPrefix) }
        .mapNotNull { contour ->
            val renamedVertices = contour.vertexIds
                .filter { it.startsWith(selectedPrefix) }
                .map { it.renameFacePrefix(faceIndex) }
                .filter { it in selectedLandmarkIds }
                .distinct()
            if (renamedVertices.isEmpty() || (contour.closed && renamedVertices.size < 3)) {
                null
            } else {
                val renamedId = contour.id.renameFacePrefix(faceIndex)
                renamedId to contour.copy(id = renamedId, vertexIds = renamedVertices)
            }
        }
        .toMap()

    val selectedRegions = regions.values
        .filter { it.id.startsWith(selectedPrefix) }
        .associateRenamed(faceIndex) { value, renamedId -> value.copy(id = renamedId) }

    val selectedClassifications = classifications.values
        .filter { it.id.startsWith(selectedPrefix) }
        .associateRenamed(faceIndex) { value, renamedId -> value.copy(id = renamedId) }

    val selectedMeshes = meshes.values
        .filter { it.id.startsWith(selectedPrefix) }
        .mapNotNull { mesh -> mesh.remap(faceIndex, selectedLandmarkIds) }
        .toMap()

    val selectedPose = facePoses[faceIndex]
        ?: if (faceCount == 1) pose else PoseObservation()

    return SubjectObservation(
        subjectCount = 1,
        faceCount = 1,
        bodyCount = 0,
        landmarks = selectedLandmarks,
        regions = selectedRegions,
        pose = selectedPose,
        facePoses = mapOf(0 to selectedPose),
        classifications = selectedClassifications,
        masks = emptyMap(),
        contours = selectedContours,
        meshes = selectedMeshes
    )
}

private fun MeshObservation.remap(
    faceIndex: Int,
    availableLandmarks: Set<String>
): Pair<String, MeshObservation>? {
    val renamedVertices = vertexIds
        .filter { it.startsWith("face_${faceIndex}_") }
        .map { it.renameFacePrefix(faceIndex) }
        .filter { it in availableLandmarks }
        .toSet()
    if (renamedVertices.isEmpty()) return null

    val renamedTriangles = triangles.mapNotNull { triangle ->
        val first = triangle.firstVertexId.renameFacePrefix(faceIndex)
        val second = triangle.secondVertexId.renameFacePrefix(faceIndex)
        val third = triangle.thirdVertexId.renameFacePrefix(faceIndex)
        if (setOf(first, second, third).all { it in renamedVertices }) {
            MeshTriangleObservation(first, second, third)
        } else {
            null
        }
    }
    val renamedId = id.renameFacePrefix(faceIndex)
    return renamedId to copy(
        id = renamedId,
        vertexIds = renamedVertices,
        triangles = renamedTriangles
    )
}

private inline fun <T> Iterable<T>.associateRenamed(
    faceIndex: Int,
    transform: (T, String) -> T
): Map<String, T> where T : Any = associate { value ->
    val id = when (value) {
        is RegionObservation -> value.id
        is ClassificationObservation -> value.id
        else -> error("Unsupported observation type")
    }
    val renamedId = id.renameFacePrefix(faceIndex)
    renamedId to transform(value, renamedId)
}

private fun String.renameFacePrefix(faceIndex: Int): String =
    replaceFirst("face_${faceIndex}_", "face_0_")

private fun NormalizedPoint3D.remapInto(crop: PortraitNormalizedRect): NormalizedPoint3D =
    NormalizedPoint3D(
        x = ((x - crop.left) / crop.width).coerceIn(0f, 1f),
        y = ((y - crop.top) / crop.height).coerceIn(0f, 1f),
        z = z
    )

private fun List<NormalizedPoint3D>.toNormalizedBounds(): PortraitNormalizedRect? {
    if (isEmpty()) return null
    val left = minOf { it.x }.coerceIn(0f, 1f)
    val top = minOf { it.y }.coerceIn(0f, 1f)
    val right = maxOf { it.x }.coerceIn(0f, 1f)
    val bottom = maxOf { it.y }.coerceIn(0f, 1f)
    if (right <= left || bottom <= top) return null
    return PortraitNormalizedRect(left, top, right, bottom)
}

private fun PortraitNormalizedRect.expandedForPortrait(): PortraitNormalizedRect {
    val horizontalPadding = width * 0.45f
    val topPadding = height * 0.55f
    val bottomPadding = height * 0.70f
    val expandedLeft = (left - horizontalPadding).coerceAtLeast(0f)
    val expandedTop = (top - topPadding).coerceAtLeast(0f)
    val expandedRight = (right + horizontalPadding).coerceAtMost(1f)
    val expandedBottom = (bottom + bottomPadding).coerceAtMost(1f)
    return PortraitNormalizedRect(expandedLeft, expandedTop, expandedRight, expandedBottom)
}

private fun Bitmap.cropNormalized(rect: PortraitNormalizedRect): Bitmap {
    val left = floor(rect.left * width).toInt().coerceIn(0, width - 1)
    val top = floor(rect.top * height).toInt().coerceIn(0, height - 1)
    val right = ceil(rect.right * width).toInt().coerceIn(left + 1, width)
    val bottom = ceil(rect.bottom * height).toInt().coerceIn(top + 1, height)
    return Bitmap.createBitmap(this, left, top, max(1, right - left), max(1, bottom - top))
}
