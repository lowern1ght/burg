# specs/player-role/spec.md

## Purpose

Encode the **Pillar 2 + Ruling 2** contract — *"player helps, not commands"*. This pillar is **reclassified** (VISION.md, 2026-07-31): it is the contract of acts 0–3, and the player earns the right to break it as he rises into acts 4–5. The capability captures both halves — what's banned in early game, what's allowed in late game, and the transition that opens the late game.

Source: P2 (PHILOSOPHY.md §"Pillar 2") + R2 (ROADMAP.md §"Three rulings") + VISION.md §"What becomes a starting role (was pillar 2)".

---

## Requirements

### Requirement: stranger/guest role (acts 0–3)

Through act 3, the player influences the village **only** through the listed verbs. No other lever exists; building one is a regression. The five allowed verbs:

1. Deposit resources into the stock.
2. Add a building to the construction queue.
3. Upgrade a placed building (within the limits the NPC executes).
4. Complete a quest the village has emitted.
5. Defend the village against a raid.

#### Scenario: stranger right-clicks the Town Anchor
- **WHEN** a player with zero standing interacts with the Town Anchor during acts 0–3
- **THEN** the anchor tells the player whose village this is and that he is not of it; the hub does NOT open for a stranger (ROADMAP §"Act 1 — Stranger").

#### Scenario: no command-mode hub in acts 0–3
- **WHEN** the town hub is opened in acts 0–3
- **THEN** every interactive widget in the hub resolves to one of the five allowed verbs; "issue build order", "set NPC schedule", "demolish building at will", and "allocate citizen to job X" do NOT exist.

### Requirement: ban-lift ladder (act 4 transition)

In act 4 the player earns the right to command villages he **founded or captured** (acts 4+); in act 5 the player has hard command of villages that are *loyal* to him. The transition is gated, not arbitrary:

| Ban (PHILOSOPHY.md) | When it holds | When it lifts |
|---|---|---|
| MineColonies-style colony management | acts 0–3 | act 4+, only for villages the player **founded or captured** |
| Player-controlled NPCs | acts 0–3 | act 4+, soft; act 5, hard — for **loyal** villages only |
| Combat overhaul | always | raid-scale vanilla combat allowed in any act; war-scale NPC-vs-NPC |
| Player-placed blocks in villages | **always** | never — pillar 4 is eternal |

#### Scenario: founded village, act 5
- **WHEN** a village has `acquisition = FOUNDED` and the player has reached act 5
- **THEN** the player MAY issue build orders and assign NPCs; the NPC builder still paces itself, still sleeps, still has morale — the player is not a god-console.

#### Scenario: free village, act 5
- **WHEN** a village has `acquisition = FREE` in act 5 (still unpillared)
- **THEN** the player's command input is ignored; the village is deaf to orders, runs on Pillar 1 only.

#### Scenario: captured village, garrison withdrawn
- **WHEN** a village has `acquisition = CAPTURED` and the player's garrison has been fully withdrawn for `> N in-game days` (N is a tuning constant, proposed in `changes/realm-acquisition/` when that change is opened)
- **THEN** the village revolts: production drops, the autonomy slider slides back toward 0, and the held status is lost.

### Requirement: hub becomes a window (act 4)

The town hub transitions from a command console (acts 0–3, where the player queues buildings) to a *window onto town intent* (act 4+, where the player's lever is supply). The transition is part of this capability, not a separate one.

#### Scenario: act 3 hub
- **WHEN** the hub is opened in act 3
- **THEN** a "construction" tab lets the player queue buildings; this is the command-console shape that exists today.

#### Scenario: act 4 hub
- **WHEN** the hub is opened in act 4 or later, on a village the player has standing in
- **THEN** the hub shows the town's current intent (what the builder is queued for, what materials it needs, what it would build if it had more of X); the player's input is **supply**, not orders. Bring stone and the town builds in stone; starve it of iron and no smithy appears.

#### Scenario: secondary entry points, act 4+
- **WHEN** the player tries to talk to a citizen about village business in act 4+
- **THEN** the conversation is a *secondary* channel (personal news, quests, gossip from a specific NPC). The rathaus / anchor / carried scrolls are the other channels. There is no single god-block — the late-game ruler reaches his realm through all three; a stranger reaches one village through one.

### Requirement: standing is per-player, persistent

Through acts 0–3 the player's standing with a given village is the gate on every command he tries to give. Through acts 4+ it is the gate on his `acquisition` and `autonomy` slider values.

#### Scenario: standing load-bearing
- **WHEN** a player has `standing < threshold` with a village
- **THEN** the hub does not open for him, settlers do not credit to him, and the act-4 command path is denied until standing crosses the threshold.

---

## Cross-references

- PHILOSOPHY.md §"Out of scope" — the original hard bans, now time-bound
- VISION.md §"What becomes a starting role (was pillar 2)" + §"the hub is a window"
- ROADMAP.md §"Three rulings" (R2) and §"Act 1–4 transitions"
- `changes/hub-becomes-window/` — the change that lands the act 4 transition
