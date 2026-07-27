/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis.derive

import com.t8rin.imagetoolbox.lib.portrait_analysis.catalog.PortraitLandmarkId
import com.t8rin.imagetoolbox.lib.portrait_analysis.catalog.PortraitParameterId
import com.t8rin.imagetoolbox.lib.portrait_analysis.catalog.PortraitRegionId
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation

/**
 * Architecture-only capability gate for future isolated silhouette edits.
 *
 * This contract never warps coordinates, moves pixels, estimates hidden anatomy, protects background
 * pixels or connects to final UI. It only proves that a single requested body parameter has complete
 * accepted evidence and may enter a later, isolated Stage-B adapter test.
 */
object SilhouetteEditContract {

    const val FINGERPRINT = "POSTAC_MASTER_SILHOUETTE_EDIT_CONTRACT_V1"

    enum class Status {
        BLOCKED,
        READY_FOR_ISOLATED_STAGE_B_ADAPTER
    }

    enum class ValueDomain {
        BIDIRECTIONAL_MINUS_ONE_TO_ONE,
        UNIDIRECTIONAL_ZERO_TO_ONE
    }

    enum class Blocker {
        A1_DEVICE_PASS_MISSING,
        RAW_OBSERVATION_MISSING,
        FILTERED_OBSERVATION_MISSING,
        ACCEPTED_OBSERVATION_MISSING,
        BODY_NOT_DETECTED,
        SUBJECT_MASK_MISSING,
        PARAMETER_UNSUPPORTED,
        REQUEST_VALUE_NON_FINITE,
        REQUEST_VALUE_OUT_OF_DOMAIN,
        REQUIRED_BODY_REGION_NOT_ACCEPTED,
        REQUIRED_LANDMARK_NOT_ACCEPTED,
        ISOLATED_STAGE_B_TEST_NOT_DECLARED,
        BACKGROUND_PROTECTION_EVIDENCE_MISSING,
        PROVENANCE_MISSING,
        PROVENANCE_MISMATCH
    }

    data class ParameterSpec(
        val parameterId: String,
        val valueDomain: ValueDomain,
        val requiredRegionIds: Set<String>,
        val requiredLandmarkIds: Set<String>
    )

    data class Evidence(
        val a1DevicePass: Boolean,
        val rawObservation: SubjectObservation?,
        val filteredObservation: SubjectObservation?,
        val acceptedObservation: SubjectObservation?,
        val acceptedRegionIds: Set<String>,
        val parameterId: String,
        val normalizedValue: Float,
        val isolatedStageBTestDeclared: Boolean,
        val backgroundProtectionEvidenceReady: Boolean,
        val provenanceBranch: String,
        val provenanceCommit: String
    )

    data class Request internal constructor(
        val acceptedObservation: SubjectObservation,
        val acceptedRegionIds: Set<String>,
        val parameterSpec: ParameterSpec,
        val normalizedValue: Float,
        val provenanceBranch: String,
        val provenanceCommit: String
    )

    data class Decision internal constructor(
        val status: Status,
        val blockers: Set<Blocker>,
        val request: Request?,
        val fingerprint: String = FINGERPRINT
    ) {
        init {
            when (status) {
                Status.BLOCKED -> {
                    require(blockers.isNotEmpty())
                    require(request == null)
                }
                Status.READY_FOR_ISOLATED_STAGE_B_ADAPTER -> {
                    require(blockers.isEmpty())
                    require(request != null)
                }
            }
        }
    }

    fun evaluate(evidence: Evidence): Decision {
        val blockers = linkedSetOf<Blocker>()
        if (!evidence.a1DevicePass) blockers += Blocker.A1_DEVICE_PASS_MISSING
        if (evidence.rawObservation == null) blockers += Blocker.RAW_OBSERVATION_MISSING
        if (evidence.filteredObservation == null) blockers += Blocker.FILTERED_OBSERVATION_MISSING

        val accepted = evidence.acceptedObservation
        if (accepted == null) {
            blockers += Blocker.ACCEPTED_OBSERVATION_MISSING
        } else {
            if (accepted.bodyCount != 1) blockers += Blocker.BODY_NOT_DETECTED
            if (PortraitRegionId.SUBJECT_MASK !in accepted.masks) {
                blockers += Blocker.SUBJECT_MASK_MISSING
            }
        }

        val spec = parameterSpecs[evidence.parameterId]
        if (spec == null) {
            blockers += Blocker.PARAMETER_UNSUPPORTED
        } else {
            if (!evidence.normalizedValue.isFinite()) {
                blockers += Blocker.REQUEST_VALUE_NON_FINITE
            } else if (!spec.valueDomain.accepts(evidence.normalizedValue)) {
                blockers += Blocker.REQUEST_VALUE_OUT_OF_DOMAIN
            }
            if (!evidence.acceptedRegionIds.containsAll(spec.requiredRegionIds)) {
                blockers += Blocker.REQUIRED_BODY_REGION_NOT_ACCEPTED
            }
            if (accepted != null && !accepted.landmarks.keys.containsAll(spec.requiredLandmarkIds)) {
                blockers += Blocker.REQUIRED_LANDMARK_NOT_ACCEPTED
            }
        }

        if (!evidence.isolatedStageBTestDeclared) {
            blockers += Blocker.ISOLATED_STAGE_B_TEST_NOT_DECLARED
        }
        if (!evidence.backgroundProtectionEvidenceReady) {
            blockers += Blocker.BACKGROUND_PROTECTION_EVIDENCE_MISSING
        }
        if (evidence.provenanceBranch.isBlank() || evidence.provenanceCommit.isBlank()) {
            blockers += Blocker.PROVENANCE_MISSING
        }

        val request = if (blockers.isEmpty() && accepted != null && spec != null) {
            Request(
                acceptedObservation = accepted,
                acceptedRegionIds = evidence.acceptedRegionIds.toSet(),
                parameterSpec = spec,
                normalizedValue = evidence.normalizedValue,
                provenanceBranch = evidence.provenanceBranch,
                provenanceCommit = evidence.provenanceCommit
            )
        } else {
            null
        }

        return Decision(
            status = if (request == null) {
                Status.BLOCKED
            } else {
                Status.READY_FOR_ISOLATED_STAGE_B_ADAPTER
            },
            blockers = blockers,
            request = request
        )
    }

