#!/usr/bin/env python3
"""Build and test the exact-target OEM star-choice transaction helper."""
from pathlib import Path
import hashlib
import json
import os
import shutil
import subprocess
import zipfile

SOURCE = Path(__file__).resolve().parent
ROOT = SOURCE.parent
WORKSPACE = ROOT.parents[1]
JAVA = Path(os.environ.get('JAVA_HOME', str(WORKSPACE / '.tools/java-build/jdk-17.0.21+9/Contents/Home'))) / 'bin'
ANDROID = Path(os.environ.get('ANDROID_JAR', str(WORKSPACE / '.tools/android34/android.jar')))
R8 = Path(os.environ.get('R8_JAR', str(WORKSPACE / '.tools/java-build/r8.jar')))

def main():
    build = ROOT / 'build'
    classes, dex = build / 'helper-classes', build / 'helper-dex'
    for directory in [classes, dex]:
        if directory.exists(): shutil.rmtree(directory)
        directory.mkdir(parents=True)
    def run(*args):
        subprocess.run([str(x) for x in args], check=True)
    run(JAVA / 'javac', '--release', '8', '-encoding', 'UTF-8', '-d', classes,
        SOURCE / 'vendor/hmgapi/engine/Json.java', *sorted((SOURCE / 'java').rglob('*.java')))
    run(JAVA / 'javac', '--release', '8', '-encoding', 'UTF-8', '-cp', classes, '-d', classes,
        SOURCE / 'tests/kr/ioniq5/staroptions/StarOptionsTest.java')
    run(JAVA / 'java', '-cp', classes, 'kr.ioniq5.staroptions.StarOptionsTest', SOURCE / 'payload/maps')
    production = build / 'helper-production.jar'
    with zipfile.ZipFile(production, 'w') as z:
        for file in sorted(classes.rglob('*.class')):
            if 'Test' not in file.name: z.write(file, file.relative_to(classes).as_posix())
    run(JAVA / 'java', '-cp', R8, 'com.android.tools.r8.D8', '--min-api', '19', '--lib', ANDROID,
        '--output', dex, production)
    data = (dex / 'classes.dex').read_bytes()
    assert data.startswith(b'dex\n035\0') and b'StarOptionsTest' not in data
    output = build / 'star-options.jar'
    with zipfile.ZipFile(output, 'w', zipfile.ZIP_DEFLATED) as z:
        z.write(dex / 'classes.dex', 'classes.dex')
    (build / 'HELPER-BUILD.json').write_text(json.dumps({'min_api':19, 'dex':'035',
        'sha256':hashlib.sha256(output.read_bytes()).hexdigest(), 'host_scenarios':25}, indent=2) + '\n')
    print(output)

if __name__ == '__main__':
    main()
