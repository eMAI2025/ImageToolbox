# CLAUDE.md

Claude Code must follow the repository-wide rules in `AGENTS.md`.

Before making changes, read:

1. `AGENTS.md`
2. `docs/ai/PROJECT_CONTEXT.md`
3. `docs/ai/PROJECT_STATE.md`
4. `docs/ai/DECISIONS.md`
5. `docs/ai/THREAD_REPORT_TEMPLATE.md`

Treat the committed project documentation as the shared cross-thread context. Treat the current Claude Code session history as local thread context only.

Do not assume another Claude, Codex, or DevSwarm workspace has seen this session. Communicate durable findings by updating the shared documentation and by leaving a completed thread report.

Do not merge into `master` automatically. Keep each task isolated to its workspace branch and verify the final diff before handoff.
