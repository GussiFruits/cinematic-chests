# Changelog

Each release's notes are taken from its section here (`## <version>`) for the GitHub release and the CurseForge file.

## 0.1.1

- **Storage with something on top now opens as a scene.** A chest or barrel with a block on it (a chest stacked on a chest, say)
  was turned away with "In the way". Its slots now show on its front instead, as on a sideways barrel:
  - the upgrade rack runs along the bottom of the front;
  - upgrade panels (crafting, smelting, jukebox and the rest) stand just in front of the block;
  - for a double chest you step back a little, so the whole front fits in view.
- **"In the way" names Sophisticated Storage blocks properly**, instead of showing "%s%s Netherite Chest".
- **Stacks lying in an open chest stay in their own slot.** The piles are a little smaller and still look messy, but they no
  longer spill into the next slot.

## 0.1.0

- First release: chests, trapped chests, ender chests, barrels and shulker boxes open as Cinematic Interactions scenes.
- Modded storage is detected automatically, and more can be added in the startup config.
- Built-in Sophisticated Storage support: upgrade rack, crafting, stonecutter, smelting and auto-smelting, filters with tags,
  jukebox and alchemy, all inside the scene.
