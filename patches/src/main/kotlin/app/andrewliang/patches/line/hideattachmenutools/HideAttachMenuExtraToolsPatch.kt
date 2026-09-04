package app.andrewliang.patches.line.hideattachmenutools

import app.andrewliang.patches.shared.Constants.COMPATIBILITY_LINE
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val hideAttachMenuExtraToolsPatch = bytecodePatch(
    name = "Hide attach menu extra tools",
    description = "Removes all the server-provided extra tools from the + attach menu in a chat " +
        "room (Poll, Reservation, Schedule, Ladder shuffle, and more). The built-in tiles " +
        "(camera, gallery, files, and contact) do not change.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_LINE)

    // Every server-driven attach service is rendered by the shared class yi1.d, shown only when its
    // per-item gate f(...) returns true. yi1.d is the sole renderer of these services, so forcing
    // its f() to unconditionally return false drops the entire set — with no dependency on any
    // server channel id (the reason a single-service patch cannot be stable). p0 is `this`;
    // clobbering it is fine because we return immediately.
    execute {
        AttachMenuServiceGateFingerprint.method.addInstructions(
            0,
            """
                const/4 p0, 0x0
                return p0
            """,
        )
    }
}
