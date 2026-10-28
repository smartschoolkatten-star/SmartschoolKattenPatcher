#!/usr/bin/env python3
"""User-run local crash capture. Saves logs locally; sends nothing over the network."""
import argparse
from datetime import datetime
from pathlib import Path
import subprocess
import time

parser = argparse.ArgumentParser()
parser.add_argument("--adb", type=Path, default=Path.home() / "Android/Sdk/platform-tools/adb")
parser.add_argument("--serial", help="ADB serial if several phones/emulators are connected")
parser.add_argument("--output", type=Path)
args = parser.parse_args()
command = [str(args.adb)]
if args.serial:
    command += ["-s", args.serial]
subprocess.run(command + ["get-state"], check=True)
launch = subprocess.run(command + ["shell", "am", "start", "-W", "-n",
                                   "be.smartschoolkatten.mobile/be.smartschool.mobile.SplashActivity"],
                        capture_output=True, text=True)
time.sleep(3)
logs = subprocess.run(command + ["logcat", "-d", "-v", "threadtime", "-b", "crash", "-b", "main",
                                 "AndroidRuntime:E", "SmartschoolKatten:V", "*:S"],
                      capture_output=True, text=True, check=True)
target = args.output or Path.home() / "Downloads" / f"Smartschool-Katten-crash-{datetime.now():%Y%m%d-%H%M%S}.txt"
target.parent.mkdir(parents=True, exist_ok=True)
target.write_text(launch.stdout + launch.stderr + "\n" + logs.stdout)
print(f"Foutgegevens lokaal opgeslagen: {target}")
