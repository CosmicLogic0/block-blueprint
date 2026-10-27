#!/usr/bin/env python3
"""
Translations for the mod (see Tr.java).
  python3 tools/i18n.py wrap      - puts Tr.t(...) around the text the screens draw (safe to run again)
  python3 tools/i18n.py extract   - writes fabric-mod/src/main/resources/assets/buildblueprint/lang/bb_en.txt: every
                                    text (or text template like "Layer {0} of {1}") the mod can show
  python3 tools/i18n.py check     - which texts each language file is still missing
"""
import re, sys, pathlib
ROOT = pathlib.Path(__file__).resolve().parent.parent
MENU = ROOT / 'fabric-mod/src/menu/java/dev/buildblueprint/client'
MAIN = ROOT / 'fabric-mod/src/main/java/dev/buildblueprint/client'
LANG = ROOT / 'fabric-mod/src/main/resources/assets/buildblueprint/lang'
LANGS = ['es', 'pt', 'de', 'fr', 'ru', 'zh']
SKIP = {'UiTest.java', 'ChestHighlight.java'}

def files():
    out = [p for p in sorted(MENU.glob('*.java')) if p.name not in SKIP]
    out += [MAIN / n for n in ['Chat.java', 'Materials.java', 'Mistakes.java', 'Layers.java', 'Stats.java', 'Swapper.java', 'Copier.java',
                               'BuildBlueprintClient.java', 'AutoBuild.java', 'UpdateCheck.java', 'Coop.java', 'Hint.java', 'SavedBuilds.java', 'Chests.java', 'GhostPick.java', 'SessionStore.java']]
    out.append(ROOT / 'fabric-mod/src/main/java/dev/buildblueprint/core/Orient.java')
    return [p for p in out if p.exists()]

# ---------------------------------------------------------------- a tiny Java expression scanner
STR = re.compile(r'"(?:[^"\\]|\\.)*"')

def skip_ws(s, i):
    while i < len(s) and s[i] in ' \t\r\n': i += 1
    return i

def match_paren(s, i):
    """s[i] is '(' -> index after the matching ')'."""
    depth = 0
    while i < len(s):
        c = s[i]
        if c == '"':
            m = STR.match(s, i); i = m.end() if m else i + 1; continue
        if c == "'":
            j = i + 1
            while s[j] != "'": j += 2 if s[j] == '\\' else 1
            i = j + 1; continue
        if c in '([{': depth += 1
        elif c in ')]}':
            depth -= 1
            if depth == 0: return i + 1
        i += 1
    return len(s)

def arg_end(s, i):
    """From i (start of an argument), the index of the ',' or ')' that ends it."""
    depth = 0
    while i < len(s):
        c = s[i]
        if c == '"':
            m = STR.match(s, i); i = m.end() if m else i + 1; continue
        if c == "'":
            j = i + 1
            while s[j] != "'": j += 2 if s[j] == '\\' else 1
            i = j + 1; continue
        if c in '([{': depth += 1
        elif c in ')]}':
            if depth == 0: return i
            depth -= 1
        elif c == ',' and depth == 0: return i
        elif c == '-' and depth == 0 and s[i:i + 2] == '->': return -1   # a lambda, leave it
        i += 1
    return -1

CALLS = ['Component.literal(', 'g.text(font, ', 'font.width(']

def wrap():
    for p in files():
        if p.parent != MENU: continue
        s = p.read_text(); out = []; i = 0; n = 0
        while True:
            hits = [(s.find(c, i), c) for c in CALLS]
            hits = [h for h in hits if h[0] >= 0]
            if not hits: out.append(s[i:]); break
            pos, c = min(hits)
            a = pos + len(c)
            out.append(s[i:a])
            e = arg_end(s, a)
            arg = s[a:e] if e > 0 else ''
            if e > 0 and not arg.strip().startswith('Tr.t(') and arg.strip():
                out.append('Tr.t(' + arg + ')'); n += 1; i = e
            else: i = a
        s2 = ''.join(out)
        if s2 != s: p.write_text(s2); print(p.name, n, 'wrapped')

# ---------------------------------------------------------------- texts and templates
def operand_end(s, i):
    """End of one operand of a '+' chain starting at i (literal, name, call chain, (...) or number)."""
    i = skip_ws(s, i)
    if i >= len(s): return i
    c = s[i]
    if c == '"':
        m = STR.match(s, i)
        return m.end() if m else i
    if c == '(':
        j = match_paren(s, i)
        return chain(s, j)
    if c == "'":
        j = i + 1
        while s[j] != "'": j += 2 if s[j] == '\\' else 1
        return j + 1
    m = re.match(r'(?:new\s+)?[A-Za-z_][\w.]*', s[i:])
    if m: return chain(s, i + m.end())
    m = re.match(r'-?[\d.]+[LlFfDd]?', s[i:])
    if m: return i + m.end()
    return i

def chain(s, j):
    while j < len(s):
        if s[j] == '(' : j = match_paren(s, j); continue
        if s[j] == '[' : j = match_paren(s, j); continue
        m = re.match(r'\.[A-Za-z_]\w*', s[j:])
        if m: j += m.end(); continue
        break
    return j

