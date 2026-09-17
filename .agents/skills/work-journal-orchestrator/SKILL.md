---
name: work-journal-orchestrator
description: "The single entry point for the project's .work/ working-memory system (STATUS.md + per-task state.md + per-session SCRATCHPAD.md). Use it to run any part of a .work task's life: starting or resuming a work session, capturing progress mid-session, ending a session, or wrapping a finished task. Trigger it whenever the user says 'follow work-journal-orchestrator', 'start a work session', 'where were we', 'pick up where we left off', 'update .work', 'record this', 'checkpoint this', 'wrap this up', 'we finished X', or otherwise wants the working record kept current – even if they do not name it. It detects which phase the task is in and drives the scratchpad and work-journal component skills; do not invoke those two directly. Re-trigger it any time the previous cycle looks unfinished; it is safe to run repeatedly and finishes whatever step was left open."
---

# Work Journal Orchestrator – the single door to `.work/`

This skill is the one entry point for the project's working-memory system.
You do not call the `scratchpad` or `work-journal` skills yourself.
This orchestrator detects where the task is in its life and drives them for you.

The point is simplicity: the user manages one skill, not three.

## The system in one picture

Context flows upward as it settles from volatile to durable:

```
SCRATCHPAD.md   (session, ephemeral)   what I am doing right now; reset each session
    |  distill at session end
    v
state.md        (task, durable)        what the task is and where it stands; read to resume
    |  roll up one line
    v
STATUS.md       (project, durable)     every task, one line + a pointer down to state.md
```

- `SCRATCHPAD.md` and `state.md` are written by the **scratchpad** skill.
- `STATUS.md` and the `.work/` layout are owned by the **work-journal** skill.
- This split gives exactly one writer per file, which is what keeps the files from clashing.

## Step 0 – Orient before you route

Always read these first, so you route from fact, not guess:
1. `.work/README.md` – the canonical folder map for this project.
2. `.work/STATUS.md` – the index; find the row for the work at hand and its pointer into `active/`.
3. The active task's `state.md` and `SCRATCHPAD.md`, if a task is in flight.

The `.work/` layout can differ per project, so let `README.md` win over anything assumed here.

## Step 1 – Detect the phase

Read the observable state, then pick the row.
When more than one fits (for example a session that resumes and then ends), run them in order.

| Observed state | Phase | What to do |
|---|---|---|
| The work has no task folder in `active/` yet | **OPEN** | Invoke **work-journal** to open the task: create `active/<task-id>/`, point it at its `spec/`, add a STATUS row. |
| `SCRATCHPAD.md` is missing, empty, or reset | **START** | Invoke **scratchpad**: bootstrap `state.md` if needed, then seed a fresh `SCRATCHPAD.md` from `state.md`. |
| A task folder exists; `SCRATCHPAD.md` holds live content | **RESUME** | Invoke **scratchpad**: read `SCRATCHPAD.md` in full, then continue the work. |
| The session is ending, or the user says "checkpoint / wrap the session / stop for now" | **SESSION END** | Invoke **scratchpad**: distill `SCRATCHPAD.md` into `state.md`, verify the transfer, reset the scratchpad. Then invoke **work-journal** to refresh the one-line STATUS note. |
| The task is finished (user says "done", or `state.md` status is done) | **WRAP** | Run SESSION END first if the scratchpad still holds content. Then invoke **work-journal**: promote reusable research, remove the empty scratchpad, move the folder to `archive/`, flip the STATUS row to Done. |

If you cannot resolve an active task and the user has not named one, ask which task this is.
Never invent a task folder or drop files in an arbitrary place.

## Step 2 – Delegate

Invoke the component skill named in the phase row and follow it.
Each component skill carries the exact schema, fill-guide, and rules for the files it owns.
This orchestrator holds none of that on purpose, so there is one source of truth per file.

## Re-trigger is always safe

The user may fire this orchestrator again when a cycle looks unfinished – for example the agent stopped before ending the session.
That is expected and safe.
Phase detection reads the current files, so a re-run simply picks up the open step (typically a SESSION END that never happened) and completes it.
Clearing the scratchpad is gated: the scratchpad skill resets `SCRATCHPAD.md` only after it confirms every open item already landed in `state.md`.
So a re-run can never throw away context that was not first saved.

## What NOT to do

- Do not write `SCRATCHPAD.md`, `state.md`, or `STATUS.md` from here. Route to the component that owns the file.
- Do not skip Step 0. Routing without reading the current files is how the wrong phase gets chosen.
- Do not invent a location when no active task resolves. Ask.
