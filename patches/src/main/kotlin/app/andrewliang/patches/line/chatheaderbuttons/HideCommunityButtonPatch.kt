package app.andrewliang.patches.line.chatheaderbuttons

import app.andrewliang.patches.line.shared.lineSettingsExtensionPatch
import app.andrewliang.patches.line.shared.markLineSettingIncluded
import app.andrewliang.patches.line.shared.skipIfLineSetting
import app.andrewliang.patches.shared.Constants.COMPATIBILITY_LINE
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

@Suppress("unused")
val hideCommunityButtonPatch = bytecodePatch(
    name = "[Chat] Hide community button",
    description = "Removes the community (OpenChat) button from the top of the Chats tab header.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_LINE)

    dependsOn(lineSettingsExtensionPatch)

    // Skip the `sget-object OPEN_CHAT` + following `add(...)` pair so nothing adds the button
    // to the header list. instructionMatches[0] = the OPEN_CHAT sget-object. The add is the
    // next instruction. The switch uses the register of the sget-object: the sget-object writes
    // it, and the code after the add writes it before it reads it.
    execute {
        val openChatSget = CommunityButtonFingerprint.instructionMatches.first()
        val register = (openChatSget.instruction as OneRegisterInstruction).registerA
        CommunityButtonFingerprint.method.skipIfLineSetting(
            openChatSget.index,
            2,
            "hideCommunityButton",
            "v$register",
        )
        markLineSettingIncluded("hideCommunityButton")
    }
}
