"""Validate and package the locally built MobileD Android release.

Run from this Android project after the final Gradle build and lint review.
No build, install, firmware flash, network operation or signing-key read occurs.
The existing, signed release APK is verified with Android SDK build-tools 34.
"""
from __future__ import annotations

import argparse
from datetime import datetime
from hashlib import sha256
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET
from zipfile import ZipFile


VERSION = '2.0.11'
VERSION_CODE = 24
APPLICATION_ID = 'com.mobiled.android.test'
EXPECTED_TESTS = {
    'com.mobiled.android.ui.controller.MusicOutputTest': 15,
    'com.mobiled.android.DeviceRoutingPolicyTest': 3,
    'com.mobiled.android.DiscoveryCommandRoutesTest': 3,
    'com.mobiled.android.ExampleUnitTest': 1,
    'com.mobiled.android.ui.controller.TapTempoTest': 4,
}
ROOT = Path(__file__).resolve().parents[1]
ALIGNMENT = ROOT / 'build' / 'reports' / 'alignment'
DIST = ROOT / 'dist'
CONFIG_INPUTS = {
    'app/build.gradle', 'app/proguard-rules.pro', 'build.gradle',
    'settings.gradle', 'gradle/wrapper/gradle-wrapper.properties',
    'gradle/wrapper/gradle-wrapper.jar', 'gradlew', 'gradlew.bat',
}
SOURCE_PREFIXES = ('app/src/main/', 'app/src/test/', 'app/src/androidTest/')


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def digest(path):
    return sha256(path.read_bytes()).hexdigest()


