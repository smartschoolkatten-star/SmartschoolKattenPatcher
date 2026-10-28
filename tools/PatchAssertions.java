import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.*;
import com.android.tools.smali.dexlib2.iface.instruction.*;
import com.android.tools.smali.dexlib2.iface.reference.*;
import com.android.tools.smali.dexlib2.Opcode;
import java.io.*;
import java.util.*;
import java.util.zip.*;
public final class PatchAssertions {
    public static void main(String[] args)throws Exception {
        be.smartschoolkatten.patcher.engine.PatchValidation.verify(new File(args[1]));
        Set<String> before=classes(args[0]),after=classes(args[1]);
        if(!after.containsAll(before))throw new AssertionError("Original classes lost");
        if(!after.contains("Lbe/smartschoolkatten/runtime/KattenRuntime;"))throw new AssertionError("Runtime missing");
        int avatars=0,init=0,editors=0,home=0,images=0,settings=0,extraImages=0,directImages=0,glideImages=0,composeImages=0,ownStudentCard=0;
        int plannerPhotos=0,resultTeacherPhotos=0,plannerHeaders=0;
        try(ZipFile zip=new ZipFile(args[1])) {
            for(ZipEntry e:Collections.list(zip.entries()))if(e.getName().matches("classes[0-9]*\\.dex")) {
                DexBackedDexFile dex=DexBackedDexFile.fromInputStream(null,new BufferedInputStream(zip.getInputStream(e)));
                if(dex.getMethodSection().size()>65536)throw new AssertionError("DEX method overflow");
                for(ClassDef cls:dex.getClasses())for(Method m:cls.getMethods())if(m.getImplementation()!=null) {
                    List<Instruction> instructions=new ArrayList<>();for(Instruction i:m.getImplementation().getInstructions())instructions.add(i);
                    for(int index=0;index<instructions.size();index++) {
                        Instruction i=instructions.get(index);
                        if(i instanceof ReferenceInstruction && ((ReferenceInstruction)i).getReference() instanceof MethodReference) {
                            MethodReference ref=(MethodReference)((ReferenceInstruction)i).getReference();
                            if(ref.getDefiningClass().equals("Lbe/smartschoolkatten/runtime/KattenRuntime;")) {
                                if(ref.getName().equals("init"))init++;
                                if(ref.getName().equals("plannerHeader"))plannerHeaders++;
                                if(ref.getName().equals("home")) {
                                    home++;
                                    if(!cls.getType().equals("Lbe/smartschool/mobile/modules/home/presentation/HomeFragment;") || !m.getName().equals("D0") || index!=0
                                        || !(i instanceof RegisterRangeInstruction)
                                        || ((RegisterRangeInstruction)i).getStartRegister()!=m.getImplementation().getRegisterCount()-2
                                        || ((RegisterRangeInstruction)i).getRegisterCount()!=1)
                                        throw new AssertionError("Unsafe home view hook");
                                }
                                if(ref.getName().equals("editorUnavailable"))editors++;
                                if(ref.getName().equals("avatarImage"))images++;
                                if(ref.getName().matches("image[A-D]"))extraImages++;
                                if(ref.getName().equals("directImage"))directImages++;
                                if(ref.getName().equals("glideImage") || ref.getName().equals("glideTarget"))glideImages++;
                                if(ref.getName().equals("composeImage")) {
                                    composeImages++;
                                    if(!cls.getType().equals("Lb66;"))throw new AssertionError("Unexpected Compose image caller "+cls.getType());
                                }
                                if(ref.getName().equals("configureCard")) {
                                    settings++;
                                    if(!cls.getType().equals("Lrc5;") || !m.getName().equals("b"))
                                        throw new AssertionError("Settings row outside ConfigureCard");
                                }
                                if(ref.getName().equals("avatar") || ref.getName().equals("ownAvatar") || ref.getName().equals("studentCardAvatar") || ref.getName().equals("plannerAvatar")) {
                                    if(ref.getName().equals("plannerAvatar"))plannerPhotos++;
                                    if(ref.getName().equals("avatar") && cls.getType().equals("Lbe/smartschool/mobile/model/ApiUser;") && m.getName().equals("getPictureUrl"))resultTeacherPhotos++;
                                    if(index>0 && instructions.get(index-1) instanceof ReferenceInstruction
                                        && ((ReferenceInstruction)instructions.get(index-1)).getReference().toString().equals("Lgac;->a:Ljava/lang/String;")) {
                                        if(!ref.getName().equals("studentCardAvatar") || !cls.getType().equals("Ldac;"))
                                            throw new AssertionError("Own student card uses random avatar or unexpected caller");
                                        ownStudentCard++;
                                    }
                                    if(index+1>=instructions.size() || instructions.get(index+1).getOpcode()!=Opcode.MOVE_RESULT_OBJECT)throw new AssertionError("Avatar result not consumed");
                                    avatars++;
                                }
                            }
                        }
                    }
                }
            }
            if(zip.getEntry("assets/katten-urls.txt")==null)throw new AssertionError("Cat links missing");
            if(zip.getEntry("res/drawable/ic_launcher_foreground.jpg")==null)throw new AssertionError("Icon missing");
        }
        if(ownStudentCard!=1)throw new AssertionError("Own student card favorite hook missing or duplicated");
        if(plannerPhotos==0 || resultTeacherPhotos!=1 || plannerHeaders==0)throw new AssertionError("Planner header/photo or results teacher pictureUrl hook missing");
        System.out.println("PASS planner photo hooks and results teacher ApiUser.getPictureUrl hook");
        System.out.println("PASS own student card uses favorite avatar in its UI binding");
        if(images<1 || settings!=1 || extraImages<1 || directImages!=9)throw new AssertionError("Avatar crop, direct loader or home settings row hook missing: direct="+directImages);
        System.out.println("PASS "+directImages+" direct image loader hooks, including default-argument delegation");
        if(glideImages<1)throw new AssertionError("Direct Glide image routes missing");
        System.out.println("PASS "+glideImages+" direct Glide ImageView routes");
        if(composeImages!=3)throw new AssertionError("Shared Compose/Coil image hooks missing: "+composeImages);
        System.out.println("PASS shared Compose/Coil image hook used by message and menu avatars");
        System.out.println("PASS "+images+" avatar loaders plus "+extraImages+" additional image loaders and one home settings row");
        if(home!=1)throw new AssertionError("Home streak hook missing or duplicated");
        System.out.println("PASS one home streak hook at entry with original View parameter");
        if(editors < 1)throw new AssertionError("Photo editor actions were not disabled");
        System.out.println("PASS " + editors + " host photo-editor actions disabled; licensed SDK retained");
        if(avatars<40 || init!=1)throw new AssertionError("Missing patch hooks");
        System.out.println("APK: "+before.size()+" originele classes behouden; "+avatars+" avatar-aanroepen en "+init+" initialisatie-aanroepen gecontroleerd");
    }
    private static Set<String> classes(String file)throws Exception {
        Set<String> classes=new HashSet<>();
        try(ZipFile zip=new ZipFile(file)){
            for(ZipEntry e:Collections.list(zip.entries()))if(e.getName().matches("classes[0-9]*\\.dex")) {
                DexBackedDexFile dex=DexBackedDexFile.fromInputStream(null,new BufferedInputStream(zip.getInputStream(e)));
                for(ClassDef cls:dex.getClasses())if(!classes.add(cls.getType()))throw new AssertionError("Duplicate class "+cls.getType());
            }
        }return classes;
    }
}
