---
name: add-project-rule
description: Add, update, split, rename, or reorganize this repository's project rules while keeping `.agents/rules/README.agents.md` accurate. Use when the user explicitly asks to add a rule, capture a new standard, document a common mistake, update rule files, or maintain the project rules index.
---

# Add Project Rule

## Overview

Use this skill when the user explicitly asks to maintain project rules.
The goal is to keep `.agents/rules/` easy for future agents to discover, scan, and apply.

## Workflow

1. Read `.agents/rules/README.agents.md`.
2. Inspect existing `.agents/rules/*.agents.md` files before adding a new rule.
3. Decide whether the new guidance belongs in an existing numbered file or a new `NN-topic.agents.md` file.
4. Add or update the rule with short, actionable language.
5. Keep each full sentence on its own physical line.
6. Avoid duplicating an existing rule.
7. Update `.agents/rules/README.agents.md` in the same change whenever files are added, renamed, split, removed, or their purpose changes.
8. Check for em dash and en dash characters before finishing.

## File Standards

Use the `NN-topic.agents.md` pattern for every rule file.
Use two-digit ordering.
Choose filenames that describe the stable topic, not a temporary task.
Prefer updating the nearest existing rule file when the new guidance fits cleanly.
Create a new file only when the guidance introduces a distinct topic or the existing file would become hard to scan.

## Rule Writing Standards

Write rules as direct instructions.
Keep rules specific to this repository.
Prefer durable standards, recurring pitfalls, or reusable project facts.
Do not add broad advice that already belongs in global agent instructions.
Do not include implementation history, changelog notes, or one-off session details.
When replacing a rule, preserve any still-valid intent from the old wording.
