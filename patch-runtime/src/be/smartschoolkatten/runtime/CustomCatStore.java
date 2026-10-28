package be.smartschoolkatten.runtime;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Private copies survive source deletion and app updates; enforce the cap at storage time. */
public final class CustomCatStore {
    public static final int LIMIT=100;
    public static final int MAX_BYTES=8*1024*1024;
    public interface Validator {void validate(File file)throws IOException;}
    private final File directory;
    public CustomCatStore(File directory) {this.directory=directory;}
    public synchronized List<File> files() {
        File[] files=directory.listFiles((dir,name)->name.endsWith(".img"));
        if(files==null)return Collections.emptyList();
        Arrays.sort(files,Comparator.comparing(File::getName));
        return Collections.unmodifiableList(Arrays.asList(files));
    }
    public synchronized int count() {return files().size();}
    public synchronized void delete(File file)throws IOException {
        File target=file.getCanonicalFile();
        if(!directory.getCanonicalFile().equals(target.getParentFile()) || !target.getName().endsWith(".img"))
            throw new IOException("Alleen eigen katten kunnen worden verwijderd");
        Files.deleteIfExists(target.toPath());
    }
    public synchronized File add(InputStream input,Validator validator)throws IOException {
        if(count()>=LIMIT)throw new IOException("Maximum van 100 eigen katten bereikt");
        if(!directory.isDirectory() && !directory.mkdirs())throw new IOException("Kattenmap kon niet worden gemaakt");
        File temporary=File.createTempFile("import-",".tmp",directory);
        try {
            try(OutputStream output=new FileOutputStream(temporary)) {
                byte[] buffer=new byte[8192];int bytes,total=0;
                while((bytes=input.read(buffer))!=-1) {
                    total+=bytes;if(total>MAX_BYTES)throw new IOException("Foto is groter dan 8 MB");
                    output.write(buffer,0,bytes);
                }
                if(total==0)throw new IOException("Leeg afbeeldingsbestand");
            }
            validator.validate(temporary);
            File destination=new File(directory,UUID.randomUUID()+".img");
            Files.move(temporary.toPath(),destination.toPath());
            return destination;
        } finally {temporary.delete();}
    }
}
