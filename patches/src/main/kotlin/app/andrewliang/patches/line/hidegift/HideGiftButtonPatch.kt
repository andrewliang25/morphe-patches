package app.andrewliang.patches.line.hidegift

import app.andrewliang.patches.shared.Constants.COMPATIBILITY_LINE
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val hideGiftButtonPatch = bytecodePatch(
    name = "Hide LINE GIFT button",
    description = "Removes the LINE GIFT tile from a chat room's + attach menu.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_LINE)

    // The LINE GIFT tile (hg1.h) is shown only if its availability predicate `j(gi1.b)Z` returns
    // true (the attach-menu filter hg1.r.f gates on it). Neuter that predicate to false; the tile
    // is then dropped by the existing filter in gg1.e. Anchor via the constructor (the sole reader
    // of the fg1.a$b.GIFT enum constant), then select `j` by its unique descriptor.
    execute {
        val giftClass = mutableClassDefBy(GiftAttachButtonFingerprint.method.definingClass)
        val availabilityMethod = giftClass.methods.first { method ->
            method.returnType == "Z" &&
                method.parameterTypes.map { it.toString() } == listOf("Lgi1/b;")
        }
        availabilityMethod.addInstructions(
            0,
            """
                const/4 p0, 0x0
                return p0
            """,
        )
    }
}
