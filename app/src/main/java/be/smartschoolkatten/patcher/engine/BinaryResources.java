package be.smartschoolkatten.patcher.engine;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.UnaryOperator;

/** Rebuild Android string pools without changing resource or string indices. */
public final class BinaryResources {
    public static final Set<String> EDITOR_COMPONENTS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        "ly.img.android.IMGLYAutoInit", "ly.img.android.pesdk.ui.activity.EditorActivity",
        "ly.img.android.pesdk.ui.activity.PhotoEditorActivity")));
    /** Remove an unavailable optional SDK's Android entry points, leaving its license code intact. */
    public static byte[] withoutEditorComponents(byte[] xml) throws IOException {
        return filterComponents(xml, true, null);
    }
    public static Set<String> componentNames(byte[] xml) throws IOException {
        Set<String> names = new HashSet<>(); filterComponents(xml, false, names); return names;
    }
    private static byte[] filterComponents(byte[] xml, boolean remove, Set<String> names) throws IOException {
        if (xml.length < 8 || u16(xml, 0) != 3) throw new IOException("Ongeldig manifest.");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int rootHeader = u16(xml, 2); output.write(xml, 0, rootHeader);
        List<String> strings = Collections.emptyList(); int skippedDepth = 0;
        for (int offset = rootHeader; offset < xml.length;) {
            int type = u16(xml, offset), header = u16(xml, offset + 2), size = i32(xml, offset + 4);
            if (size < header || size < 8 || offset + (long) size > xml.length) throw new IOException("Ongeldig manifestonderdeel.");
            if (type == 1) {
                byte[] pool = Arrays.copyOfRange(xml, offset, offset + size);
                int count = i32(pool, 8), start = i32(pool, 20); strings = new ArrayList<>();
                boolean utf8 = (i32(pool, 16) & 0x100) != 0;
                for (int i = 0; i < count; i++) {
                    int[] at = {start + i32(pool, header + 4 * i)};
                    if (utf8) { length8(pool, at); int n = length8(pool, at); strings.add(new String(pool, at[0], n, StandardCharsets.UTF_8)); }
                    else { int n = length16(pool, at); strings.add(new String(pool, at[0], n * 2, StandardCharsets.UTF_16LE)); }
                }
            }
            boolean skip = skippedDepth > 0;
            if (type == 0x102) {
                if (skip) skippedDepth++;
                else {
                    int extension = offset + header;
                    String tag = strings.get(i32(xml, extension + 4));
                    if (Arrays.asList("provider", "activity", "service", "receiver").contains(tag)) {
                        int start = u16(xml, extension + 8), stride = u16(xml, extension + 10), count = u16(xml, extension + 12);
                        for (int i = 0; i < count; i++) {
                            int attribute = extension + start + stride * i;
                            if (!strings.get(i32(xml, attribute + 4)).equals("name")) continue;
                            int raw = i32(xml, attribute + 8), value = i32(xml, attribute + 16);
                            String name = strings.get(raw >= 0 ? raw : value);
                            if (names != null) names.add(name);
                            if (remove && EDITOR_COMPONENTS.contains(name)) { skippedDepth = 1; skip = true; }
                        }
                    }
                }
            } else if (type == 0x103 && skip) skippedDepth--;
            if (!skip) output.write(xml, offset, size);
            offset += size;
        }
        if (skippedDepth != 0) throw new IOException("Onvolledig manifest-element.");
        byte[] result = output.toByteArray(); put(result, 4, result.length); return result;
    }
    public static Map<String, String> manifestAttributes(byte[] b) throws IOException {
        Map<String, String> result = new HashMap<>();
        List<String> strings = new ArrayList<>();
        if (b.length < 8 || u16(b, 0) != 3) throw new IOException("Ongeldig Android-manifest.");
        for (int offset = u16(b, 2); offset < b.length;) {
            if (offset + 8 > b.length) throw new IOException("Onvolledig manifest.");
            int type = u16(b, offset), header = u16(b, offset + 2), size = i32(b, offset + 4);
            if (size < 8 || size < header || offset + (long) size > b.length) throw new IOException("Ongeldig manifestonderdeel.");
            if (type == 1) {
                byte[] pool = Arrays.copyOfRange(b, offset, offset + size);
                int count = i32(pool, 8), start = i32(pool, 20);
                boolean utf8 = (i32(pool, 16) & 0x100) != 0;
                for (int i = 0; i < count; i++) {
                    int[] at = {start + i32(pool, header + 4 * i)};
                    if (utf8) {
                        length8(pool, at); int n = length8(pool, at);
                        strings.add(new String(pool, at[0], n, StandardCharsets.UTF_8));
                    } else {
                        int n = length16(pool, at);
                        strings.add(new String(pool, at[0], n * 2, StandardCharsets.UTF_16LE));
                    }
                }
            } else if (type == 0x102) {
                int extension = offset + header;
                if (extension + 20 > offset + size) throw new IOException("Ongeldig manifest-element.");
                if (!"manifest".equals(strings.get(i32(b, extension + 4)))) { offset += size; continue; }
                int start = u16(b, extension + 8), stride = u16(b, extension + 10), count = u16(b, extension + 12);
                if (stride < 20 || extension + start + (long) stride * count > offset + size)
                    throw new IOException("Ongeldige manifest-attributen.");
                for (int i = 0; i < count; i++) {
                    int attribute = extension + start + stride * i;
                    String name = strings.get(i32(b, attribute + 4));
                    int raw = i32(b, attribute + 8), value = i32(b, attribute + 16);
                    int valueType = b[attribute + 15] & 255;
                    result.put(name, raw >= 0 ? strings.get(raw) : valueType == 3 ? strings.get(value) : Integer.toString(value));
                }
                return result;
            }
            offset += size;
        }
        throw new IOException("Manifest-element ontbreekt.");
    }
    /** Read the compiled manifest attribute, rather than treating pool strings as attributes. */
    public static boolean requiresSplits(byte[] manifest) throws IOException {
        if (manifest.length < 8 || u16(manifest, 0) != 3)
            throw new IOException("Ongeldig Android-manifest.");
        int[] resourceIds = new int[0];
        for (int offset = u16(manifest, 2); offset < manifest.length;) {
            if (offset + 8 > manifest.length) throw new IOException("Onvolledig Android-manifest.");
            int type = u16(manifest, offset), header = u16(manifest, offset + 2);
            int size = i32(manifest, offset + 4);
            if (size < header || size < 8 || offset + size > manifest.length)
                throw new IOException("Ongeldig manifestonderdeel.");
            if (type == 0x180) {
                resourceIds = new int[(size - header) / 4];
                for (int i = 0; i < resourceIds.length; i++)
                    resourceIds[i] = i32(manifest, offset + header + 4 * i);
            } else if (type == 0x102) {
                int extension = offset + header;
                if (extension + 20 > offset + size) throw new IOException("Ongeldig manifest-element.");
                int start = u16(manifest, extension + 8);
                int stride = u16(manifest, extension + 10);
                int count = u16(manifest, extension + 12);
                if (stride < 20 || extension + start + (long) stride * count > offset + size)
                    throw new IOException("Ongeldige manifest-attributen.");
                for (int i = 0; i < count; i++) {
                    int attribute = extension + start + i * stride;
                    int name = i32(manifest, attribute + 4);
                    if (name >= 0 && name < resourceIds.length && resourceIds[name] == 0x0101064e)
                        return true; // android:requiredSplitTypes
                }
            }
            offset += size;
        }
        return false;
    }
    public static byte[] rewrite(byte[] bytes, UnaryOperator<String> replace, String packageName) throws IOException {
        int type = u16(bytes, 0), header = u16(bytes, 2);
        if (type == 1) return pool(bytes, replace);
        if (type != 2 && type != 3 && type != 0x200) return bytes;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] head = Arrays.copyOf(bytes, header);
        if (type == 0x200 && packageName != null) {
            Arrays.fill(head, 12, 268, (byte) 0);
            byte[] name = packageName.getBytes(StandardCharsets.UTF_16LE);
            System.arraycopy(name, 0, head, 12, name.length);
        }
        output.write(head);
        int offset = header;
        while (offset < bytes.length) {
            int size = i32(bytes, offset + 4);
            if (size < 8 || offset + size > bytes.length) throw new IOException("Invalid resource chunk");
            // Package headers store relative offsets to their two string pools.
            if (type == 0x200) {
                if (i32(bytes, 268) == offset) put(head, 268, output.size());
                if (i32(bytes, 276) == offset) put(head, 276, output.size());
            }
            output.write(rewrite(Arrays.copyOfRange(bytes, offset, offset + size), replace, packageName));
            offset += size;
        }
        byte[] result = output.toByteArray();
        System.arraycopy(head, 0, result, 0, header);
        put(result, 4, result.length);
        return result;
    }
    private static byte[] pool(byte[] b, UnaryOperator<String> replace) throws IOException {
        int header = u16(b, 2), count = i32(b, 8), styles = i32(b, 12);
        int flags = i32(b, 16), start = i32(b, 20), styleStart = i32(b, 24);
        boolean utf8 = (flags & 0x100) != 0;
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        int[] offsets = new int[count];
        for (int i = 0; i < count; i++) {
            int[] at = {start + i32(b, header + 4 * i)};
            String value;
            if (utf8) {
                length8(b, at); int n = length8(b, at);
                value = new String(b, at[0], n, StandardCharsets.UTF_8);
            } else {
                int n = length16(b, at);
                value = new String(b, at[0], n * 2, StandardCharsets.UTF_16LE);
            }
            value = replace.apply(value);
            offsets[i] = data.size();
            byte[] encoded = value.getBytes(utf8 ? StandardCharsets.UTF_8 : StandardCharsets.UTF_16LE);
            if (utf8) { writeLength8(data, value.length()); writeLength8(data, encoded.length); }
            else writeLength16(data, value.length());
            data.write(encoded); data.write(0); if (!utf8) data.write(0);
        }
        while (data.size() % 4 != 0) data.write(0);
        int newStart = header + 4 * (count + styles);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] head = Arrays.copyOf(b, newStart);
        for (int i = 0; i < count; i++) put(head, header + i * 4, offsets[i]);
        put(head, 16, flags & ~1); // replacements no longer promise a sorted pool
        put(head, 20, newStart);
        put(head, 24, styles > 0 ? newStart + data.size() : 0);
        output.write(head); data.writeTo(output);
        if (styles > 0) output.write(b, styleStart, b.length - styleStart);
        byte[] result = output.toByteArray(); put(result, 4, result.length); return result;
    }
    static int u16(byte[] b, int o) { return (b[o] & 255) | ((b[o+1] & 255) << 8); }
    static int i32(byte[] b, int o) { return u16(b,o) | (u16(b,o+2) << 16); }
    static void put(byte[] b,int o,int v) { for(int i=0;i<4;i++) b[o+i]=(byte)(v >>> (8*i)); }
    private static int length8(byte[] b,int[] o) { int a=b[o[0]++]&255; return (a&128)==0?a:((a&127)<<8)|(b[o[0]++]&255); }
    private static int length16(byte[] b,int[] o) { int a=u16(b,o[0]);o[0]+=2; if((a&32768)==0)return a; int c=u16(b,o[0]);o[0]+=2;return ((a&32767)<<16)|c; }
    private static void writeLength8(OutputStream out,int n)throws IOException { if(n>127)out.write((n>>8)|128);out.write(n&255); }
    private static void writeLength16(OutputStream out,int n)throws IOException { if(n>32767){int high=(n>>>16)|32768;out.write(high&255);out.write(high>>>8);}out.write(n&255);out.write((n>>>8)&255); }
}
