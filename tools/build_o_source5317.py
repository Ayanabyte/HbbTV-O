#!/usr/bin/env python3
"""Reproduce the 5.3.17 HbbTV source workflow in a fresh checkout.

This deliberately builds the actual uploaded/source-matched runtime and
TCL integration, not the earlier O! laboratory reimplementations.

Source reference is immutable and verified in the GitHub Action checkout.
Original source notices/licenses remain intact in the staged build.
Only the Android application ID and visible label of the test variant change.
"""
from __future__ import annotations
import hashlib
import os
import pathlib
import re
import subprocess
import sys

import yaml

ROOT = pathlib.Path(__file__).resolve().parents[1]
SOURCE = ROOT / "source5317"
EXPECTED_STEPS = [
    "Overlay exact 5.3.7 LOVEStv source changes",
    "Fix Channel+ startup regression in TCL 32-bit process",
    "Repair HbbTV remote and observable startup",
    "Native LOVEStv 1.45 autostart parity (without coloured key)",
    "Enable native HbbTV autostart for every full-screen TCL TDT tune",
    "Replace experimental browser surface with LOVEStv 1.45 Activity view",
    "Restore tested LOVEStv 1.45 ad skip and progressive seek",
    "Restore reliable LOVEStv remote scanning and return to TDT",
    "Validate unreleased Channel+ 5.3.15 UX and timeshift fixes",
    "Repair repeated LOVEStv launches on the same TDT channel",
]
OVERLAY_SHA = "fea649552eb21f7a5df958a20dbb1cf6915b186b701a346aca498be434b8173b"

def replace_exact(text: str, old: str, new: str, target: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{target}: expected one {old!r}; got {count}")
    return text.replace(old, new)

def main() -> None:
    if not SOURCE.is_dir():
        raise RuntimeError("Pinned 5.3.17 checkout is missing")
    base = SOURCE / "ci/channelplus-5.3.7-overlay.zip"
    digest = hashlib.sha256(base.read_bytes()).hexdigest()
    if digest != OVERLAY_SHA:
        raise RuntimeError(f"Unexpected HbbTV source overlay checksum {digest}")
    workflow_file = SOURCE / ".github/workflows/channelplus-537-lovestv.yml"
    workflow = yaml.safe_load(workflow_file.read_text(encoding="utf-8"))
    steps = workflow["jobs"]["hbbtv32"]["steps"]
    actual_names = [step.get("name") for step in steps[1:11]]
    if actual_names != EXPECTED_STEPS:
        raise RuntimeError(f"Upstream source workflow changed unexpectedly: {actual_names}")

    for index, step in enumerate(steps[1:11], start=1):
        print(f"\n=== ORIGINAL SOURCE STEP {index}/10: {step['name']} ===", flush=True)
        subprocess.run(["bash", "-e", "-o", "pipefail", "-c", step["run"]],
            cwd=SOURCE, check=True)

    # Only packaging identity changes. Keep internal source class names as
    # provided: renaming them risks breaking the working integration.
    gradle_path = SOURCE / "owntv_tdt/app/build.gradle.kts"
    gradle = gradle_path.read_text(encoding="utf-8")
    gradle = replace_exact(
        gradle, 'applicationId = "tv.own.owntv.tdt2"',
        'applicationId = "com.ayanabyte.o.lovestv.src5317"',
        str(gradle_path))
    gradle_path.write_text(gradle, encoding="utf-8")
    label_path = SOURCE / "owntv_tdt/app/src/main/res/values/strings_tuner.xml"
    label = label_path.read_text(encoding="utf-8")
    label = replace_exact(label, '<string name="tdt_app_name">Channel +</string>',
        '<string name="tdt_app_name">O! LOVEStv · Fuente 5.3.17</string>',
        str(label_path))
    label_path.write_text(label, encoding="utf-8")
    target = SOURCE / "owntv_tdt/app/src/hbbtv32/AndroidManifest.xml"
    manifest = target.read_text(encoding="utf-8")
    if '<application android:process="com.tcl.tv"' not in manifest:
        raise RuntimeError("Source no longer has TCL 32-bit process")
    if not (SOURCE / "owntv_tdt/app/libs/lovestv-runtime.jar").is_file():
        raise RuntimeError("Original 5.3.17 runtime JAR was not restored")
    if not (SOURCE / "owntv_tdt/app/src/main/jniLibs/armeabi-v7a/libsectionprobe.so").is_file():
        raise RuntimeError("Original native section bridge missing")
    if not (SOURCE / "owntv_tdt/app/src/main/java/tv/own/channelplus/lovestvprobe/HbbTvRuntimeService.java").is_file():
        raise RuntimeError("Original 5.3.17 HbbTV integration missing")
    print("\nPASS: built from unmodified HbbTV source routines with standalone O! app ID", flush=True)
    print("Source classes and bundled libraries retained for functional fidelity.", flush=True)

if __name__ == "__main__":
    main()
