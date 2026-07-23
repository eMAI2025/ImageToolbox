# DevSwarm Setup for ImageToolbox

## Architecture

DevSwarm is installed on the development computer. It connects to this Git repository and creates isolated Git worktrees and branches for workspaces.

Do not copy DevSwarm binaries, application state, credentials, or agent conversation databases into this repository.

## Prerequisites

- Windows 11.
- Git installed and configured.
- DevSwarm installed from the official download page: `https://devswarm.ai/download/`.
- At least one supported coding assistant installed and authenticated, for example Claude Code or OpenAI Codex CLI.
- Access to `https://github.com/eMAI2025/ImageToolbox`.

## Recommended repository location

For use with the native DevSwarm editor and Android tooling, keep the working clone on the Windows filesystem, for example:

```text
C:\Dev\ImageToolbox
```

Avoid placing the primary clone only inside a WSL-native path when native Windows tools must access it.

## Connect GitHub

1. Open DevSwarm.
2. Open **Integrations** using the puzzle-piece icon.
3. Select **GitHub → Connect**.
4. Complete GitHub OAuth authorization.
5. Confirm that `eMAI2025/ImageToolbox` is visible.

Never paste a GitHub token into a tracked repository file.

## Add the repository

Use one of these methods:

### Clone through DevSwarm

1. Select **Clone from GitHub**.
2. Choose `eMAI2025/ImageToolbox`.
3. Select a Windows-local destination.
4. Use `master` as the source branch.

### Add an existing local clone

1. Select **Add Repository**.
2. Choose the local `ImageToolbox` directory.
3. Confirm that the detected default branch is `master`.

## Connect assistants

DevSwarm detects installed CLI assistants. Configure at least:

- Claude Code for implementation or architecture work;
- OpenAI Codex for implementation, testing, or review.

Authentication remains local to each assistant. Do not commit provider keys or credentials.

## Initial workspace layout

Create these workspaces from `master` as needed:

```text
00-coordinator
01-face-detection
02-landmark-overlay
03-coordinate-mapping
04-partial-face-profile
05-beard-region
06-regression-tests
07-review-audit
```

Each workspace must use its own branch. Suggested examples:

```text
feature/face-detection
fix/landmark-overlay
fix/coordinate-mapping
feature/partial-face-profile
feature/beard-region
test/visual-regression
review/pipeline-audit
```

Do not create all workspaces merely to fill the list. Create a workspace only when its task and acceptance criteria are defined.

## Agent startup prompt

Use this prompt when starting a new workspace:

```text
Read AGENTS.md, CLAUDE.md when applicable, and every file in docs/ai/ before changing code. Work only on the assigned task and branch. Reproduce the current behavior, define acceptance criteria, implement the smallest responsible change, run relevant checks, inspect the final diff, update shared project memory when required, and finish with the THREAD_REPORT_TEMPLATE.md handoff. Do not merge into master.
```

## Coordinator workspace

The coordinator must not absorb complete conversation transcripts. It consolidates only verified artifacts:

- branch and commit status;
- test results;
- visual proof references;
- decisions recorded in `DECISIONS.md`;
- updates recorded in `PROJECT_STATE.md`;
- completed workspace handoffs.

Coordinator duties:

1. Detect conflicting decisions or overlapping file ownership.
2. Reject claims that lack reproducible evidence.
3. Update shared status without deleting traceability.
4. Assign dependency-aware follow-up tasks.
5. Keep integration human-controlled.

## First verification

After adding the repository:

1. Open the primary workspace.
2. Start one assistant terminal.
3. Ask it to summarize `AGENTS.md` and `docs/ai/` without modifying files.
4. Confirm it identifies the current functional constraints correctly.
5. Create one test workspace from a dedicated branch.
6. Verify that the workspace has an isolated directory and branch.
7. Close and reopen DevSwarm and confirm the workspace restores correctly.

## Merge policy

- Review every workspace diff against its source branch.
- Prefer pull requests for durable changes.
- Require explicit acceptance criteria and verification results.
- Merge only after human approval.
- Delete or archive abandoned workspaces after preserving any verified findings in shared documentation.
