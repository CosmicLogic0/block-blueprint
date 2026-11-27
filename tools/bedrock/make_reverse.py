#!/usr/bin/env python3
"""Bedrock -> Java block table for reading Bedrock .mcstructure files in the mod, made by turning around
renderer/bedrock-blocks.json (made by make_map.py from Minecraft's report and GeyserMC's MIT mappings).
usage: make_reverse.py renderer/bedrock-blocks.json fabric-mod/src/main/resources/assets/buildblueprint/tables/bedrock-blocks.txt.gz
Lines: "minecraft:<bedrock name>|key=value;key=value<TAB>java state", sorted keys, true/false as 1/0;
and "minecraft:<bedrock name>|*<TAB>java state" for when the exact states aren't known."""
import json, sys, gzip

src = json.load(open(sys.argv[1]))

def val(v):
    if isinstance(v, bool): return '1' if v else '0'
    return str(v)

exact, loose = {}, {}
for jid, e in src.items():
    props, default = e['p'], e['d']
    # the block's usual state first, then not-waterlogged ones, so a Bedrock state picks the plainest Java state
    order = sorted(e['m'].items(), key=lambda kv: (kv[0] != default, 'waterlogged' in props and kv[0].split(',')[props.index('waterlogged')] == 'true'))
    for vals, m in order:
        bname = 'minecraft:' + (m[0] or jid[10:]) if not (m[0] or '').startswith('minecraft:') else m[0]
        states = m[1] if len(m) > 1 else {}
        key = bname + '|' + ';'.join(f'{k}={val(states[k])}' for k in sorted(states))
        java = jid + ('[' + ','.join(f'{p}={v}' for p, v in zip(props, vals.split(','))) + ']' if props else '')
        exact.setdefault(key, java)
        if vals == default: loose.setdefault(bname + '|*', java)
        loose.setdefault(bname + '|*', java)
# kept in preference order (a block's usual state first): the mod picks the first line that fits when a file has fewer states
lines = [f'{k}\t{v}' for k, v in exact.items()] + [f'{k}\t{v}' for k, v in loose.items()]
head = '# Bedrock -> Java blocks. Made from GeyserMC mappings (MIT, github.com/GeyserMC/mappings) and Minecraft\'s block report.\n'
gzip.open(sys.argv[2], 'wb', 9).write((head + '\n'.join(lines) + '\n').encode())
print(len(exact), 'exact,', len(loose), 'bedrock names')
