package app.andrewliang.patches.line.callrecording

import app.andrewliang.patches.line.shared.lineSettingsExtensionPatch
import app.andrewliang.patches.line.shared.markLineSettingIncluded
import app.andrewliang.patches.line.shared.skipIfLineSetting
import app.andrewliang.patches.shared.Constants.COMPATIBILITY_LINE
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

/**
 * Records a call without telling the other party: no "is recording" banner and no spoken
 * "recording started/ended" announcement. Use the "Record without notifying" switch to turn it off.
 *
 * When recording starts, the notify emitter (`nv7.n0.emit()` in 26.17.1) sends a "record"
 * FeatureShare SET (`tw7.f.H(tw7.a)`) over the call session. That one send drives both the peer
 * banner and the local announcement on both devices (a `MediaPlayer` that plays off the shared
 * "record" state). The patch skips the send while the switch is on. The `R("record")` stop branch
 * stays, and LINE's own recorder keeps
 * working, because the capture is a separate consumer of the recording state. v2 is the Continuation
 * parameter, dead at this point in `emit()`.
 */
@Suppress("unused")
val silentCallRecordingPatch = bytecodePatch(
    name = "[Call] Record calls without notifying",
    description = "Records a 1-to-1 LINE call without telling the other party: no on-screen banner " +
        "and no spoken recording announcement. Recording a call without the other person's consent " +
        "is illegal in some places. You are responsible for following the law where you are.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_LINE)

    dependsOn(callRecordingEnablePatch, lineSettingsExtensionPatch)

    execute {
        val emit = CallRecordingNotifyFingerprint.method

        val instructions = emit.implementation!!.instructions.toList()
        // The send is the one-argument interface call that does not take the "record" string.
        val sendIndex = instructions.indexOfFirst {
            it.opcode == Opcode.INVOKE_INTERFACE &&
                ((it as? ReferenceInstruction)?.reference as? MethodReference).let { ref ->
                    ref?.returnType == "V" &&
                        ref.parameterTypes.size == 1 &&
                        ref.parameterTypes[0] != "Ljava/lang/String;"
                }
        }
        check(sendIndex >= 0) { "could not find the record send in the notify emitter" }
        emit.skipIfLineSetting(sendIndex, 1, "silentCallRecording", "v2")

        markLineSettingIncluded("silentCallRecording")
    }
}
