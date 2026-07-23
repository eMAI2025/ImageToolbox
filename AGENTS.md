# AGENTS.md

## Purpose

This file defines the repository-wide operating rules for AI coding assistants, including OpenAI Codex, Claude Code, and assistants launched through DevSwarm.

## Mandatory startup sequence

Before changing code, read these files in order:

1. `docs/ai/PROJECT_CONTEXT.md`
2. `docs/ai/PROJECT_STATE.md`
3. `docs/ai/DECISIONS.md`
4. `docs/ai/THREAD_REPORT_TEMPLATE.md`

Then inspect the relevant source files, tests, build configuration, and the current Git diff.

## Source of truth

Use this priority order when information conflicts:

1. Current user instruction.
2. Verified behavior reproduced in the current branch.
3. Automated tests and build results.
4. `docs/ai/DECISIONS.md`.
5. `docs/ai/PROJECT_STATE.md`.
6. Older conversation summaries or assumptions.

Do not infer that a feature works from logs, counters, or non-visual status messages alone. Where the requirement is visual, provide a reproducible visual proof.

## Workspace rules

- One workspace and branch must cover one clearly defined task.
- Do not modify unrelated modules or reformat unrelated files.
- Do not overwrite user changes.
- Do not merge into `master` automatically.
- Do not force-push shared branches.
- Do not commit generated binaries, credentials, API keys, local databases, model files, or private user data.
- Do not add a dependency until its license, maintenance status, and necessity have been checked.
- Prefer adapting an existing implementation over replacing a working subsystem without evidence.

Recommended branch prefixes:

- `feature/`
- `fix/`
- `test/`
- `docs/`
- `refactor/`
- `experiment/`

## Execution standard

For each task:

1. State the exact target and acceptance criteria.
2. Reproduce the current behavior before editing when possible.
3. Identify the smallest responsible code area.
4. Implement the change.
5. Run the narrowest relevant tests first, then broader checks.
6. Inspect the final diff for unrelated changes.
7. Update `docs/ai/PROJECT_STATE.md` when project status changed.
8. Add an entry to `docs/ai/DECISIONS.md` when a durable technical decision was made.
9. Produce a handoff using `docs/ai/THREAD_REPORT_TEMPLATE.md`.

## Current project constraints

- NEXT AI is excluded from the project and must not be reintroduced or analyzed further.
- Face-recognition validation must show where points, contours, or masks were detected on the actual image. Counts alone are not proof.
- Profile and partial-face images must not be forced into a full frontal-face fitting model.
- Left-side and right-side adjustments must remain independently controllable where the feature supports asymmetric editing.
- Beard-region correction is an open functional requirement.
- Functional correctness and technical validation take priority over final interface styling. UI aesthetics are deferred until the functional pipeline is reliable.

## Communication between workspaces

Do not copy full conversation histories between workspaces. Transfer only verified, task-relevant information through:

- committed code and tests;
- `docs/ai/PROJECT_STATE.md`;
- `docs/ai/DECISIONS.md`;
- a completed thread report.

A coordinator workspace may consolidate these artifacts, detect conflicts, and assign follow-up work. It must not silently change established decisions.

## Definition of done

A task is complete only when:

- acceptance criteria are met;
- relevant tests or reproducible checks pass;
- visual requirements include visual evidence;
- the diff contains no accidental changes;
- status and durable decisions are documented;
- remaining risks and blockers are explicit.
