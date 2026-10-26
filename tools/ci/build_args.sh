#!/usr/bin/env bash
# Prints the Gradle arguments that build the mod for one release row (same logic as release.yml).
# usage: build_args.sh <mc> <loader or ""> <kind or ""> <java target> <fabric-api mc or ""> [extra gradle args]
mc="$1"; loader="$2"; kind="$3"; target="$4"; fapi="${5:-$1}"; extra="$6"
if [ "$mc" = "26.3" ] && [ -z "$loader" ]; then echo ""; exit 0; fi     # 26.3 Fabric uses gradle.properties
if [ "$kind" = "neo26" ]; then
  P="$mc"; case "$P" in *.*.*) ;; *) P="$P.0" ;; esac
  F=$(curl -s https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml | grep -oE "<version>$P\.[0-9]+(-beta)?</version>" | sed 's/<[^>]*>//g' | sort -V | tail -1)
  echo "-Pminecraft_version=$mc -Pneo_version=$F"; exit 0
fi
if [ "$kind" = "forge26" ]; then
  for i in 1 2 3 4 5; do F=$(curl -s --retry 3 https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml | grep -o "<version>$mc-[^<]*</version>" | sed "s/<[^>]*>//g; s/^$mc-//" | tail -1); [ -n "$F" ] && break; sleep 10; done
  echo "-Pminecraft_version=$mc -Pforge_version=$F"; exit 0
fi
LOOM=$(curl -s https://maven.architectury.dev/dev/architectury/loom/dev.architectury.loom.gradle.plugin/maven-metadata.xml | grep -o "<latest>[^<]*</latest>" | sed 's/<[^>]*>//g')
if [ "$loader" = "forge" ]; then
  F=$(curl -s https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml | grep -o "<version>$mc-[^<]*</version>" | sed "s/<[^>]*>//g; s/^$mc-//" | tail -1)
  echo "-Pminecraft_version=$mc -Pforge_version=$F -Parch_loom_version=$LOOM -Pjava_version=$target $extra"; exit 0
fi
if [ "$loader" = "neoforge" ]; then
  P=$(echo "$mc" | sed 's/^1\.//'); case "$P" in *.*) ;; *) P="$P.0" ;; esac
  F=$(curl -s https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml | grep -oE "<version>$P\.[0-9]+(-beta)?</version>" | sed 's/<[^>]*>//g' | sort -V | tail -1)
  echo "-Ploom.platform=neoforge -Pminecraft_version=$mc -Pforge_version=$F -Parch_loom_version=$LOOM -Pjava_version=$target $extra"; exit 0
fi
V=$(curl -s https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml | grep -oE "<version>[^<]*\+(build\.[0-9]+-)?$fapi</version>" | sed 's/<[^>]*>//g' | tail -1)
if [ -z "$V" ]; then echo "No Fabric API found for $fapi" >&2; exit 1; fi
echo "-Pminecraft_version=$mc -Pfabric_api_version=$V -Pjava_version=$target $extra"
