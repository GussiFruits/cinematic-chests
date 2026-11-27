# Cinematic Chests

[![Build](https://github.com/GussiFruits/cinematic-chests/actions/workflows/build.yml/badge.svg)](https://github.com/GussiFruits/cinematic-chests/actions/workflows/build.yml)

An add-on for [Cinematic Interactions](https://www.curseforge.com/minecraft/mc-mods/cinematic-interactions) (NeoForge 1.21.1). Chests, trapped chests, ender chests, barrels and shulker boxes open as first-person scenes instead of a window. You can move from a chest to a crafting table, furnace or any other station, and back, without leaving the cinematic view.

## Install

Put the jar on **both client and server** next to Cinematic Interactions **0.18.x** (0.18.30 or newer) and NeoForge 21.1.200 or newer, for Minecraft 1.21.1.

Nothing else is required. Support for other mods turns on only when they are installed. Sophisticated Storage is an optional dependency: version 1.5.39 or newer of Sophisticated Core.

If a later Sophisticated version changes something the upgrade support relies on, that support switches itself off with one warning in the log. Storage scenes keep working.

## How each block plays

| Block | Scene |
|---|---|
| Chest, trapped chest, ender chest | You lean over the chest, the lid opens, and the items lie inside: 9 across, 3 deep, in the window's order. A double chest shows each half's 27 slots inside that half. |
| Barrel facing up | The items lie in its open top. Work from any side. |
| Barrel facing sideways | The items sit upright in its open front. You crouch in front of it. |
| Shulker box facing up | The lid lifts and the items sit upright in the opening between base and lid. |
| Barrel facing down, shulker box facing sideways or down | The normal window. |

## Modded containers

Slots are laid out the way the block's own window lays them out, read from the menu's slot positions. A modded 12×9 chest looks like its window, not like a vanilla chest.

- **Detected automatically:** blocks from any mod that extend vanilla's chest, ender chest, barrel or shulker box. They get the matching layout. For example, Woodworks' wooden chests are detected.
- **Built in:** Sophisticated Storage's chests, barrels and shulker boxes, in every tier, get scenes automatically whenever it's installed. Turn this off with `builtInSupport = false` in `config/cinematicchests-startup.toml`.
- **Added by name:** list any other block in `config/cinematicchests-startup.toml`:

  ```toml
  extraContainers = ["sophisticatedstorage:*chest=chest", "sophisticatedstorage:*barrel=barrel", "somemod:crate=front"]
  ```

  `*` matches any characters, so one line covers a whole family of tiers. A shape listed here overrides a detected one.

  | Shape | Layout |
  |---|---|
  | `chest` | Chest-shaped with a lid: the items lie inside. |
  | `barrel` | The items lie on its top (facing up) or sit on its front (facing sideways). |
  | `shulker` | A box whose lid lifts: the items sit in the opening. |
  | `front` | Anything else: the items sit upright on its front face. |

- **Turned off:** `excluded = ["modid:block"]` gives a block its normal window back. This works for vanilla blocks too.

A modded block is opened by running its own right-click, as if you had clicked it with an empty hand. Its mod still opens the menu its own way, with whatever extra data its screen needs, and applies its own rules and statistics.

The startup config must be the same on the server and on every client. Cinematic Interactions needs both sides to agree on which blocks are stations. The game log lists every detected block at startup.

Turn all modded scenes off on one client with `scenes.moddedContainers` in the client config.

### Sophisticated Storage upgrades

When Sophisticated Core is installed, a block's upgrade slots hang as a row:

| Block | Where the upgrade row is |
|---|---|
| Chest | Inside the open lid |
| Barrel facing up | Behind its open top |
| Barrel facing sideways | Below the storage slots |
| Shulker box | Below the opening |

| Input | Action |
|---|---|
| Click with an upgrade | Clip it in |
| Click an upgrade | Open its slots over the block (a crafting grid with its result, filter slots, smelting slots); click again to close |
| Shift-click | Take it out |
| Right-click | Switch it on or off |

Switched-off upgrades sit in a red slot. When Sophisticated refuses a change, its own reason shows above the hotbar.

Everything goes through Sophisticated's own menu, the same way its window does.

An opened upgrade appears on a chest's lid above the rack, or on top of a barrel. The camera draws back so the storage and the upgrade are both in view. Opening an upgrade here is the same as opening its tab in the window, so the window remembers it.

The upgrade's slots are real menu slots, laid out like its tab:

| Upgrade | In the scene |
|---|---|
| Crafting | 3×3 grid and result; a row of results to pick from when several recipes match |
| Smelting, smoking, blasting | Input over fuel with the result beside them, plus a cooking bar and a fuel bar |
| Auto-smelting (and auto versions) | Input filter, then the furnace, then the fuel filter |
| Stonecutter, Sawmill mod | Input and result, plus a row of shapes to choose from |
| Filters (pickup, filter, magnet, feeding, compacting, void, hopper, …) | Filter slots in a square. Click with an item to set its pattern (the item stays in hand); click with an empty hand to clear it. When matching by tag, the item's tags and the filter's tags show as clickable, scrollable lists. |
| Jukebox | Disc slots and ◀◀ ▶ ■ ▶▶ ⇄ ↻ buttons; the playing disc is framed green |
| Tank, battery | Their slots and a level bar |
| Alchemy | Filter slots, each with a condition button (scroll to change the "hurt" health value) |

Each upgrade's settings are buttons above its slots: allow or block, match by item, mod or tag, durability, components, direction, and so on. A button is lit when its setting is on, and its tooltip names the setting and its value. An upgrade with several filters gets a labeled row of buttons for each.

Every button and slot works through Sophisticated's own menu and settings, so the window always shows the same state.

Only Chipped's block-transformation picker still needs the window (sneak-click).

While a storage scene is open, the jukebox upgrade's music notes aren't spawned, so they don't cover the view.

The Sophisticated-specific code (`client/compat/`) is only loaded when the mod is installed.

## Controls

| Input | Action |
|---|---|
| Left-click | Take or put down a stack |
| Right-click | Take half, or put down one |
| Shift-click | Move a stack to the other side |
| Q over a slot | Drop one item; Ctrl+Q drops the stack |
| Shift-click in the inventory drawer | Move a stack into the chest |
| Sneak + right-click with an empty hand | Open the normal window |
| Esc | Step back |

All other Cinematic Interactions controls work as usual.

## Vanilla rules still apply

Every click is a normal click on the block's real menu, so hoppers, comparators, locks, loot tables and trapped-chest signals behave exactly as in vanilla.

Opening still counts the statistic and angers nearby piglins. A chest under a cat or a solid block, an ender chest under a solid block, and a shulker box without room for its lid all stay shut, as in vanilla.

## Client config (`config/cinematicchests-client.toml`)

- `scenes.chests`, `scenes.enderChests`, `scenes.barrels`, `scenes.shulkerBoxes`, `scenes.moddedContainers`: turn a block's scene off to get its normal window back.
- `display.showCounts`: show stack sizes next to the items.
- `display.showGrid`: faintly mark every slot, so empty ones can be found.

## How it hooks in

The public `AddonStations.register` refuses blocks in the `minecraft` namespace, so `StorageBlocks.registerStations` writes the definitions straight into the API's tables. It uses an accessor mixin and takes the same lock as the API.

All other add-ons still go through the normal check. Six more mixins handle the rest:

| Mixin | What it does |
|---|---|
| `MenuActionsMixin` | Opens vanilla storage under vanilla's rules (the ender chest menu, shulker lid room, stats, piglin anger), and modded storage through its own right-click. |
| `DefinitionMixin` | Modded stations accept any menu class except your own inventory. |
| `AddonScenesMixin` | Applies the config switches. |
| `SceneMixin` | Stops the other half of a double chest being offered as a separate station. |
| `SceneScreenMixin` | Names storage in the "move over" label by its pick-block item, so templated names like Sophisticated's read properly. |
| `ParticleEngineMixin` | Holds back the jukebox upgrade's notes during a storage scene; a vanilla class, matched by name, with no other mod needed. |

These mixins target Cinematic Interactions internals, which are not a stable API, so check them when updating the host mod. In a dev run, the targets are loaded at startup, so a mixin that no longer fits fails right away.

## Building

```
./gradlew build      # build/libs/cinematicchests-<version>.jar
./gradlew runClient  # dev client with Cinematic Interactions from libs/
```

Dev runs also load Woodworks, Sophisticated Storage, and Sophisticated Backpacks with its own Cinematic Interactions add-on (`libs/dev/`) to test modded containers. That add-on patches the same base-mod code as this one. Their scenes are set up in `run/config/cinematicchests-startup.toml`.

`libs/` holds the official Cinematic Interactions 0.18.30 jar from CurseForge (file 9345403). The mod isn't on any Maven repository.
