/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis_mlkit.face

import com.google.mlkit.vision.facemesh.FaceMeshDetection
import com.google.mlkit.vision.facemesh.FaceMeshDetector
import com.google.mlkit.vision.facemesh.FaceMeshDetectorOptions
import com.t8rin.imagetoolbox.lib.portrait_analysis.engine.PortraitObservationEngine
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ObservationBackend
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis_mlkit.MlKitImageInput
import com.t8rin.imagetoolbox.lib.portrait_analysis_mlkit.awaitResult

/** Observation-only ML Kit Face Mesh backend for the Market build. */
class MlKitFaceMeshObservationEngine(
    private val detector: FaceMeshDetector = FaceMeshDetection.getClient(
        FaceMeshDetectorOptions.DEFAULT_OPTIONS
    )
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
                        }
                    )
                }
            )
        )
    }

    override fun close() {
        detector.close()
    }
}
