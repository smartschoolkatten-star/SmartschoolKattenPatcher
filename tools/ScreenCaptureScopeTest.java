import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.*;
import com.android.tools.smali.dexlib2.iface.instruction.*;
import com.android.tools.smali.dexlib2.iface.reference.MethodReference;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/** Verify the screenshot change against the actual host, including other windows. */
public final class ScreenCaptureScopeTest {
    private static final String CARD="Leac;->k0";
    private static final String ORIGINAL="Landroid/view/Window;->setFlags";
    private static final String HOOK="Lbe/smartschoolkatten/runtime/KattenRuntime;->studentCardWindowFlags";
    public static void main(String[] args)throws Exception {
        Map<String,List<String>> before=windowCalls(args[0]),after=windowCalls(args[1]);
        List<String> expected=new ArrayList<>(Objects.requireNonNull(before.get(CARD),"Card screen missing"));
        if(Collections.frequency(expected,ORIGINAL)!=1)throw new AssertionError("Unexpected card window entry");
        Collections.replaceAll(expected,ORIGINAL,HOOK);
        if(!expected.equals(after.remove(CARD)))throw new AssertionError("Card window operations changed outside the scoped flag update");
        before.remove(CARD);
        if(!before.equals(after))throw new AssertionError("Window operations changed outside the student card");
        long protectedEntries=before.values().stream().flatMap(List::stream).filter(ORIGINAL::equals).count();
        if(protectedEntries<2)throw new AssertionError("Insufficient coverage of other protected windows");
        System.out.println("PASS screenshots: own card flag hook only; brightness, cleanup, QR views and all other host window calls preserved");
    }
    private static Map<String,List<String>> windowCalls(String apk)throws Exception {
        Map<String,List<String>> result=new TreeMap<>();
        try(ZipFile zip=new ZipFile(apk)) {
            for(ZipEntry entry:Collections.list(zip.entries()))if(entry.getName().matches("classes[0-9]*\\.dex")) {
                DexBackedDexFile dex=DexBackedDexFile.fromInputStream(null,new BufferedInputStream(zip.getInputStream(entry)));
                for(ClassDef cls:dex.getClasses()) {
                    if(cls.getType().startsWith("Lbe/smartschoolkatten/"))continue;
                    for(Method method:cls.getMethods())if(method.getImplementation()!=null) {
                        List<String> calls=new ArrayList<>();
                        for(Instruction instruction:method.getImplementation().getInstructions())if(instruction instanceof ReferenceInstruction) {
                            Object ref=((ReferenceInstruction)instruction).getReference();
                            if(ref instanceof MethodReference) {
                                MethodReference call=(MethodReference)ref;
                                String name=call.getDefiningClass()+"->"+call.getName();
                                if(call.getDefiningClass().equals("Landroid/view/Window;") || name.equals(HOOK))calls.add(name);
                            }
                        }
                        if(!calls.isEmpty()) {
                            String key=cls.getType()+"->"+method.getName();
                            // Include the parameter list for overloaded methods; card k0 is unique.
                            if(!key.equals(CARD))key+=method.getParameterTypes().toString()+method.getReturnType();
                            if(result.put(key,calls)!=null)throw new AssertionError("Ambiguous window method "+key);
                        }
                    }
                }
            }
        }
        return result;
    }
}
