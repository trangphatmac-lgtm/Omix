"""Exercise archive routing with Windows install artifacts on any build host."""
import contextlib
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile


spec = importlib.util.spec_from_file_location('bundle', Path(__file__).resolve().parents[1] / 'scripts/bundle.py')
bundle = importlib.util.module_from_spec(spec)
spec.loader.exec_module(bundle)


class BundleTest(unittest.TestCase):
    def test_current_platform_detection(self):
        for system, machine, expected in [
            ('Windows', 'AMD64', 'windows-x64'), ('Windows', 'ARM64', 'windows-arm64'),
            ('Darwin', 'x86_64', 'macos-x64'), ('Darwin', 'arm64', 'macos-arm64'),
            ('Linux', 'x86_64', 'linux-x64'), ('Linux', 'aarch64', 'linux-arm64'),
        ]:
            with self.subTest(expected=expected), patch.object(bundle.platform, 'system', return_value=system), patch.object(bundle.platform, 'machine', return_value=machine):
                self.assertEqual({expected}, bundle.selected_platforms('current'))
        with patch.object(bundle.platform, 'system', return_value='Linux'), patch.object(bundle.platform, 'machine', return_value='riscv64'):
            with self.assertRaises(ValueError):
                bundle.selected_platforms('current')
        with self.assertRaises(ValueError):
            bundle.selected_platforms('invalid')

    def test_host_artifacts_do_not_change_platform_archives(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)

            def write(name, data=b'fixture'):
                path = root / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(data)

            for name in ['launch.mjs', 'omix.patch.yml', 'package.json']:
                write(name)
            write('package-lock.json', b'{"packages": {}}')
            write('node_modules/node-pty/lib/index.js')
            prebuilds = {}
            for target, (system, arch) in bundle.PLATFORMS.items():
                files = ['conpty.node', 'conpty/conpty.dll', 'conpty/OpenConsole.exe'] if system == 'win32' else ['pty.node', 'spawn-helper']
                prebuilds[target] = {f'node_modules/node-pty/prebuilds/{system}-{arch}/{name}' for name in files}
                for name in prebuilds[target]:
                    write(name, target.encode())

            with patch.multiple(bundle, ROOT=root, OUT=root / 'dist', CACHE=root / 'cache'), contextlib.redirect_stdout(io.StringIO()):
                bundle.main('all')
                baseline = json.loads((root / 'dist/manifest.json').read_text())
                for name in ['Release/conpty/OpenConsole.exe', 'Release/conpty/conpty.dll', 'Release/conpty.node', 'Debug/pty.node', 'Release/spawn-helper']:
                    write('node_modules/node-pty/build/' + name, b'host-only')
                bundle.main('all')
                self.assertEqual(baseline, json.loads((root / 'dist/manifest.json').read_text()))

                for target in ['common', *bundle.PLATFORMS]:
                    with zipfile.ZipFile(root / 'dist' / (target + '.zip')) as archive:
                        names = set(archive.namelist())
                        self.assertFalse(any('/node-pty/build/' in name for name in names))
                        for owner, expected in prebuilds.items():
                            self.assertEqual(names & expected, expected if owner == target else set())

                # Test all -> one -> another -> all without cleaning the output directory.
                # Shared and selected archives must retain their all-platform hashes.
                for target in bundle.PLATFORMS:
                    bundle.main(target)
                    manifest = json.loads((root / 'dist/manifest.json').read_text())
                    expected = {'common.zip', target + '.zip'}
                    self.assertEqual(expected, set(manifest['archives']))
                    self.assertEqual(expected, {p.name for p in (root / 'dist').glob('*.zip')})
                    self.assertEqual(manifest['archives'], {name: baseline['archives'][name] for name in expected})
                bundle.main('all')
                self.assertEqual(baseline, json.loads((root / 'dist/manifest.json').read_text()))

                # Unselected optional dependencies must not even be downloaded.
                packages = {
                    f'node_modules/optional-{name}': {'os': [system], 'cpu': [arch]}
                    for name, (system, arch) in bundle.PLATFORMS.items()
                }
                write('package-lock.json', json.dumps({'packages': packages}).encode())
                with patch.object(bundle, 'locked_package', return_value=[]) as download, patch.object(bundle.platform, 'system', return_value='Windows'), patch.object(bundle.platform, 'machine', return_value='AMD64'):
                    bundle.main()
                    self.assertEqual(['node_modules/optional-windows-x64'], [call.args[0][0] for call in download.call_args_list])

                # Keep rejecting unknown binaries outside node-pty's host build tree.
                write('package-lock.json', b'{"packages": {}}')
                write('node_modules/unknown/addon.node')
                with self.assertRaisesRegex(RuntimeError, 'Unattributed native binary'):
                    bundle.main('all')


if __name__ == '__main__':
    unittest.main()
