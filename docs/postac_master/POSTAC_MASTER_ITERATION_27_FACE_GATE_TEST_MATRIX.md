# POSTAC_MASTER — Iteration 27 Face Gate Test Matrix

## Scope

This checkpoint validates the fail-closed face pipeline delivered in iterations 20–26. It does not enable deformation, modify final UI, merge a draft PR or publish a final APK.

## Acceptance invariant

`DETECTION != USABLE GEOMETRY`.

A detected face is active only when every required policy returns coherent evidence. Any rejection preserves raw detector evidence and removes active geometry and overlay.

## Regression matrix

| Case | Required result |
|---|---|
| frontal, finite, large, sharp, transform-consistent | accepted |
| mild half-profile LEFT with matching yaw and visible anchors | accepted |
| mild half-profile RIGHT with matching yaw and visible anchors | accepted |
| strong profile LEFT | rejected; no active oval or mesh |
| strong profile RIGHT | rejected; no active oval or mesh |
| explicit hair/hand/phone/strong-shadow occlusion | rejected |
| face bbox cropped by image frame | rejected |
| small face in a large source image | rejected with image reselection reason |
| missing or insufficient blur evidence | rejected |
| inconsistent EXIF rotation/dimensions | rejected |
| inconsistent mirror provenance | rejected |
| geometry outside image or marked `OUTSIDE_FRAME` | rejected |
| two detected faces with one selected active face | only selected face enters the gate |
| hidden-side eye/brow or closed full oval in partial pose | rejected |
| broken contour/mesh references or rejected mesh vertex | rejected |
| inconsistent full-image and ROI pass | rejected; no averaging or reconstruction |

## Threshold governance

Thresholds are versioned architectural limits covered by the regression matrix. They are not calibrated from a single photograph. Changes require either an official API contract, a structural invariant or a versioned multi-case evidence set.

## CI gate

The iteration is complete only after the integration branch passes:

1. portrait module assembly;
2. unit and integration tests;
3. Market smoke-test compilation;
4. lint;
5. dependency recording;
6. debug build and artifact preparation.

A successful build from this checkpoint is not the final user APK. The single final combined APK remains iteration 37 scope after silhouette integration and complete project CI.
