# Excerpts — official NeoForge 1.21.1 docs (verbatim, short)

Short verbatim excerpts for teaching/verification, each with source. All from
**NeoForge docs, version 1.21.1** (MIT-licensed, © NeoForged). Read the full
page via the link before relying on details beyond the quote.

---

## 1. Sides — logical vs physical

> The logical server is where the game logic runs. Things like time and
> weather changing, entity ticking, entity spawning, etc. all run on the
> server. [...] The logical client, on the other hand, is responsible for
> displaying everything there is to display.
>
> If a logical server can work with your code, that alone doesn't guarantee
> that a physical server will be able to work with as well. This is why you
> should always test with dedicated servers to check for unexpected behavior.
>
> In the NeoForge codebase, the physical side is represented by an enum called
> `Dist`, while the logical side is represented by an enum called
> `LogicalSide`.

And on the two checks:

> Querying this field on a `Level` object establishes the **logical** side
> the level belongs to. [...] `FMLEnvironment.dist` is the **physical**
> counterpart to a `Level#isClientSide()` check.

Source: <https://docs.neoforged.net/docs/1.21.1/concepts/sides> — NeoForge 1.21.1

---

## 2. SavedData — Factory pattern and setDirty

> Each SD implementation must subtype the `SavedData` class. There are two
> important methods to be aware of:
>
> - `save`: Allows the implementation to write NBT data to the level.
> - `setDirty`: A method that must be called after changing the data, to
>   notify the game that there are changes that need to be written. If not
>   called, `#save` will not get called and the original data will remain
>   unchanged.

> `DimensionDataStorage#computeIfAbsent` takes in two arguments. The first is
> an instance of `SavedData.Factory`, which consists of a supplier to
> construct a new instance of the SD and a function to load NBT data into a SD
> and return it. The second argument is the name of the `.dat` file [...]

> If a SD is not specific to a level, the SD should be attached to the
> Overworld, which can be obtained from `MinecraftServer#overworld`. The
> Overworld is the only dimension that is never fully unloaded and as such
> makes it perfect to store multi-level data on.

Registration shape (abridged from the same page):

```java
dataStorage.computeIfAbsent(
    new Factory<>(ExampleSavedData::create, ExampleSavedData::load),
    "example");
```

Source: <https://docs.neoforged.net/docs/1.21.1/datastorage/saveddata> — NeoForge 1.21.1

---

## 3. Networking — payload registration overview

> Payloads are a way to send arbitrary data between the client and the
> server. They are registered using the `PayloadRegistrar` from the
> `RegisterPayloadHandlersEvent` event.

> The registrar has `play*` methods, that can be used for registering payloads
> which are sent during the play phase of the game. [...] The registrar uses a
> `*Bidirectional` method [...] The type of the payload is used as a unique
> identifier for the payload. The stream codec is used to read and write the
> payload to and from the buffer [...] The payload handler is a callback for
> when the payload arrives on one of the logical sides.

Registration shape (abridged from the same page):

```java
@SubscribeEvent // on the mod event bus
public static void register(final RegisterPayloadHandlersEvent event) {
    final PayloadRegistrar registrar = event.registrar("1");
    registrar.playBidirectional(
        MyData.TYPE,
        MyData.STREAM_CODEC,
        new DirectionalPayloadHandler<>(
            ClientPayloadHandler::handleDataOnMain,
            ServerPayloadHandler::handleDataOnMain));
}
```

Payload size limits from the same page: to-client payloads max **1 MiB**,
to-server payloads max **32 KiB**. Handlers run on the main thread by default;
`context.enqueueWork(...)` marshals from the network thread and unhandled
exceptions in the returned future are **silently swallowed**.

Source: <https://docs.neoforged.net/docs/1.21.1/networking/payload> — NeoForge 1.21.1
