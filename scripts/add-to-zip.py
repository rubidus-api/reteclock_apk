#!/usr/bin/env python3
"""Adds one file to an existing zip (APK) archive.

Usage: scripts/add-to-zip.py <archive> <source-file> <name-in-archive>

The build needs this because aapt2 links resources and the manifest into an APK but cannot add
classes.dex, and the `zip` command line tool is not present on every build host. Python 3 is.

The entry is stamped with the zip epoch, 1980-01-01, which is what aapt2 already writes for
everything it puts in the archive. zipfile.write would otherwise copy the file's modification time,
which is the moment the build ran, and two builds of the same source would differ.
"""

import sys
import zipfile

# The earliest date the zip format can store. aapt2 uses it for the entries it writes, so using it
# here keeps every entry in the APK on the same fixed date.
ZIP_EPOCH = (1980, 1, 1, 0, 0, 0)


def main(argv):
    if len(argv) != 4:
        print(__doc__.strip(), file=sys.stderr)
        return 2
    archive, source, name = argv[1], argv[2], argv[3]
    info = zipfile.ZipInfo(filename=name, date_time=ZIP_EPOCH)
    info.compress_type = zipfile.ZIP_DEFLATED
    # zipfile.write would take these from the file on disk; ZipInfo starts them empty.
    info.external_attr = 0o644 << 16
    with open(source, "rb") as f:
        data = f.read()
    with zipfile.ZipFile(archive, "a", compression=zipfile.ZIP_DEFLATED) as zf:
        zf.writestr(info, data)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
