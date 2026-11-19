#!/usr/bin/env python3
"""A local Flatpak around the jpackage app image, for development.

The release Flatpak (formats/flatpak.py, `build.py --format flatpak`) wraps the GraalVM native
image and its evidence, and is the bundle verify.py and CI require. This is the fast host-side
counterpart next to dev_appimage.py and dev_rpm.py: it wraps the JVM app image that
createReleaseDistributable leaves in composeApp/build/compose/binaries/main-release/app/nuvio,
using the already-installed pinned GNOME runtime. No native image, no private WebKit build.

Two differences follow, and they are why nothing here is named or checked like a release artifact:

  * the image ships the JDK runtime and the application JARs, so the bundle is larger;
  * there is no native-image smoke evidence and no release verification (`verify.py` has no dev
    format). The Flatpak still uses the GNOME runtime's WebKit, and carries the same launcher
    layout, desktop entry, icon and sandbox permissions as the release bundle.

It deliberately shares the installed app's `~/.config/nuviohtpc` profile (via `XDG_CONFIG_HOME` and
a scoped Flatpak filesystem permission), so a dev bundle opens with the same login, settings and
account data as the regular app.

Usage:

  python3 tools/linux/packaging/dev_flatpak.py \
      --app-image composeApp/build/compose/binaries/main-release/app/nuvio \
      --version 1.42.0 --work-dir composeApp/build/flatpak-linux \
      --output composeApp/build/flatpak-linux/Nuvio-Desktop-1.42.0-dev-x86_64.flatpak
"""
import argparse
import datetime
import json
import os
import pathlib
import re
import shutil
import subprocess
import sys

HERE = pathlib.Path(__file__).resolve().parent
REPO = HERE.parents[2]
sys.path.insert(0, str(HERE))

from fetch import CONFIG  # noqa: E402

DESKTOP_ENTRY = HERE / 'com.nuvio.media.desktop.desktop'
RUNTIME_REPO = json.loads((HERE / 'flatpak-repo/repo.json').read_text())['runtime_repo']


def run(*args, **kwargs):
    return subprocess.run([str(a) for a in args], check=True, **kwargs)


def grant_input_device(metadata: pathlib.Path) -> None:
    """Controllers are read from /dev/input; older Flatpak clients ignore an unknown device."""
    text = metadata.read_text()
    granted, count = re.subn(r'(?m)^devices=dri;$', 'devices=dri;input;', text)
    if count != 1:
        raise ValueError('Expected one devices=dri; line in the Flatpak metadata')
    metadata.write_text(granted)


def check_app_image(app_image: pathlib.Path, launcher: str) -> None:
    binary = app_image / 'bin' / launcher
    if not app_image.is_dir() or not binary.is_file():
        raise ValueError(
            f'No app image at {app_image} (expected {binary}). Build it first: '
            './gradlew :composeApp:createReleaseDistributable')
    native = app_image / 'lib' / 'native'
    for name in ('libplayer_bridge.so', 'libmpv.so.2', 'libquickjs.so'):
        if not (native / name).is_file():
            raise ValueError(
                f'The app image carries no {name} in lib/native; it would start and play nothing.')


def launcher_text() -> str:
    return '''#!/bin/sh
set -eu
unset LD_PRELOAD
# Development bundles share the installed app's profile instead of a sandbox-only one.
export XDG_CONFIG_HOME="$HOME/.config"
exec /app/lib/nuvio-desktop/bin/nuvio "$@"
'''


