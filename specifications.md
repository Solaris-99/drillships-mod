# Drillships — Specification

Drillships is a NeoForge mod for Minecraft 1.21.1, inspired by the drillships of *Volcanoids*: tunnel-boring vehicles that the player drives through the world and lives in as a mobile base.

This document is the source of truth for the mod's design. The README is a player-facing summary and will be updated from this spec later. Values marked *(config)* are server config options; values marked *(in-game)* are set by players in a GUI.

---

## 1. Overview

- A drillship is made of a **drillhead** (required) followed by up to N **wagons** that trail behind it like train cars.
- The drillhead drives and drills: it mines every block in front of it and stores what it mines.
- The drillhead and wagons have real interiors where players build freely, including machines from other mods, which keep working while the ship moves.
- Drillships come in three tiers: **basic**, **standard** and **hardened**. Tiers are independent: a higher tier is crafted new, not upgraded from a lower one.
- Ships have no health and are immune to all damage, including explosions, fire and lava.

### Tiers

| | Basic | Standard | Hardened |
|--|--|--|--|
| Wagon interior (w × h × l) | 3 × 3 × 7 | 5 × 5 × 10 | 9 × 9 × 15 |
| Drillhead interior (w × h × l) | 3 × 3 × 5 | 5 × 5 × 7 | 9 × 9 × 9 |
| Exterior cross-section (with hull) | 5 × 5 | 7 × 7 | 11 × 11 |
| Max wagons | 3 | 5 | 8 |
| Ship inventory (slots, before inventory upgrades) | 100 | 150 | 200 |
| Top speed (blocks/s) | 8 | 10 | 12 |
| Energy buffer (FE) | 1,000,000 | 2,000,000 | 5,000,000 |
| Generator output (FE/t) | 240 | 480 | 1024 |
| Drill mining level | Stone | Iron | Netherite |
| Drill speed | 6 | 8 | 10 |
| Drill durability | 1,000,000 | 2,000,000 | 3,000,000 |
| Filter entries | 10 | 15 | 20 |
| Barrels (count × capacity) | 8 × 4096 | 10 × 8192 | 12 × 16384 |
| Radar radius (blocks) | 64 | 128 | 256 |
| Primary / secondary material | Copper / Iron | Bronze / Brass | Steel / Diamond |

The hull is one block thick, so the exterior is the interior plus 2 in each direction. The drill is the full exterior cross-section, 1 block deep, in front of the drillhead.

---

## 2. Architecture

- **Pocket-dimension interiors.** The exterior is an entity that moves, rotates freely and drills in the real world. Each ship's interior is a room of real blocks in the dimension `drillships:interior`, in its own region spaced 1024 blocks apart along X. Because interiors are ordinary chunks, every mod's blocks keep working while the ship moves.
- **Interior walls** are indestructible hull blocks, so players can't dig out of the pocket dimension.
- **Ship state lives in world save data**, not in the entity: pose, inventory, energy, fuel, drill and settings. The exterior entity is never saved; it is respawned from that data whenever its chunk loads, so a ship can't be lost, duplicated or saved into a player's file.
- **Movement is server-authoritative.** The driver's client only sends key input.

---

## 3. Driving

### Getting in and out
- **Doors:** every section (drillhead and wagons) has one door, in its right wall, third block from the back, 2 blocks high, in the same place inside and out. The exterior door slides open and closed (with iron door sounds) whenever someone goes through.
- **Enter:** right-click any section's exterior. The player comes in through that section's door and stands just inside it.
- **Leave:** use a door (or a hatch module). The player steps out beside that same door, on the ground or on the track. If blocks are in the way, the door cuts a way out the size of a player: the blocks are broken as the player (claims, spawn protection and unbreakable blocks are respected, and blocks with inventories are never broken) and their drops go to the ship's inventory. If that isn't allowed, the player goes out the other side, behind the last section, or on top, whichever has room; failing all of them a "the way out is blocked" message is shown.
- **Collision:** sections are solid. Each one is covered by a grid of 4 × 6 collision parts (NeoForge part entities), each the bounds of its slice of the rotated hull, so players and mobs bump into and stand on the hull itself even when the ship is turned diagonally. Clicking any part clicks its section, and parts also stop blocks from being placed inside the ship.
- **Drive:** sit in the driver's chair in the cockpit (or use the helm). The player is moved to the exterior and seated at the front of the drill, looking through the periscope. This causes a short dimension-change loading screen.
- **Stop driving:** the "Leave the helm" key (Shift by default). The player gets up just behind the chair, facing the front.
- A player who logs out while driving logs back in at the helm.
- While driving, the player can't break or place blocks and takes no damage.

### Controls
| Action | Default | Notes |
|--|--|--|
| Accelerate / brake and reverse | W / S | Reverse up to 25% of top speed |
| Steer | Mouse | The ship turns and pitches toward where the driver looks (see Steering below) |
| Drill | F | Toggle (press to start, press again to stop) or hold (spins while held), chosen in the controls panel |
| Radar target | R | The next kind of ore for the radar to look for (with a radar upgrade) |
| Leave the helm | Left Shift | |

Every action can be bound to any key or mouse button, in the **controls panel** (the Controls button on the status panel) or in vanilla's key binds under "Drillships". The bindings are only live while driving, so they may share keys with anything done on foot: while driving, vanilla actions on the same keys are held back (a click bound to the drill doesn't also attack; leaving is whatever key "Leave the helm" is on, not vanilla's sneak). The panel shows two driving actions on the same key in red. The drill mode is a client setting (`drillships-client.toml`, `drillMode`).

### Steering
- The ship steers toward where the driver looks, stopped or moving (stopped, it turns in place): it turns at up to 2.7°/tick and pitches at up to 1.35°/tick until it faces the crosshair. There are no steering keys.
- **Pitch** follows the look, limited to ±30°. A look within 5° of the horizon asks for level: out in the open that is left to levelling out (see Movement rules), so the ship settles onto its tracks; with its drill in terrain, or flying with an antigravity engine, it pitches level itself.
- **Looking around** is limited to 60° either side of the ship's heading and 45° up or down, so the driver can't look behind the ship. The view doesn't turn with the ship: the ship turns to meet it.

### Camera
- **First person (periscope):** the eye sits on the drill's axis, 1 block behind the hull's front face. Blocks being drilled can reach at most √3/2 of a block into the hull, so they never cover the view, and the "inside a block" overlay is suppressed while driving. The view stays where the driver looks while the ship turns to follow it (see Steering). Nothing of the drillhead is drawn from in here, since the eye is inside its hull; the steering marker on the HUD shows where its nose points.
- **Third person:** the camera backs off to the hull's length plus 1.5 × its height, enough to see the whole drillhead.
- A ship section is not drawn while the camera is inside it (or within 0.2 blocks), e.g. the drillhead from the periscope or a wagon when a tunnel pulls the third-person camera in.

