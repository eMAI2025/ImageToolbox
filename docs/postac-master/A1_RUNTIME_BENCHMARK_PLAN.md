# A1 runtime benchmark plan

## Objective

Compare observation backends on the same images before any geometry modification is implemented.

## Candidate backends

### Face

- MediaPipe Face Landmarker;
- ML Kit Face Mesh.

### Body

- MediaPipe Pose Landmarker;
- ML Kit Pose Accurate;
- Selfie/subject segmentation;
- optional DensePose desktop quality comparison.

## Common output contract

Each adapter must produce:

- normalized landmark coordinates;
- backend identifier;
- per-landmark confidence and visibility;
- semantic-region confidence, occlusion and pixel coverage;
- face/body/subject counts;
- pose where available;
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

## Measurements

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
- Stage B remains blocked until explicit user acceptance.

## First implementation increment

1. common detector-independent models;
2. deterministic parameter-gate evaluator;
3. unit tests for missing landmarks, low confidence, occlusion and pose;
4. ML Kit adapter;
5. MediaPipe adapter;
6. benchmark UI and export report.
