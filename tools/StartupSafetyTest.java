import be.smartschoolkatten.patcher.engine.PatchValidation;
import java.io.File;
import java.io.IOException;

/** Regression check for the reused p0 receiver and accidental model mutations. */
public final class StartupSafetyTest {
    public static void main(String[] args) throws Exception {
        PatchValidation.verify(new File(args[0]));
        System.out.println("PASS startup receiver: one init call at entry; model identity/serialization preserved");
        if (args.length > 1) {
            try {
                PatchValidation.verify(new File(args[1]));
                throw new AssertionError("Old unsafe patch unexpectedly accepted");
            } catch (IOException expected) {
                System.out.println("PASS old unsafe patch rejected: " + expected.getMessage());
            }
        }
    }
}
