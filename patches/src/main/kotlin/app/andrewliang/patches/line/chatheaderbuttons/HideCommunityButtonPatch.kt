package app.andrewliang.patches.line.chatheaderbuttons

import app.andrewliang.patches.shared.Constants.COMPATIBILITY_LINE
import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val hideCommunityButtonPatch = bytecodePatch(
    name = "Hide community button",
    description = "Removes the community (OpenChat) button from the top of the Chats tab header.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_LINE)

    // Remove the `sget-object OPEN_CHAT` + following `add(...)` pair so nothing adds the button
    // to the header list. instructionMatches[0] = the OPEN_CHAT sget-object. The add is the
    // next instruction.
    execute {
        val openChatSgetIndex = CommunityButtonFingerprint.instructionMatches.first().index
        CommunityButtonFingerprint.method.removeInstructions(openChatSgetIndex, 2)
    }
}
