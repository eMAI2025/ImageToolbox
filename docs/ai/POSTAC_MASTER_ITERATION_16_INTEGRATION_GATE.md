# POSTAC_MASTER — Iteration 16 integration gate

## Scope

This checkpoint defines the single-build contract that iteration 17 must compile and package. It does not perform device testing, deformation, final UI work or a merge.

## Canonical state model

Face and body diagnostics use the same three semantic states:

- `NOT_DETECTED` — no usable candidate exists;
- `DETECTED_ACCEPTED` — a candidate exists and active geometry passed all fail-closed gates;
- `DETECTED_REJECTED` — a candidate exists, but active geometry is empty because evidence is incomplete, contradictory or unsafe.

`DETECTED` alone never authorizes rendering or editing geometry.

## Mandatory diagnostic layers

Every geometry report must preserve three distinct layers:

1. `RAW` — unmodified backend evidence;
2. `FILTERED` — visibility/locality-reduced candidate geometry;
3. `ACCEPTED` — geometry permitted for active overlay and later editing.

For `DETECTED_REJECTED`, `ACCEPTED` must be absent. Raw evidence remains available only in the diagnostic package.

## Mandatory archive files

- `diagnostic_manifest.json`;
- `geometry_raw.json`;
- `geometry_filtered.json`;
- `geometry_accepted.json`;
- `overlay_raw.png`;
- `overlay_accepted.png`;
- `runtime_log.txt`.

`geometry_accepted.json` and `overlay_accepted.png` may represent an explicitly empty result, but may not silently fall back to raw detector output.

## Build fingerprint

The manifest must include:

- diagnostic schema version;
- integration contract version;
- branch;
- exact source commit injected by the final build;
- backend and model versions;
- active feature flags;
- APK SHA-256 after artifact extraction.

The exact APK SHA cannot be self-embedded during compilation. It is added to the external artifact manifest after the APK is produced in iteration 17.

## Static integration inventory

The device-integration branch contains the following required features:

| Feature | Source path / evidence | Status |
|---|---|---|
| visible-half face filtering | `FaceVisibleGeometryFilter.kt` | PRESENT |
| fail-closed face acceptance | `FaceGeometryAcceptanceGate.kt` | PRESENT |
| raw and active face observations | `PortraitLabRunOutput.rawObservation`, `faceGeometryAcceptance` | PRESENT |
| local limb section validation | `BodySilhouetteSectionValidator.kt` | PRESENT |
| torso corridor validation | `BodyTorsoSectionValidator.kt` | PRESENT |
| crossed-limb torso occlusion | `BodyTorsoSectionValidator.kt` | PRESENT |
| silhouette raw/filtered counters | Market `SilhouetteLabRunnerFactory.kt` | PRESENT |
| build/source fingerprint | Market `SilhouetteLabRunnerFactory.kt` | PRESENT, final commit injection pending |
| unified V2 contract and filenames | `PostacMasterDiagnosticContract.kt` | PRESENT |

## A2 boundary

The MediaPipe scaffold and dense adapter remain on draft PR #11. They define candidate geometry only and cannot bypass the common acceptance gate. A verified `face_landmarker.task` is not present, therefore MediaPipe runtime is not part of the iteration 17 executable acceptance path unless provenance is supplied before the build.

## Iteration 17 acceptance entry conditions

Iteration 17 may start only from this integration branch and must verify:

- compilation of all integrated A1/B1 safety code;
- unit and integration tests for accepted/rejected geometry;
- fail-closed empty active overlay for strong profile, partial or contradictory face evidence;
- body region blocking for missing, outside-frame or mask-inconsistent evidence;
- diagnostic V2 files and build fingerprint;
- exactly one ARM64 APK and its SHA-256;
- no merge before target-device acceptance.

## Prohibited behavior

- no raw-to-active fallback;
- no reconstruction of hidden face or body anatomy;
- no face/body deformation;
- no final UI redesign;
- no commit to `master`;
- no draft PR merge.
