import json
import os
from pathlib import Path

run_id = "discover-2be3bc4d-0178-4bb1-9c5b-b722c1a59204"
root = Path(os.getenv("CRAWL_STORAGE_ROOT", "/var/lib/stackline/crawling"))
print(f"storage_root={root}")

matches = []
for registry in root.glob(".jobs/*.json"):
    try:
        value = json.loads(registry.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        continue
    if value.get("run_id") == run_id:
        matches.append((registry, value))

if not matches:
    for state_path in root.rglob("run-state.json"):
        try:
            value = json.loads(state_path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError):
            continue
        if value.get("run_id") == run_id:
            matches.append((state_path, {"run_id": run_id, "build_root": str(state_path.parent), **value}))

if not matches:
    print("run_found=False")
    raise SystemExit(0)

print("run_found=True")
for source, job in matches:
    print(f"source={source}")
    print(f"status={job.get('status')}")
    build_root = Path(str(job.get("build_root", source.parent)))
    print(f"build_root={build_root}")
    for relative in ("run-state.json", "results/categories-result.json", "workspace/manifest.json", "workspace/site-sop.md"):
        path = build_root / relative
        print(f"artifact_{relative.replace('/', '_')}_exists={path.is_file()}")
        if path.is_file() and relative.endswith(".json"):
            try:
                artifact = json.loads(path.read_text(encoding="utf-8"))
                print(f"artifact_{relative.replace('/', '_')}={json.dumps(artifact, ensure_ascii=False)[:4000]}")
            except (OSError, json.JSONDecodeError) as error:
                print(f"artifact_read_error={type(error).__name__}")
    transcript = build_root / "logs/agent-transcript.jsonl"
    if transcript.is_file():
        lines = transcript.read_text(encoding="utf-8", errors="replace").splitlines()
        print(f"transcript_lines={len(lines)}")
        for line in lines[-80:]:
            print("TRANSCRIPT " + line[:4000])
