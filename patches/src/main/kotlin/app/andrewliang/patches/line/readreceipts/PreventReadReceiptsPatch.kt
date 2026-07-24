package app.andrewliang.patches.line.readreceipts

import app.andrewliang.patches.shared.Constants.COMPATIBILITY_LINE
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val preventReadReceiptsPatch = bytecodePatch(
    name = "Prevent read receipts",
    description = "Stops LINE from telling senders when you have read their messages, " +
        "across 1:1, group, and OpenChat rooms.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_LINE)

    extendWith("extensions/extension.mpe")

    // At the top of the generic Thrift send `o.b(methodName, args)`, rewrite the method name
    // to "noop" for the read-receipt ops (sendChatChecked / markAsRead / markChatsAsRead /
    // markThreadsAsRead). The send still happens (seq/transport stay balanced, so no stream
    // desync — unlike an early return); the server rejects the unknown op and the caller
    // swallows the resulting exception. p1 = methodName; .locals 3 leaves room to reuse p1.
    execute {
        ThriftSendFingerprint.method.addInstructions(
            0,
            """
                invoke-static {p1}, Lapp/andrewliang/extension/ReadReceipts;->rewrite(Ljava/lang/String;)Ljava/lang/String;
                move-result-object p1
            """,
        )
    }
}