### Driver HUD
While driving, a HUD styled like an old military terminal (green monospace text on dark panels with scanlines) shows:
- **Attitude indicator** (top right): an artificial horizon with a pitch ladder, plus heading (degrees and compass point) and pitch.
- **Power:** a bar, percentage and FE stored / capacity.
- **Drill:** on/off and durability.
- **Speed.**
- **Steering marker:** a green circle where the ship's nose points, with a line from it to the crosshair (where the driver looks, and so where the ship is turning). Once the ship faces the look, the circle sits on the crosshair. A nose outside the view is shown at the screen's edge, in its direction.
- **Warnings**, blinking: inventory full, no/low power, no drill, drill broken/worn, path blocked.
- **Radar** (a compass line at the top), when the ship has radar upgrades (see section 6, Radar).

### Movement rules
- The ship turns freely (any angle) and climbs 1-block steps when the drill is off.
- **Support and falling:** what holds the drillhead up is whatever lies under its tracks (from the back to the drill face), or its drill biting into terrain. With neither, it falls, keeping its horizontal momentum; a level landing snaps to the block grid.
- **Levelling out:** out in the open (nothing in the drill zone) and with the driver looking level (or nobody driving), a tilted ship turns back to level, accelerating (0.25°/tick², up to 4°/tick), hinged on its front-most support (else its rear-most, else its middle). Coming out of a tunnel on the surface it drops its nose and settles on its tracks instead of staying pointed at the sky; after landing nose first, its back comes down. While still inside the tunnel, or resting on a slope, it can't turn and stays as it is. With the drill in terrain it keeps whatever angle it was drilling at, unless the driver steers it.
- **Coupled sections never cross:** before the drillhead turns, pitches, levels out or drives, the new pose is checked against the first wagon (and, when driving, every coupled pair at the poses the path would give them): if two neighbours would start overlapping, or the drillhead would bend more than 35° / 40° / 35° away from the first wagon, that motion doesn't happen and the "path blocked" warning lights. A joint already past its limit may always straighten out. Falling is never held back by the wagons.
- **Fluids:** the ship drives through water and lava like any entity. Fluids never get into the interior.
- **Energy:** moving costs 1 FE/t *(config)*, plus 10 FE/t per engine overdrive. At 0 energy the ship stops and a warning lights up.
- **Flight:** with a powered antigravity engine (section 8) there is no falling and no levelling out; the ship goes where it is driven.
- **Chunk loading:** the chunks around each ship and its interior stay loaded *(config, default on)*.
- **Multiplayer:** there's no permission system. Anyone can drive, one driver at a time, and other players can be inside doing other things.
- **Dimensions:** ships work in any dimension they're placed in, including the Nether. Portals are ignored for now.

---

## 4. Mining

- The drill mines every block whose center falls within a 1-block-deep zone ahead of the drill face, plus any block blocking the ship's next move (for example when turning or pitching). All of them are mined in parallel.
- The whole face must be cleared before the ship advances, so it slows down while waiting for the drill. The target is a cruise of about 50–75% of top speed through stone with a matching drill.
- **Mining speed** uses the vanilla tool formula. Progress per tick = `drillSpeed × miningSpeedMultiplier / hardness / (correct tier ? 30 : 100)`.
  - Efficiency adds `level² + 1` to drill speed.
  - `miningSpeedMultiplier` defaults to 2.0 *(config)*. With it, the basic tier crosses stone at about 67% and deepslate at about 33% of top speed.
- **Blocks above the drill's tier** are mined slowly (the `/100` above) and don't drop *(config: `dropWithoutCorrectTier`)*.
- **Unbreakable blocks** (hardness < 0, e.g. bedrock) stop the ship and light the "path blocked" warning.
- **Blocks with inventories** are mined and spill their contents *(config: `mineBlockEntities`, default on; when off they stop the ship)*.
- **Claims and spawn protection** are respected: before a block breaks, a block-break event is fired as the driver. If it's refused, the ship stops in front of that block.
- **Falling blocks** (gravel, sand) are mined even if they don't fall into the drill.
- The ship leaves a tunnel behind it.

### Mined items
- Mined items go into the ship's inventory.
- When the inventory is full, the "inventory full" warning lights up and items drop on the ground, or are destroyed *(config: `voidWhenFull`)*.
- **Filter:** a whitelist/blacklist toggle *(in-game)* with 10 / 15 / 20 entries per tier.
- **Barrels:** 8 / 10 / 12 barrel-like storages per tier, holding 4096 / 8192 / 16384 items each. The player chooses which item goes into each barrel; several barrels may hold the same item. Barrels are always present (they may become a module later) and are shown in the ship's inventory screen.

---

## 5. Drill

- **Tiers:** basic (stone mining level, speed 6), standard (iron, 8), hardened (netherite, 10).
- **Fit:** a drill of any tier fits a ship of any tier, and is always as big as the ship it's installed in. It's installed and swapped freely through the status panel.
- **Durability:**
  - The base is 1,000,000, increasing by the base each tier: 1M / 2M / 3M.
  - It loses 1 per block mined. Unbreaking applies.
- **Broken drill:** at 0 durability the drill isn't destroyed. It mines at 10% speed and blocks drop nothing.
- **Wear:** at 25% durability or less, the drill visibly deteriorates and the "drill worn" warning lights up.
- **Repair:** in an anvil. Each block of the drill's primary material restores 10% durability. Repairs should be cheap.
- **Enchanting:** with mining enchantments (Efficiency, Fortune, Silk Touch, Unbreaking, Mending) through an enchanting table or books.
- **Contact damage:** a spinning drill deals 20 damage to entities in front of it, including players and item entities, but never to the ship itself or anything inside it. Steering may be limited so the drill can't hit the ship's own wagons.

---

## 6. Drillhead

The drillhead is the only required section. Its built-in fixtures are fixed and can't be removed. The drill is the only exception: it can be swapped.

