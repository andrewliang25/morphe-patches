package app.andrewliang.patches.line.callrecording

import app.andrewliang.patches.shared.Constants.COMPATIBILITY_LINE
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

/**
 * Exposes LINE's call recording. Both `[Call]` patches depend on this, so the record button and
 * the recording flow are available without a paid plan.
 *
 * This forces the two gates `premiumcall.a.d()` (LINE Labs) and `.e()` (paid plan) true. The LINE
 * Labs call recording switch must still be on: other code reads the Labs flag directly, and with
 * the switch off LINE makes no recording (`docs/line/calls.md`, "Recording with the LINE Labs
 * switch off"). The server still decides the "REC" capability per call.
 */
internal val callRecordingEnablePatch = bytecodePatch {
    compatibleWith(COMPATIBILITY_LINE)

    execute {
        CallRecordingEnabledFingerprint.method.addInstructions(0, "const/4 v0, 0x1\nreturn v0")
        CallRecordingPremiumFingerprint.method.addInstructions(0, "const/4 v0, 0x1\nreturn v0")
    }
}
