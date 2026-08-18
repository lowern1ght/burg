# Diplomacy — relations between realms — design

The web of relations between realms once more than one exists: war, truce,
alliance, tribute, embargo ([VISION](../../01-vision/VISION.md)). None of the
machinery is built; the 2026-07-31 grilling settled the decision model (player
proposes, AI-chief decides), the relation set, chief personality, and that
the player acts through the chief NPC rather than a diplomacy menu. Open:
tribute rate/cycle, alliance effects on autonomy, and whether neutrals
self-organise.

## Decisions

1. **Player proposes, AI-chief decides (Hybrid C).** The player proposes — via
   a messenger NPC or in person — and the AI-chief decides from a model of the
   player's strength, reputation, and what is offered. The player's levers are
   concrete: offer tribute (pay to agree), threaten force (show the army →
   fear), exchange resources. No negotiation console; the proposal is an act,
   the chief's answer is an act back.

2. **Relations are war, truce, alliance, tribute, and embargo.** Drawn from
   VISION and the Villager Recruits faction system. These are the discrete
   states; they compose (an alliance can carry a tribute obligation; a truce
   can expire into war), implemented as orthogonal flags rather than a single
   continuous value so the verbs stay legible.

3. **AI-chiefs have personality traits that drive their decisions.** A chief
   reacts differently by temperament — cautious, ambitious, loyal, treacherous
   — set at promotion. A cautious chief takes tribute; an ambitious one wants
   land; a treacherous one breaks truces. This is what keeps neighbouring
   realms from reading as copies of the player's logic.

4. **The player-as-king acts through the chief NPC and messengers, not a
   diplomacy menu.** The player *is* the ruler of his realm, but he acts by
   talking to his own chief NPC and sending messengers, the same conversation
   surface as the rest of the game (pillar 5). There is no hub diplomacy tab;
   the relationship is managed through people.

## Open questions

1. **Tribute flow rate and cycle.** The medium is gold
   ([economy stub](../economy/README.md), Q11); the periodicity and percentage
   are not. *Why it matters:* tribute is the economic face of an asymmetric
   relationship and has to bite without being a micromanagement chore.

2. **How alliances affect member village autonomy.** VISION's autonomy slider
   is per-village; an alliance is realm-to-realm. *Why it matters:* the two
   scales can contradict — does my ally's captured village count as "mine" for
   any command purpose? Leading option: alliances grant no command rights over
   the ally's villages; only the ruler holds his own.

3. **Whether neutral villages can self-organise into AI realms.** VISION says
   free villages are "pure pillar 1," but a map of only player-ruled realms is
   dead. *Why it matters:* AI realms are the player's peers and opponents in
   act 5. Leading option: yes — free villages can self-organise into AI-ruled
   realms over time, giving the player rivals without scripting them.

## Dependencies

- **Needs:** the `Realm` layer and its `relations` field
  ([realm stub](../realm/README.md)); chief NPCs who can speak for a town
  ([npc stub](../npc/README.md)); war resolution if war is a relation state
  ([war stub](../war/README.md)).
- **Blocks:** nothing strictly blocks on diplomacy, but act 5's "rule, and
  negotiate" verb is hollow without it.

## Status

`design settled 2026-07-31 (pending implementation)` — the Hybrid-C decision
model, the relation set, chief personality, and the no-menu surface are
decided. Tribute rate/cycle, alliance-autonomy interaction, and neutral
self-organisation remain open.

## Related

- [VISION](../../01-vision/VISION.md) — §"immediate architecture consequence"
  (relations on Realm), §"the far end"
- [ROADMAP](../../02-roadmap/ROADMAP.md) — act 5 diplomacy between chiefs
- [../realm/README.md](../realm/README.md) — the Realm abstraction
- [../npc/README.md](../npc/README.md) — chief as speaker for the town
- [../economy/README.md](../economy/README.md) — tribute as a flow
