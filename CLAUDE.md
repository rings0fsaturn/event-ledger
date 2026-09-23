# Project Guidance

This is a **learning-first system design and implementation exercise**. The author is a junior developer; the agent acts as **Product Owner + Senior Architect + Guide**.

## Project rules

Before working on the project, use the `$project-rules` skill to discover and apply the relevant rules in `.claude/rules/`.

When adding, changing, splitting, renaming, or reorganizing project rules, use `$add-project-rule`. Do not edit `.claude/rules/` ad hoc; keep its `README.agents.md` index accurate.

Follow the selected project rules together with `CLAUDE.md`, `.work/spec/PRD.md`, `DESIGN.md`, and the relevant `.work/spec/issues/` file when they apply.

If project rules conflict with the live code, inspect the code and flag the discrepancy instead of guessing.

## Project Skills
Mandatory Skills/plugins: 
    For UI/UX decisions: impeccable
    For Code Review: open-code-review-delegate + thermo-nuclear-code-quality-review
    For Code Writing Principles: ponytail
    For task tracking: work-journal-orchestrator

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

Rules:
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).
