# Technical Decisions

This log records durable project decisions. Do not delete superseded entries; mark them as superseded and reference the replacement decision.

## D-001 — Shared project memory is repository-based

- Date: 2026-07-23
- Status: accepted
- Decision: Cross-thread and cross-agent continuity is maintained through committed documentation, code, tests, and structured handoffs. Full conversation histories are not merged into one global thread.
- Reason: Each agent session has independent history and context limits. Repository artifacts are inspectable, versioned, model-independent, and reviewable.
- Consequence: Every workspace must read and update the relevant files in `docs/ai/`.

## D-002 — DevSwarm remains a local orchestration tool

- Date: 2026-07-23
- Status: accepted
- Decision: DevSwarm application files, credentials, and local state are not copied into this repository.
- Reason: DevSwarm is installed locally and connects to an existing Git repository. The repository stores only project-side instructions and shared context.
- Consequence: Local installation and connection steps are documented in `docs/ai/DEVSWARM_SETUP.md`.

## D-003 — Human-controlled branch integration

- Date: 2026-07-23
- Status: accepted
- Decision: AI workspaces operate on isolated branches. No agent may merge directly into `master` without explicit human approval.
- Reason: Parallel work can introduce conflicts, regressions, and incompatible assumptions.
- Consequence: Changes are reviewed through diffs or pull requests before merge.

## D-004 — Visual evidence is mandatory for visual recognition claims

- Date: 2026-07-23
- Status: accepted
- Decision: Backend counters or textual success messages are insufficient proof of correct face recognition or geometry placement.
- Reason: A detector may return data that is incorrectly transformed, misplaced, collapsed, or unrelated to the intended face.
- Consequence: Relevant tasks must provide an overlay, mask, mesh, contour, or equivalent inspectable output tied to acceptance criteria.

## D-005 — NEXT AI is excluded

- Date: 2026-07-23
- Status: accepted
- Decision: NEXT AI is not part of the implementation path and must not be analyzed or proposed again.
- Reason: The option was reviewed and explicitly closed by the project owner.
- Consequence: Agents must focus on the remaining approved backends and architecture.

## D-006 — Functional pipeline precedes final UI refinement

- Date: 2026-07-23
- Status: accepted
- Decision: Detection, geometry, editing behavior, tests, and proof outputs are stabilized before final interface aesthetics and usability polishing.
- Reason: UI refinement cannot compensate for an unreliable processing pipeline.
- Consequence: UI changes are limited to what is necessary for testing and functional operation until the pipeline is accepted.
