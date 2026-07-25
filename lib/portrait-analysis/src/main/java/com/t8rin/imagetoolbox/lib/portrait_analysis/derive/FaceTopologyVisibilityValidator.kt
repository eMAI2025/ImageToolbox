/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis.derive

import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ContourObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.NormalizedPoint3D
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Validates candidate face topology after visible-side filtering and before active overlay routing.
 *
 * Raw detector topology is never rewritten here. This validator only returns fail-closed reasons.
 * Any rejection therefore leaves the raw payload available diagnostically while active geometry is
 * removed by [FaceGeometryAcceptanceGate].
 */
object FaceTopologyVisibilityValidator {

    const val FINGERPRINT = "POSTAC_MASTER_FACE_TOPOLOGY_VISIBILITY_V1"

    // Structural discontinuity ceiling relative to the detected face diagonal. This is a generic
    // topology invariant, not a value tuned against one photograph.
    const val MAXIMUM_SEGMENT_TO_FACE_DIAGONAL = 0.75f

    data class Metrics(
        val checkedContourCount: Int,
        val checkedSegmentCount: Int,
        val selfIntersectionCount: Int,
        val unsupportedSpaceSegmentCount: Int,
        val brokenContourReferenceCount: Int,
        val brokenMeshReferenceCount: Int
    )

    data class Assessment(
        val accepted: Boolean,
        val reasons: Set<FaceGeometryRejectionReason>,
        val metrics: Metrics
    )

    fun evaluate(
        rawObservation: SubjectObservation,
        candidateObservation: SubjectObservation,
        awareness: FaceRegionAwareness
    ): Assessment {
        val reasons = linkedSetOf<FaceGeometryRejectionReason>()
        val landmarkIds = candidateObservation.landmarks.keys
        val partialPose = awareness.poseMode == FacePoseMode.HALF_PROFILE ||
            awareness.poseMode == FacePoseMode.PROFILE

        var checkedSegments = 0
        var selfIntersections = 0
        var unsupportedSegments = 0
        var brokenContourReferences = 0
        var brokenMeshReferences = 0

        if (partialPose && candidateObservation.contours.values.any(::isClosedFullFaceContour)) {
            reasons += FaceGeometryRejectionReason.CLOSED_FULL_OVAL_IN_PARTIAL_POSE
        }
        if (
            partialPose &&
            containsHiddenSideEyeOrBrow(candidateObservation, awareness.dominantImageSide)
        ) {
            reasons += FaceGeometryRejectionReason.HIDDEN_SIDE_EYE_OR_BROW_PRESENT
        }

        val bounds = rawObservation.faceBounds()
        candidateObservation.contours.values
            .filterNot { isBoundingGeometry(it.id) }
            .forEach { contour ->
                val missing = contour.vertexIds.count { it !in landmarkIds }
                if (missing > 0) {
                    brokenContourReferences += missing
                    reasons += FaceGeometryRejectionReason.BROKEN_LANDMARK_CONTOUR_REFERENCE
                    return@forEach
                }

                val segments = contour.segments(candidateObservation)
                checkedSegments += segments.size

                val intersections = segments.countSelfIntersections(contour.closed)
                if (intersections > 0) {
                    selfIntersections += intersections
                    reasons += FaceGeometryRejectionReason.CONTOUR_SELF_INTERSECTION
                }

                if (bounds != null) {
                    val unsupported = segments.count { segment ->
                        segment.length > bounds.diagonal * MAXIMUM_SEGMENT_TO_FACE_DIAGONAL ||
                            segment.midpoint.isOutsideVisibleSupport(
                                bounds = bounds,
                                awareness = awareness,
                                candidateObservation = candidateObservation
                            )
                    }
                    if (unsupported > 0) {
                        unsupportedSegments += unsupported
                        reasons += FaceGeometryRejectionReason.CONTOUR_CROSSES_UNSUPPORTED_SPACE
                    }
                }
            }

        candidateObservation.meshes.values.forEach { mesh ->
            val rejectedVertexIds = mesh.vertexIds.filter { it !in landmarkIds }
            val rejectedTriangles = mesh.triangles.count { triangle ->
                triangle.vertexIds.any { it !in landmarkIds || it !in mesh.vertexIds }
            }
            val brokenCount = rejectedVertexIds.size + rejectedTriangles
            if (brokenCount > 0) {
                brokenMeshReferences += brokenCount
                reasons += FaceGeometryRejectionReason.BROKEN_MESH_REFERENCE
                reasons += FaceGeometryRejectionReason.MESH_TRIANGLE_REFERENCES_REJECTED_VERTEX
            }
        }

        return Assessment(
            accepted = reasons.isEmpty(),
            reasons = reasons,
            metrics = Metrics(
                checkedContourCount = candidateObservation.contours.values.count {
                    !isBoundingGeometry(it.id)
                },
                checkedSegmentCount = checkedSegments,
                selfIntersectionCount = selfIntersections,
                unsupportedSpaceSegmentCount = unsupportedSegments,
                brokenContourReferenceCount = brokenContourReferences,
                brokenMeshReferenceCount = brokenMeshReferences
            )
        )
    }

    private data class Bounds(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float
    ) {
        val width: Float get() = (right - left).coerceAtLeast(0f)
        val height: Float get() = (bottom - top).coerceAtLeast(0f)
        val diagonal: Float get() = hypot(width, height)
        val centerX: Float get() = (left + right) / 2f
    }

