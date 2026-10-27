# Sky's Map Shapes 1.7.1: audit fixes and spawn proofing setup help

Worked on branch `fixes-1.7.1` in `E:\Claude\SkysMapShapes-fixes`, taken from `mc26.3` at
`3092e6b` (1.7.0, the beacons commit, not yet pushed). Every line number below is from that commit.

Written from a full read of all 73 files on 2026-10-27. Nothing here has been built or run yet.

## How to work through this

- **One phase per session.** Start each in a new session in this folder, with Remote Control on,
  and say: "Do phase N of docs/FIX-PLAN.md." The session reads this file and `CLAUDE.md`, then
  only the files its phase names.
- **Suggested model per phase** is given at its top. Opus 5.5 Medium where a mistake is costly
  (security, poking at Litematica internals); Sonnet 5.5 for the mechanical phases.
- **Each phase ends the same way:**
  1. `./gradlew build` passes (it runs the unit tests).
  2. Any new or changed behaviour has a unit test where it can have one.
  3. The "Check in game" list for that phase is written into the phase's commit message, for
     Sky to try (the session cannot run the game client over Remote Control usefully).
  4. One commit for the phase, message starting `Phase N:`. **Never push.**
  5. Tick the phase's box below and note anything left undone under it.
- **Phases 1 → 6 in order.** Phase 4 can come before 2 and 3 if spawn proofing matters more right now; nothing
  in it depends on them.
- Keep the code's own style: plain-English KDoc saying *why*, no jargon, the same naming. Read
  `CLAUDE.md` first.

## Progress

- [x] Phase 0: baseline build
  - 2026-10-27: `./gradlew build` passed in 40 s; 61 tests, 0 failures (MiniHud 15, Shape 10,
    Share 14, SpawnOutlines 8, Untrusted 4, Volume 10).
- [x] Phase 1: chat share security and save bugs
  - 2026-10-27: build passes, 68 tests (ShareTest 17, UntrustedTest 8). Not checked in game yet.
  - Left for 5.1: `AddShapeWindow.commit` (not a phase 1 file) still warns about lag from the
    outline only. It is safe, because `MiniHudShapes.create` now refuses a too-big finished JSON for
    every caller. It just does not give the "press again" warning for big-but-legal shared sizes.
  - `centered_box` is accepted as a shared MiniHUD type as well as the `NEEDS` types and `SPHERES`,
    since sharing already round-trips it (`aMiniHudShapeCarriesItsOwnSizes`).
  - Found while testing: three `UntrustedTest` lines were `Map shape: circle ...` with no name.
    They never parsed, so they passed without testing anything. They now have a name.
- [x] Phase 2: per-frame speed
  - 2026-10-27: build passes, 71 tests, 0 failures (3 new in ShapeTest). Not checked in game yet.
  - `ShapeStore.inDimension` no longer sorts, so `MapMenus` sorts its own click-time list by area.
  - `Polygon.contains` now rejects points outside the bounding box first and measures edge distance only when the sides say outside.
  - `MiniHudShapes.version` goes up on every read (twice a second), so the cached list is remade that often until phase 3.1 reads on triggers.
- [x] Phase 3: background work (MiniHUD triggers, beacons, Hide all)
  - 2026-10-27: build passes, 72 tests, 0 failures (1 new in MiniHudTest). Not checked in game yet.
  - 3.1: MiniHUD GUI class names in its jar are `GuiShapeEditor`, `GuiShapeManager`, `GuiShapesImport` and
    `GuiShapesExport`; any screen whose class starts `fi.dy.masa.minihud.gui.GuiShape` marks a re-read on close.
    The mixin (`ShapeManagerMixin`) is checked against MiniHUD 0.40.7's `addShape`/`removeShape` signatures.
    Hide-all notes are pruned on disconnect from `MiniHudShapes.idsForPruning()`, which reads the other
    dimensions' files then if they were not being read, so notes about their shapes are not lost.
  - 3.3: after a shape is switched in MiniHUD inside a batch, `MiniHudShape.withEnabled` stands in for the re-read.
  - 3.4: reach is `size / zoom + 16`, kept between 256 and 4096 blocks. Not checked that `size` is in pixels
    and `zoom` pixels per block in Xaero's minimap: look at a shape at the minimap's edge in cave mode.
  - Beacons: also a 30-second full re-scan as a safety net (chunks sent again would otherwise go unseen).
- [x] Phase 4: spawn proofing setup help and the render layer bypass
  - 2026-10-27: build passes, 77 tests, 0 failures (5 new in SpotSectionsTest). Not checked in game yet:
    the redirects are `require = 0`, so only the log can show they apply (look for
    "Render layer bypass working: ..." lines with layers set to a single layer away from the spots).
    `devmods/` was empty and there is no 26.3 MiniHUD jar here, so `runClientGameTest` could not run.
  - The bypass covers every layer check in 4.1's findings, so the Layers check only fails when no
    bypass hook has run yet and a shown spot is outside the layers.
  - Chat: after switching on, MiniHUD's rendering and light level overlay are not named again when
    they go off, since the overlay hotkey is how spawn proofing is meant to be switched off. Every
    other check is said again as the plan says. "Switched on" means spawn proofing on with
    Everywhere or a spawn-proofed shape in your dimension. Quiet for 5 s after joining.
  - MiniHUD's light level range counts as tiny under 8 blocks (no fix, says where to change it).