def build(app_image: pathlib.Path, version: str, work: pathlib.Path, output: pathlib.Path,
          java: str, icon_source: pathlib.Path, launcher: str) -> None:
    if any(os.environ.get(key) for key in ('DISPLAY', 'WAYLAND_DISPLAY',
                                           'NUVIO_RUN_LIVE_DISPLAY_TESTS')):
        raise ValueError('Unset display/live-test environment before packaging')
    check_app_image(app_image, launcher)

    app_id = CONFIG['application_id']
    runtime = CONFIG['flatpak_runtime']
    branch = CONFIG['flatpak_branch']
    expected_commit = CONFIG['flatpak_runtime_commit']
    installed = subprocess.check_output(
        ['flatpak', 'info', '--show-commit', f'{runtime}//{branch}'], text=True).strip()
    if installed != expected_commit:
        raise ValueError(
            f'Installed {runtime}//{branch} is {installed}, expected {expected_commit}; '
            'install the pinned runtime before packaging')

    root = work / 'flatpak-build'
    shutil.rmtree(root, ignore_errors=True)
    work.mkdir(parents=True, exist_ok=True)
    subprocess.run(
        ['flatpak', 'build-init', str(root), app_id, runtime, runtime, branch], check=True)

    image = root / 'files' / 'lib' / 'nuvio-desktop'
    shutil.copytree(app_image, image, symlinks=True)
    # Use the runtime's loader and driver, never a second Vulkan loader.
    for path in (image / 'lib' / 'native').glob('libvulkan.so*'):
        path.unlink()

    bin_dir = root / 'files' / 'bin'
    bin_dir.mkdir(parents=True)
    entrypoint = bin_dir / 'nuvio-desktop'
    entrypoint.write_text(launcher_text())
    entrypoint.chmod(0o755)

    applications = root / 'files' / 'share' / 'applications'
    applications.mkdir(parents=True)
    shutil.copy2(DESKTOP_ENTRY, applications / DESKTOP_ENTRY.name)
    icons = root / 'files' / 'share' / 'icons' / 'hicolor' / '512x512' / 'apps'
    icons.mkdir(parents=True)
    run(java, '-Djava.awt.headless=true', HERE / 'ResizeIcon.java', icon_source,
        icons / f'{app_id}.png')

    permissions = json.loads((HERE / 'flatpak-permissions.json').read_text())
    permissions.append('--filesystem=xdg-config/nuviohtpc:create')
    subprocess.run(
        ['flatpak', 'build-finish', '--command=nuvio-desktop', *permissions, str(root)], check=True)
    grant_input_device(root / 'metadata')

    repository = work / 'flatpak-repo'
    export_branch = 'nightly'
    timestamp = datetime.datetime.now(datetime.timezone.utc).replace(microsecond=0).isoformat()
    subprocess.run(
        ['flatpak', 'build-export', '--timestamp=' + timestamp, str(repository), str(root),
         export_branch], check=True)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.unlink(missing_ok=True)
    subprocess.run(
        ['flatpak', 'build-bundle', '--arch=x86_64', '--runtime-repo=' + RUNTIME_REPO,
         str(repository), str(output), app_id, export_branch], check=True)

    provenance = {
        'application_id': app_id,
        'version': version,
        'channel': 'nightly',
        'development': True,
        'runtime': runtime,
        'runtime_branch': branch,
        'runtime_commit': installed,
        'source_commit': subprocess.check_output(
            ['git', 'rev-parse', 'HEAD'], cwd=REPO, text=True).strip(),
    }
    (work / 'flatpak-provenance.json').write_text(json.dumps(provenance, indent=2) + '\n')


def main() -> None:
    parser = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--app-image', required=True, help='jpackage app image to wrap')
    parser.add_argument('--version', required=True, help='version stamped into the bundle')
    parser.add_argument('--output', required=True, help='the .flatpak file to write')
    parser.add_argument('--work-dir', required=True, help='Flatpak build tree')
    parser.add_argument('--java', required=True, help='java executable for icon resizing')
    parser.add_argument('--icon-source', required=True, help='source application icon')
    parser.add_argument('--launcher', default='nuvio', help='launcher inside the app image')
    args = parser.parse_args()
    build(pathlib.Path(args.app_image).resolve(), args.version, pathlib.Path(args.work_dir).resolve(),
          pathlib.Path(args.output).resolve(), args.java, pathlib.Path(args.icon_source).resolve(),
          args.launcher)


if __name__ == '__main__':
    main()
