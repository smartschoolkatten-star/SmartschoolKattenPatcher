import be.smartschoolkatten.patcher.engine.KattenPatcher;
import java.io.File;
public final class PatchCli {
    public static void main(String[] args) throws Exception {
        if(args.length<4 || args.length>6)throw new IllegalArgumentException("input.apk output.apk patch-assets signing-directory [target-package] [app-name]");
        new KattenPatcher(args.length>=5?args[4]:KattenPatcher.PACKAGE,args.length==6?args[5]:"Smartschool Katten").patch(new File(args[0]),new File(args[1]),new File(args[2]),new File(args[3]),System.out::println);
    }
}
