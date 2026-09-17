---
name: work-journal
description: "Component skill of work-journal-orchestrator – normally driven by the orchestrator, not invoked on its own. It owns the top of the .work/ memory system: the task-centric directory layout, opening a new task, the STATUS.md project index (schema + cleanup), and the wrap that finishes a task (promote reusable research, move the task to archive, flip its STATUS row). It does not write state.md or SCRATCHPAD.md – those belong to the scratchpad skill; it only reads state.md to keep STATUS honest. If you reached this skill directly, prefer running work-journal-orchestrator."
---

# Work Journal – layout, index, and task lifecycle

This skill owns the durable top of the `.work/` system.
The **scratchpad** skill writes `state.md` and `SCRATCHPAD.md`; you never write those.
You read `state.md` to keep `STATUS.md` honest, and you own everything at the project and task-boundary level.

`.work/` lives at the project root, a sibling of both repos, **outside both git worktrees on purpose** so git (`merge`, `git clean -fdx`) cannot delete it.
The cost of that safety is that cleanup is manual – this skill is that habit made repeatable.

## Step 0 – Orient

Read `.work/README.md` (the canonical folder map) and `.work/STATUS.md` (the index) before writing anything.
You are updating a living record, so find the row that already covers the work and update it rather than making a duplicate.

## The task-centric layout

Everything about one task lives in one folder, so nobody hunts across directories.

```
.work/
  STATUS.md               # the project index – read first (this skill owns it)
  README.md               # the folder map
  spec/                   # CORE – stable contracts, top-level (built against; outlive a task)
    <task-id>-<slug>.md
  active/
    <task-id>/             # everything about the live task, together
      state.md            #   durable task doc      (scratchpad skill writes)
      SCRATCHPAD.md        #   session ledger        (scratchpad skill writes)
      plan/                #   the how
      prompts/             #   this task's prompts
      research/            #   this task's research
  archive/
    <task-id>/             # the finished task folder, moved intact (minus the emptied scratchpad)
```

The core top-level directories are exactly three: `active/`, `archive/`, `spec/`.
`spec/` stays top-level because a spec is the stable contract – it is built against, sometimes shared across tasks, and outlives any one task folder.
Everything else about a task (plan, prompts, research) lives inside the task folder.

### Invariants

- **One stable task-id everywhere.** Pick it once and reuse it verbatim across `spec/`, `active/`, and `archive/` so the task is greppable end to end. Match the ids already in the tree (`issue-NN` for UI, kebab names like `branch-b` for backend).
- **Never delete; archive instead.** Moved working state is the safety net given this project's history of losing work. The only sanctioned deletion is the emptied `SCRATCHPAD.md` at wrap, and that is safe because it is already empty.
- **Code is ground truth.** If a doc and the code disagree, fix the doc.
- **Ceremony dial.** Trivial change = one STATUS line, no folder. Normal task = spec + task folder. Complex task = full plan + session loop.

## TASK OPEN

When new work starts and has no folder yet:
1. Create `active/<task-id>/` and its `plan/`, `prompts/`, `research/` subfolders as needed.
2. Make sure the task's contract lives at `spec/<task-id>-<slug>.md` (create it or point to the existing one).
3. Add a STATUS row (see below), usually under Active.

Do **not** create `state.md` here – the scratchpad skill bootstraps it on the first session start.
Your job at open is the folder, the spec pointer, and the STATUS row.

## STATUS.md – the project index

`STATUS.md` is the read-first file – the local Jira.
It holds one row per task: a single line plus a pointer down to the detail.
An out-of-date index is one nobody trusts, so keep it honest in the same session as the work.

### Structure

```markdown
# <Project> – STATUS
_Last reconciled: YYYY-MM-DD_

Read first. One line per task – follow Detail for everything else. No stale line survives an edit.

## Active
| Tag | Task | Where it stands (one line) | Detail |
|---|---|---|---|

## Queued
| Tag | Task | Note (one line) | Detail |
|---|---|---|---|

## Done
| Tag | Task | Durable record |
|---|---|---|

## Reference
- <durable pointers – a link list, not prose>

## Gotchas (active only)
- <project-wide traps still live; deleted the moment fixed>
```

### Field guide and rules

