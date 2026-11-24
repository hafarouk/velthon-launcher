"""Static checks over the Java sources (no phone needed):
 - every L.t("...") word has a translation row
 - each translation row has all 6 languages and no row repeats a word with a different translation
 - every Settings choice() has as many names as values
"""
import re, sys, glob, os
src = os.path.join(os.path.dirname(__file__), '..', '..', 'src', 'com', 'velthon', 'launcher')
L = open(os.path.join(src, 'L.java'), encoding='utf8').read()
rows = re.findall(r'^\s*\{("(?:[^"\\]|\\.)*"(?:\s*,\s*"(?:[^"\\]|\\.)*")*)\},?\s*$', L, re.M)
table = {}; bad = 0
for r in rows:
    cells = re.findall(r'"((?:[^"\\]|\\.)*)"', r)
    if len(cells) != 6: print('ROW NOT 6 LANGUAGES:', cells[:2]); bad += 1; continue
    if cells[0] in table and table[cells[0]] != cells: print('CONFLICTING ROW:', cells[0]); bad += 1
    table[cells[0]] = cells
missing = set()
for f in glob.glob(os.path.join(src, '*.java')):
    if f.endswith('L.java'): continue
    for w in re.findall(r'L\.t\("((?:[^"\\]|\\.)*)"\)', open(f, encoding='utf8').read()):
        if w not in table: missing.add((os.path.basename(f), w))
for f, w in sorted(missing): print('NO TRANSLATION', f, repr(w))
S = open(os.path.join(src, 'SettingsActivity.java'), encoding='utf8').read()
for m in re.finditer(r'choice\((L\.t\("[^"]*"\)|"[^"]*"), "([^"]+)", [^,]+, new String\[\] \{(.*?)\},\s*new String\[\] \{(.*?)\}\)', S, re.S):
    n = len(re.findall(r'"(?:[^"\\]|\\.)*"', m.group(3)))
    v = len(re.findall(r'"(?:[^"\\]|\\.)*"', m.group(4)))
    if n != v: print('CHOICE NAMES/VALUES DIFFER:', m.group(2), n, v); bad += 1
print('source checks: %d translation rows, %d words without translation, %d problems' % (len(table), len(missing), bad))
_exit_bad = bad

# every verse has all four languages and their references
import json as _json, os as _os
_v = _json.load(open(_os.path.join(_os.path.dirname(__file__), '..', '..', 'app', 'assets', 'bible_verses.json'), encoding='utf-8'))
_bad = [e.get('ref') for e in _v if not all(e.get(k) for k in ('en', 'ar', 'fr', 'es', 'ref_en', 'ref_ar', 'ref_fr', 'ref_es'))]
if _bad:
    print('verses missing a language:', _bad[:10]); sys.exit(1)
print('verses: %d, each in English, Arabic, French and Spanish' % len(_v))
# a verse must read as a whole sentence: no repeats, no half sentence, every feast reading present
import re as _re, collections as _co
_dup = [t for t, n in _co.Counter(e['en'] for e in _v).items() if n > 1]
_cut = [(e['ref'], k) for e in _v for k in ('en', 'ar', 'fr', 'es') if not _re.search(r'[.!?»؟”")]$', e[k].strip())
        or e[k].count('«') != e[k].count('»') or _re.search(r'Selah|سِلَاهْ', e[k])]
_FRAG = ('These things I command', 'This is the first and great', 'On these two commandments', 'Not of works', 'Proving what',
         'Distributing to', 'Endeavouring', 'Submitting yourselves', 'Naked, and', 'It shall be health', 'Her ways', 'She is a tree')
_frag = [e['ref'] for e in _v if e['en'].startswith(_FRAG)]
_src = open(_os.path.join(_os.path.dirname(__file__), '..', '..', 'src', 'com', 'velthon', 'launcher', 'Church.java'), encoding='utf-8').read()
_feast = set(_re.findall(r'"((?:\d?[A-Z][a-z]+|1John) \d+:\d+(?:-\d+)?)"', _src)) - {e['ref'] for e in _v}
if _dup or _cut or _frag or _feast:
    print('verse problems: repeated', _dup[:3], 'cut', _cut[:5], 'fragments', _frag, 'feast refs missing', sorted(_feast)); sys.exit(1)
print('verses: no repeats, no half sentences, all feast readings present')

# every word shown on screen has a translation row (English, Arabic, French, Spanish, German, Portuguese)
import re as _re, glob as _glob, os as _os
_src = _os.path.join(_os.path.dirname(__file__), '..', '..', 'src', 'com', 'velthon', 'launcher')
_rows = _re.findall(r'^\s*\{"((?:[^"\\]|\\.)*)",((?:\s*"(?:[^"\\]|\\.)*",?){5})\s*\},', open(_os.path.join(_src, 'L.java'), encoding='utf-8').read(), _re.M)
_keys = {r[0] for r in _rows}
_missing = set()
for _f in _glob.glob(_os.path.join(_src, '*.java')):
    if _f.endswith('L.java'): continue
    _t = open(_f, encoding='utf-8').read()
    for _m in _re.finditer(r'L\.(?:t|in)\((?:[^,()]+,\s*)?"((?:[^"\\]|\\.)*)"\)', _t): 
        if _m.group(1) not in _keys: _missing.add(_m.group(1))
if _missing:
    print('UNTRANSLATED:', '\n  '.join(sorted(_missing))); raise SystemExit(1)
print('translations: every on-screen word has all languages (%d)' % len(_keys))

sys.exit(1 if _exit_bad else 0)
