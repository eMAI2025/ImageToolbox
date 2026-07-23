# Project State

Last updated: 2026-07-23

## Current phase

Functional validation and technical stabilization of the image-analysis and controlled-editing workflow. Final interface aesthetics and broader UX refinement are deferred.

## Confirmed project rules

- NEXT AI is closed as an option and must not be analyzed or reintroduced.
- A recognition test is not accepted merely because a backend returns point or contour counts.
- Detection results must be visible on the processed image so their position and correctness can be inspected.
- Profile photographs and partially visible faces must be supported without forcing a full-face fit.
- Independent left-side and right-side control is required where asymmetric correction is available.
- Beard-region correction remains part of the target functionality.

## Current technical concerns

- Verify coordinate mapping from detector output to the displayed image.
- Produce an inspectable landmark, contour, mask, or equivalent visual overlay.
- Determine why previous output could collapse into a small point near the image center.
- Validate partial-face and profile-image behavior.
- Establish explicit failure states when reliable face geometry is unavailable.
- Add reproducible test assets and reports without committing private source photographs.

## Required evidence

For every recognition or geometry change, record:

- input category, such as frontal, profile, or partial face;
- detector/backend used;
- point, contour, mask, or mesh output;
- transformation from source coordinates to display coordinates;
- visual overlay or generated proof image;
- pass/fail result against defined acceptance criteria.

## Open workstreams

1. Face detection and partial-face handling.
2. Landmark and contour visualization.
3. Coordinate transformation and image orientation.
4. Independent left/right adjustment logic.
5. Beard-region handling.
6. Automated regression tests and visual proof reports.
7. UI and aesthetic refinement after the functional pipeline is accepted.

## Blockers

No blocker is recorded in this document yet. Each workspace must add a blocker only after reproducing and documenting it.

## Next coordination action

Create isolated DevSwarm workspaces for the open workstreams. Each workspace must return a structured report and must not merge directly into `master`.