| Column | What goes here | Format |
|---|---|---|
| Tag | which repo/area | `[UI]`, `[BE]`, or `[UI][BE]` |
| Task | the stable task-id | matches `active/`, `spec/`, `archive/` |
| Where it stands / Note | one-line status | one line only; no paragraphs |
| Detail | the pointer to the record | `active/<task-id>/state.md` (active), `spec/` or `plan/` (queued), `archive/<task-id>/state.md` (done) |

Rules:
- One row per task, keyed by the stable task-id.
- One line per row. All detail lives behind the Detail pointer – this is what keeps STATUS from bloating.
- Move a row between sections on state change; do not copy it.
- Gotchas are active-only; delete each the moment it is fixed.

### Filled sample

```markdown
# HostedUA Migration – STATUS
_Last reconciled: 2026-08-25_

Read first. One line per task – follow Detail for everything else. No stale line survives an edit.

## Active
| Tag | Task | Where it stands (one line) | Detail |
|---|---|---|---|
| [UI][BE] | pre-integration-tests | Phase 4 in progress; app-caller context live across all 8 Nodeweb calls | active/pre-integration-tests/state.md |
| [UI] | issue-12 | Phase 3 done; v2 mappers landed, v1 mirror pending | active/issue-12-edit-business-profile/state.md |

## Queued
| Tag | Task | Note (one line) | Detail |
|---|---|---|---|
| [UI] | mb-styling-rebalance | verify current code before implementation | spec/mb-styling-rebalance-*.md |

## Done
| Tag | Task | Durable record |
|---|---|---|
| [BE] | branch-b | archive/backend/branch-b/state.md |

## Reference
- Contract source: spec/mom-contract/.
- Cross-task rules: .agents/rules/.

## Gotchas (active only)
- .work is outside both git worktrees; archive cleanup and status updates are manual.
```

### Cleanup – fix or remove, never tag-and-leave

Stale data is what makes an index untrustworthy, so it is not allowed to accumulate.
The rule is the same litmus the state doc uses:

> Would this line mislead a fresh agent? Then fix it to the truth or cut it – in the same edit.

Never annotate a line as `STALE:` and leave it sitting.
That habit is exactly how this file rotted before.

Cleanup runs at two levels:
- **Incremental tidy (every write):** whenever you touch a row, fix any stale line you pass. Move rows on state change – Queued to Active when work starts, Active to Done when it finishes.
- **Full reconciliation sweep (on demand, or when the file grows stale):** verify each Active row against its `state.md` and the code (code is ground truth); migrate finished and started rows; prune Done rows to one line plus a pointer; delete gotchas that are fixed; bump `Last reconciled`.

Cleanup is safe because nothing durable lives only in `STATUS.md` – the detail is in `state.md` or `archive/`.
You are trimming a summary, not destroying a record.

### Session-end STATUS refresh

After the scratchpad skill has updated `state.md` at session end, refresh the task's one-line STATUS note from it.
You read `state.md`'s Current state & next and compress it to one line; the detail stays behind the pointer.

## TASK WRAP

Finishing a task is an ordered sequence.
Do all of it, in order – stopping halfway leaves the index lying.
1. **Confirm state.md is final.** It should read status done with the completion recorded. If the scratchpad still holds content, run the session-end distill first (that is the scratchpad skill).
2. **Promote reusable research.** Move any genuinely cross-task knowledge from the task's `research/` into `.agents/rules/` (the cross-agent rule set), so it survives beyond this task. Task-only research stays in the folder and rides into archive.
3. **Remove the emptied `SCRATCHPAD.md`.** Once `state.md` is the durable capture, the reset scratchpad has no value; do not archive noise.
4. **Move** `active/<task-id>/` into `archive/<task-id>/` intact (`mv`, never delete). Mirror the existing archive shape (UI under `archive/ui/`, backend under `archive/backend/`).
5. **Flip the STATUS row** to Done: a one-liner plus a pointer to `archive/<task-id>/state.md`. Delete any gotcha this task closed, and repoint any link that targeted the old `active/` path.

## What NOT to do

- Do not write `state.md` or `SCRATCHPAD.md`. Read `state.md`; the scratchpad skill writes both.
- Do not leave a `STALE:` tag on anything. Fix the truth or cut the line in the same edit.
- Do not bloat a STATUS row with detail that belongs behind the pointer.
- Do not delete working state; archive it. The one exception is the emptied scratchpad at wrap.
- Do not fork a second spec for one task. Edit the existing one.
