# Economy — production, trade, gold, supply — design

The flows that make a village and then a realm run: what gets produced, what
the player trades for it, what currency prices it, and how the player's
supply choices steer construction. Acts 0–3 build the per-town half; act 5
needs a realm-level half. The 2026-07-31 grilling settled the currency (gold,
two denominations), dynamic pricing, NPC merchants, and caravans; the
supply-vs-hub question, skill transfer, and inflation mitigation remain open.

## Decisions

1. **Gold is the universal currency, in two denominations (nugget + ingot),
   and it is not hidden.** Gold replaces emeralds (ROADMAP act 2 goal, not yet
   implemented). Two denominations because `MerchantOffer` takes an arbitrary
   `ItemCost` and a single denomination makes everything cheap cost the same —
   the nugget prices small trade, the ingot prices large and era costs.
   Vanilla items, no custom currency to maintain.

2. **Prices are dynamic, driven by supply and demand, and visible to the
   player.** Every tradable item has a price, recalculated periodically from
   how much the village produced, how much it consumed, how much the player
   bought or sold, seasonality, and events (famine → food ×5). Prices are
   visible — a price board in the rathaus, NPC merchants quoting the current
   price — never a hidden number.

3. **NPC merchants offer trades at the dynamic price with a per-merchant
   spread.** Individual NPCs at the market offer trades at the current price
   plus a small per-merchant spread (each slightly cheaper or pricier than the
   base — competition). The player trades with a person, not only a menu. This
   is where `Npc implements Merchant` (ROADMAP goal) lands.

4. **Caravans between settlements equalize prices — the realm-level flow.**
   When the player is king he can send caravans (metropolis ↔ colony,
   [realm stub](../realm/README.md)). A caravan carries goods and equalizes
   prices across the network: wood is cheap in the forest colony and expensive
   in the capital, so the caravan profits on the spread. This is the arbitrage
   mechanic for a trading-focused player, and it is the realm-level economic
   flow (no taxes, no shared stock).

## Open questions

1. **"Supply steers what gets built" — narrow vs strict vs hybrid.** ROADMAP
   act 3 names this tension and defers it. *Why it matters:* the strict reading
   (bring stone → it builds in stone; starve it of iron → no smithy) is a
   better fit for the long "become chief" game but rebuilds the hub's purpose.
   - **Narrow:** ruling 3 is about the player's hands only; the hub stays his,
     he decides, the town executes. Simplest, and what exists.
   - **Strict:** the town decides what to build; the player's leverage is
     *what he chooses to supply*. The hub becomes a window, not a console.
   - Hybrid: the queue is the player's, but each entry checks a "can we build
     this in the supplied material" gate and downgrades or stalls.

2. **Per-worker skill transfer.** Skill is done (ROADMAP cross-cutting table).
   Open: does skill transfer with the worker (a soldier's barracks skill) or
   stay with the building? *Why it matters:* war and capture both move people
   between towns. Leading option: skill stays with the building (a barracks
   trains whoever is assigned); capture does not strip the garrison of skill.

3. **Inflation mitigation, now that gold is the single mineable currency.**
   Gold nuggets are mineable by the player and by NPC builders (the builder
   mines per `jobs/builder.json`). *Why it matters:* a single fiat currency
   with an unbounded supply source inflates, and the two-currency option is
   now rejected (gold is universal). Open: cap gold throughput from mining and
   sink it through trade and era costs.

## Dependencies

- **Needs:** the `Realm` layer for caravans ([realm stub](../realm/README.md));
  the nugget/ingot currency before the trade screen is finished (ROADMAP act
  2).
- **Blocks:** the supply-vs-narrow decision (question 1) blocks the hub
  redesign and the act 3 "trusted" feel; tribute (diplomacy) blocks on the
  currency decision, now settled.
- Per-worker skill already done; verify it survives the war/capture flows once
  those land.

## Status

`design settled 2026-07-31 (pending implementation)` for the currency,
dynamic pricing, NPC merchants, and caravans. Acts 0–3 per-town production,
stock, trade prices and per-worker skill are **built** (ROADMAP cross-cutting
table) but not verified in a running game; the supply-vs-hub, skill-transfer,
and inflation questions above are scoped for a later pass.

## Related

- [VISION](../../01-vision/VISION.md) — supply as the player's leverage,
  tribute as a relation
- [ROADMAP](../../02-roadmap/ROADMAP.md) — act 2 gold, act 3 supply tension,
  cross-cutting table
- [ARCHITECTURE](../../04-engineering/ARCHITECTURE.md) — `ProductionManager`,
  `TradePriceDataHandler`
- [../realm/README.md](../realm/README.md) — realm-level flows (caravans)
- [../diplomacy/README.md](../diplomacy/README.md) — tribute as a flow
