/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis.merge

import com.t8rin.imagetoolbox.lib.portrait_analysis.model.LandmarkObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.PoseObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.RegionObservation
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation
import kotlin.math.abs

data class ObservationMergePolicy(
    val poseEqualityToleranceDegrees: Float = 0.01f
) {
    init {
        require(poseEqualityToleranceDegrees >= 0f)
    }
}

enum class ObservationMergeConflictType {
    LANDMARK_ID_COLLISION,
    REGION_ID_COLLISION,
    POSE_YAW_CONFLICT,
    POSE_PITCH_CONFLICT,
    POSE_ROLL_CONFLICT
}

data class ObservationMergeConflict(
    val type: ObservationMergeConflictType,
    val itemId: String? = null,
    val existingValue: String? = null,
    val incomingValue: String? = null
)

sealed interface ObservationMergeResult {
    data class Success(
        val observation: SubjectObservation
    ) : ObservationMergeResult

    data class Conflict(
        val partialObservation: SubjectObservation,
        val conflicts: List<ObservationMergeConflict>
    ) : ObservationMergeResult
}

/**
 * Strictly combines observations produced for the same source image.
 *
 * Counts use max rather than sum because independent detectors observe the same subject.
 * Duplicate identifiers are accepted only when their complete records are identical.
 */
object SubjectObservationMerger {

    fun merge(
        observations: List<SubjectObservation>,
        policy: ObservationMergePolicy = ObservationMergePolicy()
    ): ObservationMergeResult {
        val conflicts = mutableListOf<ObservationMergeConflict>()
        val landmarks = linkedMapOf<String, LandmarkObservation>()
        val regions = linkedMapOf<String, RegionObservation>()

        observations.forEach { observation ->
            observation.landmarks.forEach { (id, incoming) ->
                val existing = landmarks[id]
                when {
                    existing == null -> landmarks[id] = incoming
                    existing == incoming -> Unit
                    else -> conflicts += ObservationMergeConflict(
                        type = ObservationMergeConflictType.LANDMARK_ID_COLLISION,
                        itemId = id,
                        existingValue = existing.toString(),
                        incomingValue = incoming.toString()
                    )
                }
            }

            observation.regions.forEach { (id, incoming) ->
                val existing = regions[id]
                when {
                    existing == null -> regions[id] = incoming
                    existing == incoming -> Unit
                    else -> conflicts += ObservationMergeConflict(
                        type = ObservationMergeConflictType.REGION_ID_COLLISION,
                        itemId = id,
                        existingValue = existing.toString(),
                        incomingValue = incoming.toString()
                    )
                }
            }
        }

        val yaw = mergePoseAxis(
            axisName = "yaw",
            values = observations.mapNotNull { it.pose.yawDegrees },
            tolerance = policy.poseEqualityToleranceDegrees,
            conflictType = ObservationMergeConflictType.POSE_YAW_CONFLICT,
            conflicts = conflicts
        )
        val pitch = mergePoseAxis(
            axisName = "pitch",
            values = observations.mapNotNull { it.pose.pitchDegrees },
            tolerance = policy.poseEqualityToleranceDegrees,
            conflictType = ObservationMergeConflictType.POSE_PITCH_CONFLICT,
            conflicts = conflicts
        )
        val roll = mergePoseAxis(
            axisName = "roll",
            values = observations.mapNotNull { it.pose.rollDegrees },
            tolerance = policy.poseEqualityToleranceDegrees,
            conflictType = ObservationMergeConflictType.POSE_ROLL_CONFLICT,
            conflicts = conflicts
        )

        val merged = SubjectObservation(
            subjectCount = observations.maxOfOrNull { it.subjectCount } ?: 0,
            faceCount = observations.maxOfOrNull { it.faceCount } ?: 0,
            bodyCount = observations.maxOfOrNull { it.bodyCount } ?: 0,
            landmarks = landmarks,
            regions = regions,
            pose = PoseObservation(
                yawDegrees = yaw,
                pitchDegrees = pitch,
                rollDegrees = roll
            )
        )

        return if (conflicts.isEmpty()) {
            ObservationMergeResult.Success(merged)
        } else {
            ObservationMergeResult.Conflict(
                partialObservation = merged,
                conflicts = conflicts.toList()
            )
        }
    }

    private fun mergePoseAxis(
        axisName: String,
        values: List<Float>,
        tolerance: Float,
        conflictType: ObservationMergeConflictType,
        conflicts: MutableList<ObservationMergeConflict>
    ): Float? {
        if (values.isEmpty()) return null
        val first = values.first()
        values.drop(1).firstOrNull { abs(it - first) > tolerance }?.let { conflicting ->
            conflicts += ObservationMergeConflict(
                type = conflictType,
                itemId = axisName,
                existingValue = first.toString(),
                incomingValue = conflicting.toString()
            )
        }
        return first
    }
}
