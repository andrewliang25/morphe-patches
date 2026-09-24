package app.andrewliang.patches.facebook.shared

import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

/**
 * Every `reported name -> field` pair that [dump], a debug dump method, writes about
 * fields of its own class.
 *
 * The dump reads a field, then loads the name, then calls the reporter. Nearness alone does not
 * pair them. The tag of the whole class is loaded between the first field and its name, and sits
 * nearer to it than the name does. So the pairing is made against the **call**, which is what
 * receives the name: the last string loaded before the reporter runs.
 *
 * One class writes the name before the field instead of after, so a backward window is tried when
 * the forward one finds nothing.
 */
internal fun reportedFieldNames(dump: Method): Map<String, String> {
    val owner = dump.definingClass
    val instructions = dump.implementation?.instructions?.toList() ?: emptyList()
    val names = mutableMapOf<String, String>()

    fun scan(from: Int, step: Int): String? {
        var name: String? = null

        var index = from
        while (index in instructions.indices) {
            val instruction = instructions[index]

            // The reporter call ends the window: everything loaded before it is its arguments.
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

private fun Instruction.fieldReference() =
    (this as? ReferenceInstruction)?.reference as? FieldReference

private fun Instruction.stringReference() =
    ((this as? ReferenceInstruction)?.reference as? StringReference)?.string