| Fixture | Function |
|--|--|
| **Cockpit** | The driver's chair (sit down to drive), the desk and consoles around it (they open the status panel) and screens on the front wall, set apart from the rest of the cabin (see below). |
| **Helm** | The periscope screen on the front wall, above the desk. Using it also starts driving. |
| **Periscope** | Switches the player's camera to the front and center of the drill. Night vision may come as an upgrade later. |
| **Status panel** | Readouts, warnings, drill slot, fuel slot, toggles. Lights up while any warning is active. |
| **Upgrade panels** | In the left wall, beside the cockpit: 1 / 2 / 3 per tier, four upgrades each (see section 8). |
| **Generator** | Burns fuel to produce FE. Lights up while burning. On the right wall, beside the cockpit, under the exhaust. |
| **Storage access** | Opens the ship's inventory. On the left wall at the back, next to the crafting table, facing the door. |
| **Crafting table** | A normal crafting table. |
| **Door** | Exit. |
| **Headlights** | Light up the area in front of the drillhead while the engine runs (see below). |

### Cockpit
The chair sits two blocks back from the front wall, with a desk across the cockpit in front of it (slanted control surfaces, keys and small displays), a console on either side of it (gauges and levers) and screens filling the front wall beside the helm and status panel (a radar repeater next to the helm, telemetry further out). How it is set apart depends on the cabin:

| Tier | Cabin (inside) | Cockpit |
|--|--|--|
| Basic | 3 × 3 × 5 | The front two rows: desk, chair and its two consoles fill the width, flanked by the generator and the upgrade panel on the walls. No room for a partition. |
| Standard | 5 × 5 × 7 | The front two rows, behind a glazed bulkhead across the cabin: a kick plate, two rows of glass, a header with hazard stripes over a three-wide opening, and the wall pipes carried across above it. A lamp over the chair. |
| Hardened | 9 × 9 × 9 | A glazed pod, three wide and three deep inside (desk, chair between its consoles, and a walkway behind), three high under its own roof and lamp, with posts at the back corners and a two-high door under a striped header. It leaves a bay on either side: the generator's on the right, the upgrade panels' on the left. |

The cockpit blocks are built in, like the hull; they don't stop the ship from being picked up.

### Status panel
- **Shows:**
  - energy
  - FE/t in use: everything drawn from the buffer (movement, drill, upgrades, modules, FE pulled out through an Energy IO), averaged over the last second
  - warnings
  - fuel, with remaining burn time (an estimate, capped at 1 hour)
  - drill durability
  - wagon count and capacity
  - speed
  - coordinates
- **Controls:**
  - drill on/off
  - automatic fuel on/off
  - wagons: a wagon slot (a wagon placed in it is attached right away) and a button to detach the last wagon
  - tesla turrets on/off, and what they target (one setting for all of them)
  - picking the ship up
- **Warnings:** no energy, low energy (< 10%), inventory full, no drill, drill broken, drill worn, path blocked.
- **Layout** (324 × 222, so it fits at the default GUI scale on a 1080p screen): readouts in the left column; in the right column the drill, fuel and wagon slots side by side, with the six buttons two to a row under them (drill, automatic fuel, detach wagon, pick up, turrets on/off, turret targets); warnings across the panel, two to a row; the player's inventory centred below; the Controls button in the title bar.

### Generator
- **Location:** only the drillhead has one.
- **Fuel:** anything that works in a vanilla furnace.
- **Rate:** it burns one burn-tick per game tick while the buffer has room, producing 240 / 480 / 1024 FE/t. For example, one coal (1,600 burn-ticks) gives 384,000 FE on the basic tier.
- **Automatic fuel:** it pulls fuel from the ship's inventory automatically *(in-game toggle)*.
- **No upgrades.** To bring in other power, players use an Energy IO module.

### Headlights
- On while the engine runs, i.e. while someone is driving and the ship has energy. No energy cost.
- Kept simple on purpose: not a directional beam, just one light source (level 15) inside the front of the hull, lighting a radius around the front of the drillhead.
- It is an invisible vanilla light block that moves with the ship (waterlogged under water) and is removed when the engine stops or the ship is picked up.

### Radar
The radar comes from radar upgrades in the upgrade panels and shows on the driver's HUD as a compass line. There is one radar, and it is cumulative: each radar upgrade installed raises its tier, up to 3 (more change nothing).

