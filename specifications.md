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
| Drillhead interior (w × h × l) | 3 × 3 × 5 | 5 × 5 × 5 | 9 × 9 × 5 |
| Exterior cross-section (with hull) | 5 × 5 | 7 × 7 | 11 × 11 |
| Max wagons | 3 | 5 | 8 |
| Ship inventory (slots) | 100 | 150 | 200 |
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
- **Enter:** right-click the ship's exterior.
- **Leave:** use the door (or a hatch module). The player is placed beside, behind or on top of the ship, whichever has room. If none has room, a "the way out is blocked" message is shown.
- **Drive:** use the helm. The player is moved to the exterior and seated at the front of the drill, looking through the periscope. This causes a short dimension-change loading screen.
- **Stop driving:** Shift. The player returns to the helm.
- A player who logs out while driving logs back in at the helm.
- While driving, the player can't break or place blocks and takes no damage.

### Controls
| Key | Action |
|--|--|
| W / S | Accelerate / brake and reverse (reverse up to 25% of top speed) |
| A / D | Steer left / right |
| Space / Ctrl | Pitch up / down (limited to ±30°) |
| F | Toggle the drill (rebindable; only active while driving) |
| Shift | Stop driving |

*Later:* steering with the mouse through the periscope, with two modes. **Drive** mode, used while moving, limits the view to the front of the ship. **Free look** mode, only available while stopped, lets the player look around freely.

### Movement rules
- The ship turns freely (any angle) and climbs 1-block steps when the drill is off.
- **Gravity:** if nothing is under the ship, it falls, keeping its horizontal momentum, and snaps to the block grid on landing.
- **Fluids:** the ship drives through water and lava like any entity. Fluids never get into the interior.
- **Energy:** moving costs 1 FE/t *(config)*. At 0 energy the ship stops and a warning lights up.
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
| **Helm** | Steering controls and periscope. Using it starts driving. |
| **Periscope** | Switches the player's camera to the front and center of the drill. Night vision may come as a module later. |
| **Status panel** | Readouts, warnings, drill slot, fuel slot, toggles. Lights up while any warning is active. |
| **Radar** | 2D top-down map of nearby ores (see below). |
| **Generator** | Burns fuel to produce FE. Lights up while burning. |
| **Storage access** | Opens the ship's inventory. |
| **Crafting table** | A normal crafting table. |
| **Door** | Exit. |

### Status panel
- **Shows:**
  - energy
  - warnings
  - fuel, with remaining burn time (an estimate, capped at 1 hour)
  - drill durability
  - wagon count and capacity
  - speed
  - coordinates
- **Controls:**
  - drill on/off
  - automatic fuel on/off
  - wagons: list, add, pick up
  - tesla turrets on/off
  - picking the ship up
- **Warnings:** no energy, low energy (< 10%), inventory full, no drill, drill broken, drill worn, path blocked.

### Generator
- **Location:** only the drillhead has one.
- **Fuel:** anything that works in a vanilla furnace.
- **Rate:** it burns one burn-tick per game tick while the buffer has room, producing 240 / 480 / 1024 FE/t. For example, one coal (1,600 burn-ticks) gives 384,000 FE on the basic tier.
- **Automatic fuel:** it pulls fuel from the ship's inventory automatically *(in-game toggle)*.
- **No upgrades.** To bring in other power, players use an Energy IO module.

### Radar
- **What it detects:** all ores by default *(config: list of block tags)*.
- **Display:** top-down. A large group of ores shows as a bright point and a small deposit as a faint one. An up or down arrow on a point shows that the ore is above or below the ship.
- **Range:** 64 / 128 / 256 blocks radius *(config, per tier)*.
- **Scan rate:** once a minute, or slower if that's still too expensive. The scanning animation can run faster.

---

## 7. Wagons

- Wagons follow the drillhead's path like train cars.
- A wagon must be the same tier as its ship. The limit is 3 / 5 / 8 wagons per tier.
- **Adding:** craft a wagon item and insert it through the status panel. It needs free air behind the ship; otherwise a "something is blocking the wagon" message is shown.
- **Removing:** a wagon can be picked up from the status panel once it's empty.
- **Inventory:** wagons don't add inventory slots. Players extend storage themselves with inventory access modules and pipes.

### Picking up the whole ship
The ship can be picked up from the status panel when all of these are true:
- every wagon has been removed
- the inventory and fuel slot are empty
- the interior is empty

The resulting item keeps the installed drill. Picking up is meant for storage. To use the ship again, it is placed down again.

---

## 8. Modules

Modules are fixtures installed into the hull from the inside:
- **Installing:** right-click any interior wall block while holding the module. There's no limit on the number of modules.
- **Removing:** Shift + right-click with a wrench. The wrench is crafted from copper in a Y pattern. Other mods' wrenches also work (`c:tools/wrench` tag).

| Module | Behaviour |
|--|--|
| Crafting table | A crafting table. |
| Furnace | Vanilla smelting speed. Uses 20 FE/t only while smelting. Options *(in-game)*: auto-push output to the ship inventory, auto-pull input with a whitelist filter. |
| Alloy furnace | Like the furnace module, with two inputs and the alloy recipes. |
| Inventory access | Exposes the ship inventory to pipes and hoppers, with a filter on what is exposed. |
| Crafting & inventory access | Both of the above in one block. |
| Energy IO | Exposes the energy buffer. Input/output is switchable through its GUI or with the wrench. No rate limit by default; players can set limits *(in-game)*, capped by a config maximum *(config)*. |
| Door | An extra exit. Can't be locked. |
| Hatch | An exit (e.g. through the roof or floor). Can't be locked. |
| Tesla turret (small / medium) | See below. |
| Ship locator | Works like a compass: points to the last ship used. |

