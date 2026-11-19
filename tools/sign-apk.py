#!/usr/bin/env python3
"""Sign an aligned test APK with an existing app identity. Never creates keys."""
import argparse
from pathlib import Path
import re
import subprocess
import tempfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("apk", type=Path)
parser.add_argument("--output", type=Path, required=True)
parser.add_argument("--java-home", type=Path, required=True)
parser.add_argument("--sdk", type=Path, default=Path.home() / "Android/Sdk")
parser.add_argument("--match-installed", type=Path, help="Fail unless the signature matches this installed/exported app APK")
signing = parser.add_mutually_exclusive_group(required=True)
signing.add_argument("--signing-dir", type=Path)
signing.add_argument("--keystore", type=Path)
parser.add_argument("--alias")
args = parser.parse_args()
tools = sorted((args.sdk / "build-tools").iterdir())[-1]
java = args.java_home / "bin/java"
base = [str(java), "-jar", str(tools / "lib/apksigner.jar")]
args.output.parent.mkdir(parents=True, exist_ok=True)
if args.apk.resolve() == args.output.resolve():
    parser.error("Use a separate output path")
if args.signing_dir and not all((args.signing_dir / name).is_file() for name in ("signing.pk8", "signing.der")):
    parser.error("The existing app identity is incomplete")


def certificate(file):
    result = subprocess.run([*base, "verify", "--print-certs", str(file)], check=True, capture_output=True, text=True)
    return set(re.findall(r"certificate SHA-256 digest: ([a-f0-9]+)", result.stdout))


with tempfile.TemporaryDirectory(prefix="katten-sign-", dir=args.output.parent) as temporary:
    target = Path(temporary) / "Smartschool-Katten.apk"
    command = [*base, "sign"]
    if args.signing_dir:
        command += ["--key", str(args.signing_dir / "signing.pk8"), "--cert", str(args.signing_dir / "signing.der")]
    else:
        # apksigner prompts privately on an interactive terminal; alternatively
        # set KATTEN_KEYSTORE_PASSWORD and KATTEN_KEY_PASSWORD in your own shell.
        import os
        command += ["--ks", str(args.keystore)]
        if args.alias:
            command += ["--ks-key-alias", args.alias]
        for variable, flag in (("KATTEN_KEYSTORE_PASSWORD", "--ks-pass"), ("KATTEN_KEY_PASSWORD", "--key-pass")):
            if variable in os.environ:
                command += [flag, "env:" + variable]
    subprocess.run([*command, "--out", str(target), str(args.apk)], check=True)
    actual = certificate(target)
    if not actual or args.match_installed and actual != certificate(args.match_installed):
        raise SystemExit("Signature does not match the existing app. Nothing was published or replaced.")
    subprocess.run([str(tools / "zipalign"), "-c", "-P", "16", "4", str(target)], check=True)
    target.replace(args.output)
print(f"Signed APK: {args.output}")
print("Certificate SHA-256: " + ", ".join(sorted(actual)))
