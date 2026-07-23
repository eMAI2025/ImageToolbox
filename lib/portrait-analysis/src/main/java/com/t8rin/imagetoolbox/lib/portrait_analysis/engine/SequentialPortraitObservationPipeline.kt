/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis.engine

import com.t8rin.imagetoolbox.lib.portrait_analysis.merge.ObservationMergeConflict
import com.t8rin.imagetoolbox.lib.portrait_analysis.merge.ObservationMergeResult
import com.t8rin.imagetoolbox.lib.portrait_analysis.merge.SubjectObservationMerger
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ObservationBackend
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation
import java.util.concurrent.CancellationException

data class ObservationEngineFailure(
    val backend: ObservationBackend,
    val exceptionType: String,
    val message: String?
)

sealed interface ObservationPipelineResult {
    val backendResults: List<ObservationBenchmarkResult>

    data class Success(
        override val backendResults: List<ObservationBenchmarkResult>,
        val observation: SubjectObservation
    ) : ObservationPipelineResult

    data class MergeConflict(
        override val backendResults: List<ObservationBenchmarkResult>,
        val partialObservation: SubjectObservation,
        val conflicts: List<ObservationMergeConflict>
    ) : ObservationPipelineResult

    data class BackendFailure(
        override val backendResults: List<ObservationBenchmarkResult>,
        val failure: ObservationEngineFailure
    ) : ObservationPipelineResult
}

/**
 * Runs detector backends one by one and merges only observations from the same input.
 *
 * The first backend failure stops the pipeline. Cancellation is never converted into a result.
 * Binary compatibility failures from optional detector SDKs are contained as backend failures
 * instead of terminating the application process.
 */
class SequentialPortraitObservationPipeline<Input>(
    engines: List<PortraitObservationEngine<Input>>,
    private val nanoTime: () -> Long = System::nanoTime
) {
    private val engines: List<PortraitObservationEngine<Input>> = engines.toList()

    init {
        require(this.engines.isNotEmpty()) { "At least one observation engine is required" }
        val duplicateBackends = this.engines
            .groupingBy { it.backend }
            .eachCount()
            .filterValues { it > 1 }
            .keys
            .sortedBy { it.name }
        require(duplicateBackends.isEmpty()) {
            "Duplicate observation backends: ${duplicateBackends.joinToString()}"
        }
    }

    suspend fun observe(input: Input): ObservationPipelineResult {
        val backendResults = mutableListOf<ObservationBenchmarkResult>()

        engines.forEach { engine ->
            val startedAt = nanoTime()
            val observation = try {
                engine.observe(input)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                return backendFailure(
                    engine = engine,
                    backendResults = backendResults,
                    error = error
                )
            } catch (error: LinkageError) {
                return backendFailure(
                    engine = engine,
                    backendResults = backendResults,
                    error = error
                )
            }
            val finishedAt = nanoTime()
            val elapsedNanos = (finishedAt - startedAt).coerceAtLeast(0L)
            backendResults += ObservationBenchmarkResult(
                observation = observation,
                measurement = BenchmarkMeasurement(
                    backend = engine.backend,
                    processingTimeMillis = elapsedNanos / NANOS_PER_MILLISECOND
                )
            )
        }

        return when (
            val mergeResult = SubjectObservationMerger.merge(
                backendResults.map { it.observation }
            )
        ) {
            is ObservationMergeResult.Success -> ObservationPipelineResult.Success(
                backendResults = backendResults.toList(),
                observation = mergeResult.observation
            )

            is ObservationMergeResult.Conflict -> ObservationPipelineResult.MergeConflict(
                backendResults = backendResults.toList(),
                partialObservation = mergeResult.partialObservation,
                conflicts = mergeResult.conflicts
            )
        }
    }

    private fun backendFailure(
        engine: PortraitObservationEngine<Input>,
        backendResults: List<ObservationBenchmarkResult>,
        error: Throwable
    ): ObservationPipelineResult.BackendFailure = ObservationPipelineResult.BackendFailure(
        backendResults = backendResults.toList(),
        failure = ObservationEngineFailure(
            backend = engine.backend,
            exceptionType = error::class.qualifiedName ?: error::class.simpleName.orEmpty(),
            message = error.message
        )
    )

    private companion object {
        const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}
