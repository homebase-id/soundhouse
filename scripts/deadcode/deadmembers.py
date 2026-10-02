import re, subprocess, collections, os, sys, json
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
WORK = os.environ.get('DEADCODE_WORK', '/tmp/deadcode')
os.makedirs(WORK, exist_ok=True)
os.chdir(ROOT)
allfiles = subprocess.run(['git','ls-files'],capture_output=True,text=True).stdout.split()
textfiles = [f for f in allfiles if f.endswith(('.kt','.kts','.xml','.pro','.sq','.swift','.json','.properties'))]
count = collections.Counter()
for f in textfiles:
    try:
        for w in re.findall(r'\b[A-Za-z_]\w*\b', open(f, encoding='utf-8').read()): count[w] += 1
    except Exception: pass
SKIP_MOD = re.compile(r'\b(override|operator|external|actual|expect|infix)\b')
DECL = re.compile(r'^(?P<indent>[ \t]*)(?P<mods>(?:(?:@[\w.]+(?:\([^)\n]*\))?|public|internal|private|protected|inline|suspend|tailrec|const|lateinit|open|abstract|final|data|enum|sealed|value|annotation|inner)\s+)*)(?P<kind>fun|val|var|class|object|interface)\s+(?:<[^>\n]*>\s*)?(?:(?P<recv>[\w.<>?, *]+)\.)?(?P<name>[A-Za-z_]\w*)', re.M)
main = [f for f in allfiles if f.endswith('.kt') and '/src/' in f and not re.search(r'/src/\w*[Tt]est/', f)]
declsites = collections.Counter(); cands = []
for f in main:
    s = open(f, encoding='utf-8').read()
    for m in DECL.finditer(s):
        name = m.group('name'); declsites[name] += 1
        cands.append((f, m.start(), m))
out = []
for f, pos, m in cands:
    name = m.group('name'); mods = m.group('mods') or ''
    if SKIP_MOD.search(mods) or name in ('main',) or name.startswith('component'): continue
    if count[name] != declsites[name]: continue
    s = open(f, encoding='utf-8').read()
    # skip constructor properties: the line is inside a parameter list if the previous non-space char before is '(' or ','
    before = s[:pos].rstrip()
    if before.endswith(('(', ',')): continue
    # skip members of @Serializable classes (rough: nearest enclosing 'class' preceded by @Serializable)
    head = s[:pos]
    lastclass = max(head.rfind('\nclass '), head.rfind(' class '), head.rfind('\ndata class'))
    if m.group('kind') in ('val','var') and '@Serializable' in head[max(0,lastclass-200):lastclass+1]: continue
    # skip functions annotated in a way that platforms call by name
    pre = s[max(0, pos-200):pos]
    if re.search(r'@(JvmStatic|JvmName|JavascriptInterface|Keep|ObjCName|Test|BeforeTest|AfterTest|Throws)\b', pre.split('\n\n')[-1] + mods): continue
    out.append(dict(file=f, pos=pos, name=name, kind=m.group('kind'), indent=len(m.group('indent'))))
json.dump(out, open(os.path.join(WORK, 'deadmembers.json'),'w'))
by = collections.Counter(o['file'].split('/')[0] for o in out)
print(len(out), 'candidates', dict(by))
