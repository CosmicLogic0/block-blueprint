## 1.2.8
- **Fixed: Fabric 1.16.x, 1.17.x, 1.18.x and 1.19-1.19.2 refused to start** with "requires fabric-api, which is missing" when you had an older Fabric API. Any Fabric API for your version works now.
- The block list in the top-left corner now starts at **50% size** (it was too big). Change it in Settings > List size. If you never changed it, it switches to 50% by itself.
- Fabric 1.16.2 - 1.16.4: no more crash with the older Fabric API those versions get. Keys and menus work; for the /buildblueprint commands install Fabric API 0.42.0+1.16 (works on 1.16.2 - 1.16.5).
- Forge 1.20.1 and 1.21.1 files are now only listed for their own version (they need that exact Forge). Forge 1.20 and 1.21 have their own files.
- New: Forge 1.16.5, 1.17.1, 1.21 and 1.21.11.
- **Most versions are now Release instead of Beta**: every file is tested before upload the way a player runs it (the real game, the real loader and the Fabric API from Modrinth, joining a world and using the mod) on every Minecraft version it's listed for.
- Build Blueprint is now on **Modrinth** too.

## 1.2.7
- **Copy a build (Insert key):** no more automatic selection - a box starts on the block you look at and you make it as big as your build. Arrows grow it, Alt + arrows shrink it, Ctrl + Up/Down for top/bottom, Shift = 5 blocks, and **Page Down stretches it to the block you're looking at**.
- **Simpler menus in every version (Fabric, Forge, NeoForge):**
  - Build menu (Page Up): layer arrows right on the layer tab, new **Turn** and **Remove** buttons, and tools only show when they make sense (moving tools until it's placed, auto-build in Creative).
  - Settings: a short **Basics** page and a **More** page instead of 18 buttons at once.
  - Saved builds: **search box**, **Save .schem** button, Delete asks twice, Enter loads, nicer names for schematic files, buttons fit small windows.
  - One consistent look across all screens.
- NeoForge 26.x: no more warning screen at startup.
- New (beta): Fabric 1.16.2, 1.16.3 and 1.16.4.
- App: **1,400+ new templates** from 149 open-license creators on Modrinth (houses, towers, ships, castles, ruins...), each credited with its creator, license and link.

## 1.2.6
- App: removed the circle shape guides from the templates.

## 1.2.5
- App: Minecraft's own buildings (villages, trial chambers, shipwrecks, ruins...) have their own **Minecraft Buildings** tab with a dropdown to pick the kind. They're no longer mixed into All templates.

## 1.2.4
- **Schematic files in every version (Fabric, Forge, NeoForge):** .schem (WorldEdit / Sponge v1-v3), .litematic (Litematica) and .nbt (structure blocks) files show up in Saved builds. Put them in the BlockBlueprint/builds folder in your user folder; your WorldEdit and Litematica schematics folders are read too.
- New command **/buildblueprint export** saves the loaded build as a .schem file for WorldEdit, Litematica, Axiom and other tools.
- App: **Open .schem file** (or drag one onto the window) and **Save as .schem**.
- App: **1,300+ Minecraft buildings** - Minecraft's own village houses, outposts, shipwrecks, ruins, trial chambers and more, read from your own game files.

## 1.2.3
- **Forge and NeoForge for Minecraft 26.x** (beta): Forge 26.1, 26.1.1, 26.1.2, 26.2 and 26.3; NeoForge 26.1, 26.1.1, 26.1.2, 26.2 and 26.3.
- More in-between versions (beta): Forge 1.18, 1.19.1, 1.20, 1.21.6, 1.21.7 and 1.21.9; NeoForge 1.21, 1.21.2, 1.21.6, 1.21.7 and 1.21.9.
- Every new version was started in Minecraft by an automatic test before upload.

## 1.2.2
- New (beta): Forge 1.21.8 and 1.21.10, NeoForge 1.21.10 and 1.21.11 - launch-tested before upload.

## 1.2.1
- **20 new Forge and NeoForge versions** (beta): Forge 1.18.1, 1.18.2, 1.19, 1.19.2, 1.19.3, 1.19.4, 1.20.2, 1.20.4, 1.20.6, 1.21.1, 1.21.3, 1.21.4 and 1.21.5; NeoForge 1.20.2, 1.20.4, 1.20.6, 1.21.3, 1.21.4, 1.21.5 and 1.21.8. Every one was started in Minecraft by an automatic test before upload.
- New Fabric versions (beta): 1.21.9 and 1.16.5.
- The in-game "new version" message now points to CurseForge and only shows when a new mod version is out.

## 1.2.0
- App updates fixed: only one copy of the app can run, the update installs when you press Restart (not in the background when you close the app), the app closes completely first and opens again by itself, and the installer closes any leftover copy before replacing files.

## 1.1.9
- **Forge and NeoForge!** Build Blueprint now also runs on Forge 1.20.1 and NeoForge 1.21.1 (beta).
- New Fabric version: 1.16.5 (beta).
- Update checks in the mod no longer need newer Java, so they work on older Minecraft too.

## 1.1.8
- 12 more Minecraft versions for the mod (beta): 1.21.10, 1.21.3, 1.21.2, 1.20.2, 1.19.4, 1.19.3, 1.19.2, 1.19.1, 1.19, 1.18.2, 1.18-1.18.1 and 1.17-1.17.1. That's 23 builds covering 1.17 to 26.3.

## 1.1.7
- The mod now comes for 11 Minecraft versions: 26.3, 26.2, 26.1, 1.21.11, 1.21.6-1.21.8, 1.21.5, 1.21.4, 1.21-1.21.1, 1.20.5-1.20.6, 1.20.3-1.20.4 and 1.20-1.20.1.
- Everything except 26.3 is a beta: please report anything that looks wrong on the GitHub issues page.

## 1.1.6
- Fixed: the installed app could get stuck on "Loading blocks..." forever (a file the 3D view needs was left out of the installer).
- New loading screen: shows each step, how many block pictures are loaded, and a clear message if something goes wrong or takes too long.

## 1.1.5
- New project address: github.com/CosmicLogic0/block-blueprint (updates and links point there now).

## 1.1.4
- Fixed: the game crashed when opening the Page Up menus ("Can only blur once per frame").
- Safety net: if any part of the mod runs into a problem it now shows a message in chat instead of crashing the game.

## 1.1.3
- App: cleaner Settings with on/off switches and sections, grouped toolbar with hover names and shortcuts,
  a Keyboard shortcuts screen (press ?), Esc closes panels, toolbar fits smaller windows.
- Updated Boat 3 template.

## 1.1.2
- App: version number and "Check for updates" in Settings, plus a banner when a new version is out.
  The installed app downloads updates by itself and asks to restart.
- Mod: tells you in chat when a newer Build Blueprint is available.

## 1.1.1
- Smart copy: look at a build and press Insert - the whole build is boxed. Arrows resize the box, ground and trees are left out.
- Swap blocks in-game: change a whole wood / stone / colour set (oak -> spruce, black sails -> red) or one block.
- Builds start fully built; press Delete to place, then build layer by layer.
- Big builds (14,000+ blocks) show completely; blueprint is remembered per world.
- Mirror, snap to ground, next-block helper, pick block from ghost, chest finder, build timer, sounds.
- Auto-build in Creative, with undo.
