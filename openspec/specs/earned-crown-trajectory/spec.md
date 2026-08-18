# specs/earned-crown-trajectory/spec.md

## Purpose

Capture **VISION.md**'s earned-crown trajectory as a testable capability — the single sentence on the front of the project (*"a medieval life-sim where villages live on their own, and a stranger can — through help, or force, or time — become a king over them"*) broken into act-numbered states, with the pivot at act 4 (the hub becomes a window) and the act 5 endgame (a metropolis plus colonies plus foreign holdings).

Source: VISION.md §"The trajectory" + §"Three ways to hold a village" + §"The autonomy–control slider" + §"The realm grows from inside".

---

## Requirements

### Requirement: act-numbered player standing

Each act is identified by what the player can do and what he can hold, in that order. A player who has surpassed the verbs of act N is act N+1; a player who has not yet acquired the lowest verb of act N is at most act N−1.

| Act | Player's verb(s) | What he can hold | Power over a village |
|---|---|---|---|
| 0 | travel | nothing | none — he is not of it |
| 1 | look, speak | nothing | none, but he is noticed |
| 2 | trade, supply | nothing | influence (standing) |
| 3 | choose | nothing yet, but heard | supply steers what gets built |
| 4 | keep choosing | a village may name him chief | soft command of one village |
| 5 | rule, negotiate, conquer | a **realm** of many villages | hard command, scaled by acquisition |

#### Scenario: player in act 3 enters a virgin village
- **WHEN** a player who has been trading and supplying (act 2 standing) for ≥ one in-game week then makes a non-trivial supply choice in a new village
- **THEN** he crosses into act 3 — the village notices him, the era-branch choice is surfaced to him if the prerequisites fire, and he can no longer pretend to be anonymous.

### Requirement: hub transitions in act 4

Up through act 3 the hub is a command console (the player queues buildings). At act 4 it becomes a view onto what the town intends to build; the player's lever is **supply**. This transition is mandatory, not optional — and the act-4 town is also the act-4 village that earned him standing, so the transition is felt by the player in the same town, not a new one.

#### Scenario: act 4 transition
- **WHEN** the player's standing with a town crosses the act-4 threshold and the town has crossed the act-4 structural threshold (core-radius populated, industry zoning pushed out, road laid)
- **THEN** on next hub open the construction tab becomes a read-only "what the town intends" view; the player's input field is the supply list, not the build queue.

### Requirement: realm grows from inside (act 5)

In act 5 the player's **realm** is structured, not flat. The realm has three kinds of holding:

1. **Metropolis** — the player's first settlement, grown from village → town → city. Water is a city-forming factor; a candidate site without water tops out as a village, not a city.
2. **Colonies (founded by expedition)** — when the metropolis needs a resource it lacks, it sends an expedition 1000+ blocks out to found a small resource colony on the deposit. The colony depends on and trades with the metropolis; it is never an equal.
3. **Foreign villages** — neighboring AI villages the player has acquired by one of three paths.

#### Scenario: metropolis founds a colony
- **WHEN** the metropolis's stock reports a deficit in a resource category for `≥ K in-game days` (K is a tuning constant)
- **THEN** an expedition NPC leaves the metropolis bound for the nearest deposit of that category > 1000 blocks away, founds a colony anchor there, enlists settlement as it grows, and the colony's production targets the deficit.

#### Scenario: water gates a city
- **WHEN** a candidate town site has no river or lake within `R` blocks
- **THEN** the town tops out as a village. It cannot become a city. (Worldgen must supply the candidate water; a river-locked town that already started cannot be promoted.)

### Requirement: three ways to hold a foreign village

Foreign villages become "his" by exactly one of three paths; the path decides how obedient the village is:

| Path | Method | Obedience |
|---|---|---|
| Elevated | The village names him chief; a network of chiefs raises him to king | Most loyal (reputation-driven) |
| Founded | He settles a new village from nothing | Loyal by birth, weak |
| Captured | He takes it by force (siege / starvation / submission) | Resists: low morale, slow output, revolt-prone |

