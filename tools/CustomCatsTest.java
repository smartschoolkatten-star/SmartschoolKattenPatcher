import be.smartschoolkatten.runtime.CustomCatStore;
import be.smartschoolkatten.runtime.CatSelection;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class CustomCatsTest {
    public static void main(String[] args)throws Exception {
        Path root=Files.createTempDirectory("katten-custom-test-");
        try {
            CustomCatStore store=new CustomCatStore(root.toFile());
            byte[] data={1,2,3};
            File first=store.add(new ByteArrayInputStream(data),file->{
                if(!Arrays.equals(Files.readAllBytes(file.toPath()),data))throw new IOException("Corrupt copy");
            });
            if(new CustomCatStore(root.toFile()).count()!=1 || !Arrays.equals(Files.readAllBytes(first.toPath()),data))
                throw new AssertionError("Imported photo did not survive store recreation");
            reject(()->store.add(new ByteArrayInputStream(data),file->{throw new IOException("Invalid image");}));
            reject(()->store.add(new ByteArrayInputStream(new byte[0]),file->{}));
            reject(()->store.add(new InputStream(){int remaining=CustomCatStore.MAX_BYTES+1;
                public int read(){return remaining-->0?1:-1;}
                public int read(byte[] bytes,int offset,int length){if(remaining==0)return -1;int count=Math.min(remaining,length);remaining-=count;return count;}
            },file->{}));
            if(store.count()!=1)throw new AssertionError("Failed import used a slot");
            for(int i=1;i<CustomCatStore.LIMIT;i++)store.add(new ByteArrayInputStream(data),file->{});
            reject(()->store.add(new ByteArrayInputStream(data),file->{}));
            if(store.count()!=100 || new CustomCatStore(root.toFile()).count()!=100)
                throw new AssertionError("100-photo cap or counter is wrong");
            Path outside=Files.createTempFile("katten-protected-",".img");
            try {
                reject(()->store.delete(outside.toFile()));
                if(!Files.exists(outside))throw new AssertionError("Deleted a file outside the uploads directory");
            } finally {Files.deleteIfExists(outside);}
            store.delete(first);
            store.delete(first); // Deleting an already removed upload is harmless.
            if(store.count()!=99)throw new AssertionError("Deleting a selected upload removed other photos");
            store.add(new ByteArrayInputStream(data),file->{});
            if(store.count()!=100)throw new AssertionError("Deleted upload slot could not be reused");
            try(java.util.stream.Stream<Path> paths=Files.list(root)) {
                if(paths.anyMatch(path->path.toString().endsWith(".tmp")))throw new AssertionError("Temporary files left after rejected imports");
            }
            List<String> cats=Arrays.asList("bundled-a","bundled-b","custom-c");
            Set<String> disabled=new HashSet<>(Arrays.asList("bundled-a","custom-c"));
            List<String> active=CatSelection.available(cats,disabled);
            ConcurrentHashMap<String,String> assigned=new ConcurrentHashMap<>();
            assigned.put("person","custom-c");
            if(!CatSelection.choose("person",active,assigned).equals("bundled-b"))throw new AssertionError("Disabled cached cat spawned");
            for(int i=0;i<1000;i++)if(!CatSelection.choose("person-"+i,active,assigned).equals("bundled-b"))throw new AssertionError("Disabled cat spawned");
            if(!CatSelection.choose("original",Collections.emptyList(),assigned).equals("original"))throw new AssertionError("All-disabled fallback failed");
            if(CatSelection.available(cats,Collections.emptySet()).size()!=3)throw new AssertionError("Re-enabled cat remains excluded");
            System.out.println("PASS custom cats: persistent copies, invalid/oversize rollback, exact 100 cap/counter, disabled cached and random assignments excluded, re-enable and empty-pool fallback");
        } finally {
            try(java.util.stream.Stream<Path> paths=Files.walk(root)) {
                for(Path path:(Iterable<Path>)paths.sorted(Comparator.reverseOrder())::iterator)Files.delete(path);
            }
        }
    }
    interface Operation {void run()throws IOException;}
    static void reject(Operation operation)throws IOException {
        try {operation.run();throw new AssertionError("Invalid import accepted");}catch(IOException expected){}
    }
}
