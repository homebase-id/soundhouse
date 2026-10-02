import re, json, os, collections, subprocess
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
WORK = os.environ.get('DEADCODE_WORK', '/tmp/deadcode')
os.makedirs(WORK, exist_ok=True)
os.chdir(ROOT)
cands = [c for c in json.load(open(os.path.join(WORK, 'deadmembers.json'))) if not c['file'].startswith('baselineprofile/')]
def code_mask(s):
    # returns list of booleans: True where char is code (not in string/comment)
    mask = [True]*len(s); i = 0; n = len(s)
    while i < n:
        if s.startswith('//', i):
            j = s.find('\n', i); j = n if j < 0 else j
            for k in range(i, j): mask[k] = False
            i = j
        elif s.startswith('/*', i):
            j = s.find('*/', i+2); j = n if j < 0 else j+2
            for k in range(i, j): mask[k] = False
            i = j
        elif s.startswith('"""', i):
            j = s.find('"""', i+3); j = n if j < 0 else j+3
            for k in range(i, j): mask[k] = False
            i = j
        elif s[i] == '"' or s[i] == "'":
            q = s[i]; j = i+1
            while j < n and s[j] != q and s[j] != '\n':
                j += 2 if s[j] == '\\' else 1
            j = min(n, j+1)
            for k in range(i, j): mask[k] = False
            i = j
        else: i += 1
    return mask
CONT = re.compile(r'^\s*(\.|\?\.|\?:|\+|-|\*|/|&&|\|\||=|->|:)')
def extent(s, mask, pos, indent):
    # start: back over contiguous annotation / comment lines
    line_start = s.rfind('\n', 0, pos) + 1
    start = line_start
    while True:
        prev_end = start - 1
        if prev_end <= 0: break
        prev_start = s.rfind('\n', 0, prev_end) + 1
        line = s[prev_start:prev_end].strip()
        if line.startswith(('@', '*', '/**', '/*', '//')) or line.endswith('*/'):
            start = prev_start
        else: break
    depth = 0; i = pos; n = len(s); seen_body = False
    while i < n:
        c = s[i]
        if mask[i]:
            if c in '({[': depth += 1
            elif c in ')}]': depth -= 1
        if depth < 0:
            return start, s.rfind('\n', 0, i) + 1
        if c == '\n' and depth == 0:
            nxt_end = s.find('\n', i+1); nxt_end = n if nxt_end < 0 else nxt_end
            nxt = s[i+1:nxt_end]
            if nxt.strip() == '' or (len(nxt) - len(nxt.lstrip())) <= indent and not CONT.match(nxt):
                cur_line = s[s.rfind('\n',0,i)+1:i].rstrip()
                if not cur_line.endswith(('=', '(', ',', '->', '.', '{')):
                    return start, i+1
        i += 1
    return start, n
removed = collections.defaultdict(list)
byfile = collections.defaultdict(list)
for c in cands: byfile[c['file']].append(c)
for f, cs in byfile.items():
    s = open(f, encoding='utf-8').read(); mask = code_mask(s)
    ranges = sorted(extent(s, mask, c['pos'], c['indent']) for c in cs)
    merged = []
    for a, b in ranges:
        if merged and a < merged[-1][1]: merged[-1] = (merged[-1][0], max(b, merged[-1][1]))
        else: merged.append((a, b))
    for a, b in reversed(merged):
        s = s[:a] + s[b:]
    s = re.sub(r'\n{3,}', '\n\n', s)
    s = re.sub(r'\{\n\n+', '{\n', s); s = re.sub(r'\n\n+(\s*\})', r'\n\1', s)
    open(f, 'w', encoding='utf-8').write(s)
    removed[f] = [c['name'] for c in cs]
json.dump(removed, open(os.path.join(WORK, 'cut-removed.json'), 'w'))
print('edited files', len(removed), 'declarations', sum(len(v) for v in removed.values()))
