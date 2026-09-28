package app.andrewliang.patches.facebook.disablestoryautoadvance

import app.andrewliang.patches.shared.Constants.COMPATIBILITY_FACEBOOK
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

/**
 * The method of the auto-play navigation controller that runs when a story ends. The controller
 * keeps its original name in a trace string. A table of trace names also holds the string, thus
 * the fingerprint pins the story parameter.
 */
internal object AutoPlayNavigationFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Lcom/facebook/stories/model/StoryCard;"),
    strings = listOf("StoryviewerAutoPlayNavigationController.moveToNextBucketOrThread"),
)

@Suppress("unused")
val disableStoryAutoAdvancePatch = bytecodePatch(
    name = "[Stories] Disable auto advance",
    description = "Keeps each story on the screen until you tap or swipe to the next one.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_FACEBOOK)

    // When the progress bar of a story is full, the auto-play navigation controller moves to the
    // next story. Before it moves, it asks one predicate if auto play is off. The predicate is true
    // for Facebook's own setting `disable_storyviewer_autoplay`, and for its test builds. The patch
    // makes the predicate true, so the viewer acts as it does with that setting on.
    //
    // The predicate is the only static method of the controller that takes the controller, returns
    // a boolean, and asks `EndToEnd.isRunningEndToEndTest`. That name is kept.
    execute {
        val controller = AutoPlayNavigationFingerprint.method.definingClass
        val isAutoPlayOff = mutableClassDefBy(controller).methods.single { method ->
            AccessFlags.STATIC.isSet(method.accessFlags) &&
                method.returnType == "Z" &&
                method.parameterTypes.map { it.toString() } == listOf(controller) &&
                method.implementation?.instructions?.any { instruction ->
                    instruction.opcode == Opcode.INVOKE_STATIC &&
                        ((instruction as ReferenceInstruction).reference as MethodReference).name ==
                        "isRunningEndToEndTest"
                } == true
        }

        isAutoPlayOff.addInstructions(
            0,
            """
                const/4 v0, 0x1
                return v0
            """,
        )
    }
}
