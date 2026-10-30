#!/usr/bin/env python3
"""Java -> Bedrock block table for the app's "Save for Bedrock" (.mcstructure).
usage: make_map.py <Java reports blocks.json> <GeyserMC mappings blocks.nbt> <out.json>
The Java report gives every block state and its id; GeyserMC's table (MIT, github.com/GeyserMC/mappings) says what each
Java state id is on Bedrock. Out: {"minecraft:oak_stairs": {"p": ["facing", "half", ...], "d": "north,bottom,...",
"m": {"north,bottom,...": ["oak_stairs", {"weirdo_direction": 3, ...}]}}, ...}. Bedrock name "" = same as Java."""
import json, sys, nbtlib

rep = json.load(open(sys.argv[1]))
geyser = nbtlib.load(sys.argv[2])['bedrock_mappings']

def plain(v):
    if isinstance(v, nbtlib.tag.String): return str(v)
    if isinstance(v, nbtlib.tag.Byte): return bool(int(v))
    return int(v)

out = {}
for jid, b in rep.items():
    props = list((b.get('properties') or {}).keys())
    e = {'p': props, 'm': {}}
    for st in b['states']:
        vals = ','.join(str(st.get('properties', {})[k]) for k in props)
        if st.get('default'): e['d'] = vals
        g = geyser[st['id']]
        name = str(g['bedrock_identifier']) if 'bedrock_identifier' in g else ''
        if name.startswith('minecraft:'): name = name[10:]
        if name == jid[10:]: name = ''
        states = {str(k): plain(v) for k, v in g['state'].items()} if 'state' in g else {}
        e['m'][vals] = [name, states] if states else [name]
    if 'd' not in e: e['d'] = next(iter(e['m']))
    out[jid] = e
json.dump(out, open(sys.argv[3], 'w'), separators=(',', ':'))
print(len(out), 'blocks,', sum(len(e['m']) for e in out.values()), 'states')
