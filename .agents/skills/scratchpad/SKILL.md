---
name: scratchpad
description: "Component skill of work-journal-orchestrator – normally driven by the orchestrator, not invoked on its own. It owns the two lower files of the .work/ memory system: SCRATCHPAD.md (the per-session working ledger that survives context compaction) and state.md (the durable, curated per-task context a new session reads to resume). It seeds the scratchpad at session start, appends to it during work, and at session end distills it into state.md and resets it. If you reached this skill directly, prefer running work-journal-orchestrator so the whole session cycle is handled."
---

# Scratchpad – session ledger and task state

This skill owns the two lower files of the `.work/` memory system.
It does more than the file it is named after: it also writes `state.md`.
That is deliberate.
The session-end distill reads the scratchpad and writes the state in one move, so keeping both in one skill is what stops them clashing.

| File | Altitude | Lifespan | Discipline |
|---|---|---|---|
| `SCRATCHPAD.md` | one session | ephemeral, reset each session | small overwritten header + append-only log |
| `state.md` | one task | durable, read to resume across sessions | curated: keep true history, strip stale |

`STATUS.md` and the `.work/` layout belong to the **work-journal** skill, not here.
You are the only writer of these two files, and that single-writer rule is what keeps the record clean.

## Step 0 – Orient

Resolve the layout from the project; do not hardcode it.
1. Read `.work/README.md` for the active-task folder location.
2. Read `.work/STATUS.md` to find the task row and its pointer.
Both `SCRATCHPAD.md` and `state.md` live inside the active-task folder.
If no active task resolves, stop and ask – do not drop files in an arbitrary place.

---

## SCRATCHPAD.md – the session ledger

Its one job is to defeat context compaction.
Your durable files are safe on disk; what compaction destroys is the in-flight reasoning that only lived in the context window.
The scratchpad externalizes exactly that, so it can be reloaded.

### Structure

Two parts only. Keep it to these.

```markdown
# Scratchpad – <task-id> · session <YYYY-MM-DD>
_state.md: <path> · Updated: <YYYY-MM-DDThh:mm>_

## Now / Next
- Doing: <the one thing in progress right now, with plan+phase>
- Next: <the next concrete action>
- Blocked: <active blockers only, or "none">

## Session log
- <hh:mm> FOUND  <discovery / flow-trace note / pitfall>  (file:line)
- <hh:mm> DECIDED <decision + why>
- <hh:mm> EDIT   <file changed, what changed, + verified | UNVERIFIED>
- <hh:mm> BLOCKED <what · on what>
- <hh:mm> UNBLOCKED <what · how>
- <hh:mm> DONE   <a piece of work completed>
- <hh:mm> NEXT   <the next action, when it changes>
```

- **Now / Next** is overwritten in place and kept to a few lines. It is the first thing a compacted agent reads, so it must always describe the present.
- **Session log** is append-only. Never edit past lines; just add new ones. The log is the raw trail you distill into `state.md` at session end.

### The six tags – what each captures and where it lands

The tags exist so the session-end distill is near-mechanical.
Each tag has a home section in `state.md`, so promoting is copy-then-compress, not re-think.

| Tag | Use it when | Format | Lands in state.md section |
|---|---|---|---|
| `FOUND` | you learn how something works, or hit a pitfall | terse fact + `file:line` | Flow trace / Pitfalls & rules |
| `DECIDED` | you make a choice that will govern later work | `<decision> because <why>` | Decisions in force / Pitfalls & rules |
| `EDIT` | you change a file | `<path> – <what> – verified\|UNVERIFIED` | Files affected |
| `BLOCKED` / `UNBLOCKED` | you get stuck, or get unstuck | `<what> · <on what>` / `<what> · <how>` | Open / Done so far |
| `DONE` | you complete a piece of work | past-tense, cite commit/file | Done so far |
| `NEXT` | the next action changes | imperative | Current state & next |

### When to write

Append a log line right after each event, and rewrite the header whenever Doing / Next / Blocked changes:
a discovery, a decision, a file edit, a blocker hit or cleared, a step done.
The invariant: the scratchpad is never more than one meaningful event stale.
Then whenever compaction lands, the latest version is already a faithful snapshot.
Bump `Updated:` on every rewrite.

### Filled sample

