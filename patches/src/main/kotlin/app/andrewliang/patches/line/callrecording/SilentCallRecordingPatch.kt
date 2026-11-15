package app.andrewliang.patches.line.callrecording

import app.andrewliang.patches.line.shared.lineSettingsExtensionPatch
import app.andrewliang.patches.line.shared.markLineSettingIncluded
import app.andrewliang.patches.line.shared.skipIfLineSetting
import app.andrewliang.patches.shared.Constants.COMPATIBILITY_LINE_CALL_RECORDING
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

/**
 * Records a call without telling the other party: no "is recording" banner and no spoken
 * "recording started/ended" announcement. Use the "Record without notifying" switch to turn it off.
 *
 * When recording starts, `xt7/n0.emit()` sends a "record" FeatureShare SET (`dv7.f.L(dv7.a)`) over
 * the call session. That one send drives both the peer banner and the local announcement on both
 * devices (a `MediaPlayer` in `du7/c`, played off the shared "record" state). The patch skips the
 * send while the switch is on. The `O("record")` stop branch stays, and LINE's own recorder keeps
 * working, because the capture is a separate consumer of the recording state. v2 is the Continuation
 * parameter, dead at this point in `emit()`.
 *
 * Call recording is a 26.16.0+ feature, so the patch lists only those versions. The site stays
 * guarded, because a forced build (`-f`) can still apply it to an older version.
 */
@Suppress("unused")
val silentCallRecordingPatch = bytecodePatch(
    name = "[Call] Record calls without notifying",
    description = "Records a 1-to-1 LINE call without telling the other party: no on-screen banner " +
        "and no spoken recording announcement. Recording a call without the other person's consent " +
        "is illegal in some places. You are responsible for following the law where you are.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_LINE_CALL_RECORDING)

    dependsOn(callRecordingEnablePatch, lineSettingsExtensionPatch)

    execute {
        // 26.16.0+ feature. Without it the notify emitter is absent: no-op on older versions.
        val emit = CallRecordingNotifyFingerprint.methodOrNull ?: return@execute

        val instructions = emit.implementation!!.instructions.toList()
        val lIndex = instructions.indexOfFirst {
            it.opcode == Opcode.INVOKE_INTERFACE &&
                ((it as? ReferenceInstruction)?.reference as? MethodReference).let { ref ->
                    ref?.name == "L" && ref.returnType == "V"
                }
        }
        check(lIndex >= 0) { "could not find dv7.f.L() send in xt7.n0.emit()" }
        emit.skipIfLineSetting(lIndex, 1, "silentCallRecording", "v2")

        markLineSettingIncluded("silentCallRecording")
    }
}
