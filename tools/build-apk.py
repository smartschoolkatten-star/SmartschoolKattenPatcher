#!/usr/bin/env python3
"""Build the standalone, self-updating APK from a complete Smartschool source."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import urllib.request
import zipfile
from standalone_manifest import decorate

ROOT = Path(__file__).resolve().parent.parent
EDITOR_URL = "https://github.com/REAndroid/APKEditor/releases/download/V1.4.9/APKEditor-1.4.9.jar"
EDITOR_SHA256 = "a9cd40df818845456be6d696de6110c89edf4b0a0580cb83438ed6b25a366e67"
SOURCE_API = "https://ws75.aptoide.com/api/7/app/get/app_id=76524701/aab=1"


def fetch(url, target, limit=512 * 1024 * 1024):
    if not url.startswith("https://"):
        raise ValueError("Download requires HTTPS")
    request = urllib.request.Request(url, headers={"User-Agent": "Smartschool-Katten-Build"})
    try:
        with urllib.request.urlopen(request, timeout=60) as source, target.open("wb") as output:
            if not source.url.startswith("https://"):
                raise ValueError("Insecure redirect")
            total = 0
            while data := source.read(65536):
                total += len(data)
                if total > limit:
                    raise ValueError("Download is too large")
                output.write(data)
    except BaseException:
        target.unlink(missing_ok=True)
        raise


def download_source(directory):
    metadata = directory / "source.json"
    fetch(SOURCE_API, metadata, 4 * 1024 * 1024)
    data = json.loads(metadata.read_text())
    if data["info"]["status"] != "OK":
        raise ValueError("Smartschool source is unavailable")
    nodes = data["nodes"]
    app = nodes["meta"]["data"]
    if app["file"]["vername"] != "12.3.0":
        raise ValueError("Unsupported Smartschool source version")
    candidates = {app["file"]["path"]: app["file"]}

    def collect(value, depth=0):
        if not isinstance(value, dict):
            return
        if depth > 8:
            raise ValueError("Invalid split metadata")
        for split in value.get("splits", value.get("list", [])):
            if isinstance(split, dict) and "path" in split:
                candidates[split["path"]] = split
        collect(value.get("data"), depth + 1)

    collect(nodes.get("aab"))
    collect(app.get("aab"))
    if not 2 <= len(candidates) <= 128:
        raise ValueError("The complete source split set is missing")
    parts = directory / "source-parts"
    parts.mkdir()
    for i, (url, spec) in enumerate(candidates.items()):
        print(f"Downloading Smartschool part {i + 1}/{len(candidates)}", flush=True)
        target = parts / f"part-{i:03d}.apk"
        fetch(url, target)
        expected = spec.get("md5sum")
        with target.open("rb") as stream:
            actual = hashlib.file_digest(stream, "md5").hexdigest()
        if not expected or actual != expected.lower():
            raise ValueError("Source download checksum mismatch")
    return parts


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java-home", type=Path, required=True)
    parser.add_argument("--sdk", type=Path, default=Path(os.environ.get("ANDROID_HOME", Path.home() / "Android/Sdk")))
    parser.add_argument("--source", type=Path, help="Complete APK, split directory or APKS/XAPK archive; downloads source if omitted")
    parser.add_argument("--apk-editor", type=Path)
    parser.add_argument("--output", type=Path, default=ROOT / "artifacts/apk-release/Smartschool-Katten.apk")
    parser.add_argument("--studio-output", type=Path, help="Android Gradle Plugin APK output directory")
    parser.add_argument("--studio-variant", default="debug")
    signing = parser.add_mutually_exclusive_group(required=True)
    signing.add_argument("--signing-dir", type=Path, help="Existing signing.pk8 and signing.der; never generates a new release identity")
    signing.add_argument("--unsigned", action="store_true", help="Build for local signing; never publish this as an update")
    signing.add_argument("--debug-test", action="store_true", help="Sign an isolated, debuggable .test app; never touches the installed app")
    args = parser.parse_args()
    if args.signing_dir and not all((args.signing_dir / name).is_file() for name in ("signing.pk8", "signing.der")):
        parser.error("Existing app signing.pk8 and signing.der are required")
    java = args.java_home / "bin/java"
    sdk_tools = max((args.sdk / "build-tools").iterdir(), key=lambda p: tuple(int(x) for x in re.findall(r"\d+", p.name)))
    android = args.sdk / "platforms/android-37.0/android.jar"
    revision = int(re.search(r"PATCH_REVISION\s*=\s*(\d+)", (ROOT / "app/src/main/java/be/smartschoolkatten/patcher/engine/KattenPatcher.java").read_text())[1])
    if args.studio_output:
        args.output = args.studio_output / "Smartschool-Katten.apk"
    args.output.parent.mkdir(parents=True, exist_ok=True)
    if args.unsigned and args.output.name == "Smartschool-Katten.apk":
        args.output = args.output.with_name("Smartschool-Katten-unsigned.apk")
    if args.debug_test:
        args.output = args.output.with_name("Smartschool-Katten-test.apk")
    work_root = ROOT / "build/standalone-apk"
    work_root.mkdir(parents=True, exist_ok=True)

    def run(*command):
        subprocess.run(list(map(str, command)), check=True, cwd=ROOT)

    with tempfile.TemporaryDirectory(prefix="release-", dir=work_root) as temporary:
        work = Path(temporary)
        cached_source = work_root / "Smartschool-12.3.0-complete.apk"
        cached_digest = cached_source.with_suffix(".apk.sha256")
        source = args.source
        if source is None and cached_source.is_file() and cached_digest.is_file():
            with cached_source.open("rb") as stream:
                if hashlib.file_digest(stream, "sha256").hexdigest() == cached_digest.read_text().strip():
                    source = cached_source
        if source is None:
            source = download_source(work)
        if source.is_dir() or source.suffix.lower() in (".apks", ".xapk", ".apkm", ".zip"):
            editor = args.apk_editor or work_root / "APKEditor-1.4.9.jar"
            if not editor.exists():
                fetch(EDITOR_URL, editor, 32 * 1024 * 1024)
            with editor.open("rb") as stream:
                if hashlib.file_digest(stream, "sha256").hexdigest() != EDITOR_SHA256:
                    raise ValueError("APKEditor checksum mismatch")
            merged = work / "Smartschool-universal.apk"
            run(java, "-Xmx2g", "-jar", editor, "m", "-i", source, "-o", merged)
            source = merged
        if args.source is None and source != cached_source:
            shutil.copyfile(source, cached_source)
            with cached_source.open("rb") as stream:
                cached_digest.write_text(hashlib.file_digest(stream, "sha256").hexdigest())
            source = cached_source
        run("python3", ROOT / "tools/build-payload.py", "--java-home", args.java_home, "--sdk", args.sdk)
        classes = work / "classes"
        classes.mkdir()
        engine = ROOT / "app/src/main/java/be/smartschoolkatten/patcher/engine"
        run(args.java_home / "bin/javac", "--release", "11", "-cp", f"{ROOT / 'app/libs/*'}:{android}", "-d", classes,
            *(engine / name for name in ("KattenPatcher.java", "BinaryResources.java", "SigningIdentity.java", "PatchValidation.java", "RuntimePayload.java")),
            *(ROOT / "tools" / name for name in ("PatchCli.java", "UiHooksTest.java", "PatchAssertions.java", "ModelIntegrityTest.java", "RuntimeLinkageTest.java", "StartupSafetyTest.java", "ScreenCaptureScopeTest.java")))
        patched = work / "patched.apk"
        # The intermediate identity is never a release identity. Final signing
        # occurs only after the updater manifest and alignment are finished.
        identity = work_root / "debug-signing" if args.debug_test else work / "intermediate-signing"
        run(java, "-Xmx1g", "-cp", f"{classes}:{ROOT / 'app/libs/*'}", "PatchCli", source, patched,
            ROOT / "app/src/main/assets/patch", identity, "be.smartschoolkatten.mobile.test" if args.debug_test else "be.smartschoolkatten.mobile")
        unsigned = work / "unsigned.apk"
        with zipfile.ZipFile(patched) as original, zipfile.ZipFile(unsigned, "w") as output:
            for item in original.infolist():
                if item.filename.startswith("META-INF/") or item.is_dir():
                    continue
                data = original.read(item)
                if item.filename == "AndroidManifest.xml":
                    data = decorate(data, revision, debug=args.debug_test)
                compression = zipfile.ZIP_STORED if item.filename == "resources.arsc" or item.filename.startswith("lib/") else zipfile.ZIP_DEFLATED
                output.writestr(item.filename, data, compress_type=compression)
        aligned = work / "aligned.apk"
        run(sdk_tools / "zipalign", "-P", "16", "-f", "4", unsigned, aligned)
        if args.unsigned:
            shutil.copyfile(aligned, args.output)
            print("UNSIGNED build: sign with your existing app identity before installation or publication.")
        else:
            final_identity = identity if args.debug_test else args.signing_dir
            run(java, "-jar", sdk_tools / "lib/apksigner.jar", "sign", "--key", final_identity / "signing.pk8", "--cert", final_identity / "signing.der",
                "--out", args.output, aligned)
            run(java, "-jar", sdk_tools / "lib/apksigner.jar", "verify", "--verbose", "--print-certs", args.output)
        run(sdk_tools / "zipalign", "-c", "-P", "16", "4", args.output)
        classpath = f"{classes}:{ROOT / 'app/libs/*'}"
        for test, inputs in (("UiHooksTest", [source]), ("PatchAssertions", [source, args.output]), ("ModelIntegrityTest", [source, args.output]),
                             ("RuntimeLinkageTest", [args.output]), ("StartupSafetyTest", [args.output]), ("ScreenCaptureScopeTest", [source, args.output])):
            run(java, "-Xmx1g", "-cp", classpath, test, *inputs)
        badging = subprocess.run([str(sdk_tools / "aapt2"), "dump", "badging", str(args.output)], check=True, capture_output=True, text=True).stdout
        print(badging)
        expected_package = "be.smartschoolkatten.mobile.test" if args.debug_test else "be.smartschoolkatten.mobile"
        if f"package: name='{expected_package}'" not in badging or args.debug_test and "application-debuggable" not in badging:
            raise ValueError("Wrong package or missing debug flag in test APK")
        with args.output.open("rb") as stream:
            digest = hashlib.file_digest(stream, "sha256").hexdigest()
        args.output.with_suffix(".apk.sha256").write_text(f"{digest}  {args.output.name}\n")
        if args.studio_output:
            metadata = {"version": 3, "artifactType": {"type": "APK", "kind": "Directory"},
                        "applicationId": expected_package, "variantName": args.studio_variant,
                        "elements": [{"type": "SINGLE", "filters": [], "attributes": [],
                                      "versionCode": 120300000 + revision, "versionName": f"12.3.0-katten.r{revision}",
                                      "outputFile": args.output.name}], "elementType": "File", "minSdkVersionForDexing": 32}
            (args.studio_output / "output-metadata.json").write_text(json.dumps(metadata, indent=2) + "\n")
        print(f"Standalone APK katten-r{revision}: {args.output}")


if __name__ == "__main__":
    main()
