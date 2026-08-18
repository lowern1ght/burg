# Delta spec — `hub-becomes-window`

## MODIFIED Requirements

### Requirement: hub becomes a window (act 4) — permadeath on the transition

**MODIFIED** (replaces the act-3 → act-4 framing in `specs/player-role/spec.md`)

**Previous text:** the act-3 → act-4 transition was described at proposal
level only; no test enforced it.

**New text:** the act-4 transition SHALL be gated on the per-town derived
`HubMode` (§"hub has two modes" in `construction-mode-supply-mode`).
Concretely:

- `Town#hubMode` MUST be the source of truth.
- The transition from `CONSTRUCTION` to `SUPPLY` happens at the predicate's
  *transition* moment — the first tick where both standing and structural
  conditions are met.
- The transition is **permanent**: a town that has crossed into SUPPLY mode
  MUST NOT revert to CONSTRUCTION mode for any standing decay in acts 4–5.
  (Standing can fall; the town can de-list the player; the mode does not
  flip back. This matches VISION §"earned-crown trajectory": once you cross,
  you have crossed.)

#### Scenario: act-4 transition is permanent
- **WHEN** a town has crossed into SUPPLY mode and the player's standing
  later decays below the act-4 threshold
- **THEN** the hub remains in SUPPLY mode for that town; the player's
  account is de-listed but the mode does not flip.

#### Scenario: stranger-act town is always CONSTRUCTION
- **WHEN** a stranger (standing = 0) visits any town
- **THEN** `hubMode` is `CONSTRUCTION` for that visit; the standalone
  right-click behavior ("you are not of this village") is the gate, not the
  mode itself. The mode is the *post-gate* shape.

---

## REMOVED Requirements

### REMOVED Requirement: act 3 hub unchanged

The text of the old "act 3 hub" scenario in the unmodified
`player-role` spec describes a terminal state. It is **not** removed from
the cap; it is preserved exactly. What this delta removes is the proposal-
level transition language that implied the hub could keep growing tabs —
that language is replaced by the new mode-based model.

(Net effect on the cap: zero scenarios removed; one new scenario added;
no requirement text removed.)

