package app.andrewliang.patches.line.chatheaderbuttons

import app.andrewliang.patches.line.shared.forceServerFlagFalse
import app.andrewliang.patches.shared.Constants.COMPATIBILITY_LINE
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val hideAiFriendsButtonPatch = bytecodePatch(
    name = "[Chat] Hide AI Friends button",
    description = "Removes the AI Friends button from the Chats tab header. The Album button " +
        "shows in its place.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_LINE)

    // Turn off the server flag, not only the button. The Chats tab also reads the flag for the
    // Album button and its green dot, and all readers must agree that the feature is off.
    execute {
        forceServerFlagFalse(AiFriendsFlagFingerprint)
    }
}
