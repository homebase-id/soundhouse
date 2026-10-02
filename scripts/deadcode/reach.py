import subprocess, re, os, collections, json, sys
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
WORK = os.environ.get('DEADCODE_WORK', '/tmp/deadcode')
os.makedirs(WORK, exist_ok=True)
os.chdir(ROOT)
files = [f for f in subprocess.run(['git','ls-files','*.kt'],capture_output=True,text=True).stdout.split() if '/src/' in f]
ROOT_MODULES = ('audio-app/','androidApp/','desktopApp/','baselineprofile/')
def is_test(f):
    ss = f.split('/src/')[1].split('/')[0]
    return 'test' in ss.lower()
MODS = r'(?:(?:public|internal|protected|expect|actual|abstract|open|sealed|data|enum|annotation|value|inline|suspend|tailrec|operator|infix|external|const|lateinit|override|inner|crossinline|noinline)\s+)*'
DECL = re.compile(r'^((?:@[\w.]+(?:\([^)\n]*\))?\s+)*)(private\s+)?' + MODS + r'(fun\s+interface|class|interface|object|fun|val|var|typealias)\s+(?:<[^>\n]*>\s*)?(?:[\w.<>?, *]+\.)?(`[^`]+`|[A-Za-z_]\w*)', re.M)
PKG = re.compile(r'^package\s+([\w.]+)', re.M)
IMP = re.compile(r'^import\s+([\w.]+)(\.\*)?(?:\s+as\s+(\w+))?', re.M)
WORD = re.compile(r'\b[A-Za-z_]\w*\b')
info = {}
by_pkg_name = collections.defaultdict(set)   # (pkg,name) -> files
for f in files:
    s = open(f, encoding='utf-8').read()
    m = PKG.search(s); pkg = m.group(1) if m else ''
    pub = set()
    for d in DECL.finditer(s):
        if s[d.start():d.start()+1] in (' ','\t'): continue
        if d.group(2): continue  # private top-level: file-local
        name = d.group(4).strip('`')
        pub.add(name)
    body = re.sub(r'^(package|import)\s.*$', '', s, flags=re.M)
    info[f] = dict(pkg=pkg, decls=pub, words=set(WORD.findall(body)), imports=IMP.findall(s), text=s)
    for n in pub: by_pkg_name[(pkg, n)].add(f)
def refs(f):
    i = info[f]; out = set()
    visible = {}
    for w in i['words']:
        if (i['pkg'], w) in by_pkg_name: out |= by_pkg_name[(i['pkg'], w)]
    for path, star, alias in i['imports']:
        if star:
            for w in i['words']:
                out |= by_pkg_name.get((path, w), set())
        else:
            pkg, _, name = path.rpartition('.')
            out |= by_pkg_name.get((pkg, name), set())
            # import of a class: also nested/companion access goes through it; import of member of object: pkg is class path
            parent_pkg, _, parent = pkg.rpartition('.')
            out |= by_pkg_name.get((parent_pkg, parent), set())
    # fully-qualified mentions in code
    for (pkg, name), fs in ():
        pass
    return out - {f}
# roots
extra_fq = set()
for p in subprocess.run(['git','ls-files'],capture_output=True,text=True).stdout.split():
    try: t = open(p).read()
    except Exception: continue
    if p.endswith('AndroidManifest.xml'):
        extra_fq |= set(re.findall(r'android:name="([\w.]+)"', t))
    elif p.endswith('.pro') or '/META-INF/services/' in p:
        extra_fq |= set(re.findall(r'\b(id\.homebase[\w.]*\.[A-Z]\w*)', t))
roots = [f for f in files if f.startswith(ROOT_MODULES) and not is_test(f)]
for fq in extra_fq:
    pkg, _, name = fq.rpartition('.')
    roots += [f for f in by_pkg_name.get((pkg, name), ()) if not is_test(f)]
alive = set(); parent = {}; stack = [(r, None) for r in roots]
while stack:
    f, p = stack.pop()
    if f in alive: continue
    alive.add(f); parent[f] = p
    for g in refs(f):
        if g not in alive and not is_test(g): stack.append((g, f))
# expect/actual: if any file declaring (pkg,name) is alive, all declarers are alive
changed = True
while changed:
    changed = False
    for f in list(alive):
        for n in info[f]['decls']:
            for g in by_pkg_name[(info[f]['pkg'], n)]:
                if g not in alive and not is_test(g):
                    stack = [(g, f)]
                    while stack:
                        h, p = stack.pop()
                        if h in alive: continue
                        alive.add(h); parent[h] = p; changed = True
                        for k in refs(h):
                            if k not in alive and not is_test(k): stack.append((k, h))
dead = sorted(f for f in files if not is_test(f) and f not in alive)
json.dump(dead, open(os.path.join(WORK, 'reach-dead.json'),'w'))
json.dump(parent, open(os.path.join(WORK, 'reach-parent.json'),'w'))
lines = {f: info[f]['text'].count('\n') for f in dead}
print('alive', len(alive), 'dead', len(dead), 'dead lines', sum(lines.values()))
by = collections.Counter()
for f in dead: by['/'.join(f.split('/')[:1] + f.split('/')[2:3])] += lines[f]
for k, v in by.most_common(): print(v, k)
