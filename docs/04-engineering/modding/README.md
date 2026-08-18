# NeoForge 1.21.1 modding knowledge pack

Curated, LLM-friendly reference for coding Burg against **NeoForge 21.1.x /
Minecraft 1.21.1 / Java 21**. Agents: read this instead of guessing APIs.

## When to read what

| Situation | Read |
|---|---|
| You are about to write or review any mod Java code | [AGENT-RULES.md](AGENT-RULES.md) first — the footguns, ~2 min |
| You need the canonical URL / cheat-sheet for a subsystem (sides, events, registries, networking, saved data, NBT, codecs) | [INDEX.md](INDEX.md) |
| You are touching client/server sync or persistence and want the exact official wording | [EXCERPTS.md](EXCERPTS.md) — short verbatim quotes with sources |
| You need deeper detail than the excerpt | Follow the permanent link from INDEX.md into the official 1.21.1 docs |

## Ground truth

- **Official docs (pinned to our version):** <https://docs.neoforged.net/docs/1.21.1/gettingstarted/>
- **This repo's stack:** Minecraft `1.21.1`, NeoForge `21.1.x`, Java 21, mod id `burg`
  (see `gradle.properties`; mappings note in [INDEX.md](INDEX.md)).
- **This repo's architecture:** [../ARCHITECTURE.md](../ARCHITECTURE.md) and
  [ADR-0008](../../06-decisions/ADR-0008-ddd-foundation.md) (domain packages are Minecraft-free).

## Version warning — read this before any web search

The NeoForge docs you will find by search or by LLM memory are often for
**newer NeoForge (26.x / 1.21.x-late)**. Those versions changed or removed
APIs we still use (e.g. `RegisterPayloadHandlersEvent` era networking vs the
newer systems, registry and event-bus changes). **Only trust pages whose URL
contains `/docs/1.21.1/`.** The 1.21.1 docs pages carry a banner saying the
version is "no longer actively maintained" — that is fine; *we* are pinned to
it. Every link in [INDEX.md](INDEX.md) is a 1.21.1 permanent link.

Rule of thumb: if an API name from a newer doc does not compile against
NeoForge 21.1.77, believe the compiler and the 1.21.1 docs, not the newer page.
