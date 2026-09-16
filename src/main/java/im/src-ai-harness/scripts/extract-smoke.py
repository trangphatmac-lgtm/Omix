"""Extract the same two archives as the Java launcher into a disposable QA directory."""
import hashlib, json, os, platform, sys, zipfile
from pathlib import Path
root = Path(__file__).resolve().parents[1]
target = Path(sys.argv[1]).resolve()
arch = {'aarch64':'arm64', 'arm64':'arm64', 'amd64':'x64', 'x86_64':'x64'}[platform.machine().lower()]
name = {'Darwin':'macos', 'Windows':'windows', 'Linux':'linux'}[platform.system()]+'-'+arch
manifest = json.loads((root/'dist/manifest.json').read_text())
target.mkdir(parents=True, exist_ok=True)
for part in ['common', name]:
    path = root/'dist'/f'{part}.zip'
    assert hashlib.sha256(path.read_bytes()).hexdigest() == manifest['archives'][f'{part}.zip']
    with zipfile.ZipFile(path) as z:
        for entry in z.infolist():
            resolved = (target/entry.filename).resolve()
            assert resolved.is_relative_to(target)
        z.extractall(target)
    if os.name != 'nt':
        for executable in json.loads((target/f'executables-{part}.json').read_text()):
            p = target/executable
            p.chmod(p.stat().st_mode | 0o100)
print(f'Extracted {name}: {target}')