| Tier | Upgrades | Finds | Within | Shows |
|--|--|--|--|--|
| I | 1 | Metals: iron, copper, gold, ancient debris, other metals (any other ore in `c:ores`, e.g. other mods' tin or lead) | 24 blocks | Where the deposits lie, and whether they are above or below the ship |
| II | 2 | Every ore: also coal, redstone, lapis, diamond, emerald, quartz | 32 blocks | Also the distance to the nearest one |
| III | 3 | Every ore | 48 blocks | Also its exact height in blocks, creatures, vessels, hostiles and players on the line (within 48 blocks, 16 up or down), and on water the depth under the ship and the distance to the shore ahead |

Distances are measured from the drillhead's middle; ores are found up to half the range above or below it.
- **Target:** the radar looks for one kind of ore at a time. The driver cycles it with the "Radar target" key (R by default), or with ◀ ▶ on any upgrade panel. Only kinds the tier finds come up; if the tier drops, a kind it can no longer find falls back to iron.
- **The line:** at the top of the screen, between the readouts and the attitude indicator (under the attitude indicator when the window is too narrow), like Fallout's compass. It scrolls with the heading across half a turn: N, E, S, W bright, the points between dim, ticks every 15°, and a caret over the heading. The four nearest deposits of the target show as diamonds in its colour where they lie, the nearest bigger and brighter, with an arrow above it if the ore is more than 2 blocks higher than the ship and below it if lower. When the nearest is outside the line, a chevron at the edge shows which way to turn. At tier III, contacts show as short coloured ticks (creatures cyan, vessels white, hostiles red, players yellow).
- **Readouts** under the line: the target and, from tier II, the distance to the nearest deposit, then its height (▲, ▼ or =; at tier III ▲12, ▼3...); the radar's tier and the target key. At tier III a second line: depth and shore ("DPT 12M SHR 30M", "OPEN" with no shore in 64 blocks, or "LAND"), and how many hostiles are around, in red when there are any.
- **Scanning:** server side, from loaded chunks only, never the ship itself or its riders. Contacts and water are measured twice a second; the ore scan reads a few chunk sections a tick (about 50,000 blocks, skipping sections without the target), and the HUD shows the last complete sweep, shifted as the ship moves. Changing the target starts a new sweep (under a second).

---

## 7. Wagons

- Wagons follow the drillhead's path like train cars: the path is traced by the drillhead's centre at half its height, and both ends of a wagon's centre line ride on it, with a gap of 1 / 2 / 3 blocks between sections (per tier), bridged by bellows. Joining at half height (rather than at the bottom) lets sections bend up or down about 38° before their ends touch, instead of about 15°. When the drillhead reverses, the wagons are pushed back along the same path.
- Wagons ride through the tunnel the drillhead dug and don't collide with blocks themselves. Turning the drillhead in place doesn't move them, and it stops turning at the joint limit (see Driving).
- A wagon must be the same tier as its ship. The limit is 3 / 5 / 8 wagons per tier.
- **Interior:** an empty room with its own door (no other fixtures, no lamp). Rooms are spaced by the same gap as the sections outside (1 / 2 / 3 blocks), and each wagon is joined to the section in front of it by a passage of 1 × 2 / 3 × 3 / 5 × 4 blocks (w × h, from the floor up) that cuts through both walls and crosses the gap. Across the gap the passage is lined with bellows blocks on its sides and ceiling over a hull floor: the inside of the accordion that joins the sections outside. Detaching requires the room and the passage to be empty.
- **Adding:** craft a wagon item and put it in the status panel's wagon slot. It needs free space behind the last section; otherwise a "something is blocking the wagon" message is shown and the item stays in the slot.
- **Removing:** only the last wagon can be detached, so the chain never has gaps. It must be empty (no blocks or entities inside). The wagon item goes back to the player and the passage is sealed.
- **Inventory:** wagons don't add inventory slots. Players extend storage with inventory upgrades (section 8), or themselves with inventory access modules and pipes.

### Picking up the whole ship
The ship can be picked up from the status panel when all of these are true:
- every wagon has been removed
- the inventory and fuel slot are empty
- the interior is empty

The resulting item keeps the installed drill. Picking up is meant for storage. To use the ship again, it is placed down again.

---

## 8. Upgrades

Upgrades aren't blocks: they plug into upgrade panels in the drillhead's left wall (the side without the door), at eye level beside the cockpit, from the front back. There is one more panel per tier: 1 / 2 / 3. Each panel has four bays, one upgrade per bay. Upgrades give the ship bonuses or change how it behaves; each kind counts up to its own limit, and more change nothing.
- **Using a panel** opens its four bays, laid out as on the wall, above the player's inventory, plus what all the ship's upgrades add up to: the radar's tier and ore range with its target (◀ ▶ to change it), and the radio with its station (◀ ▶ to tune). Any panel sets the same radar and radio.
- **On the wall** each upgrade shows as a faceplate standing a pixel proud of its bay: a green scope for the radar, a speaker grille and dial for the radio (the dial lit while it plays), amber chevrons for the engine overdrive, a violet glow for the antigravity engine and a pair of drawers for the inventory upgrade.
- **Outside** the radar shows as a pod under the nose and a search coil under the tail, the radio as an antenna. The other upgrades don't show outside.
- **Picking up the ship** gives the upgrades back along with the ship.
- Upgrades used to be called modules; worlds saved before the rename keep their radar and radio (`radar_module`, `radio_module` and `module_panel` are aliased to the new ids).

| Upgrade | Use | Limit | Outside | Recipe |
|--|--|--|--|--|
| Radar | The driver's radar (section 6). Cumulative: each one raises its tier. | 3 | A pod under the nose and a search coil under the tail | Copper, compass, glass panes, redstone, prismarine shard |
| Radio | Tuned from an upgrade panel: off, FM Tumbera 98.7 (the mod's own track), then five vanilla music discs (cat, blocks, chirp, mall, pigstep). It plays at the panel holding the ship's first radio, and from the drillhead outside, loud enough to hear about 24 blocks away. Taking the last one out switches it off. Going in or out of the ship (the game cuts every sound when it changes dimension) or a full sound engine never leaves it silent: the station starts again within half a second. | 1 | A whip antenna folded back along the roof (the tunnel leaves no room for a mast) | Copper, amethyst shard, note block, redstone |
| Engine Overdrive | Each one adds +50% of the tier's top speed and +10% acceleration (also braking), and 10 FE/t to the cost of moving: one / two / three give 150 / 200 / 250% speed, 110 / 120 / 130% acceleration and 11 / 21 / 31 FE/t with the default `moveEnergy`. Reverse stays at 25% of the raised top speed. | 3 | — | Copper, blaze rod, redstone blocks, furnace, piston |
| Antigravity Engine | Turns gravity off: the ship flies where its nose points (W / S along the heading, pitch still limited to ±30°), hovers in place when stopped, never levels out on its own and only comes down when driven onto the ground. It draws 200 FE/t whenever it is installed, even parked with nobody aboard; when the buffer can't pay a tick's draw, gravity comes back and the ship falls. Wagons follow the path as usual. | 1 | — | Shulker shells, eye of ender, phantom membranes, redstone block, copper, netherite scrap |
| Inventory Upgrade | Adds 50 slots to the ship's inventory, after the tier's. It can only be taken out while the last 50 slots are empty; otherwise the bay refuses and a message says to empty them first. | none (bays) | — | Copper, chests, barrel |

*Planned with the destruction mechanic (ships will get health and armor):* **Self-Healing Hull**: every 60 seconds it recovers 1% of the ship's health, for 5 FE/t.

---

## 9. Modules

Modules are fixtures installed into the hull from the inside, in place of shell blocks:
- **Installing:** right-click a free block of any room's hull, drillhead or wagon, while holding the module. Wall modules go into side or end wall blocks (plating, kick plate or pipes), not the corner beams, built-in fixtures, upgrade panels or passages. The door module takes two wall blocks, one above the other: the clicked one and the one above, or below where there's no room above. The hatch goes into a floor or ceiling block away from the walls. There's no limit on the number of modules. Modules are indestructible, like the hull.
- **Removing:** Shift + right-click with a wrench. The module comes back to the player along with anything it held (a furnace's items), and the hull block that was there comes back. The wrench is crafted from copper in a Y pattern. Other mods' wrenches also work (`c:tools/wrench` tag).
- **Picking up the ship** or detaching a wagon is refused while that room has modules, with a message to take them out with a wrench first.

| Module | Behaviour |
|--|--|
| Crafting table | A crafting table. |
| Furnace | An input and an output slot, no fuel: vanilla smelting speed on 20 FE/t from the buffer, drawn only while smelting; without enough energy it pauses ("No power"). No smelting XP. Options *(in-game)*, both off by default: push the output into the ship inventory, and pull input from it, only items on its 9-entry whitelist of ghost slots (an empty whitelist pulls nothing). It moves items once a second. Lit while smelting. |
| Alloy furnace | Like the furnace module, with two inputs and the alloy recipes (section 12). |
| Inventory access | Shows the ship's inventory, like the built-in storage access. |
| Inventory IO | Its side exposes the ship inventory to pipes and hoppers. It never moves items on its own. Mode: input (they can only insert), output (only extract) or both (the default). A rate limit in items per second, each way, set with -64 / -8 / -1 / +1 / +8 / +64 and "No limit" (0, the default), and capped by `inventoryIoMaxRate` *(config, items/s, default 0 = no cap)*. A filter of 9 ghost slots, as a blacklist (the default, so an empty filter passes everything) or a whitelist, applied both ways; items it holds back are hidden from pipes. A plain right-click with a wrench also steps the mode. |
| Crafting & inventory access | A crafting grid beside a page of the ship's inventory, in one wide screen (crafting on the left, the ship's page with its page buttons on the right, the player's inventory below). Shift-click moves between the player's inventory and the ship's; what's crafted goes to the player, and what doesn't fit there to the ship. |
| Energy IO | Its side exposes the energy buffer. It never pushes on its own. Mode: input (the default; other blocks can push FE in), output (they can pull it out) or both. A rate limit in FE/t, each way, set with -1000 / -100 / -10 / +10 / +100 / +1000 and "No limit" (0, the default), and capped by `energyIoMaxRate` *(config, default 0 = no cap)*. A plain right-click with a wrench also steps the mode. |
| Door | An extra exit, two blocks tall. Can't be locked. |
| Hatch | An exit through the roof or the floor. Can't be locked. |
| Tesla turret (small / medium) | See below. |

**Door and hatch exits.** Using one leaves the ship at that spot: out of the hull beside a door module (facing away from the wall), onto the roof above a ceiling hatch, or under the ship below a floor hatch. Blocks in the way are cut as at the section's door (section 3). Where that isn't allowed, or a door module in an end wall opens onto the next section, the player leaves through the section's own door. Entering is still through the section's door.

**Ship locator.** Not installed: a compass, crafted from a compass and a module base, whose needle points to the drillhead of the last ship its holder entered (by a door or the helm). Like a compass in the Nether, it spins in other dimensions, inside the ship among them. Which ship that was is kept on the player, through death, and the needle updates once a second.

The modules that keep settings or items (furnace, Inventory IO, Energy IO) are blocks with their own data in the interior's chunks, which stay loaded with the ship.

### Tesla turrets
- **Placement:** a module for the ceiling: right-click any free ceiling block of a room (away from the walls) with it. The block becomes the turret's mount inside, and outside the turret always sits in the middle of that section's roof. Each section can have at most one turret ("This section already has a tesla turret"). It comes out with a wrench like any module, and using the mount opens the status panel.
- **Outside:** a hatch flush with the roof, out of which a tesla coil (a column with a crown of rings and a ball on top) rises when the two blocks above the roof's middle are clear, and folds back where they aren't, as in a tunnel, so it stays inside the tunnel's cross-section. The medium turret is 1.3 × bigger. It fires either way, from where the coil's top would be.
- **Damage:** small 4, medium 6, every 0.25 s, to every target in its area at once. It ignores armor completely and invulnerability frames (damage type `drillships:tesla`, tagged `bypasses_armor` and `bypasses_cooldown`).
- **Area:** a box around its own section, enlarged by 3 blocks (small) or 7 blocks (medium). Line of sight isn't required. Never the ship's own riders, or players in creative.
- **Targets** *(in-game setting, one for all the ship's turrets)*: hostiles (the default), all mobs (but not pets, villagers or iron golems; never players), or everything (mobs and players).
- **Energy:** costs 100 FE per hit and stops firing when the buffer can't pay. No cost while idle.
- **Effect:** a jagged line of electric sparks from the coil to each target, and a short crack of thunder.
- **Status panel:** one "Turrets: ON/OFF" toggle and one target setting for every turret of the ship.

---

## 10. Energy

All machines on the ship share one buffer (1M / 2M / 5M FE).

| Consumer | Cost |
|--|--|
| Moving | 1 FE/t *(config)*, plus 10 FE/t per engine overdrive |
| Drilling | 1 FE/t per block of drill face while the drill spins, whether or not anything is being broken *(config)* |
| Furnace / alloy furnace modules | 20 FE/t while working |
| Energy IO module | Whatever other blocks push in or pull out, up to its limit |
| Antigravity engine | 200 FE/t while installed |
| Tesla turret | 100 FE per hit |

- Modules and upgrades keep drawing from the buffer while the ship is parked and the generator is off.
- An upgrade that increases the buffer may come later.

---

## 11. Damage

| Source | Damage |
|--|--|
| Spinning drill (contact) | 20 *(config)* |
| Squashed between the moving ship and a solid block | 100 *(config)*. Entities are pushed aside instead whenever possible. |
| Tesla turret | 4 (small) / 6 (medium) per 0.25 s, ignores armor |

Passengers inside a moving ship never take damage from it.

---

## 12. Materials, alloys and world generation

- Recipes use common tags (`c:ingots/steel`, `c:ingots/bronze`, …). Materials from other mods are preferred.
- **If no other mod provides tin, zinc, bronze, brass or steel**, the mod enables its own:
  - an ingot, a nugget and a block of each (9 to 1 both ways)
  - for tin and zinc, like iron: stone and deepslate ore, raw metal (dropped by the ore, Fortune applies; Silk Touch gives the ore), a block of raw metal, and smelting (200 ticks) and blasting (100 ticks) of raw metal or ore into ingots, 0.7 XP
  - tin and zinc ore generated like iron's middle and small veins: 10 veins of 9 blocks a chunk between Y −24 and 56 (most around 16), and 10 of 4 from the bottom of the world to Y 72, in every overworld biome
- **Detection** (`material/Material`), per material:
  - A popular mod that provides it is loaded: tin and bronze Mekanism or Thermal, zinc and brass Create, steel Mekanism or Immersive Engineering.
  - Or its `c:ingots/<material>` tag holds another mod's item, to catch less popular mods.
  - Items are always registered, because Minecraft can't register them conditionally. When another mod provides the material, our recipes for it are off (recipe condition `drillships:material`, checked as recipes load, when tags are known), our ore doesn't generate (placement filter `drillships:material`, checked as chunks generate: worldgen data loads before tags), and our items are hidden in JEI.
- Our items carry the usual common tags (`c:ingots/tin`, `c:nuggets/tin`, `c:storage_blocks/tin`, `c:raw_materials/tin`, `c:ores/tin`, `c:ores_in_ground/stone`…), so other mods' recipes accept them; the ores are in `c:ores`, so the radar finds them as other metals. Metal blocks are beacon bases.
- **The alloy furnace and its recipes:** each alloy recipe (bronze, brass, steel) is on only while that alloy is ours. The alloy furnace and the alloy furnace module recipes are on while at least one of them is.

### Large ore deposits
- **Shape:** roughly a sphere, 28 to 48 blocks in diameter.
- **Rarity:** very rare, comparable in feel to ancient debris. *(default: about 1 deposit per 500 chunks, config)*
- **Contents:** one ore type per deposit, chosen from the overworld ores in the `c:ores` tag, so other mods' ores can appear. *(default)*
- **Depth:** centered between Y −40 and Y 40. *(default)*
- **Generation:** added on top of normal ore generation, not replacing it. Can be turned off *(config)*.
- The radar shows deposits as bright points.

### Alloy furnace
A standalone block (and a module). It works like a furnace with two inputs and burns furnace fuel: one burn-tick per tick, lighting new fuel only when it has an alloy to make. It faces the player who placed it, is lit while burning, drops itself and its contents, and is mined with a pickaxe. Hoppers and pipes fill the inputs from above and the fuel from the sides, and take the output from below. Ingredients go in either input slot.

The **alloy furnace module** works like the furnace module (section 9): 20 FE/t from the buffer while working, two inputs, push and pull toggles with a whitelist. When pulling, an item goes into the input already holding it, else an empty one, so it never fills both.

Alloy recipes are their own data-driven type (`drillships:alloying`: one or two ingredients with counts, a result and a time), so packs can add more. JEI shows them in an Alloy Furnace category, with the alloy furnace and its module as catalysts.

| Alloy | Inputs | Output | Time |
|--|--|--|--|
| Bronze | 3 copper + 1 tin | 4 bronze | 200 ticks |
| Brass | 3 copper + 1 zinc | 4 brass | 200 ticks |
| Steel | 1 iron + 2 `#minecraft:coals` | 1 steel | 200 ticks |

---

## 13. Recipes

All drillship recipes have the same shape in every tier and differ only in material. Each tier has its own parts (e.g. basic drill piece, standard drill piece). **P** is the tier's primary ingot, **S** its secondary ingot and **B** a block of the primary material.

| Tier | P | S | B |
|--|--|--|--|
| Basic | copper ingot | iron ingot | copper block |
| Standard | bronze ingot | brass ingot | bronze block |
| Hardened | steel ingot | diamond | steel block |

The ship is meant to be expensive. For example, hardened hulls need 18 steel blocks per drillhead.

**Wheel**
| | | |
|--|--|--|
| | P | |
| P | | P |
| | P | |

**Tread**
| | | |
|--|--|--|
| P | P | P |
| Wheel | Wheel | Wheel |
| P | P | P |

**Hull**
| | | |
|--|--|--|
| B | B | B |
| S | S | S |
| B | B | B |

**Drill piece**
| | | |
|--|--|--|
| S | S | S |
| | P | |
| | P | |

**Drill shaft**
| | | |
|--|--|--|
| | P | |
| | P | |
| | P | |

**Drill**
| | | |
|--|--|--|
| | Drill piece | |
| Drill piece | Drill shaft | Drill piece |
| | Drill shaft | |

**Engine**
| | | |
|--|--|--|
| B | S | B |
| S | Furnace | S |
| B | S | B |

**Drillship (drillhead)**
| | | |
|--|--|--|
| Hull | Tread | |
| Hull | Engine | Drill |
| Hull | Tread | |

**Wagon**
| | | |
|--|--|--|
| Hull | Tread | Hull |
| Hull | | Hull |
| Hull | Tread | Hull |

### Other recipes

**Alloy furnace**
| | | |
|--|--|--|
| Brick | Brick | Brick |
| Furnace | Iron ingot | Furnace |
| Brick | Brick | Brick |

**Wrench**
| | | |
|--|--|--|
| Copper ingot | | Copper ingot |
| | Copper ingot | |
| | Copper ingot | |

**Module base**
| | | |
|--|--|--|
| Copper ingot | Copper ingot | Copper ingot |
| Copper ingot | | Copper ingot |
| Copper ingot | Copper ingot | Copper ingot |

The module base is the same for every tier.

**Energy IO module**
| | | |
|--|--|--|
| | Redstone | |
| Redstone | Module base | Redstone |
| | Redstone | |

**Tesla turret (small)**
| | | |
|--|--|--|
| | Redstone | |
| Copper ingot | Iron ingot | Copper ingot |
| Copper ingot | Iron ingot | Copper ingot |

**Tesla turret (medium)**
| | | |
|--|--|--|
| | Redstone block | |
| Copper block | Steel ingot | Copper block |
| Copper block | Steel ingot | Copper block |

**Shapeless:** each of these is a module base plus the listed items.

| Module | Ingredients |
|--|--|
| Furnace module | Furnace + module base |
| Alloy furnace module | Alloy furnace + module base |
| Inventory access module | Chest + module base |
| Inventory IO module | Hopper + chest + module base |
| Crafting module | Crafting table + module base |
| Crafting & inventory access module | Crafting table + chest + module base |
| Door module | Any door (`#minecraft:doors`) + module base |
| Hatch module | Any trapdoor (`#minecraft:trapdoors`) + module base |
| Ship locator | Compass + module base |

Chests are any wooden chest (`#c:chests/wooden`).

---

## 14. Compatibility

- The mod should work alongside most mods. The goal is a mobile base the player builds in freely.
- **Tested against:** JEI, Create, Mekanism, AE2, Refined Storage 2 and FTB Chunks.
- There's no in-game guide or advancements for now.

## 15. Configuration

Server config (`drillships-server.toml`):

| Option | Default |
|--|--|
| `chunkLoading` | true |
| `miningSpeedMultiplier` | 2.0 |
| `dropWithoutCorrectTier` | false |
| `mineBlockEntities` | true |
| `voidWhenFull` | false |
| `moveEnergy` (FE/t) | 1 |
| `drillEnergyPerBlock` (FE/t) | 1 |
| `drillDamage` | 20 |
| `squashDamage` | 100 |
| `energyIoMaxRate` (FE/t each way, 0 = no cap) | 0 |
| `inventoryIoMaxRate` (items/s each way, 0 = no cap) | 0 |

Options that come with later features: radar ore tags and radius per tier.

---

## 16. Assets

Textures and sounds are supplied by the author. Missing assets use vanilla placeholders.

### Textures in use
| Texture | Used by |
|--|--|
| `hull_00`..`hull_11` | Interior walls: the exterior's painted plating, one panel per 2 × 2 blocks, paired from the floor up |
| `hull_floor` | Floor: steel tread plate, like the roof outside |
| `hull_ceiling` | Ceiling: gunmetal panels with a vent |
| `hull_frame` | Beams in the vertical corners of every room |
| `hull_skirt` | Kick plate along the bottom row of the walls, with hazard stripes (the rubbing strip outside) |
| `hull_conduit` | Steel and amber pipes along the top row of the walls, in rooms 4 or more blocks tall inside |
| `bellows` | Passage lining between sections (the inside of the exterior bellows) |
| `hull_lamp` | Ceiling lamp: the headlights' lens |
| `exterior/*` | The exterior model (see below) |
| `periscope` | Helm |
| `status` | Status panel. It has no separate warning texture; the panel lights up instead (this may change later). |
| `engine_off` / `engine_on` | Generator, idle / burning |
| `inventory_io` | Storage access |
| `crafting_module` | Built-in crafting table |
| `door_bottom` / `door_top` | Door, lower / upper half: the same art as the exterior door |
| `cockpit_seat` | Driver's chair: dark quilted leather with red piping, and the joysticks' red knobs |
| `cockpit_desk_top` / `cockpit_console_top` | The slanted tops of the desk (displays and keys) and the side consoles (gauges and levers) |
| `cockpit_console_side` | Desk and console bodies: gunmetal with a red stripe, vents and LEDs |
| `cockpit_screen` / `cockpit_telemetry` | Front wall screens: radar repeater / telemetry trace and meters |
| `cockpit_glass` | Cockpit glazing: a gunmetal frame around a tinted, see-through pane (translucent) |
| `bulkhead` | The header over cockpit openings: a gunmetal beam with hazard stripes along its foot |
| `upgrade_panel` / `upgrade_faces` | Upgrade panel with four bays / the faceplates of every upgrade (the radio off and on). `upgrade_faces` is 32 × 32 so each 6-pixel faceplate keeps the panel's pixel size; the engine overdrive, antigravity and inventory faceplates are placeholders |

The chair, desk and consoles are block models with elements (the desk and console tops tilted 22.5°); the pod's roof and lamp reuse `exterior/roof`, `exterior/trim`, `hull_ceiling` and `hull_lamp`. Upgrade items have their own icons (`textures/item/radar_upgrade`, `radio_upgrade`, and placeholders `engine_overdrive_upgrade`, `antigravity_upgrade`, `inventory_upgrade` drawn on the radar's case).

Modules are full blocks facing into the room (set when installed): their texture is on that face and `hull_00` plating on the others, except the door module, which shows its texture on both sides of the wall, and the hatch and turret mounts, which show theirs on top and bottom. Textures: `crafting_module` (crafting), `crafting_inventory_access` (crafting & inventory access: the crafting grid beside a chest), `inventory_access` (a chest), `furnace_off` / `furnace_on`, `inventory_io` (Inventory IO), `energyio`, `hatch`, `small_tesla_turret` / `medium_tesla_turret` (the turret mounts: one or two amber bolts in a ring; placeholders), and `door_bottom` / `door_top` for the door module.

**Item icons** (drawn in the mod, in each tier's colours where it has one): the tier parts (`textures/item/<tier>_wheel`, `_tread`, `_hull`, `_drill_piece`, `_drill_shaft`, `_engine`, `_drill`, `_drillship`, `_wagon`: copper and iron, bronze and brass, steel and diamond), every module and turret (the module base's copper frame with the module's symbol), and the ship locator (a brass dial whose needle turns through `ship_locator_00`..`31`, picked by the compass angle like vanilla's). The wrench, module base, upgrades and materials keep their placeholder icons for now.

The alloy furnace and its module use `alloy_furnace_off` / `alloy_furnace_on` (the standalone block on its front, with vanilla's bricks on every other face).

**Materials** (placeholders drawn for now, one file each): `textures/item/<material>_ingot`, `<material>_nugget`, `raw_tin`, `raw_zinc`; `textures/block/<material>_block`, `raw_tin_block`, `raw_zinc_block`, and `tin_ore_overlay` / `zinc_ore_overlay`, the ore's spots, drawn over vanilla's stone or deepslate by the ore's model.

### Exterior model
The exterior is a procedural model built in code from each section's size (`client/model/SectionModel`), so all three tiers and future longer sections share one design. Faces are cut on a 1-block grid, so textures keep 16 pixels per block at any size; body plating uses 2 × 2-block panels.

- **Everything stays inside the tunnel's cross-section.** The body is inset 1/8 block on the sides and 3/8 on the roof, leaving room for the tracks, door, rails, lamps and stacks without anything leaving the tunnel the drill cuts.
- **Body:** a box with 45° bevels along the top edges, sitting on two tracks (1 block high) with a belly plate between them. Ends narrow by the tier's taper so coupled sections can bend; the taper never cuts into the interior room.
- **Coherent with the interior:**

| Exterior part | Interior counterpart |
|--|--|
| Sliding door on the right side, third block from the back, 2 blocks high | The interior door |
| Periscope housing on the roof's front | The helm |
| Exhaust stack on the right bevel, third block from the front (smokes while burning) | The generator |
| Coupling mouths and bellows | The passage to the next section |

- **Other parts:** drill housing (hazard-striped ring), headlights in the front bevel corners (glow while the engine runs), roof beacon (flashes while a warning is lit), roof rails, a ladder ahead of the door, side ribs every third block, a rubbing strip over the tracks, roof hatch and side vents on wagons, a roll-up shutter on closed couplings.
- **Animated:** the drill spins; road wheels turn and track belts run with the distance driven; the door slides back along the hull (opening is wired in a later step); the bellows follow both sections every frame, folding and bending.
- **Drill:** round cutter plate with carbide teeth on its rim and a cone (1.35 × its radius long) wrapped in three spiral flights, as wide as the body. It looks rusty when worn. The cone sticks out ahead of the drill zone, which is purely visual.

Per tier (from `ShipTier.Coupling`):

| | Basic | Standard | Hardened |
|--|--|--|--|
| Gap between sections (bellows length) | 1 | 2 | 3 |
| Passage (w × h) | 1 × 2 | 3 × 3 | 5 × 4 |
| End taper | 1.0 | 1.25 | 1.5 |
| Track width | 1.25 | 1.54 | 2.42 |

**Textures** (`textures/block/exterior/`): `plate_00`..`plate_11` (one 2 × 2 panel), `roof`, `trim`, `belly`, `track_belt`, `track_side`, `wheel`, `hazard`, `door_lower`, `door_upper`, `glass`, `lamp`, `lamp_on`, `beacon`, `beacon_on`, `pipe`, `soot`, `vent`, `hatch`, `bellows`, `shutter`, `drill_steel`, `drill_plate`, `drill_tooth`. One palette: painted red-orange plating, gunmetal trim, steel roof and drill, hazard yellow, amber doors matching the interior door.

**Still placeholders:** the wrench, module base, new upgrade and material icons, and the GUI screens, which are drawn without textures. The fixtures keep the author's art, recoloured from the old red to the exterior's paint.

### Sounds
All four are mono, so they fade with distance. Stereo originals are kept in `art-source/sounds/`.

| Sound | When |
|--|--|
| `engine_idle` | Loops at 35% volume while someone is driving, the ship has energy, and it isn't moving (heard up to 16 blocks) |
| `engine_working` | Loops at 50% while the engine is on and the ship moves, pitched up to 15% higher at top speed. It crossfades with idle. |
| `engine_stop` | Plays once at 50% when the engine turns off (the driver leaves or energy runs out) |
| `digging` | Loops at 55% only while the drill is actually cutting blocks, not while it spins in the air |

The driver, inside the hull in first person, hears the loops at 60%. A big drill breaks many blocks at once, so only 2 per tick play their vanilla break sound (and 10 more just throw dust); the rest break quietly, which keeps the sound engine from running out of channels.

Vanilla sounds used: iron door open/close for the sliding doors, a low anvil thud when landing from a real fall, chain place/break when a wagon is coupled or uncoupled.

**Music:** `music/cumbia_del_taladro` ("Cumbia del Taladro", the radio's FM Tumbera station) is an original instrumental synthesized from scratch by `art-source/music/CumbiaDelTaladro.java`: a cumbia section (keyboard lead with vibrato and echo, guiro, timbal, cowbell, bass) and an RKT section (dembow beat, 808 bass), mastered like a car stereo pushed too far. 48 seconds at 100 bpm, looping seamlessly; mono OGG Vorbis encoded with ffmpeg. Players can replace it, or add their own, with a resource pack.

### Particles
Spawned on the client, at the exterior model's positions:

| Effect | When |
|--|--|
| Smoke from the exhaust stack | While the generator burns: light puffs when idle, thick smoke while moving or cutting |
| Chips of the block being cut, thrown back and out | While the drill cuts; sparks off rock with hardness 3 or more |
| Bubbles at the drill | While the drill spins under water |
| Dust from the ground behind the tracks | While any section rolls over solid ground |
| A burst of ground dust | Landing from a real fall (server-side, seen by everyone) |

---

## 17. Roadmap

1. **MVP: done.**
   - Basic-tier drillhead.
   - Placing it, entering and leaving it.
   - Driving, drilling and gravity.
   - Inventory, generator, status panel with warnings, and crafting table.
   - Drill durability, repair and enchanting.
   - Chunk loading and pickup.
   - Basic-tier recipes.
2. **Wagons, headlights and driver HUD: done.**
2b. **Redesign (in progress on `feature/drill-model`):**
   - A. Procedural exterior model and textures for all tiers, bellows, animated tracks: **done.**
   - B. Interiors coherent with the exterior: **done.** Door-based entering/exiting with the door animating and cutting a way out, wagon doors, passages sized per tier and lined with bellows, interior gap matching the exterior, collision parts, interior textures in the exterior's palette (hull plating, door, lamp, recoloured fixtures); items and tag-based recipes for the standard and hardened tiers (bronze/brass and steel/diamond, craftable once another mod provides those tags; available in the creative tab meanwhile).
   - C. Physics: **done.** Falling, and levelling out on the surface instead of staying pointed at the sky; the path at half height; joint checks so sections never cross. Gametests (run in game with `/test runall`): `levelsoutonthesurface`, `keepsitsanglewhiledrilling`, `wagonlimitsturninginplace`.
   - D. Particles and sounds: **done.** Exhaust smoke, drill chips and sparks, bubbles, track dust, landing dust; quieter loops, digging only while cutting, engine pitch with speed, muffled for the driver, capped break sounds, landing thud and coupling sounds.
   - E. Comfort modules for the bigger tiers: **done.** Radio with stations and its original track (see section 8).
   - F. Upgrade panels, radar and cockpit: **done.** Upgrades (then called modules) plug into upgrade panels (1 / 2 / 3 per tier, four bays each) instead of being blocks; the sonar and metal detector became one radar, a compass line on the driver's HUD with a chosen target ore and height arrows, whose tier (up to 3) is how many radar modules are installed; a cockpit per tier with a modelled chair, desk, consoles, screens and glazing (see sections 6 and 8). Gametest: `radarmodulesaddup`.
3. **Upgrades, modules and wrench.**
   - A. Upgrades: **done** on `feat-upgrades`. The panel modules became upgrades (radar, radio) and three new ones were added: engine overdrive, antigravity engine and inventory upgrade (see section 8). Gametests: `radarupgradesaddup`, `engineoverdrivesstackuptothree`, `antigravityholdstheshipup`, `antigravitywithoutenergyfalls`, `inventoryupgradesaddslots`.
   - B. Modules and wrench: **done** on `feat-modules`. Crafting, furnace, inventory access, Inventory IO, crafting & inventory access, Energy IO, door and hatch modules, the module base, the wrench and the ship locator (see section 9). The alloy furnace module comes with step 5 and the turrets with step 4. Gametests: `modulesgoinandcomeout`, `inventoryiofiltersandlimits`, `energyiokeepsitslimit`, `furnacemodulesmeltsonenergy`, `doormoduleleadsoutside`.
4. **Tesla turrets: done** on `feat-tesla-turrets` (see section 9, Tesla turrets). The exterior coil reuses `exterior/hatch`, `trim`, `pipe` and `drill_steel`. Gametests: `teslaturretzapshostilesthrougharmor`, `teslaturretsettings`.
5. **Standard and hardened tiers, materials and alloys: done** on `feat-materials` (see section 12). The standard and hardened tiers now craft from our bronze, brass and steel when no other mod provides them. JEI is an optional dependency (its API at compile time, the full mod in the dev runs only). Gametests: `materialsareourswhennoothermodhasthem`, `alloyfurnacemakesbronze`, `alloyfurnacemodulemakessteel`.
6. **Ore deposits** (the radar is done, see 2b F).
7. **Filters and barrels.**
8. **Mouse steering through the periscope: done** on `feat-mouse-steering`. The ship follows the driver's look within 60° / 45°, shown on the HUD by the steering marker (see Steering and Driver HUD). Gametest: `steerstowardthelook`.
9. **Destruction:** ship health and armor, and the Self-Healing Hull upgrade.

## 18. Open questions

1. **Ore deposits:** the frequency, ore selection and depth range in section 12 are defaults. Confirm them when the feature is built.
