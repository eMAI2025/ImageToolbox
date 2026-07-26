# POSTAC_MASTER — Iteration 18 device-evidence consolidation

**Date:** 2026-07-25  
**Scope:** device packages and screenshots supplied on 2026-07-24 and 2026-07-25  
**Policy:** `DETECTION != USABLE_GEOMETRY`; raw evidence is retained, active geometry is fail-closed.

## 1. Evidence inventory

### Face evidence

1. `portrait_visual_proof_20260724_205300.zip`
2. `portrait_visual_proof_20260724_205314.zip`

The two archives are byte-identical:

```text
sha256: 728ad73c0888fa0f7a16be8ec5f2964b8873c6954f2656d15fa8780e4a93fbd6
```

Deduplicated result: **1 unique portrait package**.

Additional evidence supplied on 2026-07-25:

- one screenshot from `Portrait Lab / P1-VISUAL-PROOF`, backend `ML_KIT_FACE_DETECTION`, status `PARTIAL`;
- no matching ZIP, build commit or integration fingerprint was supplied with that screenshot.

The screenshot therefore confirms the visual failure class but does not prove the exact tested build.

### Silhouette evidence — 2026-07-24

- 14 supplied ZIP packages;
- 6 visually and byte-wise unique runs after duplicate elimination;
- all unique runs used the previous local-section lineage rather than the current torso-validation integration build.

### Silhouette evidence — 2026-07-25

Nine supplied ZIP packages reduce to five unique runs:

| Unique run | Byte-identical duplicate |
|---|---|
| `025501` | — |
| `025542` | `025552` |
| `025619` | `025631` |
| `025713` | `025719` |
| `025802` | `025812` |

Deduplicated result: **5 unique silhouette runs**.

### Consolidated count

| Evidence class | Supplied items | Unique evidence |
|---|---:|---:|
| Portrait ZIP, 2026-07-24 | 2 | 1 |
| Portrait screenshot, 2026-07-25 | 1 | 1 visual-only case |
| Silhouette ZIP, 2026-07-24 | 14 | 6 |
| Silhouette ZIP, 2026-07-25 | 9 | 5 |
| **Total** | **26** | **13 unique evidence items** |

The total includes one screenshot without a diagnostic ZIP. The 12 archive-based unique items consist of one portrait package and eleven silhouette packages.

## 2. Build provenance

### Portrait package from 2026-07-24

Runtime evidence contained:

```text
p2_pose_mode=PROFILE
p2_dominant_image_side=LEFT
p2_full_face_geometry_allowed=false
```

but did not contain the expected `p2_visible_filter_*` counters from PR #7. The active export still contained the raw 18-contour proposal. This is consistent with the silhouette APK replacing the face APK because both builds shared the same application ID.

Classification:

```text
WRONG_BUILD_PROVENANCE
PR7_ACCEPTANCE_TEST_INVALID
```

### Portrait screenshot from 2026-07-25

The screen shows the same strong-profile failure class, but has no diagnostic ZIP, branch, commit or gate fingerprint.

Classification:

```text
BUILD_PROVENANCE_UNKNOWN
VISUAL_FAILURE_CONFIRMED
```

### Silhouette packages from 2026-07-24 and 2026-07-25

The supplied reports use the previous diagnostic shape:

```text
mode=B1_SILHOUETTE_OBSERVATION
```

and omit:

```text
diagnostic_schema=POSTAC_MASTER_DIAGNOSTICS_V1/V2
build_branch
build_source_commit
torso_section_raw_count
torso_section_filtered_count
torso rejection diagnostics
```

They therefore test the earlier run-296/local-section lineage, not current PR #9 or PR #10.

Classification:

```text
OUTDATED_BUILD_RUN296
CURRENT_PR9_DEVICE_ACCEPTANCE_NOT_TESTED
CURRENT_PR10_DEVICE_ACCEPTANCE_NOT_TESTED
```

## 3. Face comparison and failure classification

Both the unique 2026-07-24 portrait package and the 2026-07-25 screenshot show a strong side profile with active geometry that is not supported by visible evidence.

Observed failure:

- closed full-face oval in a profile view;
- hidden-side eye and eyebrow geometry;
- contour segments crossing non-visible face space;
- `PARTIAL` used as a detector-completeness label despite geometry being unsafe for rendering/editing.

Required fail-closed result:

```text
FACE_DETECTED_GEOMETRY_REJECTED
NO_USABLE_FACE_GEOMETRY
active overlay = empty
raw detector payload = diagnostic only
```

## 4. Five unique new silhouette runs — consolidated findings

All five unique 2026-07-25 runs report `PARTIAL` and were produced by the outdated run-296 lineage.

Cross-run findings:

