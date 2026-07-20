# A1 benchmark ground-truth schema

## Objective

A detector comparison is not meaningful when it only reports that two backends produced different points. Each benchmark image needs a manually reviewed expected state that defines which evidence should be present and which edit parameters should be enabled or disabled.

Ground truth remains backend-independent. It uses canonical landmark, region and parameter identifiers rather than ML Kit or MediaPipe point numbers.

## Runtime contract

The pure Kotlin contract and evaluator are located in:

`lib/portrait-analysis/.../benchmark/PortraitBenchmarkGroundTruth.kt`

The evaluator compares:

- subject, face and body counts;
- required and forbidden canonical landmarks;
- required and forbidden semantic regions;
- expected ENABLED/DISABLED parameter decisions.

A mismatch is reported explicitly and sorted deterministically.

## Example case

```yaml
case_id: FACE_03_THREE_QUARTER_RIGHT

expected_counts:
  subjects: 1..1
  faces: 1..1
  bodies: 0..1

required_landmarks:
  - mouth_left_corner
  - mouth_right_corner
  - left_eye_center
  - right_eye_center

required_regions:
  - mouth_region
  - left_eye_region
  - right_eye_region

expected_enabled_parameters:
  - mouth_corner_curve
  - eye_brightness

expected_disabled_parameters:
  - teeth_whiteness
  - waist_width
  - shoulder_width

manual_annotations:
  - STRAIGHT_BACKGROUND_LINES
```

## Annotation policy

Annotations describe visible facts about the source image. They do not infer hidden anatomy or personality.

Current annotations:

- `STRAIGHT_BACKGROUND_LINES`;
- `PARTIAL_FACE_OCCLUSION`;
- `LOW_CONTRAST`;
- `VISIBLE_TEETH`;
- `LOOSE_CLOTHING`;
- `ARMS_CROSSING_TORSO`.

## Authoring rules

1. Review the original image at full resolution.
2. Record only visible and verifiable evidence.
3. Do not require a region that is hidden by pose, crop or occlusion.
4. Mark an edit parameter disabled when the visible evidence is insufficient for safe execution.
5. Keep detector-specific indices outside ground truth.
6. Version every change to expected results.
7. Preserve the original image checksum next to the case manifest.

## A1 acceptance use

A backend is not selected from average landmark count alone. It must be evaluated against ground truth for:

- false availability: an unsafe parameter is enabled;
- false unavailability: a supported parameter is disabled;
- count errors;
- missing canonical evidence;
- unstable decisions across repeated runs;
- processing-time and package-size cost.

Stage B remains blocked until the benchmark set has reviewed ground truth and the selected adapter combination passes the agreed thresholds.
