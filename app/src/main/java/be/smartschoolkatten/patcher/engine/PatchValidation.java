package be.smartschoolkatten.patcher.engine;

import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.*;
import com.android.tools.smali.dexlib2.iface.instruction.*;
import com.android.tools.smali.dexlib2.iface.reference.MethodReference;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/** Checks the injected call's receiver before an APK is published or installed. */
public final class PatchValidation {
    private static final String RUNTIME = "Lbe/smartschoolkatten/runtime/KattenRuntime;";
    private static String signature(MethodReference method) {
        StringBuilder result=new StringBuilder(method.getDefiningClass()).append("->").append(method.getName()).append('(');
        for(CharSequence parameter:method.getParameterTypes())result.append(parameter);
        return result.append(')').append(method.getReturnType()).toString();
    }
    public static void verify(File apk) throws IOException {
        boolean application = false, runtime = false;
        int initCalls = 0, avatarCalls = 0;
        Map<String,Integer> definitions = new HashMap<>();
        Set<String> references = new HashSet<>();
        try (ZipFile zip = new ZipFile(apk)) {
            try(InputStream input=zip.getInputStream(zip.getEntry("AndroidManifest.xml"))) {
                ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;
                while((n=input.read(buffer))!=-1)bytes.write(buffer,0,n);
                Set<String> components=BinaryResources.componentNames(bytes.toByteArray());
                for(String editor:BinaryResources.EDITOR_COMPONENTS)
                    if(components.contains(editor))throw new IOException("De niet-beschikbare foto-editor is nog actief in het manifest.");
            }
            for (ZipEntry entry : Collections.list(zip.entries())) {
                if (!entry.getName().matches("classes[0-9]*\\.dex")) continue;
                DexBackedDexFile dex;
                try (InputStream input = new BufferedInputStream(zip.getInputStream(entry))) {
                    dex = DexBackedDexFile.fromInputStream(null, input);
                }
                if (dex.getMethodSection().size() > 65536 || dex.getTypeSection().size() > 65536)
                    throw new IOException("De patch overschrijdt een DEX-referentielimiet.");
                for (ClassDef cls : dex.getClasses()) {
                    if (cls.getType().equals(RUNTIME)) runtime = true;
                    if (cls.getType().startsWith("Lbe/smartschoolkatten/runtime/")) {
                        for(Method defined:cls.getMethods())definitions.put(signature(defined),defined.getAccessFlags());
                    }
                    for (Method method : cls.getMethods()) {
                        MethodImplementation body = method.getImplementation();
                        if (body == null) continue;
                        int index = 0;
                        for (Instruction instruction : body.getInstructions()) {
                            if (instruction instanceof ReferenceInstruction
                                && ((ReferenceInstruction) instruction).getReference() instanceof MethodReference) {
                                MethodReference ref = (MethodReference) ((ReferenceInstruction) instruction).getReference();
                                if(ref.getDefiningClass().startsWith("Lbe/smartschoolkatten/runtime/")
                                    && (instruction.getOpcode()==Opcode.INVOKE_STATIC || instruction.getOpcode()==Opcode.INVOKE_STATIC_RANGE))references.add(signature(ref));
                                if (ref.getDefiningClass().equals(RUNTIME) && !cls.getType().startsWith("Lbe/smartschoolkatten/runtime/")) {
                                    if (ref.getName().equals("init")) {
                                        initCalls++;
                                        if (!cls.getType().equals("Lbe/smartschool/mobile/Application;")
                                            || !method.getName().equals("onCreate") || !method.getParameterTypes().isEmpty()
                                            || index != 0 || instruction.getOpcode() != Opcode.INVOKE_STATIC_RANGE
                                            || !(instruction instanceof RegisterRangeInstruction))
                                            throw new IOException("Onveilige opstartpatch: initialisatie staat niet aan het begin van Application.onCreate.");
                                        RegisterRangeInstruction call = (RegisterRangeInstruction) instruction;
                                        if (call.getRegisterCount() != 1 || call.getStartRegister() != body.getRegisterCount() - 1)
                                            throw new IOException("Onveilige opstartpatch: verkeerd Application-register.");
                                        application = true;
                                    } else if (ref.getName().equals("avatar") || ref.getName().equals("ownAvatar")) {
                                        avatarCalls++;
                                        if (Arrays.asList("equals", "hashCode", "toString", "copy", "copy$default", "writeToParcel", "<init>").contains(method.getName()))
                                            throw new IOException("De kattenpatch verandert interne modelgegevens.");
                                    }
                                }
                            }
                            index++;
                        }
                    }
                }
            }
        } catch (RuntimeException failure) { throw new IOException("De gepatchte bytecode kan niet worden gelezen.", failure); }
        for(String reference:references)if(!definitions.containsKey(reference))
            throw new IOException("De kattenruntime is verouderd of onvolledig: " + reference + ". Werk de patcher bij via Run in Android Studio en maak een nieuwe patch.");
        if (!application || !runtime || initCalls != 1 || avatarCalls == 0)
            throw new IOException("De opstartpatch of kattenruntime is onvolledig.");
    }
}
