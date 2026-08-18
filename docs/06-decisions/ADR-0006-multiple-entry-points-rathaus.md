# ADR-0006: Multiple entry points to a village — rathaus, conversation, scrolls — replacing the single god-block

- **Status**: Accepted
- **Date**: 2026-07-31
- **Decided by**: owner (grilling session)

## Context

Today the [`TownAnchorBlock`](../../common/src/main/java) is a single block at the
village centre; right-clicking it opens the whole mod's UI — Stock, Construction,
Upgrade, era, quests. It is the sole point of entry into a village's management
layer. During the grilling (Q1/Q2) the owner rejected this on two grounds: a block
plonked in the middle of a square is architecturally wrong, and a village should
be knowable and governable through many channels, not one.

The god-block pattern also collides with the founding flow. Today founding is a
menu click on a block; the owner wants it to be a ritual — a deed item used on the
ground, after which an NPC builder raises the first building — and pillar 4 (the
player never places blocks) has to survive that ritual intact.

This ADR replaces the single entry point with three channels matched to three
play styles, and redefines founding to fit.

## Decision

The **rathaus is a building** — an NBT structure with levels, like the barracks
or livestock sets — not a god-block. Inside it sits the rathaus-block, the
functional block that opens the official interface (Stock, era tree, construction
queue, village health). It is the **primary** entry point: the block is part of a
real place, not alone in a plaza.

**Conversation with a citizen** is the **secondary** entry point. Right-click a
person for name, trade, skill, current activity, local news, and personal quests
keyed to that NPC's morale — not the village's. This channel is for life, not
management.

**Scrolls and map items** are the **tertiary** entry point: mobile access for a
king (view the map, read stats, issue orders at range). This channel is for
governance when the realm has grown, not for building.

The **Town Anchor ceases to be the player's interface**. It may remain as an
invisible worldgen marker for placement and state, but the player no longer clicks
it. Founding a new settlement (Q3) becomes: the player uses a "foundation deed"
item on the ground (use-item-on-block, like planting a sapling) → an invisible
marker is placed → the rathaus NBT is queued → an NPC builder constructs it
block-by-block → on completion the rathaus-block appears inside. The player placed
an **item**, never a block; pillar 4 holds.

## Consequences

- + The god-block anti-pattern is removed; the rathaus is a real place a player
  walks into.
- + Three channels match three play styles — manager (rathaus), social
  (conversation), king-on-the-move (scrolls).
- + Founding becomes a ritual act (deed on ground, builder raises the hall)
  rather than a menu click.
- + Quests become personal — issued by an NPC with a mood — rather than
  institutional, issued by the village abstraction.
- − A new NBT structure set (rathaus levels) is required — content work alongside
  the military and livestock sets.
- − A conversation system deeper than the current trade screen is needed
  ([`ROADMAP.md`](../02-roadmap/ROADMAP.md) act 1).
- − Deprecating the Town Anchor as the player-facing interface needs a migration
  path for existing saves.

## Related

- [`VISION.md`](../01-vision/VISION.md) — villages as places to be known, not managed.
- [ADR-0001](ADR-0001-earned-crown-trajectory.md) — the trajectory that grows the player from stranger to king.
- [`ROADMAP.md`](../02-roadmap/ROADMAP.md) — act 1 conversation; act 4 hub-as-window.
- [`../03-design/npc/README.md`](../03-design/npc/README.md) — the NPC layer the conversation channel keys off.
