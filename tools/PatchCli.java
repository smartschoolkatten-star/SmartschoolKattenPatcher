import be.smartschoolkatten.patcher.engine.KattenPatcher;
import java.io.File;
public final class PatchCli {
    public static void main(String[] args) throws Exception {
        if(args.length!=4 && args.length!=5)throw new IllegalArgumentException("input.apk output.apk patch-assets signing-directory [target-package]");
        new KattenPatcher(args.length==5?args[4]:KattenPatcher.PACKAGE).patch(new File(args[0]),new File(args[1]),new File(args[2]),new File(args[3]),System.out::println);
    }
}