    private data class Segment(
        val index: Int,
        val firstId: String,
        val secondId: String,
        val first: NormalizedPoint3D,
        val second: NormalizedPoint3D
    ) {
        val length: Float get() = hypot(second.x - first.x, second.y - first.y)
        val midpoint: NormalizedPoint3D
            get() = NormalizedPoint3D(
                x = (first.x + second.x) / 2f,
                y = (first.y + second.y) / 2f,
                z = null
            )
    }

    private fun SubjectObservation.faceBounds(): Bounds? {
        val contour = contours.values.firstOrNull { isBoundingGeometry(it.id) } ?: return null
        val points = contour.vertexIds.mapNotNull(landmarks::get).map { it.point }
        if (points.isEmpty()) return null
        return Bounds(
            left = points.minOf { it.x },
            top = points.minOf { it.y },
            right = points.maxOf { it.x },
            bottom = points.maxOf { it.y }
        ).takeIf { it.width > 0f && it.height > 0f }
    }

    private fun ContourObservation.segments(
        observation: SubjectObservation
    ): List<Segment> {
        if (vertexIds.size < 2) return emptyList()
        val pairs = vertexIds.zipWithNext().toMutableList()
        if (closed) pairs += vertexIds.last() to vertexIds.first()
        return pairs.mapIndexedNotNull { index, (firstId, secondId) ->
            val first = observation.landmarks[firstId]?.point ?: return@mapIndexedNotNull null
            val second = observation.landmarks[secondId]?.point ?: return@mapIndexedNotNull null
            Segment(index, firstId, secondId, first, second)
        }
    }

    private fun List<Segment>.countSelfIntersections(closed: Boolean): Int {
        var count = 0
        for (firstIndex in indices) {
            for (secondIndex in (firstIndex + 1) until size) {
                val first = this[firstIndex]
                val second = this[secondIndex]
                if (first.sharesEndpointWith(second)) continue
                if (closed && firstIndex == 0 && secondIndex == lastIndex) continue
                if (segmentsIntersect(first.first, first.second, second.first, second.second)) {
                    count += 1
                }
            }
        }
        return count
    }

    private fun Segment.sharesEndpointWith(other: Segment): Boolean =
        firstId == other.firstId ||
            firstId == other.secondId ||
            secondId == other.firstId ||
            secondId == other.secondId

    private fun segmentsIntersect(
        a: NormalizedPoint3D,
        b: NormalizedPoint3D,
        c: NormalizedPoint3D,
        d: NormalizedPoint3D
    ): Boolean {
        val o1 = orientation(a, b, c)
        val o2 = orientation(a, b, d)
        val o3 = orientation(c, d, a)
        val o4 = orientation(c, d, b)
        return o1 * o2 < 0f && o3 * o4 < 0f
    }

    private fun orientation(
        a: NormalizedPoint3D,
        b: NormalizedPoint3D,
        c: NormalizedPoint3D
    ): Float = (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)

    private fun NormalizedPoint3D.isOutsideVisibleSupport(
        bounds: Bounds,
        awareness: FaceRegionAwareness,
        candidateObservation: SubjectObservation
    ): Boolean {
        if (x !in bounds.left..bounds.right || y !in bounds.top..bounds.bottom) return true
        if (
            awareness.poseMode != FacePoseMode.HALF_PROFILE &&
            awareness.poseMode != FacePoseMode.PROFILE
        ) {
            return false
        }
        val axisX = candidateObservation.landmarks.values
            .filter { landmark ->
                val id = landmark.id.lowercase()
                id.contains("nose_base") || id.contains("nose_tip") || id.endsWith("_nose")
            }
            .map { it.point.x }
            .takeIf { it.isNotEmpty() }
            ?.average()
            ?.toFloat()
            ?: bounds.centerX
        val corridor = bounds.width * 0.12f
        return when (awareness.dominantImageSide) {
            FaceImageSide.LEFT -> x > axisX + corridor
            FaceImageSide.RIGHT -> x < axisX - corridor
            else -> true
        }
    }

    private fun containsHiddenSideEyeOrBrow(
        observation: SubjectObservation,
        activeSide: FaceImageSide
    ): Boolean {
        val hiddenTokens = when (activeSide) {
            FaceImageSide.LEFT -> listOf("right_eye", "right_eyebrow", "right_brow", "eye_brow_right")
            FaceImageSide.RIGHT -> listOf("left_eye", "left_eyebrow", "left_brow", "eye_brow_left")
            else -> return true
        }
        return observation.landmarks.keys.any { id -> hiddenTokens.any(id.lowercase()::contains) } ||
            observation.contours.keys.any { id -> hiddenTokens.any(id.lowercase()::contains) }
    }

    private fun isClosedFullFaceContour(contour: ContourObservation): Boolean {
        if (!contour.closed || isBoundingGeometry(contour.id)) return false
        val id = contour.id.lowercase()
        return id.contains("face_oval") ||
            id.contains("contour_face") ||
            (id.contains("face") && id.contains("contour"))
    }

    private fun isBoundingGeometry(id: String): Boolean {
        val value = id.lowercase()
        return value.contains("bounding_box") || value.contains("bbox")
    }
}