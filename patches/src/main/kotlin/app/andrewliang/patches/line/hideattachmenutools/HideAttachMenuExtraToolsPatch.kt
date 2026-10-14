package app.andrewliang.patches.line.hideattachmenutools

import app.andrewliang.patches.line.shared.lineSettingsExtensionPatch
import app.andrewliang.patches.line.shared.markLineSettingIncluded
import app.andrewliang.patches.line.shared.returnFalseIfLineSetting
import app.andrewliang.patches.shared.Constants.COMPATIBILITY_LINE
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val hideAttachMenuExtraToolsPatch = bytecodePatch(
    name = "[Chat] Hide attach menu extra tools",
    description = "Removes the server-provided tools (Poll, Schedule and more) from the + menu " +
        "in a chat. Built-in tiles stay.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_LINE)

    dependsOn(lineSettingsExtensionPatch)

    // Every server-driven attach service is rendered by the shared class yi1.d, shown only when its
    // per-item gate f(...) returns true. yi1.d is the sole renderer of these services, so forcing
    // its f() to unconditionally return false drops the entire set — with no dependency on any
    // server channel id (the reason a single-service patch cannot be stable). The switch uses p2:
    // f() writes it before it reads it.
    execute {
        AttachMenuServiceGateFingerprint.method.returnFalseIfLineSetting("hideAttachTools", "p2")
        markLineSettingIncluded("hideAttachTools")
    }
}
