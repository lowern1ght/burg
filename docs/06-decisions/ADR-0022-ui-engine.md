# ADR-0022: bare-JVM UI engine + immediate-mode widgets

- **Status**: Accepted
- **Date**: 2026-08-19
- **Decided by**: owner (foundation carve request)

## Context

`TownHubScreen` is a 1000+ line concrete subclass of
`AbstractContainerScreen` that mixes Minecraft rendering (`GuiGraphics` /
`PoseStack` / `Font`) with construction-mode business logic — three tabs,
an inventory grid, a map widget, an era widget, an NBT preview, and an
expanded-view overlay. The act-4 SUPPLY mode (ADR-0019, ADR-0021's
read-only window) needs a second screen whose entire job is to draw
two rows: the construction-intent roll and the stock-gap roll. Building
that screen on top of the legacy `TownHubScreen` would mean either
forking another `AbstractContainerScreen` (and inheriting the same
1000-line tax) or rewriting `TownHubScreen` from scratch. Neither is
the foundation act-5 wants.

What we need is a Minecraft-free UI engine under `common/` so the
widgets run on a bare JVM, tests pin their behaviour without booting
Minecraft, and the act-4 SUPPLY-mode screen is a small adapter over a
real engine instead of a thin wrapper over a brittle direct draw.
ADR-0008 set the layering: only `infrastructure/` may import
`net.minecraft.*`. The engine lives under `common/` and is constrained
to never import Minecraft — the same import fence `DomainPurityTest`
already enforces on `domain/` and `application/`.

## Decision

Build a tiny immediate-mode UI engine under
`common/.../common/ui/`, integrate it via a per-screen adapter in
`neoforge/.../client/ui/`, and ship the first widget — the act-4
SUPPLY-mode intent list — as the foundation the next carves can grow
on.

### Engine shape — `common/.../common/ui/`

The engine is eleven types, ~700 lines of code, all POJO value types:

