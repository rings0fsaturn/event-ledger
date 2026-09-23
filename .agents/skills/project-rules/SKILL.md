---
name: project-rules
description: Discover and apply this repository's expandable project rules from `.agents/rules/`. Use when working in this repository on issue implementation, planning, review, verification, standards compliance, debugging, UI work, library work, or any task where project-specific rules and known pitfalls may apply.
---

# Project Rules

## Overview

Use this skill to find and follow the repository rules stored in `.agents/rules/`.
The skill is a router to the rules directory, not a duplicate copy of those rules.

## Workflow

1. Read `.agents/rules/README.agents.md` first.
2. Use the index to choose the relevant `.agents/rules/*.agents.md` files for the current task.
3. Read the selected rule files before making decisions or edits.
4. Follow the selected rules together with `AGENTS.md`, `.work/spec/PRD.md`, `DESIGN.md`, and the relevant `.work/spec/issues/` file when they apply.
5. If rule guidance conflicts with the live code, inspect the code and flag the discrepancy instead of guessing.

## Selection Guidance
For adding or changing rules, use `$add-project-rule` instead of editing the rules directory ad hoc.
