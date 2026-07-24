package app.andrewliang.patches.line.readreceipts

import app.andrewliang.patches.shared.Constants.COMPATIBILITY_LINE
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val preventReadReceiptsPatch = bytecodePatch(
    name = "Prevent read receipts",
    description = "Stops LINE from telling senders when you have read their messages " +
        "(neutralizes the sendChatChecked request).",
    default = false, // opt-in; flip to true to enable by default in Morphe Manager.
) {
    compatibleWith(COMPATIBILITY_LINE)

    // The target method's return value is discarded by its caller
    // (LegacyTalkServiceClientImpl.j1 is fire-and-forget), so returning null immediately
    // makes the read-receipt op a no-op: no network send, no paired recv, no exception.
    // The method declares `.locals 5`, so v0 is free to use.
    execute {
        SendChatCheckedFingerprint.method.addInstructions(
            0,
            """
                const/4 v0, 0x0
                return-object v0
            """,
        )
    }
}
