/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis.report

import com.t8rin.imagetoolbox.lib.portrait_analysis.gate.GateFailureReason
import com.t8rin.imagetoolbox.lib.portrait_analysis.gate.ParameterGateSpec
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParameterGateReportTest {

    @Test
    fun `sorts parameters and reports enabled and disabled counts`() {
        val report = ParameterGateReportBuilder.build(
            specs = listOf(
                ParameterGateSpec(
                    parameterId = "z_disabled",
                    requiredLandmarkIds = setOf("missing"),
                    minimumLandmarkConfidence = null,
                    minimumRegionConfidence = null,
                    maximumRegionOcclusion = null
                ),
                ParameterGateSpec(
                    parameterId = "a_enabled",
                    minimumLandmarkConfidence = null,
                    minimumRegionConfidence = null,
                    maximumRegionOcclusion = null
                )
            ),
            observation = emptyObservation()
        )

        assertEquals(listOf("a_enabled", "z_disabled"), report.entries.map { it.parameterId })
        assertEquals(1, report.enabledCount)
        assertEquals(1, report.disabledCount)
        assertEquals(ParameterAvailability.ENABLED, report.entries.first().availability)
        assertEquals(ParameterAvailability.DISABLED, report.entries.last().availability)
        assertTrue(
            report.entries.last().failures.any {
                it.reason == GateFailureReason.LANDMARK_MISSING
            }
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects duplicate parameter ids`() {
        val spec = ParameterGateSpec(
            parameterId = "duplicate",
            minimumLandmarkConfidence = null,
            minimumRegionConfidence = null,
            maximumRegionOcclusion = null
        )

        ParameterGateReportBuilder.build(
            specs = listOf(spec, spec),
            observation = emptyObservation()
        )
    }

    @Test
    fun `renders stable compact output`() {
        val report = ParameterGateReportBuilder.build(
            specs = listOf(
                ParameterGateSpec(
                    parameterId = "missing_parameter",
                    requiredRegionIds = setOf("missing_region"),
                    minimumLandmarkConfidence = null,
                    minimumRegionConfidence = null,
                    maximumRegionOcclusion = null
                )
            ),
            observation = emptyObservation()
        )

        assertEquals(
            "enabled=0\n" +
                "disabled=1\n" +
                "missing_parameter=DISABLED[REGION_MISSING:missing_region]\n",
            ParameterGateReportRenderer.render(report)
        )
    }

    private fun emptyObservation() = SubjectObservation(
        subjectCount = 1,
        faceCount = 0,
        bodyCount = 0,
        landmarks = emptyMap(),
        regions = emptyMap()
    )
}
