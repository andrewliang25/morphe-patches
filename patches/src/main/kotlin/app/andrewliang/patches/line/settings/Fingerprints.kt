package app.andrewliang.patches.line.settings

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

/**
 * The static initializer of LINE's main Settings list, `ka5/o2.<clinit>` in 26.14.0. It builds every
 * row of the list once and keeps them in a static `List`.
 *
 * Its class and its row types are obfuscated, but the "About LINE" row reads two enum constants
 * whose names R8 keeps: its row key `AboutLine` and its analytics event
 * `MORETAB_SETTINGS_ABOUTLINE`. No other method reads both. The `sget-object` opcode keeps out the
 * initializers of the two enums, which write these constants.
 */
internal object SettingsRowsFingerprint : Fingerprint(
    name = "<clinit>",
    filters = listOf(
        fieldAccess(name = "AboutLine", opcode = Opcode.SGET_OBJECT),
        fieldAccess(name = "MORETAB_SETTINGS_ABOUTLINE", opcode = Opcode.SGET_OBJECT),
    ),
)

/**
 * The getter of the static Settings list in [rowsClass]: it reads the list field with
 * `sget-object` and returns it. Two patches add a call before its return, so the match takes the
 * getter in its original shape or with calls added before the return.
 */
internal fun BytecodePatchContext.settingsListGetter(rowsClass: String): MutableMethod =
    mutableClassDefBy(rowsClass).methods.single { method ->
        val instructions = method.implementation?.instructions?.toList() ?: return@single false
        val read = instructions.firstOrNull()
        method.returnType == "Ljava/util/List;" && method.parameterTypes.isEmpty() &&
            read?.opcode == Opcode.SGET_OBJECT &&
            ((read as ReferenceInstruction).reference as FieldReference).definingClass == rowsClass &&
            instructions.last().opcode == Opcode.RETURN_OBJECT &&
            instructions.subList(1, instructions.size - 1).all {
                it.opcode == Opcode.INVOKE_STATIC || it.opcode == Opcode.MOVE_RESULT_OBJECT
            }
    }
