package app.andrewliang.patches.line.callrecording

import app.andrewliang.patches.shared.Constants.COMPATIBILITY_LINE
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

/**
 * Exposes LINE's call recording. Both `[Call]` patches depend on this, so the record button and
 * the recording flow are available whether the user has LINE Labs and a paid plan or not.
 *
 * Call recording is gated behind LINE Labs (`premiumcall.a.d()`) and a paid plan (`.e()`). This
 * forces both gates true. The server still decides the "REC" capability per call, so a region that
 * withholds it is not unlocked here.
 */
internal val callRecordingEnablePatch = bytecodePatch {
    compatibleWith(COMPATIBILITY_LINE)

    execute {
        CallRecordingEnabledFingerprint.method.addInstructions(0, "const/4 v0, 0x1\nreturn v0")
        CallRecordingPremiumFingerprint.method.addInstructions(0, "const/4 v0, 0x1\nreturn v0")
    }
}
