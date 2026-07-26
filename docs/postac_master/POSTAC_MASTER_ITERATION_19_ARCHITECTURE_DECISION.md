# POSTAC_MASTER — Iteration 19 Architecture Decision

**Status:** ACCEPTED  
**Date:** 2026-07-25  
**Scope:** detector/geometry architecture only  
**Branch:** `feature/postac-master-a1-b1-device-integration`

## 1. Decision

POSTAC_MASTER separates **detection** from **permission to render or edit geometry**.

The canonical pipeline is:

```text
input image
→ detector / ROI proposal
→ raw detector evidence
→ backend-specific normalization
→ common fail-closed acceptance gate
→ accepted active geometry OR explicit rejection
→ overlay / diagnostic export
```

No backend result is considered editable merely because it returned a bounding box, landmarks, contours, a segmentation mask, or a dense mesh.

## 2. Backend responsibilities

### 2.1 ML Kit Face Detection

ML Kit Face Detection is accepted as:

- a face bounding-box and orientation proposal;
- an active-face ROI selector;
- a limited landmark/contour candidate for frontal and mild-yaw cases;
- a source of raw diagnostic evidence.

It is **not** accepted as a universal dense geometry backend.

Official ML Kit documentation states that landmark availability depends on Euler Y. For strong yaw, only the visible-side eye, mouth, ear/cheek and nose-base landmarks are expected. ML Kit contours use fixed point counts, and contour detection is provided only for the most prominent face. Input guidance recommends images of at least `480×360` and faces around `100×100 px` or larger for useful detection quality.

Consequences:

1. Strong profile from ML Kit is fail-closed unless the backend-specific evidence satisfies the common acceptance gate.
2. A full closed face oval, hidden-side eye/brow, contradictory visible side, or missing required pose evidence causes `FACE_DETECTED_GEOMETRY_REJECTED`.
3. ML Kit may still provide the ROI used by another geometry backend.
4. `PARTIAL` must never imply that geometry is safe for rendering or editing.

Primary sources:

- https://developers.google.com/ml-kit/vision/face-detection/face-detection-concepts
- https://developers.google.com/ml-kit/vision/face-detection/android

## 3. ML Kit Pose Detection

ML Kit Pose Detection returns one 33-landmark pose. Official documentation states:

- partial bodies can still produce a pose;
- landmarks not recognized can be assigned coordinates outside the image;
- each landmark has `InFrameLikelihood` in `[0,1]`;
- only one person is returned, choosing the person with the highest confidence when multiple people are present;
- the Z coordinate is experimental and is not true metric 3D.

Consequences:

1. A nominal 33-landmark result is not proof that all landmarks are visible.
2. Every landmark must be classified independently as `VISIBLE`, `LOW_CONFIDENCE`, `OFF_FRAME`, or `MISSING`.
3. A body segment is active only when every required endpoint has sufficient evidence.
4. Missing/off-frame hand or limb landmarks must not generate artificial endpoints or inferred anatomy.
5. Multi-person images require an explicit contamination gate because the API can return one mixed/highest-confidence pose.
6. Z may support diagnostics but cannot be treated as physical depth without further validation.

Primary sources:

- https://developers.google.com/ml-kit/vision/pose-detection
- https://developers.google.com/ml-kit/vision/pose-detection/android

## 4. ML Kit Selfie Segmentation

Selfie Segmentation returns a per-pixel foreground confidence mask. Each value is in `[0,1]`, where values closer to `1` mean higher confidence that a pixel belongs to a person. The API supports one or multiple people and full or upper-body portraits. The mask is not a semantic body-part map and does not prove component identity.

Consequences:

1. The raw mask is evidence, not accepted editable silhouette.
2. Connected-component validation is mandatory before geometry derivation.
3. Detached halo/smear components must be rejected unless anchored to the verified pose/torso.
4. Raw and filtered masks must be exported separately.
5. Pose/mask disagreement blocks only the affected region when safe isolation is possible.

Primary sources:

- https://developers.google.com/ml-kit/vision/selfie-segmentation
- https://developers.google.com/ml-kit/vision/selfie-segmentation/android
- https://developers.google.com/android/reference/com/google/mlkit/vision/segmentation/SegmentationMask

## 5. MediaPipe Face Landmarker

MediaPipe Face Landmarker is accepted as a **dense geometry candidate** when, and only when, a legally sourced and SHA-256-verified `.task` model is configured.

Official task options include:

- `num_faces`;
- minimum face detection confidence;
- minimum face presence confidence;
- minimum tracking confidence;
- optional blendshapes;
- optional facial transformation matrices.

The result contains normalized face landmarks plus optional blendshapes and transformation matrices. These outputs remain candidates and diagnostics; they do not bypass the common acceptance gate.

Consequences:

1. Runtime stays explicitly blocked without verified model provenance.
2. Exactly one active face ROI is selected for POSTAC_MASTER editing.
3. ROI↔source mapping must be reversible and audited.
4. Dense mesh, blendshapes and matrices are raw/filtered evidence until accepted.
5. MediaPipe and ML Kit use the same terminal acceptance states and reason codes.

