/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis.engine

import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ConfidenceSource
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.LandmarkObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.NormalizedPoint3D
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ObservationBackend
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.VisibilityState
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SequentialPortraitObservationPipelineTest {

    @Test
    fun `runs engines sequentially and records independent durations`() {
        val times = listOf(0L, 2_000_000L, 5_000_000L, 9_000_000L).iterator()
        val pipeline = SequentialPortraitObservationPipeline(
            engines = listOf(
                FakeEngine(
                    backend = ObservationBackend.ML_KIT_FACE_MESH,
                    observation = observation(faceCount = 1)
                ),
                FakeEngine(
                    backend = ObservationBackend.ML_KIT_POSE,
                    observation = observation(bodyCount = 1)
                )
            ),
            nanoTime = { times.next() }
        )

        val result = runImmediate { pipeline.observe("image") }

        assertTrue(result is ObservationPipelineResult.Success)
        result as ObservationPipelineResult.Success
        assertEquals(1, result.observation.faceCount)
        assertEquals(1, result.observation.bodyCount)
        assertEquals(listOf(2L, 4L), result.backendResults.map {
            it.measurement.processingTimeMillis
        })
    }

    @Test
    fun `stops after first backend failure and preserves completed measurements`() {
        val pipeline = SequentialPortraitObservationPipeline(
            engines = listOf(
                FakeEngine(
                    backend = ObservationBackend.ML_KIT_FACE_MESH,
                    observation = observation(faceCount = 1)
                ),
                FailingEngine(ObservationBackend.ML_KIT_POSE),
                FakeEngine(
                    backend = ObservationBackend.ML_KIT_SELFIE_SEGMENTATION,
                    observation = observation(subjectCount = 1)
                )
            )
        )

        val result = runImmediate { pipeline.observe("image") }

        assertTrue(result is ObservationPipelineResult.BackendFailure)
        result as ObservationPipelineResult.BackendFailure
        assertEquals(1, result.backendResults.size)
        assertEquals(ObservationBackend.ML_KIT_POSE, result.failure.backend)
        assertEquals("detector failed", result.failure.message)
    }

    @Test
    fun `returns merge conflict instead of silently overwriting a landmark`() {
        val first = observation(
            landmarks = mapOf("shared" to landmark("shared", 0.2f))
        )
        val second = observation(
            landmarks = mapOf("shared" to landmark("shared", 0.8f))
        )
        val pipeline = SequentialPortraitObservationPipeline(
            engines = listOf(
                FakeEngine(ObservationBackend.ML_KIT_FACE_MESH, first),
                FakeEngine(ObservationBackend.ML_KIT_POSE, second)
            )
        )

        val result = runImmediate { pipeline.observe("image") }

        assertTrue(result is ObservationPipelineResult.MergeConflict)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects duplicate backend registrations`() {
        SequentialPortraitObservationPipeline(
            engines = listOf(
                FakeEngine(ObservationBackend.ML_KIT_POSE, observation()),
                FakeEngine(ObservationBackend.ML_KIT_POSE, observation())
            )
        )
    }

    private fun observation(
        subjectCount: Int = 1,
        faceCount: Int = 0,
        bodyCount: Int = 0,
        landmarks: Map<String, LandmarkObservation> = emptyMap()
    ) = SubjectObservation(
        subjectCount = subjectCount,
        faceCount = faceCount,
        bodyCount = bodyCount,
        landmarks = landmarks,
        regions = emptyMap()
    )

    private fun landmark(id: String, x: Float) = LandmarkObservation(
        id = id,
        point = NormalizedPoint3D(x = x, y = 0.5f),
        confidence = 0.95f,
        confidenceSource = ConfidenceSource.DIRECT,
        visibility = VisibilityState.VISIBLE,
        backend = ObservationBackend.ML_KIT_POSE
    )

    private class FakeEngine(
        override val backend: ObservationBackend,
        private val observation: SubjectObservation
    ) : PortraitObservationEngine<String> {
        override suspend fun observe(input: String): SubjectObservation = observation
    }

    private class FailingEngine(
        override val backend: ObservationBackend
    ) : PortraitObservationEngine<String> {
        override suspend fun observe(input: String): SubjectObservation {
            error("detector failed")
        }
    }

    private fun <T> runImmediate(block: suspend () -> T): T {
        var outcome: Result<T>? = null
        block.startCoroutine(
            object : Continuation<T> {
                override val context = EmptyCoroutineContext

                override fun resumeWith(result: Result<T>) {
                    outcome = result
                }
            }
        )
        return requireNotNull(outcome) {
            "Test coroutine did not complete synchronously"
        }.getOrThrow()
    }
}
