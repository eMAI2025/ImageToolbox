/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis.report

import com.t8rin.imagetoolbox.lib.portrait_analysis.gate.GateFailure
import com.t8rin.imagetoolbox.lib.portrait_analysis.gate.ParameterGateDecision
import com.t8rin.imagetoolbox.lib.portrait_analysis.gate.ParameterGateEvaluator
import com.t8rin.imagetoolbox.lib.portrait_analysis.gate.ParameterGateSpec
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation

enum class ParameterAvailability {
    ENABLED,
    DISABLED
}

data class ParameterGateReportEntry(
    val parameterId: String,
    val availability: ParameterAvailability,
    val failures: List<GateFailure>
)

data class ParameterGateReport(
    val entries: List<ParameterGateReportEntry>
) {
    val enabledCount: Int
        get() = entries.count { it.availability == ParameterAvailability.ENABLED }

    val disabledCount: Int
        get() = entries.size - enabledCount

    fun entry(parameterId: String): ParameterGateReportEntry? =
        entries.firstOrNull { it.parameterId == parameterId }
}

/** Builds a deterministic parameter report for one merged subject observation. */
object ParameterGateReportBuilder {

    fun build(
        specs: Collection<ParameterGateSpec>,
        observation: SubjectObservation
    ): ParameterGateReport {
        val duplicateIds = specs
            .groupingBy { it.parameterId }
            .eachCount()
            .filterValues { it > 1 }
            .keys
            .sorted()
        require(duplicateIds.isEmpty()) {
            "Duplicate parameter ids: ${duplicateIds.joinToString()}"
        }

        val entries = specs
            .sortedBy { it.parameterId }
            .map { spec ->
                when (val decision = ParameterGateEvaluator.evaluate(spec, observation)) {
                    is ParameterGateDecision.Enabled -> ParameterGateReportEntry(
                        parameterId = decision.parameterId,
                        availability = ParameterAvailability.ENABLED,
                        failures = emptyList()
                    )

                    is ParameterGateDecision.Disabled -> ParameterGateReportEntry(
                        parameterId = decision.parameterId,
                        availability = ParameterAvailability.DISABLED,
                        failures = decision.failures.sortedWith(
                            compareBy<GateFailure>(
                                { it.reason.name },
                                { it.itemId.orEmpty() },
                                { it.observedValue ?: Float.NEGATIVE_INFINITY }
                            )
                        )
                    )
                }
            }

        return ParameterGateReport(entries)
    }
}

/** Stable text representation suitable for logs and the first Portrait Lab UI. */
object ParameterGateReportRenderer {

    fun render(report: ParameterGateReport): String = buildString {
        appendLine("enabled=${report.enabledCount}")
        appendLine("disabled=${report.disabledCount}")
        report.entries.forEach { entry ->
            append(entry.parameterId)
            append('=')
            append(entry.availability.name)
            if (entry.failures.isNotEmpty()) {
                append('[')
                append(
                    entry.failures.joinToString(separator = ",") { failure ->
                        buildString {
                            append(failure.reason.name)
                            failure.itemId?.let {
                                append(':')
                                append(it)
                            }
                        }
                    }
                )
                append(']')
            }
            appendLine()
        }
    }
}
