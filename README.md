# Block Blueprint

Design Minecraft builds in 3D on your PC (or phone), then build them in your world with the **Build Blueprint** mod:
see-through ghost blocks, a materials list, layer-by-layer building, block swapping and copying builds from your world.
Made for **Minecraft Java 26.3** with Fabric.

## Download
- **Windows app:** go to [Releases](https://github.com/dylanemason0330-code/block-blueprint/releases/latest) and download
  `Block-Blueprint-Setup-x.y.z.exe`. The app updates itself when a new version comes out.
  Windows may say "Windows protected your PC" the first time - click **More info -> Run anyway** (the app isn't code-signed).
- **Minecraft mod:** on CurseForge (search "Build Blueprint"), or the `buildblueprint-x.y.z.jar` on the Releases page.
  Needs Fabric Loader and Fabric API.

## In Minecraft (default keys, change them in Options > Controls > Key Binds)
| Key | What it does |
|---|---|
| Page Up | Menu: saved builds, materials, settings |
| Arrow keys | Move the blueprint (Shift = 5 blocks, Ctrl + Up/Down = up/down) |
| Page Down | Turn it 90 degrees |
| Delete | Place it (then build layer by layer) / unlock |
| Home / End | Layer up / down |
| Insert | Copy a build from your world (press again to save) |
| Middle-click a ghost | Hold that block |

## Making a release (for the maintainer)
Push a tag like `v1.2.0`. GitHub Actions builds the installer and the mod, puts both on the Releases page and uploads
the mod to CurseForge (needs the `CURSEFORGE_TOKEN` secret and the `CURSEFORGE_ID` variable in the repo settings).

## License
All Rights Reserved - see LICENSE. You can use the official downloads; please do not re-upload or copy the code.

## Run from source
Install Node.js, then `npm install` and `npm start` (or double-click `start.bat`).
The mod is in `fabric-mod/` (`build-mod.bat`, or `gradlew build`).