```markdown
# Scratchpad – issue-12-edit-business-profile · session 2026-08-25
_state.md: active/issue-12-edit-business-profile/state.md · Updated: 2026-08-25T14:32_

## Now / Next
- Doing: Phase 3 – wiring the business-name field into the edit view
- Next: add the fieldMapper entry in v2, then mirror it into v1
- Blocked: none

## Session log
- 13:05 FOUND  edit view renders via staticViewMapper only (HostedUAView.js:41)
- 13:20 DECIDED namespace the field static/editBusinessName to avoid the auth-page collision because the fieldMapper is shared
- 13:40 EDIT   server/src/config/mappers/v2/fieldMapper/default.json – added static/editBusinessName – UNVERIFIED
- 14:10 DONE   v2 fieldMapper + elementMapper entries added, unit test green
- 14:30 NEXT   mirror both entries into v1 (runtime uses v1)
```

### Reading it

- **Session start:** read `SCRATCHPAD.md` in full before touching the work. This loads your context for the session.
- **Mid-session self-rescue:** if you cannot account for the current plan position, the open blockers, and the recent decisions from your own memory, re-read it first. That thin-context feeling is what compaction feels like, so this fires exactly when it is needed.

---

## state.md – the durable task doc

This is what a fresh session reads to understand what the task is and where it stands.
It keeps true task history – flow trace, files touched, pitfalls, rules – plus the current state, curated so no stale line can mislead the next agent.
It is not an append-forever diary and not a wipe-clean snapshot; it is maintained.

### Structure – seven sections

The sections map one-to-one onto the scratchpad tags, so the distill is a promote-and-prune.

```markdown
# State – <task-id>
_Spec: <path> · Plan: <path> · STATUS row: <task-id> · Status: <active|blocked|done> · Updated: <YYYY-MM-DD>_

## Current state & next
## Done so far
## Flow trace
## Files affected
## Pitfalls & rules
## Decisions in force
## Open
```

### Field guide – what goes in each, and how

| Section | What goes here | Format | Fed by tag |
|---|---|---|---|
| Current state & next | where the task stands + the single next action | 2-4 bullets; last is an imperative `Next:` | Now/Next, NEXT, open BLOCKED |
| Done so far | completed work, curated | past-tense bullets, cite commit/file | DONE |
| Flow trace | how it works, for a resuming agent | short ordered steps with `file:line` | FOUND (how-it-works) |
| Files affected | files touched and why | `path – change – why` per line | EDIT |
| Pitfalls & rules | traps to avoid; rules/specs to honor | `Avoid X because Y` / `Must Z per <ref>` | FOUND (pitfall), DECIDED (rule) |
| Decisions in force | load-bearing choices still governing | `Decided X because Y (date)` | DECIDED (decision) |
| Open | unresolved blockers/deferrals/questions | `what · on what · unblock path`, or `none` | unresolved BLOCKED, deferrals |

### Format conventions (state once, keep every line terse)

- Absolute dates (`2026-08-25`), never "today".
- Cite `file:line` or a commit; do not restate what the code or a research doc already says.
- One fact per bullet.
- Never record PII (card, CVV, SMS code, raw phone, password).
- Mark anything unverified as `UNVERIFIED`.
- An empty section keeps its heading with `- none`, so the reader sees it was considered, not forgotten.

### Filled sample

```markdown
# State – issue-12-edit-business-profile
_Spec: spec/issue-12-edit-business-profile.md · Plan: active/issue-12-edit-business-profile/plan/ · STATUS row: issue-12 · Status: active · Updated: 2026-08-25_

## Current state & next
- Phase 3 of 4 done: business-name field renders in the edit view at v1 parity.
- v2 mapper entries landed and unit-tested; v1 not yet mirrored.
- Next: mirror the fieldMapper + elementMapper entries into v1 (runtime uses v1).

## Done so far
- Phase 1-2: edit view registered in template/default.json, viewMapper wired (commit 9f3a1c2).
- Phase 3: v2 fieldMapper + elementMapper for static/editBusinessName, unit test green (commit a1b2c3d).

## Flow trace
1. Edit view renders through staticViewMapper only – HostedUAView.js:41.
2. The field name on the wire comes from the elementMapper top-level `name`, not the viewMapper key.

## Files affected
- server/src/config/mappers/v2/fieldMapper/default.json – added static/editBusinessName – field wiring.
- server/src/config/mappers/v2/elementMapper/default.json – added editBusinessName element – render + submit.

## Pitfalls & rules
- Must add mapper entries to BOTH v1 and v2, or the page renders empty on the other version (silent failure).
- The fieldMapper is shared, so namespace keys (static/editBusinessName) to avoid the auth-page collision.

## Decisions in force
- Decided to reuse HostedUAWelcomeHeader rather than a new component because it already forwards dataTestId (2026-08-24).

## Open
- none
```

