# Dead-code tooling

Finds code nothing reaches and removes it, proving each cut by compiling. Working files go to
`$DEADCODE_WORK` (default `/tmp/deadcode`). Run from a clean tree.

| Script | What it does |
|---|---|
| `reach.py` | File-level reachability from the app modules (package/import aware; expect/actual live or die together; roots include manifest, keep-rule and service names). Writes `reach-dead.json`. |
| `prune.sh [--fast]` | Deletes the files `reach.py` found, compiles, lists failing main/test files. Tests for deleted code go by hand after checking what they cover. |
| `deadmembers.py` | Declarations whose name appears nowhere else in the repo (all platforms, tests, XML, ProGuard, SQL). Writes `deadmembers.json`. |
| `r8-usage.sh` + `r8members.py` | Release build with R8 `-printusage` (ProGuard rules restored afterwards), then the methods Android never calls, as `deadmembers.json`. |
| `cut-loop.sh [--fast]` | Cuts every candidate in `deadmembers.json`, compiles, excludes what breaks, recuts from clean until it compiles. |
| `compile-all.sh [--fast]` | Every module, main and tests; `--fast` skips iOS. |

Typical pass: `python3 scripts/deadcode/reach.py && scripts/deadcode/prune.sh --fast`, then
`python3 scripts/deadcode/deadmembers.py && scripts/deadcode/cut-loop.sh --fast`, then the full gate.
Audit every cut: each removed hunk must start with a candidate declaration.
