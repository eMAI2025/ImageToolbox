# POSTAC_MASTER A2 — MediaPipe Face Landmarker scaffold

## Status

`SCAFFOLD_ONLY — RUNTIME BLOCKED UNTIL VERIFIED MODEL`

## Inventory result

The repository contains the common portrait-analysis model and the active fail-closed face acceptance gate, but no verified `face_landmarker.task` model asset and no active MediaPipe Face Landmarker runtime implementation on the integration branch.

No external model was downloaded in this iteration.

## Required model contract

A runtime model is accepted only when all fields are known and stored with the build:

- asset name: `face_landmarker.task`;
- SHA-256: exact 64-character digest;
- source URI: authoritative source location;
- license identifier;
- verifier identity;
- UTC verification timestamp.

A file matching the name without this provenance remains `MODEL_PROVENANCE_UNVERIFIED` and must not be initialized.

## Runtime boundary

The future adapter must:

1. receive one explicitly selected face ROI;
2. run only inside that ROI;
3. preserve the raw detector result;
4. map ROI-normalized points back to source-image coordinates;
5. preserve a separate visibility-filtered candidate;
6. pass the candidate through the common `FaceGeometryAcceptanceGate`;
7. expose active geometry only after `FACE_DETECTED_GEOMETRY_ACCEPTED`;
8. return no active geometry for `NO_FACE` or `FACE_DETECTED_GEOMETRY_REJECTED`.

MediaPipe cannot bypass or replace the common fail-closed gate.

## Coordinate contract

`ActiveFaceRoi` uses normalized source-image bounds and stores source width and height. `MediaPipeFaceRoiTransform` provides reversible ROI-to-source and source-to-ROI mapping.

The transform deliberately does not clip points. Out-of-range coordinates remain diagnostic evidence and are evaluated by the acceptance gate instead of being silently corrected.

## Geometry layers

Every result preserves three distinct layers:

- `rawObservation` — direct backend proposal;
- `visibleObservation` — candidate after visibility/topology filtering;
- `acceptedObservation` — nullable geometry approved by the common gate.

The diagnostic record contains separate raw, visible and accepted landmark and triangle counts.

## Single-face rule

- the upstream detector may find multiple faces;
- one face index must be selected explicitly;
- the MediaPipe runtime receives one active ROI;
- whole-frame implicit multi-face dense processing is outside the A2 contract.

## Export contract

The future diagnostic ZIP must contain:

- model provenance and SHA-256;
- contract version;
- runtime state;
- active ROI and selected face index;
- raw/visible/accepted counts;
- raw geometry payload;
- visible candidate payload;
- acceptance decision and reason codes;
- accepted geometry payload only when approved;
- blendshape and transformation-matrix counts and values as diagnostic data;
- build branch, exact commit and active feature flags.

## Explicit exclusions

- no model download;
- no unverified `.task` asset;
- no face or body deformation;
- no final UI work;
- no merge to `master`;
- no automatic acceptance based solely on MediaPipe detection confidence.

## Next step

Iteration 15 may implement the SDK-independent dense-mesh adapter, index preservation, clipping diagnostics and ROI transform tests. Runtime initialization remains blocked until a legal and verified model asset is present.
