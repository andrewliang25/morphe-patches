package app.andrewliang.patches.line.hidegift

import app.andrewliang.patches.line.shared.lineSettingsExtensionPatch
import app.andrewliang.patches.line.shared.markLineSettingIncluded
import app.andrewliang.patches.line.shared.returnFalseIfLineSetting
import app.andrewliang.patches.shared.Constants.COMPATIBILITY_LINE
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val hideGiftButtonPatch = bytecodePatch(
    name = "[Chat] Hide LINE GIFT button",
    description = "Removes the LINE GIFT tile from the + attach menu in a chat room.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_LINE)

    dependsOn(lineSettingsExtensionPatch)

    // The LINE GIFT tile (yi1.h) is shown only if its availability predicate `j(xk1.b)Z` returns
    // true (the attach-menu filter yi1.p.f gates on it). Neuter that predicate to false. The tile
    // is then dropped by the existing filter in xi1.c. Anchor via the constructor (the sole reader
    // of the wi1.b$b.GIFT enum constant), then select `j` by its unique descriptor. The switch
    // uses p0: `j` writes it before it reads it.
    execute {
        val giftClass = mutableClassDefBy(GiftAttachButtonFingerprint.method.definingClass)
        val availabilityMethod = giftClass.methods.first { method ->
            method.returnType == "Z" &&
                method.parameterTypes.map { it.toString() } == listOf("Lxk1/b;")
        }
        availabilityMethod.returnFalseIfLineSetting("hideGift", "p0")
        markLineSettingIncluded("hideGift")
    }
}
