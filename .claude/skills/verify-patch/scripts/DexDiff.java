import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.Field;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.MethodImplementation;
import com.android.tools.smali.dexlib2.iface.TryBlock;
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.iface.instruction.OffsetInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.ThreeRegisterInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.WideLiteralInstruction;
import com.android.tools.smali.dexlib2.util.ReferenceUtil;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Lists what a patch changed: each class of the patched dex files that differs from the original,
 * and for each changed method an instruction diff.
 *
 * Usage: java -cp <dexlib2>:<guava> DexDiff.java <original dex dir> <patched dex dir> [new-class prefix]
 *
 * Classes whose type starts with the prefix (default "Lapp/andrewliang/") are new extension code:
 * they are counted, not printed. Branch targets print as labels :L0, :L1 in address order, so an
 * insertion does not renumber every branch of the method.
 */
@SuppressWarnings("deprecation")
public final class DexDiff {

    private static final int CONTEXT = 2;

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: DexDiff <original dex dir> <patched dex dir> [new-class prefix]");
            System.exit(2);
        }
        String prefix = args.length > 2 ? args[2] : "Lapp/andrewliang/";
        Map<String, ClassDef> original = load(new File(args[0]));
        Map<String, ClassDef> patched = load(new File(args[1]));

        int changed = 0;
        int extension = 0;
        List<String> added = new ArrayList<>();
        for (ClassDef patchedClass : new TreeMap<>(patched).values()) {
            String type = patchedClass.getType();
            ClassDef originalClass = original.get(type);
            if (originalClass == null) {
                if (type.startsWith(prefix)) extension++;
                else added.add(type);
                continue;
            }
            List<String> report = compare(originalClass, patchedClass);
            if (report.isEmpty()) continue;
            changed++;
            System.out.println("== " + type);
            for (String line : report) System.out.println(line);
            System.out.println();
        }
        int removed = 0;
        for (String type : original.keySet()) if (!patched.containsKey(type)) removed++;

        System.out.println("SUMMARY: " + changed + " changed classes, " + extension + " extension classes, "
                + added.size() + " other new classes, " + removed + " removed classes");
        // Other new classes are mostly libraries that the extension bundle carries, such as the
        // Kotlin standard library. Count them by package, so a stray new class still stands out.
        Map<String, Integer> packages = new TreeMap<>();
        for (String type : added) {
            String[] parts = type.substring(1).split("/");
            String key = parts.length > 2 ? parts[0] + "/" + parts[1] : parts.length == 2 ? parts[0] : type;
            packages.merge(key, 1, Integer::sum);
        }
        for (Map.Entry<String, Integer> entry : packages.entrySet()) {
            System.out.println("  new: " + entry.getKey() + " (" + entry.getValue() + ")");
        }
    }

    private static Map<String, ClassDef> load(File dir) throws Exception {
        Map<String, ClassDef> classes = new HashMap<>();
        File[] files = dir.listFiles((d, name) -> name.endsWith(".dex"));
        if (files == null || files.length == 0) throw new IllegalArgumentException("No .dex files in " + dir);
        Arrays.sort(files);
        for (File file : files) {
            DexBackedDexFile dex = DexBackedDexFile.fromInputStream(
                    Opcodes.getDefault(), new BufferedInputStream(new FileInputStream(file)));
            // The first dex that defines a class wins, as at run time.
            for (ClassDef classDef : dex.getClasses()) classes.putIfAbsent(classDef.getType(), classDef);
        }
        return classes;
    }

    private static List<String> compare(ClassDef a, ClassDef b) {
        List<String> report = new ArrayList<>();
        if (!String.valueOf(a.getSuperclass()).equals(String.valueOf(b.getSuperclass()))) {
            report.add("  superclass " + a.getSuperclass() + " -> " + b.getSuperclass());
        }
        if (!a.getInterfaces().equals(b.getInterfaces())) {
            report.add("  interfaces " + a.getInterfaces() + " -> " + b.getInterfaces());
        }
        if (a.getAccessFlags() != b.getAccessFlags()) {
            report.add("  class access flags 0x" + Integer.toHexString(a.getAccessFlags())
                    + " -> 0x" + Integer.toHexString(b.getAccessFlags()));
        }

        Map<String, Field> fieldsA = fields(a);
        Map<String, Field> fieldsB = fields(b);
        for (String key : fieldsB.keySet()) if (!fieldsA.containsKey(key)) report.add("  + field " + key);
        for (String key : fieldsA.keySet()) if (!fieldsB.containsKey(key)) report.add("  - field " + key);

        Map<String, Method> methodsA = methods(a);
        Map<String, Method> methodsB = methods(b);
        for (Map.Entry<String, Method> entry : methodsB.entrySet()) {
            Method before = methodsA.get(entry.getKey());
            List<String> after = lines(entry.getValue());
            if (before == null) {
                report.add("-- + method " + entry.getKey() + " (" + after.size() + " lines)");
                for (String line : after) report.add("  + " + line);
                continue;
            }
            List<String> diff = diff(lines(before), after);
            if (before.getAccessFlags() != entry.getValue().getAccessFlags()) {
                diff.add(0, "  access flags 0x" + Integer.toHexString(before.getAccessFlags())
                        + " -> 0x" + Integer.toHexString(entry.getValue().getAccessFlags()));
            }
            if (!diff.isEmpty()) {
                report.add("-- " + entry.getKey());
                report.addAll(diff);
            }
        }
        for (String key : methodsA.keySet()) if (!methodsB.containsKey(key)) report.add("-- - method " + key);
        return report;
    }

    private static Map<String, Field> fields(ClassDef classDef) {
        Map<String, Field> result = new LinkedHashMap<>();
        for (Field field : classDef.getFields()) result.put(field.getName() + ":" + field.getType(), field);
        return result;
    }

    private static Map<String, Method> methods(ClassDef classDef) {
        Map<String, Method> result = new LinkedHashMap<>();
        for (Method method : classDef.getMethods()) {
            StringBuilder key = new StringBuilder(method.getName()).append('(');
            for (CharSequence parameter : method.getParameterTypes()) key.append(parameter);
            result.put(key.append(')').append(method.getReturnType()).toString(), method);
        }
        return result;
    }

    /** The method as text: one line per instruction, labels before branch targets, then try blocks. */
    private static List<String> lines(Method method) {
        List<String> out = new ArrayList<>();
        MethodImplementation impl = method.getImplementation();
        if (impl == null) return out;
        out.add(".registers " + impl.getRegisterCount());

        List<Instruction> instructions = new ArrayList<>();
        for (Instruction instruction : impl.getInstructions()) instructions.add(instruction);
        int[] address = new int[instructions.size()];
        int at = 0;
        for (int i = 0; i < instructions.size(); i++) {
            address[i] = at;
            at += instructions.get(i).getCodeUnits();
        }

        TreeMap<Integer, String> labels = new TreeMap<>();
        for (int i = 0; i < instructions.size(); i++) {
            if (instructions.get(i) instanceof OffsetInstruction offset) {
                labels.put(address[i] + offset.getCodeOffset(), null);
            }
        }
        for (TryBlock<?> tryBlock : impl.getTryBlocks()) {
            labels.put(tryBlock.getStartCodeAddress(), null);
            labels.put(tryBlock.getStartCodeAddress() + tryBlock.getCodeUnitCount(), null);
            for (var handler : tryBlock.getExceptionHandlers()) labels.put(handler.getHandlerCodeAddress(), null);
        }
        int n = 0;
        for (Integer key : labels.keySet()) labels.put(key, ":L" + n++);

        for (int i = 0; i < instructions.size(); i++) {
            String label = labels.get(address[i]);
            if (label != null) out.add(label);
            out.add(text(instructions.get(i), address[i], labels));
        }
        for (TryBlock<?> tryBlock : impl.getTryBlocks()) {
            for (var handler : tryBlock.getExceptionHandlers()) {
                out.add(".catch " + (handler.getExceptionType() == null ? "all" : handler.getExceptionType())
                        + " {" + labels.get(tryBlock.getStartCodeAddress()) + " .. "
                        + labels.get(tryBlock.getStartCodeAddress() + tryBlock.getCodeUnitCount()) + "} "
                        + labels.get(handler.getHandlerCodeAddress()));
            }
        }
        return out;
    }

    private static String text(Instruction instruction, int address, Map<Integer, String> labels) {
        StringBuilder line = new StringBuilder("    ").append(instruction.getOpcode().name);
        List<String> operands = new ArrayList<>();
        if (instruction instanceof FiveRegisterInstruction five) {
            int[] registers = {five.getRegisterC(), five.getRegisterD(), five.getRegisterE(),
                    five.getRegisterF(), five.getRegisterG()};
            StringBuilder list = new StringBuilder("{");
            for (int i = 0; i < five.getRegisterCount(); i++) list.append(i == 0 ? "" : ", ").append('v').append(registers[i]);
            operands.add(list.append('}').toString());
        } else if (instruction instanceof RegisterRangeInstruction range) {
            operands.add("{v" + range.getStartRegister() + " .. v"
                    + (range.getStartRegister() + range.getRegisterCount() - 1) + "}");
        } else {
            if (instruction instanceof OneRegisterInstruction one) operands.add("v" + one.getRegisterA());
            if (instruction instanceof TwoRegisterInstruction two) operands.add("v" + two.getRegisterB());
            if (instruction instanceof ThreeRegisterInstruction three) operands.add("v" + three.getRegisterC());
        }
        if (instruction instanceof WideLiteralInstruction literal) operands.add("0x" + Long.toHexString(literal.getWideLiteral()));
        if (instruction instanceof ReferenceInstruction reference) {
            try {
                operands.add(ReferenceUtil.getReferenceString(reference.getReference()));
            } catch (Exception e) {
                operands.add(String.valueOf(reference.getReference()));
            }
        }
        if (instruction instanceof OffsetInstruction offset) {
            operands.add(labels.getOrDefault(address + offset.getCodeOffset(), "?" + offset.getCodeOffset()));
        }
        if (!operands.isEmpty()) line.append(' ').append(String.join(", ", operands));
        return line.toString();
    }

    /** A line diff with CONTEXT lines around each change, from a longest common subsequence. */
    private static List<String> diff(List<String> a, List<String> b) {
        int n = a.size();
        int m = b.size();
        List<String> out = new ArrayList<>();
        if (a.equals(b)) return out;
        // Trim the shared head and tail, so the table covers only the changed middle.
        int head = 0;
        while (head < n && head < m && a.get(head).equals(b.get(head))) head++;
        int tail = 0;
        while (tail < n - head && tail < m - head && a.get(n - 1 - tail).equals(b.get(m - 1 - tail))) tail++;
        List<String> midA = a.subList(head, n - tail);
        List<String> midB = b.subList(head, m - tail);
        int[][] lcs = new int[midA.size() + 1][midB.size() + 1];
        for (int i = midA.size() - 1; i >= 0; i--) {
            for (int j = midB.size() - 1; j >= 0; j--) {
                lcs[i][j] = midA.get(i).equals(midB.get(j)) ? lcs[i + 1][j + 1] + 1 : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
            }
        }
        List<String> ops = new ArrayList<>();
        for (int k = 0; k < head; k++) ops.add("  " + a.get(k));
        int i = 0;
        int j = 0;
        while (i < midA.size() || j < midB.size()) {
            if (i < midA.size() && j < midB.size() && midA.get(i).equals(midB.get(j))) {
                ops.add("  " + midA.get(i++));
                j++;
            } else if (j < midB.size() && (i == midA.size() || lcs[i][j + 1] >= lcs[i + 1][j])) {
                ops.add("+ " + midB.get(j++));
            } else {
                ops.add("- " + midA.get(i++));
            }
        }
        for (int k = n - tail; k < n; k++) ops.add("  " + a.get(k));

        boolean[] keep = new boolean[ops.size()];
        for (int k = 0; k < ops.size(); k++) {
            if (ops.get(k).charAt(0) != ' ') {
                for (int c = Math.max(0, k - CONTEXT); c <= Math.min(ops.size() - 1, k + CONTEXT); c++) keep[c] = true;
            }
        }
        boolean gap = false;
        for (int k = 0; k < ops.size(); k++) {
            if (keep[k]) {
                if (gap) out.add("  ...");
                out.add("  " + ops.get(k));
                gap = false;
            } else if (!out.isEmpty()) {
                gap = true;
            }
        }
        return out;
    }
}
