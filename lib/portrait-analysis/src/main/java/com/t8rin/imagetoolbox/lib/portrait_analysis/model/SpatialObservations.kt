/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis.model

/** Immutable per-pixel confidence mask in row-major order. */
class ConfidenceMask(
    val width: Int,
    val height: Int,
    confidenceValues: FloatArray
) {
    private val values: FloatArray = confidenceValues.copyOf()

    init {
        require(width > 0) { "width must be positive" }
        require(height > 0) { "height must be positive" }
        require(values.size == width * height) {
            "confidenceValues must contain exactly width * height values"
        }
        require(values.all { it in 0f..1f }) {
            "confidenceValues must be in 0f..1f"
        }
    }

    val size: Int
        get() = values.size

    operator fun get(x: Int, y: Int): Float {
        require(x in 0 until width) { "x is outside the mask" }
        require(y in 0 until height) { "y is outside the mask" }
        return values[y * width + x]
    }

    fun copyValues(): FloatArray = values.copyOf()

    override fun equals(other: Any?): Boolean =
        other is ConfidenceMask &&
            width == other.width &&
            height == other.height &&
            values.contentEquals(other.values)

    override fun hashCode(): Int {
        var result = width
        result = 31 * result + height
        result = 31 * result + values.contentHashCode()
        return result
    }

    override fun toString(): String =
        "ConfidenceMask(width=$width, height=$height, size=${values.size})"
}

data class SemanticMaskObservation(
    val id: String,
    val mask: ConfidenceMask,
    val backend: ObservationBackend
) {
    init {
        require(id.isNotBlank()) { "id cannot be blank" }
    }
}

data class MeshTriangleObservation(
    val firstVertexId: String,
    val secondVertexId: String,
    val thirdVertexId: String
) {
    init {
        require(firstVertexId.isNotBlank()) { "firstVertexId cannot be blank" }
        require(secondVertexId.isNotBlank()) { "secondVertexId cannot be blank" }
        require(thirdVertexId.isNotBlank()) { "thirdVertexId cannot be blank" }
        require(setOf(firstVertexId, secondVertexId, thirdVertexId).size == 3) {
            "A mesh triangle must reference three different vertices"
        }
    }

    val vertexIds: Set<String>
        get() = setOf(firstVertexId, secondVertexId, thirdVertexId)
}

data class MeshObservation(
    val id: String,
    val vertexIds: Set<String>,
    val triangles: List<MeshTriangleObservation>,
    val backend: ObservationBackend
) {
    init {
        require(id.isNotBlank()) { "id cannot be blank" }
        require(vertexIds.isNotEmpty()) { "vertexIds cannot be empty" }
        require(vertexIds.none { it.isBlank() }) { "vertexIds cannot contain blank ids" }
        val unknownVertexIds = triangles
            .flatMap { it.vertexIds }
            .filterNot { it in vertexIds }
            .distinct()
            .sorted()
        require(unknownVertexIds.isEmpty()) {
            "Triangles reference unknown vertices: ${unknownVertexIds.joinToString()}"
        }
    }
}
