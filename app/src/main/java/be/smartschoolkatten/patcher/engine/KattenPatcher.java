package be.smartschoolkatten.patcher.engine;

import com.android.tools.smali.dexlib2.*;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.*;
import com.android.tools.smali.dexlib2.iface.instruction.*;
import com.android.tools.smali.dexlib2.iface.reference.*;
import com.android.tools.smali.dexlib2.immutable.*;
import com.android.tools.smali.dexlib2.immutable.reference.*;
import com.android.tools.smali.dexlib2.builder.*;
import com.android.tools.smali.dexlib2.builder.instruction.*;
import com.android.tools.smali.dexlib2.writer.pool.DexPool;
import com.android.tools.smali.dexlib2.writer.io.MemoryDataStore;
import com.android.apksig.*;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.*;

/** A local, version-specific APK patch. No upload of the source APK is needed. */
public final class KattenPatcher {
    public static final String PACKAGE = "be.smartschoolkatten.mobile";
    public static final int PATCH_REVISION = 38;
    private static final String ORIGINAL = "be.smartschool.mobile";
    private static final String RUNTIME = "Lbe/smartschoolkatten/runtime/KattenRuntime;";
    public interface Progress {
        void update(String message);
        default void update(String message,float fraction) {update(message);}
    }
    private int avatarHooks, applicationHooks, authorityHooks;
    private final List<ClassDef> overflowClasses = new ArrayList<>();
    private final Map<String,String> renamed = new HashMap<>();
    public KattenPatcher() {
        for(String suffix:Arrays.asList("androidx-startup","provider","IMGLYAutoInit","firebaseinitprovider","SentryInitProvider","SentryPerformanceProvider","SentryNdkPreloadProvider","DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"))
            renamed.put(ORIGINAL+"."+suffix,PACKAGE+"."+suffix);
    }
    public void patch(File input, File output, File assets, File identityDirectory, Progress progress) throws Exception {
        patchBase(input, output, assets, identityDirectory, progress, false);
    }
    void patchBase(File input, File output, File assets, File identityDirectory, Progress progress, boolean completeSet) throws Exception {
        avatarHooks=0;applicationHooks=0;authorityHooks=0;overflowClasses.clear();
        File unsigned=File.createTempFile("katten-unsigned-", ".apk",output.getParentFile());
        try(ZipFile original=new ZipFile(input); ZipOutputStream zip=new ZipOutputStream(new FileOutputStream(unsigned))) {
            zip.setLevel(Deflater.BEST_SPEED);
            String phase="Tweede app en icoon maken…";
            progress.update(phase,0.02f);
            long otherBytes=0;
            int heavyDexCount=0;
            for(ZipEntry entry:Collections.list(original.entries())) {
                if(heavyDex(entry.getName()))heavyDexCount++;
                else otherBytes+=Math.max(0,entry.getSize());
            }
            // The base covers 90% of the whole patch: (0.25 / 0.90) * 0.90 = 25% per heavy DEX.
            double heavyWeight=0.25/0.90;
            double otherWeight=0.90-heavyDexCount*heavyWeight;
            double processedWeight=0;
            int lastPercent=-1;
            int dexCount=0;
            Enumeration<? extends ZipEntry> entries=original.entries();
            while(entries.hasMoreElements()) {
                if(Thread.currentThread().isInterrupted()) throw new InterruptedException();
                ZipEntry entry=entries.nextElement();String name=entry.getName();
                if(!name.matches("classes[0-9]*\\.dex"))phase="Appbestanden verwerken…";
                if(entry.isDirectory() || name.matches("META-INF/[^/]+\\.(SF|RSA|DSA|EC|MF)") || name.equals("META-INF/MANIFEST.MF")) continue;
                byte[] data;
                try(InputStream in=original.getInputStream(entry)){data=read(in);}
                if(name.equals("AndroidManifest.xml")) {
                    if (!completeSet && BinaryResources.requiresSplits(data))
                        throw new IOException("Deze download is alleen de basis-APK. De verplichte processor- en scherm-APK's ontbreken. Een volledige APK of ondersteuning voor split-APK's is nodig om deze versie te installeren.");
                    data=BinaryResources.rewrite(data,s->s.equals(ORIGINAL)?PACKAGE:renamed.getOrDefault(s,s),null);
                    data=BinaryResources.withoutEditorComponents(data);
                } else if(name.equals("resources.arsc")) {
                    data=BinaryResources.rewrite(data,s->{
                        if(s.equals("Smartschool"))return "Smartschool Katten";
                        if(s.equals("res/drawable/ic_launcher_foreground.xml"))return "res/drawable/ic_launcher_foreground.jpg";
                        if(s.equals("res/drawable/ic_smartschool_app_logo.xml"))return "res/drawable/katten_app_logo.png";
                        if(s.equals("res/drawable/splash_logo.xml"))return "res/drawable/katten_splash_logo.png";
                        if(s.equals("res/drawable/splash_logo_animated.xml"))return "res/drawable/katten_splash_logo_animated.png";
                        return s;
                    },PACKAGE);
                } else if(name.matches("classes[0-9]*\\.dex")) {
                    dexCount++;
                    phase="Kattenprofielen aanpassen ("+name+")…";
                    progress.update(phase,(float)(0.02+processedWeight));
                    data=patchDex(data);
                } else if(name.equals("res/drawable/ic_smartschool_app_logo.xml")) {
                    name="res/drawable/katten_app_logo.png";data=Files.readAllBytes(new File(assets,"in-app-logo.png").toPath());
                } else if(name.equals("res/drawable/splash_logo.xml") || name.equals("res/drawable/splash_logo_animated.xml")) {
                    name=name.endsWith("splash_logo.xml")?"res/drawable/katten_splash_logo.png":"res/drawable/katten_splash_logo_animated.png";
                    data=Files.readAllBytes(new File(assets,"splash-logo.png").toPath());
                } else if(name.equals("res/drawable/ic_launcher_foreground.xml")) {
                    name="res/drawable/ic_launcher_foreground.jpg";data=Files.readAllBytes(new File(assets,"icon.jpg").toPath());
                } else if(name.equals("res/drawable/ic_launcher_background.xml")) {
                    data=Files.readAllBytes(new File(assets,"icon-background.xml").toPath());
                } else if(name.matches("res/mipmap-anydpi-v26/ic_launcher(_round)?\\.xml")) {
                    data=Files.readAllBytes(new File(assets,"icon-adaptive.xml").toPath());
                } else if(name.matches("res/mipmap-[^/]+/ic_launcher(_round)?\\.png")) {
                    data=Files.readAllBytes(new File(assets,"icon.jpg").toPath());
                }
                put(zip,name,data,name.equals("resources.arsc"));
                processedWeight+=heavyDex(entry.getName())?heavyWeight:otherWeight*Math.max(0,entry.getSize())/Math.max(1,otherBytes);
                float completed=(float)(0.02+processedWeight);
                if((int)(completed*100)!=lastPercent) {
                    lastPercent=(int)(completed*100);progress.update(phase,completed);
                }
            }
            if(applicationHooks<1 || avatarHooks==0)throw new IOException("Deze APK heeft niet de verwachte 12.3.0-patchpunten (app="+applicationHooks+", avatars="+avatarHooks+").");
            progress.update("Kattenstreak en favorieten toevoegen…",0.94f);
            DexBackedDexFile payload=new DexBackedDexFile(null,RuntimePayload.data());
            List<ClassDef> added=new ArrayList<>(overflowClasses);added.addAll(payload.getClasses());
            MemoryDataStore payloadStore=new MemoryDataStore();
            DexPool.writeTo(payloadStore,new ImmutableDexFile(payload.getOpcodes(),added));
            put(zip,"classes"+(dexCount+1)+".dex",payloadStore.getData(),false);
            put(zip,"assets/katten-urls.txt",Files.readAllBytes(new File(assets,"cat-urls.txt").toPath()),false);
            put(zip,"assets/katten-patch.txt",("Smartschool Katten / 12.3.0 / revision="+PATCH_REVISION+" / avatarhooks="+avatarHooks).getBytes(java.nio.charset.StandardCharsets.UTF_8),false);
        } catch(Exception error) { unsigned.delete();throw error; }
        try {
            progress.update("APK ondertekenen en handtekening controleren…",0.96f);
            sign(unsigned, output, identityDirectory);
            progress.update("Veilige opstart en kattenpatch controleren…",0.98f);
            PatchValidation.verify(output);
            progress.update("Smartschool Katten staat klaar ("+avatarHooks+" avatarpunten).",1f);
        } catch(Exception error) {output.delete();throw error;}
        finally{unsigned.delete();}
    }
    private static boolean heavyDex(String name) {
        return name.equals("classes.dex") || name.equals("classes3.dex") || name.equals("classes4.dex");
    }
    static void sign(File unsigned, File output, File identityDirectory) throws Exception {
        SigningIdentity identity = SigningIdentity.load(identityDirectory);
        ApkSigner.SignerConfig signer = new ApkSigner.SignerConfig.Builder("katten", identity.key,
            Collections.singletonList(identity.certificate)).build();
        new ApkSigner.Builder(Collections.singletonList(signer)).setInputApk(unsigned).setOutputApk(output)
            .setMinSdkVersion(32).setV1SigningEnabled(false).setV2SigningEnabled(true)
            .setV3SigningEnabled(true).setV4SigningEnabled(false).setLibraryPageAlignmentBytes(16384).build().sign();
        ApkVerifier.Result verified = new ApkVerifier.Builder(output).setMinCheckedPlatformVersion(32).build().verify();
        if (!verified.isVerified()) throw new IOException("Ongeldige APK-handtekening: " + verified.getErrors());
    }
    void patchSplit(File input, File output, File identityDirectory) throws Exception {
        File unsigned = File.createTempFile("katten-split-", ".apk", output.getParentFile());
        try {
            try (ZipFile original = new ZipFile(input); ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(unsigned))) {
                zip.setLevel(Deflater.BEST_SPEED);
                Enumeration<? extends ZipEntry> entries = original.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement(); String name = entry.getName();
                    if (entry.isDirectory() || name.matches("META-INF/[^/]+\\.(SF|RSA|DSA|EC|MF)") || name.equals("META-INF/MANIFEST.MF")) continue;
                    byte[] data;
                    try (InputStream in = original.getInputStream(entry)) { data = read(in); }
                    if (name.equals("AndroidManifest.xml")) {
                        data = BinaryResources.rewrite(data, s -> s.equals(ORIGINAL) ? PACKAGE : renamed.getOrDefault(s, s), null);
                        data = BinaryResources.withoutEditorComponents(data);
                    } else if (name.equals("resources.arsc"))
                        data = BinaryResources.rewrite(data, s -> s.equals("Smartschool") ? "Smartschool Katten" : s, PACKAGE);
                    // Keep native libraries uncompressed; apksig aligns them to 16 KiB.
                    put(zip, name, data, name.equals("resources.arsc") || name.matches("lib/[^/]+/[^/]+\\.so"));
                }
            }
            sign(unsigned, output, identityDirectory);
        } catch (Exception failure) { output.delete(); throw failure; }
        finally { unsigned.delete(); }
    }
    private byte[] patchDex(byte[] data) throws Exception {
        DexBackedDexFile dex=new DexBackedDexFile(null,data);
        List<ClassDef> classes=new ArrayList<>();
        for(ClassDef cls:dex.getClasses()) {
            List<Method> methods=new ArrayList<>();
            boolean classChanged=false;
            for(Method method:cls.getMethods()) {
                MethodImplementation implementation=method.getImplementation();
                if(implementation==null){methods.add(method);continue;}
                if (editorAction(cls, method)) {
                    MutableMethodImplementation disabled = new MutableMethodImplementation(implementation.getRegisterCount());
                    disabled.addInstruction(new BuilderInstruction35c(Opcode.INVOKE_STATIC,0,0,0,0,0,0,
                        new ImmutableMethodReference(RUNTIME,"editorUnavailable",Collections.emptyList(),"V")));
                    disabled.addInstruction(new BuilderInstruction21c(Opcode.SGET_OBJECT,0,
                        new ImmutableFieldReference("Ld5c;","a","Ld5c;")));
                    disabled.addInstruction(new BuilderInstruction11x(Opcode.RETURN_OBJECT,0));
                    methods.add(new ImmutableMethod(method.getDefiningClass(),method.getName(),method.getParameters(),method.getReturnType(),method.getAccessFlags(),method.getAnnotations(),method.getHiddenApiRestrictions(),disabled));
                    classChanged=true; continue;
                }
                boolean homeView=cls.getType().equals("Lbe/smartschool/mobile/modules/home/presentation/HomeFragment;")
                    && method.getName().equals("D0") && method.getReturnType().equals("V")
                    && method.getParameterTypes().equals(Arrays.asList("Landroid/view/View;","Landroid/os/Bundle;"));
                boolean needsEdit=homeView || cls.getType().equals("Lbe/smartschool/mobile/Application;") && method.getName().equals("onCreate");
                if(!needsEdit) for(Instruction instruction:implementation.getInstructions()) {
                    if(!(instruction instanceof ReferenceInstruction))continue;
                    Reference reference=((ReferenceInstruction)instruction).getReference();
                    if(replacementCall(cls,method,reference)!=null){needsEdit=true;break;}
                    if(avatarMethod(method) && instruction.getOpcode()==Opcode.IGET_OBJECT && reference instanceof FieldReference && isAvatarField((FieldReference)reference)) {needsEdit=true;break;}
                    if(reference instanceof StringReference && renamed.containsKey(((StringReference)reference).getString())) {needsEdit=true;break;}
                }
                if(!needsEdit){methods.add(method);continue;}
                MutableMethodImplementation body=new MutableMethodImplementation(implementation);
                boolean changed=false;
                // Iterate backwards so inserted instructions do not disturb remaining indices.
                for(int i=body.getInstructions().size()-1;i>=0;i--) {
                    BuilderInstruction instruction=body.getInstructions().get(i);
                    if(instruction instanceof ReferenceInstruction) {
                        Reference reference=((ReferenceInstruction)instruction).getReference();
                        MethodReference callReplacement=replacementCall(cls,method,reference);
                        if(callReplacement!=null) {
                            if(instruction instanceof RegisterRangeInstruction) {
                                RegisterRangeInstruction registers=(RegisterRangeInstruction)instruction;
                                body.replaceInstruction(i,new BuilderInstruction3rc(Opcode.INVOKE_STATIC_RANGE,registers.getStartRegister(),registers.getRegisterCount(),callReplacement));
                            } else if(instruction instanceof FiveRegisterInstruction) {
                                FiveRegisterInstruction registers=(FiveRegisterInstruction)instruction;
                                body.replaceInstruction(i,new BuilderInstruction35c(Opcode.INVOKE_STATIC,registers.getRegisterCount(),registers.getRegisterC(),registers.getRegisterD(),registers.getRegisterE(),registers.getRegisterF(),registers.getRegisterG(),callReplacement));
                            } else throw new IOException("Onverwacht formaat voor kattenweergave-aanroep.");
                            changed=true;
                        } else if(instruction.getOpcode()==Opcode.IGET_OBJECT && reference instanceof FieldReference) {
                            FieldReference field=(FieldReference)reference;
                            if(avatarMethod(method) && isAvatarField(field)) {
                                int register=((OneRegisterInstruction)instruction).getRegisterA();
                                String action=field.getDefiningClass().startsWith("Lbe/smartschool/mobile/modules/planner/")?"plannerAvatar":
                                    field.getDefiningClass().equals("Lgac;")?"studentCardAvatar":
                                    field.getDefiningClass().equals("Lbe/smartschool/mobile/model/User;")?"ownAvatar":"avatar";
                                ImmutableMethodReference hook=new ImmutableMethodReference(RUNTIME,action,Collections.singletonList("Ljava/lang/String;"),"Ljava/lang/String;");
                                body.addInstruction(i+1,new BuilderInstruction3rc(Opcode.INVOKE_STATIC_RANGE,register,1,hook));
                                body.addInstruction(i+2,new BuilderInstruction11x(Opcode.MOVE_RESULT_OBJECT,register));
                                avatarHooks++;changed=true;
                            }
                        } else if(reference instanceof StringReference && (instruction.getOpcode()==Opcode.CONST_STRING || instruction.getOpcode()==Opcode.CONST_STRING_JUMBO)) {
                            String original=((StringReference)reference).getString();String replacement=renamed.getOrDefault(original,original);
                            if(!replacement.equals(original)){
                                int register=((OneRegisterInstruction)instruction).getRegisterA();
                                // Jumbo also avoids overflow if new references push a DEX over 64K strings.
                                body.replaceInstruction(i,new BuilderInstruction31c(Opcode.CONST_STRING_JUMBO,register,new ImmutableStringReference(replacement)));
                                if(!replacement.equals(original))authorityHooks++;changed=true;
                            }
                        }
                    }
                }
                if(homeView) {
                    body.addInstruction(0,new BuilderInstruction3rc(Opcode.INVOKE_STATIC_RANGE,implementation.getRegisterCount()-2,1,
                        new ImmutableMethodReference(RUNTIME,"home",Collections.singletonList("Landroid/view/View;"),"V")));
                    changed=true;
                }
                if(cls.getType().equals("Lbe/smartschool/mobile/Application;") && method.getName().equals("onCreate")) {
                    // R8 reuses p0 as a String/Throwable on some exit paths. Only
                    // at method entry is the incoming receiver guaranteed to be Application.
                    int thisRegister=implementation.getRegisterCount()-1;
                    body.addInstruction(0,new BuilderInstruction3rc(Opcode.INVOKE_STATIC_RANGE,thisRegister,1,
                        new ImmutableMethodReference(RUNTIME,"init",Collections.singletonList("Landroid/app/Application;"),"V")));
                    applicationHooks++;changed=true;
                }
                classChanged |= changed;
                methods.add(changed?new ImmutableMethod(method.getDefiningClass(),method.getName(),method.getParameters(),method.getReturnType(),method.getAccessFlags(),method.getAnnotations(),method.getHiddenApiRestrictions(),body):method);
            }
            classes.add(classChanged?new ImmutableClassDef(cls.getType(),cls.getAccessFlags(),cls.getSuperclass(),cls.getInterfaces(),cls.getSourceFile(),cls.getAnnotations(),cls.getFields(),methods):cls);
        }
        // Some original DEX files already contain 65,536 method references.
        // Move complete classes to an additional DEX rather than overflowing
        // reference indices when the avatar helper adds a new method reference.
        DexPool pool=new DexPool(dex.getOpcodes());
        for(ClassDef cls:classes) {
            pool.mark();pool.internClass(cls);
            if(pool.hasOverflowed(65000)) {pool.reset();overflowClasses.add(cls);}
        }
        MemoryDataStore store=new MemoryDataStore();
        pool.writeTo(store);
        return store.getData();
    }
    private static MethodReference replacementCall(ClassDef cls,Method method,Reference reference) {
        if(!(reference instanceof MethodReference) || cls.getType().startsWith("Lly/img/"))return null;
        MethodReference call=(MethodReference)reference;
        if(call.getDefiningClass().equals("Lo26;") && call.getName().equals("P")
            && call.getReturnType().equals("V") && call.getParameterTypes().equals(Arrays.asList("Lbe/smartschool/mobile/ui/components/header/SmscHeader;","Lbe/smartschool/mobile/components/quicksearch/data/QuickSearchGraphic;")))
            return new ImmutableMethodReference(RUNTIME,"plannerHeader",Arrays.asList("Ljava/lang/Object;","Ljava/lang/Object;"),"V");
        if(call.getDefiningClass().equals("Lyx5;") && call.getName().equals("a")
            && call.getReturnType().equals("V") && call.getParameterTypes().equals(Arrays.asList("Ljava/lang/Object;","Ljava/lang/String;","Lhx6;","Lb34;","Lhn;","Lsz1;","F","Lcx0;","Ldm1;","I","I")))
            return new ImmutableMethodReference(RUNTIME,"composeImage",Arrays.asList("Ljava/lang/Object;","Ljava/lang/String;","Ljava/lang/Object;","Ljava/lang/Object;","Ljava/lang/Object;","Ljava/lang/Object;","F","Ljava/lang/Object;","Ljava/lang/Object;","I","I"),"V");
        if(call.getDefiningClass().equals("Lkw9;") && call.getName().equals("y")
            && call.getReturnType().equals("V") && call.getParameterTypes().equals(Arrays.asList("Lwgb;","Lls0;")))
            return new ImmutableMethodReference(RUNTIME,"glideTarget",Arrays.asList("Ljava/lang/Object;","Ljava/lang/Object;","Ljava/lang/Object;"),"V");
        if(call.getDefiningClass().equals("Lkw9;") && call.getName().equals("z")
            && call.getReturnType().equals("V") && call.getParameterTypes().equals(Collections.singletonList("Landroid/widget/ImageView;")))
            return new ImmutableMethodReference(RUNTIME,"glideImage",Arrays.asList("Ljava/lang/Object;","Landroid/widget/ImageView;"),"V");
        if(call.getDefiningClass().equals("Ld94;") && call.getName().equals("a")
            && call.getReturnType().equals("V") && call.getParameterTypes().equals(Arrays.asList("Ld94;","Ljava/lang/String;","Landroid/widget/ImageView;","Ljava/lang/Integer;","Z","I")))
            return new ImmutableMethodReference(RUNTIME,"directImage",Arrays.asList("Ljava/lang/Object;","Ljava/lang/String;","Landroid/widget/ImageView;","Ljava/lang/Integer;","Z","I"),"V");
        if(call.getDefiningClass().equals("Lul0;") && call.getName().equals("U")
            && call.getReturnType().equals("V") && call.getParameterTypes().equals(Arrays.asList("Landroid/widget/ImageView;","Ljava/lang/String;","Z")))
            return new ImmutableMethodReference(RUNTIME,"avatarImage",call.getParameterTypes(),"V");
        if(call.getDefiningClass().equals("Leo0;") && call.getReturnType().equals("V")) {
            List<? extends CharSequence> parameters=call.getParameterTypes();
            if(parameters.size()>=2 && parameters.get(0).equals("Landroid/widget/ImageView;") && parameters.get(1).equals("Ljava/lang/String;")
                && Arrays.asList("a","b","c","d").contains(call.getName()))
                return new ImmutableMethodReference(RUNTIME,"image"+call.getName().toUpperCase(Locale.ROOT),parameters,"V");
        }
        if(cls.getType().equals("Lrc5;") && method.getName().equals("b") && method.getParameterTypes().size()==6
            && call.getDefiningClass().equals("Lb66;") && call.getName().equals("b")
            && call.getParameterTypes().equals(Arrays.asList("Lhx6;","Ljava/lang/String;","Lv75;","La34;","Z","Ldm1;","I","I")))
            return new ImmutableMethodReference(RUNTIME,"configureCard",Arrays.asList("Ljava/lang/Object;","Ljava/lang/String;","Ljava/lang/Object;","Ljava/lang/Object;","Z","Ljava/lang/Object;","I","I"),"V");
        return null;
    }
    private static boolean isAvatarField(FieldReference field) {
        String name=field.getName().toLowerCase(Locale.ROOT);
        if(!field.getType().equals("Ljava/lang/String;"))return false;
        // Verified against 12.3.0 data-class toString() labels: Avatar.avatar,
        // PupilUiModel.avatarUrl, and SmscPillModel.iconLeftAvatarUrl.
        if(field.getDefiningClass().equals("Lik0;") && field.getName().equals("a"))return true;
        if(field.getDefiningClass().equals("Ldi9;") && field.getName().equals("e"))return true;
        if(field.getDefiningClass().equals("Lnza;") && field.getName().equals("c"))return true;
        // UserCardState.userPictureUrl: own student card, separate from other pupil cards.
        if(field.getDefiningClass().equals("Lgac;") && field.getName().equals("a"))return true;
        // Planner participants/calendars and results Feedback.teacher use pictureUrl.
        if(name.equals("pictureurl") && (field.getDefiningClass().startsWith("Lbe/smartschool/mobile/modules/planner/")
            || field.getDefiningClass().equals("Lbe/smartschool/mobile/model/ApiUser;")))return true;
        return field.getDefiningClass().startsWith("Lbe/smartschool/mobile/") &&
            (name.equals("avatarurl") || name.equals("iconleftavatarurl") || name.equals("userpictureurl"));
    }
    private static boolean avatarMethod(Method method) {
        // Cat preferences must not change data-class identity, copying or persisted
        // parcel values. Render through getters/components and actual UI reads.
        String name = method.getName();
        return !Arrays.asList("equals", "hashCode", "toString", "copy", "copy$default", "writeToParcel", "<init>").contains(name);
    }
    // Smartschool 12.3.0 obfuscates kotlin.Unit.INSTANCE as d5c.a.
    private static boolean editorAction(ClassDef cls, Method method) {
        if (!cls.getType().startsWith("Lbe/smartschool/mobile/") || !method.getName().equals("invokeSuspend")
            || !"Lkotlin/coroutines/jvm/internal/SuspendLambda;".equals(cls.getSuperclass())) return false;
        boolean editor=false, unit=false;
        for (Instruction instruction : method.getImplementation().getInstructions()) {
            if (!(instruction instanceof ReferenceInstruction)) continue;
            Reference ref=((ReferenceInstruction)instruction).getReference();
            if (ref instanceof MethodReference) {
                MethodReference call=(MethodReference)ref;
                editor |= call.getDefiningClass().startsWith("Lly/img/android/")
                    || (call.getDefiningClass().equals("Lbe/smartschool/mobile/components/photoeditor/a;") && call.getName().equals("a"));
            } else if (ref instanceof TypeReference) editor |= ((TypeReference)ref).getType().startsWith("Lly/img/android/");
            else if (ref instanceof FieldReference) {
                FieldReference field=(FieldReference)ref;
                editor |= field.getDefiningClass().startsWith("Lly/img/android/");
                unit |= field.getDefiningClass().equals("Ld5c;") && field.getName().equals("a") && field.getType().equals("Ld5c;");
            }
        }
        return editor && unit;
    }
    private static byte[] read(InputStream input)throws IOException {ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[65536];for(int n;(n=input.read(b))!=-1;)out.write(b,0,n);return out.toByteArray();}
    private static void put(ZipOutputStream out,String name,byte[] bytes,boolean stored)throws IOException {
        ZipEntry entry=new ZipEntry(name);
        if(stored){CRC32 crc=new CRC32();crc.update(bytes);entry.setMethod(ZipEntry.STORED);entry.setSize(bytes.length);entry.setCompressedSize(bytes.length);entry.setCrc(crc.getValue());}
        out.putNextEntry(entry);out.write(bytes);out.closeEntry();
    }
}
