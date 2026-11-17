"""The development RPM: what dev_rpm.py writes into the spec and what it refuses to package.
Builds nothing -- rpmbuild is exercised by CI's Fedora smoke test, not by a unit test."""
from pathlib import Path
import sys
import tempfile
import unittest

HERE = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(HERE))
import dev_rpm


def app_image(root: Path, native=('libplayer_bridge.so', 'libmpv.so.2')) -> Path:
    (root / 'bin').mkdir(parents=True)
    (root / 'bin' / 'nuvio').write_text('#!/bin/sh\nexit 0\n')
    native_dir = root / 'lib' / 'native'
    native_dir.mkdir(parents=True)
    for name in native:
        (native_dir / name).write_bytes(b'\x7fELF')
    return root


def spec_tree(root: Path) -> Path:
    (root / 'SPECS').mkdir(parents=True, exist_ok=True)
    return root


class DevRpmTest(unittest.TestCase):
    def test_spec_keeps_bundled_sonames_out_of_provides_and_requires(self):
        with tempfile.TemporaryDirectory() as directory:
            top = spec_tree(Path(directory))
            spec = dev_rpm.write_spec(top, '1.41.0', {'libmpv.so.2', 'libplayer_bridge.so'}, 'nuvio')
            text = spec.read_text()
        self.assertIn('__requires_exclude ^(libmpv[.]so[.]2|libplayer_bridge[.]so)([(]|$)', text)
        self.assertIn('__provides_exclude_from ^/opt/nuvio-desktop/lib/(native|app)/.*$', text)
        # rpmbuild strips backslashes in a spec, so the pattern must not rely on them.
        self.assertNotIn('\\\\', text)

    def test_spec_packages_the_release_layout_owned_by_root(self):
        with tempfile.TemporaryDirectory() as directory:
            spec = dev_rpm.write_spec(spec_tree(Path(directory)), '1.41.0', set(), 'nuvio')
            text = spec.read_text()
        self.assertIn('ln -s /opt/nuvio-desktop/bin/nuvio %{buildroot}/usr/bin/nuvio-desktop', text)
        self.assertIn('%defattr(-,root,root,-)', text)
        self.assertIn('/usr/share/applications/com.nuvio.media.desktop.desktop', text)
        self.assertIn('/usr/share/icons/hicolor/512x512/apps/com.nuvio.media.desktop.png', text)
        self.assertTrue(text.rstrip().endswith('%changelog'))

    def test_spec_requires_the_fedora_names_it_does_not_bundle(self):
        with tempfile.TemporaryDirectory() as directory:
            spec = dev_rpm.write_spec(spec_tree(Path(directory)), '1.41.0', set(), 'nuvio')
            text = spec.read_text()
        for name in ['webkit2gtk4.1', 'gstreamer1-plugins-good', 'gstreamer1-plugin-libav', 'glib-networking']:
            self.assertIn('Requires: ' + name + '\n', text)
        # mpv-libs is deliberately absent: the image carries its own libmpv beside the bridge.
        self.assertNotIn('Requires: mpv-libs', text)

    def test_an_image_without_the_player_is_refused(self):
        with tempfile.TemporaryDirectory() as directory:
            image = app_image(Path(directory), native=('libquickjs.so',))
            with self.assertRaisesRegex(ValueError, 'libplayer_bridge.so'):
                dev_rpm.check_app_image(image, 'nuvio')

    def test_an_image_without_a_launcher_is_refused(self):
        with tempfile.TemporaryDirectory() as directory:
            image = Path(directory)
            with self.assertRaisesRegex(ValueError, 'No app image'):
                dev_rpm.check_app_image(image, 'nuvio')

    def test_a_complete_image_passes(self):
        with tempfile.TemporaryDirectory() as directory:
            dev_rpm.check_app_image(app_image(Path(directory)), 'nuvio')


if __name__ == '__main__':
    unittest.main()
