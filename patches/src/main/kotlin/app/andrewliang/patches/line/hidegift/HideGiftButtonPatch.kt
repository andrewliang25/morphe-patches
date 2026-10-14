package app.andrewliang.patches.line.hidegift

import app.andrewliang.patches.line.shared.hideAttachTile
import app.andrewliang.patches.line.shared.lineSettingsExtensionPatch
import app.andrewliang.patches.line.shared.markLineSettingIncluded
import app.andrewliang.patches.shared.Constants.COMPATIBILITY_LINE
import app.andrewliang.patches.shared.resourceIdsPatch
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val hideGiftButtonPatch = bytecodePatch(
    name = "[Chat] Hide LINE GIFT button",
    description = "Removes the LINE GIFT tile from the + attach menu in a chat room.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_LINE)
    dependsOn(resourceIdsPatch)

    dependsOn(lineSettingsExtensionPatch)

    execute {
        hideAttachTile(GiftAttachButtonFingerprint.method.definingClass, "hideGift")
        markLineSettingIncluded("hideGift")
    }
}
