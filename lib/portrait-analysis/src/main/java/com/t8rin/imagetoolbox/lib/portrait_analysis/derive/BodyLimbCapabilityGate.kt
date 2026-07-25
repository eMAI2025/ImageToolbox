/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package com.t8rin.imagetoolbox.lib.portrait_analysis.derive

import com.t8rin.imagetoolbox.lib.portrait_analysis.catalog.PortraitLandmarkId
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation

/** Fail-closed, per-side upper-arm / forearm / hand capability contract. */
object BodyLimbCapabilityGate {

    const val FINGERPRINT = "POSTAC_MASTER_BODY_LIMB_CAPABILITY_V1"

    enum class Side { LEFT, RIGHT }
    enum class Part { UPPER_ARM, FOREARM, HAND }
    enum class Reason {
        SHOULDER_UNUSABLE,
        ELBOW_UNUSABLE,
        WRIST_UNUSABLE,
        FINGER_EVIDENCE_MISSING,
        FINGER_EVIDENCE_UNUSABLE
    }

    data class Capability(
        val side: Side,
        val part: Part,
        val available: Boolean,
        val requiredLandmarkIds: Set<String>,
        val blockingLandmarkIds: Set<String>,
        val reasons: Set<Reason>
    )

    data class Assessment(
        val capabilities: Map<String, Capability>,
        val fingerprint: String = FINGERPRINT
    ) {
        fun capability(side: Side, part: Part): Capability =
            capabilities.getValue("${side.name.lowercase()}_${part.name.lowercase()}")
    }

    private data class SideIds(
        val shoulder: String,
        val elbow: String,
        val wrist: String,
        val fingers: Set<String>
    )

    private val ids = mapOf(
        Side.LEFT to SideIds(
            PortraitLandmarkId.LEFT_SHOULDER,
            PortraitLandmarkId.LEFT_ELBOW,
            PortraitLandmarkId.LEFT_WRIST,
            setOf("left_pinky", "left_index", "left_thumb")
        ),
        Side.RIGHT to SideIds(
            PortraitLandmarkId.RIGHT_SHOULDER,
            PortraitLandmarkId.RIGHT_ELBOW,
            PortraitLandmarkId.RIGHT_WRIST,
            setOf("right_pinky", "right_index", "right_thumb")
        )
    )

    fun evaluate(
        observation: SubjectObservation,
        visibility: BodyLandmarkVisibilityGate.Assessment =
            BodyLandmarkVisibilityGate.evaluate(observation)
    ): Assessment {
        val result = linkedMapOf<String, Capability>()
        ids.forEach { (side, sideIds) ->
            result.putCapability(side, Part.UPPER_ARM, setOf(sideIds.shoulder, sideIds.elbow), visibility)
            result.putCapability(side, Part.FOREARM, setOf(sideIds.elbow, sideIds.wrist), visibility)

            val wristUsable = visibility.landmarks[sideIds.wrist]?.usable == true
            val presentFingers = sideIds.fingers.filterTo(linkedSetOf()) { it in observation.landmarks }
            val usableFingers = presentFingers.filterTo(linkedSetOf()) { id ->
                val landmark = observation.landmarks.getValue(id)
                landmark.visibility == com.t8rin.imagetoolbox.lib.portrait_analysis.model.VisibilityState.VISIBLE &&
                    landmark.confidence != null &&
                    landmark.confidence >= BodyLandmarkVisibilityGate.MINIMUM_IN_FRAME_CONFIDENCE &&
                    landmark.point.x in 0f..1f && landmark.point.y in 0f..1f
            }
            val reasons = linkedSetOf<Reason>()
            val blocking = linkedSetOf<String>()
            if (!wristUsable) {
                reasons += Reason.WRIST_UNUSABLE
                blocking += sideIds.wrist
            }
            if (presentFingers.isEmpty()) {
                reasons += Reason.FINGER_EVIDENCE_MISSING
                blocking += sideIds.fingers
            } else if (usableFingers.isEmpty()) {
                reasons += Reason.FINGER_EVIDENCE_UNUSABLE
                blocking += presentFingers
            }
            val required = setOf(sideIds.wrist) + presentFingers
            result["${side.name.lowercase()}_hand"] = Capability(
                side = side,
                part = Part.HAND,
                available = wristUsable && usableFingers.isNotEmpty(),
                requiredLandmarkIds = required,
                blockingLandmarkIds = blocking,
                reasons = reasons
            )
        }
        return Assessment(result)
    }

    /** Removes synthetic hand contours/end markers when hand capability is unavailable. */
    fun filterForActiveGeometry(
        observation: SubjectObservation,
        assessment: Assessment
    ): SubjectObservation {
        val blockedSides = Side.entries.filter { !assessment.capability(it, Part.HAND).available }
            .map { it.name.lowercase() }
            .toSet()
        if (blockedSides.isEmpty()) return observation
        val contours = observation.contours.filterNot { (id, _) ->
            blockedSides.any { side ->
                val value = id.lowercase()
                value.contains(side) &&
                    (value.contains("hand") || value.contains("palm") || value.contains("finger") || value.contains("wrist_end"))
            }
        }
        return observation.copy(contours = contours)
    }

    private fun MutableMap<String, Capability>.putCapability(
        side: Side,
        part: Part,
        required: Set<String>,
        visibility: BodyLandmarkVisibilityGate.Assessment
    ) {
        val blocked = required.filterTo(linkedSetOf()) { visibility.landmarks[it]?.usable != true }
        val reasons = blocked.mapTo(linkedSetOf()) { id ->
            when {
                id.contains("shoulder") -> Reason.SHOULDER_UNUSABLE
                id.contains("elbow") -> Reason.ELBOW_UNUSABLE
                else -> Reason.WRIST_UNUSABLE
            }
        }
        this["${side.name.lowercase()}_${part.name.lowercase()}"] = Capability(
            side, part, blocked.isEmpty(), required, blocked, reasons
        )
    }
}
