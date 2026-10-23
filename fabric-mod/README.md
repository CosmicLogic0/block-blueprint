# Build Blueprint (Fabric mod, Minecraft 26.3)

A **client-side** mod: it runs only on your PC and never changes the server. You paste a build from the
Block Blueprint desktop app, see exactly what materials you need (and how many you already carry),
preview the build in your world, and slide it into place with commands. **You place the blocks yourself** -
nothing is placed automatically, so it is safe on servers that allow client mods.

## Commands (all typed in chat, all local to you)
| Command | What it does |
|---|---|
| `/buildblueprint load <id> <data>` | The lines the app gives you. Paste every line, in any order. |
| `/buildblueprint` | Opens the materials menu: what you need / what you have, updates as you pick things up |
| `/buildblueprint move up 1` | Slides the blueprint. `up down left right forward back` (relative to the way you face) or `north south east west` |
| `/buildblueprint move left 5` | Any amount works: `move up 3`, `move back 10`, ... (amount defaults to 1) |
| `/buildblueprint rotate left|right|180` | Turns it about its centre (stairs, doors etc. turn too) |
| `/buildblueprint here` | Moves the blueprint's corner to where you are standing |
| `/buildblueprint layer 3` / `layer all` | Show just one layer while you build it |
| `/buildblueprint materials` | The needed/have list in chat |
| `/buildblueprint status` `show` `hide` `clear` `help` | |

## Keyboard controls (rebind in Options > Controls > Build Blueprint)
| Key | Action |
|---|---|
| Left arrow / Right arrow | Move the blueprint left / right |
| Down arrow | Move it forward (away from you) |
| Up arrow | Move it back (toward you) |
| Shift + Up arrow | Move it up |
| Shift + Down arrow | Move it down |
| Page Down | Rotate 90 degrees (Page Up rotates back) |
Holding a key keeps moving it. "Forward/back/left/right" follow the way you are facing. (Down = forward and Up = back
is the reverse of most games; swap them in Controls if you prefer.)

In the preview, white outlines = blocks still to place, red = a different block is in the way; blocks you have
placed correctly stop being drawn.

## Install the mod (play side)
1. Install the **Fabric Loader** for Minecraft **26.3**: https://fabricmc.net/use/installer/ (needs Java 25, which the Minecraft launcher already includes).
2. Put the **Fabric API** jar for 26.3 and `buildblueprint-1.0.0.jar` into `%APPDATA%\.minecraft\mods`.
3. Start the "fabric-loader-26.3" profile in the Minecraft launcher.

## Build the mod (one time)
You need the **Temurin 25 JDK**: https://adoptium.net (choose version 25, Windows x64, `.msi`).

### Step 1 - nothing to do (the Gradle wrapper is included).

### Step 2 - Versions
Already filled in for your CurseForge profile "My mod" (Fabric Loader 0.19.5, Fabric API 0.164.0+26.3, Minecraft 26.3).
Only change `gradle.properties` if you use a different profile.

### Step 3 - Build
Double-click **build-mod.bat**. It builds the mod and copies it into your CurseForge profile "My mod" (`%USERPROFILE%\curseforge\minecraft\Instances\My mod\mods`). The jar is also in `build\libs`.

## If the build fails
This mod was written without being able to compile against 26.3 (Fabric's servers can't be reached from where
it was written), so a first-build error is possible. The pieces are separated so that one problem doesn't block the rest:
- Errors in `MaterialsScreen.java` -> set `enableMenu=false` in `gradle.properties` and rebuild. `/buildblueprint` then shows the materials list in chat instead.
- Errors in `PreviewModule.java` -> set `enablePreview=false` and rebuild (no in-world outline, everything else works).
- Errors in `KeysModule.java` -> set `enableKeys=false` and rebuild (commands still work).
- Errors in `Cmd.java` -> the comment at the top lists the older class name to try.
**Copy the red error text and send it to Claude** and it will fix the exact lines.

## Notes
- The core logic (decoding, rotating, counting materials, moving) is plain Java and is tested: `src/test/java/CoreSelfTest.java`.
- The app now draws and exports beds, chests, bells, shulker boxes, signs and banners. Heads, hanging signs and decorated pots are still missing.
- Server rules: this mod only shows overlays and lists. Some servers still restrict client mods - check yours.
