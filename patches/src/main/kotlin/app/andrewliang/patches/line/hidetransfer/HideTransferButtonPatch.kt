package app.andrewliang.patches.line.hidetransfer

import app.andrewliang.patches.shared.Constants.COMPATIBILITY_LINE
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val hideTransferButtonPatch = bytecodePatch(
    name = "Hide Transfer button",
    description = "Removes the Transfer (LINE Pay) tile from the + attach menu in a chat room.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_LINE)

    // The Transfer tile (yi1.j) is shown only if its availability predicate `j(xk1.b)Z` returns
    // true (the attach-menu filter yi1.p.f gates on it). Neuter that predicate to false. The tile
    // is then dropped by the existing filter in xi1.c. Anchor via the constructor (the sole reader
    // of the wi1.b$b.PAY enum constant), then select `j` by its unique descriptor.
    execute {
        val transferClass =
            mutableClassDefBy(TransferAttachButtonFingerprint.method.definingClass)
        val availabilityMethod = transferClass.methods.first { method ->
            method.returnType == "Z" &&
                method.parameterTypes.map { it.toString() } == listOf("Lxk1/b;")
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
