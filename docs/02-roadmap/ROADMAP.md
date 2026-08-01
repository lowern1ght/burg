# Burg — the road

Not a feature list. The order the mod becomes playable in, and the reasons for that order.

Written after measuring the state of the code rather than the state of the plan, which turned
out to matter: the mod's **middle** is built, its **deep end** is under construction, and its
**entrance does not exist**. A new player can spend hours in a world and never learn Burg is
installed. Everything below is ordered to fix that first.

## Three rulings that decide everything else

Taken by the owner, and they are constitutional — a proposal that contradicts one of them is
wrong, not merely different.

1. **The player FINDS the village.** He does not start in it. A settlement is therefore a reward
   for exploring, and it must have room around it to grow into. And where possible **an ordinary
   Minecraft village becomes one of ours**, rather than the mod placing a rival structure beside
   vanilla's.
2. **The player starts as a stranger and earns his way in.** Guest is not a formality, it is the
   key role. He is not the owner of the village until the village decides he is.
3. **The player only trades and supplies.** He never lays a block for the town and never fights
   for it. Every loop in this mod has to be legible through those two verbs.

### The author already wrote this

Two of the four shipped quests are `NOTE` type — notes written *by the villagers, about the
player*, and they call him **the stranger**:

> *"Since the stranger's arrival, something has changed. The builder is waking up earlier and new
> walls are rising. We should keep an eye on him"* — `new_visitor`

> *"The builder tend to acknowledge the stranger, he brought supplies to build our place. The
> builder never refused more help, but here that's another level"* — `trusting_someone_else`

That is ruling 2, in the datapack, before this roadmap existed. The voice of the mod is the
village talking about you behind your back. Keep it.

### The tension ruling 3 creates, unresolved

Today the player IS the town planner: he right-clicks the anchor and queues buildings. Ruling 3
says he only trades and supplies, and a planner is neither.

Two readings, and the choice changes acts 2–4:

- **Narrow.** Ruling 3 is about his hands — no building, no fighting — and the hub stays his. He
  decides, the town executes. Simplest, and it is what exists.
- **Strict.** The town decides what to build, and the player's leverage is *what he chooses to
  supply*: bring stone and it builds in stone, bring timber and it builds in timber, starve it of
  iron and it never earns a smithy. The hub becomes a window, not a command console.

The strict reading is a far better fit for rulings 2 and 3 together and for the long game where
you must *become* chief — but it means rebuilding the hub's purpose. **Not decided.**

---

## Act 0 — Arrival

*The player's verb: travel.*
**State: does not exist.** Nothing in the mod touches player spawn. `plains_town` generates
wherever its biome tag allows; the player appears at world spawn; the two are unrelated.

- **Convert a vanilla village.** The strongest lever available, because vanilla villages are
  already everywhere and already full of people and buildings. Conversion means: place an anchor
  at the meeting point, register a `Town`, mark the existing houses as occupied footprints so we
  never build over them, and enlist the villagers — the last of which already works
  (`Citizens.enlistAllNear`).
- **The hard part, named early: a converted village has no connectors we recognise.** Growth is
  pure jigsaw: a building attaches to a free `ConnectionPoint` carried by one of our own NBTs.
  Vanilla's pieces carry none, so a converted village has nowhere for the builder to attach and
  cannot grow at all. Three ways out, in preference order:
  1. **A bridgehead.** Place one of our street pieces at the village edge. It arrives with
     connectors, and the whole existing growth system works from there unchanged.
  2. Synthesise connection points around the perimeter by finding flat ground facing outward.
     More code, and it can point a building at a cliff.
  3. Treat the village as a blocked zone and grow beside it. Cheapest, and it reads as two
     villages rather than one that grew.
- **Room to build** is a placement condition, not a hope. A site check before registering: enough
  flat, unobstructed ground outside the core radius for the industry zoning will push out there.
- **Being found** wants a hint that is not a marker on a screen. Smoke, a road, a traveller met on
  the way. Cheapest honest version first: make sure a converted village near spawn is common
  enough that the first one is found by accident.

## Act 1 — Stranger

*The player's verb: look, and speak.*
**State: nothing to speak to.** `mobInteract` is unimplemented on every entity we own. Right-
clicking a citizen does nothing at all — forty walking bodies that cannot be addressed.

