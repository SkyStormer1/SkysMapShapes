<p align="center"><img src="src/main/resources/assets/skysmapshapes/icon.png" width="128" alt="Sky's Map Shapes icon"></p>

# Sky's Map Shapes

A client-side Fabric mod for Minecraft 26.2 that draws labelled circles, squares and other shapes
on **Xaero's World Map** and **Xaero's Minimap**, sized in blocks. Made for seeing despawn spheres
around a farm or an AFK spot, and useful for anything else you want marked out: a build area, a
claim, the range of a beacon.

**[Download the latest version](https://github.com/SkyStormer1/SkysMapShapes/releases/latest)**

## What it does

- **Add a shape anywhere.** Right-click the world map and choose **Add shape here**, right-click
  one of your waypoints to centre a shape on it, or type in the coordinates yourself.
- **Six kinds of shape:** circle, square, rectangle, diamond, octagon and ellipse. Circles,
  squares, diamonds and octagons are sized by their radius in blocks; rectangles and ellipses by
  their width and length.
- **Presets in one click.** Two are set up to begin with, for vanilla's despawn distances: instant
  despawn beyond 128 blocks, and no despawning within 32. **Add all presets** puts both rings
  around a spot at once. Every preset can be renamed, resized and recoloured, because servers
  change these distances.
- **Label, colour, thickness and fill** for each shape, with a global thickness scale for all of
  them at once.
- **Hover an outline** to see what it is, and right-click it to edit, hide or delete it. Two
  shapes on top of each other are both offered.
- **A shapes list**, from the **Shapes** button on the world map: search it, show or hide shapes,
  jump the map to one, edit or delete it. Hidden shapes stay listed, so they are easy to bring
  back.
- **Shapes sit under your waypoints**, and a smaller shape always draws on top of a larger one.
- Shapes are saved per server and per dimension.

## With MiniHUD

MiniHUD is optional. When it is installed, its shapes are drawn on your map as outlines:

- **Every kind of MiniHUD shape**, as its footprint seen from above: a sphere is its widest
  circle, a prism or pyramid its base, a box its rectangle, a line its line. A shape lying
  sideways is the strip it covers.
- **Kept in step.** Shapes in the dimension you are in are read from MiniHUD twice a second, so
  adding, moving or deleting one in MiniHUD shows on the map straight away. Other dimensions come
  from the files MiniHUD saves. Your own shapes are never added to MiniHUD unless you ask.
- **Shown even when MiniHUD's own shape renderer is off**, so you can keep the world clear and
  still see everything on the map.
- **Edit** on a MiniHUD shape opens MiniHUD's own Shape Editor on it.
- **Hide** switches it off in MiniHUD too, so it goes from the world as well. Right-click for
  **Hide on the map only**, or turn off **Hide in MiniHUD** in the settings.
- **Make a shape in MiniHUD from the map.** When adding a shape, switch **Make it in** to MiniHUD
  and it becomes an ordinary MiniHUD shape, in the world as well as on the map. Map shapes are
  flat, so it is put at your feet: circles and ellipses become spheres and ellipsoids centred
  there, and the others become 256-block-tall prisms and boxes around it, all adjustable
  afterwards in MiniHUD.

## Installing

1. [Fabric Loader](https://fabricmc.net/use/) for Minecraft 26.2.
2. [Fabric API](https://modrinth.com/mod/fabric-api) and
   [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin).
3. [Xaero's World Map](https://modrinth.com/mod/xaeros-world-map).
4. This mod's jar, into your `mods` folder.

Optional: [Xaero's Minimap](https://modrinth.com/mod/xaeros-minimap) for shapes on the minimap,
[MiniHUD](https://modrinth.com/mod/minihud) for everything above, and
[Mod Menu](https://modrinth.com/mod/modmenu) to reach the settings from the mods list.

Client-side only: it does nothing on the server and works on any server you join.

## Using it

| To do this | Do that |
|:--|:--|
| Add a shape | Right-click the world map → **Add shape here** |
| Add one on a waypoint | Right-click the waypoint → **Add shape here** |
| See all your shapes | **Shapes** button, top-left of the world map |
| Edit or delete one | Right-click its outline or its label, or use the list |
| Hide one for now | Right-click it → **Hide**, then **Show** it from the list |
| Change the settings | Mod Menu → Sky's Map Shapes → cog |
| Open the list with a key | Set one in Options → Controls → Sky's Map Shapes |

Shapes live in `config/skysmapshapes/<server>.json`, and the settings in
`config/skysmapshapes.json`.

## Notes

- A circle on the map is a flat slice of a sphere, taken at the height of its centre. Standing far
  above or below it, the real range at your height is smaller.
- Shapes are drawn on the map only, never in the world. For that, make them in MiniHUD.

## Building

```
./gradlew build
```

The jar lands in `build/libs`. `./gradlew runClient` starts a development client;
`./gradlew runClient -PnotIde` does the same without Minecraft's development-environment flag,
which MaLiLib (MiniHUD's library) switches on and which crashes some graphics drivers.

## Licence

MIT, see [LICENSE](LICENSE). Written from scratch. Xaero's World Map, Xaero's Minimap and MiniHUD
are only compiled against or read at run time; no code from them is included or copied.
