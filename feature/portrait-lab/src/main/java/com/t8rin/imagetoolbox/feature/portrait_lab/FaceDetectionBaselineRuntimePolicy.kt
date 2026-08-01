/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.feature.portrait_lab

/**
 * Fail-safe runtime decision boundary for FACE_DETECTION_BASELINE_V1.
 *
 * The detector count is evidence only. It must never be interpreted as a verified count of real
 * people and must never stop the full-image baseline merely because it exceeds a UI/detail limit.
 * Every returned detector candidate remains visible and auditable through its bounding box.
 */
object FaceDetectionBaselineRuntimePolicy {

    const val FINGERPRINT = "FACE_DETECTION_BASELINE_RUNTIME_POLICY_V1"

    enum class State {
        NO_DETECTIONS,
        FULL_IMAGE_EVIDENCE_READY
    }

    data class Decision internal constructor(
        val state: State,
        val detectorCandidateCount: Int,
        val candidates: List<PortraitFaceCandidate>,
        val activeFaceIndex: Int? = null,
        val blocksBaseline: Boolean = false,
        val detectorCandidatesVerifiedAsFaces: Boolean = false,
        val fingerprint: String = FINGERPRINT
    ) {
        init {
            require(detectorCandidateCount == candidates.size)
            require(activeFaceIndex == null) {
                "The baseline runtime policy must not select an active face"
            }
            require(!blocksBaseline) {
                "A detector candidate count must not block the full-image baseline"
            }
            require(!detectorCandidatesVerifiedAsFaces) {
                "Raw detector candidates are evidence, not verified identities"
            }
            require(
                (state == State.NO_DETECTIONS) == candidates.isEmpty()
            )
        }
    }

    fun evaluate(snapshot: FaceDetectionBaselineRouting.Snapshot): Decision = Decision(
        state = if (snapshot.candidates.isEmpty()) {
            State.NO_DETECTIONS
        } else {
            State.FULL_IMAGE_EVIDENCE_READY
        },
        detectorCandidateCount = snapshot.candidates.size,
        candidates = snapshot.candidates,
        activeFaceIndex = null,
        blocksBaseline = false,
        detectorCandidatesVerifiedAsFaces = false
    )
}
