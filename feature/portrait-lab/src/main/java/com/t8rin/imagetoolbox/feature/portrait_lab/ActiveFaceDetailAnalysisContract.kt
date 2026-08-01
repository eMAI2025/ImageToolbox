/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.feature.portrait_lab

/**
 * Frozen hand-off from full-image multi-face detection to optional selected-face detail analysis.
 *
 * This contract never detects faces, changes the baseline inventory, chooses index zero implicitly,
 * crops the source, runs Face Mesh, accepts edit geometry or starts deformation. It only proves that
 * a face index was explicitly selected from the immutable [FaceDetectionBaseline.Result].
 */
object ActiveFaceDetailAnalysisContract {

    const val FINGERPRINT = "ACTIVE_FACE_DETAIL_ANALYSIS_V1"

    enum class Status {
        WAITING_FOR_EXPLICIT_SELECTION,
        READY_FOR_OPTIONAL_CROP_AND_DETAIL,
        BLOCKED
    }

    enum class Blocker {
        NO_FACES_DETECTED,
        SELECTED_FACE_NOT_IN_BASELINE
    }

    data class Evidence(
        val baseline: FaceDetectionBaseline.Result,
        val explicitlySelectedFaceIndex: Int?
    )

    data class Request internal constructor(
        val selectedFace: FaceDetectionBaseline.FaceRecord,
        val baselineFaceCount: Int,
        val optionalCropBounds: PortraitNormalizedRect,
        val fingerprint: String = FINGERPRINT
    ) {
        init {
            require(baselineFaceCount > 0)
            require(selectedFace.faceIndex >= 0)
            require(optionalCropBounds == selectedFace.boundingBox) {
                "Crop seed may only reference the selected baseline face bounds"
            }
        }
    }

    data class Decision internal constructor(
        val status: Status,
        val blockers: Set<Blocker>,
        val activeFaceIndex: Int?,
        val request: Request?,
        val baselineFaceCount: Int,
        val fingerprint: String = FINGERPRINT
    ) {
        init {
            when (status) {
                Status.WAITING_FOR_EXPLICIT_SELECTION -> {
                    require(activeFaceIndex == null)
                    require(request == null)
                    require(blockers.isEmpty())
                }
                Status.READY_FOR_OPTIONAL_CROP_AND_DETAIL -> {
                    require(activeFaceIndex != null)
                    require(request != null)
                    require(blockers.isEmpty())
                    require(request.selectedFace.faceIndex == activeFaceIndex)
                    require(request.baselineFaceCount == baselineFaceCount)
                }
                Status.BLOCKED -> {
                    require(activeFaceIndex == null)
                    require(request == null)
                    require(blockers.isNotEmpty())
                }
            }
        }
    }

    fun evaluate(evidence: Evidence): Decision {
        val faces = evidence.baseline.faces
        val selectedIndex = evidence.explicitlySelectedFaceIndex

        if (selectedIndex == null) {
            return if (faces.isEmpty()) {
                Decision(
                    status = Status.BLOCKED,
                    blockers = setOf(Blocker.NO_FACES_DETECTED),
                    activeFaceIndex = null,
                    request = null,
                    baselineFaceCount = 0
                )
            } else {
                Decision(
                    status = Status.WAITING_FOR_EXPLICIT_SELECTION,
                    blockers = emptySet(),
                    activeFaceIndex = null,
                    request = null,
                    baselineFaceCount = faces.size
                )
            }
        }

        val selectedFace = faces.singleOrNull { it.faceIndex == selectedIndex }
            ?: return Decision(
                status = Status.BLOCKED,
                blockers = setOf(Blocker.SELECTED_FACE_NOT_IN_BASELINE),
                activeFaceIndex = null,
                request = null,
                baselineFaceCount = faces.size
            )

        return Decision(
            status = Status.READY_FOR_OPTIONAL_CROP_AND_DETAIL,
            blockers = emptySet(),
            activeFaceIndex = selectedIndex,
            request = Request(
                selectedFace = selectedFace,
                baselineFaceCount = faces.size,
                optionalCropBounds = selectedFace.boundingBox
            ),
            baselineFaceCount = faces.size
        )
    }
}