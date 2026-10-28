import be.smartschoolkatten.patcher.engine.PatchValidation;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.*;
import com.android.tools.smali.dexlib2.immutable.*;
import com.android.tools.smali.dexlib2.writer.pool.DexPool;
import com.android.tools.smali.dexlib2.writer.io.MemoryDataStore;
import java.io.*;import java.nio.file.*;import java.util.*;import java.util.zip.*;
/** Reproduce the phone's missing configureCard without relying on revision filenames. */
public final class RuntimeLinkageTest {
    public static void main(String[] args)throws Exception {
        File broken=File.createTempFile("katten-stale-runtime-",".apk");boolean removed=false;
        try {
            try(ZipFile input=new ZipFile(args[0]);ZipOutputStream output=new ZipOutputStream(new FileOutputStream(broken))) {
                for(ZipEntry entry:Collections.list(input.entries())) {
                    byte[] data;try(InputStream stream=input.getInputStream(entry)){data=stream.readAllBytes();}
                    if(entry.getName().matches("classes[0-9]*\\.dex")) {
                        DexBackedDexFile dex=new DexBackedDexFile(null,data);List<ClassDef> classes=new ArrayList<>();boolean changed=false;
                        for(ClassDef cls:dex.getClasses()) {
                            if(!cls.getType().equals("Lbe/smartschoolkatten/runtime/KattenRuntime;")){classes.add(cls);continue;}
                            List<Method> methods=new ArrayList<>();for(Method method:cls.getMethods()) {
                                if(method.getName().equals("configureCard")){removed=true;changed=true;}else methods.add(method);
                            }
                            classes.add(new ImmutableClassDef(cls.getType(),cls.getAccessFlags(),cls.getSuperclass(),cls.getInterfaces(),cls.getSourceFile(),cls.getAnnotations(),cls.getFields(),methods));
                        }
                        if(changed) {MemoryDataStore store=new MemoryDataStore();DexPool.writeTo(store,new ImmutableDexFile(dex.getOpcodes(),classes));data=store.getData();}
                    }
                    output.putNextEntry(new ZipEntry(entry.getName()));output.write(data);output.closeEntry();
                }
            }
            if(!removed)throw new AssertionError("configureCard absent in positive fixture");
            try {PatchValidation.verify(broken);throw new AssertionError("Stale runtime accepted");}
            catch(IOException expected){if(!expected.getMessage().contains("configureCard"))throw expected;}
            PatchValidation.verify(new File(args[0]));
            System.out.println("PASS old runtime missing configureCard rejected; matching runtime accepted");
        } finally {Files.deleteIfExists(broken.toPath());}
    }
}
