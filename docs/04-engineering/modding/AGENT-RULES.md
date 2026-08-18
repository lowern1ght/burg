# Agent rules — NeoForge 1.21.1 coding on Burg

Hard rules for LLMs writing mod code in this repo. Violations crash dedicated
servers, corrupt saves, or break the DDD boundary. Each rule links the pinned
1.21.1 doc for depth.

1. **Sides: logical vs physical.** `Level#isClientSide()` reports the
   **logical** side; `FMLEnvironment.dist` reports the **physical** side.
   Singleplayer runs a logical server inside the physical client. Game logic
   only when `!level.isClientSide()`. **Always verify on a dedicated server**
   (`runServer`) — client-class references that sneaked into common code only
   explode there (`NoClassDefFoundError`).
   <https://docs.neoforged.net/docs/1.21.1/concepts/sides>

2. **No game logic on the client.** The client displays; the server decides.
   To move data between sides, define a `CustomPacketPayload` + `StreamCodec`
   and register it in `RegisterPayloadHandlersEvent` (mod event bus). Send via
   `PacketDistributor`. Never mutate world/inventory state from client code
   and hope it syncs back.
   <https://docs.neoforged.net/docs/1.21.1/networking/payload>

3. **SavedData must be marked dirty.** Call `setDirty()` after every mutation
   or `save()` is never invoked and changes silently vanish on world reload.
   Data not specific to one level (multi-level / global data) must be stored
   via the **overworld** (`MinecraftServer#overworld()` data storage) — it is
   the only dimension never fully unloaded.
   <https://docs.neoforged.net/docs/1.21.1/datastorage/saveddata>

4. **Datapack namespace is `burg`.** All data/assets live under
   `data/burg/` / `assets/burg/`. Never hardcode resource strings — build ids
   with `ResourceLocation.fromNamespaceAndPath("burg", path)` (no `new
   ResourceLocation(...)`, which is gone in 1.21).
   <https://docs.neoforged.net/docs/1.21.1/resources/>

5. **Do not invent Forge 1.20 / NeoForge-late APIs.** This is NeoForge
   **21.1** on Minecraft **1.21.1**. Differences that bite:
   `DeferredRegister` + `RegisterEvent`/`NewRegistryEvent` flow differs from
   1.20 Forge; `@EventBusSubscriber` on 1.21.1 takes `bus = EventBusSubscriber.Bus.MOD`
   or `Bus.GAME` (mod bus = registration/lifecycle, game bus = gameplay
   events) — a handler on the wrong bus silently never fires; networking is
   `RegisterPayloadHandlersEvent` + `PayloadRegistrar`, not 1.20.x
   `SimpleChannel`. When unsure, check the pinned 1.21.1 doc, and trust the
   compiler over memory.
   <https://docs.neoforged.net/docs/1.21.1/concepts/events>

6. **Domain packages stay Minecraft-free (ADR-0008).** Nothing under
   `org.lowern1ght.burg.domain` (and `application` ports) may import
   `net.minecraft.*` or NeoForge types — no `Level`, no NBT, no
   `ResourceLocation`. MC types appear only behind infrastructure adapters
   and value-object wrappers (`TownId`, `BlockCoord`). A domain test must run
   on a bare JVM. [ADR-0008](../../06-decisions/ADR-0008-ddd-foundation.md)

7. **Agents never launch the game.** No `./gradlew runClient` / `runServer` /
   `runData` from agent sessions — long-lived processes are banned by
   agent-runtime safety. Builds (`./gradlew build`, `compileJava`) are fine;
   in-game verification is done by the owner.
