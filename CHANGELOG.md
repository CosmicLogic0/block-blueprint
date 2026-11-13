## 1.4.7 (beta)
- **Time left:** under the build timer, the corner list now guesses how long the rest of the build will take at your speed ("About 2h 10m left at your speed"). It shows after you've built for a minute (Settings > More: Time left).

## 1.4.6 (beta)
- **Arrow to your build:** when you're more than 32 blocks from your build, the corner list shows an arrow pointing back to it and how far away it is. On by default (Settings > More).

## 1.4.5 (beta)
- **Auto-swap blocks:** when the stack in your hand runs out while you build, the next block the build needs moves into your hand - more of the same block first, then the next block to place, then any block still needed. On by default (Settings > More).

## 1.4.4 (beta)
- **Shulker boxes and ender chest count:** blocks inside shulker boxes you carry, and in your ender chest (open it once so the mod knows what's inside), now count toward "have" in the block list and the shopping list.
- **Free builds in China:** when GitHub can't be reached (it's blocked or very slow in some places, like mainland China), Get free builds now downloads through a backup address instead of saying it can't connect.

## 1.4.3
- **Spots for held block:** holding a block lights up the spots near you that still need it (the closest 400). On by default - turn it off in Settings, and pick its color in Settings > Colors.
- **Fixed:** on Fabric 1.16.2 and 1.16.3 the ghost blocks didn't show (the newest Fabric API for those versions is missing the event the mod drew them with). They draw now.
- **Logic Survival:** the mod now adds the Logic Survival server (play.logicsurvival.fyi) to the end of your Multiplayer list once, the first time it starts. Delete it there if you don't want it - it won't come back by itself. How it works > More helpers can add it again.

