# POSTAC_MASTER integration

Status: **Stage A1 — detector and parameter-gate benchmark**

This directory documents the integration of the POSTAC_MASTER portrait/body editing architecture into ImageToolbox.

## Current scope

The first implementation stage is observation-only:

- normalize detector outputs from multiple backends;
- represent landmarks, semantic regions, visibility, occlusion and pose;
- evaluate each edit parameter independently;
- disable a parameter when required evidence is missing;
- collect deterministic benchmark measurements.

## Explicitly out of scope in A1

- face or body deformation;
- personal face identification;
- personal correction-profile calibration;
- clothing replacement;
- background generation;
- importing Xiaomi or FaceUnity binaries, models or proprietary resources.

## Sequence gate

1. **A1:** detector, mask and parameter-gate benchmark;
2. **B:** controlled geometry tests after explicit user acceptance of A1;
3. **C:** personal identity/reference calibration after explicit user acceptance of B.

## Initial code

The pure contract and gate logic are located in:

`lib/portrait-analysis`

Detector-specific adapters will be added after the contract and unit tests are accepted.
