# Slate Building (`slate_building`)

A survival-friendly building mod on top of Slate. Every block has one family of **shapes** (stairs, slabs,
vertical slabs, walls, steps, panels and more), and you switch a held stack between them with a radial
**swap wheel** on Left Alt. A see-through **placement ghost** shows what you are about to place. **R** opens the
**build menu**: shape and chisel wheels on the left, and 20 **building modes** on the right (fill, walls, sphere,
replace, copy/paste, move, mirror and more), each selected with right-clicks in the world, previewed as ghosts
and undoable. Modes are unlocked by tiered **tools** in a **Builder's Toolbox**, and they cost real materials,
following one anti-dupe rule: every shape is worth exactly one block of its material.

Unlike the other Slate modules, Building adds blocks and items, so the **server and every player need it**
(same version). It requires Slate (`slate`). Slate Config (settings tab), JEI and BetterInventory are optional.
It runs on NeoForge and Fabric.

**Quick start:** hold a stack of planks and hold **Left Alt**. Move the mouse onto *Stairs* and let go, and the
stack is now oak stairs, same count. Craft a **Builder's Toolbox** (a chest and 3 copper ingots) and a
**Copper Trowel**, and put the trowel in the toolbox. Then hold a block, press **R** and click **Fill**.
Right-click two corners, then right-click a third time to build.

## Shapes and unified variants

| Shape | What it is | Units |
|---|---|---|
| Full block | The material itself (the centre of every wheel) | 1 |
| Stairs | Vanilla stairs, including inner and outer corners | 1 |
| Slab | Vanilla slab. Two of the same material merge into a double slab | 1 (double: 2) |
| Vertical Slab | Half a block standing against a side. Two of the same material merge into a full block | 1 (double: 2) |
| Vertical Stairs | A full-height L: the block minus one quarter column. The notch opens towards you when placed | 1 |
| Wall | Vanilla wall (posts, low/tall sides, connections) | 1 |
| Fence | Vanilla fence. Joins every fence, wooden or not | 1 |
| Step | Create's copycat step: full width, half height, half depth | 1 |
| Panel | Create's copycat panel: a 3 px plate against the face you click | 1 |
| Fence Gate | Vanilla gate (opens, redstone, sits lower in walls) | 1 |
| Vertical Step | An 8 × 16 × 8 quarter column in the corner you point at. It fills the notch of vertical stairs | 1 |
| Post | An 8 × 8 beam along the axis of the clicked face. Logs keep their bark along the beam | 1 |
| Layer | 1–8 layers of 2 px that grow from the clicked face. Click the growing face again to add a layer | n layers = n |
| Pane | A 2 px pane that joins panes, bars and walls | 1 |

