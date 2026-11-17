#!/usr/bin/env python3
"""A local Fedora RPM around the jpackage app image, for development.

The release RPM (formats.build_rpm, `build.py --format rpm`) wraps the GraalVM native image, runs
inside the pinned Ubuntu builder and is the artifact verify.py and CI require. This is the fast
host-side counterpart next to dev_appimage.py: it wraps the *JVM* app image that
createReleaseDistributable leaves in composeApp/build/compose/binaries/main-release/app/nuvio, and
needs nothing but the host's own rpmbuild -- seconds to a couple of minutes, not hours.

Three differences follow, and they are why nothing here is named or checked like a release artifact:

  * the image ships the JDK runtime and the application JARs, so the package is larger and the
    launcher is the JVM one; it uses the host's WebKitGTK rather than a bundled private engine;
  * it is built by the host's rpmbuild (a Fedora/Bazzite host already has it) instead of the
    digest-pinned builder, so the package is host-versioned in its file listing;
  * the layout, the entry point, the desktop entry and the icon deliberately match the release
    package: /opt/nuvio-desktop, /usr/bin/nuvio-desktop, share/applications and
    hicolor/512x512/apps, the canonical .desktop file and a 512px icon resized offscreen.

The app image carries its player: createReleaseDistributable stages lib/native (libplayer_bridge.so,
libmpv.so.2, libquickjs.so) through composeApp/linux-native-runtime.gradle.kts, and those files are
copied here unchanged because NativePlayerBridge looks for them beside the runtime. An image without
them starts and never plays, so this script refuses to package one.

The Fedora dependency names follow Nuvio Desktop's own RPM packaging upstream: WebKitGTK 4.1 and the
GStreamer/GLib networking pieces the WebView needs, plus the Vulkan driver as a recommendation
because the player only renders through Vulkan. Everything else is resolved by rpmbuild's own ELF
SONAME scanning, and the SONAMEs this package carries itself (the bridge, libmpv, quickjs, skiko)
are excluded so they neither shadow nor require a distribution package.

Usage (the packageDevRpm Gradle task runs exactly this):

  python3 tools/linux/packaging/dev_rpm.py \
      --app-image composeApp/build/compose/binaries/main-release/app/nuvio \
      --version 1.41.0 --work-dir composeApp/build/rpm-linux \
      --output composeApp/build/rpm-linux/nuvio-desktop-1.41.0-dev-x86_64.rpm
"""
import argparse
import hashlib
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

# Non-ELF dependencies, in Fedora names. RPM derives the ELF ones itself from the packaged files.
# webkit2gtk4.1 is the WebView the JVM launcher uses from the host; the GStreamer plugins and
# glib-networking are the pieces a WebKitGTK video element needs at runtime. mpv-libs is absent on
# purpose: this package carries its own libmpv beside the bridge.
RPM_REQUIRES = [
    'ca-certificates',
    'xdg-utils',
    'hicolor-icon-theme',
    'webkit2gtk4.1',
    'gstreamer1-plugins-good',
    'gstreamer1-plugin-libav',
    'glib-networking',
]
RPM_RECOMMENDS = ['ffmpeg', 'mesa-vulkan-drivers']


def run(*args, **kwargs):
    return subprocess.run([str(a) for a in args], check=True, **kwargs)


def readelf_sonames(root: pathlib.Path) -> set:
    """SONAMEs of the shared objects this package carries itself."""
    names = set()
    for path in sorted(root.rglob('*')):
        if not path.is_file() or path.is_symlink():
            continue
        try:
            dynamic = subprocess.check_output(['readelf', '-d', str(path)], text=True,
                                              stderr=subprocess.DEVNULL)
        except (OSError, subprocess.CalledProcessError):
            continue
        match = re.search(r'\(SONAME\).*?\[(.*?)\]', dynamic)
        if match:
            names.add(match.group(1))
    return names


