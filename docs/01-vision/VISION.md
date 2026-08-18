# Vision — the earned crown

> Resolved 2026-07-31, after the fork between Burg's existing canon and a larger
> medieval ambition. This document is now the top-level authority on *what kind
> of game Burg is*. Where it disagrees with [`PHILOSOPHY.md`](PHILOSOPHY.md) or
> [`ROADMAP.md`](../02-roadmap/ROADMAP.md), **this document wins**, and the disagreeing lines
> in those two are flagged at their top.

## The question this exists to settle

Is Burg a stranger-sim (the player is always a passing helper) or a lord-sim
(the player commands, conquers, rules)? Both prior docs commit hard to the
first; the owner's actual ambition is the second, reached through the first.

The answer is **both, in sequence**. The player's power is **earned**, not
given. He starts under the strictest reading of the canon and graduates out of
it by act. This single decision shapes every system below it — the NPC state
machines, the diplomacy layer, the combat model, the test framework — which is
why it had to be settled before any of them.

## The one sentence

*Burg is a medieval life-sim where villages live on their own, and a stranger
can — through help, or force, or time — become a king over them.*

## The trajectory

The player's role changes across the acts. The verbs are borrowed from
[`ROADMAP.md`](../02-roadmap/ROADMAP.md); the **power** column is what this document adds.

| Act | Player's verb | What he can hold | Power over a village |
|---|---|---|---|
| 0 — Arrival | travel | nothing | none — he is not of it |
| 1 — Stranger | look, speak | nothing | none, but he is noticed |
| 2 — Guest | trade, supply | nothing | influence only (standing) |
| 3 — Trusted | choose | nothing yet, but heard | his supply steers what gets built |
| 4 — A town, a wall | keep choosing | a village may name him chief | soft command of one village |
| 5 — The far end | rule, negotiate, conquer | a **realm** of many villages | hard command, scaled by how each was acquired |

Acts 0–3 are the existing canon, unchanged. Acts 4–5 are where this document
extends it: chief → king, one village → a realm, help → the right to command.

## What stays eternal

Four of the five pillars in [`PHILOSOPHY.md`](PHILOSOPHY.md) are constitutional
and hold for every act:

1. **Villages are autonomous.** They live whether or not the player exists.
2. ~~Player helps, not commands.~~ → *see below; this is now the starting role.*
3. **Datapack-first content.** New buildings, eras, quests, realms, wars are
   JSON, not code.
4. **The NPC builder is the actor** that places blocks. The player never lays a
   block in a village, even a captured one — he gives orders, the builder
   executes.
5. **Vanilla feel.** Minimal GUI, no UI overhaul.

## What becomes a starting role (was pillar 2)

"Player helps, not commands" is **no longer eternal**. It is the contract of
the early game — the stranger and guest acts — and the player earns the right
to break it as he rises. Concretely, the [`PHILOSOPHY.md`](PHILOSOPHY.md) hard
bans are reclassified as **the rules of the stranger/guest phase**, lifted in
the late game through earned progression:

| Hard ban (PHILOSOPHY.md) | When it holds | When it lifts |
|---|---|---|
| MineColonies-style colony management | acts 0–3 | act 4+ for villages the player **founded or captured** |
| Player-controlled NPCs | acts 0–3 | act 4+, soft; act 5, hard — for **loyal** villages only |
| Combat overhaul | always, for war-scale army combat | raid-scale vanilla combat is allowed at any act (see ruling 3 patch below); **NPC vs NPC / army** war is a separate system (see scale problem) |
| Player-placed blocks in villages | **always** | never — pillar 4 is eternal |

Note the one asymmetry the owner's ruling creates and this document keeps:
**the player never fights a *war* himself, and he never places a block.** Even a
king commands; he does not lay the stone. His leverage is logistics (supply),
loyalty (standing), and orders (which a loyal builder executes). That is what
keeps a conquered village from feeling like a puppet: it still has a builder
with its own will, merely听从ing under duress.

> **Refined 2026-07-31 by grilling — ruling 3 patched, not repealed.** The
> "never swings a sword" line above was too absolute. Combat splits by scale:
>
> - **Raid-scale** — attacking a village whose garrison is worker-militia. The
>   player **may** fight personally, vanilla combat, any act. A geared player
>   can solo the garrison. This is the kind of fight Minecraft combat is built
>   for, and forbidding it gains nothing.
> - **War-scale** — realm-vs-realm armies, sieges, field battles. **Always
>   NPC-vs-NPC.** The player commands, supplies, and watches; he does not swing
>   the sword. This is where the scale problem (below) lives.
>
> The eternal ban is on *war*-scale personal combat, not on *raid*-scale
> fighting. Pillar 4 (no player-placed blocks) is untouched.

## Three ways to hold a village

This is the core of the endgame. A village becomes "yours" by exactly one of
three paths, and **the path decides how obedient it is**:

1. **Elevated** — you helped so much that the village names you chief, and a
   network of chiefs raises you to king. *Peaceful, reputation-driven. The
   existing reputation work is the seed.* Yields the most loyal subjects.
2. **Founded** — you settle a new village from nothing (anchor placement by the
   player is the one new player-placed-block exception, scoped to the founding
   act). *Loyal by birth, but young and weak.* Takes the longest to matter.
3. **Captured** — you take an existing village by force (siege / starvation /
   submission). *Fast, but it resists: low morale, slow output, prone to
   revolt.* Obedience is extracted, not given.

The three paths are not mutually exclusive across a playthrough. But see the
correction below for **which villages** they apply to.

### Refined 2026-07-31 by grilling — the realm grows from inside

The original framing above treated a king's realm as a bag of acquired villages.
That is wrong. The player's **own** realm grows from inside:

- **The metropolis** is his first settlement, grown from village → town → city.
  A city is what a village becomes when it is large, walled, and populous.
- **Colonies** are founded by expedition. When the metropolis needs a resource
  it lacks (timber, ore, food, livestock), it sends an expedition 1000+ blocks
  out to found a small resource colony on the deposit. The colony depends on
  and trades with the metropolis; it is never an equal.

The three acquisition paths above (elevated / founded / captured) describe how a
**foreign** village — a neighbouring AI realm — becomes yours. Your own colonies
are always **founded by expedition**. This keeps the endgame from reading as a
shopping list of conquests: the spine of the realm is growth, and the three
paths are how the periphery attaches.

**Water is a city-forming factor.** Settlements need rivers or lakes; a village
that would grow to a city must have the water for it. Worldgen has to supply
this — a candidate site without water tops out as a village, not a metropolis.

## The autonomy–control slider

A village is never a fully willing puppet, and never fully deaf to power. Each
held village sits on a slider:

```
free ──────── elevated ──────── founded ──────── captured
(100% autonomous,          (loyal, takes        (resists, low morale,
 deaf to orders)            soft orders)          revolt risk, force only)
```

- **Free** villages (act 0–3, or AI-only villages forever) are pure pillar 1.
- **Elevated/founded** villages accept the player's construction queue and
  production directives, but the NPC builder still paces itself, still sleeps,
  still has morale.
- **Captured** villages obey only under garrison and starve/revolt if the
  garrison is withdrawn. They are the cost of the fast path.

This slider is **how endgame command works without killing the living-world
feeling**. The player never gets a god-console; he gets degrees of influence,
earned or enforced, and every village on the map keeps some will of its own.

## The immediate architecture consequence

Today `Town` is the top entity in the state layer ([`ARCHITECTURE.md`](../04-engineering/ARCHITECTURE.md),
`town/Town.java`). A king over many villages, and capture, both need a layer
**above** `Town`:

```
Realm / Kingdom            ← does not exist yet
  └── ruler         (a player, or an AI chief)
  └── villages:     List<Town> with an acquisition flag + autonomy value
  └── relations:    to other realms (war / truce / alliance / tribute)

*(Refined 2026-07-31 by grilling.)* The flat `villages: List<Town>` above is the
pre-grilling shape. The realm is actually three kinds of holding: a
**metropolis** (the first city) plus **colonies founded by expedition**
(resource satellites that trade up), plus **foreign villages** acquired by the
three paths. See "the realm grows from inside" above.
```

This `Realm` abstraction is the **next architectural decision**, not a feature.
It is what unblocks acts 4–5. Building it is tracked separately; this document
only names it as a consequence of the vision, so it is not re-litigated.

Capture also implies `Town` gains an `acquisition` field (`FREE | ELEVATED |
FOUNDED | CAPTURED`) and a derived `autonomy` value, and that the construction
queue / order-taking paths check `autonomy` before accepting player input.
Today the hub accepts the player's queue unconditionally — that is the
stranger-phase behaviour and will need gating.

### Refined 2026-07-31 by grilling — the hub is a window, and it has siblings

Two decisions settle how the player talks to a town:

1. **Strict reading of ruling 3 (Q1).** The hub becomes a **window**, not a
   command console. The town decides what to build; the player's leverage is
   *what he supplies*. Bring stone and it builds in stone; starve it of iron
   and it never earns a smithy. The hub shows the town's intent and takes the
   player's supply, not his orders. (This transitions in act 4 — see
   [`ROADMAP.md`](../02-roadmap/ROADMAP.md).)
2. **Multiple entry points (Q2).** There is no single god-block. The **rathaus**
   is a *building* — an NBT with levels, like the barracks — with a
   rathaus-block inside it. The Town Anchor ceases to be the sole entry.
   Conversation with citizens is a **secondary** channel (personal news,
   quests, gossip from specific NPCs). Scrolls and carried items are a
   **tertiary** channel — mobile access for a king who is not in town. A
   late-game ruler reaches his realm through all three; a stranger reaches one
   village through one.

## The honest scale problem (named, deferred)

The inspiration titles — Mount & Blade, Manor Lords — are **army-scale** war
games. Minecraft combat is **1v1**. The war half of the vision lives or dies on
bridging that gap, and the bridge is not designed yet. Candidate shapes (NPC
squads as aggregate units, abstracted siege resolution, auto-resolved battles
the player supplies but does not fight) are open. **This is the single hardest
design problem in act 5 and it is deliberately not solved here.** It is named
so it is not forgotten and not mistaken for trivial.

The player's own combat, by contrast, is settled: vanilla, unchanged, pillar
keeps its ban. The player supplies, orders, and watches wars; he does not win
them with his own sword.

## What this document is not

- Not a feature list, and not a commitment to build any of it on a date.
- Not a combat design, a diplomacy design, or a state-machine spec. Those come
  next, each its own document, each measured against this vision.
- Not a replacement for [`ROADMAP.md`](../02-roadmap/ROADMAP.md) — the act order and the
  "nothing has been verified in a running game" rule still hold. This document
  extends act 5 and reclassifies pillar 2; it does not rewrite the road.

## Status

**None of this is verified in a running game.** The stranger→guest→trusted
arc is built on a green build and contact sheets; the chief→king→realm arc is
design only. The first of it to be *finished* must be finished in the world,
same rule as the rest of the roadmap.

## Related

- [`PHILOSOPHY.md`](PHILOSOPHY.md) — the five pillars; pillar 2 and the hard
  bans are reclassified above.
- [`ROADMAP.md`](../02-roadmap/ROADMAP.md) — the act order; act 5 is expanded by this doc.
- [`ARCHITECTURE.md`](../04-engineering/ARCHITECTURE.md) — the state layer where `Realm` will
  land above `Town`.
