/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis.derive

import com.t8rin.imagetoolbox.lib.portrait_analysis.catalog.PortraitLandmarkId
import com.t8rin.imagetoolbox.lib.portrait_analysis.catalog.PortraitParameterId
import com.t8rin.imagetoolbox.lib.portrait_analysis.catalog.PortraitRegionId
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ConfidenceMask
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ConfidenceSource
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.LandmarkObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.NormalizedPoint3D
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ObservationBackend
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SemanticMaskObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.VisibilityState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SilhouetteEditContractTest {

    @Test
    fun `missing A1 device pass blocks silhouette edit capability`() {
        val decision = SilhouetteEditContract.evaluate(
            validEvidence().copy(a1DevicePass = false)
        )

        assertEquals(SilhouetteEditContract.Status.BLOCKED, decision.status)
        assertNull(decision.request)
        assertTrue(
            SilhouetteEditContract.Blocker.A1_DEVICE_PASS_MISSING in decision.blockers
        )
    }

    @Test
    fun `waist edit requires accepted waist region and all structural anchors`() {
        val missingRegion = SilhouetteEditContract.evaluate(
            validEvidence().copy(
                acceptedRegionIds = validEvidence().acceptedRegionIds - PortraitRegionId.WAIST_CONTOUR
            )
        )
        val bodyWithoutRightHip = acceptedBody().copy(
            landmarks = acceptedBody().landmarks - PortraitLandmarkId.RIGHT_HIP
        )
        val missingAnchor = SilhouetteEditContract.evaluate(
            validEvidence().copy(acceptedObservation = bodyWithoutRightHip)
        )

        assertTrue(
            SilhouetteEditContract.Blocker.REQUIRED_BODY_REGION_NOT_ACCEPTED in
                missingRegion.blockers
        )
        assertTrue(
            SilhouetteEditContract.Blocker.REQUIRED_LANDMARK_NOT_ACCEPTED in
                missingAnchor.blockers
        )
    }

    @Test
    fun `arm edit with missing wrist remains fail closed`() {
        val accepted = acceptedBody().copy(
            landmarks = acceptedBody().landmarks - PortraitLandmarkId.LEFT_WRIST
        )
        val decision = SilhouetteEditContract.evaluate(
            validEvidence().copy(
                acceptedObservation = accepted,
                parameterId = PortraitParameterId.ARM_WIDTH,
                acceptedRegionIds = setOf(
                    PortraitRegionId.SUBJECT_MASK,
                    PortraitRegionId.ARM_CONTOURS
                )
            )
        )

        assertEquals(SilhouetteEditContract.Status.BLOCKED, decision.status)
        assertTrue(
            SilhouetteEditContract.Blocker.REQUIRED_LANDMARK_NOT_ACCEPTED in decision.blockers
        )
    }

    @Test
    fun `leg length cannot use negative bidirectional value`() {
        val decision = SilhouetteEditContract.evaluate(
            validEvidence().copy(
                parameterId = PortraitParameterId.LEG_LENGTH,
                normalizedValue = -0.10f,
                acceptedRegionIds = setOf(
                    PortraitRegionId.SUBJECT_MASK,
                    PortraitRegionId.HIP_CONTOUR,
                    PortraitRegionId.LEG_CONTOURS
                )
            )
        )

        assertEquals(SilhouetteEditContract.Status.BLOCKED, decision.status)
        assertTrue(
            SilhouetteEditContract.Blocker.REQUEST_VALUE_OUT_OF_DOMAIN in decision.blockers
        )
    }

    @Test
    fun `background protection evidence is mandatory before any Stage B adapter token`() {
        val decision = SilhouetteEditContract.evaluate(
            validEvidence().copy(backgroundProtectionEvidenceReady = false)
        )

        assertEquals(SilhouetteEditContract.Status.BLOCKED, decision.status)
        assertTrue(
            SilhouetteEditContract.Blocker.BACKGROUND_PROTECTION_EVIDENCE_MISSING in
                decision.blockers
        )
    }

    @Test
    fun `accepted isolated waist evidence creates capability token without deformation`() {
        val evidence = validEvidence()
        val decision = SilhouetteEditContract.evaluate(evidence)

        assertEquals(
            SilhouetteEditContract.Status.READY_FOR_ISOLATED_STAGE_B_ADAPTER,
            decision.status
        )
        assertTrue(decision.blockers.isEmpty())
        val request = requireNotNull(decision.request)
        assertSame(evidence.acceptedObservation, request.acceptedObservation)
        assertEquals(PortraitParameterId.WAIST_WIDTH, request.parameterSpec.parameterId)
        assertEquals(0.20f, request.normalizedValue)
        assertEquals("feature/test", request.provenanceBranch)
        assertEquals("abc123", request.provenanceCommit)
    }

    @Test
    fun `unsupported parameter cannot fabricate an adapter request`() {
        val decision = SilhouetteEditContract.evaluate(
            validEvidence().copy(parameterId = "body_magic_unknown")
        )

        assertEquals(SilhouetteEditContract.Status.BLOCKED, decision.status)
        assertNull(decision.request)
        assertTrue(SilhouetteEditContract.Blocker.PARAMETER_UNSUPPORTED in decision.blockers)
    }

    @Test
    fun `adapter provenance must match capability token exactly`() {
        val request = requireNotNull(
            SilhouetteEditContract.evaluate(validEvidence()).request
        )

        assertTrue(
            SilhouetteEditContract.verifyAdapterProvenance(
                request = request,
                adapterBranch = "feature/test",
                adapterCommit = "abc123"
            ).isEmpty()
        )
        assertEquals(
            setOf(SilhouetteEditContract.Blocker.PROVENANCE_MISMATCH),
            SilhouetteEditContract.verifyAdapterProvenance(
                request = request,
                adapterBranch = "feature/other",
                adapterCommit = "abc123"
            )
        )
    }

    private fun validEvidence() = SilhouetteEditContract.Evidence(
        a1DevicePass = true,
        rawObservation = acceptedBody(),
        filteredObservation = acceptedBody(),
        acceptedObservation = acceptedBody(),
        acceptedRegionIds = setOf(
            PortraitRegionId.SUBJECT_MASK,
            PortraitRegionId.WAIST_CONTOUR
        ),
        parameterId = PortraitParameterId.WAIST_WIDTH,
        normalizedValue = 0.20f,
        isolatedStageBTestDeclared = true,
        backgroundProtectionEvidenceReady = true,
        provenanceBranch = "feature/test",
        provenanceCommit = "abc123"
    )

    private fun acceptedBody(): SubjectObservation {
        val mask = SemanticMaskObservation(
            id = PortraitRegionId.SUBJECT_MASK,
            mask = ConfidenceMask(2, 2, floatArrayOf(1f, 1f, 1f, 1f)),
            backend = ObservationBackend.ML_KIT_SELFIE_SEGMENTATION
        )
        val landmarks = listOf(
            point(PortraitLandmarkId.LEFT_SHOULDER, 0.35f, 0.25f),
            point(PortraitLandmarkId.RIGHT_SHOULDER, 0.65f, 0.25f),
            point(PortraitLandmarkId.LEFT_ELBOW, 0.28f, 0.42f),
            point(PortraitLandmarkId.RIGHT_ELBOW, 0.72f, 0.42f),
            point(PortraitLandmarkId.LEFT_WRIST, 0.25f, 0.58f),
            point(PortraitLandmarkId.RIGHT_WRIST, 0.75f, 0.58f),
            point(PortraitLandmarkId.LEFT_HIP, 0.42f, 0.58f),
            point(PortraitLandmarkId.RIGHT_HIP, 0.58f, 0.58f),
            point(PortraitLandmarkId.LEFT_KNEE, 0.43f, 0.76f),
            point(PortraitLandmarkId.RIGHT_KNEE, 0.57f, 0.76f),
            point(PortraitLandmarkId.LEFT_ANKLE, 0.44f, 0.95f),
            point(PortraitLandmarkId.RIGHT_ANKLE, 0.56f, 0.95f)
        ).associateBy { it.id }
        return SubjectObservation(
            subjectCount = 1,
            faceCount = 0,
            bodyCount = 1,
            landmarks = landmarks,
            regions = emptyMap(),
            masks = mapOf(PortraitRegionId.SUBJECT_MASK to mask)
        )
    }

    private fun point(id: String, x: Float, y: Float) = LandmarkObservation(
        id = id,
        point = NormalizedPoint3D(x, y),
        confidence = 0.95f,
        confidenceSource = ConfidenceSource.DIRECT,
        visibility = VisibilityState.VISIBLE,
        backend = ObservationBackend.ML_KIT_POSE
    )
}
