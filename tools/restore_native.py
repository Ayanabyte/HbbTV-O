#!/usr/bin/env python3
"""Restore version-pinned native bridges from the supplied, user-owned APK.

The checked-in files are zlib-compressed, base64-encoded stripped ARM32 ELF
images with neutral JNI symbol names. Their hashes are checked before Gradle.
No credentials or signing keys are included.
"""
import base64
import hashlib
from pathlib import Path
import zlib

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "tools/native-assets"
DEST = ROOT / "app/src/main/jniLibs/armeabi-v7a"

EXPECTED = {
    "libosection.so": (
        ["libosection.so.part1", "libosection.so.part2"],
        "441ec8e23ca67b0412a935ce192ee18e6a91725c91c0d19abad244f32e8a287d"
    ),
    "liboua.so": (
        ["liboua.so.zlib64"],
        "4279ffd0f423a69cbc028694f478e2a235eba6814d9870d0c1391a60c01b9391"
    ),
}

def main():
    DEST.mkdir(parents=True, exist_ok=True)
    for name, (parts, expected) in EXPECTED.items():
        encoded = "".join((SOURCE / part).read_text().strip() for part in parts)
        image = zlib.decompress(base64.b64decode(encoded, validate=True))
        digest = hashlib.sha256(image).hexdigest()
        if digest != expected:
            raise RuntimeError(f"{name}: SHA-256 mismatch; refusing to package")
        if not image.startswith(b"\x7fELF") or image[4] != 1:
            raise RuntimeError(f"{name}: not a 32-bit ELF")
        if b"Java_tv_own_" in image:
            raise RuntimeError(f"{name}: outdated JNI symbol present")
        (DEST / name).write_bytes(image)
        print(f"Restored {name}, {len(image)} bytes, SHA-256 {digest}")

if __name__ == "__main__":
    main()