def unescape(lit):
    return bytes(lit[1:-1], 'utf-8').decode('unicode_escape').encode('latin-1').decode('utf-8')

def templates_in(s):
    """Texts the code can show: each '+' chain that has a string in it ("Layer " + n + " of " + top -> "Layer {0} of {1}"),
    scanned from every place an expression can start."""
    found = []
    masked = STR.sub(lambda m: '"' + 'x' * (len(m.group(0)) - 2) + '"', s)          # no starts inside strings
    masked = re.sub(r"'(?:[^'\\]|\\.)'", lambda m: "'" + 'x' * (len(m.group(0)) - 2) + "'", masked)
    starts = [m.end() for m in re.finditer(r'(?:[(,=?:{]|->|\breturn\b)', masked)]
    seen = set()
    for st in starts:
        i = skip_ws(s, st)
        if i in seen or i >= len(s): continue
        seen.add(i)
        parts = []
        j = i
        while True:
            e = operand_end(s, j)
            if e <= skip_ws(s, j): break
            while True:                                            # a * 90, n / 2, i % 4 belong to the same part
                k = skip_ws(s, e)
                if k < len(s) and s[k] in '*/%' and s[k + 1:k + 2] not in ('=', '/', '*'):
                    e2 = operand_end(s, k + 1)
                    if e2 > skip_ws(s, k + 1): e = e2; continue
                break
            op = s[skip_ws(s, j):e].strip()
            parts.append(('lit', unescape(op)) if STR.fullmatch(op) else ('var', op))
            k = skip_ws(s, e)
            if k < len(s) and s[k] == '+' and s[k + 1:k + 2] not in ('+', '='): j = k + 1
            else: break
        if not any(kind == 'lit' for kind, v in parts): continue
        if len(parts) == 1: found.append(parts[0][1]); continue
        t = ''; n = 0
        for kind, v in parts:
            if kind == 'lit': t += v
            else: t += '{%d}' % n; n += 1
        found.append(t)
    return found

def keep(t):
    if len(t.strip()) < 2 or not re.search(r'[A-Za-z]', t): return False
    if re.fullmatch(r'[a-z0-9_.:/\-\[\]=,{}%]+', t): return False          # ids, keys, paths
    if t.startswith(('minecraft:', 'http', 'BBUITEST', 'BB1:', 'BBCOOP', '/')) or '\\' in t: return False
    if re.search(r'[\[\]\\^$*+?|]{2,}', t): return False                    # regexes
    if t in ('Block{', '}'): return False
    return True

def no_comments(s):
    """The code without // and /* */ comments (but "https://..." inside strings stays)."""
    out = []; i = 0
    while i < len(s):
        c = s[i]
        if c == '"':
            m = STR.match(s, i); e = m.end() if m else i + 1; out.append(s[i:e]); i = e; continue
        if c == "'":
            m = re.match(r"'(?:[^'\\]|\\.)*'", s[i:]); e = i + (m.end() if m else 1); out.append(s[i:e]); i = e; continue
        if s.startswith('//', i):
            j = s.find('\n', i); i = len(s) if j < 0 else j; continue
        if s.startswith('/*', i):
            j = s.find('*/', i + 2); i = len(s) if j < 0 else j + 2; out.append(' '); continue
        out.append(c); i += 1
    return ''.join(out)

def extract():
    keys = []
    for p in files():
        s = p.read_text()
        s = no_comments(s)
        for t in templates_in(s):
            for u in (t.split(':', 2)[1:] if t.startswith('key:') else [t]):   # help pages: "key:Insert:what it does"
                if keep(u) and u not in keys: keys.append(u)
    # single lowercase words that get filled into other texts (directions, sides)
    for w in ['north', 'south', 'east', 'west', 'north-east', 'north-west', 'south-east', 'south-west', 'left', 'right', 'kept', 'left out', 'just me', 'this layer', 'whole build', 'auto', 'top right', 'top left',
              # kinds of free builds (renderer/templates/mod-index.tsv)
              'Community Builds', 'Houses', 'Towers', 'Ships', 'Ruins & Temples', 'Castles & Forts', 'Village Buildings', 'Decor', 'Camps & Outposts']:
        if w not in keys: keys.append(w)
    LANG.mkdir(parents=True, exist_ok=True)
    (LANG / 'bb_en.txt').write_text('# Every text Build Blueprint shows. Translations: bb_<language>.txt, "English<TAB>translation".\n' + '\n'.join(keys) + '\n')
    print(len(keys), 'texts')

def check():
    en = [l for l in (LANG / 'bb_en.txt').read_text().split('\n') if l and not l.startswith('#')]
    for l in LANGS:
        f = LANG / ('bb_%s.txt' % l)
        have = set()
        if f.exists():
            for line in f.read_text().split('\n'):
                if '\t' in line: have.add(line.split('\t', 1)[0])
        miss = [k for k in en if k not in have]
        print(l, len(en) - len(miss), '/', len(en), 'translated')

if __name__ == '__main__':
    {'wrap': wrap, 'extract': extract, 'check': check}[sys.argv[1]]()
