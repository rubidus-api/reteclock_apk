#!/usr/bin/env python3
"""Adds one file to an existing zip (APK) archive.

Usage: scripts/add-to-zip.py <archive> <source-file> <name-in-archive>

The build needs this because aapt2 links resources and the manifest into an APK but cannot add
classes.dex, and the `zip` command line tool is not present on every build host. Python 3 is.
"""

import sys
import zipfile


def main(argv):
    if len(argv) != 4:
        print(__doc__.strip(), file=sys.stderr)
        return 2
    archive, source, name = argv[1], argv[2], argv[3]
    with zipfile.ZipFile(archive, "a", compression=zipfile.ZIP_DEFLATED) as zf:
        zf.write(source, name)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