- **Right-click a person.** The single highest-value missing thing in the mod, and cheap. Name,
  trade, skill, what they are doing right now. This is what turns a crowd into people.
- **The anchor does NOT open the hub for a stranger.** It tells him whose village this is and
  that he is not of it. Ruling 2 has to bite on the first click or it is decoration.
- The notes about him begin. `new_visitor` already exists and already fires on stock conditions;
  it should fire on *him*, and be delivered as something a person says rather than a line in a
  menu.

## Act 2 — Guest

*The player's verb: trade.*
**State: the machinery exists, the gate does not.** Hub trading, stock, prices and quests are all
built. There is no per-player standing anywhere — `Town` holds only `chatSubscribers`.

- **Standing, per player.** The immigration gate already derives a town-wide standing from whether
  the last feeding fed everyone; a player's standing is a second, separate thing and has to be
  stored. It is then load-bearing three times: who may open the hub, whether settlers come, and
  eventually whether you can command anyone.
- **Trade with a person, not only with a menu.** `Merchant` is a standalone interface — verified
  from the jar, twelve methods and a default `openTradingScreen` — so `Npc implements Merchant`
  gives vanilla's real trade screen, offers by trade, no imitation. This is also where **gold
  replaces emeralds**: `MerchantOffer` takes an arbitrary `ItemCost`, so nothing fights it. Two
  denominations, nugget and ingot, because a single unit makes everything cheap cost the same.
- Supplying materials is how standing rises. `stone_supplies` and `wood_supplies` already are
  exactly this task; they need to be a ladder rather than two entries.

## Act 3 — Trusted

*The player's verb: choose.*
**State: built, and reachable too early.** The hub, the construction queue, upgrades, era
advancement and the era branch are all working — they are simply available from the first click.

- The hub opens. What it opens *into* depends on the narrow-versus-strict reading above.
- **The era branch is the mod's strongest asset and is currently invisible.** Sixteen era defs in
  four tiers: `agricultural / industrial / pastoral`, then `cooking / forge / ranching / rural /
  urban`. The player never learns he is choosing a path. Surfacing that choice — as a decision the
  village asks him to weigh in on — is worth more than any new building.

## Act 4 — A town, and then a wall

*The player's verb: keep choosing.*
**State: the pieces exist and are not chained.** Military NBTs, `checkVillageFullTransition`,
zoning and street growth are all in. Nothing joins "the village is full" to "it becomes a town"
to "it builds a wall".

- Zoning already does the honest half: industry is pushed outside the core radius, and the town
  lays a road out to it rather than stalling. `military` is deliberately `ANY` — the wall goes
  where the ground allows — and eventually **the wall ring should BE the core boundary** instead
  of a flat 32-block radius, since a wall already knows where inside is.
- Walls, gates and towers are built and style-gated but have never been chained to an era.

## Act 5 — The far end

*The player's verb: rule, and negotiate.*
**State: nothing.**

- A chief: a named citizen who speaks for the town, so diplomacy has someone to be between.
- An army from the barracks; the training yard already exists.
- Child towns that specialise — one farms, one logs. **Zoning is the mechanism this will be
  expressed in**, which is why it came first: a child town is a second anchor with a small core
  and an outer zone of one industry.
- Diplomacy between chiefs.

---

## Cross-cutting, and where it sits

| thread | state | act it belongs to |
|---|---|---|
| people who are people, not villagers | building | 1 |
| hand-drawn bodies at reference density | building | 1 |
| a schedule — sleep at dusk, work by day | not started | 1 |
| idlers: wander, talk, swim, hunt, and die of it | not started | 1 |
| births needing a man, a woman and a spare bed | not started | 2 |
| gold in two denominations | not started | 2 |
| the pub | not started | 2 |
| a per-worker skill raising output | **done** | 3 |
| settlers arriving rather than spawning | **done** | 2 |
| zoning, people in / industry out | **done** | 4 |

## The rule this document is under

**Nothing on this road has been verified in a running game.** Every commit behind it went in on a
green build and a contact sheet. The first act to be *finished* rather than written must be
finished in the world, with a player walking into a village and finding a person to talk to.
