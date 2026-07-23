/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis_mlkit.face

import com.google.mlkit.vision.facemesh.FaceMesh
import com.google.mlkit.vision.facemesh.FaceMeshDetection
import com.google.mlkit.vision.facemesh.FaceMeshDetector
import com.t8rin.imagetoolbox.lib.portrait_analysis.engine.PortraitObservationEngine
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ObservationBackend
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis_mlkit.MlKitImageInput
import com.t8rin.imagetoolbox.lib.portrait_analysis_mlkit.awaitResult

/** Observation-only ML Kit Face Mesh backend for the Market build. */
class MlKitFaceMeshObservationEngine(
    private val detector: FaceMeshDetector = FaceMeshDetection.getClient()
) : PortraitObservationEngine<MlKitImageInput>, AutoCloseable {

    override val backend: ObservationBackend = ObservationBackend.ML_KIT_FACE_MESH

    override suspend fun observe(input: MlKitImageInput): SubjectObservation {
        val detectedFaces = detector.process(input.image).awaitResult()
        return MlKitFaceMeshObservationMapper.map(
            MlKitFaceMeshSnapshot(
                imageWidth = input.width,
                imageHeight = input.height,
                faces = detectedFaces.map { face ->
                    MlKitFaceSnapshot(
                        points = face.allPoints.map { point ->
                            MlKitFaceMeshPointSnapshot(
                                index = point.index,
                                xPixels = point.position.x,
                                yPixels = point.position.y,
                                zPixels = point.position.z
                            )
                        },
                        triangles = face.allTriangles.map { triangle ->
                            val points = triangle.allPoints
                            require(points.size == 3) {
                                "ML Kit face mesh triangle must contain exactly three points"
                            }
                            MlKitFaceMeshTriangleSnapshot(
                                firstPointIndex = points[0].index,
                                secondPointIndex = points[1].index,
                                thirdPointIndex = points[2].index
                            )
                        },
                        contours = CONTOUR_SPECS.mapNotNull { spec ->
                            val pointIndices = face.getPoints(spec.type)
                                .map { it.index }
                                .distinct()
                            pointIndices.takeIf { it.isNotEmpty() }?.let {
                                MlKitFaceMeshContourSnapshot(
                                    id = spec.id,
                                    pointIndices = it,
                                    closed = spec.closed
                                )
                            }
                        }
                    )
                }
            )
        )
    }

    override fun close() {
        detector.close()
    }

    private data class ContourSpec(
        val id: String,
        val type: Int,
        val closed: Boolean
    )

    private companion object {
        val CONTOUR_SPECS = listOf(
            ContourSpec("face_oval", FaceMesh.FACE_OVAL, closed = true),
            ContourSpec("left_eye", FaceMesh.LEFT_EYE, closed = true),
            ContourSpec("right_eye", FaceMesh.RIGHT_EYE, closed = true),
            ContourSpec("left_eyebrow_top", FaceMesh.LEFT_EYEBROW_TOP, closed = false),
            ContourSpec(
                "left_eyebrow_bottom",
                FaceMesh.LEFT_EYEBROW_BOTTOM,
                closed = false
            ),
            ContourSpec("right_eyebrow_top", FaceMesh.RIGHT_EYEBROW_TOP, closed = false),
            ContourSpec(
                "right_eyebrow_bottom",
                FaceMesh.RIGHT_EYEBROW_BOTTOM,
                closed = false
            ),
            ContourSpec("upper_lip_top", FaceMesh.UPPER_LIP_TOP, closed = false),
            ContourSpec("upper_lip_bottom", FaceMesh.UPPER_LIP_BOTTOM, closed = false),
            ContourSpec("lower_lip_top", FaceMesh.LOWER_LIP_TOP, closed = false),
            ContourSpec("lower_lip_bottom", FaceMesh.LOWER_LIP_BOTTOM, closed = false),
            ContourSpec("nose_bridge", FaceMesh.NOSE_BRIDGE, closed = false)
        )
    }
}
