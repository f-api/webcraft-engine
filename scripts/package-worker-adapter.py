#!/usr/bin/env python3
"""Compile the worker against the immutable producer, never against host classes."""
import hashlib
import io
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import zipfile

def require(condition, message):
    if not condition:
        raise RuntimeError(message)


root, archive, javac, destination = map(Path, sys.argv[1:])
sha = lambda data: hashlib.sha256(data).hexdigest()
require(sha(archive.read_bytes()) == 'a4caebe4f454b916e1dd1c320b20cbf27179396ad3ae905987b6ea2725761aed',
        'Producer archive checksum mismatch')
manifest_text = (root / 'src/main/resources/generation-producers/current-worker.properties').read_text()
manifest = dict(line.split('=', 1) for line in manifest_text.splitlines() if '=' in line)
sources = sorted((root / 'src/main/java/com/gameexpert/authority/versioned/worker').glob('*.java'))
require(bool(sources), 'Worker sources missing')
source_hashes = {path: sha(path.read_bytes()) for path in sources}
with tempfile.TemporaryDirectory(prefix='worker-adapter-', dir=destination.parent) as temporary:
    work = Path(temporary)
    bundle = work / 'bundle'
    bundle.mkdir()
    with zipfile.ZipFile(archive) as zipped:
        for entry in zipped.infolist():
            target = bundle / entry.filename
            require(target.resolve().is_relative_to(bundle.resolve())
                    and entry.filename.endswith('.jar') and not entry.is_dir(),
                    f'Invalid producer archive entry: {entry.filename}')
            zipped.extract(entry, bundle)
    classpath = []
    for index in range(1, int(manifest['jar.count'])):
        jar = bundle / manifest[f'jar.{index}.file']
        require(jar.resolve().is_relative_to(bundle.resolve()), f'Invalid producer JAR path: {jar}')
        require(sha(jar.read_bytes()) == manifest[f'jar.{index}.sha256'],
                f'Producer JAR checksum mismatch: {jar.name}')
        classpath.append(str(jar))
    classes = work / 'classes'
    classes.mkdir()
    empty = work / 'sourcepath'
    empty.mkdir()
    subprocess.run([str(javac), '--release', '21', '-encoding', 'UTF-8', '-proc:none', '-implicit:none',
                    '-sourcepath', str(empty), '-classpath', os.pathsep.join(classpath),
                    '-d', str(classes), *map(str, sources)], check=True)
    require(source_hashes == {path: sha(path.read_bytes()) for path in sources},
            'Worker sources changed while compiling')
    payload = io.BytesIO()
    with zipfile.ZipFile(payload, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as zipped:
        for path in sorted(classes.rglob('*.class')):
            relative = path.relative_to(classes).as_posix()
            require(relative.startswith('com/gameexpert/authority/versioned/worker/'),
                    f'Unexpected adapter class: {relative}')
            info = zipfile.ZipInfo(relative, (1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            zipped.writestr(info, path.read_bytes())
    binary = payload.getvalue()
digest = sha(binary)
name = f'worker-adapter.{digest}.jar'
destination.mkdir(parents=True, exist_ok=True)
for old in destination.glob('worker-adapter.*.jar'):
    old.unlink()
(destination / name).write_bytes(binary)
lines = [f'jar.0.file={name}' if line.startswith('jar.0.file=') else
         f'jar.0.sha256={digest}' if line.startswith('jar.0.sha256=') else line
         for line in manifest_text.splitlines()]
(destination / 'runtime-worker.properties').write_text('\n'.join(lines) + '\n')
print(f'Packaged {name}: {len(binary)} bytes, {len(sources)} sources')
