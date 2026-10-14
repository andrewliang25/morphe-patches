package app.andrewliang.patches.line.chatheaderbuttons

import app.andrewliang.patches.line.shared.hideIfLineSetting
import app.andrewliang.patches.line.shared.lineSettingsExtensionPatch
import app.andrewliang.patches.line.shared.markLineSettingIncluded
import app.andrewliang.patches.line.shared.serverFlagAccessor
import app.andrewliang.patches.shared.Constants.COMPATIBILITY_LINE
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OffsetInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

@Suppress("unused")
val hideAiFriendsButtonPatch = bytecodePatch(
    name = "[Chat] Hide AI Friends button",
    description = "Removes the AI Friends button from the Chats tab header. The Album button " +
        "shows in its place.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_LINE)

    dependsOn(lineSettingsExtensionPatch)

    // Turn off the server flag, not only the button. The Chats tab also reads the flag for the
    // Album button and its green dot, and all readers must agree that the feature is off.
    // The accessor is a field getter with one return and no branch, so the switch clears the
    // value right before that return.
    execute {
        val accessor = serverFlagAccessor(AiFriendsFlagFingerprint)
        val instructions = accessor.implementation!!.instructions
        if (instructions.any { it is OffsetInstruction }) throw PatchException("AI Friends flag accessor branches")
        val returns = instructions.withIndex().filter { it.value.opcode == Opcode.RETURN }
        val ret = returns.singleOrNull() ?: throw PatchException("AI Friends flag accessor has ${returns.size} returns")
        val register = (ret.value as OneRegisterInstruction).registerA
        if (register > 15) throw PatchException("AI Friends flag accessor returns v$register")
        accessor.addInstructions(ret.index, hideIfLineSetting("hideAiFriends", "v$register"))
        markLineSettingIncluded("hideAiFriends")
    }
}