- [x] Phase 5: duplicated and stale code
  - 2026-10-27: build passes, 82 tests, 0 failures (ConfigTest 4 new, VolumeTest 1 new). One commit per
    part (5.1–5.7). Not checked in game yet.
  - 5.1's commit also holds 5.2's changes to `MiniHudShapes.kt` (staged together by mistake).
  - 5.2: `Guard(off).run(doing, default) { }` logs "Could not <doing>; <off> until the game restarts".
    LightLevels' redraw request now switches light levels off on failure too (one guard per object).
  - 5.3: settings are delegated properties keyed by their own names (`var x by bool(true)`); screens
    reset with `Config.default(Config::x)`. One unreadable value now costs only that setting.
  - 5.4: the plugins live in `com.skystormer.skysmapshapes.plugin`, outside the mixin packages. The
    minimap plugin also accepts `xaerominimapfair` (Xaero's Fair-Play build), as the hook report does.
  - 5.5: `Volume.Direction` reads `main_axis`/`direction` in either case; unknown names are UP everywhere
    (before, a lying prism with an unknown axis was drawn pointing east on the map).
  - 5.6: `PanelScreen` and `PagedRows` in `gui/PanelScreen.kt`; `Widgets` and `Thickness` in `gui/Widgets.kt`.
  - 5.7 B6 is not a bug: Fabric screen API 5.2.4's `ScreenMixin.beforeInit` makes new per-screen events
    on every `init` and `resize`, and every listener the map panels add is per-screen.
  - 5.8 done afterwards (2026-10-27, 84 tests, 0 failures): `ShapeDraft` holds the save step both editors
    shared (still there, valid, MiniHUD create with the press-again warning, or saved on the map) and the
    typed size check. The editors keep their own fields: the add window holds live numbers dragged on the
    map, the edit screen typed text, so only the finished shape goes through `ShapeDraft`.
- [x] Phase 6: defaults, version, notes
  - 2026-10-27: build passes, 82 tests, 0 failures. Jar: `build/libs/skysmapshapes-1.7.1+26.3.jar`.
  - Beacon defaults 200 and 12 in the settings table; a saved config keeps its own values.
  - The repo had no changelog (releases are written on GitHub), so the notes are in `docs/RELEASE-1.7.1.md`,
    ready to paste. README: beacon defaults, the setup help and layer bypass, and MiniHUD read on
    change rather than twice a second.

---

## Phase 0: baseline build (any model, a few minutes)

1. `./gradlew build` on the untouched branch. Note the time and whether every test passes.
2. If it fails, stop and report: nothing else should start on a broken baseline.

---

## Phase 1: chat share security and save bugs (Opus 5.5 Medium)

Files: `ShapeShare.kt`, `MiniHudGeometry.kt`, `MiniHudShapes.kt`, `gui/ShapeEditScreen.kt`,
`gui/ShareScreen.kt`, `Config.kt`, `gui/ConfigScreen.kt`, tests `ShareTest.kt`, `UntrustedTest.kt`.

### 1.1 Hidden MiniHUD sizes can crash the player who adds a shared shape (critical)

**Why.** A shared line's extras (after the first ` · `) can carry MiniHUD sizes such as
`radius=4000 max_radius=4002`. They are never shown: `ShapeShare.readLine` cuts them off the
displayed text (line 205) and the hover shows only the outline's size. `MiniHudShapes.create`
checks `MiniHudGeometry.whyTooBig(shape, ...)` (line 234), which measures the *outline*, then
`MiniHudGeometry.forMiniHud` (line 502) overwrites the radius, height and limits with the shared
sizes. `tookSize` then compares MiniHUD's answer with that same inflated JSON, so it passes.
`ShapeEditScreen` ticks "In MiniHUD" by default for a shared MiniHUD shape (its `inMiniHud`
initialiser), so **[Add to my map] → Save** builds the inflated shape. The MAX_BLOCKS comment in
MiniHudGeometry records a radius-3,791 sphere crashing the game.

**Fix.**
1. In `MiniHudGeometry`, add `fun blocksOf(json: JsonObject): Double?`: the number of blocks
   MiniHUD would draw for a finished MiniHUD JSON, read from its own `type`, `radius`,
   `radius_y`, `radius_z`, `height`, `bottom_radius`, `top_radius`, `corner1/2`. Use the same
   formulas as the private `blocks(shape, form)` (sphere surface, cone side, prism wall), so
   both agree. A box stays null (six flat sides).
2. Add `fun whyTooBig(json: JsonObject): String?` that applies `MAX_BLOCKS`, the ellipsoid limit
   and `MAX_RADIUS_LIMIT` to the finished JSON, with the same messages as the shape version.
3. In `MiniHudShapes.create`, after `forMiniHud` builds `json` and **before** anything is sent to
   MiniHUD, call `whyTooBig(json)` and return its message if any. Keep the existing outline
   check too (it gives the nicer "largest it can be" message for typed shapes).
4. Run `lagWarning` on the finished JSON as well, so a shared shape that is big but legal still
   gets the "press again" warning.
5. In `ShapeEditScreen`, for a shared MiniHUD shape whose sizes make it **bigger than its outline**
   (compare `blocksOf(final json)` against `blocksOf(json from the outline alone)`; more than
   1.5 times is "bigger"), start with "In MiniHUD" **off** and say why in the message line:
   "Its MiniHUD sizes are larger than the shape shown: about N blocks of it."
6. Show the real MiniHUD size whenever "In MiniHUD" is on for a shared shape, in the message line.

**Tests (ShareTest).**
- A line with `circle r5` and hidden `radius=4000 max_radius=4002`: `create`'s checks refuse it
  (test the pure functions: `forMiniHud` then `whyTooBig(json)` returns a message).
- The existing round-trip tests still pass unchanged.

### 1.2 Only known MiniHUD fields and values from chat

**Why.** `MiniHudGeometry.parseSizes` (line 479) keeps any value for a known key, including
free text, and `readLine` takes any word as the MiniHUD type (line 178). These go into MiniHUD's
`fromJson` by reflection. Errors are caught, but nothing from chat should reach another mod
unchecked.

**Fix.**
- Type: must be one of the types `NEEDS` knows (line ~452) or `SPHERES`; else drop the MiniHUD
  part (the shape is still offered as a map shape).
- Numbers: finite, and within `-MAX_COORDINATE..MAX_COORDINATE` for positions,
  `0..MAX_RADIUS_LIMIT` for radii and heights, `0..MAX_RADIUS_LIMIT` for `max_radius`/`max_height`.
- Strings: `main_axis` and `direction` only `UP DOWN NORTH SOUTH EAST WEST` (either case);
  `snap` only `none center corner`. Anything else: drop that key.
- Apply the same checks in `ShapeShare.decode` to the `h` object and `m` type (line 242–244),
  since a server can send a click event that runs `/skysmapshapes <code>` directly.

**Tests.** A line with `MiniHUD rm_rf y 64`, `main_axis=sideways`, `radius=NaN`,
`radius=1e99` each come back without that part.

### 1.3 Private shares can turn public

**Why.** `ShareScreen.players()` takes names from `connection.onlinePlayers` unchecked, and
`ShapeShare.tick` builds `"<command> <name> <line>"`. A server or plugin can list a fake player
called `@a`, or a name with a space, so `/tell @a ...` goes to everyone.

**Fix.** Accept only names matching `[A-Za-z0-9_]{1,16}` (Mojang's rule), both when listing them
in `ShareScreen` and again in `ShapeShare.shareWith` (defence in depth). Leave others out of the
list.

**Test.** `shareWith` with `@a`, `a b`, `x;y` and a 17-letter name queues nothing for them.

### 1.4 The private share command from the config file

**Why.** `Config.load` takes `privateShareCommand` as any text (line 231).

**Fix.** Accept `[a-z0-9_:-]{1,32}` (`tell`, `msg`, `w`, `minecraft:tell`, a plugin's `pm`);
anything else keeps `tell` and logs a warning once.

### 1.5 Shared shapes no bigger than typed ones

**Why.** `Shape.isValid` allows 60,000,000 (line 126) while typing allows 1,000,000
(`MAX_TYPED_SIZE`). A shared shape between the two opens in the add screen but cannot be saved
from it.

**Fix.** In `ShapeShare.readLine` and `decode`, refuse sizes over `Shape.MAX_TYPED_SIZE`. Keep
`isValid`'s larger limit for files (old saved shapes still load).

**Test.** `circle r2000000` from chat reads as null; `r1000000` reads.

### 1.6 Chat lines: skip the action bar, and the old code path

**Why.** `ClientReceiveMessageEvents.ALLOW_GAME` is called for action-bar text too (its second
argument, `overlay`); `ShapesClient` ignores it (line ~442), so a shape on the action bar becomes
a chat line. The 1.2.0-and-earlier `SMS1:` code path (`ShapeShare.codeIn`, `onChat`'s fallback)
decodes raw JSON, including unchecked MiniHUD sizes.

**Fix.**
- `ALLOW_GAME.register { message, overlay -> overlay || ShapeShare.onChat(message.string) }`.
- Remove the `SMS1:` path entirely (`TAG`, `codeIn`, the fallback in `onChat`, its tests
  `linesFromOlderVersionsAreStillUnderstood` and any other that uses it). 1.2.0 is old enough.

### 1.7 Full edit screen saves shapes the next join throws away (bug)

**Why.** `ShapeEditScreen.save` never calls `shape.isValid()`. `toIntOrNull` takes X/Z up to
±2,147,483,647; on the next join `ShapeStore.read` drops that shape and copies the whole file
aside as "unreadable". `AddShapeWindow.commit` does check.

**Fix.** After building `shape` in `save`, `if (!shape.isValid()) return show("That size or place
is beyond any world.")`, as `AddShapeWindow` says it.

### 1.8 Presets with no size limit (bug)

**Why.** `ConfigScreen.save` accepts any positive radius; `Config.load` accepts any radius at
all (negative, NaN, huge). A bad preset makes a shape that cannot be saved.

**Fix.** Both: radius finite, `> 0` and `<= Shape.MAX_TYPED_SIZE`, name `<= 32` letters; else
that preset is skipped (load) or the box shows red and is not kept (screen).

### Phase 1 check in game

- Share a MiniHUD sphere to yourself on a test server; [Add to my map] still offers MiniHUD and
  makes the same shape.
- Paste a hand-made line with hidden `radius=4000 max_radius=4002`: Add shows the warning, "In
  MiniHUD" starts off, Save with it on refuses.
- Private share list: everyone online still listed.
- Typing X = 99999999 in the full edit screen: refused with the message.

---

## Phase 2: per-frame speed (Sonnet 5.5)

Files: `Shape.kt`, `Geometry.kt`, `MapShapes.kt`, `ShapeStore.kt`, `MiniHudShapes.kt`,
`ShapeDrawing.kt`, `ShapeHover.kt`, `ShapeLabels.kt`, `gui/ShapesPanel.kt`, `gui/AddShapeWindow.kt`.

### 2.1 Shape outlines made once, not on every read (high)

**Why.** `Shape.geometry` (line 63) is a getter that builds a new `Geometry` every time;
`MapShape.bounds` and `area` read it again (Geometry.kt, the `MapShape` interface).
`Polygon.bounds` loops its corners on every read too. Drawing, hover, labels (several reads per
label per frame) and sorting all hit this.

**Fix.** `Shape` is an immutable data class: give it `override val geometry: Geometry by lazy {...}`
(declared in the class body so `copy()` and `equals` are unaffected), and make `bounds` and
`area` lazy in `Geometry` itself (`Circle`, `Ellipse`, `Polygon`). `MiniHudShape` already stores
its geometry.

### 2.2 One sorted shape list per dimension, rebuilt only on change (high)

**Why.** `MapShapes.visibleIn` (MapShapes.kt line 196) filters, joins and sorts every call;
`ShapeStore.inDimension` (line 263) sorts by area as well, so it is sorted twice. It is called by
the world map, both minimap paths, the labels and spawn proofing, several times a frame.

**Fix.**
- Give `ShapeStore` and `MiniHudShapes` a `version: Int` that goes up whenever `all` (or the
  hidden/shown notes) is replaced.
- `MapShapes.visibleIn(dimension)` caches its result keyed by (dimension, store version, MiniHUD
  version, `Config.miniHudIncludeDisabled`, draft identity). Return the cache when nothing changed.
- The draft (`AddShapeWindow.draft()`) changes as the mouse moves: keep it out of the cache and
  insert it into a copy only while the add window is open.
- `ShapeStore.inDimension` stops sorting (callers that need order get it from `visibleIn`).

### 2.3 The Shapes panel's list, once per frame (high)

**Why.** `ShapesPanel.Panel.listed` (getter) calls `shapes()`, which runs `inDimension` twice and
sorts by name, **every read**. `itemCount()` and `naturalWidth()` are called from `DockPanel.layout`
several times a frame, plus `drawLocal`, `afterLayout`, `shapeAt`; `naturalWidth` also measures
every shape's name and size text. With many shapes this is the biggest per-frame cost on the map.

**Fix.** Cache `listed` and `naturalWidth()` keyed by (map dimension, store version, MiniHUD
version). Text widths only change with the list.

### 2.4 Curve points and hover (medium)

**Why.** `ShapeDrawing.draw` calls `geometry.points(blocksPerUnit)` per shape per frame: a new
array and up to 1,024 sin/cos. `Geometry.Ellipse.distanceTo` (line 51) builds a 256-point ring
on every call, and hover calls it every frame.

**Fix.**
- In `Circle` and `Ellipse`, keep the last points array with the segment count that made it;
  reuse it when `segments(...)` gives the same count.
- `Ellipse`: build the 256-point ring once (lazy).
- `Polygon.contains` checks `polygonDistance` before the real test; only do that when the ray
  test says outside and the point might be on an edge (or test edges last).

### 2.5 Labels (low)

**Why.** `ShapeLabels.halfWidth` measures `font.width(label)` 6–8 times per label per frame
(interaction box, render box, render).

**Fix.** Keep a small map of label text → width in the `Context`, filled in `begin`.

### Phase 2 check in game

- Make 100 shapes (a test command or a quick loop in a dev build), open the world map, compare
  frame rate before and after with F3.
- Shapes, labels, hover, the panel and the add window all behave exactly as before.

---

## Phase 3: background work (Sonnet 5.5)

Files: `MiniHudShapes.kt`, `ShapesClient.kt`, `BeaconBeams.kt`, `mixin/ClientLevelMixin.java`,
`ShapeStore.kt`, `ShapeDrawing.kt`, `mixin/minimap/MinimapFBORendererMixin.java`,
`LightLevels.kt`, `SpawnProofing.kt`.

### 3.1 MiniHUD read on triggers, not twice a second (medium)

**Why.** `MiniHudShapes.tick` (line 87) exports every live MiniHUD shape to JSON by reflection
every 10 ticks, and `otherDimensions` lists `config/minihud` and reads each file's modified time
on disk, all on the main thread, all the time.

**Design (Sky's).**
- **Your dimension (MiniHUD's live list).** Re-read when:
  - a shape is added or removed: mixin `@Pseudo` into MiniHUD's `ShapeManager.addShape` and
    `removeShape` (RETURN), which call `MiniHudShapes.markDirty()`;
  - MiniHUD's Shape Editor closes: `ScreenEvents.remove` on any screen whose class name is
    `fi.dy.masa.minihud.gui.GuiShapeEditor` (and MiniHUD's shape list screen, `GuiShapeManager`
    — confirm the name in MiniHUD's jar), calls `markDirty()`;
  - the player's dimension changes (compare `Dimensions.ofPlayer()` each tick; cheap);
  - this mod changes a MiniHUD shape (it already calls `tick(force = true)`).
  The next tick after `markDirty()` does the read. Put the mixin in `skysmapshapes.minihud.mixins.json`.
- **Other dimensions (MiniHUD's files).** Read a dimension's file only when something asks for
  that dimension: the world map showing it (`Dimensions.ofMap()` differs from the player's), or
  the shapes list asking for all dimensions. Keep `fileCache` by modified time as now. Listing the
  folder happens then too, not on a timer.
- **Safety net.** A slow re-read every 10 seconds catches anything the triggers miss (MiniHUD
  changing a shape from a hotkey, say). Cheap compared with now.
- **Resilience (bug B4).** Parse each live shape inside its own try/catch, so one odd shape is
  skipped and logged once instead of turning MiniHUD reading off until restart (line 98–102).

**Check in game.** Add, edit, delete a MiniHUD shape: the map follows within a tick. Flip the map
to the Nether while in the Overworld: the Nether's MiniHUD shapes show. Walk through a portal:
the new dimension's live shapes show.

### 3.2 Beacons: scan less, and spread it out (medium)

**Why.** `BeaconBeams.tick` (line 306) every second scans every chunk within the distance (up to
32 chunks, about 3,200) and reads each beacon's whole column up to the world top (`beamOf`,
line 364) for up to 1,000 beacons, all in one tick. The Max Y slider only limits drawing.

**Fix.**
- `beamOf` stops at `minOf(level.maxY, Config.beaconMaxY)` and starts at
  `maxOf(beacon.y + 1, Config.beaconMinY)` (runs below Min Y are never drawn). Re-scan when either
  slider changes.
- Keep a `HashMap<Long, List<BlockPos>>` of beacons per chunk. A chunk is scanned when it first
  comes into range or when a block in it changes (`ClientLevelMixin.sendBlockUpdated` already
  sees every change: add `BeaconBeams.blockChanged(pos)` beside `SpawnProofing.blockChanged`).
  Chunks out of range are forgotten.
- A beacon's column is re-read only when a block in that column changes.
- Do at most N chunk scans per tick (start at 64) so a big first scan spreads over a few ticks.

**Check in game.** 200 beacons around you: no once-a-second stutter. Break a block above one:
its outline goes within a tick.

### 3.3 Hide all / Show all: one save, one MiniHUD read (medium)

**Why.** `ShapeStore.hideAll` and `showAll` (lines 208–250) call `setVisible` and `setInMiniHud`
per shape. Each of those calls `save()` (whole file) and, for MiniHUD shapes, `MiniHudShapes.setEnabled`
calls `tick(force = true)` (a full MiniHUD read) and, for other dimensions, rewrites MiniHUD's
file per shape. Fifty shapes is fifty-plus file writes in one click.

**Fix.**
- Give `setVisible`, `setInMiniHud`, `MiniHudShapes.setEnabled` a `batch: Boolean = false`
  parameter (or a `ShapeStore.batch { ... }` block) that skips `save()` and the forced read.
  `hideAll`/`showAll` save once and read MiniHUD once at the end.
- For other-dimension MiniHUD shapes, group them by file and edit each file once
  (`editFile(file) { shapes -> for each shape in this file ... }`).

### 3.4 Cave-mode minimap reach (low)

**Why.** `ShapeDrawing.drawMinimapAnyMode` draws everything within `MINIMAP_REACH = 4096`
(line 125); the minimap shows far less.

**Fix.** `MinimapFBORendererMixin` already receives `zoom` and `size`. Pass them through and use
`reach = size / zoom` blocks (plus the longest line's half width, plus 16) instead of 4096.
Confirm the formula by drawing a test shape at the minimap's edge.

### 3.5 Light levels and spawn proofing share their area code (with D2, D3)

**Why.** `LightLevels.volumes` (line ~93) and `SpawnProofing.volumes` (line 155) walk the same
shapes and turn each into a `Volume`; only the filter differs. Both read MiniHUD's
`lightInfos`/`pos` by reflection with their own cached fields.

**Fix.**
- New `Volumes.inDimension(dimension, keep: (id) -> Boolean): List<Pair<String, Volume>>` used by both.
- New `MiniHudLight` object holding the `lightInfos`, `pos` and `block` field lookups, used by both.
- `SpawnProofing.gather` collects into a `LongArrayList` (fastutil, already used) instead of `ArrayList<Long>`.
- `SpawnProofing.updateOutlines`: `spots.filter {...}.toLongArray()` → a `LongArrayList` loop.

---

## Phase 4: spawn proofing setup help and the render layer bypass (Opus 5.5 Medium)

Files: `SpawnProofing.kt`, `LitematicaPlacement.kt`, `gui/LightLevelsScreen.kt`, new
`SpawnProofSetup.kt`, new Litematica mixin(s) in `mixin/litematica/`,
`skysmapshapes.litematica.mixins.json`.

### Goal

Turning spawn proofing on should either just work or say exactly what is stopping it, with a
one-click fix. Render layers are handled differently: spawn proofing's own placement ignores
them, so your layer setting for building is left alone.

### 4.1 Investigate Litematica's render layers first (do this before writing any code)

Litematica 0.29.1 for 26.3 (Modrinth version id `ZqV316KY`, already a compile-only dependency;
the jar is in Gradle's cache). Find, and write down in this file under "4.1 findings":

1. Where the layer range lives: expect `DataManager.getRenderLayerRange()` returning a
   `LayerRange` with `isPositionWithinRange(BlockPos)` / `(int x, int y, int z)` and
   `intersects(...)`, and modes ALL, SINGLE_LAYER, ALL_ABOVE, ALL_BELOW, LAYER_RANGE.
2. **Every caller** of those methods. Expect at least:
   - schematic chunk building (`ChunkRendererSchematicVbo`, which this mod already hooks):
     which blocks and overlays get drawn;
   - which sub-chunks are rebuilt at all (`WorldRendererSchematic` / `RenderChunkSchematicVbo`
     or similar, which may skip whole sections outside the range);
   - Easy Place (`EasyPlaceUtils`, already hooked): whether it refuses positions outside the
     range, and where;
   - the placement's schematic-world block lookups (`WorldSchematic`/`SchematicWorldHandler`),
     if they filter by layer.
3. For each, the exact method, descriptor and whether it is called with a `BlockPos` or x/y/z, so
   a `@Redirect` or `@WrapOperation` can target it.

#### 4.1 findings (Litematica 0.29.1 `ZqV316KY`, MaLiLib 0.30.2 `JX9bESec`, read with `javap -c`)

1. The range is MaLiLib's `fi.dy.masa.malilib.util.position.LayerRange`, from
   `DataManager.getRenderLayerRange()`. `isPositionWithinRange(BlockPos)`, `(long)`, `(III)`;
   `intersectsBox(IIIIII)`; `getClampedRenderBoundingBox(IntBoundingBox)`; `setLayerMode(LayerMode)`.
   Modes in `fi.dy.masa.malilib.util.LayerMode`: ALL, SINGLE_LAYER, LAYER_RANGE, ALL_BELOW, ALL_ABOVE.
2. Callers that matter for a placement's blocks and Easy Place:
   - `ChunkRendererSchematicVbo.rebuildChunk(ChunkRenderTaskSchematic, ChunkRenderDispatcherBuffers)V`:
     - `intersectsBox(IIIIII)Z` on the render chunk's whole column box: false skips building it (**box check**);
     - `getClampedRenderBoundingBox(IntBoundingBox)` on each placement box in the chunk: only the
       clamped part is walked, block by block, into `renderBlocksAndOverlay` (**box check**).
   - `SchematicPlacementManager.getAndUpdateVisibleChunks(ChunkPos)`: `intersectsBox(IIIIII)Z` on each
     loaded schematic chunk column, full world height; false leaves the column out of the chunks drawn
     (**box check**; only ever false for X or Z axis layers). Recomputed after
     `setVisibleSubChunksNeedsUpdate()`.
   - `EasyPlaceUtils.placementRestrictionInEffect()Z` and `placementRestrictionInEffect(Minecraft)Z`:
     `isPositionWithinRange(BlockPos)Z`; outside the range, placing there is refused (**single position**).
   - `RayTraceUtils.traceFirstStep` / `traceLoopSteps` (private static): `isPositionWithinRange(III)Z`
     on `RayTraceCalcsData.x/y/z`, only when tracing with the layer range respected (the schematic
     world traces Easy Place uses through `getGenericTrace` and
     `getFurthestSchematicWorldTraceBeforeVanilla`) (**single position**).
   - `RayTraceUtils.getFurthestSchematicWorldBlockBeforeVanilla` and `getPickBlockLastTrace`:
     `isPositionWithinRange(BlockPos)Z` (**single position**).
   - Not relevant here: entity and block entity drawing (`WorldRendererSchematic.prepareEntities` /
     `prepareBlockEntities`), pasting (`SchematicPlacingUtils.shouldPasteBlock`, the paste and
     delete tasks), the verifier, face culling at the range's edge (`isPositionAtRenderEdgeOnSide`).
   - The schematic world itself is filled without looking at the range (`SchematicPlacingUtils`
     only checks it when pasting), so the blocks are always there to be found.
3. Descriptors are in the mixins that hook them: `ChunkRendererSchematicVboMixin`, `SchematicPlacementManagerMixin`,
   `EasyPlaceUtilsMixin` and `RayTraceUtilsMixin` (all `@Redirect`, `require = 0`).

**Decision.** Every check can be bypassed: the single-position ones by "is one of the spots"; the
three box checks by "the box holds a spot" (`SpawnProofing.litematicaBoxHasSpot`, from a set of
packed section keys built with `shownSpots`). Where the clamped box is widened, the blocks outside
the range that are not spots are left out again one by one, so other placements still follow the
layers.

**Decision point.** If every check can be bypassed for single positions:
go to 4.2. If a check works on whole sections (skips building a section outside the range),
bypass that one by "section holds a spawn proofing spot" (`SpawnProofing.sectionHasSpot(sectionX, sectionY, sectionZ)`,
a set of packed section keys built alongside `shownSpots`). If something cannot be bypassed
cleanly, drop the bypass for that part and rely on 4.3's message and Fix button for render layers.

### 4.2 The render layer bypass

- New `@Pseudo` mixin(s) in `mixin/litematica/`, `require = 0`, registered in
  `skysmapshapes.litematica.mixins.json` (applied only with Litematica, via the plugin).
- Each wraps a layer-range check found in 4.1 and returns `true` when
  `SpawnProofing.coversLitematicaOverlay(pos)` (one of spawn proofing's spots; it is a binary
  search on a sorted array and already safe on Litematica's builder threads), otherwise the
  original answer.
- Only spawn proofing's spots: every other placement and block still follows your layers.
- When the layer setting changes, nothing extra is needed: the spots are still exempt.
- Log once, the first time each bypass runs, as the other hooks do ("Render layer bypass working: ...").
- If a hook is missing on some Litematica version, it just doesn't apply; 4.3's checklist then
  shows render layers as a cause, with the Fix button.

**Tests.** Pure parts only (the section key set). The rest is checked in game.

### 4.3 Setup check: what is stopping spawn proofing

New `SpawnProofSetup` object. A list of checks, each with a short name, a "why" sentence, a test,
and (where possible) a fix:

| Check | Read from | Fix |
|---|---|---|
| MiniHUD main rendering on | `Configs$Generic.MAIN_RENDERING_TOGGLE` (already read in `SpawnProofing.overlayShowing`) | set it on |
| MiniHUD light level overlay on | `RendererToggle.OVERLAY_LIGHT_LEVEL` (already read) | set it on |
| MiniHUD light level range not tiny | MiniHUD's light level range config (find its name in MiniHUD's `Configs`) | none; say the number and where to change it |
| Litematica rendering on | Litematica's main rendering toggle and schematic rendering toggle (find names in `Configs.Visuals`) | set them on |
| Litematica Easy Place on | `Configs.Generic.EASY_PLACE_MODE` (already read) | set it on |
| Render layers include the spots | `DataManager.getRenderLayerRange()` | only if 4.2's bypass is not active: set layers to All |
| An accepted block in your inventory | `SpawnProofBlocks.accepted()` and the inventory | none; say "carry a slab (or another accepted block)" |
| Inside a spawn-proofed shape | `Config.spawnProofEverywhere`, `SpawnProofing.volumes` | none; say "right-click a shape → Spawn proofing: on, or turn on Everywhere" |

- Reading and setting MaLiLib options goes through reflection the same way the mod already does
  (`getBooleanValue`, and `setBooleanValue(boolean)` on the same objects). Each check is in its
  own try/catch: a missing option means "cannot tell", never a crash.
- Fixes only ever happen on a click. Nothing is changed in MiniHUD or Litematica on its own.

### 4.4 Telling the player

- **In chat, once.** When spawn proofing is switched on (in the settings, or by the first
  spawn-proofed shape) and a check fails, one client-only chat line:
  `Spawn proofing is waiting: MiniHUD's light level overlay is off. [Fix it]`
  - `[Fix it]` runs a client command `/skysmapshapes fix <check>` that applies that one fix and
    says what it changed.
  - Said again only when a *different* check starts failing, or after the player fixes it and it
    breaks again. Never more than once a minute.
  - Only the first failing check is named, in the table's order (fix that, then the next shows).
- **In the Light levels & spawn proofing screen.** A "Setup" section at the top, one line per
  check: ✔ or ✘, its name, its "why" as a tooltip, and a Fix button where there is a fix. A
  "Fix all" button applies every available fix. Lines refresh when the screen opens and after
  each fix.
- **Register the `fix` subcommand** beside the existing `skysmapshapes <code>` command in
  `ShapesClient` (`literal("fix").then(argument("check", word()))`). The code argument already
  takes one word; make sure "fix" is matched first.

### Phase 4 check in game

- Set Litematica render layers to "single layer" well above you, turn on spawn proofing: the
  slabs still show on the floor and Easy Place still places them. Your other schematic still
  shows only its single layer.
- Turn MiniHUD's light level overlay off, turn spawn proofing on: one chat line with [Fix it];
  clicking it turns the overlay on and spawn proofing starts.
- Empty your inventory of slabs: the message says to carry one.
- The settings screen's Setup lines match, and Fix all works.

---

## Phase 5: duplicated and stale code (Sonnet 5.5)

Do these as separate small commits inside the phase if that is easier to review.

### 5.1 One "make it in MiniHUD" flow (D1)

`AddShapeWindow.commit` (line ~797) and `ShapeEditScreen.save` (line ~367) each: check too big,
show the lag warning and wait for a second press, call `MiniHudShapes.create`, cut the message at
" It stays" / ", so it stays". Move it into one function in `MiniHudShapes`:

```kotlin
sealed interface CreateResult { object Made; class Refused(val why: String); class WarnFirst(val warning: String) }
fun tryCreate(shape: Shape, y: Int, form: Form?, typeId: String?, sizes: JsonObject?, warnedFor: String?): CreateResult
```

Both screens show `why` or `warning` and keep their own "press again" state.

### 5.2 Small helpers (D4)

- `Every(ticks)` with `fun due(): Boolean` replaces the hand-made counters in `MiniHudShapes`,
  `LightLevels`, `SpawnProofing`, `BeaconBeams`, `ShapeShare`.
- `Guard(what)` with `inline fun <T> run(default: T, block: () -> T): T` replaces the
  "`broken = true`; log 'off until the game restarts'" blocks (about 14) in `BeaconBeams`,
  `LightLevels`, `MiniHudShapes`, `SpawnProofing`, `MenuTips`.

### 5.3 Settings written once (D5)

Every setting is currently written four times: its default in `Config`, read in `Config.load`,
written in `Config.save`, and copied/reset in `ConfigScreen` or `LightLevelsScreen`
(`resetToDefaults` repeats the defaults by hand: `fillOpacity = 0.15f`, `spawnProofOpacity = 0.25f`
and so on). Make one table:

```kotlin
class Setting<T>(val key: String, val default: T, val read: (JsonElement) -> T?, val clamp: (T) -> T)
```

`load`, `save` and both screens' reset use the table. Screens keep their local copies (so Cancel
still works) but reset from `setting.default`. Panel positions can stay hand-written if the table
gets awkward for them.

### 5.4 One mixin plugin (D6, fixes B5)

`LitematicaMixinPlugin` and `MiniHudMixinPlugin` are identical but for the mod id. Make one
abstract `ModLoadedPlugin(modId)` with two two-line subclasses, and add a third for
`xaerominimap` to `skysmapshapes.minimap.mixins.json` (today it has no plugin, so without
Xaero's Minimap installed Mixin logs target-not-found warnings).

### 5.5 MiniHUD JSON read once per shape (D7)

`MiniHudGeometry.of` and `volumeOf` branch on the same keys separately; the type → section
mapping (`pyramid` → square and so on) is written three times, the direction names three times
(`standing`, `along`, `Volume.Tapered`). Make one `MiniHudKind` lookup (type → section, form,
sphere or not) and one `Direction` enum with its axis and step, used by all of them.

### 5.6 Screens (D8)

- `field()` (ConfigScreen, ShapeEditScreen) → one function in a small `gui/Widgets.kt`.
- The colour cycle button (ShapeEditScreen.colourButton, ConfigScreen presets,
  LightLevelsScreen overlay) → `Widgets.colourButton(...)`.
- `ThicknessSlider` → a `ConfigScreen.Slider` with a formatter (or keep `ThicknessSlider` as a thin
  wrapper over it).
- `ShareScreen`, `MoveToMiniHudScreen`, `MiniHudVisibilityScreen`, `ShapeListScreen` each draw the
  same panel background and go back to `parent` on close → `PanelScreen(parent, title, width)` base.
- The paged search list in `ShapeListScreen` and `ShareScreen` (query, page, rows, < / >, page
  label, refreshRows) → shared helper class.
- `MapMenus`: the "MiniHUD would not switch X on/off; the log says why." branch appears six times
  → `switchInMiniHud(shape, on, saidWhenDone)`.
- `isWorldMap(screen)` (4 places, by class name) → one function in `ShapesClient` or `Dimensions`'
  neighbour; use it in `ShapesClient`, `MapMenus`, `ShapeLabels`, `ShapeDrawing`.
- The "From MiniHUD · right-click to edit or hide" / "Right-click to edit or delete" footer in
  `ShapeHover.drawTooltip` and `ShapeLabels.describe` → one function.

### 5.7 Small fixes and stale bits

- **Swap cooldown (B3).** `SpawnProofing.beforeUse` (line 368–372): count `swapCooldown` down at
  the top of every call, before the "use key down" check, so a short click after a swap is not
  ignored.
- **B6, unverified.** `ShapesPanel.addTo`, `AddShapeWindow.install` and `DockPanel`'s
  `ScreenEvents.beforeTick(screen)` register listeners each time the map screen initialises
  (resizing the window does that again). Check in a dev client whether Fabric clears a screen's
  listeners on re-init: resize the map window five times, then scroll on the panel and see if it
  scrolls five rows. If they pile up, register once per screen (keep a weak set of screens already
  hooked).
- `Shape.y` KDoc says "not used yet": it is used for MiniHUD height and sharing. Fix the comment.
- `fabric.mod.json` description: mention all six kinds, MiniHUD, light levels, spawn proofing and
  beacons in one or two sentences.
- `@Volatile` on fields only the main/render thread touches (`BeaconBeams.beams`,
  `ShapeHover.hovered`): remove. Keep it where Litematica's builder threads read
  (`SpawnProofing.shownSpots`, `LitematicaPlacement.box`).
- `ShapeStore.open` parses the file twice (`read` and `readNotes`): parse once, pass the JSON to both.
- `SpawnProofBlocksScreen`: make each block's `ItemStack` and id once in `filter()`, not per tile per frame.
- `CLAUDE.md` still says "Minecraft 26.2" and "circles, squares and rectangles": update for 26.3
  and the current feature list (it is gitignored; edit it in place).

### 5.8 Optional, larger (D9)

`AddShapeWindow` (980 lines) and `ShapeEditScreen` (432) are two complete editors. Keep both UIs
but give them one `ShapeDraft` (typed fields → validated `Shape`, MiniHUD create via 5.1). Only
if phases 1–5.7 are done and there is budget left.

---

## Phase 6: defaults, version, notes (Sonnet 5.5, short)

- Beacon defaults: `beaconLimit` 200 and `beaconDistance` 12 (in `Config` and, after 5.3, the
  settings table). Existing configs keep what they have.
- `mod_version=1.7.1` in `gradle.properties`.
- A short changelog section (README or release notes, whichever the repo uses for 1.7.0): the
  share fixes, the speed-ups, the setup help, the render layer bypass, the new beacon defaults.
- Final `./gradlew build`.
- Tell Sky: the branch `fixes-1.7.1` is ready to try, then to merge into `mc26.3` (and how), and
  that nothing has been pushed.

---

## Reference: everything found, and where it is handled

| Id | What | Phase |
|---|---|---|
| S1 | Hidden MiniHUD sizes crash the player who adds a share | 1.1 |
| S2 | Private share names unchecked (`@a`) | 1.3 |
| S3 | Shared sizes up to 60M vs typed 1M | 1.5 |
| S4 | MiniHUD type and text values from chat unchecked | 1.2 |
| S5 | Action bar not skipped; replaced line loses report/secure marker (blocked players: check in game) | 1.6, check |
| S6 | Old `SMS1:` path skips checks | 1.6 |
| S7 | `privateShareCommand` unchecked | 1.4 |
| S8 | Auto swap may trip anti-cheat | note only |
| B1 | Full edit screen saves invalid shapes | 1.7 |
| B2 | Presets unbounded | 1.8 |
| B3 | Swap cooldown | 5.7 |
| B4 | One bad MiniHUD shape stops all reading | 3.1 |
| B5 | Minimap mixins warn without the minimap | 5.4 |
| B6 | Screen listeners may pile up on resize (unverified) | 5.7 |
| P1 | Lists rebuilt and sorted every frame, twice | 2.2 |
| P2 | Shapes panel rebuilds its list 10+ times a frame | 2.3 |
| P3 | Geometry and bounds rebuilt on every read | 2.1 |
| P4 | Curve points per frame; ellipse hover ring | 2.4 |
| P5 | MiniHUD polled twice a second | 3.1 |
| P6 | Beacons scan everything every second, to the world top | 3.2 |
| P7 | Hide all writes files once per shape | 3.3 |
| P8 | Cave-mode minimap reach 4,096 | 3.4 |
| P9 | Small allocations (boxing, labels, item stacks, double parse) | 2.5, 3.5, 5.7 |
| D1–D9 | Duplicated code | 3.5, 5.1–5.8 |
| New | Spawn proofing setup check and render layer bypass | 4 |

Already sound, leave alone: file and folder names, dimension ids, chat line length cap and parser,
nothing added without a click, the private send queue, spawn proofing's patch outlines,
the Litematica hooks and in-place placement updates, the light level filter, the Nether biome check.
