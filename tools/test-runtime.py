#!/usr/bin/env python3
"""Offline runtime regressions and aapt2 validation of the standalone manifest."""
import argparse
from pathlib import Path
import subprocess
import tempfile
import zipfile
from standalone_manifest import decorate

ROOT = Path(__file__).resolve().parent.parent
parser = argparse.ArgumentParser()
parser.add_argument("--java-home", type=Path, required=True)
parser.add_argument("--sdk", type=Path, default=Path.home() / "Android/Sdk")
args = parser.parse_args()
android = args.sdk / "platforms/android-37.0/android.jar"
sdk_tools = sorted((args.sdk / "build-tools").iterdir())[-1]
java = args.java_home / "bin/java"


def run(*command):
    return subprocess.run(list(map(str, command)), check=True, text=True, capture_output=True)


with tempfile.TemporaryDirectory(prefix="katten-runtime-test-") as temporary:
    work = Path(temporary)
    classes = work / "classes"
    tests = ["AvatarSettingsTest", "StudentCardDisplayTest", "UpdatePolicyTest", "StreakTest", "CoverScaleTest", "CustomCatsTest"]
    result = run(args.java_home / "bin/javac", "--release", "11", "-cp", android, "-d", classes,
                 *sorted((ROOT / "patch-runtime/src").rglob("*.java")), *(ROOT / f"tools/{name}.java" for name in tests))
    for test in tests:
        print(run(java, "-cp", f"{classes}:{android}", test).stdout.strip())
    manifest = work / "fixture.xml"
    manifest.write_text('''<manifest xmlns:android="http://schemas.android.com/apk/res/android"
        package="be.smartschoolkatten.mobile" android:versionCode="120300" android:versionName="12.3.0">
        <uses-sdk android:minSdkVersion="32" android:targetSdkVersion="37"/>
        <uses-permission android:name="android.permission.INTERNET"/>
        <application android:label="Smartschool Katten"><activity android:name="example.HostActivity" android:exported="false"/></application>
        </manifest>''')
    original = work / "original.apk"
    run(sdk_tools / "aapt2", "link", "-I", android, "--manifest", manifest, "-o", original)
    updated = work / "standalone.apk"
    with zipfile.ZipFile(original) as source, zipfile.ZipFile(updated, "w") as output:
        for item in source.infolist():
            data = source.read(item)
            output.writestr(item, decorate(data, 45) if item.filename == "AndroidManifest.xml" else data)
    xml = run(sdk_tools / "aapt2", "dump", "xmltree", updated, "--file", "AndroidManifest.xml").stdout
    badging = run(sdk_tools / "aapt2", "dump", "badging", updated).stdout
    for expected in ("be.smartschoolkatten.runtime.UpdateActivity", "android.permission.REQUEST_INSTALL_PACKAGES", "example.HostActivity"):
        assert expected in xml, f"Manifest lost {expected}"
    assert "versionCode='120300045'" in badging and "versionName='12.3.0-katten.r45'" in badging
    assert xml.count("E: activity") == 2 and xml.count("exported(0x01010010)=false") == 2, xml
    # A base APK requiring missing configuration APKs must never masquerade as standalone.
    manifest.write_text(manifest.read_text().replace('package="be.smartschoolkatten.mobile"', 'package="be.smartschoolkatten.mobile" android:requiredSplitTypes="base__abi"'))
    run(sdk_tools / "aapt2", "link", "-I", android, "--manifest", manifest, "-o", original)
    with zipfile.ZipFile(original) as source:
        try:
            decorate(source.read("AndroidManifest.xml"), 45)
        except ValueError as failure:
            assert "split" in str(failure)
        else:
            raise AssertionError("Incomplete split base accepted as a standalone APK")
    print("PASS standalone manifest: host components retained, updater private, install permission, increasing version code, incomplete split bases rejected")
