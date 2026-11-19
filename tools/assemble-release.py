#!/usr/bin/env python3
"""Reassemble the locally signed release artifact and verify it before upload."""
from pathlib import Path
import gzip
import hashlib
import json
import re
import shutil
import struct
from standalone_manifest import chunks, strings
import zipfile

root=Path(__file__).resolve().parent.parent
source=root / ".release"
manifest=json.loads((source / "manifest.json").read_text())
assert re.fullmatch(r"katten-r[1-9][0-9]{0,5}",manifest["tag"])
assert manifest["versionCode"]==120300000+int(manifest["tag"][8:])
output=root / "build/publication"
output.mkdir(parents=True,exist_ok=True)
transport=output / "artifact.gz"
with transport.open("wb") as assembled:
    for name in manifest["parts"]:
        assert re.fullmatch(r"part-[0-9]{3}\.bin",name)
        with (source / name).open("rb") as part:shutil.copyfileobj(part,assembled)
with transport.open("rb") as stream:assert hashlib.file_digest(stream,"sha256").hexdigest()==manifest["transportSha256"]
apk=output / "Smartschool-Katten.apk"
with gzip.open(transport,"rb") as input,apk.open("wb") as target:shutil.copyfileobj(input,target)
with apk.open("rb") as stream:assert hashlib.file_digest(stream,"sha256").hexdigest()==manifest["sha256"]
assert apk.stat().st_size==manifest["size"]
with zipfile.ZipFile(apk) as package:
    for kind,data in chunks(package.read("AndroidManifest.xml")):
        if kind==1:names=strings(data)
        if kind==0x102:
            header=struct.unpack_from("<H",data,2)[0]
            if names[struct.unpack_from("<i",data,header+4)[0]]=="application":
                start,stride,count=struct.unpack_from("<HHH",data,header+8)
                for i in range(count):
                    at=header+start+i*stride
                    if names[struct.unpack_from("<i",data,at+4)[0]]=="debuggable":
                        assert struct.unpack_from("<I",data,at+16)[0]==0,"Development APK cannot be published"
apk.with_suffix(".apk.sha256").write_text(manifest["sha256"]+"  Smartschool-Katten.apk\n")
(output / "notes.md").write_text("Smartschool Katten voor Android.\n\n- Rechtstreeks installeren als APK, met ingebouwde updatecontrole.\n- Werkende profielfoto-instellingen en een volledig aanklikbare streakpil.\n- Screenshots op de leerlingenkaart.\n\nAndroid 12L of nieuwer, 64-bits ARM.\n")
print("PASS signed release transport, SHA-256, version and production manifest")
