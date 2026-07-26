/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis.derive

import com.t8rin.imagetoolbox.lib.portrait_analysis.catalog.PortraitRegionId
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.ObservationBackend
import com.t8rin.imagetoolbox.lib.portrait_analysis.model.SubjectObservation

/** Applies mask/pose gate decisions without synthesising replacement body geometry. */
object BodyMaskPoseRuntimeFilter {

    const val FINGERPRINT = "POSTAC_MASTER_BODY_MASK_POSE_RUNTIME_FILTER_V1"

    /**
     * Removes pose landmarks lacking accepted mask support and every contour that depends on them.
     * Raw evidence remains owned by the caller.
     */
    fun filterPoseInput(
        observation: SubjectObservation,
        assessment: BodyMaskPoseConsistencyGate.Assessment
    ): SubjectObservation {
        val rejectedLandmarkIds = assessment.landmarks.values
            .filterNot(BodyMaskPoseConsistencyGate.LandmarkDecision::maskSupported)
            .mapTo(linkedSetOf(), BodyMaskPoseConsistencyGate.LandmarkDecision::landmarkId)
        val blockedRegionIds = assessment.regions.values
            .filterNot(BodyMaskPoseConsistencyGate.RegionDecision::available)
            .mapTo(linkedSetOf(), BodyMaskPoseConsistencyGate.RegionDecision::regionId)
        return observation.copy(
            landmarks = observation.landmarks - rejectedLandmarkIds,
            contours = observation.contours.filterValues { contour ->
                contour.vertexIds.none { it in rejectedLandmarkIds }
            },
            regions = observation.regions - blockedRegionIds
        )
    }

    /**
     * Removes derived cross-sections and regions blocked by the local mask/pose assessment. No new
     * endpoints or replacement sections are created.
     */
    fun filterEnrichment(
        enrichment: BodySilhouetteEnrichmentResult,
        assessment: BodyMaskPoseConsistencyGate.Assessment
    ): BodySilhouetteEnrichmentResult {
        val acceptedRegionIds = assessment.acceptedRegionIds
        val observation = enrichment.observation
        val filteredContours = observation.contours.filterValues { contour ->
            if (contour.backend != ObservationBackend.DERIVED_BODY_SILHOUETTE) {
                true
            } else {
                sectionRegionId(contour.id)?.let { it in acceptedRegionIds } ?: false
            }
        }
        val retainedDerivedLandmarkIds = filteredContours.values
            .filter { it.backend == ObservationBackend.DERIVED_BODY_SILHOUETTE }
            .flatMapTo(linkedSetOf()) { it.vertexIds }
        val filteredLandmarks = observation.landmarks.filterValues { landmark ->
            landmark.backend != ObservationBackend.DERIVED_BODY_SILHOUETTE ||
                landmark.id in retainedDerivedLandmarkIds
        }
        val bodyRegionIds = setOf(
            PortraitRegionId.SHOULDER_CONTOUR,
            PortraitRegionId.ARM_CONTOURS,
            PortraitRegionId.WAIST_CONTOUR,
            PortraitRegionId.HIP_CONTOUR,
            PortraitRegionId.LEG_CONTOURS
        )
        val filteredRegions = observation.regions.filterKeys { regionId ->
            regionId !in bodyRegionIds || regionId in acceptedRegionIds
        }
        return enrichment.copy(
            observation = observation.copy(
                landmarks = filteredLandmarks,
                contours = filteredContours,
                regions = filteredRegions
            ),
            derivedRegionIds = enrichment.derivedRegionIds.intersect(acceptedRegionIds)
        )
    }

    fun sectionRegionId(sectionId: String): String? = when {
        sectionId.contains("shoulder") -> PortraitRegionId.SHOULDER_CONTOUR
        sectionId.contains("upper_arm") || sectionId.contains("forearm") ->
            PortraitRegionId.ARM_CONTOURS
        sectionId.contains("waist") -> PortraitRegionId.WAIST_CONTOUR
        sectionId.contains("hip") -> PortraitRegionId.HIP_CONTOUR
        sectionId.contains("thigh") || sectionId.contains("calf") ->
            PortraitRegionId.LEG_CONTOURS
        else -> null
    }
}
