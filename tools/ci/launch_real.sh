#!/usr/bin/env bash
# Starts Minecraft the way a player's launcher does (real Mojang game, real loader, real Fabric API from Modrinth)
# with the built mod jar in the mods folder, runs the mod's screenshot tour and writes a PASS/FAIL result.
# usage: launch_real.sh <jar> <game version> <loader: fabric|forge|neoforge> <result dir> <name>
set -u
jar="$1"; gv="$2"; loader="$3"; out="$4"; name="$5"
mkdir -p "$out"; R="$out/$name.txt"
G="$PWD/game"; mkdir -p "$G/mods" "$HOME/BlockBlueprint/builds"
cp "$jar" "$G/mods/"
cp .github/uitest/* "$HOME/BlockBlueprint/builds/" 2>/dev/null || true

fail() { { echo "FAIL $name: $1"; echo; [ -f game.log ] && grep -iE "incompatible|missing|requires|exception|error|crash|buildblueprint|BBUITEST" game.log | grep -viE "realms|telemetry|narrator|authlib|YggdrasilUserApi|Failed to fetch user properties|profile" | head -60; echo; echo "--- end of log ---"; tail -n 60 game.log 2>/dev/null; } > "$R"; cat "$R" | head -80; exit 0; }

# the version string the launcher understands
case "$loader" in
  fabric)
    spec="fabric:$gv"
    # the newest Fabric API Modrinth lists for this exact game version (what a player downloads)
    url=$(curl -s "https://api.modrinth.com/v2/project/fabric-api/version?game_versions=%5B%22$gv%22%5D&loaders=%5B%22fabric%22%5D" -H "User-Agent: block-blueprint-ci" | python3 -c 'import json,sys; v=json.load(sys.stdin); print(next((f["url"] for f in v[0]["files"] if f["primary"]), v[0]["files"][0]["url"]) if v else "")')
    [ -z "$url" ] && fail "no Fabric API on Modrinth for $gv"
    curl -sL "$url" -o "$G/mods/$(basename "$url")"; echo "Fabric API: $(basename "$url")" ;;
  forge)
    F=$(curl -s --retry 3 https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml | grep -o "<version>$gv-[^<]*</version>" | sed "s/<[^>]*>//g" | sort -V | tail -1)
    [ -z "$F" ] && fail "no Forge for $gv"; spec="forge::$F" ;;
  neoforge)
    case "$gv" in 1.*) P=$(echo "$gv" | sed 's/^1\.//'); case "$P" in *.*) ;; *) P="$P.0" ;; esac ;;
                  *) P="$gv"; case "$P" in *.*.*) ;; *) P="$P.0" ;; esac ;; esac
    F=$(curl -s https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml | grep -oE "<version>$P\.[0-9]+(-beta)?</version>" | sed 's/<[^>]*>//g' | sort -V | tail -1)
    [ -z "$F" ] && fail "no NeoForge for $gv"; spec="neoforge::$F" ;;
esac
echo "Launching $spec with $(basename "$jar")"; ls "$G/mods"

export LIBGL_ALWAYS_SOFTWARE=1
# install first (downloads the game, libraries, Java), then start
ok=0; for i in 1 2 3; do portablemc --main-dir "$G" start --dry "$spec" > install.log 2>&1 && { ok=1; break; }; sleep 20; done
[ "$ok" = 1 ] || { cp install.log game.log; fail "the launcher could not install $spec"; }
(timeout 900 xvfb-run -n 99 -s "-screen 0 1280x720x24 +extension GLX +render -noreset -ac" \
   portablemc --main-dir "$G" start "$spec" --jvm-arg=-Xmx3G,-Dbuildblueprint.uitest=1 --resolution 1280x720 > game.log 2>&1 &)

started=0
for i in $(seq 1 170); do
  sleep 5
  if grep -qE "BBUITEST waiting" game.log; then started=1; break; fi
  grep -qE "Incompatible mods found|Missing or unsupported mandatory dependencies|requires .* which is missing|Crash report saved|---- Minecraft Crash Report ----|Mod Loading has failed|LoadingFailedException|ModLoadingException" game.log && break
  pgrep -f "portablemc|java" > /dev/null || break
done
if [ "$started" = 1 ]; then
  for j in $(seq 1 120); do
    sleep 2
    for n in $(grep -o "BBUITEST shot [a-z-]*" game.log | awk '{print $3}'); do
      f="$out/$name-$n.png"
      if [ ! -f "$f" ]; then
        N=$(ls -t "$G"/screenshots/*.png 2>/dev/null | head -1)
        if [ -n "$N" ]; then mv "$N" "$f"; else DISPLAY=:99 import -window root "$f" 2>/dev/null || true; fi
      fi
    done
    grep -q "BBUITEST done" game.log && break
    grep -qE "Crash report saved|---- Minecraft Crash Report ----" game.log && break
  done
fi
pkill -f java || true; sleep 3

if grep -qE "Crash report saved|---- Minecraft Crash Report ----" game.log; then
  cat "$G"/crash-reports/*.txt >> game.log 2>/dev/null; fail "the game crashed"
fi
[ "$started" = 1 ] || fail "the game did not reach the menu with Build Blueprint loaded"
grep -q "BBUITEST done" game.log || fail "the mod's screens did not all open"
grep -E "BBUITEST.*(failed|Exception)|\[Build Blueprint\] problem" game.log | head -5 > errs.txt
if [ -s errs.txt ]; then fail "the mod reported errors: $(head -1 errs.txt)"; fi
{ echo "PASS $name"; grep -E "BBUITEST" game.log | head -20; } > "$R"; cat "$R"
