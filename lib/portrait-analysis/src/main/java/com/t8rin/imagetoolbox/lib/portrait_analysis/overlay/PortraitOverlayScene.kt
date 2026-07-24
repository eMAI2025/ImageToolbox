/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis.overlay

import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ConfidenceMask
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ConfidenceSource
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.NormalizedPoint3D
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ObservationBackend
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.VisibilityState

data class OverlayPoint(
    val id: String,
    val position: NormalizedPoint3D,
    val backend: ObservationBackend,
    val confidence: Float?,
    val confidenceSource: ConfidenceSource,
    val visibility: VisibilityState
)

data class OverlayPolyline(
    val id: String,
    val points: List<NormalizedPoint3D>,
    val closed: Boolean,
    val backend: ObservationBackend,
    val confidence: Float?,
    val confidenceSource: ConfidenceSource
) {
    init {
        require(id.isNotBlank())
        require(points.isNotEmpty())
        require(!closed || points.size >= 3)
    }
}

data class OverlayTriangle(
    val id: String,
    val first: NormalizedPoint3D,
    val second: NormalizedPoint3D,
    val third: NormalizedPoint3D,
    val backend: ObservationBackend
)

data class OverlayMask(
    val id: String,
    val mask: ConfidenceMask,
    val backend: ObservationBackend
)

data class PortraitOverlayScene(
    val points: List<OverlayPoint>,
    val polylines: List<OverlayPolyline>,
    val triangles: List<OverlayTriangle>,
    val masks: List<OverlayMask>
) {
    val pointCount: Int get() = points.size
    val polylineCount: Int get() = polylines.size
    val triangleCount: Int get() = triangles.size
    val maskCount: Int get() = masks.size
}

data class PortraitOverlayOptions(
    val includedBackends: Set<ObservationBackend> = ObservationBackend.entries.toSet(),
    val includePoints: Boolean = true,
    val includeContours: Boolean = true,
    val includeMeshTriangles: Boolean = true,
    val includeMasks: Boolean = true,
    val includeOutsideFramePoints: Boolean = false
)

/** Creates deterministic overlay primitives from a validated subject observation. */
object PortraitOverlaySceneBuilder {

    fun build(
        observation: SubjectObservation,
        options: PortraitOverlayOptions = PortraitOverlayOptions()
    ): PortraitOverlayScene {
        val points = if (options.includePoints) {
            observation.landmarks.values
                .asSequence()
                .filter { it.backend in options.includedBackends }
                .filter {
                    options.includeOutsideFramePoints ||
                        it.visibility != VisibilityState.OUTSIDE_FRAME
                }
                .sortedBy { it.id }
                .map {
                    OverlayPoint(
                        id = it.id,
                        position = it.point,
                        backend = it.backend,
                        confidence = it.confidence,
                        confidenceSource = it.confidenceSource,
                        visibility = it.visibility
                    )
                }
                .toList()
        } else {
            emptyList()
        }

        val polylines = if (options.includeContours) {
            observation.contours.values
                .asSequence()
                .filter { it.backend in options.includedBackends }
                .sortedBy { it.id }
                .map { contour ->
                    OverlayPolyline(
                        id = contour.id,
                        points = contour.vertexIds.map { vertexId ->
                            observation.landmarks.getValue(vertexId).point
                        },
                        closed = contour.closed,
                        backend = contour.backend,
                        confidence = contour.confidence,
                        confidenceSource = contour.confidenceSource
                    )
                }
                .toList()
        } else {
            emptyList()
        }

        val triangles = if (options.includeMeshTriangles) {
            observation.meshes.values
                .asSequence()
                .filter { it.backend in options.includedBackends }
                .sortedBy { it.id }
                .flatMap { mesh ->
                    mesh.triangles.asSequence().mapIndexed { index, triangle ->
                        OverlayTriangle(
                            id = "${mesh.id}_triangle_$index",
                            first = observation.landmarks.getValue(
                                triangle.firstVertexId
                            ).point,
                            second = observation.landmarks.getValue(
                                triangle.secondVertexId
                            ).point,
                            third = observation.landmarks.getValue(
                                triangle.thirdVertexId
                            ).point,
                            backend = mesh.backend
                        )
                    }
                }
                .toList()
        } else {
            emptyList()
        }

        val masks = if (options.includeMasks) {
            observation.masks.values
                .asSequence()
                .filter { it.backend in options.includedBackends }
                .sortedBy { it.id }
                .map {
                    OverlayMask(
                        id = it.id,
                        mask = it.mask,
                        backend = it.backend
                    )
                }
                .toList()
        } else {
            emptyList()
        }

        return PortraitOverlayScene(
            points = points,
            polylines = polylines,
            triangles = triangles,
            masks = masks
        )
    }
}
