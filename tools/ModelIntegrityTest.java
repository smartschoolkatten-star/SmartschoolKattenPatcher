import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.*;
import com.android.tools.smali.dexlib2.iface.instruction.*;
import com.android.tools.smali.dexlib2.util.ReferenceUtil;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** Compare actual model operations, independent of DEX reference numbering. */
public final class ModelIntegrityTest {
    private static final Set<String> OPERATIONS = new HashSet<>(Arrays.asList(
        "equals", "hashCode", "toString", "copy", "copy$default", "writeToParcel", "<init>"));
    public static void main(String[] args) throws Exception {
        Map<String, String> before = operations(args[0]), after = operations(args[1]);
        if (before.size() < 50) throw new AssertionError("Insufficient model coverage");
        for (Map.Entry<String, String> entry : before.entrySet())
            if (!entry.getValue().equals(after.get(entry.getKey())))
                throw new AssertionError("Model operation changed: " + entry.getKey());
        System.out.println("PASS " + before.size() + " model operations and photo-editor SDK methods unchanged");
    }
    private static Map<String, String> operations(String file) throws Exception {
        Map<String, String> result = new TreeMap<>();
        try (ZipFile zip = new ZipFile(file)) {
            for (ZipEntry entry : Collections.list(zip.entries())) {
                if (!entry.getName().matches("classes[0-9]*\\.dex")) continue;
                DexBackedDexFile dex;
                try (InputStream input = new BufferedInputStream(zip.getInputStream(entry))) {
                    dex = DexBackedDexFile.fromInputStream(null, input);
                }
                for (ClassDef cls : dex.getClasses()) {
                    boolean model = cls.getType().startsWith("Lbe/smartschool/mobile/model/")
                        || Arrays.asList("Lik0;", "Ldi9;", "Lnza;").contains(cls.getType());
                    boolean editorSdk = cls.getType().startsWith("Lly/img/android/");
                    if (!model && !editorSdk) continue;
                    for (Method method : cls.getMethods()) {
                        if ((!editorSdk && !OPERATIONS.contains(method.getName())) || method.getImplementation() == null) continue;
                        StringBuilder shape = new StringBuilder();
                        MethodImplementation body = method.getImplementation();
                        shape.append(body.getRegisterCount()).append(':').append(method.getAccessFlags());
                        for (Instruction instruction : body.getInstructions()) {
                            shape.append('\n').append(instruction.getOpcode()).append(':').append(instruction.getCodeUnits());
                            if (instruction instanceof OneRegisterInstruction) shape.append(" a").append(((OneRegisterInstruction) instruction).getRegisterA());
                            if (instruction instanceof TwoRegisterInstruction) shape.append(" b").append(((TwoRegisterInstruction) instruction).getRegisterB());
                            if (instruction instanceof ThreeRegisterInstruction) shape.append(" c").append(((ThreeRegisterInstruction) instruction).getRegisterC());
                            if (instruction instanceof FiveRegisterInstruction) {
                                FiveRegisterInstruction r = (FiveRegisterInstruction) instruction;
                                shape.append(" r").append(r.getRegisterCount()).append(':').append(r.getRegisterC())
                                    .append(':').append(r.getRegisterD()).append(':').append(r.getRegisterE())
                                    .append(':').append(r.getRegisterF()).append(':').append(r.getRegisterG());
                            }
                            if (instruction instanceof RegisterRangeInstruction) {
                                RegisterRangeInstruction r = (RegisterRangeInstruction) instruction;
                                shape.append(" range").append(r.getStartRegister()).append(':').append(r.getRegisterCount());
                            }
                            if (instruction instanceof ReferenceInstruction) shape.append(" ref").append(ReferenceUtil.getReferenceString(((ReferenceInstruction) instruction).getReference()));
                            if (instruction instanceof WideLiteralInstruction) shape.append(" lit").append(((WideLiteralInstruction) instruction).getWideLiteral());
                            if (instruction instanceof OffsetInstruction) shape.append(" offset").append(((OffsetInstruction) instruction).getCodeOffset());
                        }
                        for (TryBlock<? extends ExceptionHandler> block : body.getTryBlocks()) {
                            shape.append(" try").append(block.getStartCodeAddress()).append(':').append(block.getCodeUnitCount());
                            for (ExceptionHandler handler : block.getExceptionHandlers()) shape.append(':').append(handler.getExceptionType()).append(':').append(handler.getHandlerCodeAddress());
                        }
                        byte[] hash = MessageDigest.getInstance("SHA-256").digest(shape.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        result.put(ReferenceUtil.getMethodDescriptor(method), Base64.getEncoder().encodeToString(hash));
                    }
                }
            }
        }
        return result;
    }
}
