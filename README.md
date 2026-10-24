# Block Blueprint

Design Minecraft builds in 3D on your PC (or phone), then build them in your world with the **Build Blueprint** mod:
see-through ghost blocks, a materials list, layer-by-layer building, block swapping and copying builds from your world.
Works on **Minecraft Java 1.17 to 26.3** with Fabric, plus Forge and NeoForge.

## Download
- **Windows app:** go to [Releases](https://github.com/CosmicLogic0/block-blueprint/releases/latest) and download
  `Block-Blueprint-Setup-x.y.z.exe`. The app updates itself when a new version comes out.
  Windows may say "Windows protected your PC" the first time - click **More info -> Run anyway** (the app isn't code-signed).
- **Minecraft mod:** on CurseForge: https://www.curseforge.com/minecraft/mc-mods/build-blueprint
  (Fabric needs Fabric API; Forge and NeoForge need nothing else).

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
Raise the version in `package.json` and push: GitHub Actions builds the app installer and puts it on the Releases page.
To also publish the mod, list the versions in `.github/curseforge-upload.txt` in the same push (only new versions or real changes -
CurseForge rejects unchanged re-uploads) and set `mod-version.txt` to the new mod version (the mod uses it for its update notice).

## License
All Rights Reserved - see LICENSE. You can use the official downloads; please do not re-upload or copy the code.

## Run from source
Install Node.js, then `npm install` and `npm start` (or double-click `start.bat`).
The mod is in `fabric-mod/` (`build-mod.bat`, or `gradlew build`).
