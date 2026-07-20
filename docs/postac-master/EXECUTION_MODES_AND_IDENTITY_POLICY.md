# Execution modes and identity policy

## Purpose

POSTAC_MASTER does not impose one global prohibition on editing a face. It separates scene generation from explicit portrait editing.

This distinction is mandatory because background replacement, clothing replacement and scene generation must preserve the person, while the dedicated editor must allow reversible user-controlled changes to facial and body appearance.

## Mode 1: observation benchmark

`OBSERVATION_BENCHMARK`

Used during Stage A1.

Allowed:

- face, body and subject detection;
- landmark, contour, mesh and mask extraction;
- parameter-gate evaluation;
- benchmark overlays and reports.

Forbidden:

- geometry deformation;
- texture retouching;
- scene generation;
- identity calibration.

## Mode 2: identity-locked generation

`IDENTITY_LOCKED_GENERATION`

Used for:

- background replacement;
- clothing replacement;
- location or scene generation;
- lighting and composition changes;
- generation of a new image from references.

Protected invariants:

- facial identity;
- characteristic facial proportions;
- natural asymmetry;
- stable body identity and recognizable proportions;
- distinctive features defined by the master reference set.

Permitted changes:

- scene and background;
- clothing;
- framing;
- lighting and color treatment;
- pose when anatomically supported;
- expression when explicitly controlled by the style profile.

Every result must be re-observed and compared with the selected master identity before acceptance.

## Mode 3: face edit

`FACE_EDIT`

This mode is entered explicitly by the user. It may expose independent, reversible controls for:

- face shape and size;
- jaw, chin and cheek geometry;
- brows and eyes;
- nose regions;
- lips and mouth corners;
- hairline;
- skin, under-eye and local appearance operations.

Rules:

- the master identity is never overwritten;
- edits are stored as an appearance variant;
- every parameter can be reset independently;
- left and right controls may be linked or independent;
- no attractiveness score or ideal proportion recommendation is generated;
- parameter conflicts are resolved before rendering;
- history and before/after comparison are required.

## Mode 4: body edit

`BODY_EDIT`

Used for explicit, reversible user-controlled body geometry operations. It remains separate from scene generation and from facial editing.

The same evidence, background-protection and history rules apply. Missing evidence disables only the affected parameter.

## Data separation

The system should keep the following objects separate:

### `MASTER_IDENTITY`

Stable reference evidence used to recognize and preserve the same person.

### `APPEARANCE_VARIANT`

A non-destructive set of deliberate face, body, hair or grooming edits.

### `PERSONALITY_STYLE_PROFILE`

An explicit description of how the person should be presented, for example calm expression, natural gaze, restrained contrast and stable posture.

The system must not infer personality from facial structure.

### `SCENE_REQUEST`

The requested location, clothing, pose, framing, lighting, color and props for one output.

### `EXECUTION_POLICY`

The active mode and the list of allowed operation classes.

## Product benchmark use

Hypic and similar editors are useful for studying:

- parameter taxonomy;
- category organization;
- independent left/right controls;
- local retouch versus geometry separation;
- progressive disclosure from simple to advanced controls.

They are not sources of implementation code, models or proprietary assets. Their features should be translated into independently designed contracts and tested against the POSTAC_MASTER safety and identity rules.
