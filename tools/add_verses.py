import csv, json, re, sys
sys.path.insert(0, '/home/claude/work')
# reuse the cleaning rules of the first build
SRC = '/home/claude/scrollmapper/bible_databases/formats/csv/'
def load(f):
    return {(r['Book'], int(r['Chapter']), int(r['Verse'])): r['Text'] for r in csv.DictReader(open(f, encoding='utf-8'))}
syn, jnd, rv = load(SRC + 'FreSynodale1921.csv'), load(SRC + 'FreJND.csv'), load(SRC + 'SpaRV.csv')
kjv = load('/tmp/sm/formats/csv/KJV.csv')
arb = {}
for line in open('/tmp/arb/arb-vd_vpl.txt', encoding='utf-8'):
    m = re.match(r'(\S+) (\d+):(\d+) (.*)', line.strip())
    if m: arb[(m[1], int(m[2]), int(m[3]))] = m[4]

# short ref book -> (KJV/CSV book, English name, Arabic code, Arabic name, French, Spanish)
B = {
 'Ps': ('Psalms', 'Psalms', 'PSA', 'مزامير', 'Psaumes', 'Salmos'),
 'Prov': ('Proverbs', 'Proverbs', 'PRO', 'أمثال', 'Proverbes', 'Proverbios'),
 'Isa': ('Isaiah', 'Isaiah', 'ISA', 'إشعياء', 'Ésaïe', 'Isaías'),
 'Matt': ('Matthew', 'Matthew', 'MAT', 'متى', 'Matthieu', 'Mateo'),
 'Mark': ('Mark', 'Mark', 'MAR', 'مرقس', 'Marc', 'Marcos'),
 'Luke': ('Luke', 'Luke', 'LUK', 'لوقا', 'Luc', 'Lucas'),
 'John': ('John', 'John', 'JOH', 'يوحنا', 'Jean', 'Juan'),
 'Rom': ('Romans', 'Romans', 'ROM', 'رومية', 'Romains', 'Romanos'),
 'Phil': ('Philippians', 'Philippians', 'PHI', 'فيلبي', 'Philippiens', 'Filipenses'),
 '1John': ('I John', '1 John', '1JO', 'يوحنا الأولى', '1 Jean', '1 Juan'),
 'Josh': ('Joshua', 'Joshua', 'JOS', 'يشوع', 'Josué', 'Josué'),
 'Deut': ('Deuteronomy', 'Deuteronomy', 'DEU', 'التثنية', 'Deutéronome', 'Deuteronomio'),
 'Jer': ('Jeremiah', 'Jeremiah', 'JER', 'إرميا', 'Jérémie', 'Jeremías'),
 'Lam': ('Lamentations', 'Lamentations', 'LAM', 'مراثي إرميا', 'Lamentations', 'Lamentaciones'),
 'Zeph': ('Zephaniah', 'Zephaniah', 'ZEP', 'صفنيا', 'Sophonie', 'Sofonías'),
 'Mic': ('Micah', 'Micah', 'MIC', 'ميخا', 'Michée', 'Miqueas'),
 'Hab': ('Habakkuk', 'Habakkuk', 'HAB', 'حبقوق', 'Habacuc', 'Habacuc'),
 '1Cor': ('I Corinthians', '1 Corinthians', '1CO', 'كورنثوس الأولى', '1 Corinthiens', '1 Corintios'),
 '2Cor': ('II Corinthians', '2 Corinthians', '2CO', 'كورنثوس الثانية', '2 Corinthiens', '2 Corintios'),
 'Gal': ('Galatians', 'Galatians', 'GAL', 'غلاطية', 'Galates', 'Gálatas'),
 'Col': ('Colossians', 'Colossians', 'COL', 'كولوسي', 'Colossiens', 'Colosenses'),
 '2Tim': ('II Timothy', '2 Timothy', '2TI', 'تيموثاوس الثانية', '2 Timothée', '2 Timoteo'),
 'Heb': ('Hebrews', 'Hebrews', 'HEB', 'العبرانيين', 'Hébreux', 'Hebreos'),
 'Jas': ('James', 'James', 'JAM', 'يعقوب', 'Jacques', 'Santiago'),
 '1Pet': ('I Peter', '1 Peter', '1PE', 'بطرس الأولى', '1 Pierre', '1 Pedro'),
 'Rev': ('Revelation of John', 'Revelation', 'REV', 'رؤيا يوحنا', 'Apocalypse', 'Apocalipsis'),
}
AD = str.maketrans('0123456789', '٠١٢٣٤٥٦٧٨٩')

def clean(s):
    s = re.sub(r'\s+', ' ', s).strip().replace(' ,', ',').replace(' .', '.')
    s = s.replace('’ ', '’')
    for ch in 'áóé':  # 1909 Spanish accents on single-letter words
        s = re.sub(r'(?<![\wÁÉÍÓÚáéíóúñÑ])%s(?![\wÁÉÍÓÚáéíóúñÑ])' % ch, {'á': 'a', 'ó': 'o', 'é': 'e'}[ch], s)
    s = re.sub(r'^([¡¿«]?)([A-ZÁÉÍÓÚÑ]{2,})\b', lambda m: m.group(1) + m.group(2)[0] + m.group(2)[1:].lower(), s)
    s = s.replace('JAH', 'Jah')
    if s and s[0].islower(): s = s[0].upper() + s[1:]
    return s