- giant non-local arm and leg cross-sections are mostly suppressed;
- torso regions remain nominally available because the new torso gate is absent;
- landmarks can be serialized as `OUTSIDE_FRAME` while the pose still contains a nominal 33-landmark set;
- missing or low-visibility limbs are represented through broad aggregate blocking rather than independent arm/forearm/hand capabilities;
- at least one source includes more than one person while the current contract selects one subject/body;
- raw Selfie Segmentation masks may contain detached halo/smear components not anchored to a verified torso or pose;
- no package proves current PR #9 torso validation or PR #10 integrated face/body gates.

## 5. Failure taxonomy

### `WRONG_BUILD`

**Definition:** The installed APK does not contain the feature being evaluated, or its provenance cannot be proven.

**Evidence:**

- portrait package lacks `p2_visible_filter_*` despite testing PR #7 behavior;
- all silhouette packages lack current branch/commit/schema fingerprint;
- shared application ID allowed one debug APK to replace another.

**Required response:** Reject the acceptance result. Do not interpret visual failure as proof about an uninstalled branch.

### `UNSUPPORTED_PROFILE`

**Definition:** Backend detects a face bbox but does not provide sufficient visible geometry for a strong profile.

**Evidence:** Strong profile marked `PARTIAL` with full-face proposal.

**Required response:** `FACE_DETECTED_GEOMETRY_REJECTED`; no active contours or mesh.

### `HIDDEN_SIDE_HALLUCINATION`

**Definition:** Active geometry contains eye, eyebrow, oval or mesh for the non-visible side.

**Evidence:** Closed oval and hidden eye/brow in both face evidence cases.

**Required response:** Empty active overlay; raw proposal retained separately.

### `OFF_FRAME_OR_LOW_VISIBILITY_LANDMARK`

**Definition:** A body landmark is missing, outside the source image or lacks sufficient in-frame/visibility evidence.

**Evidence:** New silhouette reports serialize `OUTSIDE_FRAME` landmarks while aggregate pose remains present.

**Required response:** Block only the dependent segment/region; do not draw artificial endpoints.

### `MASK_HALO_OR_DISCONNECTED_COMPONENT`

**Definition:** Segmentation foreground contains a detached or weakly connected component not anchored to the verified person.

**Evidence:** Visual mask smears/halo above or beside the person in supplied silhouette evidence.

**Required response:** Preserve raw mask; remove the component from accepted mask unless pose/torso anchoring proves membership.

### `MISSING_HAND_OR_LIMB`

**Definition:** Shoulder/elbow/wrist/hand evidence is incomplete or cropped.

**Evidence:** Several packages lack reliable full-limb evidence while the previous pipeline only blocks aggregate arm/leg capabilities.

**Required response:** Independent upper-arm, forearm and hand capabilities; no synthetic hand, circle or endpoint.

### `TORSO_CONTAMINATION`

**Definition:** Shoulder/waist/hip cross-section includes crossed arm, hand, phone, second person or unrelated foreground.

**Evidence:** 2026-07-24 unique runs show waist/upper-torso sections crossing forearms and phone; current packages do not contain the PR #9 torso gate.

**Required response:** `TORSO_OCCLUDED_BY_LIMB`, `TORSO_WIDTH_OUTLIER` or `SECTION_NON_LOCAL`; block the affected torso region.

### `MULTI_PERSON_CONTAMINATION`

**Definition:** Pose or mask evidence from another person enters the selected subject geometry.

**Evidence:** At least one new silhouette source contains two people while the old contract reports one subject/body.

**Required response:** Require one explicitly selected subject and component; conflicting evidence blocks affected geometry.

## 6. Serialization audit

No critical serialization corruption was proven.

The observed problems are attributable to:

- outdated or unknown build provenance;
- unsafe interpretation of detector output;
- missing per-region visibility/component gates;
- insufficient separation of raw, filtered and accepted geometry in old reports.

Therefore iteration 18 introduces **no algorithmic production changes**.

## 7. Acceptance status

```text
FACE_VISUAL_EVIDENCE: FAIL
FACE_CURRENT_INTEGRATION_BUILD: NOT_DEVICE_TESTED
RUN296_LIMB_GIANT_LINE_FIX: PARTIAL_PASS
CURRENT_TORSO_GATE: NOT_DEVICE_TESTED
CURRENT_INTEGRATED_BODY_GATE: NOT_DEVICE_TESTED
DEVICE_ACCEPTANCE: BLOCKED
MERGE: BLOCKED
```

## 8. Next step

Iteration 19 must record the architecture decision from official primary sources only:

- ML Kit Face Detection capabilities and yaw-dependent landmark availability;
- ML Kit Pose partial-body and in-frame evidence;
- Selfie Segmentation mask semantics;
- MediaPipe Face Landmarker options/result contract;
- Google Face Mesh / Attention Mesh publications.

The resulting decision must keep ML Kit as bbox/ROI plus restricted geometry, MediaPipe as a dense candidate after verified model provenance, and both behind the same fail-closed acceptance gate.
