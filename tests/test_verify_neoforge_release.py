import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from verify_neoforge_release import verify


class MetadataTest(unittest.TestCase):
    def test_rejects_a_mislabeled_jar_even_when_its_filename_looks_compatible(self):
        with tempfile.TemporaryDirectory() as root:
            path = Path(root) / "bluemap-5.7.1-neoforge.jar"
            for version in ["6.0.0", "5.7.1"]:
                with zipfile.ZipFile(path, "w") as jar:
                    jar.writestr("META-INF/neoforge.mods.toml", f'[[mods]]\nmodId="bluemap"\nversion="{version}"\n')
                if version == "6.0.0":
                    with self.assertRaisesRegex(ValueError, "metadata"):
                        verify(path, "5.7.1")
                else:
                    verify(path, "5.7.1")


if __name__ == "__main__":
    unittest.main()
