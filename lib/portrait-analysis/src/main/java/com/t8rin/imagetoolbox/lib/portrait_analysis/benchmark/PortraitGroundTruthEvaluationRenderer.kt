/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis.benchmark

/** Stable compact representation suitable for benchmark logs and the first Portrait Lab UI. */
object PortraitGroundTruthEvaluationRenderer {

    fun render(evaluation: PortraitGroundTruthEvaluation): String = buildString {
        appendLine("case=${evaluation.caseId}")
        appendLine("passed=${evaluation.passed}")
        appendLine("mismatches=${evaluation.mismatches.size}")

        evaluation.mismatches.forEach { mismatch ->
            append(mismatch.reason.name)
            mismatch.itemId?.let {
                append(':')
                append(it)
            }
            mismatch.observedValue?.let {
                append(" observed=")
                append(it)
            }
            mismatch.expectedRange?.let {
                append(" expected=")
                append(it.first)
                append("..")
                append(it.last)
            }
            appendLine()
        }
    }
}