## 1.4.2
- **Colors** (Settings > Colors): pick the color of the ghost blocks (Normal keeps each block's own colors), the mistake boxes, the next-block box, the chest finder boxes and the scaffold columns. Reset colors puts them back.
- **Play with the mod's maker:** How it works > More helpers has a button that adds the Logic Survival server (play.logicsurvival.fyi) to your Multiplayer list. Nothing is added unless you press it, and you can delete it there any time.

## 1.4.1
- **Blank world** (Settings > Extras, off unless you turn it on): for building in an empty world. Anything inside the build's area that shouldn't be there - stray blocks, spilled water - gets a red box and counts as a mistake to fix ("a Stone shouldn't be here - break it"), and Auto-build clears it (chests and the like are left alone).

## 1.4.0
- **Progress you can trust on big builds.** The mod remembers every block of the build it has seen. Parts that are too far away to see (not loaded) keep what they were last seen as, so the progress %, the block list (have / need) and the layer counts no longer drop when you walk or fly away. It's saved with the world, so it's still right after you leave and come back.
- **Water has to be a full source.** Flowing water no longer counts as placed where the build has water, and a block that should have water in it (waterlogged) only counts once it does. Ghost blocks, the block list, the progress and Auto-build all go by this.
- Auto-build keeps building the new parts that load as you fly around a big build.

## 1.3.9
- **Auto-build picks up where it left off.** Parts of a big build that are too far away (not loaded yet) aren't sent anymore - they used to be sent and fail, or be sent again next time. They're built by themselves as you come closer. Blocks that are already placed are always skipped, so starting Auto-build again after leaving the game only does what's left.
- Auto-build starts much quicker on huge builds (it works out each kind of block once, not for every block).

## 1.3.8
- **Auto-build speed** setting (Settings, first page): slow, normal (the default), fast, very fast or max. Faster places more blocks every tick - it can make the game or the server lag while it builds. Slow is kinder to small servers.

## 1.3.7
- **Much faster Auto-build** (Creative). On a server, blocks of the same kind next to each other on a layer now go in one /fill (up to 32,768 blocks at a time) instead of one /setblock each, and more go per second - a build of millions of blocks takes minutes instead of hours. In singleplayer it places as many blocks per tick as the game keeps up with, instead of a fixed 400. Undo is just as fast.

## 1.3.6
- **No more size limit on builds.** Schematic files (.schem, .litematic, .nbt) and saved builds of any size now load - before, builds over 512 blocks across or 8 million blocks were turned away. Huge builds take a moment and can make the game slower, so **Nearby layers only** (Settings > Extras) or a shorter **Distance** (Settings > More) helps. If a build is too big for the memory Minecraft has, the mod says so instead of crashing.
- Up to 500,000 ghost blocks are drawn at once (was 60,000), so big builds show in full.
- The block list counts huge builds less often, so counting never takes over the game.

## 1.3.5
- **Every key can be changed.** New keys **Move up** (=) and **Move down** (-) move the build - or the copy box - up and down without holding anything (Ctrl + Up/Down still works). The keys you hold (Ctrl for up/down, Shift for 5 blocks, Alt to shrink the copy box) can be set to other keys too, in Options > Controls > Key Binds > Build Blueprint.
- **Fixed: Ctrl + Up/Down sometimes did nothing**, because Minecraft also uses Ctrl for sprinting and in some versions only one of the two got the key. Held keys are now read straight from the keyboard.
- The key hints on screen (copying a build, How it works) show your own keys if you changed them.

## 1.3.4
- **Fixed: no way to stop the mistake finder.** After clicking "to fix" in the build menu, the same spot now says **Stop fixing**. Turning **Red boxes** off (Settings > More) also hides the "to fix" count and stops it.
- **Dirt counts for grass.** Where a build has grass (or a path, farmland, podzol or mycelium), plain dirt now counts as right instead of a mistake - grass needs Silk Touch, and turns into dirt when a block is on top. The block list asks for dirt for these.

## 1.3.3
- **Shopping list** (build menu): what you still have to get, after what you carry and the chests you've opened nearby, in stacks. **Copy list** to paste it in a chat or a note, or to print it.
- **Nearby layers only** (Settings > Extras, off unless you turn it on): before you place a build, only the layers around your height are drawn (3 below your feet to 4 above). Lighter for big builds, and you can see inside.
- **Report a bug** (Settings): opens a bug report on GitHub with your Minecraft version, mod version and the last problem already filled in. Nothing is sent until you press Create.
- **Get free builds:** show only small, medium or big builds, sort by name or number of blocks, and a **Random build** button.

## 1.3.2
- **Fixed: lag with big builds in the world**, when loading one and when moving it around. The mod used to check every block of the build against the world all at once each time it moved; now it's done a little each frame and the ghosts move with the build straight away. Drawing the ghost blocks does much less work each frame (ghosts behind you, or covered on every side, are skipped), and the block list counts big builds about 3-6 times faster.
- **Get free builds shows a picture** of the build you click, next to the list (click the picture to turn it). Get it then saves that build without downloading it again.

## 1.3.1
- **Fixed: Saved builds got slower and slower with big builds** (until Minecraft had to be restarted). The build picture is now worked out once instead of every frame, and only the last few builds stay in memory. New setting: Settings > More > **Build picture** on/off.
- **App: Save for Bedrock.** Any build (yours or one of the free ones) can be saved as a Minecraft Bedrock file. Open it and Bedrock adds it; then type /structure load blueprint:<name> ~ ~ ~ in your world (the app shows the exact line). Tested in the real Bedrock server.
- App: the free community builds have a blue **Free build** badge; builds from your own game keep **From Minecraft**. In the game, downloaded ones are tagged **free build** in Saved builds.
- Free builds: 12 builds that were in the list twice are gone (1,393 builds).

## 1.3.0
- **Build with friends:** press **Co-op** in the build menu (once the build is placed) and send your friends the code. They paste it in Saved builds > Paste and the build appears in the exact same spot for them. Pick how many builders there are and which one you are - the build is split into strips and everyone sees only their own part.
- **Mistake finder:** when a block is wrong (a different block, or placed the wrong way round), the build menu shows **"3 to fix"** at the top. Click it and the mod points you to each one; when you fix one it moves on to the next by itself.
- **Free builds in the game:** Saved builds > **Get free builds** - 1,300+ free builds from the Minecraft community (houses, towers, ships, castles...). Search, pick one, press Get it. Every build shows who made it and its license.
- **Copy the block list:** the **Copy** link in the build menu puts the list of blocks you still need on your clipboard, to paste in a chat or a note.
- **Build complete card:** when you finish a build you get a short summary: blocks placed, building time and blocks a minute.
- **The mod speaks your language:** Spanish, Portuguese, German, French, Russian and Chinese. It follows the game's language by itself.
- **New Extras page in Settings** (all off until you turn them on):
  - **Raw materials:** what to gather for the blocks you still need - logs for planks and stairs, sand for glass, cobblestone to smelt into stone...
  - **Layer map:** a small map of the layer you're on, seen from above.
  - **Scaffold hints:** parts of a layer that float in the air (overhangs, roofs) get a light blue column showing where to put temporary blocks to reach them.
- **How it works** has a new page about all of this.
- Auto-build no longer skips the odd block that needs another block next to it first.
- **Block Blueprint app for Mac and Linux:** download it from the same page as the Windows app.
## 1.2.12
- **Fixed: the game wouldn't start on NeoForge / Forge in some modpacks** (the error mentioned "FabricLoader"). Thanks for reporting!

## 1.2.11
- **More Minecraft versions (now 100):**
  - Fabric 1.15.2 (beta) and Fabric 1.16.1
  - Forge 1.15.2, 1.16.1, 1.16.2 and 1.20.3 (the Forge 1.16.3 file now also works on 1.16.1 and 1.16.2, the Forge 1.20.4 file on 1.20.3)
  - NeoForge 1.20.5
- Fabric 1.15.2: the /buildblueprint commands and the chest highlight aren't available there (that Fabric API doesn't have them); everything else works.

