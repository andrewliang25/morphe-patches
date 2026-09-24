package app.andrewliang.patches.facebook.shared

import app.morphe.patcher.patch.BytecodePatchContext
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

/**
 * The real names of the fields of [owner], as `reported name -> field name`.
 *
 * Redex renames the fields on every release. Some classes keep a debug dump method that writes
 * each field with its real name, and this reads the pairs from it. [marker] is one of the reported
 * names. It finds the dump among the other methods of the class.
 */
internal fun BytecodePatchContext.reportedFieldNames(owner: String, marker: String): Map<String, String> {
    val dumps = mutableClassDefBy(owner).methods.filter { method ->
        method.instructions().any { it.stringReference() == marker }
    }

    check(dumps.size == 1) { "Expected 1 method of $owner that reports \"$marker\", found ${dumps.size}" }

    return pairs(dumps.single())
}

/**
 * The pairs that one dump writes.
 *
 * The dump reads a field, loads its name, and then calls the reporter. The name is not always the
 * nearest string to the field: the tag of the class can come between them. So the pair is made at
 * the call. The name is the last string loaded before the call.
 *
 * One class loads the name before it reads the field. So when the forward scan finds no name, a
 * backward scan is tried.
 */
private fun pairs(dump: Method): Map<String, String> {
    val owner = dump.definingClass
    val instructions = dump.instructions()
    val names = mutableMapOf<String, String>()

    fun scan(from: Int, step: Int): String? {
        var name: String? = null

        var index = from
        while (index in instructions.indices) {
            val instruction = instructions[index]

            // The call ends the scan. Every string before it is an argument of this call.
            if (instruction.opcode.name.startsWith("invoke")) return name

            instruction.stringReference()?.let { name = it }
            index += step
        }

        return name
    }

    instructions.forEachIndexed { index, instruction ->
        val field = instruction.fieldReference() ?: return@forEachIndexed
        if (field.definingClass != owner) return@forEachIndexed
        if (!instruction.opcode.name.startsWith("iget")) return@forEachIndexed

        val name = scan(index + 1, 1) ?: scan(index - 1, -1)
        if (name != null) names.putIfAbsent(name, field.name)
    }

    return names
}

private fun Method.instructions(): List<Instruction> =
    implementation?.instructions?.toList() ?: emptyList()

private fun Instruction.fieldReference() =
    (this as? ReferenceInstruction)?.reference as? FieldReference

private fun Instruction.stringReference() =
    ((this as? ReferenceInstruction)?.reference as? StringReference)?.string
