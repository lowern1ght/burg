# OpenSpec — initialization and walk-through

> **Dated 2026-08-19.** First OpenSpec init on `lowern1ght/burg`. Six
> capability specs and two in-flight changes land green.

This file documents the initial OpenSpec setup so the next contributor
(and the next agent session) does not have to redo the archaeology.

## What got created

```
openspec/
├── AGENTS.md                                 ← this file's hub pointer (see below)
├── config.yaml                               ← project context for AI authors
├── specs/
│   ├── village-autonomy/spec.md              ← P1 (eternal)
│   ├── player-role/spec.md                   ← P2 (reclassified) + R2
│   ├── datapack-content/spec.md              ← P3 (eternal — the 5 handlers)
│   ├── npc-builder-actor/spec.md             ← P4 (eternal)
│   ├── vanilla-feel/spec.md                  ← P5 (eternal)
│   └── earned-crown-trajectory/spec.md       ← VISION.md, all six acts
└── changes/
    ├── hub-becomes-window/                   ← Act 4 transition
    │   ├── proposal.md
    │   ├── tasks.md
    │   └── specs/
    │       ├── construction-mode-supply-mode/spec.md   (NEW)
    │       ├── player-role/spec.md                    (MODIFIED)
    │       └── npc-builder-actor/spec.md              (MODIFIED)
    └── vanilla-village-conversion/           ← Act 0 bridgehead
        ├── proposal.md
        ├── tasks.md
        └── specs/vanilla-village-anchor/spec.md  (NEW)
```

`openspec/config.yaml` carries the project context (the five pillars +
the three rulings + the trajectory) so any AI agent authoring a future
spec or change sees the design canon as load-bearing context, not as
free-floating advice.

## How to verify

```bash
openspec validate --all                  # exit 0, all green
openspec list --specs                    # 6 specs
openspec list                            # 2 changes
openspec status --change hub-becomes-window
openspec status --change vanilla-village-conversion
openspec doctor                          # root ok, no references
```

The current green baseline is:

```
✓ spec/datapack-content
✓ spec/earned-crown-trajectory
✓ change/hub-becomes-window
✓ spec/npc-builder-actor
✓ spec/player-role
✓ spec/vanilla-feel
✓ change/vanilla-village-conversion
✓ spec/village-autonomy
Totals: 8 passed, 0 failed
```

## Authoring loop

For a new feature:

```bash
# 1. Read the relevant spec(s) first — capability, not implementation.
openspec show specs/<capability>

# 2. Author a change.
openspec new change <change-name>
# ... edit proposal.md / tasks.md / specs/<modified-capability>/spec.md ...

# 3. Validate before commit.
openspec validate <change-name>

# 4. Status during implementation.
openspec status --change <change-name>

# 5. Archive when shipped.
openspec archive <change-name>
```

The archive step ALSO merges the deltas in
`changes/<name>/specs/<cap>/spec.md` back into the canonical
`openspec/specs/<cap>/spec.md`. The change directory then moves to
`openspec/changes/archive/`. **Do not manually move files** — `archive`
is the only sanctioned merge.

## Constitutional guards baked in

The `config.yaml` rules block the most common LLM mistakes:

- `rules.proposal`: cite the pillar(s), quote STATUS.md evidence, name
  the act. A proposal that doesn't cite a pillar is rejected at
  validation.
- `rules.spec`: SHALL/MUST vocabulary, `Source: P#` footer when a
  requirement restates a pillar. Traceability to PHILOSOPHY survives
  the LLM session.
- `rules.tasks`: no multi-day tasks; every JSON-touching task gets a
  `[ ] tools/describe.py self-gate` line; every act-number-changing
  task gets a STATUS.md-update line.

The `operations.archive` block mandates a one-paragraph summary naming
which STATUS.md rows moved and on what evidence — closing the
"build-green → verified-in-game" loop that ROADMAP.md requires.

## Where to read more

- **[`FOCUS.md`](../01-vision/FOCUS.md)** — what this fork wants vs
  what the author wanted. Read this BEFORE adding any feature.
- **`CLAUDE.md`** §"Constitutional rulings" — the four-gate model and
  the `tools/` calibration rig.
- **`docs/02-roadmap/ROADMAP.md`** — six acts in order, with the
  closing-line rule that nothing is finished until it is
  `verified-in-game`.

## Status of this file

Green baseline. Next session: do not regenerate — extend.