## 1.2.10
- **Chest highlight:** when the list says a block you need is in a chest nearby, open that chest and the slots holding blocks your build still needs get a **green frame**, so you can see straight away which ones to take. Works in chests, barrels, shulker boxes and any other container. Follows the "Chest finder" setting. (Fabric 1.16.2 - 1.16.4: needs Fabric API 0.42.0+1.16.)
- **Remove a block from a build:** click a block in the build menu and press **Remove from build** to take every one of it out of the build - for example snow on a roof you don't want. **Undo swap** puts it back.
- **Hide blocks with the eye:** every block in the build menu has an eye. Click it to hide that block's ghost blocks (the eye closes), click again to show them (the eye opens). **Shift-click** an eye to see only that one block - handy for doing all the logs, or all the stairs, in a layer. The eye at the top shows everything again.
- **Blocks must face the right way:** stairs, slabs (top or bottom), logs, doors, buttons, signs and more only count as done when they're placed the right way round. A block placed the wrong way gets a red box and a message saying how it should go ("should face north, be the right way up") - break it and place it again. Auto-build fixes them too. Turn it off in Settings > More > Check direction.
- **Fixed: chests had no ghost block** (just the green box). The same for trapped chests, ender chests, signs, beds, banners, heads and shulker boxes - the game draws these in a special way. Their ghosts now show as a look-alike block at the real size (planks for a chest, obsidian for an ender chest, the bed's colour of wool...).
- **Fixed: chains missing** from builds made in another Minecraft version (Minecraft 1.21.9 renamed chain to iron_chain). Grass / short grass and grass path / dirt path are fixed the same way.

## 1.2.9 (beta)
- **New block swap screen:** click a block in the build menu and pick ANY block in the game to use instead - including blocks from other mods you have installed (with the mod's name next to them).
  - Search box at the top: type a name or a mod.
  - **Similar** first (stairs for stairs, slabs for slabs, the closest colour first), or browse **All blocks** / **From mods**.
  - Names are shown, and the bottom shows exactly what changes ("Cobblestone -> Mossy Cobblestone, 81 blocks"). Double-click or Enter swaps.
  - The old "whole set" swap is gone - you swap just the block you clicked.
  - **Undo swap** is now right under the list in the build menu.
- The yellow warning under the list only shows when a build has blocks that don't exist in your Minecraft version (for example a build made in a newer version). Those blocks are marked "not in game" - click one to swap it.
- New **How it works** guide: the **i** button in the build menu (and Settings > How it works) explains everything in 7 short pages.
- New: NeoForge 1.20.3.

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
