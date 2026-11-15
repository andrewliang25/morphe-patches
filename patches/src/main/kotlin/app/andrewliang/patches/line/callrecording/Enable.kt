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
 * withholds it is not unlocked here. The feature is 26.16.0+, so the sites are guarded and this
 * no-ops on older versions.
 */
internal val callRecordingEnablePatch = bytecodePatch {
    compatibleWith(COMPATIBILITY_LINE)

    execute {
        // 26.16.0+ only: the premiumcall package is absent from older versions.
        if (CallRecordingGateFingerprint.methodOrNull == null) return@execute

        CallRecordingEnabledFingerprint.methodOrNull?.addInstructions(0, "const/4 v0, 0x1\nreturn v0")
        CallRecordingPremiumFingerprint.methodOrNull?.addInstructions(0, "const/4 v0, 0x1\nreturn v0")
    }
}
