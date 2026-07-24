/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis.derive

import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ContourObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.LandmarkObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.MeshObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.NormalizedPoint3D
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation
import kotlin.math.abs

/**
 * Result of converting a detector's full-face proposal into trusted visible geometry.
 *
 * The raw detector may still predict landmarks on the hidden side of a profile. Those points are
 * useful as detector diagnostics, but they must not become editable geometry and must not be drawn
 * as if they were observed. This filter removes hidden-side points, splits crossing contours into
 * open visible segments and drops mesh triangles that depend on removed vertices.
 */
data class FaceVisibleGeometryResult(
    val observation: SubjectObservation,
    val applied: Boolean,
    val activeImageSide: FaceImageSide,
    val rawLandmarkCount: Int,
    val visibleLandmarkCount: Int,
    val rawContourCount: Int,
    val visibleContourCount: Int,
    val splitContourCount: Int,
    val rawTriangleCount: Int,
    val visibleTriangleCount: Int
) {
    val removedLandmarkCount: Int
        get() = (rawLandmarkCount - visibleLandmarkCount).coerceAtLeast(0)

    val removedContourCount: Int
        get() = (rawContourCount - visibleContourCount + splitContourCount).coerceAtLeast(0)

    val removedTriangleCount: Int
        get() = (rawTriangleCount - visibleTriangleCount).coerceAtLeast(0)
}

object FaceVisibleGeometryFilter {

    private const val HALF_PROFILE_CENTER_CORRIDOR_FACTOR = 0.11f
    private const val PROFILE_CENTER_CORRIDOR_FACTOR = 0.07f

    fun filter(
        observation: SubjectObservation,
        awareness: FaceRegionAwareness
    ): FaceVisibleGeometryResult {
        val rawTriangleCount = observation.meshes.values.sumOf { it.triangles.size }
        val shouldFilter = awareness.poseMode == FacePoseMode.HALF_PROFILE ||
            awareness.poseMode == FacePoseMode.PROFILE
        val side = awareness.dominantImageSide

        if (!shouldFilter || side !in setOf(FaceImageSide.LEFT, FaceImageSide.RIGHT)) {
            return FaceVisibleGeometryResult(
                observation = observation,
                applied = false,
                activeImageSide = side,
                rawLandmarkCount = observation.landmarks.size,
                visibleLandmarkCount = observation.landmarks.size,
                rawContourCount = observation.contours.size,
                visibleContourCount = observation.contours.size,
                splitContourCount = 0,
                rawTriangleCount = rawTriangleCount,
                visibleTriangleCount = rawTriangleCount
            )
        }

        val bounds = observation.faceBounds() ?: return FaceVisibleGeometryResult(
            observation = observation,
            applied = false,
            activeImageSide = side,
            rawLandmarkCount = observation.landmarks.size,
            visibleLandmarkCount = observation.landmarks.size,
            rawContourCount = observation.contours.size,
            visibleContourCount = observation.contours.size,
            splitContourCount = 0,
            rawTriangleCount = rawTriangleCount,
            visibleTriangleCount = rawTriangleCount
        )
        val axisX = observation.faceAxisX(bounds)
        val corridor = bounds.width * when (awareness.poseMode) {
            FacePoseMode.PROFILE -> PROFILE_CENTER_CORRIDOR_FACTOR
            else -> HALF_PROFILE_CENTER_CORRIDOR_FACTOR
        }

        fun pointIsVisible(landmark: LandmarkObservation): Boolean {
            if (isBoundingGeometry(landmark.id)) return true
            if (isCenterGeometry(landmark.id)) return true
            return landmark.point.isOnVisibleSide(side, axisX, corridor)
        }

        val baseVisibleLandmarkIds = observation.landmarks.values
            .filter(::pointIsVisible)
            .mapTo(linkedSetOf()) { it.id }

        val visibleContours = linkedMapOf<String, ContourObservation>()
        var splitContourCount = 0

        observation.contours.values.forEach { contour ->
            if (isBoundingGeometry(contour.id)) {
                visibleContours[contour.id] = contour
                return@forEach
            }

            val runs = contour.visibleRuns(baseVisibleLandmarkIds)
            when {
                runs.isEmpty() -> Unit
                runs.size == 1 && runs.single().size == contour.vertexIds.size -> {
                    visibleContours[contour.id] = contour
                }
                else -> {
                    runs.forEachIndexed { index, run ->
                        if (run.size >= 2) {
                            val segmentId = "${contour.id}_visible_segment_$index"
                            visibleContours[segmentId] = contour.copy(
                                id = segmentId,
                                vertexIds = run,
                                closed = false
                            )
                            splitContourCount += 1
                        }
                    }
                }
            }
        }

        val contourVertexIds = visibleContours.values
            .flatMapTo(linkedSetOf()) { it.vertexIds }
        val visibleLandmarkIds = linkedSetOf<String>().apply {
            addAll(baseVisibleLandmarkIds)
            addAll(contourVertexIds)
        }

        val visibleMeshes = observation.meshes.values.mapNotNull { mesh ->
            mesh.retainVertices(visibleLandmarkIds)
        }.associateBy { it.id }
        visibleMeshes.values.forEach { visibleLandmarkIds += it.vertexIds }

        val visibleLandmarks = observation.landmarks
            .filterKeys { it in visibleLandmarkIds }

        val visibleObservation = observation.copy(
            landmarks = visibleLandmarks,
            contours = visibleContours,
            meshes = visibleMeshes
        )
        val visibleTriangleCount = visibleMeshes.values.sumOf { it.triangles.size }

        return FaceVisibleGeometryResult(
            observation = visibleObservation,
            applied = true,
            activeImageSide = side,
            rawLandmarkCount = observation.landmarks.size,
            visibleLandmarkCount = visibleObservation.landmarks.size,
            rawContourCount = observation.contours.size,
            visibleContourCount = visibleObservation.contours.size,
            splitContourCount = splitContourCount,
            rawTriangleCount = rawTriangleCount,
            visibleTriangleCount = visibleTriangleCount
        )
    }