#### Scenario: elevated village, act 5
- **WHEN** a foreign village has `acquisition = ELEVATED`, the player has standing ≥ elevated-threshold for ≥ `T` in-game days, and a chief NPC exists
- **THEN** the village may name the player chief; from that moment, the village's `autonomy` value sits at the `elevated` band of the slider, the player's commands are accepted as soft directives, and the NPC builder still paces itself.

#### Scenario: captured village, garrison left
- **WHEN** a village has `acquisition = CAPTURED` and the player's garrison NPC count drops below `G_min`
- **THEN** the village's `autonomy` slides back toward `captured` band (lower compliance); production slows; if the garrison drops to 0 for `> N days`, the village revolts and the held status is lost.

### Requirement: autonomy–control slider is per-holding

A held village sits on a slider `autonomy ∈ [0, 1]`. The slider is **derived** from `acquisition` + garrison state + standing:

- `FREE`: 1.0 (deaf to orders; pure Pillar 1).
- `ELEVATED`: 0.7
- `FOUNDED`: 0.8
- `CAPTURED`: 0.3, sliding toward 0 if garrison drops.

#### Scenario: command path uses autonomy
- **WHEN** the player issues a build order to a held village
- **THEN** the order is forwarded to the NPC builder only if `autonomy > threshold_for(acquisition)`; otherwise the order is queued in the village's "heard but deferred" log.

### Requirement: war-scale combat is NPC-vs-NPC

War-scale combat (realm vs realm, sieges, field battles) is **always** NPC-vs-NPC. The player supplies, orders, and watches; he does not swing the sword. The scale problem (Mount & Blade / Manor Lords vs Minecraft's 1v1) is bridged by a custom battle state-machine (60+ NPC battles resolved at chosen pace) using the Villager Recruits mod as feasibility proof.

#### Scenario: realm raises an army
- **WHEN** the realm's barracks and armory hit a recruitment threshold
- **THEN** soldier NPCs are eligible for war-scale battle assignment; the player does not spawn a personal combat instance; the war-scale simulator runs autonomously and reports results to the player's hub.

#### Scenario: war simulation reports back
- **WHEN** a war-scale battle resolves (in-game days, not real time)
- **THEN** the player's realm gains/loses gold, garrison, and standing with the opposing realm; battle outcome is loggable and survives `data/onceuponatown/realm/<realm-id>/wars/*.json`.

### Requirement: raid-scale combat is vanilla and player-controlled

Raid-scale combat (a small group attacking a village garrisoned by worker-militia) lets the player fight personally, with vanilla combat, in **any act**. This is the patched reading of Ruling 3 (2026-07-31) and is bounded by Pillar 4+Pillar 5.

#### Scenario: player attacks an enemy village, act 2
- **WHEN** a player in act 2 enters a hostile village's perimeter during a raid event
- **THEN** vanilla combat plays out (swords, bows, the same damage numbers as survival); on victory the player may loot per vanilla rules; he does not place blocks and does not open any town hub for that village.

---

## Anti-patterns

- ❌ A flat `villages: List<Town>` shape for the realm (the pre-grilling shape). Act 5 has three kinds of holdings; the data model must too.
- ❌ A single god-block for player↔realm input (VISION §"the hub is a window"). Three channels: rathaus (building), conversation (NPC), scrolls/items (mobile).
- ❌ Player-built walls around his own town pillar. Walls are NPC-built.
- ❌ A god-mode "I command everything" command panel, in any act.

---

## Cross-references

- VISION.md — the trajectory, the realm structure, the slider
- PHILOSOPHY.md §"Pillar 2 reclassification" + §"Hard bans"
- ROADMAP.md §"Act 5" + §"the closing-line rule"
- `changes/realm-acquisition/` (not yet authored) — the change that lands acts 4–5 mechanics
- `changes/hub-becomes-window/` — the act-4 transition