---

## Keeping state.md honest – staleness

On every rewrite, apply one test to each line:

> Would a fresh agent, reading this line with no other memory, be led to do the wrong thing?
> If yes, fix it or cut it. If it is simply old-but-true history, keep it.

The seven sections split into two kinds, handled differently:

| Kind | Sections | Rule |
|---|---|---|
| Current | Current state & next · Open | Drop superseded lines outright. A finished Next becomes a Done-so-far bullet; a cleared blocker moves to Done so far and leaves Open. |
| Cumulative | Done so far · Flow trace · Files affected · Pitfalls & rules · Decisions in force | Keep true history; correct in place. Delete a line only when it is now false or misleading (reversed decision, a trace the code contradicts, a reverted file, a fixed pitfall). When you remove a load-bearing reversal, leave a one-line note in Done so far: `Reversed X -> now Y (date)`. |

Triggers for the sweep: a decision reversed; code contradicts a line (code is ground truth – fix the doc); a blocker cleared; a pitfall no longer applies.
Run the sweep as part of the session-end distill, before you reset the scratchpad.

Before / after:

```
# reversed decision (cumulative section) – correct in place, note the reversal in Done so far
- Decided to finalize Case A in AuthCode
  ->
- Decided to finalize Case A in Confirmation, not AuthCode (2026-08-20)
  (Done so far gains: "Reversed: Case A finalize moved AuthCode -> Confirmation (2026-08-20)")

# code contradicts doc – code wins
- Handler lives at HostedUALandingHandler.js
  ->
- Handler lives at HostedUAAuthCodeHandler.ts (verified in tree)
```

---

## The session lifecycle

### Session start / resume
1. Orient (Step 0).
2. If `state.md` does not exist, **bootstrap** it (see below).
3. If `SCRATCHPAD.md` is missing, empty, or reset, seed a fresh one from `state.md` (Now/Next from Current state & next; header filled). This is a START.
4. If `SCRATCHPAD.md` holds content, read it in full and continue. This is a RESUME.

### During the session
Append tagged log lines and keep Now / Next current, per the write-triggers above.

### Session end – distill, verify, reset
This is the core handoff. Do all three, in order.
1. **Distill:** promote each scratchpad tag into its `state.md` section, fold newly completed work into Done so far, refresh Current state & next, and run the staleness sweep. Bump `Updated:`.
2. **Verify (the gate):** re-read `state.md` and confirm every still-open item from the scratchpad – blockers, deferrals, in-flight edits, decisions in force – now has a home there. If anything lives only in the scratchpad, put it in `state.md` first.
3. **Reset:** only after the gate passes, wipe `SCRATCHPAD.md` back to the blank template for the next session. Do not delete the file – a reset file always answers "read me first"; a missing one breeds doubt.

The gate is what makes the whole system safe to re-run: nothing is thrown away until it is provably saved.

## Bootstrapping – when state.md does not exist yet

A brand-new task has no `state.md`.
Create it – seeded, never blank.
1. Write the header (task-id, spec/plan pointers, STATUS row id, status active, date).
2. Seed sections from the durable files already present: Current state & next and Flow trace from the plan/spec; Open from any STATUS gotcha relevant to this task; the rest `- none`.
3. Record only what the durable files actually say. Mark inferences as inferences. No fabrication.

Then seed the first `SCRATCHPAD.md` from this `state.md`.

## What NOT to do

- Do not fold the scratchpad into `state.md` or the reverse. Different lifespans, different disciplines.
- Do not edit past lines in the session log – it is append-only. Curation happens in `state.md`.
- Do not reset the scratchpad before the verify gate passes.
- Do not silently drop an open item during a state.md rewrite – move it to Done or Open with an outcome, or keep it.
- Do not write `STATUS.md` or move folders – that is the work-journal skill's job.
- Do not fabricate state when seeding or distilling.
