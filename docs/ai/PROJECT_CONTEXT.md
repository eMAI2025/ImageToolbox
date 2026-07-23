# Project Context

## Repository

- Repository: `eMAI2025/ImageToolbox`
- Default branch: `master`
- Upstream origin visible in the existing README: `t8rin/ImageToolbox`
- Technology visible in the repository documentation: Android, Kotlin, Jetpack Compose.

## Project purpose

This repository is being adapted as the technical base for an image-editing workflow with controlled, verifiable face and body adjustments. The implementation must preserve image identity and avoid presenting unverified AI output as a successful result.

## Operating model

The project may be developed through multiple independent AI conversation threads and DevSwarm workspaces. Each thread keeps its own conversational history. Cross-thread continuity is maintained through committed code, tests, and the files in `docs/ai/`.

The repository documentation is the durable project memory. Conversation history is supporting context, not the sole source of truth.

## Roles

Typical workspace roles:

- `coordinator`: consolidates verified status, decisions, conflicts, and dependencies;
- `implementer`: changes one bounded feature;
- `test`: builds reproducible tests and visual proofs;
- `review`: audits the diff, architecture, regressions, and licenses;
- `experiment`: evaluates an isolated technical option without modifying the accepted implementation path.

A role is a responsibility, not a requirement to use a specific model. Claude Code, Codex, or another supported assistant may fill any role.

## Shared-context mechanism

Each workspace must:

1. Read `AGENTS.md` and all files in `docs/ai/` before substantive work.
2. Work on one isolated branch.
3. Record durable decisions in `DECISIONS.md`.
4. Update `PROJECT_STATE.md` only with verified information.
5. Produce a structured handoff at task completion.

The coordinator may merge summaries, but must preserve traceability to the branch, commit, test, or visual proof that supports each claim.

## Quality principles

- Evidence over assertion.
- Minimal, reviewable diffs.
- Reproducible tests over one-off demonstrations.
- Explicit uncertainty over guessing.
- Functional validation before aesthetic refinement.
- Human approval before merging to `master`.

## Security and repository hygiene

Never commit:

- API keys or access tokens;
- `.env` files containing secrets;
- private photographs or personal datasets;
- local agent histories;
- model caches and generated binaries;
- local DevSwarm application data;
- credentials for GitHub, OpenAI, Anthropic, or other providers.

DevSwarm is installed locally and connected to this repository. Its application files do not belong in this repository.
