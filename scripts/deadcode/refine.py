import json, re, subprocess, sys, os
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
WORK = os.environ.get('DEADCODE_WORK', '/tmp/deadcode')
os.makedirs(WORK, exist_ok=True)
os.chdir(ROOT)
cands = json.load(open(os.path.join(WORK, 'r8cands.json')))
excl = set(tuple(x) for x in json.load(open(os.path.join(WORK, 'excl.json')))) if os.path.exists(os.path.join(WORK, 'excl.json')) else set()
errs = open(os.path.join(WORK, 'compile-errors.txt')).read().splitlines()
edited = set(subprocess.run(['git','diff','--name-only'],capture_output=True,text=True).stdout.split())
cnames = {}
for c in cands: cnames.setdefault(c['name'], []).append(c['file'])
added = 0
byfile = {}
for e in errs:
    f = re.sub(r'^e: ([^:]+):.*', r'\1', e)
    byfile.setdefault(f, []).append(e)
for f, es in byfile.items():
    names = set()
    for e in es: names |= set(re.findall(r"'([A-Za-z_]\w*)'", e))
    for e in es:
        m = re.match(r'^e: [^:]+:(\d+):(\d+)', e)
        if m and os.path.exists(f):
            line = open(f).read().split('\n')[int(m.group(1))-1]
            names |= set(re.findall(r'[A-Za-z_]\w*', line[int(m.group(2))-1:]))
    hit = {n for n in names if n in cnames}
    if hit:
        for n in hit:
            for cf in cnames[n]:
                if (cf, n) not in excl: excl.add((cf, n)); added += 1
    elif f in edited:
        for c in cands:
            if c['file'] == f and (f, c['name']) not in excl: excl.add((f, c['name'])); added += 1
json.dump(sorted(excl), open(os.path.join(WORK, 'excl.json'), 'w'))
keep = [c for c in cands if (c['file'], c['name']) not in excl]
json.dump(keep, open(os.path.join(WORK, 'deadmembers.json'), 'w'))
print('excluded now', len(excl), '(+%d)' % added, 'remaining candidates', len(keep))
