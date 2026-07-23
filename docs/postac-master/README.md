# POSTAC_MASTER integration

Status: **Stage A1 — detector, parameter-gate and benchmark infrastructure**

This directory documents the integration of the POSTAC_MASTER portrait/body analysis architecture into ImageToolbox.

## Current scope

Stage A1 is observation-only:

- normalize detector outputs from multiple backends;
- represent landmarks, semantic regions, contours, meshes, masks, visibility, occlusion and pose;
- merge compatible backend observations;
- evaluate each edit parameter independently;
- disable a parameter when required evidence is missing;
- create deterministic overlay and benchmark report models;
- compare runtime results with manually reviewed ground truth.

## Implemented modules

- `lib:portrait-analysis` — backend-independent contracts, gates, reports, overlays and ground-truth evaluation;
- `lib:portrait-analysis-mlkit` — ML Kit face, pose and subject-segmentation adapters;
- `lib:portrait-analysis-mediapipe` — MediaPipe face and pose adapters.

Detector-specific dependencies remain isolated from the common contract.

## Explicitly out of scope in A1

- face or body deformation;
- personal face identification;
- personal correction-profile calibration;
- clothing replacement;
- background generation;
- importing Xiaomi, FaceUnity, Hypic or other proprietary code, binaries, models or assets.

## Execution-mode distinction

POSTAC_MASTER does not prohibit explicit face editing. It separates:

- identity-locked scene generation and clothing/background replacement;
- explicit, reversible face editing;
- explicit, reversible body editing.

The formal distinction is documented in:

`EXECUTION_MODES_AND_IDENTITY_POLICY.md`

## Benchmark ground truth

Every runtime benchmark image should have a manually reviewed expected state for counts, canonical evidence and parameter availability.

Schema and authoring rules:

`A1_GROUND_TRUTH_SCHEMA.md`

## Sequence gate

1. **A1:** detector, mask, gate, report and ground-truth benchmark;
2. **A1 acceptance:** runtime comparison on the agreed image set and target device;
3. **B:** controlled geometry tests after explicit user acceptance of A1;
4. **C:** personal identity/reference calibration after explicit user acceptance of B.

## Supporting documents

- `A1_RUNTIME_BENCHMARK_PLAN.md`;
- `A1_GROUND_TRUTH_SCHEMA.md`;
- `EXECUTION_MODES_AND_IDENTITY_POLICY.md`;
- `SOURCE_RETENTION_POLICY.md`;
- `CHECKSUMS.sha256`.
