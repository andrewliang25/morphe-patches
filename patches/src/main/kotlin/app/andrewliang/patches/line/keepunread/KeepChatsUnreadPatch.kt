package app.andrewliang.patches.line.keepunread

import app.andrewliang.patches.shared.Constants.COMPATIBILITY_LINE
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val keepChatsUnreadPatch = bytecodePatch(
    name = "[Chat] Keep chats unread",
    description = "When you open a chat, LINE does not mark it read or send a read receipt. " +
        "\"Mark as read\" still works.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_LINE)

    // ReadWorkerFingerprint resolves na3.e.d — the only caller of TalkServiceClient.c1 — which
    // identifies the read-manager class. Both open-on-view and manual mark-as-read funnel
    // through that worker (local read + server receipt), but only the OPEN path reaches it via
    // the sibling wrapper b(String, String) (its sole caller is the auto-read-on-view
    // subscriber). No-op that wrapper so opening a chat marks nothing read — locally or on the
    // server — while the manual wrappers (e/c) are left intact. b is the unique (String,String)
    // -> void method in the class.
    execute {
        val readManager = mutableClassDefBy(ReadWorkerFingerprint.method.definingClass)
        val openReadWrapper = readManager.methods.first { method ->
            method.returnType == "V" &&
                method.parameterTypes.map { it.toString() } ==
                listOf("Ljava/lang/String;", "Ljava/lang/String;")
        }
        openReadWrapper.addInstructions(0, "return-void")
    }
}
