# P0 runtime stabilization and scope freeze

Status: **implementation in progress on `feature/postac-master-a1-benchmark`**

## Product decision

Portrait development is now face-first.

The body editor is frozen at the preset-contract level. The current increment must not add:

- free body sliders;
- manual hip, ankle or height boundaries;
- local body handles;
- manual body mesh editing;
- leg-length or height controls;
- body deformation UI.

Future body presets remain explicit parameter lists rather than opaque enhancement commands:

- `ORIGINAL`;
- `BODY_BALANCE_A`;
- `BODY_BALANCE_B`.

No preset is executed until every affected region has its own evidence gate and conservative limit.

## P0 objective

The target device must open Portrait Lab and process one image without a process-level crash.

The stable P0 detector set is:

- ML Kit Face Detection;
- ML Kit Pose Accurate;
- ML Kit Selfie Segmentation;
- MediaPipe Face Landmarker when `models/face_landmarker.task` is available;
- MediaPipe Pose Landmarker when `models/pose_landmarker_lite.task` is available.

ML Kit Face Mesh is excluded from the stable runtime because its beta MediaPipe-internal dependency produced a binary `NoSuchMethodError` in the combined application runtime. The adapter remains in source for isolated compatibility work, but it is opt-in and must not be initialized by Portrait Lab.

## Dependency boundary

`feature:portrait-lab` depends only on:

- `lib:portrait-analysis`;
- `lib:portrait-analysis-mlkit`;
- `lib:portrait-analysis-mediapipe`.

Vendor SDK classes are hidden inside adapter modules. The feature module must not import ML Kit or MediaPipe runtime classes directly.

## Runtime fallback

When the MediaPipe face model is unavailable or fails to initialize, Portrait Lab falls back to ML Kit Face Detection and records the fallback in the warnings report.

A backend failure must be reported as structured diagnostic data. It must not terminate the application process.

## Device smoke test

The Market Android test APK contains `StableMlKitRuntimeSmokeTest`.

The test performs five complete cycles:

1. initialize Face Detection, Pose and Selfie Segmentation;
2. process a synthetic 512×512 bitmap;
3. close all clients;
4. reinitialize;
5. repeat.

The CI job compiles the Android test APK. Actual execution still requires an Android device or emulator.

## P1 entry gate

P1 face visualization begins only after P0 passes on the target Realme device.

P1 output must display separately:

- face contour;
- jaw and chin evidence;
- eyebrows;
- eyes;
- nose;
- upper and lower lips;
- mouth corners;
- backend name;
- face count;
- processing time;
- parameter availability and blocking reasons.

No image deformation is enabled in P0 or P1.
