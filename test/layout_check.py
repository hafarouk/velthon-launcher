"""Finds squeezed or overlapping text in uiautomator dumps of the launcher's screens.
Usage: layout_check.py <package> dump1.xml [dump2.xml ...]   Prints one line per problem; nothing when all is well."""
import re, sys
pkg, files = sys.argv[1], sys.argv[2:]
seen = set()
for f in files:
    try: xml = open(f, encoding='utf-8', errors='replace').read()
    except OSError: continue
    nodes = []
    for m in re.finditer(r'<node [^>]*>', xml):
        n = m.group(0)
        if 'package="%s"' % pkg not in n or 'class="android.widget.TextView"' not in n: continue
        tm = re.search(r'\btext="([^"]*)"', n)
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
        if not tm: continue
        t = tm.group(1).strip()
        if not t or not b: continue
        x1, y1, x2, y2 = map(int, b.groups())
        nodes.append((t, x1, y1, x2, y2))
    for t, x1, y1, x2, y2 in nodes:
        w, h = x2 - x1, y2 - y1
        # a word squeezed into a column a letter or two wide (each line far taller than it is wide)
        if len(t) >= 4 and w > 0 and w < 70 and h > 2 * w:
            k = ('squeezed', t)
            if k not in seen: seen.add(k); print('SQUEEZED  "%s"  %dx%d px  (%s)' % (t[:40], w, h, f))
    for i in range(len(nodes)):
        for j in range(i + 1, len(nodes)):
            a, b = nodes[i], nodes[j]
            ox = min(a[3], b[3]) - max(a[1], b[1]); oy = min(a[4], b[4]) - max(a[2], b[2])
            if ox > 8 and oy > 8:
                k = ('overlap', a[0], b[0])
                if k not in seen: seen.add(k); print('OVERLAP   "%s"  over  "%s"  (%s)' % (a[0][:30], b[0][:30], f))
