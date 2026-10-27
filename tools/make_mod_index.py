#!/usr/bin/env python3
"""
Writes renderer/templates/mod-index.tsv: the open-license templates the mod can download in game (Saved builds >
Templates). One line each: id, file name, name, category, size (WxHxD), blocks, creator, license, source link.
Run after the templates change: python3 tools/make_mod_index.py
"""
import json, pathlib
ROOT = pathlib.Path(__file__).resolve().parent.parent
T = ROOT / 'renderer/templates'
rows = []
for x in json.load(open(T / 'index.json')):
    if not x.get('schem'): continue
    f = x['id'] + '.nbt'
    if not (T / 'schem' / f).exists(): continue
    credit = x.get('credit', '').rsplit(' (', 1)[0]
    name = x['name']
    if not any(c.isalpha() for c in name):   # "0", "1": use the project's name too
        proj = x.get('source', '').rstrip('/').rsplit('/', 1)[-1].replace('-', ' ').title()
        name = (proj + ' ' + name).strip()
    clean = lambda v: str(v).replace('\t', ' ').replace('\n', ' ')
    rows.append('\t'.join(clean(v) for v in [x['id'], f, name, x.get('cat', ''), 'x'.join(str(n) for n in x.get('size', [])),
                                             x.get('blocks', 0), credit, x.get('license', ''), x.get('source', '')]))
rows.sort(key=lambda r: r.split('\t')[2].lower())
(T / 'mod-index.tsv').write_text('\n'.join(rows) + '\n')
print(len(rows), 'templates')
