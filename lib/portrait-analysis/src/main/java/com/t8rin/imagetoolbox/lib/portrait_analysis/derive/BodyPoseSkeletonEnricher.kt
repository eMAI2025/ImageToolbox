/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis.derive

import com.t8rin.imagetoolbox.lib.portrait_analysis.catalog.PortraitLandmarkId
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ConfidenceSource
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ContourObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ObservationBackend
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation

/** Adds observation-only skeleton lines only after fail-closed landmark visibility validation. */
object BodyPoseSkeletonEnricher {

    private val connections = listOf(
        "shoulders" to (PortraitLandmarkId.LEFT_SHOULDER to PortraitLandmarkId.RIGHT_SHOULDER),
        "left_upper_arm" to (PortraitLandmarkId.LEFT_SHOULDER to PortraitLandmarkId.LEFT_ELBOW),
        "left_forearm" to (PortraitLandmarkId.LEFT_ELBOW to PortraitLandmarkId.LEFT_WRIST),
        "right_upper_arm" to (PortraitLandmarkId.RIGHT_SHOULDER to PortraitLandmarkId.RIGHT_ELBOW),
        "right_forearm" to (PortraitLandmarkId.RIGHT_ELBOW to PortraitLandmarkId.RIGHT_WRIST),
        "left_torso" to (PortraitLandmarkId.LEFT_SHOULDER to PortraitLandmarkId.LEFT_HIP),
        "right_torso" to (PortraitLandmarkId.RIGHT_SHOULDER to PortraitLandmarkId.RIGHT_HIP),
        "hips" to (PortraitLandmarkId.LEFT_HIP to PortraitLandmarkId.RIGHT_HIP),
        "left_thigh" to (PortraitLandmarkId.LEFT_HIP to PortraitLandmarkId.LEFT_KNEE),
        "left_calf" to (PortraitLandmarkId.LEFT_KNEE to PortraitLandmarkId.LEFT_ANKLE),
        "right_thigh" to (PortraitLandmarkId.RIGHT_HIP to PortraitLandmarkId.RIGHT_KNEE),
        "right_calf" to (PortraitLandmarkId.RIGHT_KNEE to PortraitLandmarkId.RIGHT_ANKLE)
    )

    fun enrich(observation: SubjectObservation): SubjectObservation {
        val visibility = BodyLandmarkVisibilityGate.evaluate(observation)
        val activeObservation = BodyLandmarkVisibilityGate.filterForActiveGeometry(
            rawObservation = observation,
            assessment = visibility
        )
        val additions = linkedMapOf<String, ContourObservation>()
        connections.forEach { (name, endpoints) ->
            val capability = visibility.segments[name] ?: return@forEach
            if (!capability.available) return@forEach
            val first = activeObservation.landmarks[endpoints.first] ?: return@forEach
            val second = activeObservation.landmarks[endpoints.second] ?: return@forEach
            val id = "derived_body_skeleton_$name"
            if (id in activeObservation.contours) return@forEach
            val confidence = listOfNotNull(first.confidence, second.confidence).minOrNull()
            additions[id] = ContourObservation(
                id = id,
                vertexIds = listOf(first.id, second.id),
                closed = false,
                confidence = confidence,
                confidenceSource = if (confidence == null) {
                    ConfidenceSource.UNAVAILABLE
                } else {
                    ConfidenceSource.DERIVED
                },
                backend = ObservationBackend.ML_KIT_POSE
            )
        }
        return activeObservation.copy(contours = activeObservation.contours + additions)
    }
}
