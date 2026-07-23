/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis.engine

import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ObservationBackend
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation

/**
 * Backend-independent contract for Stage A1 detector benchmarks.
 *
 * Implementations may wrap MediaPipe, ML Kit or desktop quality backends.
 * They must only observe the image. Geometry modification is deliberately out of scope.
 */
interface PortraitObservationEngine<Input> {
    val backend: ObservationBackend

    suspend fun observe(input: Input): SubjectObservation
}

data class BenchmarkMeasurement(
    val backend: ObservationBackend,
    val processingTimeMillis: Long,
    val peakMemoryBytes: Long? = null,
    val repeatedRunIndex: Int = 0
) {
    init {
        require(processingTimeMillis >= 0L)
        require(peakMemoryBytes == null || peakMemoryBytes >= 0L)
        require(repeatedRunIndex >= 0)
    }
}

data class ObservationBenchmarkResult(
    val observation: SubjectObservation,
    val measurement: BenchmarkMeasurement
)
