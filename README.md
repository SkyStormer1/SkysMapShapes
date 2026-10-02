<p align="center"><img src="src/main/resources/assets/skysmapshapes/icon.png" width="128" alt="Sky's Map Shapes icon"></p>

<h1 align="center">Sky's Map Shapes</h1>

<p align="center">
  <a href="https://github.com/SkyStormer1/SkysMapShapes/releases/latest"><img src="https://img.shields.io/github/v/release/SkyStormer1/SkysMapShapes?label=Download%20the%20latest%20version&style=for-the-badge&color=2ea44f" alt="Download the latest version"></a>
</p>

<p align="center">
  <b><a href="https://github.com/SkyStormer1/SkysMapShapes/releases/latest">⬇ Download the mod jar</a></b> · Minecraft 26.2 · Fabric · client-side
</p>

A client-side Fabric mod that draws labelled circles, squares and other shapes on **Xaero's World
Map** and **Xaero's Minimap**, sized in blocks. Made for seeing despawn spheres around a farm or an
AFK spot, and useful for anything else you want marked out: a build area, a claim, the range of a
beacon.

## What it does

- **Add a shape anywhere.** Right-click the world map and choose **Add shape here**, right-click
  one of your waypoints to centre a shape on it, or type in the coordinates yourself. With
  [Sky's Structure Map](https://github.com/SkyStormer1/SkysStructureMap) or
  [Sky's Map Exposer](https://github.com/SkyStormer1/SkysMapExposer) installed, structures and
  BlueMap markers have **Add shape here** too.
- **Six kinds of shape:** circle, square, rectangle, diamond, octagon and ellipse. Circles,
  squares, diamonds and octagons are sized by their radius in blocks; rectangles and ellipses by
  their width and length.
- **Presets in one click.** Two are set up to begin with, for vanilla's despawn distances: instant
  despawn beyond 128 blocks, and no despawning within 32. Every preset can be renamed, resized and
  recoloured, because servers change these distances.
- **Label, colour, thickness and fill** for each shape, with a global thickness scale for all of
  them at once.
- **Hover an outline** to see what it is, and right-click it to edit, hide or delete. Two shapes on
  top of each other are both offered.
- **A shapes list**, opened from the settings or a key you set in Controls: search it, show or hide shapes,
  jump the map to one, edit or delete it. Hidden shapes stay listed, so they are easy to bring
  back. **Hide all** remembers which shapes it hid, and **Show all** brings back only those, so
  shapes you had hidden yourself stay hidden. Press **Show all** again to show every shape.
- **Share a shape with the people you play with**, the way Xaero shares a waypoint. Right-click it
  and choose **Share in chat…**, then pick who gets it:
  - **Everyone in chat**, as one ordinary line that everyone can see.
  - **Privately, to the players you pick.** Search the online players, tick as many as you like,
    and each gets it as a private message that nobody else sees. They are sent one every half
    second, so a server does not mistake it for spam.

  The line is plain words anyone can read, such as
  `Map shape AFK spot: circle r128 at 250 -96 (Nether) · orange`. Anyone with this mod sees an
  **[Add to my map]** button beside it that opens the shape for them to look at before keeping it;
  anyone without it just reads the line. The shape carries its own
  dimension, so a Nether shape shared while they are in the Overworld is added to their Nether
  map, at the Nether coordinates it had for you. Nothing is scaled, and nothing is added to
  anyone's map without them clicking.
- **Shapes sit under your waypoints**, and a smaller shape always draws on top of a larger one.
- Shapes are saved per server and per dimension.

## With MiniHUD

MiniHUD is optional. When it is installed, this mod and MiniHUD work as one:

- **MiniHUD's shapes are drawn on your map** as outlines: a sphere is its widest circle, a prism or
  pyramid its base, a box its rectangle, a line its line. A shape lying sideways is the strip it
  covers.
- **Kept in step.** Shapes in the dimension you are in are read from MiniHUD twice a second, so
  adding, moving or deleting one in MiniHUD shows on the map straight away. Other dimensions come
  from the files MiniHUD saves.
- **Shown even when MiniHUD's own shape renderer is off**, so you can keep the world clear and
  still see everything on the map.
- **Edit** on a MiniHUD shape opens MiniHUD's own Shape Editor on it.
- **Hide it where you want.** Right-click a MiniHUD shape for **Hide in both**, **Hide on the map
  only** or **Hide in MiniHUD only** (it goes from the world but stays on the map). In the shapes
  list, **Hide…** on a MiniHUD shape has a switch for each. The **Hide all: MiniHUD** setting says
  whether **Hide all** and **Show all** switch MiniHUD's shapes off and on in MiniHUD too.
- **Delete it from MiniHUD**, which takes it off the map too.
- **Make a shape in MiniHUD from the map.** When adding one, switch **Make it in** to MiniHUD, and
  choose what it becomes in the world.
- **Move a shape you already have into MiniHUD**, from its right-click menu or its Edit screen. It
  becomes an ordinary MiniHUD shape and leaves this mod's map, so it is not drawn twice. It only
  leaves once MiniHUD has it at its full size.
- **Any dimension.** Deleting, switching on or off, making and moving work for shapes in other
  dimensions too, and shared MiniHUD shapes can be added to them. Those changes go into MiniHUD's
  saved file for that dimension, which MiniHUD loads when you go there. Only MiniHUD's Shape
  Editor needs you to be in the shape's dimension.
- **Share MiniHUD shapes too.** The shared shape carries MiniHUD's own kind of shape and the height
  it sits at: someone with MiniHUD gets the same shape in their world, and someone without it still
  gets the outline on their map.

Map shapes are flat, so one made in MiniHUD is given a height, at your feet (or height 64 for
another dimension), and you choose how it stands up:

| Shape on the map | In MiniHUD, as |
|:--|:--|
| Circle | Cylinder, sphere or cone |
| Square | Prism or pyramid |
| Diamond | Prism or pyramid |
| Octagon | Prism or pyramid |
| Rectangle | Box |
| Ellipse | Ellipsoid |

Cylinders, prisms and boxes are 256 blocks tall, centred on that height; spheres and ellipsoids are
centred on it; cones and pyramids rise from it, as tall as they are wide from the middle. All of it
can be changed afterwards in MiniHUD's editor. MiniHUD's ellipsoids are at most 2048 blocks north to
south, so a longer ellipse stays on this mod's map, and you are warned before moving a shape so big
that MiniHUD drawing it may slow the game.

## Installing

1. [Fabric Loader](https://fabricmc.net/use/) for Minecraft 26.2.
2. [Fabric API](https://modrinth.com/mod/fabric-api) and
   [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin).
3. [Xaero's World Map](https://modrinth.com/mod/xaeros-world-map).
4. **[This mod's jar](https://github.com/SkyStormer1/SkysMapShapes/releases/latest)**, into your
   `mods` folder.

Optional: [Xaero's Minimap](https://modrinth.com/mod/xaeros-minimap) for shapes on the minimap,
[MiniHUD](https://modrinth.com/mod/minihud) for everything above, and
[Mod Menu](https://modrinth.com/mod/modmenu) to reach the settings from the mods list.

Client-side only: it does nothing on the server and works on any server you join.

## Using it

| To do this | Do that |
|:--|:--|
| Add a shape | Right-click the world map → **Add shape here** |
| Add one on a waypoint | Right-click the waypoint → **Add shape here** |
| Add one on a structure or BlueMap marker | Right-click it → **Add shape here** (with Sky's Structure Map or Sky's Map Exposer) |
| See all your shapes | The **Shapes** panel on the world map, or the full list from a key or the settings |
| Edit or delete one | Right-click its outline or its label, or use the list |
| Hide one for now | Right-click it → **Hide** (or a choice of where, for a MiniHUD shape), then **Show** it from the list |
| Send one to your friends | Right-click it → **Share in chat…** |
| Change the settings | Mod Menu → Sky's Map Shapes → cog |
| Open the list with a key | Set one in Options → Controls → Sky's Map Shapes |

Shapes live in `config/skysmapshapes/<server>.json`, and the settings in
`config/skysmapshapes.json`. A private share uses `/tell`; if your server uses something else, set
`privateShareCommand` in that settings file to `msg`, `w`, or whatever it takes.

## Notes

- A circle on the map is a flat slice of a sphere, taken at the height of its centre. Standing far
  above or below it, the real range at your height is smaller.
- Shapes show on the minimap everywhere, including underground and below the Nether roof, where
  the minimap draws from its own records rather than the world map's.
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