def read_json(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def read_build_log(path):
    raw = path.read_bytes()
    # Windows PowerShell redirection commonly emits UTF-16 with a BOM.
    encoding = 'utf-16' if raw.startswith((b'\xff\xfe', b'\xfe\xff')) else 'utf-8-sig'
    return raw.decode(encoding, errors='replace')


def project_file(relative):
    path = ROOT / relative
    path.resolve().relative_to(ROOT.resolve())
    require(path.is_file(), 'Missing project file: ' + str(relative))
    return path


def check_sources():
    snapshot_path = project_file('build/reports/alignment/source-files.json')
    snapshot = read_json(snapshot_path)
    files = snapshot.get('files')
    require(isinstance(files, list) and bool(files), 'Source snapshot must contain a nonempty files list')
    canonical = json.dumps(files, sort_keys=True, separators=(',', ':'))
    require(sha256(canonical.encode('utf-8')).hexdigest() == snapshot.get('source_tree_sha256'),
            'Source snapshot aggregate hash is invalid')
    names = set()
    for item in files:
        name = item['path'].replace('\\', '/')
        require(name not in names, 'Duplicate source snapshot path: ' + name)
        names.add(name)
        require(name.startswith(SOURCE_PREFIXES) or name in CONFIG_INPUTS,
                'Snapshot path is outside the permitted source/configuration inputs: ' + name)
        require(not name.lower().endswith('.zip'), 'Archive must not be a source fingerprint input: ' + name)
        path = project_file(name)
        require(path.stat().st_size == item['bytes'] and digest(path) == item['sha256'],
                'Source changed since final-build snapshot: ' + name)
    actual_sources = {
        path.relative_to(ROOT).as_posix()
        for folder in ('main', 'test', 'androidTest')
        for path in (ROOT / 'app' / 'src' / folder).rglob('*')
        if path.is_file() and path.suffix.lower() != '.zip'
    }
    captured_sources = {name for name in names if name.startswith(SOURCE_PREFIXES)}
    require(actual_sources == captured_sources, 'Source snapshot omits or retains a source file')
    require({'app/build.gradle', 'build.gradle', 'settings.gradle',
             'gradle/wrapper/gradle-wrapper.properties', 'app/proguard-rules.pro'} <= names,
            'Source snapshot is missing a required build configuration input')
    return snapshot


def check_tests():
    directory = ROOT / 'app' / 'build' / 'test-results' / 'testDebugUnitTest'
    reports = sorted(directory.glob('TEST-*.xml'))
    require(bool(reports), 'No JVM test result XML files')
    suites, cases = {}, []
    for path in reports:
        suite = ET.parse(path).getroot()
        name = suite.attrib.get('name', '')
        require(name not in suites, 'Duplicate JVM test suite: ' + name)
        tests = suite.findall('testcase')
        failures = len(suite.findall('.//failure')) + len(suite.findall('.//error'))
        skipped = len(suite.findall('.//skipped'))
        require(failures == 0 and skipped == 0, 'JVM tests have failures/errors/skips: ' + name)
        require(all(int(suite.attrib.get(key, '0')) == 0 for key in ('failures', 'errors', 'skipped')),
                'JVM suite reports failures/errors/skips: ' + name)
        require(int(suite.attrib.get('tests', len(tests))) == len(tests), 'JVM test count mismatch: ' + name)
        suites[name] = len(tests)
        cases.extend({'class': name, 'name': case.attrib['name']} for case in tests)
    require(suites == EXPECTED_TESTS, 'Unexpected JVM test suite counts: ' + json.dumps(suites))
    return {'tests': sum(suites.values()), 'failures': 0, 'errors': 0, 'skipped': 0,
            'suites': suites, 'cases': cases}, reports


def check_lint(build_text):
    require(':app:lintDebug' in build_text, 'Final build log must include the lintDebug task')
    path = project_file('app/build/reports/lint-results-debug.xml')
    issues = ET.parse(path).getroot().findall('issue')
    errors = [issue for issue in issues if issue.attrib.get('severity', '').lower() in ('error', 'fatal')]
    warnings = [issue for issue in issues if issue.attrib.get('severity', '').lower() == 'warning']
    require(not errors, 'Final lintDebug report contains error/fatal issues; no baseline or suppression bypass is accepted')
    return {'task': ':app:lintDebug', 'status': 'passed', 'errors': len(errors), 'warnings': len(warnings),
            'error_ids': sorted(set(issue.attrib.get('id', '') for issue in errors)),
            'baseline_used': False, 'source_report': path.relative_to(ROOT).as_posix()}


def run_tool(arguments):
    result = subprocess.run([str(value) for value in arguments], cwd=ROOT,
                            capture_output=True, encoding='utf-8', errors='replace', check=False)
    output = result.stdout + result.stderr
    require(result.returncode == 0, 'SDK verification tool failed: ' + str(arguments[0]) + '\n' + output)
    return output


def check_apk(sdk_root):
    java_home = os.environ.get('JAVA_HOME')
    require(bool(java_home), 'Set JAVA_HOME to JDK 21 before packaging')
    java = Path(java_home) / 'bin' / ('java.exe' if os.name == 'nt' else 'java')
    require(java.is_file(), 'JAVA_HOME does not contain Java')
    java_version = run_tool([java, '-version'])
    require(bool(re.search(r'version\s+"21(?:[."]|$)', java_version)), 'Packaging requires JAVA_HOME JDK 21')
    build_tools = sdk_root / 'build-tools' / '34.0.0'
    aapt = build_tools / ('aapt.exe' if os.name == 'nt' else 'aapt')
    signer = build_tools / 'lib' / 'apksigner.jar'
    require(aapt.is_file() and signer.is_file(), 'SDK build-tools 34.0.0 are required')
    metadata_path = project_file('app/build/outputs/apk/release/output-metadata.json')
    metadata = read_json(metadata_path)
    require(metadata.get('applicationId') == APPLICATION_ID and metadata.get('variantName') == 'release',
            'APK metadata application ID or variant is incorrect')
    elements = metadata.get('elements', [])
    require(len(elements) == 1, 'Expected one universal release APK')
    element = elements[0]
    require(element.get('versionName') == VERSION and int(element.get('versionCode', -1)) == VERSION_CODE,
            'APK metadata version is incorrect')
    apk = metadata_path.parent / element['outputFile']
    apk.resolve().relative_to(metadata_path.parent.resolve())
    require(apk.is_file(), 'Release APK listed by metadata is missing')
    badging = run_tool([aapt, 'dump', 'badging', apk])
    package = re.search(r"^package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", badging, re.M)
    require(package is not None and package.groups() == (APPLICATION_ID, str(VERSION_CODE), VERSION),
            'Compiled APK package/version differs from release metadata')
    permissions = [
        'android.permission.RECORD_AUDIO', 'android.permission.FOREGROUND_SERVICE',
        'android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION',
    ]
    require(all("name='" + permission + "'" in badging for permission in permissions),
            'Compiled APK is missing an audio/foreground-service permission')
    manifest = run_tool([aapt, 'dump', 'xmltree', apk, 'AndroidManifest.xml'])
    service_name = 'com.mobiled.android.ui.controller.MusicCaptureKeepAliveService'
    require(service_name in manifest or '.ui.controller.MusicCaptureKeepAliveService' in manifest,
            'Compiled APK is missing MusicCaptureKeepAliveService')
    require('foregroundServiceType' in manifest, 'Compiled APK is missing foregroundServiceType')
    icon_paths = sorted(set(re.findall(r"^application-icon-[0-9]+:'([^']+)'", badging, re.M)
                            + re.findall(r"^application: .*? icon='([^']+)'", badging, re.M)))
    with ZipFile(apk) as archive:
        entries = set(archive.namelist())
    require(bool(icon_paths) and all(path in entries for path in icon_paths),
            'Compiled APK launcher icon resource is missing')
    require('resources.arsc' in entries, 'Compiled APK resources table is missing')
    signature = run_tool([java, '-jar', signer, 'verify', '--verbose', '--print-certs', apk])
    require('Verifies' in signature, 'apksigner did not confirm APK verification')
    return apk, {'application_id': APPLICATION_ID, 'version_name': VERSION,
                 'version_code': VERSION_CODE, 'variant': 'release', 'signature_verified': True,
                 'sdk_build_tools': '34.0.0', 'permissions': permissions, 'capture_service': service_name,
                 'icon_resources': icon_paths, 'java': java_version.strip()}, {
                     'aapt-badging.txt': badging, 'aapt-manifest.txt': manifest,
                     'apksigner-verify.txt': signature,
                 }


def copy_report(source, relative):
    target = DIST / 'reports' / relative
    target.resolve().relative_to((DIST / 'reports').resolve())
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, target)


