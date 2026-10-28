import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.*;
import java.io.*;import java.util.*;import java.util.zip.*;
/** Check reflection signatures against the actual supported host, including inherited statics. */
public final class UiHooksTest {
    static final Map<String,ClassDef> classes=new HashMap<>();
    public static void main(String[] args)throws Exception {
        try(ZipFile zip=new ZipFile(args[0])) {
            for(ZipEntry entry:Collections.list(zip.entries()))if(entry.getName().matches("classes[0-9]*\\.dex")) {
                DexBackedDexFile dex=DexBackedDexFile.fromInputStream(null,new BufferedInputStream(zip.getInputStream(entry)));
                for(ClassDef cls:dex.getClasses())classes.put(cls.getType(),cls);
            }
        }
        method("Lnc5;","b","V",true,"Lb34;","Lhx6;","Lb34;","Ldm1;","I","I");
        method("Lo26;","P","V",true,"Lbe/smartschool/mobile/ui/components/header/SmscHeader;","Lbe/smartschool/mobile/components/quicksearch/data/QuickSearchGraphic;");
        method("Lbe/smartschool/mobile/ui/components/header/SmscHeader;","getImage","Landroid/widget/ImageView;",false);
        method("Lbe/smartschool/mobile/ui/components/header/SmscHeader;","setIconRoundedBackground","V",false,"Z");
        method("Lbe/smartschool/mobile/components/quicksearch/data/QuickSearchGraphic;","getType","Ljava/lang/String;",false);
        method("Lbe/smartschool/mobile/components/quicksearch/data/QuickSearchGraphic;","getValue","Ljava/lang/String;",false);
        method("Lul0;","U","V",true,"Landroid/widget/ImageView;","Ljava/lang/String;","Z");
        method("Leo0;","a","V",true,"Landroid/widget/ImageView;","Ljava/lang/String;","Z");
        method("Leo0;","b","V",true,"Landroid/widget/ImageView;","Ljava/lang/String;","Ljava/lang/Integer;","Z");
        method("Leo0;","c","V",true,"Landroid/widget/ImageView;","Ljava/lang/String;","Ljava/lang/Integer;","I");
        method("Leo0;","d","V",true,"Landroid/widget/ImageView;","Ljava/lang/String;","Ljava/lang/Integer;");
        method("Ld94;","a","V",true,"Ld94;","Ljava/lang/String;","Landroid/widget/ImageView;","Ljava/lang/Integer;","Z","I");
        method("Lkw9;","z","V",false,"Landroid/widget/ImageView;");
        method("Lkw9;","y","V",false,"Lwgb;","Lls0;");
        method("Lyx5;","a","V",true,"Ljava/lang/Object;","Ljava/lang/String;","Lhx6;","Lb34;","Lhn;","Lsz1;","F","Lcx0;","Ldm1;","I","I");
        boolean crop=false;for(Field f:classes.get("Lqz1;").getFields())if(f.getName().equals("a") && f.getType().equals("Liv0;") && (f.getAccessFlags() & 9)==9)crop=true;
        if(!crop)throw new AssertionError("Compose Crop singleton absent");
        boolean targetView=false;for(Field f:classes.get("Lvw0;").getFields())if(f.getName().equals("a") && f.getType().equals("Landroid/widget/ImageView;"))targetView=true;
        if(!targetView)throw new AssertionError("Glide ImageView target field absent");
        boolean model=false;for(Field f:classes.get("Lkw9;").getFields())if(f.getName().equals("M") && f.getType().equals("Ljava/lang/Object;"))model=true;
        if(!model)throw new AssertionError("Glide request model field absent");
        method("Lde/hdodenhof/circleimageview/CircleImageView;","setDisableCircularTransformation","V",false,"Z");
        method("La34;","invoke","Ljava/lang/Object;",false);
        method("Lb34;","invoke","Ljava/lang/Object;",false,"Ljava/lang/Object;");
        boolean unit=false;for(Field f:classes.get("Ld5c;").getFields())if(f.getName().equals("a") && f.getType().equals("Ld5c;") && (f.getAccessFlags() & 9)==9)unit=true;
        if(!unit)throw new AssertionError("Unit singleton absent");
        System.out.println("PASS actual host AndroidView, callback, inherited image loader and Unit API signatures");
    }
    static void method(String owner,String name,String result,boolean isStatic,String... parameters) {
        ClassDef cls=classes.get(owner);
        if(cls==null)throw new AssertionError("Missing class "+owner);
        if((cls.getAccessFlags() & 1)==0)throw new AssertionError("Nonpublic reflection class "+owner);
        while(cls!=null) {
            for(Method m:cls.getMethods())if(m.getName().equals(name) && m.getReturnType().equals(result) && m.getParameterTypes().equals(Arrays.asList(parameters))) {
                if((m.getAccessFlags() & 1)==0 || ((m.getAccessFlags() & 8)!=0)!=isStatic)throw new AssertionError("Reflection method inaccessible: "+owner+" "+name);
                return;
            }
            cls=classes.get(cls.getSuperclass());
        }
        throw new AssertionError("Missing method "+owner+" "+name+Arrays.toString(parameters));
    }
}
