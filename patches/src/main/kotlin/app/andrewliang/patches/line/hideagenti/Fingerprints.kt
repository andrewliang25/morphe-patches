package app.andrewliang.patches.line.hideagenti

import app.andrewliang.patches.line.shared.ServerFlagFingerprint
import app.andrewliang.patches.shared.resourceId
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.literal
import app.morphe.patcher.opcode
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

/**
 * The Compose lambda of the Home tab header (`wm2.j` in 26.14.0). It draws the header icons:
 * Agent i, the services grid, the bell and Settings. It draws Agent i only if its first boolean
 * field is true. It reads that field with the first `iget-boolean`, then labels the icon with
 * `access_agenti`. Nothing else in the APK uses that label.
 */
internal object HomeHeaderAgentIFingerprint : Fingerprint(
    filters = listOf(
        opcode(Opcode.IGET_BOOLEAN),
        literal({ resourceId("string", "access_agenti") }),
    ),
)

/**
 * The server flag for the Agent i entry at the end of the search bar (`g45.i2.b()` in 26.14.0).
 * The search bar shows `main_tab_ai_entry_icon_container` only if this flag is true.
 */
internal object SearchBarAiEntryFlagFingerprint :
    ServerFlagFingerprint("function.search.line_ai_entry.enabled")

/**
 * The local key that keeps Agent i hidden in chats. It holds the time until which Agent i stays
 * hidden: "Hide temporarily" writes today + 30 days, "Show" writes 0. R8 renames its enum class
 * but keeps the constant name.
 */
internal const val AGENT_I_IN_CHAT_HIDDEN_UNTIL_KEY = "CHATROOM_AI_BUTTON_SHOWN_AFTER_MILLIS"

/**
 * True for a getter of the hidden-until time: a `()J` method that only reads
 * [AGENT_I_IN_CHAT_HIDDEN_UNTIL_KEY] from the key-value store and returns it. There are two
 * (`s81.c.v()` for the chat, `r21.b.a()` for the Settings screen in 26.16.0).
 */
internal fun isAgentIInChatHiddenUntilGetter(method: Method): Boolean {
    if (method.returnType != "J" || method.parameterTypes.isNotEmpty()) return false
    val instructions = method.implementation?.instructions?.toList() ?: return false
    if (instructions.size != 4) return false

    val keyRead = instructions[0]
    val field = (keyRead as? ReferenceInstruction)?.reference as? FieldReference ?: return false
    if (keyRead.opcode != Opcode.SGET_OBJECT || field.name != AGENT_I_IN_CHAT_HIDDEN_UNTIL_KEY) {
        return false
    }

    val storeRead = (instructions[1] as? ReferenceInstruction)?.reference as? MethodReference
        ?: return false
    return instructions[1].opcode == Opcode.INVOKE_STATIC &&
        storeRead.returnType == "J" &&
        storeRead.parameterTypes == listOf(field.type) &&
        instructions[2].opcode == Opcode.MOVE_RESULT_WIDE &&
        instructions[3].opcode == Opcode.RETURN_WIDE
}
