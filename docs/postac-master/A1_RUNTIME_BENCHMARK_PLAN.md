# A1 runtime benchmark plan

## Objective

Compare observation backends on the same images before any geometry modification is implemented.

## Candidate backends

### Face

- MediaPipe Face Landmarker;
- ML Kit Face Mesh;
- ML Kit Face Detection for coarse pose and contour evidence.

### Body

- MediaPipe Pose Landmarker;
- ML Kit Pose Accurate;
- ML Kit Selfie Segmentation;
- optional DensePose desktop quality comparison.

## Common output contract

Each adapter must produce, when available:

- normalized landmark coordinates;
- backend identifier;
- per-landmark confidence and visibility;
- semantic-region confidence, occlusion and pixel coverage;
- contours, meshes and semantic masks;
- face, body and subject counts;
- pose;
- processing time and optional memory use.

## Required cases

### Face cases

1. neutral front;
2. three-quarter left;
3. three-quarter right;
4. strict profile;
5. visible teeth;
6. closed mouth;
7. partial occlusion;
8. low contrast.

### Body cases

1. upper body, fitted clothing;
2. upper body, loose clothing;
3. full body, front;
4. full body, three-quarter;
5. arms crossing torso;
6. straight architectural background.

## Ground truth

Every case must have a manually reviewed ground-truth manifest defining:

- expected subject, face and body count ranges;
- required and forbidden canonical landmarks;
- required and forbidden semantic regions;
- expected ENABLED/DISABLED parameter decisions;
- visible source annotations such as occlusion, low contrast or straight background lines.

Ground truth must remain backend-independent and must not describe hidden anatomy.

Schema and authoring rules are defined in `A1_GROUND_TRUTH_SCHEMA.md`.

## Measurements

- agreement with ground truth;
- false parameter availability;
- false parameter unavailability;
- critical-landmark completeness;
- repeated-run stability;
- left/right semantic consistency;
- occlusion handling;
- subject-mask edge quality;
- background-line separation;
- deterministic parameter gates;
- processing time;
- memory and package-size impact.

## Pass rules

- missing evidence disables the affected parameter;
- no hidden anatomy is guessed;
- a repeated image yields the same gate decision;
- unsupported subject count blocks only incompatible operations;
- the user receives overlays and an ENABLED/DISABLED report;
- benchmark output is compared with reviewed ground truth;
- Stage B remains blocked until explicit user acceptance.

## Implementation status

Completed in the current A1 branch:

1. common detector-independent models;
2. deterministic parameter-gate evaluator;
3. unit tests for missing landmarks, low confidence, occlusion, counts and pose;
4. ML Kit adapter modules;
5. MediaPipe adapter modules;
6. observation merge pipeline;
7. overlay scene models;
8. benchmark and parameter reports;
9. deterministic ground-truth evaluator and renderer.

Remaining before A1 acceptance:

1. integrate the benchmark workflow into a usable Portrait Lab screen;
2. assemble and checksum the agreed test-image set;
3. author ground-truth manifests;
4. run repeated benchmarks on Realme RMX5051;
5. select the backend combination and thresholds;
6. obtain explicit user acceptance before Stage B.
