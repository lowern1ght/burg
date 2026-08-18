# specs/vanilla-feel/spec.md

## Purpose

Encode **Pillar 5** — *"vanilla feel"* — into a testable capability. Burg's GUI stays minimal; the mod should feel like vanilla Minecraft with extra life, not a UI overhaul. The pillar is **eternal**.

Source: P5 (PHILOSOPHY.md §"Pillar 5").

---

## Requirements

### Requirement: minimal hub surface

The Town Hub (Stock / Construction / Upgrade tabs + draggable widgets) MUST be the surface. Additions to the hub MUST be subtle — colored pips, small calendar indicators, new widgets that respect the existing visual language. New top-level tabs MUST be rejected; floating menus MUST be rejected; JEI-style sidebars MUST be rejected.

#### Scenario: a new tab proposal
- **WHEN** a PR adds a new top-level tab to the Town Hub
- **THEN** the PR MUST be rejected at the proposal stage; the proposer is asked to express the feature as a widget on an existing tab.

#### Scenario: a new widget proposal
- **WHEN** a PR adds a new widget within the Stock, Construction, or Upgrade tab
- **THEN** the widget passes if (a) it reads in the same visual language as its siblings and (b) it is keyboard-discoverable on the same screen.

### Requirement: no combat override

Burg MUST NOT change how mobs or weapons work. The combat overlay for the player MUST be vanilla. NPC-vs-NPC combat at war scale is a separate system (`changes/realm-war-state-machine/`, not yet authored), explicitly allowed by Ruling 3 (patched 2026-07-31) and not part of the player's surface.

#### Scenario: custom weapon damage in a Burg datapack
- **WHEN** a datapack adds a JSON entry that defines a custom weapon with non-vanilla damage values
- **THEN** the entry MUST be rejected at load time with a clear error; no silently-overridden combat stat ships.

### Requirement: vanilla villagers, vanilla trades

Burg MUST use vanilla villagers and vanilla trade mechanics. No "Burg" villagers with custom trades are introduced. This is one of the explicit hard bans and remains so in every act.

#### Scenario: a custom villager proposal
- **WHEN** a PR introduces a new entity with profession `burg_baker` and a custom offer table
- **THEN** the PR MUST be rejected; the surface for trading-with-NPC is a vanilla villager profession mapped through `Merchant` offer-list JSON (ROADMAP.md §"Act 2").

### Requirement: vanilla decoration stays

Burg MUST NOT introduce custom furniture, signs-with-custom-text, or any other decoration element that competes with vanilla's own. The village-decoration vocabulary is what Minecraft ships with; Burg composes from it.

#### Scenario: a custom chair proposal
- **WHEN** a PR adds a custom chair block to the mod
- **THEN** the PR MUST be rejected; the burg-buildings skill (CLAUDE.md §"tools (run from `tools/`)") and the burg-skins skill already cover the decoration vocabulary through vanilla furniture + structures, and a new block duplicates that surface for no gameplay reason.

---

## Cross-references

- PHILOSOPHY.md §"Pillar 5" + §"Hard bans"
- ROADMAP.md §"Act 0–3 hub commitments"
- VISION.md §"vanilla feel" (implicit, in pillar 5)