| Type | Role |
|------|------|
| `Rect` | `(int x, int y, int w, int h)` value type. `contains(Point)`, `inset(int)`, `intersection(Rect)`, `isEmpty()`, `translate(int, int)` / `translate(Point)`. |
| `Point` | `(int x, int y)` value type. `add(Point) / add(int, int)`, `sub(Point) / sub(int, int)`. |
| `Color` | 32-bit ARGB value type. `rgb(int, int, int)`, `rgba(int, int, int, int)` (clamp channels into `[0, 255]`), `lerp(Color, float)` (clamp `t` into `[0, 1]`), channel getters `alpha/ red/ green/ blue()`. Sentinels `BLACK`, `WHITE`, `TRANSPARENT`. |
| `TextStyle` | `(Color fill, Color text, int flags)` value type. `defaults()` factory returns the engine's neutral style (transparent fill, no flags). Bold flag bit reserved for `withBold(boolean)`. |
| `UiEvent` | Sealed interface: `MouseMoved`, `MouseDown`, `MouseUp`, `KeyDown`, `KeyUp`, `CharTyped`, `Scroll`, `Resize`. Mouse coords are in GUI-space (the adapter normalises screen-pixel offsets to the `Root`'s bounds). |
| `Widget` | Abstract base class. `Rect bounds`, abstract `draw(DrawContext)`, default `handle(UiEvent): boolean` returns `false`. State: `visible`, `enabled`, `hovered`, `focused`. Protected hooks: `onEntered()`, `onExited()`, `onFocusGained()`, `onFocusLost()`. |
| `DrawContext` | The draw surface handed to every `draw` call. `Rect clip`, `mouseX/mouseY/mouse()`. `pushClip(Rect)` / `popClip()` maintain a stack. `drawText(String, int, int, TextStyle)` and `drawRect(int, int, int, int, Color)` are no-ops in the base — the adapter overrides them. |
| `Root` | Top-level widget. `List<Widget> children`. `add(Widget)`, `remove(Widget)`, `layout(int, int)`, `draw(DrawContext)`, `dispatch(UiEvent)`. Hit-test walks children in reverse-add order; focused widget gets keys; mouse-down grants focus. |
| `Container` | A widget with children + a layout direction (`HORIZONTAL`, `VERTICAL`, `OVERLAY`) + `spacing`. `layout(int, int)` places children along the direction. Recursive hit-test. |
| `Label` | A read-only text widget — `(String text, TextStyle style)`, drawn at its top-left. |
| `Panel` | A filled rectangle (background + optional 1-pixel border) + children drawn over the rectangle. `setBackground(Color)` / `setBorder(Color)` for hover / focus overlays. |

The engine is intentionally tiny: no scene graph, no retained tree,
no preferred-size protocol. A leaf widget's size is its own concern
(its bounds are set when it's added); a container's size is the sum
of its children's sizes; a screen's size is whatever `Root.layout(w, h)`
gets called with. Adding more sophisticated layout (constraints,
anchors, etc.) is a deliberate follow-up — the spec keeps the engine
small enough that every screen today writes its layout by hand.

### First widget — `common/.../settlement/ui/`

`SupplyIntentList` is a record that carries two rolls:
`List<IntentItem> items` (pending construction intents, each with a
`Map<ItemId, Integer> inputsMissing`) and `List<StockGapItem> gaps`
(stock-gap roll summed across intents). `IntentItem` and `StockGapItem`
are nested records inside `SupplyIntentList` — the file
`SupplyIntentList.java` defines the public record plus the two nested
records (the file-naming rule allows nested types).

`SupplyIntentList.computeGaps(intents, onHand)` is the canonical
gap-math the data layer would otherwise repeat: sums every intent's
`inputsMissing` into one entry per `ItemId`, subtracts the town's
on-hand count, and floors `missing` at zero.

`SupplyIntentListWidget` (in the same package, separate file — one
public type per file) renders the data into a two-zone tree:
intent rows on top (sorted alphabetically by `buildingDefId`,
case-insensitive on the canonical lower-case form), stock-gap rows
below (sorted by `ItemId`). Empty intent list shows a single label
whose text is the lang key `burg.message.hub.supply.no_intent` — the
engine never sees a translated string; the adapter resolves the key
at draw time. Hovering an intent row highlights the matching gap
row(s) — every gap whose `ItemId` appears in the hovered intent's
`inputsMissing` map. Highlight colour is a fixed `HIGHLIGHT_TINT`
`Color.rgba(255, 220, 100, 80)`; the Minecraft adapter can replace
it with a sprite if it wants.

### Minecraft adapter — `neoforge/.../client/ui/`

| Class | Role |
|-------|------|
| `McDrawContext` | Extends `DrawContext`. Constructor takes a `GuiGraphics`, a `Font`, the GUI-space origin offset (`Screen#leftPos / topPos`), the parent's width/height, and the cursor position. `drawRect` calls `guiGraphics.fill(...)` with the engine's `Color.argb` (same byte order). `drawText` translates lang keys (`namespace.path` shape) via `Component.translatable(...)` and plain strings via `Component.literal(...)`. `pushClip` / `popClip` call `guiGraphics.enableScissor(...)` / `disableScissor()` with screen-pixel coordinates. |
| `McInputAdapter` | Static factories only: `mouseMoved`, `mouseDown`, `mouseUp`, `keyDown`, `keyUp`, `charTyped`, `scroll`, `resize`, plus an `at(guiX, guiY)` helper returning a `Point`. No state, no allocations. |
| `TownHubScreenV2` | Extends `Screen` (not `AbstractContainerScreen` — the act-4 hub has no inventory). `render(GuiGraphics, int, int, float)` builds the `McDrawContext`, lays the `Root` out, and calls `intentList.draw(ctx)`. Mouse / keyboard / scroll callbacks translate via `McInputAdapter` and dispatch into the engine. Mod-loading wiring (the `MenuType` → `Screen` registration) is **not** in this PR — a follow-up. |

The screen compiles and can be instantiated in-process (e.g. from a
test); it does not need a `MenuType` to compile.

## Three rules (the engine's discipline)

1. **Engine has zero `net.minecraft` imports.** `DomainPurityTest`
   walks every `.java` under `common/.../common/ui/` and fails the
   build on any `import net.minecraft.*` or `import net.neoforged.*`.
   The engine is the same Minecraft-free layer as `domain/` and
   `application/` — the test already extends cleanly into the UI
   types because the existing fence covers the whole `common/`
   module.
2. **Every Widget is sealed-final or abstract with no public fields.**
   `Widget`, `Root`, `Container`, `Label`, `Panel`,
   `SupplyIntentListWidget` are `public final` (or `public sealed`
   / `public abstract`); mutable state goes through setters. The four
   state flags (`visible`, `enabled`, `hovered`, `focused`) are
   `public boolean` for hot-path access from the engine but the
   setters are package-private — the engine updates them, widgets
   read them. This is the same discipline the project already applies
   to domain value objects (`sealed` interfaces, `final` records) and
   to controllers (`final sealed` — see `api-design.md`).
3. **The engine never holds a Minecraft client reference.** The
   `Widget.handle(UiEvent)` signature accepts only engine types; the
   `Root.dispatch(UiEvent)` does not see Minecraft's input. A screen
   that wants to call a Minecraft method (open a menu, send a packet)
   listens on a widget's `handle` return value, and the widget returns
   a domain-level "I was clicked" signal — the screen translates the
   signal into the Minecraft call. The engine's call graph is one-way
   and crosses no module boundaries.

## Consequences

- **`common/.../common/ui/` runs on a bare JVM.** 69 engine tests
  (12 in `RectTest`, 8 in `PointTest`, 9 in `ColorTest`, 8 in
  `WidgetTest`, 8 in `ContainerTest`, 13 in `RootTest`, 6 in
  `LabelTest`, 6 in `PanelTest`) and 12 `SupplyIntentListTest` tests
  cover value-type contracts, hit-test propagation, focus hand-off,
  layout directions, hover state, lang-key placeholder, gap math,
  intent sort, and hover-to-gap highlight. Total `common:test` count
  goes from **436** (pre-ADR-0022) to **517** (post-ADR-0022) —
  **+81 tests, 0 failures**.
- **The act-4 SUPPLY-mode screen is 170 lines, not 1000.** It extends
  `Screen`, not `AbstractContainerScreen`, and its only responsibility
  is to construct the `Root`, hand it to `McDrawContext`, and forward
  input callbacks via `McInputAdapter`. The legacy `TownHubScreen`
  stays untouched; the act-5 PR can migrate its widgets onto the new
  engine at its own pace.
- **Lang keys flow through the engine as opaque strings.** The
  placeholder label carries `"burg.message.hub.supply.no_intent"` —
  the engine never sees "No pending intent" / "Нет ожидающих решений".
  The adapter's `isLangKey(text)` heuristic (`namespace.path` shape)
  routes lang keys through `Component.translatable(...)`; the same
  heuristic is wrong for paths and dot-delimited identifiers, so the
  rule is "if the engine wants translation, use a lang key; if the
  engine wants raw text, use a plain string".
- **One engine, one adapter.** Every future widget follows the same
  pattern: a `common/.../some-context/ui/SomeWidget.java` file that
  composes `Container` + `Panel` + `Label`, and a screen in
  `neoforge/.../client/gui/SomeScreenV2.java` that wraps the widget
  in a `Root` and forwards input. The act-5 era, the act-4 standing
  screen, and the act-6 standing-acquisition review all share the
  same scaffolding.

## What this does NOT do

- **Mod-loading wiring.** `OuatForgeClient` still registers
  `TownHubScreen` against the `town_hub` `MenuType`; `TownHubScreenV2`
  is not registered. Opening the act-4 hub still routes through the
  legacy screen. The wiring is a one-line follow-up that depends on
  the Town facade exposing the `HubMode` per-tick (ADR-0019's full
  predicate, still in flight).
- **Constraint / anchor layout.** The engine's `Container` packs
  children along a single direction with `spacing`. A future
  carve can add `Constraint` or `Anchor` layout helpers; today's
  every screen writes its layout by hand.
- **Text measurement.** The engine doesn't know how wide a string is
  in pixels; `Label` always draws at its top-left, the adapter renders
  the full string. Wrapping / clipping at the panel boundary works
  because the adapter's clip-stack pushes the panel's GUI-space
  rectangle, but per-character wrapping is a future carve.
- **Animation / tweening.** The engine is immediate-mode — every
  frame the widget draws itself. There is no animation primitive
  because the act-4 hub is static. Future carves can add a
  `Widget#tick(int dtMs)` if they need animation.
- **The legacy `TownHubScreen`.** Still 1000+ lines, still owns the
  construction-mode hub. Migrating it to the new engine is act-5
  work; today's carve only adds the act-4 foundation.

## Migration checklist for the next widget

- [ ] New file under `common/.../<bounded-context>/ui/`. One Widget
      class (or value-type + Widget pair if a record carries the data).
- [ ] Constructor takes a value-type record or a domain type — no
      `net.minecraft.*` imports.
- [ ] `layout(int, int)` overrides `Widget.layout` and forwards to
      any `Container` children; leaf widgets keep their explicit
      bounds unless they were empty.
- [ ] `draw(DrawContext)` walks children; text draws go through
      `ctx.drawText(...)`, fills through `ctx.drawRect(...)`.
- [ ] Bare-JVM test in `common/src/test/java/...` pins value-type
      contracts, sort order, gap math, hover state — at least one
      test per public method.
- [ ] If the widget crosses to a screen: add a screen class in
      `neoforge/.../client/gui/` that wraps the widget in a `Root`
      and forwards input via `McInputAdapter`. Never add the widget to
      the legacy `TownHubScreen`.

## Related

- ADR-0008 — DDD layering. The engine is `common/.../common/ui/` —
  bare-JVM POJO types, the same Minecraft-free discipline as
  `domain/` and `application/`.
- ADR-0019 — `HubMode.SUPPLY`. The act-4 mode whose first widget is
  the engine's first user.
- `docs/01-vision/VISION.md` — the act-4 hub as a read-only window.
- `docs/05-craft/HOW_WE_WORK.md` — the calibration philosophy
  ("every metric must be quiet on the author's work before it is
  believed"). The engine's tests pin the value-type contract;
  visual correctness still needs an in-game look.
- `DomainPurityTest` — the import fence the engine inherits. A
  future carve that adds `common/.../some-context/ui/` only has to
  add the new code; the test extends automatically.