Primary sources:

- https://ai.google.dev/edge/api/mediapipe/python/mp/tasks/vision/FaceLandmarkerOptions
- https://ai.google.dev/edge/api/mediapipe/python/mp/tasks/vision/FaceLandmarkerResult
- https://ai.google.dev/edge/api/mediapipe/python/mp/tasks/vision/FaceLandmarker

## 6. Research evidence and limitations

Google's Face Mesh publication describes a 468-vertex approximate 3D mesh inferred from a single camera input for AR. Attention Mesh improves accuracy in semantically important eye and lip regions with an attention architecture. These publications support dense on-device geometry as a practical candidate, but do not establish that invisible or occluded anatomy was directly observed.

Therefore:

- dense monocular mesh remains inferred geometry;
- hidden-side or occluded regions cannot be treated as visible evidence automatically;
- accepted active geometry is a policy output, not a direct synonym for model output.

Primary sources:

- https://research.google/pubs/real-time-facial-surface-geometry-from-monocular-video-on-mobile-gpus/
- https://research.google/pubs/attention-mesh-high-fidelity-face-mesh-prediction-in-real-time/
- https://research.google/pubs/blazeface-sub-millisecond-neural-face-detection-on-mobile-gpus/

## 7. Common fail-closed acceptance gate

All face backends terminate in one of:

```text
NO_FACE
FACE_DETECTED_GEOMETRY_ACCEPTED
FACE_DETECTED_GEOMETRY_REJECTED
```

Mandatory rejection classes include:

- unsupported strong profile;
- missing required pose/axis evidence;
- contradictory visible-side evidence;
- hidden-side eye or eyebrow geometry;
- closed full oval in profile/half-profile;
- non-finite or out-of-image coordinates;
- excessive out-of-bbox geometry;
- collapsed landmark spread;
- broken contour/mesh references;
- inconsistent full-image versus ROI result;
- insufficient resolution or face size;
- unresolved occlusion/crop;
- unknown or unverified model provenance.

For `FACE_DETECTED_GEOMETRY_REJECTED`:

- accepted geometry is empty;
- active overlay is empty;
- raw detector JSON/PNG remains available diagnostically;
- the rejection reason and backend/build provenance are mandatory.

## 8. Threshold policy

No project threshold may be justified from a single photograph.

Thresholds must come from one of:

1. an official API contract or documented default;
2. an architecture invariant, such as finite coordinates or referential integrity;
3. a regression matrix covering both sides, poses, occlusions, crops and image sizes;
4. a versioned device-evidence dataset.

Official defaults such as MediaPipe confidence `0.5` are starting configuration values, not proof of application-level geometry acceptance.

## 9. Export and provenance requirements

Every diagnostic package must identify:

- backend and model/runtime version;
- model source, license and SHA-256 when applicable;
- branch and exact commit;
- acceptance-gate fingerprint;
- source image dimensions and EXIF/mirror transform;
- raw, filtered and accepted counts;
- acceptance state and exact reason codes;
- separate raw and accepted overlays;
- raw and filtered segmentation masks when body processing is used.

## 10. Rejected alternatives

### A. Treat ML Kit `PARTIAL` as usable geometry

Rejected. ML Kit can detect a face while returning yaw-dependent, incomplete or contradictory geometry.

### B. Reconstruct the hidden side by mirroring

Rejected. This guesses invisible anatomy and violates the project policy.

### C. Use segmentation mask directly as silhouette

Rejected. The mask is per-pixel person confidence, not a validated semantic body boundary.

### D. Activate MediaPipe with an arbitrary downloaded `.task`

Rejected. Model provenance, license and SHA-256 are mandatory.

### E. Average inconsistent full-image and ROI results

Rejected. Inconsistency is a rejection signal, not an invitation to synthesize a third result.

## 11. Implementation consequences

The next implementation block must:

1. formalize `FaceGeometryAcceptanceResult` with raw evidence, active geometry, severity, reason codes and provenance;
2. make old `PARTIAL` status diagnostic-only;
3. implement ML Kit quality/yaw/size/topology gates;
4. implement per-landmark body visibility and mask-component gates;
5. keep MediaPipe runtime blocked until model provenance is complete;
6. preserve one common fail-closed acceptance gate across all face backends.

## 12. Quality gate

```text
ARCHITECTURE_DECISION: ACCEPTED
DETECTION_EQUALS_USABLE_GEOMETRY: FALSE
ML_KIT_FACE_ROLE: DETECTOR_ROI_AND_LIMITED_GEOMETRY
MEDIAPIPE_FACE_ROLE: DENSE_CANDIDATE_AFTER_VERIFIED_MODEL
COMMON_ACCEPTANCE_GATE: REQUIRED
INVISIBLE_ANATOMY_INFERENCE: FORBIDDEN
MASTER_CHANGED: NO
DEFORMATION_ENABLED: NO
FINAL_UI_CHANGED: NO
```
