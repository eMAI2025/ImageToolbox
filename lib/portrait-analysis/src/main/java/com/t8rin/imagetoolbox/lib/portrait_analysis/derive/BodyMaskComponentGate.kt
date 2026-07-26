/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis.derive

/**
 * Fail-closed validation of connected components produced from a person-confidence mask.
 *
 * This contract never performs morphology and never joins disconnected foreground components.
 * Raw components are retained verbatim for diagnostics. Only components supported by the verified
 * torso/pose evidence are exposed in [Assessment.filteredComponents].
 */
object BodyMaskComponentGate {

    const val FINGERPRINT = "POSTAC_MASTER_BODY_MASK_COMPONENT_GATE_V1"

    enum class RejectionReason {
        PRIMARY_COMPONENT_MISSING,
        COMPONENT_NOT_TORSO_ANCHORED,
        DISCONNECTED_HALO_ABOVE_HEAD,
        DISCONNECTED_BACKGROUND_SMEAR,
        COMPONENT_TOO_SMALL_FOR_INDEPENDENT_PERSON,
        MULTI_PERSON_COMPONENT_AMBIGUOUS,
        NON_FINITE_COMPONENT_GEOMETRY
    }

    data class NormalizedBounds(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float
    ) {
        init {
            require(left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite())
            require(right >= left && bottom >= top)
        }

        val area: Float get() = (right - left) * (bottom - top)
        val centerY: Float get() = (top + bottom) / 2f
        val insideImage: Boolean
            get() = left in 0f..1f && top in 0f..1f && right in 0f..1f && bottom in 0f..1f
    }

    /**
     * Component evidence must be extracted without modifying the original confidence mask.
     *
     * [torsoAnchorCount] counts verified visible torso pose landmarks inside or immediately adjacent
     * to the component. [connectedToPrimary] is graph connectivity from the original thresholded
     * mask, not a morphologically-created bridge.
     */
    data class ComponentEvidence(
        val id: String,
        val pixelCount: Int,
        val bounds: NormalizedBounds,
        val torsoAnchorCount: Int,
        val overlapsTorsoCorridor: Boolean,
        val connectedToPrimary: Boolean,
        val aboveVerifiedHead: Boolean,
        val independentPersonAnchorCount: Int = 0
    ) {
        init {
            require(id.isNotBlank())
            require(pixelCount >= 0)
            require(torsoAnchorCount >= 0)
            require(independentPersonAnchorCount >= 0)
        }
    }

    data class ComponentDecision(
        val component: ComponentEvidence,
        val accepted: Boolean,
        val reasons: Set<RejectionReason>
    )

    data class Assessment(
        val rawComponents: List<ComponentEvidence>,
        val filteredComponents: List<ComponentEvidence>,
        val decisions: List<ComponentDecision>,
        val primaryComponentId: String?,
        val fingerprint: String = FINGERPRINT
    ) {
        val rejectedComponents: List<ComponentDecision>
            get() = decisions.filterNot(ComponentDecision::accepted)

        companion object {
            fun empty() = Assessment(
                rawComponents = emptyList(),
                filteredComponents = emptyList(),
                decisions = emptyList(),
                primaryComponentId = null
            )
        }
    }

    /**
     * Validates already-labelled components. The largest torso-anchored component is selected as the
     * primary person component. Detached regions are never joined to it.
     */
    fun evaluate(components: List<ComponentEvidence>): Assessment {
        if (components.isEmpty()) return Assessment.empty()

        val primary = components
            .filter { it.bounds.insideImage && it.torsoAnchorCount > 0 && it.overlapsTorsoCorridor }
            .maxByOrNull(ComponentEvidence::pixelCount)

        val decisions = components.map { component ->
            val reasons = linkedSetOf<RejectionReason>()

            if (!component.bounds.insideImage || component.bounds.area <= 0f) {
                reasons += RejectionReason.NON_FINITE_COMPONENT_GEOMETRY
            }

            if (primary == null) {
                reasons += RejectionReason.PRIMARY_COMPONENT_MISSING
            } else if (component.id == primary.id) {
                // The primary component is accepted when its direct torso evidence is valid.
            } else {
                val hasIndependentPersonEvidence = component.independentPersonAnchorCount >= 3
                val belongsToPrimaryWithoutRepair = component.connectedToPrimary

                if (hasIndependentPersonEvidence) {
                    reasons += RejectionReason.MULTI_PERSON_COMPONENT_AMBIGUOUS
                } else if (!belongsToPrimaryWithoutRepair) {
                    if (component.aboveVerifiedHead) {
                        reasons += RejectionReason.DISCONNECTED_HALO_ABOVE_HEAD
                    } else {
                        reasons += RejectionReason.DISCONNECTED_BACKGROUND_SMEAR
                    }
                    reasons += RejectionReason.COMPONENT_NOT_TORSO_ANCHORED
                }

                if (
                    component.pixelCount > 0 &&
                    component.pixelCount < minimumIndependentComponentPixels(primary.pixelCount) &&
                    !belongsToPrimaryWithoutRepair
                ) {
                    reasons += RejectionReason.COMPONENT_TOO_SMALL_FOR_INDEPENDENT_PERSON
                }
            }

            ComponentDecision(
                component = component,
                accepted = reasons.isEmpty(),
                reasons = reasons
            )
        }

        return Assessment(
            rawComponents = components,
            filteredComponents = decisions.filter(ComponentDecision::accepted).map { it.component },
            decisions = decisions,
            primaryComponentId = primary?.id
        )
    }

    /** Structural floor used only to classify detached noise relative to the verified person. */
    private fun minimumIndependentComponentPixels(primaryPixelCount: Int): Int =
        (primaryPixelCount * 0.01f).toInt().coerceAtLeast(16)
}
