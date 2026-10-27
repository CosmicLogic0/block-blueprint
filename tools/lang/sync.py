#!/usr/bin/env python3
"""After `python3 tools/i18n.py extract`: puts new texts into tools/lang/keys.txt (in the same order as bb_en.txt) and
keeps every language file lined up with it. New texts need a translation in tools/lang/new.json:
{"English text": {"es": "...", "pt": "...", ...}}. Texts that are gone from the mod are dropped."""
import json, pathlib, sys
L = pathlib.Path(__file__).resolve().parent
EN = L.parent.parent / 'fabric-mod/src/main/resources/assets/buildblueprint/lang/bb_en.txt'
LANGS = ['es', 'pt', 'de', 'fr', 'ru', 'zh']
old = L.joinpath('keys.txt').read_text().rstrip('\n').split('\n')
en = [l for l in EN.read_text().split('\n') if l and not l.startswith('#')]
new = json.loads(L.joinpath('new.json').read_text()) if L.joinpath('new.json').exists() else {}
keys = [k for k in en if k in set(old) or k in new]
missing = [k for k in new if k not in en]
if missing: print('not in the mod (check the text):', missing); sys.exit(1)
for code in LANGS:
    tr = L.joinpath(code + '.txt').read_text().rstrip('\n').split('\n')
    m = dict(zip(old, tr))
    L.joinpath(code + '.txt').write_text('\n'.join(m[k] if k in m else new[k][code] for k in keys) + '\n')
L.joinpath('keys.txt').write_text('\n'.join(keys) + '\n')
print(len(keys), 'texts;', len([k for k in old if k not in keys]), 'dropped;', len([k for k in keys if k not in old]), 'added')
