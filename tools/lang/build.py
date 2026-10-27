#!/usr/bin/env python3
"""Builds fabric-mod/.../lang/bb_<code>.txt from tools/lang/keys.txt (English, one per line) and tools/lang/<code>.txt
(the translations, same order, one per line). Checks that every {0}, {1}... is still there."""
import re, sys, pathlib
HERE = pathlib.Path(__file__).resolve().parent
OUT = HERE.parent.parent / 'fabric-mod/src/main/resources/assets/buildblueprint/lang'
keys = HERE.joinpath('keys.txt').read_text().split('\n')
if keys[-1] == '': keys.pop()
ok = True
for code in sys.argv[1:] or ['es', 'pt', 'de', 'fr', 'ru', 'zh']:
    f = HERE / (code + '.txt')
    if not f.exists(): continue
    tr = f.read_text().split('\n')
    if tr and tr[-1] == '': tr.pop()
    if len(tr) != len(keys): print(code, 'has', len(tr), 'lines, keys has', len(keys)); ok = False; continue
    lines = []
    for i, (en, t) in enumerate(zip(keys, tr), 1):
        a, b = sorted(re.findall(r'\{\d+\}', en)), sorted(re.findall(r'\{\d+\}', t))
        if a != b: print(code, i, 'placeholders differ:', en, '=>', t); ok = False
        lines.append(en + '\t' + t)
    OUT.joinpath('bb_%s.txt' % code).write_text('# Build Blueprint - %s\n' % code + '\n'.join(lines) + '\n')
    print(code, len(lines), 'texts')
sys.exit(0 if ok else 1)
