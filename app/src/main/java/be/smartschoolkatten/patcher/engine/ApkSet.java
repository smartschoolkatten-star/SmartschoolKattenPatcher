package be.smartschoolkatten.patcher.engine;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.*;

/** Portable zip of base.apk and configuration APKs. Android installs the entire set. */
public final class ApkSet {
    public static List<File> unpack(File archive, File directory) throws IOException {
        directory.mkdirs();
        List<File> files = new ArrayList<>();
        long total = 0;
        try (ZipFile zip = new ZipFile(archive)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".apk")) continue;
                if (files.size() >= 128) throw new IOException("Te veel APK-onderdelen.");
                File target = new File(directory, "part-" + files.size() + ".apk");
                try (InputStream in = zip.getInputStream(entry); OutputStream out = new FileOutputStream(target)) {
                    byte[] buffer = new byte[65536]; int n;
                    while ((n = in.read(buffer)) != -1) {
                        total += n;
                        if (total > 2L * 1024 * 1024 * 1024) throw new IOException("APK-pakket is te groot.");
                        out.write(buffer, 0, n);
                    }
                }
                files.add(target);
            }
        }
        validate(files);
        return files;
    }

    public static Map<String, String> info(File apk) throws IOException {
        try (ZipFile zip = new ZipFile(apk)) {
            ZipEntry manifest = zip.getEntry("AndroidManifest.xml");
            if (manifest == null) throw new IOException("APK zonder Android-manifest.");
            try (InputStream input = zip.getInputStream(manifest)) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192]; int n;
                while ((n = input.read(buffer)) != -1) {
                    if (bytes.size() + n > 8 * 1024 * 1024) throw new IOException("Manifest is te groot.");
                    bytes.write(buffer, 0, n);
                }
                return BinaryResources.manifestAttributes(bytes.toByteArray());
            }
        }
    }

    public static File validate(List<File> files) throws IOException {
        File base = null; Map<String, String> baseInfo = null;
        Set<String> names = new HashSet<>(), supplied = new HashSet<>(), required = new HashSet<>();
        List<Map<String, String>> infos = new ArrayList<>();
        for (File file : files) {
            Map<String, String> info = info(file); infos.add(info);
            String split = info.getOrDefault("split", "");
            if (!names.add(split)) throw new IOException("Dubbel APK-onderdeel: " + split);
            if (split.isEmpty()) { base = file; baseInfo = info; }
            addTypes(supplied, info.get("splitTypes"));
            addTypes(required, info.get("requiredSplitTypes"));
        }
        if (base == null) throw new IOException("De basis-APK ontbreekt.");
        for (Map<String, String> info : infos) {
            if (!Objects.equals(baseInfo.get("package"), info.get("package"))
                || !Objects.equals(baseInfo.get("versionCode"), info.get("versionCode")))
                throw new IOException("De APK-onderdelen horen niet bij dezelfde appversie.");
        }
        if (!supplied.containsAll(required)) {
            required.removeAll(supplied);
            throw new IOException("Verplichte APK-onderdelen ontbreken: " + required);
        }
        if (!"12.3.0".equals(baseInfo.get("versionName"))) throw new IOException("Alleen versie 12.3.0 wordt ondersteund.");
        return base;
    }
    private static void addTypes(Set<String> output, String values) {
        if (values != null) for (String value : values.split(",")) if (!value.trim().isEmpty()) output.add(value.trim());
    }

    public static void pack(List<File> files, File output) throws IOException {
        File base = validate(files);
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(output))) {
            int i = 0;
            for (File file : files) {
                // APKs are already compressed. Store them intact instead of deflating again.
                CRC32 crc=new CRC32();
                try(InputStream input=new BufferedInputStream(new FileInputStream(file))) {
                    byte[] buffer=new byte[65536];int count;
                    while((count=input.read(buffer))!=-1)crc.update(buffer,0,count);
                }
                ZipEntry entry=new ZipEntry(file.equals(base) ? "base.apk" : "split-" + i++ + ".apk");
                entry.setMethod(ZipEntry.STORED);entry.setSize(file.length());entry.setCompressedSize(file.length());entry.setCrc(crc.getValue());
                zip.putNextEntry(entry);
                Files.copy(file.toPath(), zip); zip.closeEntry();
            }
        }
    }

    public static void patch(File source, File output, File assets, File identity, KattenPatcher.Progress progress) throws Exception {
        File working = Files.createTempDirectory(output.getParentFile().toPath(), "katten-set-").toFile();
        try {
            progress.update("APK-onderdelen voorbereiden…",0f);
            List<File> originals = unpack(source, new File(working, "source"));
            File base = validate(originals);
            originals.sort(Comparator.comparingInt(file->file.equals(base)?0:1));
            List<File> patched = new ArrayList<>();
            KattenPatcher engine = new KattenPatcher();
            for (File file : originals) {
                File target = new File(working, "patched-" + patched.size() + ".apk");
                if (file.equals(base)) engine.patchBase(file, target, assets, identity, new KattenPatcher.Progress() {
                    public void update(String message) {progress.update(message);}
                    public void update(String message,float fraction) {progress.update(message,0.02f+0.90f*fraction);}
                }, true);
                else {
                    String message="APK-onderdeel aanpassen (" + (patched.size() + 1) + "/" + originals.size() + ")…";
                    progress.update(message,0.92f+0.05f*(patched.size()-1)/Math.max(1,originals.size()-1));
                    engine.patchSplit(file, target, identity);
                    progress.update(message,Math.min(0.97f,0.92f+0.05f*patched.size()/Math.max(1,originals.size()-1)));
                }
                patched.add(target);
            }
            progress.update("Volledig installatiepakket controleren…",0.97f);
            pack(patched, output);
            progress.update("Smartschool Katten staat klaar met " + patched.size() + " APK-onderdelen.",1f);
        } catch (Exception failure) { output.delete(); throw failure; }
        finally { deleteTree(working); }
    }
    public static void deleteTree(File directory) {
        File[] children = directory.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        directory.delete();
    }
}
