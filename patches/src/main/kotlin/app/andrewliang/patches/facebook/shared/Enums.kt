package app.andrewliang.patches.facebook.shared

import app.morphe.patcher.patch.BytecodePatchContext
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

/**
 * Resolve an enum constant to its obfuscated field through the enum's `<clinit>`.
 *
 * Bind by position, not by register: the constant name goes into one register and the enum
 * instance into another. Thus the first `sput-object` of the enum's own type after the literal is
 * that constant's field.
 */
internal fun BytecodePatchContext.enumConstantField(enumType: String, constant: String): String {
    val clinit = mutableClassDefBy(enumType).methods.single { it.name == "<clinit>" }
    val instructions = clinit.implementation!!.instructions.toList()

    instructions.forEachIndexed { index, instruction ->
        val string = (instruction as? ReferenceInstruction)?.reference as? StringReference
        if (string?.string != constant) return@forEachIndexed

        for (next in index + 1 until instructions.size) {
            val candidate = instructions[next]
            if (candidate.opcode != Opcode.SPUT_OBJECT) continue
            val field = (candidate as ReferenceInstruction).reference as FieldReference
            if (field.type == enumType) return field.name
        }
    }

    error("$enumType.$constant not found in <clinit>")
}
