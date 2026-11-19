"""Add the APK updater to compiled XML while retaining host resource indices."""
import struct

ANDROID = "http://schemas.android.com/apk/res/android"
ACTIVITY = "be.smartschoolkatten.runtime.UpdateActivity"
PERMISSION = "android.permission.REQUEST_INSTALL_PACKAGES"


def chunks(data):
    offset = struct.unpack_from("<H", data, 2)[0]
    while offset < len(data):
        kind, header, size = struct.unpack_from("<HHI", data, offset)
        if size < header or size < 8 or offset + size > len(data):
            raise ValueError("Invalid binary XML chunk")
        yield kind, data[offset:offset + size]
        offset += size


def length(data, offset, utf8):
    value = data[offset] if utf8 else struct.unpack_from("<H", data, offset)[0]
    offset += 1 if utf8 else 2
    high = 0x80 if utf8 else 0x8000
    if value & high:
        tail = data[offset] if utf8 else struct.unpack_from("<H", data, offset)[0]
        value = ((value & ~high) << (8 if utf8 else 16)) | tail
        offset += 1 if utf8 else 2
    return value, offset


def strings(pool):
    header = struct.unpack_from("<H", pool, 2)[0]
    count, _, flags, start = struct.unpack_from("<IIII", pool, 8)
    utf8 = bool(flags & 0x100)
    result = []
    for i in range(count):
        at = start + struct.unpack_from("<I", pool, header + 4 * i)[0]
        size, at = length(pool, at, utf8)
        if utf8:
            size, at = length(pool, at, True)
        result.append(pool[at:at + size * (1 if utf8 else 2)].decode("utf-8" if utf8 else "utf-16le"))
    return result


def string_pool(values, original):
    # Keep the original style offsets and spans; string indices stay unchanged.
    header = struct.unpack_from("<H", original, 2)[0]
    old_count, styles = struct.unpack_from("<II", original, 8)
    old_style_start = struct.unpack_from("<I", original, 24)[0]
    style_offsets = original[header + old_count * 4:header + (old_count + styles) * 4]
    data, offsets = bytearray(), []
    for value in values:
        offsets.append(len(data))
        encoded = value.encode("utf-16le")
        units = len(encoded) // 2
        data += struct.pack("<HH", (units >> 16) | 0x8000, units & 0xffff) if units > 0x7fff else struct.pack("<H", units)
        data += encoded + b"\0\0"
    data += bytes((-len(data)) % 4)
    start = 28 + 4 * (len(values) + styles)
    spans = original[old_style_start:] if styles else b""
    return (struct.pack("<HHIIIIII", 1, 28, start + len(data) + len(spans), len(values), styles, 0, start, start + len(data) if styles else 0)
            + struct.pack(f"<{len(offsets)}I", *offsets) + style_offsets + data + spans)


def decorate(manifest, revision):
    if not 1 <= revision <= 999999:
        raise ValueError("Invalid release revision")
    parts = list(chunks(manifest))
    old_pool = next(data for kind, data in parts if kind == 1)
    names = strings(old_pool)
    resources = next((list(struct.unpack_from(f"<{(len(data)-8)//4}I", data, 8)) for kind, data in parts if kind == 0x180), [])

    def index(value, resource=0):
        if resource and resource in resources:
            return resources.index(resource)
        if not resource and value in names:
            return names.index(value)
        names.append(value)
        resources.extend([0] * (len(names) - len(resources)))
        resources[-1] = resource
        return len(names) - 1

    namespace = index(ANDROID)
    name = index("name", 0x01010003)
    exported = index("exported", 0x01010010)

    def attribute(key, value, kind=3):
        return struct.pack("<iiiHBBI", namespace, key, value if kind == 3 else -1, 8, 0, kind, value)

    def element(tag, attributes):
        tag_index = index(tag)
        start = struct.pack("<HHIIIiiHHHHHH", 0x102, 16, 36 + 20 * len(attributes), 1, 0xffffffff, -1, tag_index, 20, 20, len(attributes), 0, 0, 0)
        end = struct.pack("<HHIIIii", 0x103, 16, 24, 1, 0xffffffff, -1, tag_index)
        return start + b"".join(attributes) + end

    permission = element("uses-permission", [attribute(name, index(PERMISSION))])
    activity = element("activity", [attribute(name, index(ACTIVITY)), attribute(exported, 0, 0x12)])
    version_name = index(f"12.3.0-katten.r{revision}")
    output = []
    application = manifest_seen = permission_seen = False
    for kind, original in parts:
        data = bytearray(original)
        if kind in (1, 0x180):
            continue
        if kind == 0x102:
            header = struct.unpack_from("<H", data, 2)[0]
            tag = names[struct.unpack_from("<i", data, header + 4)[0]]
            start, stride, count = struct.unpack_from("<HHH", data, header + 8)
            for i in range(count):
                at = header + start + stride * i
                key = names[struct.unpack_from("<i", data, at + 4)[0]]
                if tag == "manifest" and key in ("requiredSplitTypes", "split", "isSplitRequired"):
                    raise ValueError("Source still requires split APKs; merge the complete source first")
                if tag == "manifest" and key in ("versionCode", "versionCodeMajor", "versionName"):
                    value = version_name if key == "versionName" else 0 if key == "versionCodeMajor" else 120300000 + revision
                    struct.pack_into("<iHBBI", data, at + 8, value if key == "versionName" else -1, 8, 0, 3 if key == "versionName" else 0x10, value)
                if tag == "uses-permission" and key == "name":
                    value = struct.unpack_from("<i", data, at + 16)[0]
                    permission_seen |= names[value] == PERMISSION
            if tag == "application":
                application = True
                if not permission_seen:
                    output.append(permission)
            manifest_seen |= tag == "manifest"
        if kind == 0x103:
            header = struct.unpack_from("<H", data, 2)[0]
            if names[struct.unpack_from("<i", data, header + 4)[0]] == "application":
                output.append(activity)
        output.append(data)
    if not manifest_seen or not application:
        raise ValueError("Manifest has no application")
    pool = string_pool(names, old_pool)
    mapping = struct.pack("<HHI", 0x180, 8, 8 + 4 * len(resources)) + struct.pack(f"<{len(resources)}I", *resources)
    body = pool + mapping + b"".join(output)
    return struct.pack("<HHI", 3, 8, 8 + len(body)) + body
