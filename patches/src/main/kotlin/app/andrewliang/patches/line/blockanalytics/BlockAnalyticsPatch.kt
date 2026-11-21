package app.andrewliang.patches.line.blockanalytics

import app.andrewliang.patches.line.shared.lineSettingsExtensionPatch
import app.andrewliang.patches.line.shared.markLineSettingIncluded
import app.andrewliang.patches.line.shared.readLineSetting
import app.andrewliang.patches.shared.Constants.COMPATIBILITY_LINE
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference

/**
 * The `run` method of LINE's startup task `"TrackingManagerTask"` (`xp8.d.f()` in 26.17.1). It
 * builds the client of LINE's analytics service (UTS, `uts-front.line-apps.com`) and puts the real
 * logger into the shared logger reference with `AtomicReference.compareAndSet`. Until then the
 * reference holds a logger that drops every event.
 */
internal object TrackingManagerTaskFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf(),
    filters = listOf(
        methodCall(definingClass = "Ljava/util/concurrent/atomic/AtomicReference;", name = "compareAndSet"),
    ),
    custom = { _, classDef ->
        classDef.methods.any { method ->
            method.implementation?.instructions?.any { instruction ->
                ((instruction as? ReferenceInstruction)?.reference as? StringReference)?.string == "TrackingManagerTask"
            } == true
        }
    },
)

@Suppress("unused")
val blockAnalyticsPatch = bytecodePatch(
    name = "[General] Block analytics",
    description = "Stops LINE from sending usage events to its analytics service, such as the screens you " +
        "open and the buttons you tap. Crash reports are not changed.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_LINE)

    dependsOn(lineSettingsExtensionPatch)

    // About 1,000 classes log through the one shared logger. While the switch is on, the task
    // returns before it installs the real logger, so the logger that drops every event stays.
    execute {
        // v0 is dead at entry: the first instruction of the task writes it.
        val task = TrackingManagerTaskFingerprint.method
        task.addInstructionsWithLabels(
            0,
            readLineSetting("blockAnalytics", "v0") + """
                if-eqz v0, :stock
                return-void
            """,
            ExternalLabel("stock", task.getInstruction(0)),
        )
        markLineSettingIncluded("blockAnalytics")
    }
}
