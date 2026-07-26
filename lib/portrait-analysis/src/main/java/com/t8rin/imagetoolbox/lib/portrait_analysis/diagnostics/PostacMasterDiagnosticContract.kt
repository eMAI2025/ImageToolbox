/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis.diagnostics

import com.t8rin.imagetoolbox.lib.portrait_analysis.derive.FaceGeometryAcceptanceDecision
import com.t8rin.imagetoolbox.lib.portrait_analysis.derive.FaceGeometryAcceptanceStatus
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation

/**
 * Shared diagnostics contract for the single A1/B1 device-integration APK.
 *
 * Detection is evidence only. Geometry is active only after an explicit fail-closed decision.
 * Raw observations must never be overwritten by filtered or accepted observations.
 */
const val POSTAC_MASTER_DIAGNOSTIC_SCHEMA_VERSION = "POSTAC_MASTER_DIAGNOSTICS_V2"
const val POSTAC_MASTER_INTEGRATION_CONTRACT_VERSION =
    "POSTAC_MASTER_A1_B1_INTEGRATION_CONTRACT_V1"

enum class PostacMasterGeometryState {
    NOT_DETECTED,
    DETECTED_ACCEPTED,
    DETECTED_REJECTED
}

enum class PostacMasterDiagnosticLayer {
    RAW,
    FILTERED,
    ACCEPTED
}

data class PostacMasterBuildFingerprint(
    val branch: String,
    val sourceCommit: String,
    val diagnosticSchema: String = POSTAC_MASTER_DIAGNOSTIC_SCHEMA_VERSION,
    val integrationContract: String = POSTAC_MASTER_INTEGRATION_CONTRACT_VERSION,
    val activeFeatures: Set<String>
) {
    init {
        require(branch.isNotBlank())
        require(sourceCommit.isNotBlank())
        require(activeFeatures.isNotEmpty())
    }
}

data class PostacMasterGeometryExport(
    val state: PostacMasterGeometryState,
    val rawObservation: SubjectObservation?,
    val filteredObservation: SubjectObservation?,
    val acceptedObservation: SubjectObservation?,
    val reasonCodes: Set<String>,
    val gateFingerprint: String?,
    val buildFingerprint: PostacMasterBuildFingerprint
) {
    init {
        when (state) {
            PostacMasterGeometryState.NOT_DETECTED -> {
                require(acceptedObservation == null)
                require(reasonCodes.isNotEmpty())
            }

            PostacMasterGeometryState.DETECTED_ACCEPTED -> {
                require(rawObservation != null)
                require(acceptedObservation != null)
                require(reasonCodes.isEmpty())
            }

            PostacMasterGeometryState.DETECTED_REJECTED -> {
                require(rawObservation != null)
                require(acceptedObservation == null)
                require(reasonCodes.isNotEmpty())
            }
        }
    }

    fun observation(layer: PostacMasterDiagnosticLayer): SubjectObservation? = when (layer) {
        PostacMasterDiagnosticLayer.RAW -> rawObservation
        PostacMasterDiagnosticLayer.FILTERED -> filteredObservation
        PostacMasterDiagnosticLayer.ACCEPTED -> acceptedObservation
    }

    companion object {
        fun fromFaceDecision(
            decision: FaceGeometryAcceptanceDecision,
            filteredObservation: SubjectObservation?,
            buildFingerprint: PostacMasterBuildFingerprint
        ): PostacMasterGeometryExport = PostacMasterGeometryExport(
            state = when (decision.status) {
                FaceGeometryAcceptanceStatus.NO_FACE ->
                    PostacMasterGeometryState.NOT_DETECTED

                FaceGeometryAcceptanceStatus.FACE_DETECTED_GEOMETRY_ACCEPTED ->
                    PostacMasterGeometryState.DETECTED_ACCEPTED

                FaceGeometryAcceptanceStatus.FACE_DETECTED_GEOMETRY_REJECTED ->
                    PostacMasterGeometryState.DETECTED_REJECTED
            },
            rawObservation = decision.rawObservation,
            filteredObservation = filteredObservation,
            acceptedObservation = decision.activeObservation,
            reasonCodes = decision.reasons.mapTo(linkedSetOf()) { it.name },
            gateFingerprint = decision.gateVersion,
            buildFingerprint = buildFingerprint
        )
    }
}

/**
 * Canonical filenames for diagnostic archives. Exporters may add previews, but these files are
 * mandatory and their semantics may not be changed without incrementing the schema version.
 */
object PostacMasterDiagnosticFiles {
    const val MANIFEST = "diagnostic_manifest.json"
    const val RAW_GEOMETRY = "geometry_raw.json"
    const val FILTERED_GEOMETRY = "geometry_filtered.json"
    const val ACCEPTED_GEOMETRY = "geometry_accepted.json"
    const val RAW_OVERLAY = "overlay_raw.png"
    const val ACCEPTED_OVERLAY = "overlay_accepted.png"
    const val RUNTIME_LOG = "runtime_log.txt"
}

object PostacMasterIntegratedFeatures {
    val REQUIRED = setOf(
        "FACE_VISIBLE_HALF_FILTER",
        "FACE_FAIL_CLOSED_ACCEPTANCE_GATE",
        "BODY_LOCAL_LIMB_SECTION_VALIDATION",
        "BODY_TORSO_CORRIDOR_VALIDATION",
        "BODY_CROSSED_LIMB_OCCLUSION_VALIDATION",
        "RAW_FILTERED_ACCEPTED_DIAGNOSTICS",
        "BUILD_FINGERPRINT"
    )
}
