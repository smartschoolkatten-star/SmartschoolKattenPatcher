#!/usr/bin/env python3
"""Compile real binary manifest fixtures; test complete sets, signing and ZIP alignment.

Fixtures are synthetic and are never presented as working Smartschool downloads.
"""
import argparse
from pathlib import Path
import subprocess
import shutil
import tempfile
import zipfile

parser = argparse.ArgumentParser()
parser.add_argument("--java-home", type=Path, required=True)
parser.add_argument("--sdk", type=Path, default=Path.home() / "Android/Sdk")
parser.add_argument("--source-apk", type=Path)
args = parser.parse_args()
root = Path(__file__).resolve().parent.parent
build_tools = sorted((args.sdk / "build-tools").iterdir())[-1]
android_jar = sorted((args.sdk / "platforms").glob("*/android.jar"))[-1]

with tempfile.TemporaryDirectory(prefix="katten-split-test-") as temporary:
    working = Path(temporary)
    for name, split, types, version in [
        ("base", "", "", 120300),
        ("abi", "config.arm64_v8a", "base__abi", 120300),
        ("density", "config.xxhdpi", "base__density", 120300),
        ("wrong", "config.nl", "base__locale", 120301),
    ]:
        attributes = (f' split="{split}" android:splitTypes="{types}"' if split else
                      ' android:requiredSplitTypes="base__abi,base__density"')
        manifest = working / f"{name}.xml"
        manifest.write_text(
            '<manifest xmlns:android="http://schemas.android.com/apk/res/android" '
            f'package="be.smartschool.mobile" android:versionCode="{version}" '
            f'android:versionName="12.3.0"{attributes}>'
            '<uses-sdk android:minSdkVersion="32" android:targetSdkVersion="37"/>'
            '<application android:hasCode="false"/></manifest>')
        subprocess.run([str(build_tools / "aapt2"), "link", "-I", str(android_jar),
                        "--manifest", str(manifest), "-o", str(working / f"{name}.apk")], check=True)
    with zipfile.ZipFile(working / "abi.apk", "a") as archive:
        archive.writestr("lib/arm64-v8a/libfixture.so", b"alignment-test" + bytes(15000))
    classes = working / "classes"
    classes.mkdir()
    subprocess.run([str(args.java_home / "bin/javac"), "--release", "11",
                    "-cp", str(root / "app/libs/*"), "-d", str(classes),
                    *map(str, (root / "app/src/main/java/be/smartschoolkatten/patcher/engine").glob("*.java")),
                    str(root / "tools/SplitSetTest.java"), str(root / "tools/PatchSetCli.java"),
                    str(root / "tools/PatchAssertions.java"), str(root / "tools/StartupSafetyTest.java"), str(root / "tools/ModelIntegrityTest.java"), str(root / "tools/UiHooksTest.java"), str(root / "tools/RuntimeLinkageTest.java"), str(root / "tools/LogoBrandingTest.java")], check=True)
    command = [str(args.java_home / "bin/java"), "-cp", f"{classes}:{root / 'app/libs/*'}",
               "be.smartschoolkatten.patcher.engine.SplitSetTest", str(working)]
    if args.source_apk:
        command.append(str(args.source_apk))
    subprocess.run(command, check=True)
    subprocess.run([str(build_tools / "zipalign"), "-c", "-P", "16", "4",
                    str(working / "signed-abi.apk")], check=True)
    print("PASS native ZIP alignment: 16 KiB (synthetic fixture)")
    if args.source_apk:
        subprocess.run([str(args.java_home / "bin/java"), "-cp", f"{classes}:{root / 'app/libs/*'}",
                        "UiHooksTest", str(args.source_apk)], check=True)
        source_set = working / "engine-fixture.apks"
        with zipfile.ZipFile(source_set, "w") as archive:
            archive.write(args.source_apk, "base.apk")
            for name in ["abi", "density"]:
                archive.write(working / f"{name}.apk", f"{name}.apk")
        fixture_assets = working / "patch-assets"
        shutil.copytree(root / "app/src/main/assets/patch", fixture_assets)
        # Simulate an old/missing AssetManager runtime. The compiled payload must win.
        (fixture_assets / "runtime.dex").write_bytes(b"stale-runtime-must-not-be-used")
        patched_set = working / "engine-patched-fixture.apks"
        classpath = f"{classes}:{root / 'app/libs/*'}"
        subprocess.run([str(args.java_home / "bin/java"), "-Xmx256m", "-cp", classpath,
                        "PatchSetCli", str(source_set), str(patched_set),
                        str(fixture_assets), str(working / "keys")], check=True)
        with zipfile.ZipFile(patched_set) as archive:
            fixed_base = working / "fixed-base.apk"
            fixed_base.write_bytes(archive.read("base.apk"))
        subprocess.run([str(args.java_home / "bin/java"), "-Xmx256m", "-cp", classpath,
                        "PatchAssertions", str(args.source_apk), str(fixed_base)], check=True)
        subprocess.run([str(args.java_home / "bin/java"), "-Djava.awt.headless=true", "-cp", classpath,
                        "LogoBrandingTest", str(args.source_apk), str(fixed_base), str(fixture_assets)], check=True)
        subprocess.run([str(args.java_home / "bin/java"), "-Xmx256m", "-cp", classpath,
                        "RuntimeLinkageTest", str(fixed_base)], check=True)
        subprocess.run([str(args.java_home / "bin/java"), "-Xmx256m", "-cp", classpath,
                        "StartupSafetyTest", str(fixed_base)], check=True)
        subprocess.run([str(args.java_home / "bin/java"), "-Xmx256m", "-cp", classpath,
                        "ModelIntegrityTest", str(args.source_apk), str(fixed_base)], check=True)
        print("PASS full engine uses compiled runtime despite broken runtime asset; real base + synthetic configurations; not a device install test")
