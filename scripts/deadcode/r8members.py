import re, subprocess, os, collections, json
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
WORK = os.environ.get('DEADCODE_WORK', '/tmp/deadcode')
os.makedirs(WORK, exist_ok=True)
os.chdir(ROOT)
files = [f for f in subprocess.run(['git','ls-files','*.kt'],capture_output=True,text=True).stdout.split()
         if f.startswith(('homebase-api/','homebase-common/','homebase-auth/','homebase-notifshared/')) and re.search(r'/src/(commonMain|androidMain)/', f)]
PKG = re.compile(r'^package\s+([\w.]+)', re.M)
CLS = re.compile(r'^\s*(?:[\w@()]+\s+)*?(?:class|interface|object)\s+(\w+)', re.M)
where = {}
for f in files:
    s = open(f).read(); m = PKG.search(s)
    if not m: continue
    pkg = m.group(1)
    for c in CLS.findall(s): where.setdefault(pkg + '.' + c, f)
    where.setdefault(pkg + '.' + os.path.basename(f)[:-3].replace('.', '_') + 'Kt', f)
unused = collections.defaultdict(set)
cur = None
for line in open(os.path.join(WORK, 'r8-usage.txt')):
    if not line.startswith(' '):
        cur = line.strip()[:-1] if line.rstrip().endswith(':') else None; continue
    if not cur or not cur.startswith('id.homebase'): continue
    m = re.match(r'\s+(?:[\w.$\[\]<>]+\s+)*?([\w$]+)\((.*)\)', line)
    if not m: continue
    name = m.group(1)
    if '$' in name or name.startswith(('get', 'set', 'is', 'access', 'component', 'copy', 'lambda')) or name in ('equals','hashCode','toString','<init>','<clinit>','invoke','write$Self','serializer','childSerializers','deserialize','serialize'): continue
    top = cur.split('$')[0]
    f = where.get(top)
    if f: unused[f].add(name)
# keep only names declared exactly once (as a non-override fun) in that file
DECLFUN = lambda n: re.compile(r'^([ \t]*)((?:(?:@[\w.]+(?:\([^)\n]*\))?|public|internal|private|protected|inline|suspend|tailrec|open|abstract|final)\s+)*)fun\s+(?:<[^>\n]*>\s*)?(?:[\w.<>?, *]+\.)?' + re.escape(n) + r'\s*\(', re.M)
out = []
for f, names in unused.items():
    s = open(f).read()
    for n in names:
        ms = list(DECLFUN(n).finditer(s))
        allfun = len(re.findall(r'\bfun\s+(?:<[^>\n]*>\s*)?(?:[\w.<>?, *]+\.)?' + re.escape(n) + r'\s*\(', s))
        if len(ms) != 1 or allfun != 1: continue
        mods = ms[0].group(2) or ''
        if re.search(r'\b(override|operator|actual|expect|external|abstract)\b', mods): continue
        if re.search(r'@(Composable|JvmStatic|JvmName|Keep|ObjCName|Throws)', mods + s[max(0,ms[0].start()-120):ms[0].start()]): 
            # composables are fine to remove if unused, but keep platform-called ones
            if not re.search(r'@Composable', mods + s[max(0,ms[0].start()-120):ms[0].start()]): continue
        out.append(dict(file=f, pos=ms[0].start(), name=n, kind='fun', indent=len(ms[0].group(1))))
json.dump(out, open(os.path.join(WORK, 'deadmembers.json'), 'w'))
print(len(out), 'R8-unused fun candidates in', len(set(o['file'] for o in out)), 'files')
