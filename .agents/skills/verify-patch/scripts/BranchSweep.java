import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.MethodImplementation;
import com.android.tools.smali.dexlib2.iface.TryBlock;
import com.android.tools.smali.dexlib2.iface.ExceptionHandler;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.iface.instruction.OffsetInstruction;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Whole-dex branch-offset validity sweep.
 *
 * A clean build plus a correct-looking instruction dump does not prove a branch offset is valid.
 * For every method this walks the instruction list accumulating code units, then checks that:
 *   - every OffsetInstruction's (its own offset + code offset) lands on an instruction start
 *   - every try-block start, end and handler address lands on an instruction start
 *
 * A branch into the middle of an instruction is what ART rejects at class-load time with
 * "VerifyError: ... target dex pc 0xN is not at instruction start".
 */
public final class BranchSweep {

    public static void main(String[] args) throws Exception {
        int badMethods = 0;
        int methods = 0;
        int classes = 0;
        List<String> failures = new ArrayList<>();

        for (String path : args) {
            DexBackedDexFile dex = DexBackedDexFile.fromInputStream(
                    Opcodes.getDefault(),
                    new BufferedInputStream(new FileInputStream(path)));

            for (ClassDef classDef : dex.getClasses()) {
                classes++;
                for (Method method : classDef.getMethods()) {
                    MethodImplementation impl = method.getImplementation();
                    if (impl == null) continue;
                    methods++;

                    List<Instruction> instructions = new ArrayList<>();
                    List<Integer> offsets = new ArrayList<>();
                    Set<Integer> starts = new HashSet<>();
                    int offset = 0;
                    for (Instruction instruction : impl.getInstructions()) {
                        instructions.add(instruction);
                        offsets.add(offset);
                        starts.add(offset);
                        offset += instruction.getCodeUnits();
                    }
                    int end = offset;

                    List<String> problems = new ArrayList<>();

                    for (int i = 0; i < instructions.size(); i++) {
                        Instruction instruction = instructions.get(i);
                        if (!(instruction instanceof OffsetInstruction)) continue;
                        int target = offsets.get(i) + ((OffsetInstruction) instruction).getCodeOffset();
                        if (!starts.contains(target)) {
                            problems.add(String.format(
                                    "%s at 0x%x targets 0x%x (not an instruction start)",
                                    instruction.getOpcode().name, offsets.get(i), target));
                        }
                    }

                    for (TryBlock<? extends ExceptionHandler> tryBlock : impl.getTryBlocks()) {
                        int start = tryBlock.getStartCodeAddress();
                        int stop = start + tryBlock.getCodeUnitCount();
                        if (!starts.contains(start)) {
                            problems.add(String.format("try start 0x%x not an instruction start", start));
                        }
                        if (!starts.contains(stop) && stop != end) {
                            problems.add(String.format("try end 0x%x not an instruction start", stop));
                        }
                        for (ExceptionHandler handler : tryBlock.getExceptionHandlers()) {
                            int address = handler.getHandlerCodeAddress();
                            if (!starts.contains(address)) {
                                problems.add(String.format(
                                        "handler 0x%x not an instruction start", address));
                            }
                        }
                    }

                    if (!problems.isEmpty()) {
                        badMethods++;
                        failures.add(classDef.getType() + "->" + method.getName()
                                + " : " + String.join("; ", problems));
                    }
                }
            }
        }

        System.out.printf("swept %d classes, %d methods with code%n", classes, methods);
        if (failures.isEmpty()) {
            System.out.println("OK: every branch offset, try range and handler lands on an instruction start");
        } else {
            System.out.printf("FAIL: %d method(s) with invalid offsets%n", badMethods);
            for (String failure : failures) System.out.println("  " + failure);
            System.exit(1);
        }
    }
}