    private data class Bounds(
        val left: Float,
        val right: Float
    ) {
        val width: Float
            get() = (right - left).coerceAtLeast(0f)

        val centerX: Float
            get() = (left + right) / 2f
    }

    private fun SubjectObservation.faceBounds(): Bounds? {
        val bounding = contours.values.firstOrNull(::isBoundingContour)
        val points = bounding?.vertexIds
            ?.mapNotNull(landmarks::get)
            ?.map { it.point }
            ?.takeIf { it.isNotEmpty() }
            ?: landmarks.values
                .filterNot { isBoundingGeometry(it.id) }
                .map { it.point }
                .takeIf { it.isNotEmpty() }
            ?: return null

        return Bounds(
            left = points.minOf { it.x }.coerceIn(0f, 1f),
            right = points.maxOf { it.x }.coerceIn(0f, 1f)
        )
    }

    private fun SubjectObservation.faceAxisX(bounds: Bounds): Float {
        val nosePoints = landmarks.values.filter {
            val id = it.id.lowercase()
            id.contains("nose_base") ||
                id.contains("nose_bottom") ||
                id.contains("nose_bridge") ||
                id.endsWith("_nose")
        }
        return nosePoints
            .map { it.point.x }
            .average()
            .takeUnless(Double::isNaN)
            ?.toFloat()
            ?.coerceIn(bounds.left, bounds.right)
            ?: bounds.centerX
    }

    private fun NormalizedPoint3D.isOnVisibleSide(
        side: FaceImageSide,
        axisX: Float,
        corridor: Float
    ): Boolean = when (side) {
        FaceImageSide.LEFT -> x <= axisX + corridor
        FaceImageSide.RIGHT -> x >= axisX - corridor
        else -> true
    }

    private fun ContourObservation.visibleRuns(
        visibleLandmarkIds: Set<String>
    ): List<List<String>> {
        if (vertexIds.all { it in visibleLandmarkIds }) return listOf(vertexIds)
        if (vertexIds.none { it in visibleLandmarkIds }) return emptyList()

        return if (closed) {
            circularRuns(vertexIds, visibleLandmarkIds)
        } else {
            linearRuns(vertexIds, visibleLandmarkIds)
        }
    }

    private fun linearRuns(
        vertexIds: List<String>,
        visibleLandmarkIds: Set<String>
    ): List<List<String>> {
        val result = mutableListOf<List<String>>()
        var current = mutableListOf<String>()
        vertexIds.forEach { id ->
            if (id in visibleLandmarkIds) {
                current += id
            } else if (current.isNotEmpty()) {
                result += current
                current = mutableListOf()
            }
        }
        if (current.isNotEmpty()) result += current
        return result.filter { it.size >= 2 }
    }

    private fun circularRuns(
        vertexIds: List<String>,
        visibleLandmarkIds: Set<String>
    ): List<List<String>> {
        val firstHiddenIndex = vertexIds.indexOfFirst { it !in visibleLandmarkIds }
        if (firstHiddenIndex < 0) return listOf(vertexIds)

        val rotated = List(vertexIds.size) { offset ->
            vertexIds[(firstHiddenIndex + 1 + offset) % vertexIds.size]
        }
        return linearRuns(rotated, visibleLandmarkIds)
    }

    private fun MeshObservation.retainVertices(
        visibleLandmarkIds: Set<String>
    ): MeshObservation? {
        val visibleTriangles = triangles.filter { triangle ->
            triangle.vertexIds.all { it in visibleLandmarkIds }
        }
        val referencedVertices = visibleTriangles
            .flatMapTo(linkedSetOf()) { it.vertexIds }
        val retainedVertices = vertexIds.filterTo(linkedSetOf()) { it in visibleLandmarkIds }
        retainedVertices += referencedVertices
        if (retainedVertices.isEmpty()) return null

        return copy(
            vertexIds = retainedVertices,
            triangles = visibleTriangles
        )
    }

    private fun isCenterGeometry(id: String): Boolean {
        val value = id.lowercase()
        return value.contains("nose") ||
            value.contains("mouth") ||
            value.contains("lip") ||
            value.contains("chin") ||
            value.endsWith("_center")
    }

    private fun isBoundingContour(contour: ContourObservation): Boolean =
        isBoundingGeometry(contour.id)

    private fun isBoundingGeometry(id: String): Boolean {
        val value = id.lowercase()
        return value.contains("bounding_box") || value.contains("bbox")
    }
}
