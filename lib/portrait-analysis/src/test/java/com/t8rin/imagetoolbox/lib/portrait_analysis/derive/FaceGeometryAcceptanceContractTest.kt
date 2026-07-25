/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis.derive

import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceGeometryAcceptanceContractTest {

    @Test
    fun `partial legacy label never grants geometry after canonical rejection`() {
        val result = rejectedResult()

        val projection = FaceGeometryLegacyProjection.from(
            legacyVisualStatus = "PARTIAL",
            result = result
        )

        assertFalse(projection.geometryUsable)
        assertTrue(
            projection.canonicalStatus ==
                FaceGeometryAcceptanceStatus.FACE_DETECTED_GEOMETRY_REJECTED
        )
    }

    @Test
    fun `accepted result requires raw and active geometry`() {
        val observation = faceObservation()
        val result = FaceGeometryAcceptanceResult(
            status = FaceGeometryAcceptanceStatus.FACE_DETECTED_GEOMETRY_ACCEPTED,
            rawEvidence = observation,
            activeGeometry = observation,
            reasons = emptyList(),
            provenance = provenance()
        )

        assertTrue(result.faceDetected)
        assertTrue(result.geometryUsable)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejected result cannot carry active geometry`() {
        val observation = faceObservation()
        FaceGeometryAcceptanceResult(
            status = FaceGeometryAcceptanceStatus.FACE_DETECTED_GEOMETRY_REJECTED,
            rawEvidence = observation,
            activeGeometry = observation,
            reasons = listOf(
                FaceGeometryReasonEvidence(
                    code = FaceGeometryRejectionReason.UNSUPPORTED_PARTIAL_OR_STRONG_PROFILE,
                    severity = FaceGeometryReasonSeverity.ERROR
                )
            ),
            provenance = provenance()
        )
    }

    @Test
    fun `existing gate decision maps to canonical severity and provenance`() {
        val observation = faceObservation()
        val result = FaceGeometryAcceptanceDecision(
            status = FaceGeometryAcceptanceStatus.FACE_DETECTED_GEOMETRY_REJECTED,
            rawObservation = observation,
            activeObservation = null,
            reasons = setOf(
                FaceGeometryRejectionReason.CLOSED_FULL_OVAL_IN_PARTIAL_POSE,
                FaceGeometryRejectionReason.HIDDEN_SIDE_EYE_OR_BROW_PRESENT
            )
        ).toCanonicalResult(provenance())

        assertFalse(result.geometryUsable)
        assertTrue(result.rawEvidence === observation)
        assertTrue(result.activeGeometry == null)
        assertTrue(result.reasons.all { it.severity == FaceGeometryReasonSeverity.ERROR })
        assertTrue(result.provenance.backendId == "ML_KIT_FACE_DETECTION")
    }

    private fun rejectedResult() = FaceGeometryAcceptanceResult(
        status = FaceGeometryAcceptanceStatus.FACE_DETECTED_GEOMETRY_REJECTED,
        rawEvidence = faceObservation(),
        activeGeometry = null,
        reasons = listOf(
            FaceGeometryReasonEvidence(
                code = FaceGeometryRejectionReason.UNSUPPORTED_PARTIAL_OR_STRONG_PROFILE,
                severity = FaceGeometryReasonSeverity.ERROR
            )
        ),
        provenance = provenance()
    )

    private fun faceObservation() = SubjectObservation(
        subjectCount = 1,
        faceCount = 1,
        bodyCount = 0
    )

    private fun provenance() = FaceGeometryAcceptanceProvenance(
        backendId = "ML_KIT_FACE_DETECTION",
        backendVersion = "unknown",
        modelId = null,
        modelSha256 = null,
        gateVersion = FACE_GEOMETRY_ACCEPTANCE_GATE_VERSION,
        buildBranch = "feature/postac-master-a1-b1-device-integration",
        buildCommit = "test-commit"
    )
}