    fun verifyAdapterProvenance(
        request: Request,
        adapterBranch: String,
        adapterCommit: String
    ): Set<Blocker> = buildSet {
        if (adapterBranch.isBlank() || adapterCommit.isBlank()) {
            add(Blocker.PROVENANCE_MISSING)
        } else if (
            adapterBranch != request.provenanceBranch ||
            adapterCommit != request.provenanceCommit
        ) {
            add(Blocker.PROVENANCE_MISMATCH)
        }
    }

    fun specFor(parameterId: String): ParameterSpec? = parameterSpecs[parameterId]

    private fun ValueDomain.accepts(value: Float): Boolean = when (this) {
        ValueDomain.BIDIRECTIONAL_MINUS_ONE_TO_ONE -> value in -1f..1f
        ValueDomain.UNIDIRECTIONAL_ZERO_TO_ONE -> value in 0f..1f
    }

    private val parameterSpecs = listOf(
        ParameterSpec(
            parameterId = PortraitParameterId.SHOULDER_WIDTH,
            valueDomain = ValueDomain.BIDIRECTIONAL_MINUS_ONE_TO_ONE,
            requiredRegionIds = setOf(
                PortraitRegionId.SUBJECT_MASK,
                PortraitRegionId.SHOULDER_CONTOUR
            ),
            requiredLandmarkIds = setOf(
                PortraitLandmarkId.LEFT_SHOULDER,
                PortraitLandmarkId.RIGHT_SHOULDER
            )
        ),
        ParameterSpec(
            parameterId = PortraitParameterId.ARM_WIDTH,
            valueDomain = ValueDomain.BIDIRECTIONAL_MINUS_ONE_TO_ONE,
            requiredRegionIds = setOf(
                PortraitRegionId.SUBJECT_MASK,
                PortraitRegionId.ARM_CONTOURS
            ),
            requiredLandmarkIds = setOf(
                PortraitLandmarkId.LEFT_SHOULDER,
                PortraitLandmarkId.RIGHT_SHOULDER,
                PortraitLandmarkId.LEFT_ELBOW,
                PortraitLandmarkId.RIGHT_ELBOW,
                PortraitLandmarkId.LEFT_WRIST,
                PortraitLandmarkId.RIGHT_WRIST
            )
        ),
        ParameterSpec(
            parameterId = PortraitParameterId.WAIST_WIDTH,
            valueDomain = ValueDomain.BIDIRECTIONAL_MINUS_ONE_TO_ONE,
            requiredRegionIds = setOf(
                PortraitRegionId.SUBJECT_MASK,
                PortraitRegionId.WAIST_CONTOUR
            ),
            requiredLandmarkIds = setOf(
                PortraitLandmarkId.LEFT_SHOULDER,
                PortraitLandmarkId.RIGHT_SHOULDER,
                PortraitLandmarkId.LEFT_HIP,
                PortraitLandmarkId.RIGHT_HIP
            )
        ),
        ParameterSpec(
            parameterId = PortraitParameterId.HIP_WIDTH,
            valueDomain = ValueDomain.BIDIRECTIONAL_MINUS_ONE_TO_ONE,
            requiredRegionIds = setOf(
                PortraitRegionId.SUBJECT_MASK,
                PortraitRegionId.HIP_CONTOUR
            ),
            requiredLandmarkIds = setOf(
                PortraitLandmarkId.LEFT_HIP,
                PortraitLandmarkId.RIGHT_HIP
            )
        ),
        ParameterSpec(
            parameterId = PortraitParameterId.LEG_WIDTH,
            valueDomain = ValueDomain.BIDIRECTIONAL_MINUS_ONE_TO_ONE,
            requiredRegionIds = setOf(
                PortraitRegionId.SUBJECT_MASK,
                PortraitRegionId.LEG_CONTOURS
            ),
            requiredLandmarkIds = setOf(
                PortraitLandmarkId.LEFT_HIP,
                PortraitLandmarkId.RIGHT_HIP,
                PortraitLandmarkId.LEFT_KNEE,
                PortraitLandmarkId.RIGHT_KNEE,
                PortraitLandmarkId.LEFT_ANKLE,
                PortraitLandmarkId.RIGHT_ANKLE
            )
        ),
        ParameterSpec(
            parameterId = PortraitParameterId.LEG_LENGTH,
            valueDomain = ValueDomain.UNIDIRECTIONAL_ZERO_TO_ONE,
            requiredRegionIds = setOf(
                PortraitRegionId.SUBJECT_MASK,
                PortraitRegionId.HIP_CONTOUR,
                PortraitRegionId.LEG_CONTOURS
            ),
            requiredLandmarkIds = setOf(
                PortraitLandmarkId.LEFT_HIP,
                PortraitLandmarkId.RIGHT_HIP,
                PortraitLandmarkId.LEFT_KNEE,
                PortraitLandmarkId.RIGHT_KNEE,
                PortraitLandmarkId.LEFT_ANKLE,
                PortraitLandmarkId.RIGHT_ANKLE
            )
        )
    ).associateBy(ParameterSpec::parameterId)
}
