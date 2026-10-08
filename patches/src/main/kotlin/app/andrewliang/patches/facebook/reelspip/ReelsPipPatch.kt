package app.andrewliang.patches.facebook.reelspip

import app.andrewliang.patches.shared.Constants.COMPATIBILITY_FACEBOOK
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val FUNCTION0 = "Lkotlin/jvm/functions/Function0;"

/**
 * The device check of Facebook's picture-in-picture helper. It is the only method that asks the
 * system for the picture-in-picture feature.
 */
internal object PipDeviceCheckFingerprint : Fingerprint(
    returnType = "Z",
    parameters = listOf("Landroid/app/Activity;"),
    strings = listOf("android.software.picture_in_picture"),
)

/**
 * Turns on picture in picture when the Reels tab opens. It skips the players of AI styles and
 * names the reels viewer.
 */
internal object ReelsTabArmFingerprint : Fingerprint(
    returnType = "V",
    strings = listOf("ai_styles_drafts", "ai_styles_receiver", "fb_shorts_viewer"),
)

/**
 * Turns on picture in picture for each reel that the Reels tab plays. The data controller of the
 * tab keeps its original name in a trace string.
 */
internal object ReelPlayArmFingerprint : Fingerprint(
    returnType = "V",
    strings = listOf(
        "VideoHomeDataControllerImpl.onLeaveFbShortsAdsView",
        "UNIFIED_PLAYER_VDD_IN_WARION",
    ),
)

private val Method.calls
    get() = implementation!!.instructions.mapIndexedNotNull { index, instruction ->
        ((instruction as? ReferenceInstruction)?.reference as? MethodReference)?.let { index to it }
    }

private val Method.signature get() = parameterTypes.joinToString("")

private val MethodReference.signature get() = parameterTypes.joinToString("")

/** The last call before [end] that [match] accepts. */
private fun Method.lastCallBefore(end: Int, match: (MethodReference) -> Boolean) =
    calls.last { (index, reference) -> index < end && match(reference) }

/**
 * The surface check takes the player configuration and two lambdas, and returns a boolean. It runs
 * the first lambda on the Reels tab and the second lambda on other surfaces.
 */
private fun MethodReference.isSurfaceCheck() =
    returnType == "Z" &&
        parameterTypes.size == 3 &&
        parameterTypes[1].toString() == FUNCTION0 &&
        parameterTypes[2].toString() == FUNCTION0

/** Sets the result of the call at [callIndex] to true. The call itself still runs. */
private fun MutableMethod.forceTrue(callIndex: Int) {
    val moveResult = implementation!!.instructions[callIndex + 1]
    require(moveResult.opcode == Opcode.MOVE_RESULT) { "No boolean result after the call in $name" }
    val register = (moveResult as OneRegisterInstruction).registerA
    addInstruction(callIndex + 2, "const/16 v$register, 0x1")
}

@Suppress("unused")
val reelsPipPatch = bytecodePatch(
    name = "[Reels] Picture-in-picture",
    description = "Keeps a reel playing in a small window when you leave Facebook from the " +
        "Reels tab. Only vertical reels get a window. Needs Android 12 or later.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_FACEBOOK)

    // Facebook has its own picture in picture for the Reels tab, but a server flag turns it off.
    // Before the tab turns on picture in picture, it asks a surface check, which reads that flag.
    // The patch makes the result of this check true at the two places that turn it on. The surface
    // check also serves many other features, so the patch does not change the check itself.
    //
    // The checks that come after it still apply: the device must support picture in picture, and
    // the reel must be portrait or square.
    execute {
        val deviceCheck = PipDeviceCheckFingerprint.method

        fun Method.deviceCheckIndex() = calls.single { (_, reference) ->
            reference.definingClass == deviceCheck.definingClass &&
                reference.name == deviceCheck.name &&
                reference.signature == deviceCheck.signature
        }.first

        // The Reels tab asks the surface check in a helper of its own class that takes the player.
        ReelsTabArmFingerprint.method.let { arm ->
            val (_, helperReference) = arm.lastCallBefore(arm.deviceCheckIndex()) { reference ->
                reference.definingClass == arm.definingClass &&
                    reference.returnType == "Z" &&
                    reference.parameterTypes.size == 1
            }
            val helper = mutableClassDefBy(helperReference.definingClass).methods.single {
                it.name == helperReference.name &&
                    it.signature == helperReference.signature &&
                    it.returnType == "Z"
            }
            val (check, _) = helper.lastCallBefore(Int.MAX_VALUE) { it.isSurfaceCheck() }
            helper.forceTrue(check)
        }

        // The reel player asks the surface check directly, just before the device check.
        ReelPlayArmFingerprint.method.let { arm ->
            val (check, _) = arm.lastCallBefore(arm.deviceCheckIndex()) { it.isSurfaceCheck() }
            arm.forceTrue(check)
        }
    }
}
