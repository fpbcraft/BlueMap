#!/usr/bin/env python3
"""Check the actual published jar metadata, not only the asset filename."""
import sys
import tomllib
import zipfile
from pathlib import Path

from release_version import compatible_release


def verify(path: Path, expected: str) -> None:
    expected = compatible_release(expected)
    with zipfile.ZipFile(path) as jar:
        metadata = tomllib.loads(jar.read("META-INF/neoforge.mods.toml").decode())
    mods = [mod for mod in metadata.get("mods", []) if mod.get("modId") == "bluemap"]
    if len(mods) != 1 or mods[0].get("version") != expected:
        raise ValueError(f"BlueMap mod metadata must declare {expected}")


if __name__ == "__main__":
    verify(Path(sys.argv[1]), sys.argv[2])
    print(f"Verified addon-compatible BlueMap {sys.argv[2]} metadata")