**Native or custom.** A shape is *native* when a vanilla or modded block already exists for it (oak stairs,
stone brick wall, a mod's marble slab). The swap wheel then hands out that native item. Otherwise Slate Building
uses its **own shape block**, which stores the material and borrows everything from it: the look (quads cut
from the material's model, with its tint, cutout or translucency), sound, hardness, the right tool, light,
friction and explosion resistance. The item is called "*Material Shape*" (e.g. "Glass Vertical Slab"). There are
no crafting recipes for the custom shapes: hold the block and swap. A shape item without a material places nothing.

Natives are found automatically, the same way on client and server: vanilla block families, every stairs
block's base block, slabs/walls/fences/gates by name (`oak_slab` → `oak_stairs` → oak planks, else
`X`/`Xs`/`X_planks`/`X_block`/`X_bricks`/`X_tiles`), `variantOverrides` in the server config, and items re-pointed
by DiagonalFences/Walls/Windows. Only stairs, slabs, walls, fences and gates are discovered. Panes, bars,
carpets and trapdoors are not unified.

**What counts as a material:** a full cube that renders as a model, has its own block item and no block entity,
and is not in `#slate_building:not_material` (bedrock, barrier, command/structure/jigsaw blocks, spawners,
vaults, end portal frame, reinforced deepslate, light, budding amethyst, infested blocks) or the server's
`materialDenylist`. The block tag `#slate_building:material` and `materialAllowlist` force a block in.

### The economy (why nothing duplicates)

- Every shape item and every freshly placed shape is worth **one unit** of its material. A double slab or double
  vertical slab is worth 2; a layer block is worth its layer count.
- **Swapping keeps the count, 1:1**: 16 planks become 16 stairs and back.
- **Breaking any shape drops the material**, one per unit (a stone stair drops stone, not cobblestone, and a
  double slab drops 2), and only when your tool could harvest the material. This covers natives too: breaking
  oak stairs drops oak planks.
- **Fair recipes** (`rebalanceRecipes`, on by default): recipes that make natives at better than 1:1 are
  rewritten to give one shape per material block used. 3 planks → 6 slabs becomes 3 → 3, 6 planks → 4 stairs
  becomes 6 → 6, and stonecutting gives 1. Recipes whose inputs cannot be counted are left alone. Takes effect
  on `/reload` or restart.
- **Remove vanilla shapes** (`deleteNativeVariants`, off by default):
  - *Does:* hides native shape items from the creative tabs, creative search and JEI (JEI lists Slate Building's
    own item of that material in their place), and removes the recipes that **make** natives (for multi-output
    recipes, only the native outputs). Takes effect on `/reload` or restart.
  - *Does not:* unregister anything, convert items already in inventories or chests, or touch blocks already
    placed (they keep working and still drop their material). The wheel, build menu, chisel and pick-block still
    hand out the native item where one exists, so every recipe that **uses** oak slabs or `#wooden_slabs` still
    works. It only acts while *Unify shapes* is on.
- Building modes and in-world reshaping pay the same way (see [Toolbox](#builders-toolbox)). Creative costs
  nothing and drops nothing.

## Placement preview

While you hold a shape (or any block, with *Ghost for every block*) and look at a block, a translucent ghost
shows exactly what a right-click would place: slab merging, replaceable plants, the upper half of doors, the
head of beds. A placement the game would refuse shows as a red outline. Nothing shows when the click would do
something else (open a chest, flip a lever), while a building mode has a selection pending, in spectator or
adventure mode, or while BridgingMod draws its own target. With Mirror or Radial active, the mirrored copies
appear too, a little fainter. Building-mode plans use the same look: placements get an accent rim,
replacements are tinted amber, removals are red and hatched.

| Setting (Placement preview) | Key | Default |
|---|---|---|
| Show placement ghost | `preview.enabled` | on |
| Ghost for every block | `preview.allBlocks` | off |
| Ghost opacity | `preview.opacity` | 45% |
| Ghost colour (0% grey, 100% true colour) | `preview.saturation` | 60% |
| Ghost outline | `preview.outline` | on |
| Gentle pulse | `preview.pulse` | on |
| Ghost block limit (larger plans draw outlines only) | `preview.maxBlocks` | 4096 |
| Show mirrored copies | `preview.showMirrored` | on |

## Quick-swap wheel (Left Alt)

Hold the swap key with a block or shape in your main hand. The wheel opens around the crosshair as an overlay,
so you can keep walking. The centre is the full block and the slices are the shapes of that material, drawn as
the real items with the current shape highlighted. The label underneath shows the name and count, or a lock
reason such as "Needs: Hammer".

| Control | What it does |
|---|---|
| Hold the swap key | Open the wheel (closes when you let go) |
| Move the mouse | Moves a cursor on the wheel. The camera stays still |
| Release the key | Applies the hovered slice. Tapping without moving does nothing; moving out and back to the centre picks the full block |
| Left click / right click | Previous / next wheel page (page dots and the wheel name show where you are) |
| Scroll | Step around the ring |
| Hotbar keys 1–9 | Point at that slice (applied on release, or at once with *Release to apply* off) |
| Esc | Close without changing anything |

With *Release to apply* off, a left click applies (the wheel stays open) and releasing the key only closes it.

**Wheels.** Default pages: **Shapes** (stairs, slab, vertical slab, vertical stairs, wall, fence, step, panel)
and **More** (fence gate, vertical step, post, layer, pane). A wheel with more slices than *Slices per page*
(4–12, default 8) continues on another page. Shapes the material lacks are hidden, or greyed out with *Hide
missing shapes* off. When you own a Chisel, the material's **chisel groups** follow as extra pages (see
[Chisel groups](#chisel-groups)). Edit the wheels in **Edit wheels…** (settings, or the build menu button): add,
rename, remove and reorder wheels (drag or Alt+↑/↓), pick each wheel's shapes from a palette, see a live
preview in the held material, set slices per page, or reset to the defaults. Changes save immediately.

**In-world reshape (Hammer).** With the Builder's Toolbox or a building tool in your main hand, look at a placed block and hold the swap
key. The same wheel appears for that block, and picking a shape rebuilds it in place with its material and,
where the shapes share one, its orientation. This needs a Hammer of any tier in your toolbox (creative counts).
Going up in units costs the difference from your materials, and going down refunds it: a double slab turned into
stairs gives back 1. Each reshape wears the hammer by 1. In survival, a block that does not drop exactly itself
cannot be reshaped (natural stone, glass, ice, ores, grass), because that would be a free silk touch.

**Pick block.** In survival, middle-clicking a shape selects the hotbar slot that holds the same material in any
shape (preferring the same shape) and swaps it to the picked shape. Without a matching hotbar stack, vanilla's
pick block runs. Turn this off with *Pick block picks shapes*.

## Build menu (R)

Opens while you hold a block, a shape, the toolbox or a building tool, or while a mode is active (*Build menu key:
Smart*). Set it to *Always* to open it anywhere; the Slate hub's **Build menu** entry also works. It is not a
pause screen: the world keeps running behind it.

- **Left (about 40%): Shapes and Chisel wheels** for the held stack. Click a slice to swap. ◀ ▶ or PageUp /
  PageDown turn pages, and the search field makes matching slices glow and jumps to their page. The wheel does
  not react to scrolling. On short windows the two wheels become tabs. Without a Chisel, the Chisel wheel
  explains what unlocks it.
- **Right: Building modes.** The header holds the toolbox summary (your tools with tier pips; click it to open
  the toolbox), **Undo** / **Redo** with step counts, and **Edit wheels**. Below it, a table of every mode with
  icon, name, description, required tool, keybind and a lock reason when locked. Click a row to activate the
  mode and close the menu. Shift+click keeps the menu open, and right-click only selects the row so you can
  change its options. Clicking the active mode turns it off. The panel under the table edits the selected mode's
  options (saved per mode in `building.json`), with a reset button.
- **Keyboard:** Tab cycles the areas, the arrow keys move in the table or around a focused wheel, Enter
  activates, `/` searches, and R or Esc closes the menu.

## Building modes

Each mode has its own keybind (unbound by default). Pressing it again turns the mode off. Tiers are Copper 1,
Iron 2, Diamond 3, Netherite 4. Option defaults are in brackets.

| Mode | Tool | What it does | Options |
|---|---|---|---|
| Fill | Trowel 1 | Fill the box with the held block | Replace [Air and plants], Blocks [Held block] |
| Walls | Trowel 1 | The four vertical sides of the box | Thickness 1–4 [1], Replace, Blocks |
| Line | Trowel 1 | A straight 3D line from corner to corner | Thickness 1–3 [1], Blocks |
| Extend | Trowel 1 | Construction wand: one block is added in front of every connected matching block of the clicked face (nearest first). Up to 16 / 64 / 256 / 1024 by trowel tier. Adds copies of the source blocks when your hand is empty | Match: Exact state / [Same block] / Any block; Lock: [Free] / Horizontal / Vertical |
| Hollow Box | Trowel 2 | The shell of the box | Thickness 1–4 [1], Replace, Blocks |
| Outline | Trowel 2 | The twelve edges | Replace, Blocks |
| Cylinder | Trowel 2 | Corner A = base centre, B = radius and height | Hollow [off], Blocks, Replace |
| Sphere | Trowel 3 | A = centre, B = a point on the surface | Hollow [off], Part: [Full] / Dome / Bowl, Blocks, Replace |
| Replace | Brush 1 | Blocks in the box that match the filter become the held block. With *Keep shape*, oak stairs become spruce stairs facing the same way | Filter: [Clicked block] / Any solid block / Offhand block; Keep shape [on] |
| Overlay | Brush 2 | Cover the top surface of every column | Depth 1–3 [1], Mode: [On top] / Replace top |
| Clear | Hammer 1 | Break everything in the box. Drops go to you (top layer first) | Filter: [Everything] / Clicked block / Plants; Keep fluids [on] |
| Reshape | Hammer 2 | Every block in the box takes the held item's shape, keeping its material | Filter: [Everything] / Clicked material |
| Copy | Blueprint 1 | Copy the box to your clipboard | Include air [off] |
| Paste | Blueprint 1 | Place the clipboard against the clicked face | Rotation [0°] / 90° / 180° / 270°, Mirror [None] / X / Z, Include air, Replace |
| Cut | Blueprint 2 | Copy, then clear (fluids stay) | none |
| Stack | Blueprint 3 | Repeat the box into free space along a direction | Count 1–16 [2], Spacing 0–8 [0], Direction [Where you look] / Up / Down / N / S / E / W |
| Move | Blueprint 3 | Lift the box and put it down somewhere else | Rotation, Mirror |
| Mirror | Square 1 | Your normal placing and breaking is mirrored across a plane through the centre | Axis [X] / Z / X and Z; Mirror breaking [on] |
| Radial | Square 3 | N-fold rotational symmetry around the centre | Slices 2–8 [4]; Mirror breaking [on] |
| Measure | none | Size, volume and distance; changes nothing (also works in spectator) | none |

Replace choices: *Air only*, *Air and plants* (replaceables such as grass, water and snow), *Everything*. Blocks
choices: *Held block*, *Hotbar, random* (weighted by stack size), *Hotbar, checkered*. Stacks that cannot be
built with (non-blocks, doors, beds, containers in survival, shape items without a material) are left out of a
hotbar palette. A held shape builds in that shape.

### How you drive a mode

| Kind | Modes | Clicks |
|---|---|---|
| Area | fill, walls, line, hollow, outline, cylinder, sphere, replace, overlay, clear, reshape, copy, cut, stack | Right-click corner A. The box follows the crosshair. Right-click corner B, and the plan shows as ghosts. Right-click again to apply |
| Point | extend, paste | The preview follows the crosshair. Right-click fixes it, right-click again applies |
| Move | move | Corner A, corner B, then the destination follows the crosshair. Right-click fixes it, right-click again applies |
| Toggle | mirror, radial | Stays on while you build normally. It centres on the block you look at when you turn it on; the plane or centre is drawn in the world |
| Measure | measure | Two right-clicks. A third starts a new measurement, and left click clears it |

| In the world | What it does |
|---|---|
| Right click | Next step (corner, fix, apply). With *Right-click applies* off, only the Confirm key applies (right-click still does while Confirm is unbound) |
| Confirm key | Same as the next right-click. In Mirror/Radial it re-centres on the block you look at |
| Left click, Esc, Cancel key | Clear the pending selection without mining. The Cancel key also stops a running operation (what was built stays and can be undone) |
| Ctrl + scroll | Push or pull the face of the box you look at (radius/height on cylinders and spheres). On a fixed paste/move point: nudge it along your view |
| Shift + scroll | Step the main option: thickness, count, depth, slices, rotation (paste, move) or axis (mirror). Radius on spheres and cylinders |
| Scroll while aiming at air | Distance of a corner placed in the air (*Corner distance in the air*, default 4) |
| Arrow keys | Nudge the selection, paste point or symmetry centre one block. Shift+↑/↓ or PageUp/PageDown move it vertically |
| Leave-mode key, the mode's own key, or its row in the menu | Turn the mode off |

Corners can be picked at your normal reach plus the reach bonus of your tier. Clicking air puts the corner in
the air at the corner distance. While any mode except Mirror and Radial is active, right-clicking with a block, a
building tool or an empty hand selects instead of placing. Food, bows, the toolbox and (with nothing selected)
chests and barrels keep their own right-click (*Open containers*). The mode ends on death, on a dimension
change, on disconnect and in spectator (except Measure).

**HUD.** A chip (default top centre) shows the mode, live stats such as `12 × 4 × 8 · 384 blocks · 384/512 Oak
Planks`, and key hints for the current step. It shows a progress bar while the server builds, and a result line
afterwards ("Placed 384 blocks · ran out of materials for 12", with an undo hint).

**What the server does.** It checks everything again: the mode is unlocked and allowed, the selection is in
reach, loaded and within your limits, and you are not in spectator or adventure. Then it builds a few blocks per
tick (your speed, shared with a server-wide budget), one operation at a time per player. Every position goes
through protection (spawn, world border, claims) and is paid from your materials. With *Place what you can
afford* (default), a short supply builds as much as it can; otherwise the operation is refused. In survival it
skips containers and other block entities (unless *Touch containers* is on), unbreakable blocks, and blocks your
Hammer's tier could not harvest. Broken blocks drop their normal loot, rolled with an unenchanted vanilla tool of
your Hammer's tier (Copper = stone tools), so there is no free silk touch or fortune. Copies and pastes in
survival skip blocks that have no item (filled cauldrons, potted plants, fluids), and crops come back freshly
planted. Container contents are only copied in creative. Symmetry copies are paid like placements, only go into
replaceable space, and mirrored breaks use your own tool.

## Undo and redo

The **Undo** / **Redo** keys (unbound by default), the build menu buttons, or `/slatebuild undo|redo`. Only
building-mode operations are in the history: normal placing, swaps, in-world reshapes, chisels and symmetry copies
are not. Undo reverts an operation newest block first, and only where the block is still what the operation left
there. It refunds what you paid and takes back what you got (drops, refunds). What it reverted becomes the redo
step. The result line says how many positions were skipped ("had changed since", "you lack the items").
Operations done in creative can only be undone or redone in creative.

History is per player and kept in server memory, so it is gone after you log out or the server restarts. Depth is
`undoDepth` (10) plus 10 per Memory upgrade, capped by `maxUndoBlocks` positions and `maxUndoDataKiB` of container
data. An operation larger than either cap cannot be undone.

## Builder's Toolbox

The **Builder's Toolbox** (`slate_building:toolbox`) holds **6 tool slots** (one per tool type, any tier), **4
upgrade slots** and a **9-slot pouch**. Use it to open it. In an inventory, right-click tools, upgrades or blocks
onto it (or it onto them) to put them in, like a bundle. Sneak-use it on a chest (with a Supply Link) to link that
chest. Its tooltip shows the tools, upgrades, pouch and link. If the item is destroyed (lava, cactus) it drops what
it held, like a shulker box. The screen shows the slots on the left (with the tier of each tool), and on the right, per tool, everything it
unlocks (lit when unlocked) plus your limits, with upgrade-boosted values highlighted.

A toolbox counts when you **carry** it: BetterInventory's toolbox slot, then the off hand, the hotbar, the main
inventory. Only the first one found is used. In creative (with *Creative gets everything*) you have every tool at
Netherite without a toolbox. With *Modes need tools* off, everyone has every tool at Netherite, and upgrades still
come from a carried toolbox.

### Tools

Items `slate_building:<tier>_<tool>` with tiers copper, iron, diamond and netherite. Durability 250 / 750 / 2000 /
5000. A tool loses 1 durability per 4 changed blocks (`durabilityPerBlocks`, half with Efficiency). Tools take
Unbreaking and Mending, are repaired in an anvil with their tier material, and Netherite tools survive fire and
lava. A tool that breaks leaves the toolbox with a message.

| Tool | 1 Copper | 2 Iron | 3 Diamond |
|---|---|---|---|
| Trowel | Fill, Walls, Line, Extend | Hollow Box, Outline, Cylinder | Sphere |
| Hammer | Clear, reshape in place (swap wheel) | Reshape | none |
| Brush | Replace | Overlay | none |
| Blueprint | Copy, Paste | Cut | Stack, Move |
| Square | Mirror | none | Radial |
| Chisel | Chisel wheel (held blocks) | Chisel in place | none |

Higher tiers of every tool also raise your **limits**, which come from your highest-tier tool:

| Limit (server default) | Copper | Iron | Diamond | Netherite |
|---|---|---|---|---|
| Most blocks per operation | 256 | 2048 | 16384 | 65536 |
| Longest selection edge | 16 | 32 | 64 | 128 |
| Extra corner reach | +0 | +8 | +16 | +32 |
| Blocks per tick | 8 | 24 | 64 | 160 |
| Extend limit (by Trowel tier) | 16 | 64 | 256 | 1024 |
| Symmetry reach (by Square tier) | 16 | 32 | 64 | 128 |

Creative gets 262144 blocks per operation (`creativeMaxVolume`) and the Netherite values otherwise.
`/slatebuild limits` prints yours.

### Upgrades

Items `slate_building:<id>_upgrade`, one per slot. Several of one kind stack up to their maximum.

| Upgrade | Max | Effect |
|---|---|---|
| Reach | 2 | Corner reach bonus ×2 per upgrade (at least +8 per upgrade) |
| Capacity | 2 | Blocks per operation ×2 and selection edge +50% per upgrade (never above the creative cap) |
| Speed | 2 | Blocks per tick ×2 per upgrade |
| Memory | 4 | +10 undo steps per upgrade (`undoPerMemory`) |
| Supply Link | 1 | Operations take materials from a linked container |
| Magnet | 1 | Refunds and drops go to the pouch first, then the linked container, then your inventory |
| Efficiency | 1 | Tools wear half as fast |

**Where materials come from.** Operations and reshapes accept any stack of the same material, full block or any
shape, at one unit per item. They take from the **pouch** first, then your inventory (the paying hotbar slot, the
rest of the hotbar, the main inventory, the off hand; never the toolbox), then the **Supply Link** container.
Refunds and drops go to your inventory (pouch and linked container first with Magnet), and anything that does not
fit drops at your feet. The pouch holds building blocks only and can be turned off by the server (`allowPouch`).

**Supply Link.** With the upgrade installed, sneak-use the toolbox on a chest, barrel, shulker box, hopper,
dispenser or modded storage block to link it (a double chest links both halves). Sneak-use it again to unlink. The
link works in the same dimension, within 64 blocks (`supplyLinkRange`), while the chunk is loaded.

### Recipes

| Item | Recipe |
|---|---|
| Builder's Toolbox | Wooden chest + 3 copper ingots (shaped) |
| Trowel | 3 tier material + 1 stick |
| Hammer | 3 tier material + 2 sticks |
| Brush | 3 tier material + 1 wool + 1 stick |
| Chisel | 2 tier material + 1 stick |
| Blueprint | 5 paper, 3 tier material, 1 lapis |
| Square | 3 tier material over 2 planks |
| Netherite tools | Smithing table: the Diamond tool + Netherite Upgrade template + netherite ingot |
| Upgrades | 2 iron ingots + 2 redstone around a core: spyglass (Reach), chest (Capacity), blaze powder (Speed), book (Memory), ender pearl (Supply Link), compass (Magnet), diamond (Efficiency) |

Tier materials use the `c:` tags (`c:ingots/copper`, `c:ingots/iron`, `c:gems/diamond`), so modded ingots work. The
**Slate Building** creative tab holds an empty toolbox, a fully kitted one, every tool and upgrade, and one example
of each shape in oak planks and stone bricks.

## Chisel groups

A chisel swap changes a block's **material** and keeps its **shape** and count: a held stone slab becomes a
stone brick slab (the native slab where it exists, else Slate Building's), and 20 cracked stone bricks become 20
chiseled stone bricks. Each group is a page of the Alt wheel (after the shape pages) and of the build menu's
Chisel wheel.

- **Held stacks:** Copper Chisel or better. Wears the chisel by the stack count.
- **Placed blocks:** Iron Chisel or better (`chisel.inWorld`), through the in-world swap wheel (so a Hammer is needed
  to open it). The block must drop exactly itself before and after, and blocks with a block entity are refused.
  Wears the chisel by 1.

**Sources**, each producing its own pages (never merged), in page order: **Custom** (`building-chisel.json` plus the
shipped defaults), **Rechiseled**, **Chipped**, **Chisel** (Modern and Reborn), **Stonecutter** (blocks the
stonecutter turns into each other 1:1), **Block family** (vanilla chiseled / cracked / cut / mosaic / polished forms).
The mod sources are read from their data, so they only apply when those mods are installed.

**Rules** that every group obeys, whatever its source: members are plain full blocks (no block entities, doors,
beds, falling blocks or shapes); copper never mixes oxidation or wax states; and two blocks joined by a recipe
that is not 1:1 are never in one group (1 copper block → 4 cut copper keeps them apart). The shipped defaults
(Stone, Granite, Diorite, Andesite, Deepslate, Tuff, Sandstone, Red Sandstone, Quartz, Blackstone, Basalt, Nether
Bricks, End Stone, Purpur, Mud Bricks, and Copper per state) never link blocks that vanilla only converts by
smelting or by adding something: cobblestone and stone, and the smooth and mossy blocks, stay apart.

**`config/slate/building-chisel.json`** (server side, applied on `/reload` or `/slatebuild reload`):

```json
{
  "includeDefaults": true,
  "groups": [
    { "name": "Marble", "members": ["somemod:marble", "somemod:marble_bricks", "somemod:marble_tiles"] }
  ],
  "exclude": ["minecraft:chiseled_stone_bricks"]
}
```

`groups` are your own pages (`name`, or `key` for a translation key; unknown ids are skipped, so one file serves
several mod sets). `exclude` keeps blocks out of every group from every source. The group index is built on the
server and sent to players when they join and after a reload.

## Keybinds

All in the **Slate Building** category (Slate Config → Controls, or vanilla Controls).

| Keybind | Default |
|---|---|
| Quick swap wheel (hold) | Left Alt |
| Build menu | R |
| Undo build | unbound |
| Redo build | unbound |
| Confirm selection | unbound (right-click confirms) |
| Cancel selection | unbound (left click / Esc cancel) |
| Leave building mode | unbound |
| Mode: Fill … Mode: Measure (one per mode, 20) | unbound |

The keys inside the wheel (clicks, scroll, hotbar keys, Esc) and in modes (Ctrl/Shift+scroll, arrows, PageUp/PageDown)
are fixed.

**Shared keys.** Left Alt and R are popular: in the DF pack six mods use Left Alt (Create's toolbelt, Relics, Iron's
Spellbooks, Shoulder Surfing free look, BetterInventory's offhand, Create's modifier) and two use R (Iris' shader
reload, Iron's spell wheel). Slate Building takes these keys **exclusively only while it would use them**. The
swap key is claimed when the wheel would open (a block or shape in hand, or the in-world reshape conditions), and the
build menu key when *Build menu key* says so (Smart: holding a block, shape, tool or toolbox, or a mode is active).
At those moments the other mappings on the key are not pressed. The rest of the time they get the key as usual.
Releases always reach everyone, so nothing gets stuck. Turn the Alt claim off with *Swap key takes Alt*.

## Configuration

**Where:** Slate Config → **Gameplay → Building**, with secondary tabs General, Placement preview, Swap wheel, Build
menu & HUD, Building modes, Server: shapes, Server: building modes, Server: toolbox & chisel. It also opens from the
Slate hub (**Building settings**) and the NeoForge mod list's Config button. Without Slate Config, a standalone
Building settings screen shows the same rows. Client settings apply live. Server rules are editable in singleplayer
(and are pushed to LAN guests). On a server they show that server's rules, read-only.

### `config/slate/building.json` (client)

| Key | Default | Meaning | Settings row |
|---|---|---|---|
| `wheel.swapEnabled` | `true` | The Alt swap wheel | Swap wheel › Swap wheel |
| `wheel.wheels` | Shapes, More | Wheel pages: `[{name, entries: [shape ids]}]` | Swap wheel › Edit wheels… |
| `wheel.maxSlices` | `8` | Slices per page (4–12); longer wheels spill onto more pages | Swap wheel › Slices per page |
| `wheel.hideUnavailable` | `true` | Hide shapes the material lacks (off: grey them) | Swap wheel › Hide missing shapes |
| `wheel.scrollSelects` | `true` | Scroll steps around the ring | Swap wheel › Scroll selects |
| `wheel.clickSwitchesWheel` | `true` | Left / right click change page | Swap wheel › Clicks switch wheels |
| `wheel.releaseToSelect` | `true` | Releasing the key applies (off: click applies) | Swap wheel › Release to apply |
| `wheel.numberKeys` | `true` | Hotbar keys pick a slice | Swap wheel › Number keys |
| `wheel.wheelScale` | `1.0` | Wheel size (0.5–2) | Swap wheel › Wheel size |
| `wheel.labels` | `true` | Name and count under the wheel | Swap wheel › Labels |
| `wheel.includeChiselPage` | `true` | Chisel groups as extra pages | Swap wheel › Chisel pages |
| `wheel.pickBlockSwaps` | `true` | Middle-click selects the material and swaps its shape | General › Pick block picks shapes |
| `wheel.exclusiveSwapKey` | `true` | The swap key takes Alt while the wheel would open | General › Swap key takes Alt |
| `wheel.menuKeyContext` | `"SMART"` | When R opens the build menu: `SMART` or `ALWAYS` | General › Build menu key |
| `preview.*` | see [Placement preview](#placement-preview) | | Placement preview |
| `hud.enabled` | `true` | The mode chip | Build menu & HUD › Mode HUD |
| `hud.anchor` | `"TOP"` | `TOP_LEFT`, `TOP`, `TOP_RIGHT`, `LEFT`, `RIGHT`, `BOTTOM_LEFT`, `BOTTOM`, `BOTTOM_RIGHT` | Build menu & HUD › HUD position |
| `hud.scale` | `1.0` | Chip size (0.5–2) | Build menu & HUD › HUD size |
| `hud.actionBar` | `true` | Result line after an operation | Build menu & HUD › Result line |
| `hud.sounds` | `true` | Quiet clicks for the wheel, swaps and modes | General › Sounds |
| `hud.hints` | `true` | A short tip with the swap and menu keys the first times you hold a block that can change shape (shown at most twice, `hud.onboardingShown` counts) | General › Tips for new players |
| `modes.confirmWithRightClick` | `true` | A third right-click applies | Building modes › Right-click applies |
| `modes.livePreview` | `true` | Plan ghosts while the box still follows the crosshair (small selections) | Building modes › Live preview |
| `modes.labels` | `true` | Size labels above the selection | Building modes › Size labels |
| `modes.arrowNudge` | `true` | Arrow keys nudge | Building modes › Arrow keys nudge |
| `modes.escapeCancels` | `true` | Esc clears a pending selection before opening the pause menu | Building modes › Esc clears the selection |
| `modes.openContainers` | `true` | With nothing selected, right-clicking a chest opens it | Building modes › Open containers |
| `modes.airDistance` | `4` | Distance of a corner placed on air | Building modes › Corner distance in the air |
| `modes.params` | `{}` | Options per mode, e.g. `{"fill": {"replace": "AIR"}}` | Build menu options panel |
| `modes.lastMode` | `""` | Last used mode | file only |

### `config/slate/building-server.json` (server rules, synced to players)

Arrays marked *per tier* hold four values for Copper, Iron, Diamond and Netherite tools. In the settings they are
edited as text, e.g. `16, 32, 64, 128`. `/slatebuild reload` re-reads the file and sends it to everyone.

| Key | Default | Meaning | Settings row |
|---|---|---|---|
| `variants.unify` | `true` | Natives and Slate Building's shapes of one material form one family (natives drop their material, swap, and count as it) | Server: shapes › Unify shapes |
| `variants.customShapes` | `true` | Offer Slate Building's own shapes where no native exists | Server: shapes › Extra shapes |
| `variants.rebalanceRecipes` | `true` | Fair recipes (after `/reload`) | Server: shapes › Fair recipes |
| `variants.deleteNativeVariants` | `false` | Remove vanilla shapes (after `/reload`) | Server: shapes › Remove vanilla shapes |
| `variants.swapNeedsTool` | `false` | Swapping a held stack needs a Hammer (never in creative) | Server: shapes › Swapping needs a hammer |
| `variants.materialDenylist` | `[]` | Block ids or `#tags` that are never materials | file only |
| `variants.materialAllowlist` | `[]` | Block ids or `#tags` that are always materials (still need an item) | file only |
| `variants.variantOverrides` | `{}` | `"mod:weird_stairs": "mod:material#stairs"`: map a native to its material and shape (shape optional for stairs, slabs, walls, fences and gates) | file only |
| `variants.ignoredVariants` | `[]` | Native block ids that must not be treated as shapes | file only |
| `ops.enabled` | `true` | Building modes on this server | Server: building modes › Building modes |
| `ops.requireToolbox` | `true` | Modes need their tool in a carried toolbox | › Modes need tools |
| `ops.creativeBypass` | `true` | Creative players get every tool at Netherite | › Creative gets everything |
| `ops.placeWhatYouCan` | `true` | Build what you can afford instead of refusing | › Place what you can afford |
| `ops.respectClaims` | `true` | Ask protection and claim mods for every position | › Respect claims |
| `ops.allowBlockEntities` | `false` | Survival operations may change containers and other block entities | › Touch containers |
| `ops.effects` | `true` | Break particles and place sounds while building (a few per tick) | › Operation effects |
| `ops.maxVolume` | `256, 2048, 16384, 65536` | Most blocks per operation, per tier | › Most blocks per operation |
| `ops.maxSpan` | `16, 32, 64, 128` | Longest selection edge, per tier | › Longest selection edge |
| `ops.reachBonus` | `0, 8, 16, 32` | Extra corner reach, per tier | › Extra corner reach |
| `ops.blocksPerTick` | `8, 24, 64, 160` | Operation speed, per tier | › Operation speed |
| `ops.extendMax` | `16, 64, 256, 1024` | Most blocks one Extend adds, per Trowel tier | › Extend limit |
| `ops.symmetryRadius` | `16, 32, 64, 128` | Mirror/Radial reach, per Square tier | › Symmetry reach |
| `ops.globalBlocksPerTick` | `2048` | Blocks all running operations may change per tick | › Server budget per tick |
| `ops.minTicksBetweenOps` | `4` | Ticks between two operations of one player | › Pause between operations |
| `ops.undoDepth` | `10` | Undo steps per player | › Undo steps |
| `ops.undoPerMemory` | `10` | Extra undo steps per Memory upgrade | › Undo steps per Memory upgrade |
| `ops.durabilityPerBlocks` | `4` | One durability per this many changed blocks | › Blocks per durability |
| `ops.pasteOpLevel` | `0` | Operator level needed to paste (0: everyone) | › Paste permission level |
| `ops.creativeMaxVolume` | `262144` | Blocks per operation in creative (and the Capacity ceiling) | › Creative volume limit |
| `ops.maxUndoBlocks` | `200000` | Positions kept in one player's history | › Undo memory (blocks) |
| `ops.maxUndoDataKiB` | `65536` | Container data kept in one player's history (creative operations) | › Undo memory (contents), shown in MiB |
| `ops.disabledModes` | `[]` | Mode ids turned off, e.g. `["paste", "move"]` | file only |
| `toolbox.durability` | `250, 750, 2000, 5000` | Tool durability per tier (applied to a tool the next time it wears) | Server: toolbox & chisel › Tool durability |
| `toolbox.allowPouch` | `true` | Operations use the pouch (off: it takes no new items) | › Toolbox pouch |
| `toolbox.supplyLinkRange` | `64` | Most blocks between you and a linked container | › Supply Link range |
| `chisel.enabled` | `true` | Chisel groups at all | › Chisel variants |
| `chisel.inWorld` | `true` | Chiselling placed blocks | › Chisel in the world |
| `chisel.stonecutterGroups` | `true` | Groups from 1:1 stonecutter recipes | › Stonecutter variants |
| `chisel.blockFamilies` | `true` | Groups from vanilla block families | › Block family variants |
| `chisel.modCompat` | `true` | Groups from Rechiseled, Chipped and Chisel | › Chisel mods |

Tags a datapack can use: `#slate_building:material`, `#slate_building:not_material` (blocks), and
`#slate_building:toolboxes`, `#slate_building:tools`, `#slate_building:upgrades` (items). Slate Building's own
blocks are in `#minecraft:stairs`, `slabs`, `walls`, `fences` and `fence_gates`.

## Commands

| Command | Who | What it does |
|---|---|---|
| `/slatebuild undo` / `redo` | any player | Undo / redo your last operation |
| `/slatebuild cancel` | any player | Stop your running operation (what was built stays) |
| `/slatebuild limits` | any player | Your limits and tools |
| `/slatebuild reload` | operators (level 2) | Re-read `building-server.json` and the chisel file, rebuild shapes and groups, and send them to everyone. Recipe changes still need `/reload` |

## Compatibility

- **BetterInventory** (NeoForge): its inventory gets a **toolbox slot** (bottom left, beside the hotbar). It only
  accepts `#slate_building:toolboxes`, takes the toolbox on shift-click, and is the first place Slate Building looks
  for your toolbox. Like armour, the toolbox drops on death and goes back into its slot when picked up. BetterInventory's offhand carousel moves from Left Alt
  to **Right Alt**: that is the new default with Slate Building installed, and an existing Left Alt binding is moved
  once (a later rebind to Left Alt is kept).
- **Create:** the Step and Panel shapes match Create's copycat step and panel (geometry and placement). Copycats
  themselves stay Create's blocks and are not unified. Create's "Access Nearby Toolboxes" radial reads the raw Alt
  key, so it is blocked only while our swap key holds Alt (block in hand). With an empty hand it opens as usual.
  Create's stairs and slabs join their material's family like any other.
- **DiagonalFences / DiagonalWalls / DiagonalWindows:** their re-pointed items (the `oak_fence` item placing
  `diagonalfences:…/oak_fence`) are recognised as the same fence or wall and drop the material. Slate Building's
  own walls, fences and panes carry an extra hidden property so these mods do not make twins of them. Fences join
  in both directions.
- **KleeSlabs:** breaking a double slab (native or ours, horizontal or vertical) removes the half you look at and
  drops one unit of the material. A half mined without the right tool drops nothing, as with a full break. Our
  permission checks probe a single slab, so a check never splits one.
- **JEI:** each material of a shape is its own entry. With *Remove vanilla shapes*, native shape items disappear
  from JEI (and come back when the rule is turned off).
- **Open Parties and Claims and other claim mods:** every block an operation, a reshape, a chisel or a symmetry copy
  changes goes through the same break/place events as your own mining and placing (NeoForge). On Fabric, which has
  no place event, placements ask spawn protection, adventure rules, the Common Protection API (Flan, GOML, …) and
  the break event for that position.
- **Sodium:** the shapes render with Sodium (tint, cutout, translucency, light). **Iris:** with a shader pack active,
  ghosts use the pack's translucent path (opacity only, no desaturation) and are not drawn into the shadow pass.
- **BridgingMod:** the placement ghost steps aside while its bridge assist shows its own target.
- **Chisel mods:** Rechiseled, Chipped, Chisel (Modern) and Chisel (Reborn) groups are read when installed (see
  [Chisel groups](#chisel-groups)).

## Known limitations

- Undo history and the clipboard live in server memory: both are lost when you log out or the server restarts.
- Only building-mode operations can be undone. Swaps, in-world reshapes and chisels, symmetry copies and normal
  placing cannot.
- In survival, blocks that do not drop themselves (natural stone, glass, ice, ores, grass) cannot be reshaped or
  chiselled in place. Mine them first.
- Survival operations leave containers, unbreakable blocks and blocks your Hammer tier cannot harvest alone. Copies
  skip item-less blocks and do not carry container contents.
- Only stairs, slabs, walls, fences and gates are discovered as natives. Glass panes, bars, trapdoors and carpets
  stay separate (glass gets Slate Building's own pane). Map other natives with `variantOverrides`.
- Dropping a native slab item onto one of Slate Building's half slabs does not merge them. Slate Building only
  creates its own half slabs for materials that have no native slab, so this rarely comes up.
- Left Alt goes to Slate Building only while you hold a block or variant, or hold the toolbox / a building tool while
  looking at a block you may reshape. In every other case Shoulder Surfing's free look, Relics and Create's toolbelt
  keep it. Turn off *Swap key takes Alt*, or rebind one of the keys, if you want Alt for them even then.
- With *Build menu key: Smart*, R does nothing with an empty hand and no active mode (it stays with Iris and other
  mods). Use *Always* or the Slate hub.
- With a shader pack active, ghosts use a stronger fallback style, but they still look paler against bright packs.
- The Rechiseled, Chipped and Chisel readers were tested against those mods' data formats, not the running mods.
  The DF pack has none of them.

## Development

Unattended in-game checks: `./gradlew :dev:runClient -PbuildingHarness=<scenarios> -PautoWorld=true -PautoQuit=true`
(scenarios `smoke`, `variants`, `render`, `wheel`, `menu`, `input`, `selection`, `mirror`, `paste`, `move`,
`dimension`, `ops`, `toolbox`, `chisel`). Screenshots go to `dev/run/screenshots/building-*.png`. On NeoForge,
`:dev:runClientCompat` runs the same harness with the DF-pack mods copied into `neoforge/dev/run-compat` by
`tools/compat/populate-run-compat.sh` (scenarios `compat` and `compat-*`). Design contract:
[building-design.md](building-design.md).
