#!/usr/bin/env python3
"""Write the MDI icon path data the widgets draw, from @mdi/svg 7.4.47.

The names are the icons of the dashboard's `calendar_icons` (the dashboard's
file is named in app/src/main/kotlin/dev/cupr/calendar/render/PhoneMonth.kt),
the icons Home Assistant names for an entry's kind, plus the widgets' own
chevron-right and plus. Each line of the output is `<name> <path data>`; the
app reads it at run time (class Icons in render/Assets.kt). The package's
licence goes to assets/licenses/mdi-LICENSE.txt.

Run from the repository root:  python3 scripts/generate_mdi_icons.py
"""

import base64
import hashlib
import io
import re
import sys
import tarfile
import urllib.request
from pathlib import Path

VERSION = "7.4.47"
TARBALL = f"https://registry.npmjs.org/@mdi/svg/-/svg-{VERSION}.tgz"
# The registry's `dist.integrity` for this version.
INTEGRITY = "sha512-WQ2gDll12T9WD34fdRFgQVgO8bag3gavrAgJ0frN4phlwdJARpE6gO1YvLEMJR0KKgoc+/Ea/A0Pp11I00xBvw=="

CALENDAR_ICONS = [
    "cake-variant", "paw", "tooth-outline", "needle", "glasses", "heart-pulse",
    "hospital-building", "stethoscope", "account-group", "sleep",
    "baby-face-outline", "ring", "church", "skate", "ski", "run", "bicycle",
    "yoga", "dumbbell", "swim", "soccer", "gymnastics", "dance-ballroom",
    "karate", "carabiner", "horse", "table-tennis", "tennis", "badminton",
    "basketball", "golf", "music", "dice-multiple", "microphone-variant",
    "drama-masks", "filmstrip", "palette", "book-open-variant", "tent",
    "hiking", "silverware-fork-knife", "grill", "coffee", "chef-hat", "bed",
    "briefcase", "laptop", "bag-suitcase", "beach", "cart", "trash-can",
    "broom", "car-wrench", "washing-machine", "hammer-wrench",
    "account-hard-hat", "party-popper", "shoe-sneaker", "bag-personal",
    "compass-outline", "teddy-bear", "package-variant", "content-cut",
    "office-building", "flower-outline", "airplane", "train", "puzzle",
    "home-heart",
]
WIDGET_ICONS = ["chevron-right", "plus"]

ASSETS = Path("app/src/main/assets")
OUTPUT = ASSETS / "icons" / "mdi.txt"
LICENSE = ASSETS / "licenses" / "mdi-LICENSE.txt"


def download() -> bytes:
    with urllib.request.urlopen(TARBALL, timeout=60) as response:
        data = response.read()
    algorithm, expected = INTEGRITY.split("-", 1)
    actual = base64.b64encode(hashlib.new(algorithm, data).digest()).decode()
    if actual != expected:
        sys.exit(f"integrity mismatch for {TARBALL}: {actual}")
    return data


def main() -> None:
    names = CALENDAR_ICONS + WIDGET_ICONS
    if len(set(names)) != len(names):
        sys.exit("duplicate icon names")
    with tarfile.open(fileobj=io.BytesIO(download()), mode="r:gz") as archive:
        def read(member: str) -> str:
            extracted = archive.extractfile(member)
            if extracted is None:
                sys.exit(f"{member} missing from the package")
            return extracted.read().decode()

        lines = []
        for name in names:
            paths = re.findall(r'<path d="([^"]+)"', read(f"package/svg/{name}.svg"))
            if len(paths) != 1:
                sys.exit(f"{name}: expected one path, found {len(paths)}")
            lines.append(f"{name} {paths[0]}")
        license_text = read("package/LICENSE")
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(f"# @mdi/svg {VERSION}, written by scripts/generate_mdi_icons.py\n"
                      + "\n".join(lines) + "\n")
    LICENSE.parent.mkdir(parents=True, exist_ok=True)
    LICENSE.write_text(license_text)
    print(f"{OUTPUT}: {len(lines)} icons")


if __name__ == "__main__":
    main()
