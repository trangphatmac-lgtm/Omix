"""Produce deterministic common + six platform archives from the npm lock.

Foreign-platform optional packages are fetched only at BUILD time, by locked URL
and integrity. Installed dependency trees are never modified. No Node executable
or user data is packaged. Each native file is attributed to a supported platform.
"""
import base64, hashlib, io, json, os, re, tarfile, urllib.request, zipfile
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path, PurePosixPath

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'dist'
CACHE = ROOT.parents[1] / 'build' / 'harness-downloads'
PLATFORMS = {f'{display}-{arch}': (system, arch) for display, system in
             [('windows', 'win32'), ('macos', 'darwin'), ('linux', 'linux')] for arch in ['x64', 'arm64']}


def platforms(meta):
    def accepts(values, value):
        return not values or value in values or (all(v.startswith('!') for v in values) and '!'+value not in values)
    return {name for name, (system, arch) in PLATFORMS.items()
            if accepts(meta.get('os'), system) and accepts(meta.get('cpu'), arch)
            and accepts(meta.get('libc'), 'glibc')}


def native_platform(path):
    # Multi-platform packages (node-pty and pnpm) include binaries in subdirectories.
    match = re.search(r'(darwin|win32|win10|linux)[-_](x64|arm64)', path)
    if match:
        system, arch = match.groups()
        return {f'{dict(darwin="macos",win32="windows",win10="windows",linux="linux")[system]}-{arch}'}
    return None


def keep(path):
    parts = PurePosixPath(path).parts
    leaf = parts[-1]
    return not (any(p in {'test', 'tests', '__tests__', 'docs', 'examples', '.bin', '.cache', '.github'} for p in parts)
                or leaf.endswith(('.map', '.d.ts', '.tsbuildinfo')) or leaf in {'.DS_Store', '.package-lock.json'}
                or path.startswith('node_modules/node-pty/src/')
                or path.startswith('node_modules/node-pty/third_party/')
                or leaf == 'fastlist-0.3.0-x86.exe')


def locked_package(item):
    path, meta = item
    targets = platforms(meta)
    if not targets: return []
    integrity = meta['integrity']
    algorithm, expected = integrity.split('-', 1)
    cached = CACHE / (hashlib.sha256(integrity.encode()).hexdigest() + '.tgz')
    if not cached.exists():
        data = urllib.request.urlopen(meta['resolved'], timeout=90).read()
        if base64.b64encode(hashlib.new(algorithm, data).digest()).decode() != expected:
            raise RuntimeError('Integrity mismatch: ' + path)
        cached.write_bytes(data)
    data = cached.read_bytes()
    if base64.b64encode(hashlib.new(algorithm, data).digest()).decode() != expected:
        raise RuntimeError('Cached integrity mismatch: ' + path)
    files = []
    with tarfile.open(fileobj=io.BytesIO(data), mode='r:gz') as archive:
        for member in archive:
            if not member.isfile(): continue
            relative = PurePosixPath(member.name)
            if relative.is_absolute() or '..' in relative.parts: raise RuntimeError('Unsafe tar member')
            dest = path + '/' + '/'.join(relative.parts[1:])
            if keep(dest): files.append((dest, archive.extractfile(member).read(), member.mode, targets))
    return files


def main():
    OUT.mkdir(exist_ok=True)
    CACHE.mkdir(parents=True, exist_ok=True)
    lock = json.loads((ROOT / 'package-lock.json').read_text())['packages']
    platform_packages = {p: m for p, m in lock.items() if p and ('os' in m or 'cpu' in m)}
    records = {}
    for path in sorted((ROOT / 'node_modules').rglob('*')):
        if not path.is_file() or path.is_symlink(): continue
        relative = path.relative_to(ROOT).as_posix()
        if any(relative == p or relative.startswith(p + '/') for p in platform_packages): continue
        if keep(relative): records[relative] = (path.read_bytes(), path.stat().st_mode, native_platform(relative))
    with ThreadPoolExecutor(max_workers=6) as pool:
        for entries in pool.map(locked_package, platform_packages.items()):
            for name, data, mode, targets in entries: records[name] = (data, mode, targets)
    own_files = ['launch.mjs', 'omix.patch.yml', 'package.json', 'package-lock.json']
    own_files += [p.relative_to(ROOT).as_posix() for p in sorted((ROOT / 'plugin').rglob('*')) if p.is_file()]
    for relative in own_files:
        records[relative] = ((ROOT / relative).read_bytes(), 0o644, None)
    # The published helper can lose its executable bit even before ZIP extraction.
    archives = {name: zipfile.ZipFile(OUT / (name+'.zip'), 'w', zipfile.ZIP_DEFLATED, compresslevel=6)
                for name in ['common', *PLATFORMS]}
    counts = {name: 0 for name in archives}
    executable = {name: [] for name in archives}
    try:
        for path, (data, mode, targets) in sorted(records.items()):
            if PurePosixPath(path).name in {'node', 'node.exe'}: raise RuntimeError('Node runtime must not be embedded')
            names = targets if targets is not None and len(targets) < 6 else {'common'}
            if re.search(r'\.(node|dll|dylib|exe)$', path) and targets is None:
                # pnpm bundles a cross-platform Windows process listing helper.
                if path.endswith('fastlist-0.3.0-x64.exe'): names = {'windows-x64', 'windows-arm64'}
                else: raise RuntimeError('Unattributed native binary: ' + path)
            for name in sorted(names):
                entry = zipfile.ZipInfo(path, (2026, 1, 1, 0, 0, 0))
                entry.compress_type = zipfile.ZIP_DEFLATED
                entry.external_attr = (mode & 0o777) << 16
                archives[name].writestr(entry, data)
                counts[name] += 1
                if mode & 0o111 or PurePosixPath(path).name in {'spawn-helper', 'landlock-run', 'rg'}:
                    executable[name].append(path)
        for name in archives:
            entry = zipfile.ZipInfo('executables-'+name+'.json', (2026, 1, 1, 0, 0, 0))
            archives[name].writestr(entry, json.dumps(executable[name]))
    finally:
        for archive in archives.values(): archive.close()
    manifest = {'version': '0.1.6-alpha.1', 'archives': {name+'.zip': hashlib.sha256((OUT/(name+'.zip')).read_bytes()).hexdigest() for name in archives}}
    (OUT/'manifest.json').write_text(json.dumps(manifest, indent=2)+'\n')
    print(json.dumps({name: {'files': counts[name], 'MiB': round((OUT/(name+'.zip')).stat().st_size/1048576, 1)} for name in archives}))

if __name__ == '__main__': main()
