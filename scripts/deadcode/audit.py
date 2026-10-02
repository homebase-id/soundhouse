import subprocess, re, json, os
# Every removed hunk in the unstaged diff must start (after comments/annotations) with a candidate declaration.
WORK = os.environ.get('DEADCODE_WORK', '/tmp/deadcode')
os.chdir(os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..')))
names = {c['name'] for c in json.load(open(os.path.join(WORK, 'r8cands.json')))}
diff = subprocess.run(['git', 'diff', '-U0'], capture_output=True, text=True).stdout
hunks = []; cur = None; f = None; added = 0
for l in diff.splitlines():
    if l.startswith('+++ '): f = l[6:]
    elif l.startswith('@@'): cur = []; hunks.append((f, cur))
    elif l.startswith('-') and not l.startswith('---') and cur is not None: cur.append(l[1:])
    elif l.startswith('+') and not l.startswith('+++') and l[1:].strip(): added += 1
DECL = re.compile(r'^\s*(?:(?:@[\w.]+(?:\([^)]*\))?|public|internal|private|protected|inline|suspend|const|lateinit|open|abstract|data|enum|sealed|value|override)\s+)*(?:fun|val|var|class|object|interface)\s+(?:<[^>]*>\s*)?(?:[\w.<>?, *]+\.)?([A-Za-z_]\w*)')
bad = 0
for f, h in hunks:
    lines = [x for x in h if x.strip() and not x.strip().startswith(('*', '/**', '/*', '//', '@')) and not x.strip().endswith('*/')]
    if not lines: continue
    m = DECL.match(lines[0])
    if not m or m.group(1) not in names:
        bad += 1; print('SUSPICIOUS', f, '|', lines[0].strip()[:100])
print('hunks', len(hunks), 'suspicious', bad, 'non-blank added lines', added)
