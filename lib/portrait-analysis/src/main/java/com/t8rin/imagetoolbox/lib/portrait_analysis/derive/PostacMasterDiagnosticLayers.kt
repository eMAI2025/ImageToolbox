/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis.derive

import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation

/**
 * Canonical three-layer diagnostic contract shared by face and body pipelines.
 *
 * RAW is immutable backend evidence. FILTERED is the candidate after visibility/locality reduction.
 * ACCEPTED is the only geometry permitted to reach an active overlay or later editing stage.
 * Detection never implies that ACCEPTED exists.
 */
data class PostacMasterDiagnosticLayers(
    val raw: SubjectObservation,
    val filtered: SubjectObservation,
    val accepted: SubjectObservation?,
    val state: State,
    val rejectionCodes: Set<String> = emptySet(),
    val contractVersion: String = CONTRACT_VERSION
) {
    enum class State {
        NOT_DETECTED,
        DETECTED_ACCEPTED,
        DETECTED_REJECTED
    }

    data class Counts(
        val rawLandmarks: Int,
        val filteredLandmarks: Int,
        val acceptedLandmarks: Int,
        val rawContours: Int,
        val filteredContours: Int,
        val acceptedContours: Int,
        val rawTriangles: Int,
        val filteredTriangles: Int,
        val acceptedTriangles: Int,
        val rawMasks: Int,
        val filteredMasks: Int,
        val acceptedMasks: Int
    )

    init {
        require(contractVersion.isNotBlank())
        when (state) {
            State.NOT_DETECTED -> {
                require(accepted == null)
                require(raw.subjectCount == 0 && raw.faceCount == 0 && raw.bodyCount == 0)
            }
            State.DETECTED_ACCEPTED -> {
                require(accepted != null)
                require(rejectionCodes.isEmpty())
            }
            State.DETECTED_REJECTED -> {
                require(accepted == null)
                require(rejectionCodes.isNotEmpty())
            }
        }
    }

    val counts: Counts
        get() = Counts(
            rawLandmarks = raw.landmarks.size,
            filteredLandmarks = filtered.landmarks.size,
            acceptedLandmarks = accepted?.landmarks?.size ?: 0,
            rawContours = raw.contours.size,
            filteredContours = filtered.contours.size,
            acceptedContours = accepted?.contours?.size ?: 0,
            rawTriangles = raw.meshes.values.sumOf { it.triangles.size },
            filteredTriangles = filtered.meshes.values.sumOf { it.triangles.size },
            acceptedTriangles = accepted?.meshes?.values?.sumOf { it.triangles.size } ?: 0,
            rawMasks = raw.masks.size,
            filteredMasks = filtered.masks.size,
            acceptedMasks = accepted?.masks?.size ?: 0
        )

    companion object {
        const val CONTRACT_VERSION = "POSTAC_MASTER_DIAGNOSTIC_LAYERS_V1"
        const val MANIFEST_FILE = "diagnostic_manifest.json"
        const val RAW_GEOMETRY_FILE = "geometry_raw.json"
        const val FILTERED_GEOMETRY_FILE = "geometry_filtered.json"
        const val ACCEPTED_GEOMETRY_FILE = "geometry_accepted.json"
        const val RAW_MASK_FILE = "mask_raw.png"
        const val FILTERED_MASK_FILE = "mask_filtered.png"

        val REQUIRED_EXPORT_FILES: Set<String> = linkedSetOf(
            MANIFEST_FILE,
            RAW_GEOMETRY_FILE,
            FILTERED_GEOMETRY_FILE,
            ACCEPTED_GEOMETRY_FILE,
            RAW_MASK_FILE,
            FILTERED_MASK_FILE,
            "runtime_log.txt"
        )

        fun fromFace(
            raw: SubjectObservation,
            filtered: SubjectObservation,
            decision: FaceGeometryAcceptanceDecision
        ): PostacMasterDiagnosticLayers {
            require(decision.rawObservation == raw)
            return when (decision.status) {
                FaceGeometryAcceptanceStatus.NO_FACE -> PostacMasterDiagnosticLayers(
                    raw = raw,
                    filtered = filtered,
                    accepted = null,
                    state = State.NOT_DETECTED,
                    rejectionCodes = decision.reasons.mapTo(linkedSetOf()) { it.name }
                )
                FaceGeometryAcceptanceStatus.FACE_DETECTED_GEOMETRY_ACCEPTED ->
                    PostacMasterDiagnosticLayers(
                        raw = raw,
                        filtered = filtered,
                        accepted = requireNotNull(decision.activeObservation),
                        state = State.DETECTED_ACCEPTED
                    )
                FaceGeometryAcceptanceStatus.FACE_DETECTED_GEOMETRY_REJECTED ->
                    PostacMasterDiagnosticLayers(
                        raw = raw,
                        filtered = filtered,
                        accepted = null,
                        state = State.DETECTED_REJECTED,
                        rejectionCodes = decision.reasons.mapTo(linkedSetOf()) { it.name }
                    )
            }
        }

        fun fromBody(
            raw: SubjectObservation,
            filtered: SubjectObservation,
            accepted: SubjectObservation?,
            rejectionCodes: Set<String>
        ): PostacMasterDiagnosticLayers {
            val detected = raw.subjectCount > 0 || raw.bodyCount > 0
            val state = when {
                !detected -> State.NOT_DETECTED
                accepted != null -> State.DETECTED_ACCEPTED
                else -> State.DETECTED_REJECTED
            }
            return PostacMasterDiagnosticLayers(
                raw = raw,
                filtered = filtered,
                accepted = accepted,
                state = state,
                rejectionCodes = when (state) {
                    State.DETECTED_ACCEPTED -> emptySet()
                    State.NOT_DETECTED -> rejectionCodes.ifEmpty { setOf("NO_BODY_CANDIDATE") }
                    State.DETECTED_REJECTED -> rejectionCodes.ifEmpty {
                        setOf("BODY_GEOMETRY_EVIDENCE_REJECTED")
                    }
                }
            )
        }
    }
}
