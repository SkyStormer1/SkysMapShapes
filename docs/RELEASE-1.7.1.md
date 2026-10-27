# 1.7.1

For the GitHub release. Minecraft 26.3.

## Sharing is safer

- A shared shape can no longer carry hidden MiniHUD sizes that are too big for the game: the finished
  shape is measured before MiniHUD gets it, and a big but allowed one asks you to press again first.
- Names, MiniHUD kinds and text values in a shared line are checked before anything is made, and
  shared sizes are held to the same limits as typed ones.
- A private share only goes to a real player name, and `privateShareCommand` must be a plain command.
- Lines from 1.2.0 and earlier (`SMS1:` on the end) are no longer read: they skipped those checks.
- The action bar is never read as chat.

## Fixes

- The full Edit screen no longer saves a shape that the add screen would refuse.
- Presets are kept within the same limits as the settings screen.
- One MiniHUD shape that cannot be read no longer stops the rest from showing.
- A short click just after Easy Place swaps blocks is no longer ignored.
- Without Xaero's Minimap, the log no longer warns about its hooks.

## Faster

- Shapes, outlines and the shapes list are worked out when they change, not every frame.
- MiniHUD is read when it changes a shape (and every ten seconds besides), not twice a second.
- Beacons are no longer looked for everywhere once a second: each chunk is read when it comes into
  range or a block in it changes, a few at a time, and a beam is read only between From Y and To Y.
- In cave mode, the minimap only looks at shapes it can actually show.
- **Hide all** writes each file once, not once per shape.

## Spawn proofing

- **Setup help.** If something stops spawn proofing working, one chat line says what, with a
  **[Fix it]** button. The light levels screen lists every check, with a **Fix** button each and
  **Fix all**.
- **Render layers no longer get in the way.** The spots are drawn, aimed at and Easy Placed whatever
  Litematica's render layers are set to; the rest of the schematic still follows the layers.

## Beacons

- New defaults: **Distance** 12 chunks and **Up to** 200 beacons, which is quicker on big bases.
  Settings you already saved are kept.
