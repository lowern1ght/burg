# OpenSpec archive log

> Living record of which OpenSpec changes are in flight vs. archived, and
> what their archive produced. Created 2026-08-19 during the DDD foundation
> completion work (ADR-0008 + ADR-0009/0010/0011/0012/0013/0014).

## How to update

When you archive a change:
- Move the change directory under `openspec/changes/archive/<date>-<name>/` (the `openspec archive` command does this).
- Add a row to the **Archived** table below.
- If a hand-merged main spec was produced, mention it in the spec-hand-merge notes column.

When you close a change without archiving (superseded by another, or the work landed outside OpenSpec):
- Add a row to **Closed without archive** with a one-line reason.

## Active changes

| name | tasks | notes |
|---|---|---|
| `vanilla-village-conversion` | 0/15 | Design only; no code. Act-0 bridgehead lives on the same code path as the construction queue carve; consider merging into one change before any implementation. |
| `hub-becomes-window` | 0/14 | Design only; the act-4 transition. Application wiring (ADR-0018) lands here when player-facing call sites for stock deposit are added. |

## Archived (skip_specs, archived via PRs #29 / #30 already on master)
settlement-production-domain and settlement-sot-promote were archived before this PR by their feature branches; this log notes them for completeness.

## Re-archive (post-#29 / post-#30)

`settlement-stock-promote` and `settlement-standing-acquisition` were already archived via their respective feature branches (PRs #29 and #30) and are listed in the table below at lines 34 and 36. This entry is a log acknowledgement only — no commit, no PR; just confirming the re-archive.

## Archived

| name | archived | spec hand-merge | main spec updated |
|---|---|---|---|
| `settlement-application-services` | 2026-08-19 | no (skip_specs) | n/a — ports are infra/application; no domain-settlement delta |
| `settlement-construction-queue` | 2026-08-19 | no (skip_specs) | yes — `domain-settlement/spec.md` "construction queue domain view" |
| `settlement-quest-log` | 2026-08-19 | no (skip_specs) | yes — `domain-settlement/spec.md` "quest log domain view" |
| `settlement-stock-promote` | 2026-08-19 | no (skip_specs) | yes — `domain-settlement/spec.md` "stock ledger value object" cache note |
| `settlement-stock-ledger` | 2026-08-19 | no (skip_specs via `--skip-specs`) | yes — `domain-settlement/spec.md` "item identity wrapper" + "stock ledger value object" |
| `settlement-standing-acquisition` | 2026-08-19 | no (skip_specs via `--skip-specs`) | yes — `domain-settlement/spec.md` "standing value object" + "acquisition lifecycle" |
| `ddd-foundation` | 2026-08-19 | no (skip_specs) | yes — `domain-settlement/spec.md` got the foundational scenarios the carves then MODIFIED |

## Hand-merged `domain-settlement/spec.md` (2026-08-19)

When a change has a `skip_specs: true` flag the main spec is *not* auto-merged. The 7 settlement carves were deliberately skip_specs (each was a small additive view with no risk of being a draft). The hand-merge produced the current main spec at `openspec/specs/domain-settlement/spec.md` with 9 requirements:

1. settlement domain is Minecraft-free
2. Town stays the aggregate root of Settlement
3. save format survives the strangler
4. item identity wrapper (`ItemId`)
5. stock ledger value object (`StockLedger`, dual-write note)
6. standing value object (`Standing` / `StandingBook`)
7. acquisition lifecycle (`Acquisition`)
8. construction queue domain view (`ConstructionQueue` / `ConstructionIntent`)
9. quest log domain view (`QuestLog` / `QuestRef`)

The two remaining in-flight changes (`vanilla-village-conversion`, `hub-becomes-window`) are *not* skip_specs — they have full delta specs that will mutate `vanilla-village-anchor` and a new `construction-mode-supply-mode` capability respectively.

## Closed without archive

(none yet)
