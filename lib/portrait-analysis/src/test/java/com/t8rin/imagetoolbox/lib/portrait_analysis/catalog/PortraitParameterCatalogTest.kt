/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis.catalog

import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ConfidenceSource
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.LandmarkObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.NormalizedPoint3D
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ObservationBackend
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.PoseObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.RegionObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.VisibilityState
import com.t8rin.imagetoolbox.lib.portrait_analysis.report.ParameterAvailability
import com.t8rin.imagetoolbox.lib.portrait_analysis.report.ParameterGateReportBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PortraitParameterCatalogTest {

    @Test
    fun `catalog ids are unique and aligned with gate ids`() {
        val definitions = DefaultPortraitParameterCatalog.definitions

        assertEquals(definitions.size, definitions.map { it.id }.distinct().size)
        assertTrue(definitions.all { it.id == it.gateSpec.parameterId })
    }

    @Test
    fun `mouth corner control is bidirectional even though Xiaomi exposes smile intensity`() {
        val definition = DefaultPortraitParameterCatalog.definitions
            .single { it.id == PortraitParameterId.MOUTH_CORNER_CURVE }

        assertEquals(PortraitParameterDirection.BIDIRECTIONAL, definition.direction)
        assertEquals(PortraitParameterRisk.HIGH, definition.risk)
    }

    @Test
    fun `raw face mesh alone cannot enable semantic mouth correction`() {
        val observation = SubjectObservation(
            subjectCount = 1,
            faceCount = 1,
            bodyCount = 0,
            landmarks = mapOf(
                "face_0_mesh_0" to landmark(
                    id = "face_0_mesh_0",
                    backend = ObservationBackend.ML_KIT_FACE_MESH,
                    confidence = null,
                    confidenceSource = ConfidenceSource.UNAVAILABLE
                )
            ),
            regions = mapOf(
                "face_0_mesh_region" to RegionObservation(
                    id = "face_0_mesh_region",
                    confidence = null,
                    confidenceSource = ConfidenceSource.UNAVAILABLE,
                    occlusion = null,
                    pixelCoverage = 0.1f,
                    backend = ObservationBackend.ML_KIT_FACE_MESH
                )
            ),
            pose = PoseObservation(yawDegrees = 0f, pitchDegrees = 0f, rollDegrees = 0f)
        )

        val report = ParameterGateReportBuilder.build(
            specs = DefaultPortraitParameterCatalog.gateSpecs,
            observation = observation
        )

        assertEquals(
            ParameterAvailability.DISABLED,
            report.entry(PortraitParameterId.MOUTH_CORNER_CURVE)?.availability
        )
    }

    @Test
    fun `body landmarks without anatomical contours remain insufficient`() {
        val bodyLandmarks = setOf(
            PortraitLandmarkId.LEFT_SHOULDER,
            PortraitLandmarkId.RIGHT_SHOULDER
        ).associateWith { id ->
            landmark(
                id = id,
                backend = ObservationBackend.ML_KIT_POSE,
                confidence = 0.95f,
                confidenceSource = ConfidenceSource.DIRECT
            )
        }
        val observation = SubjectObservation(
            subjectCount = 1,
            faceCount = 0,
            bodyCount = 1,
            landmarks = bodyLandmarks,
            regions = mapOf(
                PortraitRegionId.SUBJECT_MASK to RegionObservation(
                    id = PortraitRegionId.SUBJECT_MASK,
                    confidence = 0.90f,
                    confidenceSource = ConfidenceSource.DERIVED,
                    occlusion = null,
                    pixelCoverage = 0.5f,
                    backend = ObservationBackend.ML_KIT_SELFIE_SEGMENTATION
                )
            )
        )

        val report = ParameterGateReportBuilder.build(
            specs = DefaultPortraitParameterCatalog.gateSpecs,
            observation = observation
        )

        val shoulder = report.entry(PortraitParameterId.SHOULDER_WIDTH)
        assertEquals(ParameterAvailability.DISABLED, shoulder?.availability)
        assertTrue(
            shoulder?.failures.orEmpty().any {
                it.itemId == PortraitRegionId.SHOULDER_CONTOUR
            }
        )
    }

    private fun landmark(
        id: String,
        backend: ObservationBackend,
        confidence: Float?,
        confidenceSource: ConfidenceSource
    ) = LandmarkObservation(
        id = id,
        point = NormalizedPoint3D(0.5f, 0.5f),
        confidence = confidence,
        confidenceSource = confidenceSource,
        visibility = VisibilityState.VISIBLE,
        backend = backend
    )
}
