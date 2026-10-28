import be.smartschoolkatten.patcher.engine.ApkSet;
import java.io.File;

/** Desktop equivalent of the patcher's complete-package operation. */
public final class PatchSetCli {
    public static void main(String[] args) throws Exception {
        if (args.length != 4) throw new IllegalArgumentException("source.apks output.apks assets-directory private-signing-directory");
        final float[] completed={-1f};
        java.util.Map<String,Float> dexStart=new java.util.HashMap<>(),dexEnd=new java.util.HashMap<>();
        ApkSet.patch(new File(args[0]), new File(args[1]), new File(args[2]), new File(args[3]), new be.smartschoolkatten.patcher.engine.KattenPatcher.Progress() {
            public void update(String message) {System.out.println(message);}
            public void update(String message,float fraction) {
                if(!Float.isFinite(fraction) || fraction<completed[0] || fraction<0f || fraction>1f)
                    throw new AssertionError("Invalid or regressing patch progress: "+fraction+" after "+completed[0]);
                if(completed[0]<0f && fraction!=0f)throw new AssertionError("Patch progress did not start at zero");
                completed[0]=fraction;
                if(message.matches("Kattenprofielen aanpassen \\(classes(3|4)?\\.dex\\)…")) {
                    dexStart.putIfAbsent(message,fraction);dexEnd.put(message,fraction);
                }
                System.out.println(Math.round(fraction*100)+"% "+message);
            }
        });
        if(completed[0]!=1f)throw new AssertionError("Patch progress did not complete");
        if(dexStart.size()!=3)throw new AssertionError("Heavy DEX progress phases missing");
        for(String phase:dexStart.keySet())if(Math.abs(dexEnd.get(phase)-dexStart.get(phase)-0.25f)>0.00001f)
            throw new AssertionError("Heavy DEX did not receive 25%: "+phase);
        System.out.println("PASS DEX 1, 3 and 4 each account for 25% of total patch progress");
        System.out.println("PASS patch progress starts at zero, stays monotonic/in range and completes at 100%");
    }
}
