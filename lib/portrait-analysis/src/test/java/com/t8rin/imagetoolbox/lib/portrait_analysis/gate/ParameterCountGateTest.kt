/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis.gate

import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParameterCountGateTest {

    @Test
    fun `group photo can block body parameter independently`() {
        val decision = ParameterGateEvaluator.evaluate(
            spec = ParameterGateSpec(
                parameterId = "shoulder_width",
                minimumSubjects = 1,
                maximumSubjects = 1,
                minimumBodies = 1,
                maximumBodies = 1,
                minimumLandmarkConfidence = null,
                minimumRegionConfidence = null,
                maximumRegionOcclusion = null
            ),
            observation = observation(
                subjectCount = 2,
                faceCount = 2,
                bodyCount = 2
            )
        )

        assertTrue(decision is ParameterGateDecision.Disabled)
        val failures = (decision as ParameterGateDecision.Disabled).failures
        assertTrue(failures.any { it.reason == GateFailureReason.SUBJECT_COUNT_UNSUPPORTED })
        assertTrue(failures.any { it.reason == GateFailureReason.BODY_COUNT_UNSUPPORTED })
    }

    @Test
    fun `face parameter can require exactly one face without requiring a body`() {
        val decision = ParameterGateEvaluator.evaluate(
            spec = ParameterGateSpec(
                parameterId = "eye_brightness",
                minimumSubjects = 1,
                maximumSubjects = 1,
                minimumFaces = 1,
                maximumFaces = 1,
                minimumBodies = 0,
                maximumBodies = 0,
                minimumLandmarkConfidence = null,
                minimumRegionConfidence = null,
                maximumRegionOcclusion = null
            ),
            observation = observation(
                subjectCount = 1,
                faceCount = 1,
                bodyCount = 0
            )
        )

        assertTrue(decision is ParameterGateDecision.Enabled)
    }

    @Test
    fun `count failure preserves the required range`() {
        val decision = ParameterGateEvaluator.evaluate(
            spec = ParameterGateSpec(
                parameterId = "single_face_only",
                minimumFaces = 1,
                maximumFaces = 1,
                minimumLandmarkConfidence = null,
                minimumRegionConfidence = null,
                maximumRegionOcclusion = null
            ),
            observation = observation(
                subjectCount = 1,
                faceCount = 0,
                bodyCount = 1
            )
        )

        val failure = (decision as ParameterGateDecision.Disabled).failures
            .single { it.reason == GateFailureReason.FACE_COUNT_UNSUPPORTED }
        assertEquals(0f, failure.observedValue)
        assertEquals(1f, failure.requiredMinimumValue)
        assertEquals(1f, failure.requiredMaximumValue)
    }

    private fun observation(
        subjectCount: Int,
        faceCount: Int,
        bodyCount: Int
    ) = SubjectObservation(
        subjectCount = subjectCount,
        faceCount = faceCount,
        bodyCount = bodyCount,
        landmarks = emptyMap(),
        regions = emptyMap()
    )
}