def check_app_image(app_image: pathlib.Path, launcher: str) -> None:
    binary = app_image / 'bin' / launcher
    if not app_image.is_dir() or not binary.is_file():
        raise ValueError(
            f'No app image at {app_image} (expected {binary}). Build it first: '
            './gradlew :composeApp:createReleaseDistributable')
    native = app_image / 'lib' / 'native'
    missing = [name for name in ('libplayer_bridge.so', 'libmpv.so.2') if not (native / name).is_file()]
    if missing:
        raise ValueError(
            f'{native} is missing {", ".join(missing)}; the packaged app would start and never play. '
            'Rebuild the image with ./gradlew :composeApp:createReleaseDistributable so '
            'linux-native-runtime.gradle.kts stages the player.')


def prepare_sources(work: pathlib.Path, app_image: pathlib.Path, icon_source: pathlib.Path,
                    java: str) -> pathlib.Path:
    top = work / 'rpmbuild'
    shutil.rmtree(top, ignore_errors=True)
    for directory in ['BUILD', 'BUILDROOT', 'RPMS', 'SOURCES', 'SPECS', 'SRPMS']:
        (top / directory).mkdir(parents=True, exist_ok=True)
    sources = top / 'SOURCES'
    shutil.copytree(app_image, sources / 'image', symlinks=True)
    shutil.copy2(DESKTOP_ENTRY, sources / DESKTOP_ENTRY.name)
    # Offscreen resize, the same one the release stage does: no Toolkit, window or display access.
    run(java, '-Djava.awt.headless=true', HERE / 'ResizeIcon.java', icon_source,
        sources / (CONFIG['application_id'] + '.png'))
    return top


def write_spec(top: pathlib.Path, version: str, private: set, launcher: str) -> pathlib.Path:
    requires = '\n'.join('Requires: ' + name for name in RPM_REQUIRES)
    recommends = '\n'.join('Recommends: ' + name for name in RPM_RECOMMENDS)
    # Private shared objects are resolved through the image's own paths, so they must neither
    # provide nor require the distribution's same-SONAME packages.
    exclusion = ''
    if private:
        # rpm's regex is POSIX ERE: dots go in a character class and the capability suffix in
        # another, so the spec file needs no backslash escapes (rpmbuild strips them).
        names = '|'.join(name.replace('.', '[.]') for name in sorted(private))
        pattern = f'^({names})([(]|$)'
        exclusion = ('%global __provides_exclude_from ^/opt/nuvio-desktop/lib/(native|app)/.*$\n'
                     '%global __requires_exclude ' + pattern + '\n')
    spec = top / 'SPECS/nuvio-desktop.spec'
    spec.write_text(f'''%global debug_package %{{nil}}
%global __os_install_post %{{nil}}
%global _build_id_links none
{exclusion}
Name: {CONFIG['package_name']}
Version: {version}
Release: 1
Summary: {CONFIG['display_name']} (development package)
License: Proprietary
URL: https://nuvio.tv
BuildArch: x86_64
{requires}
{recommends}
AutoReqProv: yes

%description
Integrated desktop media player, development package built from the jpackage application
image on this host. It ships a Java runtime and the application JARs, so it installs and
runs like the release package but is not a release artifact.

%prep

%build

%install
mkdir -p %{{buildroot}}/opt
cp -a %{{_sourcedir}}/image %{{buildroot}}/opt/nuvio-desktop
mkdir -p %{{buildroot}}/usr/bin %{{buildroot}}/usr/share/applications \\
  %{{buildroot}}/usr/share/icons/hicolor/512x512/apps
ln -s /opt/nuvio-desktop/bin/{launcher} %{{buildroot}}/usr/bin/nuvio-desktop
cp %{{_sourcedir}}/{DESKTOP_ENTRY.name} %{{buildroot}}/usr/share/applications/
cp %{{_sourcedir}}/{CONFIG['application_id']}.png \\
  %{{buildroot}}/usr/share/icons/hicolor/512x512/apps/

%files
%defattr(-,root,root,-)
/opt/nuvio-desktop
/usr/bin/nuvio-desktop
/usr/share/applications/{DESKTOP_ENTRY.name}
/usr/share/icons/hicolor/512x512/apps/{CONFIG['application_id']}.png

%changelog
''')
    return spec


