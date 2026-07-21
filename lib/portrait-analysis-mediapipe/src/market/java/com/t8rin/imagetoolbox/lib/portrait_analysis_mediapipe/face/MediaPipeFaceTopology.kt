/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis_mediapipe.face

import com.google.mediapipe.tasks.components.containers.Connection
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker

/**
 * Converts the official MediaPipe Android connection sets into deterministic diagnostic topology.
 * No geometry is invented: contours and tesselation come from FaceLandmarker constants.
 */
internal object MediaPipeFaceTopology {

    private data class Edge(val first: Int, val second: Int) {
        companion object {
            fun of(a: Int, b: Int): Edge = if (a < b) Edge(a, b) else Edge(b, a)
        }
    }

    fun contours(pointCount: Int): List<MediaPipeFaceContourSnapshot> = buildList {
        addConnectionGroup("lips", FaceLandmarker.FACE_LANDMARKS_LIPS, pointCount)
        addConnectionGroup("left_eye", FaceLandmarker.FACE_LANDMARKS_LEFT_EYE, pointCount)
        addConnectionGroup("right_eye", FaceLandmarker.FACE_LANDMARKS_RIGHT_EYE, pointCount)
        addConnectionGroup(
            "left_eyebrow",
            FaceLandmarker.FACE_LANDMARKS_LEFT_EYE_BROW,
            pointCount
        )
        addConnectionGroup(
            "right_eyebrow",
            FaceLandmarker.FACE_LANDMARKS_RIGHT_EYE_BROW,
            pointCount
        )
        addConnectionGroup("nose", FaceLandmarker.FACE_LANDMARKS_CONNECTORS, pointCount) {
            edge -> edge.first in NOSE_INDICES && edge.second in NOSE_INDICES
        }
        addConnectionGroup("face_oval", FaceLandmarker.FACE_LANDMARKS_FACE_OVAL, pointCount)

        JAWLINE_INDICES.takeIf { indices -> indices.all { it < pointCount } }?.let { indices ->
            add(MediaPipeFaceContourSnapshot("jawline", indices, closed = false))
        }
        CHIN_INDICES.takeIf { indices -> indices.all { it < pointCount } }?.let { indices ->
            add(MediaPipeFaceContourSnapshot("chin", indices, closed = false))
        }
    }

    fun triangles(pointCount: Int): List<MediaPipeFaceTriangleSnapshot> {
        val edges = FaceLandmarker.FACE_LANDMARKS_TESSELATION
            .asSequence()
            .map { Edge.of(it.start(), it.end()) }
            .filter { it.first < pointCount && it.second < pointCount }
            .toSet()
        val adjacency = buildMap<Int, MutableSet<Int>> {
            edges.forEach { edge ->
                getOrPut(edge.first) { linkedSetOf() }.add(edge.second)
                getOrPut(edge.second) { linkedSetOf() }.add(edge.first)
            }
        }

        return buildList {
            adjacency.keys.sorted().forEach { first ->
                adjacency.getValue(first)
                    .asSequence()
                    .filter { it > first }
                    .sorted()
                    .forEach { second ->
                        val common = adjacency.getValue(first)
                            .intersect(adjacency.getValue(second))
                            .asSequence()
                            .filter { it > second }
                            .sorted()
                        common.forEach { third ->
                            if (Edge.of(first, third) in edges && Edge.of(second, third) in edges) {
                                add(MediaPipeFaceTriangleSnapshot(first, second, third))
                            }
                        }
                    }
            }
        }
    }

    private fun MutableList<MediaPipeFaceContourSnapshot>.addConnectionGroup(
        id: String,
        connections: Set<Connection>,
        pointCount: Int,
        filter: (Edge) -> Boolean = { true }
    ) {
        val edges = connections
            .asSequence()
            .map { Edge.of(it.start(), it.end()) }
            .filter { it.first < pointCount && it.second < pointCount }
            .filter(filter)
            .toSet()
        connectionPaths(edges).forEachIndexed { index, path ->
            if (path.vertices.size >= 2) {
                add(
                    MediaPipeFaceContourSnapshot(
                        id = if (index == 0) id else "${id}_$index",
                        pointIndices = path.vertices,
                        closed = path.closed
                    )
                )
            }
        }
    }

    private data class ConnectionPath(val vertices: List<Int>, val closed: Boolean)

    private fun connectionPaths(sourceEdges: Set<Edge>): List<ConnectionPath> {
        val remaining = sourceEdges.toMutableSet()
        val result = mutableListOf<ConnectionPath>()
        while (remaining.isNotEmpty()) {
            val adjacency = mutableMapOf<Int, MutableSet<Int>>()
            remaining.forEach { edge ->
                adjacency.getOrPut(edge.first) { linkedSetOf() }.add(edge.second)
                adjacency.getOrPut(edge.second) { linkedSetOf() }.add(edge.first)
            }
            val start = adjacency.entries
                .filter { it.value.size != 2 }
                .minOfOrNull { it.key }
                ?: adjacency.keys.minOrNull()
                ?: break
            val vertices = mutableListOf(start)
            var previous: Int? = null
            var current = start
            var closed = false

            while (true) {
                val candidates = adjacency[current]
                    .orEmpty()
                    .asSequence()
                    .filter { Edge.of(current, it) in remaining }
                    .sorted()
                    .toList()
                val next = candidates.firstOrNull { it != previous } ?: candidates.firstOrNull() ?: break
                remaining.remove(Edge.of(current, next))
                if (next == start) {
                    closed = vertices.size >= 3
                    break
                }
                if (next in vertices) break
                vertices += next
                previous = current
                current = next
            }
            result += ConnectionPath(vertices, closed)
        }
        return result
    }

    private val NOSE_INDICES = setOf(
        1, 2, 4, 5, 6, 19, 45, 48, 64, 94, 97, 98, 115, 168, 195, 197, 220,
        275, 278, 294, 326, 327, 344, 440
    )

    private val JAWLINE_INDICES = listOf(
        454, 323, 361, 288, 397, 365, 379, 378, 400, 377, 152,
        148, 176, 149, 150, 136, 172, 58, 132, 93, 234
    )

    private val CHIN_INDICES = listOf(400, 377, 152, 148, 176)
}
