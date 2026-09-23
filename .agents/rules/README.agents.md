# Project Rules Index

This directory holds the repository's project rules for agents.
Each file is a stable topic, numbered `NN-topic.md` for ordered scanning.
Read this index first, then open only the rule files relevant to the current task.
Apply the selected rules together with `AGENTS.md`, `.work/spec/PRD.md`, `DESIGN.md`, and the relevant `.work/spec/issues/` file when they apply.
If a rule conflicts with the live code, inspect the code and flag the discrepancy instead of guessing.

## Rules

| File | Topic | Use when |
| --- | --- | --- |
| `00-learning-principle.agents.md` | Learning Principle | Always. Sets the goal: understanding over shipping, author owns decisions. |
| `01-requirement-first.agents.md` | Requirement First | Starting a phase or subphase, or when the author asks for a design. |
| `02-guidance-style.agents.md` | Guidance Style | Explaining a concept, giving implementation steps, or reviewing a proposal. |
| `03-code-ownership.agents.md` | Code Ownership | Deciding who writes application code, config, and infra artifacts. |
| `04-test-first.agents.md` | Test First | Defining a core mechanism; tests come from the spec before implementation. |
| `05-phase-gates.agents.md` | Phase Gates | Deciding what to work on next, or whether to start the next phase. |
| `06-debugging.agents.md` | Debugging | Investigating a failure, log, query plan, or race. |
| `07-explain-back.agents.md` | Explain Back | Closing out a task or phase and confirming understanding. |
| `08-concepts-and-first-encounters.agents.md` | Concepts and First Encounters | Introducing an unfamiliar tool or a core distributed-systems concept. |
| `09-work-record.agents.md` | Work Record | Reading or updating `.work/` state, status, or session notes. |
| `10-dontuse-hwclock.agents` | Linux Command  Replacement of using sudo hwclock -s. |

## Adding Or Changing Rules

Use the `$add-project-rule` skill instead of editing this directory ad hoc.
New guidance goes into the nearest existing rule file when it fits cleanly.
Create a new `NN-topic.agents.md` file only for a distinct topic.
Update this index in the same change whenever a rule file is added, renamed, split, removed, or repurposed.