def verify(rpm_path: pathlib.Path, private: set, launcher: str) -> None:
    """Check what a Fedora host needs from the package without installing it."""
    def query(*args):
        return subprocess.check_output(['rpm', *args, str(rpm_path)], text=True)

    files = query('-qlp').splitlines()
    requires = query('-qp', '--requires').splitlines()
    problems = []

    expected = ['/usr/bin/nuvio-desktop',
                '/usr/share/applications/' + DESKTOP_ENTRY.name,
                f'/usr/share/icons/hicolor/512x512/apps/{CONFIG["application_id"]}.png',
                '/opt/nuvio-desktop/lib/native/libplayer_bridge.so',
                '/opt/nuvio-desktop/lib/native/libmpv.so.2',
                f'/opt/nuvio-desktop/bin/{launcher}']
    problems += [f'missing {path}' for path in expected if path not in files]

    for line in query('-qlvp').splitlines():
        fields = line.split()
        if len(fields) > 4 and fields[0].startswith(('-', 'd', 'l')) and fields[2] != 'root':
            problems.append(f'not owned by root:root: {line}')
            break

    bundled = {name for name in private}
    problems += [f'requires the bundled library {name}' for name in sorted(bundled)
                 if any(name in line for line in requires)]
    problems += [f'missing dependency {name}' for name in RPM_REQUIRES
                 if not any(line.split(' ')[0].split('(')[0].split('[')[0] == name for line in requires)]

    if problems:
        raise SystemExit('RPM verification failed:\n  ' + '\n  '.join(problems))
    print(f'verified {rpm_path.name}: {len(files)} files, {len(requires)} requirements, '
          'root:root ownership, desktop entry and bundled player present')


def main() -> None:
    parser = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--app-image', required=True, help='jpackage app image to wrap')
    parser.add_argument('--version', required=True, help='version stamped into the RPM')
    parser.add_argument('--output', required=True, help='the .rpm file to write')
    parser.add_argument('--work-dir', required=True, help='rpmbuild tree')
    parser.add_argument('--launcher', default='nuvio', help='launcher name inside the app image')
    parser.add_argument('--java', default='java', help='java for the icon resize; no display access')
    parser.add_argument('--icon-source',
                        default=str(REPO / 'composeApp/src/desktopMain/resources/icons/nuvio-app-icon.png'))
    parser.add_argument('--source-date-epoch', type=int,
                        default=int(subprocess.check_output(
                            ['git', '-C', str(REPO), 'show', '-s', '--format=%ct', 'HEAD']).decode().strip()),
                        help='reproducibility timestamp (default: the commit time)')
    args = parser.parse_args()

    app_image = pathlib.Path(args.app_image).resolve()
    work = pathlib.Path(args.work_dir).resolve()
    output = pathlib.Path(args.output).resolve()
    icon_source = pathlib.Path(args.icon_source).resolve()
    if not icon_source.is_file():
        parser.error(f'icon source is missing: {icon_source}')
    if output.is_relative_to(HERE) or work.is_relative_to(output) or output.is_relative_to(REPO / '.git'):
        parser.error('Unsafe output path')
    if not re.fullmatch(r'[0-9][0-9A-Za-z._+~]*', args.version):
        parser.error(f'not an RPM-safe version: {args.version}')

    check_app_image(app_image, args.launcher)
    work.mkdir(parents=True, exist_ok=True)
    top = prepare_sources(work, app_image, icon_source, args.java)
    private = readelf_sonames(app_image / 'lib' / 'native') | readelf_sonames(app_image / 'lib' / 'app')
    spec = write_spec(top, args.version, private, args.launcher)
    run('rpmbuild', '-bb', '--define', '_topdir ' + str(top), spec,
        env=dict(os.environ, SOURCE_DATE_EPOCH=str(args.source_date_epoch)))
    built = sorted((top / 'RPMS/x86_64').glob('*.rpm'))
    if len(built) != 1:
        raise SystemExit('Expected exactly one built RPM, found ' + repr([p.name for p in built]))
    output.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(built[0], output)
    verify(output, private, args.launcher)
    print(f'{output}\n{output.stat().st_size / (1024 * 1024):.1f} MiB  '
          f'sha256 {hashlib.sha256(output.read_bytes()).hexdigest()}')


if __name__ == '__main__':
    main()
