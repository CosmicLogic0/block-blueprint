#!/usr/bin/env bash
# Starts Minecraft the way a player's launcher does (real Mojang game, real loader, real Fabric API from Modrinth)
# with the built mod jar in the mods folder, runs the mod's screenshot tour and writes a PASS/FAIL result.
# It also starts a plain Minecraft server on this machine and the game joins it, so the mod is tested IN a world:
# ghost blocks, placing, the corner list, the build menu with real counts and a copy box (UiTest "world" tour).
# usage: launch_real.sh <jar> <game version> <loader: fabric|forge|neoforge> <result dir> <name>
set -u
jar="$1"; gv="$2"; loader="$3"; out="$4"; name="$5"
mkdir -p "$out"; R="$out/$name.txt"
G="$PWD/game"; mkdir -p "$G/mods" "$HOME/BlockBlueprint/builds"
cp "$jar" "$G/mods/"
cp "${BUILDS_DIR:-.github/uitest}"/* "$HOME/BlockBlueprint/builds/" 2>/dev/null || true
mode="${UITEST_MODE:-world}"   # "showcase": the listing picture tour (the player is op, for auto-build and /tp)

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
# extra mods for some runs (.github/realtest-extra.txt: "<run name> <modrinth slug>"), e.g. a mod that adds blocks
if [ -f .github/realtest-extra.txt ]; then
  for slug in $(awk -v n="$name" '$1==n {print $2}' .github/realtest-extra.txt); do
    u=$(curl -s "https://api.modrinth.com/v2/project/$slug/version?game_versions=%5B%22$gv%22%5D&loaders=%5B%22$loader%22%5D" -H "User-Agent: block-blueprint-ci" | python3 -c 'import json,sys; v=json.load(sys.stdin); print(next((f["url"] for f in v[0]["files"] if f["primary"]), v[0]["files"][0]["url"]) if v else "")')
    if [ -n "$u" ]; then curl -sL "$u" -o "$G/mods/$(basename "$u")"; echo "extra mod: $(basename "$u")"; else echo "extra mod $slug: none for $gv"; fi
  done
fi
echo "Launching $spec with $(basename "$jar")"; ls "$G/mods"

export LIBGL_ALWAYS_SOFTWARE=1
# install first (downloads the game, libraries, Java), then start
ok=0; for i in 1 2 3; do portablemc --main-dir "$G" start --dry --jvm-policy mojang-then-system "$spec" > install.log 2>&1 && { ok=1; break; }; sleep 20; done
[ "$ok" = 1 ] || { cp install.log game.log; fail "the launcher could not install $spec"; }

# --- a local server (vanilla, offline, creative, flat) for the game to join ---
S="$PWD/server"; mkdir -p "$S"
surl=$(curl -s https://piston-meta.mojang.com/mc/game/version_manifest_v2.json | python3 -c "import json,sys,urllib.request; m=json.load(sys.stdin); v=[x for x in m['versions'] if x['id']=='$gv'][0]; print(json.load(urllib.request.urlopen(v['url']))['downloads']['server']['url'])")
curl -sL "$surl" -o "$S/server.jar"
# SERVER_KIND=paper: a Paper server with the Build Blueprint plugin; fabric: a Fabric server with the mod (+ Fabric API)
case "${SERVER_KIND:-}" in
  paper)
    pj=$(curl -s -H "User-Agent: block-blueprint-ci" "https://fill.papermc.io/v3/projects/paper/versions/$gv/builds/latest" | python3 -c 'import json,sys; d=json.load(sys.stdin); print(d["downloads"]["server:default"]["url"])' 2>/dev/null)
    if [ -z "$pj" ]; then
      b=$(curl -s "https://api.papermc.io/v2/projects/paper/versions/$gv/builds" | python3 -c 'import json,sys; d=json.load(sys.stdin)["builds"][-1]; print(str(d["build"])+" "+d["downloads"]["application"]["name"])' 2>/dev/null)
      [ -n "$b" ] && pj="https://api.papermc.io/v2/projects/paper/versions/$gv/builds/${b%% *}/downloads/${b#* }"
    fi
    [ -z "$pj" ] && fail "no Paper server for $gv"
    curl -sL "$pj" -o "$S/server.jar"; mkdir -p "$S/plugins"; cp plugin/*.jar "$S/plugins/" || fail "the server plugin did not build: $(grep -E 'error:|What went wrong|\.java:[0-9]+' -A2 plugin/build.log 2>/dev/null | head -10 | tr '\n' ' ')"
    echo "Paper: $pj" ;;
  fabric)
    FL=$(curl -s "https://meta.fabricmc.net/v2/versions/loader/$gv" | python3 -c 'import json,sys; print(json.load(sys.stdin)[0]["loader"]["version"])')
    FI=$(curl -s "https://meta.fabricmc.net/v2/versions/installer" | python3 -c 'import json,sys; print(json.load(sys.stdin)[0]["version"])')
    curl -sL "https://meta.fabricmc.net/v2/versions/loader/$gv/$FL/$FI/server/jar" -o "$S/server.jar"
    mkdir -p "$S/mods"; cp "$G"/mods/*.jar "$S/mods/"; echo "Fabric server: loader $FL" ;;
  modserver)   # a Forge / NeoForge server (same loader version as the game) with the mod in it
    case "$loader" in
      neoforge) iu="https://maven.neoforged.net/releases/net/neoforged/neoforge/$F/neoforge-$F-installer.jar" ;;
      forge) iu="https://maven.minecraftforge.net/net/minecraftforge/forge/$F/forge-$F-installer.jar" ;;
      *) fail "no mod server for $loader" ;;
    esac
    curl -sL "$iu" -o "$S/installer.jar" ;;
esac
minor=$(echo "$gv" | cut -d. -f2); major=$(echo "$gv" | cut -d. -f1); patch=$(echo "$gv" | cut -d. -f3); patch=${patch:-0}
if [ "$major" != 1 ]; then SJ="${JAVA_HOME_25_X64:-}"; lt="minecraft\\:flat"
elif [ "$minor" -le 16 ]; then SJ="${JAVA_HOME_8_X64:-}"; lt=flat
elif [ "$minor" -le 18 ]; then SJ="${JAVA_HOME_17_X64:-}"; lt=flat
elif [ "$minor" -lt 20 ] || { [ "$minor" = 20 ] && [ "$patch" -le 4 ]; }; then SJ="${JAVA_HOME_17_X64:-}"; lt="minecraft\\:flat"
else SJ="${JAVA_HOME_21_X64:-}"; lt="minecraft\\:flat"; fi
echo "eula=true" > "$S/eula.txt"
printf 'online-mode=false\nwhite-list=false\nenforce-whitelist=false\nenforce-secure-profile=false\ngamemode=creative\nlevel-type=%s\ngenerate-structures=false\ndifficulty=peaceful\nspawn-monsters=false\nspawn-animals=false\nspawn-protection=0\nview-distance=6\nmax-tick-time=-1\nmotd=Build Blueprint test\n' "$lt" > "$S/server.properties"
if [ "$mode" = showcase ]; then
  python3 -c "import hashlib,uuid,json; h=bytearray(hashlib.md5(b'OfflinePlayer:Builder').digest()); h[6]=h[6]&0x0f|0x30; h[8]=h[8]&0x3f|0x80; print(json.dumps([{'uuid':str(uuid.UUID(bytes=bytes(h))),'name':'Builder','level':4,'bypassesPlayerLimit':False}]))" > "$S/ops.json"
  printf 'pvp=false\n' >> "$S/server.properties"
fi
if [ "${SERVER_KIND:-}" = modserver ]; then
  (cd "$S" && "$SJ/bin/java" -jar installer.jar --installServer > install-server.log 2>&1) || { cp "$S/install-server.log" game.log; fail "the $loader server did not install"; }
  mkdir -p "$S/mods"; cp "$jar" "$S/mods/"
  echo "-Xmx2G" > "$S/user_jvm_args.txt"
  if [ -f "$S/run.sh" ]; then (cd "$S" && PATH="$SJ/bin:$PATH" timeout 1200 bash run.sh nogui > server.log 2>&1 &)
  else fj=$(cd "$S" && ls forge-*.jar 2>/dev/null | grep -v installer | head -1); (cd "$S" && timeout 1200 "$SJ/bin/java" -Xmx2G -jar "$fj" nogui > server.log 2>&1 &); fi   # Forge 1.16 and older: no run script
else
  (cd "$S" && timeout 1200 "$SJ/bin/java" -Xmx2G -jar server.jar nogui > server.log 2>&1 &)
fi
for i in $(seq 1 90); do sleep 4; grep -q 'Done (' "$S/server.log" && break; done
grep -q 'Done (' "$S/server.log" || { cp "$S/server.log" game.log; fail "the test server for $gv did not start"; }
echo "server up"

# game options: no first-launch screens, keep running without focus
printf 'onboardAccessibility:false\nskipMultiplayerWarning:true\njoinedFirstServer:true\npauseOnLostFocus:false\ntutorialStep:none\nrenderDistance:6\nguiScale:2\nnarrator:0\nsoundCategory_master:0.0\n' > "$G/options.txt"
[ "$mode" = showcase ] && printf 'chatVisibility:2\n' >> "$G/options.txt"   # the picture: no chat lines over the build
(timeout 900 xvfb-run -n 99 -s "-screen 0 1280x720x24 +extension GLX +render -noreset -ac" \
   portablemc --main-dir "$G" start --jvm-policy mojang-then-system -u Builder "$spec" --jvm-arg=-Xmx3G,-Dbuildblueprint.uitest=$mode,-Dbuildblueprint.uitest.server=127.0.0.1:25565 --resolution 1280x720 > game.log 2>&1 &)

started=0
for i in $(seq 1 170); do
  sleep 5
  if grep -qE "BBUITEST in world" game.log; then started=1; break; fi
  grep -qE "Incompatible mods found|Missing or unsupported mandatory dependencies|requires .* which is missing|Crash report saved|---- Minecraft Crash Report ----|Mod Loading has failed|LoadingFailedException|ModLoadingException" game.log && break
  pgrep -f "portablemc|java" > /dev/null || break
done
if [ "$started" = 1 ]; then
  for j in $(seq 1 200); do
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
cp "$S/server.log" "$out/$name-server.log" 2>/dev/null || true

if grep -qE "Crash report saved|---- Minecraft Crash Report ----" game.log; then
  cat "$G"/crash-reports/*.txt >> game.log 2>/dev/null; fail "the game crashed"
fi
L=$(grep -m1 -oE "(ModLoadingException|Missing or unsupported mandatory dependencies|Incompatible mods found|requires .* which is missing|needs language provider)[^\r]*" game.log)
[ -n "$L" ] && [ "$started" != 1 ] && fail "the loader refused the mod: $L"
grep -m1 -q "BBUITEST connect failed" game.log && fail "$(grep -m1 -o 'BBUITEST connect failed.*' game.log)"
if [ "$started" != 1 ]; then
  grep -qiE "buildblueprint" game.log && fail "the game started but did not get into the test world"
  fail "the game did not start with Build Blueprint loaded"
fi
grep -q "BBUITEST done" game.log || fail "the mod's screens did not all open"
grep -E "BBUITEST.*(failed|Exception)|\[Build Blueprint\] problem" game.log | head -5 > errs.txt
if [ -n "${SERVER_KIND:-}" ]; then   # the in-game invite must have gone to the server and back
  grep -q "BBUITEST sync: available=true" game.log || fail "the mod didn't find the Build Blueprint server part on the $SERVER_KIND server: $(grep -o 'BBUITEST sync:.*' game.log | head -1)"
  grep -q "BBUITEST sync check: invites 1" game.log || fail "the invite didn't come back from the $SERVER_KIND server: $(grep -o 'BBUITEST sync check:.*' game.log | head -1)"
fi
if [ -s errs.txt ]; then fail "the mod reported errors: $(head -1 errs.txt)"; fi
grep -q "BBUITEST tour: shown true on HelpScreen" game.log || fail "the first-time tour did not open: $(grep -o 'BBUITEST tour:.*' game.log | head -1)"
grep -q "BBUITEST picture: [1-9]" game.log || fail "build from a picture made no blocks: $(grep -o 'BBUITEST picture:.*' game.log | head -1)"
grep -q "BBUITEST loaded [1-9]" game.log || fail "the test build did not load in the world"
{ echo "PASS $name"; grep -E "BBUITEST" game.log | head -20; } > "$R"; cat "$R"
