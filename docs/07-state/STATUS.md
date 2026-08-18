# Burg — subsystem status

Per-subsystem tracker. Three states: `build-green` (compiles, contact sheets render, no
in-game verification), `verified-in-game` (a real player has walked through it in a running
world), `broken` / `partial` (known not to work), `not-started` (design only or absent).

This doc exists because of [`ROADMAP.md`](../02-roadmap/ROADMAP.md)'s closing line:
**"Nothing on this road has been verified in a running game."** Every commit behind the mod
went in on a green build and a contact sheet. [`VISION.md`](../01-vision/VISION.md) repeats
the same rule for the chief→king→realm arc. The gap between "builds" and "works in a world"
is the whole point of this table — rows default to `build-green`, not to `verified-in-game`.

## Subsystems

Rows grouped by layer. "Notes" cite the evidence (commit, checker, doc line) and the known
gap. Absence of a `verified-in-game` row is intentional.

### Engine layers

| subsystem | state | notes |
|---|---|---|
| state (`town/Town`, `LevelTowns`, SavedData) | build-green | God-object ~1000 LOC (ARCHITECTURE.md §state). Per-player standing absent — `Town` holds only `chatSubscribers` (ROADMAP Act 2). `Realm`/`Kingdom` layer above `Town` does not exist (VISION.md). |
| tick (`TickScheduler`, `ProductionManager`, `FoodManager`, quests, era) | build-green | Production, food, quest-spawn, era-advance all wired (ARCHITECTURE.md §tick). `EraManager.tick` is a no-op; era advance is player-triggered. |
| ai (`Npc`, `BuildGoal`, state machine, `OuatWalkNodeEvaluator`) | build-green | Builder places blocks block-by-block with burst rhythm; opens gates/doors (ARCHITECTURE.md §ai). `DemolitionAction`/`RepairAction` are future stubs. |
| datapack (buildings / eras / quests / jobs / config loaders) | build-green | Author `plains` corpus reads 125/125 after byte-exact CRLF repair (CLAUDE.md). Five JSON loaders reload on server start. Game loop over them unverified. |
| worldgen (`plains_town` jigsaw, terrain matcher) | build-green | Plains/meadow village replacement landed (`a76bbea`). Vanilla-village **conversion** not wired — Act 0's bridgehead/connector problem is open. Player spawn does not lead to a village. |
| client (`TownHubScreen`, `NpcRenderer`, draggable widgets, layers) | build-green | Black-screen regression fixed (`a6ada7f`, a layer baked but never registered). Widget positions persist per-session only — known tech debt. |
| network (17 packets, `CustomPacketPayload`) | build-green | Ported to NeoForge 1.21.1 `CustomPacketPayload` (`51d1f11`). All hub/stock/quest/era/citizen/log packets wired. |

### Content layers

| subsystem | state | notes |
|---|---|---|
| military-content (fortifications: wall, gate, `wall_tower`, watchtower, barracks, armory, training_yard) | build-green | Style-gated + usability-gated; `selfgate.py` green. Done since the open list: ramp dither in `wall.py`, `wall_tower` ladder, watchtower enterable+climbable all 7 levels (was NO-STAIR on 6), `armory_lvl2` ground floor 30/45→46/46. Open: watchtower crown reads heavy (looks), `watchtower_lvl3`/`lvl4` emit `EMPTY_TOP=1`, fortification style not yet applied to barracks/armory/training_yard. See OPEN-WORK.md. |
| livestock-content (cattle / sheep / pig farmsteads) | build-green | Gates green; `build_livestock.py` fails a rung on any fault (fourth gate). Open: three yards share devices (similarity 0.86–0.87 vs author 0.79), `pasture.py` palette decoupled from `wall.py`. See OPEN-WORK.md. |
| npc-skins (hand-drawn bodies, hair-as-paint, wealth-as-dye) | build-green | 14 bodies shipped at reference density (`d8cfdfe`, `7c85b85`). Two past decisions shipped wrong and were fixed — "cringe" + a black screen (burg-skins SKILL). Poses added: sit, doze, talk (`a956fec`). Crown-groove leak fixed (`3729f34`). |
| npc-schedule (sleep at dusk, work by day) | build-green | "The day decides what a person does, and they sleep in a real bed" (`1d8b63b`). ⚠ ROADMAP cross-cutting table still marks schedule `not started` — that line is stale relative to the commit. |
| npc-interaction (right-click to speak, trade in gold) | build-green | "A person can be spoken to, and traded with, in gold" (`adc4a26`). ⚠ ROADMAP Act 1 still says `mobInteract` is unimplemented on every entity — that line is stale relative to the commit; in-game behaviour unverified either way. |
| npc-idlers (wander / talk / swim / hunt / die of it) | partial | Poses (sit/doze/talk) landed (`a956fec`); full idler set per ROADMAP cross-cutting still `not started`. |
| npc-skill (per-worker skill raising output) | build-green | ROADMAP cross-cutting marks `done` — meaning built; in-game verification still pending under the closing-line rule. |
| settlers (arrive rather than spawn) | build-green | ROADMAP cross-cutting marks `done` (built). `Citizens.enlistAllNear` already worked pre-roadmap. |
| births (man + woman + spare bed) | not-started | ROADMAP cross-cutting `not started`, act 2. |
| gold (two denominations: nugget + ingot) | build-green | Trade-in-gold landed (`adc4a26`); `MerchantOffer` takes arbitrary `ItemCost`. ROADMAP cross-cutting line stale. |
| the pub | not-started | ROADMAP cross-cutting `not started`, act 2. |
| zoning (people in / industry out) | build-green | ROADMAP cross-cutting marks `done` (built). Industry pushed outside core radius; road laid out to it. |
| era branch surfacing | build-green | 16 era defs in 4 tiers shipped (ARCHITECTURE.md §datapack). ROADMAP Act 3: the branch is "the mod's strongest asset and is currently invisible" — the choice is never surfaced to the player. |
| i18n (en_us, ru_ru) | build-green | ru_ru added (`b9402bd`); era labels, hub tooltips, quest widgets, chat, commands, anchor all through translatable keys. |

## What "verified-in-game" would take

Per ROADMAP, the first act to be *finished* rather than written must be finished in the
world. Concretely: a player spawns, travels, and **finds** a village (Act 0); right-clicks a
citizen and **gets a response** — name, trade, what they are doing (Act 1); the anchor does
**not** open the hub for a stranger, and the notes about him begin. That single walk is the
bar that moves the first rows out of `build-green`. Nothing less counts.

## How to update

Move a row's state forward only after evidence, and link the evidence (commit SHA,
screenshot description, or session note) in the row's notes. A green build is not evidence;
a contact sheet is not evidence. `build-green → verified-in-game` requires a recorded
in-world walk.

## Related

- [`ROADMAP.md`](../02-roadmap/ROADMAP.md) — the act order and the closing-line rule this doc enforces.
- [`VISION.md`](../01-vision/VISION.md) — the earned-crown arc; same "not verified in a running game" status line.
- [`ARCHITECTURE.md`](../04-engineering/ARCHITECTURE.md) — the subsystem map these rows are derived from.
- [`OPEN-WORK.md`](OPEN-WORK.md) — the backlog this status feeds.