FIX = {'mirándo los': 'mirándolos', 'du, Christ': 'du Christ'}
def tidy(s):
    for k, v in FIX.items(): s = s.replace(k, v)
    s = re.sub(r'\[([^\]]*)\]', r'\1', s)
    s = re.sub(r'^[—\s]+', '', s).strip()
    if s.count('«') != s.count('»'): s = s.replace('«', '').replace('»', '')
    s = re.sub(r'[,;:،؛]+$', '', s.strip()) 
    if not re.search(r'[.!?»؟”")]$', s): s += '.'
    if s and s[0].islower(): s = s[0].upper() + s[1:]
    return s.strip()

def make(ref):
    b, cv = ref.rsplit(' ', 1)
    kb, en_name, ac, ar_name, fr_name, es_name = B[b]
    ch, vs = cv.split(':')
    a, z = (vs.split('-') + [vs])[:2]
    rng = range(int(a), int(z) + 1)
    pick = lambda t, book: clean(' '.join(t[(book, int(ch), v)] for v in rng))
    en = pick(kjv, kb)
    ar = ' '.join(arb[(ac, int(ch), v)] for v in rng)
    fr = pick(syn, kb) if b not in ('Prov', 'Isa') and syn.get((kb, int(ch), int(a))) else pick(jnd, kb)
    es = pick(rv, kb)
    en = en.replace('’', "'")
    ar, en, fr, es = (tidy(x) for x in (ar, en, fr, es))
    return {'ar': ar, 'en': en, 'ref': ref, 'ref_ar': ar_name + ' ' + cv.translate(AD), 'ref_en': en_name + ' ' + cv,
            'fr': fr, 'es': es, 'ref_fr': fr_name + ' ' + cv, 'ref_es': es_name + ' ' + cv}

# verses that only make sense next to the one before them, or repeat another: each is replaced in place,
# so the position of every other verse (and of the user's saved verses) stays the same
OUT = ['Matt 22:38', 'Matt 22:40', 'Prov 3:8', 'Prov 3:16', 'Prov 3:17', 'Prov 3:18', 'Prov 4:6', 'Prov 4:8', 'Prov 4:9',
       'Prov 4:13', 'Ps 112:8', 'Ps 107:14', 'Prov 4:21', 'Eph 5:10', 'John 15:17', 'Ps 71:23', 'Ps 84:5', 'Ps 92:15',
       'Ps 91:16', 'Luke 4:19', 'Eph 2:9', 'Eph 5:9', 'Rom 12:13', 'Eph 4:3', 'Eph 5:21', 'Eph 5:16', 'Matt 25:36',
       'Prov 30:6', 'Mark 13:31', 'Luke 21:33', 'Ps 62:6', 'Ps 118:29', 'Ps 46:11']
NEW = ['Matt 22:37', 'Josh 1:9', 'Deut 31:6', 'Isa 40:31', 'Isa 41:10', 'Isa 43:2', 'Isa 43:19', 'Jer 29:11', 'Jer 33:3',
       'Lam 3:22-23', 'Zeph 3:17', 'Mic 6:8', 'Ps 34:18', 'Ps 46:10', 'Ps 27:14', 'Ps 37:5', 'Ps 55:22', 'Ps 30:5',
       'Matt 6:33', 'Matt 6:34', 'Matt 7:7', 'Matt 19:26', 'Mark 9:23', 'John 3:16', 'John 8:12', 'John 11:25',
       'John 14:27', 'John 16:33', 'Rom 5:8', 'Rom 8:28', 'Rom 8:38-39', 'Rom 12:2', 'Rom 12:12', 'Rom 15:13',
       '1Cor 10:13', '1Cor 13:4', '1Cor 15:58', '1Cor 16:14', '2Cor 4:16', '2Cor 5:17', '2Cor 12:9', 'Gal 2:20',
       'Gal 6:9', 'Phil 1:6', 'Phil 4:6', 'Phil 4:7', 'Phil 4:19', 'Col 3:23', '2Tim 1:7', 'Heb 4:16', 'Heb 11:1',
       'Heb 13:8', 'Jas 1:5', 'Jas 4:8', '1Pet 5:7', '1John 1:9', '1John 4:18', 'Rev 3:20', 'Rev 21:4', 'Matt 4:4']

if __name__ == '__main__':
    path = sys.argv[1]
    d = json.load(open(path, encoding='utf-8'))
    have = {e['ref'] for e in d}
    new = [r for r in NEW if r not in have]
    print('already there:', [r for r in NEW if r in have])
    made = [make(r) for r in new]
    pos = [i for i, e in enumerate(d) if e['ref'] in OUT]
    print('replacing', len(pos), 'of', len(OUT), 'missing:', set(OUT) - {d[i]['ref'] for i in pos})
    for i, e in zip(pos, made): d[i] = e
    d.extend(made[len(pos):])
    for e in d:   # same end punctuation and paired quotes for every verse
        for k in ('en', 'ar', 'fr', 'es'): e[k] = tidy(e[k])
    # the psalms' musical mark (Selah) is not part of the sentence
    for e in d:
        for k, pat in (('en', r'\s*Selah\.?$'), ('ar', r'\s*سِلَاهْ\.?$'), ('fr', r'\s*Pause\.?$'), ('es', r'\s*\(Selah\.?\)$')):
            t = re.sub(pat, '', e[k]).strip()
            if t != e[k]: e[k] = tidy(t)
    json.dump(d, open(path, 'w', encoding='utf-8'), ensure_ascii=False, separators=(',', ':'))
    print('total', len(d))
    for e in made: print(e['ref'], '|', e['en'], '\n   ', e['ar'], '\n   ', e['fr'], '\n   ', e['es'])