def file_record(path):
    return {'path': path.relative_to(DIST).as_posix(), 'bytes': path.stat().st_size, 'sha256': digest(path)}


def package_release(sdk_root):
    require((ROOT / 'app' / 'build.gradle').is_file(), 'Script must live in the Android project scripts directory')
    source_snapshot = check_sources()
    build_log = project_file('build/reports/alignment/final-build.log')
    build_text = read_build_log(build_log)
    require('BUILD SUCCESSFUL' in build_text and 'BUILD FAILED' not in build_text, 'Final Gradle build has not succeeded')
    require(':app:assembleRelease' in build_text and ':app:testDebugUnitTest' in build_text,
            'Final Gradle log must contain the release assembly and JVM test tasks')
    require(datetime.fromisoformat(source_snapshot['captured_at']).timestamp() <= build_log.stat().st_mtime + 2,
            'Final build log predates source snapshot')
    tests, test_reports = check_tests()
    lint = check_lint(build_text)
    apk_source, apk_metadata, sdk_outputs = check_apk(sdk_root)
    with ZipFile(apk_source) as archive:
        about_bytes = archive.read('assets/aboutus.html')
    require(about_bytes == project_file('app/src/main/assets/aboutus.html').read_bytes(),
            'Packaged About page differs from the current source')
    about_text = about_bytes.decode('utf-8')
    require(all(term in about_text for term in ('{{APP_VERSION}}', 'MobileD &mdash; 20 W',
                                                'MobileD XL &mdash; 40 W', '46.1', '3.5 Ah')),
            'About page is missing the automatic version or the two product specifications')
    about_verification = {'asset': 'assets/aboutus.html', 'matches_current_source': True,
                          'sha256': sha256(about_bytes).hexdigest(),
                          'automatic_version_source': 'BuildConfig.VERSION_NAME',
                          'models': ['MobileD 20 W', 'MobileD XL 40 W'],
                          'specification_source': {'title': 'MobileD - User Manual ENG 2026.pdf',
                            'sha256': '4935c9469471225d880af921f743f852ce9c4fd84660a554ea39b57ad2a9af4e',
                            'pdf_pages': [5, 6, 7, 8, 34, 42]}}
    documentation = []
    for suffix in ('.md', '.pdf'):
        name = 'MobileD_MobileApps_Functional_Reference_v1.8' + suffix
        path = project_file(name)
        target = DIST / 'docs' / name
        require(target.is_file() and digest(path) == digest(target), 'Current dist documentation is missing or differs: ' + name)
        documentation.append(target)
    documentation_qa = read_json(project_file('build/reports/alignment/documentation-verification.json'))
    require(documentation_qa.get('passed') is True, 'Documentation verification has not passed')
    require(documentation_qa.get('sha256') == digest(documentation[-1]),
            'Documentation PDF has changed since verification')
    DIST.mkdir(parents=True, exist_ok=True)
    apk_target = DIST / ('MobileD_Android_' + VERSION + '.apk')
    shutil.copy2(apk_source, apk_target)
    for name in ('final-build.log', 'source-files.json', 'documentation-verification.json'):
        copy_report(ALIGNMENT / name, name)
    for report in sorted((ROOT / 'app' / 'build' / 'reports').glob('lint-results-*')):
        if report.is_file():
            copy_report(report, Path('lint') / report.name)
    for report in test_reports:
        copy_report(report, Path('tests') / report.name)
    html_root = ROOT / 'app' / 'build' / 'reports' / 'tests' / 'testDebugUnitTest'
    if html_root.is_dir():
        for report in html_root.rglob('*'):
            if report.is_file():
                copy_report(report, Path('tests') / 'html' / report.relative_to(html_root))
    for name, output in sdk_outputs.items():
        target = DIST / 'reports' / name
        target.write_text(output, encoding='utf-8')
    apk_record = file_record(apk_target)
    runtime = {'installed': False, 'launch_verified': False,
               'physical_phone_tests_run': False, 'emulator_tests_run': False,
               'instrumentation_tests_run': False, 'audio_playback_capture_on_device_tested': False,
               'physical_dmx_tests_run': False}
    installation_note = ("L'APK non risulta installato. Non sono stati eseguiti test su telefono/emulatore, "
                         'test strumentali, cattura audio reale o DMX fisico.')
    installation_path = ALIGNMENT / 'device-installation.json'
    if installation_path.exists():
        installed = read_json(installation_path)
        require(installed.get('apk_sha256') == apk_record['sha256']
                and installed.get('application_id') == APPLICATION_ID
                and installed.get('version_name') == VERSION
                and installed.get('version_code') == VERSION_CODE,
                'Device installation report refers to another APK/version')
        require(installed.get('installation', {}).get('status') == 'Success'
                and installed.get('launch', {}).get('status') == 'ok'
                and installed.get('package_observation', {}).get('version_name') == VERSION
                and installed.get('package_observation', {}).get('version_code') == VERSION_CODE,
                'Installation/launch/version evidence is incomplete')
        copy_report(installation_path, 'device-installation.json')
        runtime.update(installed=True, launch_verified=True,
                       installation_report='reports/device-installation.json')
        installation_note = (f"APK {VERSION} installata con aggiornamento sul telefono {installed['device_model']}; "
                             'avvio e versione verificati. Non sono stati eseguiti test funzionali del Tap Sync sul telefono, '
                             'test strumentali, cattura audio reale o DMX fisico.')
    report = {
        'generated_at': datetime.now().astimezone().isoformat(), 'project': 'MobileD Android',
        'build': {'status': 'passed', 'log': 'reports/final-build.log',
                  'tasks': [':app:testDebugUnitTest', ':app:assembleRelease', ':app:lintDebug']},
        'apk': {**apk_metadata, **apk_record}, 'jvm_tests': tests, 'lint': lint,
        'about': about_verification,
        'source_snapshot': {'captured_at': source_snapshot['captured_at'], 'files': len(source_snapshot['files']),
                            'source_tree_sha256': source_snapshot['source_tree_sha256'],
                            'report': 'reports/source-files.json', 'matches_current_sources': True},
        'documentation': {'qa_passed': True, 'pages': documentation_qa.get('pages'),
                          'files': [file_record(path) for path in documentation]},
        'runtime_verification': runtime,
        'limitations': [
            'JVM tests and APK verification do not establish capture or lighting behavior on hardware.',
            'Android Music captures permitted playback media/game/unknown on API 29+ with user consent; no phone microphone capture is implemented.',
            'No native mobile firmware-update feature is added; 8 MB combined is plain and only its OTA update payload is encrypted.',
            'Android/firmware Matrix IDs reach 1023; the current PC 0.20.45 Matrix UI still caps IDs at 255.',
        ],
        'integrity': {'algorithm': 'SHA-256', 'manifest': 'release-manifest.json', 'checksums': 'SHA256SUMS.txt',
                      'self_hash_policy': 'Manifest lists payload files; checksums also list the manifest; the checksum file excludes itself.'},
    }
    write_json(DIST / 'build-verification.json', report)
    validation = f'''# MobileD Android {VERSION} — Verifica della consegna

APK release firmata: `MobileD_Android_{VERSION}.apk`, applicationId `{APPLICATION_ID}`, versionCode {VERSION_CODE}.

- Build finale Gradle: riuscita; log in `reports/final-build.log`.
- Test JVM: {tests['tests']} passati, 0 failure/error, 0 saltati; {tests['suites']['com.mobiled.android.ui.controller.MusicOutputTest']} Music, {tests['suites']['com.mobiled.android.DeviceRoutingPolicyTest'] + tests['suites']['com.mobiled.android.DiscoveryCommandRoutesTest']} routing, {tests['suites']['com.mobiled.android.ui.controller.TapTempoTest']} Tap Sync e {tests['suites']['com.mobiled.android.ExampleUnitTest']} test esistente.
- About: asset nel pacchetto identico ai sorgenti, versione automatica e schede MobileD 20 W / MobileD XL 40 W verificate.
- Firma APK: verificata con apksigner SDK 34.0.0; metadati, permessi audio/foreground service, servizio Music e risorse icona presenti nell'APK.
- Lint debug, eseguito insieme alla build release: `{lint['status']}`, {lint['errors']} errori/fatal, {lint['warnings']} warning; nessuna baseline usata per ignorare errori.
- Sorgenti: {len(source_snapshot['files'])} file uguali allo snapshot prima della build; impronta `{source_snapshot['source_tree_sha256']}`.
- Documentazione v1.8: Markdown e PDF di {documentation_qa.get('pages')} pagine; copie e verifica PDF controllate.
- SHA-256 APK: `{apk_record['sha256']}`.

{installation_note} La presenza delle risorse icona non certifica una prova visiva nel launcher.

Music acquisisce playback ammesso da Android 10+, con consenso MediaProjection; non acquisisce il microfono del telefono. Non è aggiunta una funzione OTA nativa mobile. Il combined 8 MB resta in chiaro; solo il suo payload OTA è cifrato. Android/firmware Matrix supportano ID 0–1023; il controller Matrix PC 0.20.45 mantiene il limite UI 255.

I dettagli sono in `build-verification.json`. I report originali sono in `reports`; la documentazione è in `docs`. `release-manifest.json` elenca i file della consegna con dimensioni e SHA-256. `SHA256SUMS.txt` comprende anche il manifest ed esclude se stesso.
'''
    (DIST / 'Build_Validation.md').write_text(validation, encoding='utf-8')
    # Recheck after SDK tools and copying so a concurrent edit cannot be packaged silently.
    require(check_sources()['source_tree_sha256'] == source_snapshot['source_tree_sha256'], 'Source snapshot changed during packaging')
    payload = sorted(path for path in DIST.rglob('*') if path.is_file()
                     and path.name not in ('release-manifest.json', 'SHA256SUMS.txt'))
    write_json(DIST / 'release-manifest.json', {
        'project': 'MobileD Android', 'version': VERSION, 'version_code': VERSION_CODE,
        'generated_at': report['generated_at'], 'algorithm': 'SHA-256',
        'files': [file_record(path) for path in payload],
        'excludes': ['release-manifest.json', 'SHA256SUMS.txt'],
    })
    checksum_files = sorted(payload + [DIST / 'release-manifest.json'])
    (DIST / 'SHA256SUMS.txt').write_text(''.join(
        digest(path) + '  ' + path.relative_to(DIST).as_posix() + '\n' for path in checksum_files), encoding='utf-8')
    for item in read_json(DIST / 'release-manifest.json')['files']:
        require(digest(DIST / item['path']) == item['sha256'], 'Published payload hash mismatch: ' + item['path'])
    print(json.dumps({'apk': str(apk_target), 'tests': tests['tests'], 'lint': lint['status'],
                      'payload_files': len(payload), 'source_tree_sha256': source_snapshot['source_tree_sha256']}, indent=2))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sdk-root', type=Path,
                        default=Path(os.environ.get('ANDROID_SDK_ROOT') or os.environ.get('ANDROID_HOME')
                                     or (Path.home() / 'AppData' / 'Local' / 'Android' / 'Sdk')))
    args = parser.parse_args()
    package_release(args.sdk_root)


if __name__ == '__main__':
    try:
        main()
    except (RuntimeError, ValueError, KeyError, OSError, ET.ParseError) as error:
        print('Release packaging stopped: ' + str(error), file=sys.stderr)
        raise SystemExit(1)
