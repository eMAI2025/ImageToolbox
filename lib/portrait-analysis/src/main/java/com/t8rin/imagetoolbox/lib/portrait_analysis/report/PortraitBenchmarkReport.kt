/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis.report

import com.t8rin.imagetoolbox.lib.portrait_analysis.engine.ObservationBenchmarkResult
import com.t8rin.imagetoolbox.lib.portrait_analysis.engine.ObservationEngineFailure
import com.t8rin.imagetoolbox.lib.portrait_analysis.engine.ObservationPipelineResult
import com.t8rin.imagetoolbox.lib.portrait_analysis.gate.ParameterGateSpec
import com.t8rin.imagetoolbox.lib.portrait_analysis.merge.ObservationMergeConflict
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation

data class PortraitBenchmarkReport(
    val observation: SubjectObservation,
    val backendResults: List<ObservationBenchmarkResult>,
    val parameterReport: ParameterGateReport
)

sealed interface PortraitBenchmarkEvaluation {
    data class Ready(
        val report: PortraitBenchmarkReport
    ) : PortraitBenchmarkEvaluation

    data class MergeConflict(
        val backendResults: List<ObservationBenchmarkResult>,
        val partialObservation: SubjectObservation,
        val conflicts: List<ObservationMergeConflict>
    ) : PortraitBenchmarkEvaluation

    data class BackendFailure(
        val backendResults: List<ObservationBenchmarkResult>,
        val failure: ObservationEngineFailure
    ) : PortraitBenchmarkEvaluation
}

object PortraitBenchmarkReportBuilder {

    fun build(
        pipelineResult: ObservationPipelineResult,
        gateSpecs: Collection<ParameterGateSpec>
    ): PortraitBenchmarkEvaluation = when (pipelineResult) {
        is ObservationPipelineResult.Success -> PortraitBenchmarkEvaluation.Ready(
            report = PortraitBenchmarkReport(
                observation = pipelineResult.observation,
                backendResults = pipelineResult.backendResults,
                parameterReport = ParameterGateReportBuilder.build(
                    specs = gateSpecs,
                    observation = pipelineResult.observation
                )
            )
        )

        is ObservationPipelineResult.MergeConflict ->
            PortraitBenchmarkEvaluation.MergeConflict(
                backendResults = pipelineResult.backendResults,
                partialObservation = pipelineResult.partialObservation,
                conflicts = pipelineResult.conflicts
            )

        is ObservationPipelineResult.BackendFailure ->
            PortraitBenchmarkEvaluation.BackendFailure(
                backendResults = pipelineResult.backendResults,
                failure = pipelineResult.failure
            )
    }
}