### Tesla turrets
- **Placement:** roof only, in the middle of a wagon or the drillhead. Each section can have at most one turret.
- **Damage:** small 4, medium 6, every 0.25 s. It ignores armor completely.
- **Area:** a box around its own section, enlarged by 3 blocks (small) or 7 blocks (medium). Line of sight isn't required.
- **Targets:** hostile mobs only by default *(in-game setting)*.
- **Energy:** costs 100 FE per hit and doesn't fire without enough energy. No cost while idle.
- **Status panel:** turrets are listed and can be switched on or off there.

---

## 9. Energy

All machines on the ship share one buffer (1M / 2M / 5M FE).

| Consumer | Cost |
|--|--|
| Moving | 1 FE/t *(config)* |
| Drilling | 1 FE/t per block of drill face while the drill spins, whether or not anything is being broken *(config)* |
| Furnace / alloy furnace modules | 20 FE/t while smelting |
| Tesla turret | 100 FE per hit |

- Modules keep drawing from the buffer while the ship is parked and the generator is off.
- A module that increases the buffer may come later.

---

## 10. Damage

| Source | Damage |
|--|--|
| Spinning drill (contact) | 20 *(config)* |
| Squashed between the moving ship and a solid block | 100 *(config)*. Entities are pushed aside instead whenever possible. |
| Tesla turret | 4 (small) / 6 (medium) per 0.25 s, ignores armor |

Passengers inside a moving ship never take damage from it.

---

## 11. Materials, alloys and world generation

- Recipes use common tags (`c:ingots/steel`, `c:ingots/bronze`, …). Materials from other mods are preferred.
- **If no other mod provides tin, zinc, bronze, brass or steel**, the mod enables its own:
  - ingots, blocks and nuggets
  - tin and zinc ore, generated like iron ore
- **Detection:**
  - The mod checks for popular mods that provide these materials: Create, Mekanism, Thermal and Immersive Engineering.
  - It then checks whether the matching tag already contains another mod's item, to catch less popular mods.
  - Items are always registered, because Minecraft can't register them conditionally. When another mod provides the material, our own world generation and recipes are disabled and the items are hidden in JEI.
- **The alloy furnace and its recipes** are only offered when no other mod provides a way to make the alloys.

### Alloy furnace
A standalone block (and a module). It works like a furnace with two inputs and burns furnace fuel.

| Alloy | Inputs | Output | Time |
|--|--|--|--|
| Bronze | 3 copper + 1 tin | 4 bronze | 200 ticks |
| Brass | 3 copper + 1 zinc | 4 brass | 200 ticks |
| Steel | 1 iron + 2 `#minecraft:coals` | 1 steel | 200 ticks |

---

## 12. Recipes

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
| Crafting module | Crafting table + module base |
| Crafting & inventory access module | Crafting table + chest + module base |
| Door module | Any door + module base |
| Hatch module | Any trapdoor + module base |
| Ship locator | Compass + module base |

---

## 13. Compatibility

- The mod should work alongside most mods. The goal is a mobile base the player builds in freely.
- **Tested against:** JEI, Create, Mekanism, AE2, Refined Storage 2 and FTB Chunks.
- There's no in-game guide or advancements for now.

## 14. Configuration

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

Options that come with later features: radar ore tags and radius per tier, Energy IO maximum rate.

---

## 15. Assets

Textures and sounds are supplied by the author. Missing assets use vanilla placeholders.

### Textures in use
| Texture | Used by |
|--|--|
| `hull` | Interior walls and the exterior hull |
| `periscope` | Helm |
| `status` | Status panel. It has no separate warning texture; the panel lights up instead. |
| `engine_off` / `engine_on` | Generator, idle / burning |
| `inventory_io` | Storage access |
| `crafting_module` | Built-in crafting table |
| `door_bottom` / `door_top` | Door, lower / upper half |

**Ready for later features:** `furnace_off`/`furnace_on`, `alloy_furnace_off`/`alloy_furnace_on`, `energyio`, `hatch`.

**Still placeholders:** all item textures, the hull lamp, the drill and exterior model, and the GUI screens, which are drawn without textures.

### Sounds
All four are mono, so they fade with distance. Stereo originals are kept in `art-source/sounds/`.

| Sound | When |
|--|--|
| `engine_idle` | Loops while someone is driving, the ship has energy, and it isn't moving |
| `engine_working` | Loops while the engine is on and the ship moves. It crossfades with idle. |
| `engine_stop` | Plays once when the engine turns off (the driver leaves or energy runs out) |
| `digging` | Loops while the drill spins |

---

## 16. Roadmap

1. **MVP: done.**
   - Basic-tier drillhead.
   - Placing it, entering and leaving it.
   - Driving, drilling and gravity.
   - Inventory, generator, status panel with warnings, and crafting table.
   - Drill durability, repair and enchanting.
   - Chunk loading and pickup.
   - Basic-tier recipes.
2. **Wagons.**
3. **Modules and wrench.**
4. **Tesla turrets.**
5. **Standard and hardened tiers, materials and alloys.**
6. **Radar and ore deposits.**
7. **Filters and barrels.**
8. **Mouse steering through the periscope.**

## 17. Open questions

1. **Large ore deposits** (README): size, shape, rarity, depth range, which ores (vanilla only, or other mods' ores via tags), whether they replace or add to normal ore generation, and whether they can be toggled in the config.
2. **Alloy recipes:** the ratios and times in section 11 are proposals. Confirm or change them.
3. **Status panel warning:** keep the light-up indicator, or add a separate warning texture?
4. **Wagon interiors:** do wagons have any built-in fixtures (a door, a lamp), or are they empty rooms?
