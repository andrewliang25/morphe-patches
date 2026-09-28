package app.andrewliang.patches.facebook.blockfeedautorefresh

import app.andrewliang.patches.shared.Constants.COMPATIBILITY_FACEBOOK
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.removeInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

private const val IS_AUTOMATIC = "andrewIsAutomaticRevisit"

/**
 * The reasons that the news feed fragment gives when it comes back to the screen by itself. The
 * other callers give "onActivityResult" and "FullscreenVideoViewCloseEvent", and the patch keeps
 * them.
 */
private val AUTOMATIC_REASONS = listOf("onResume", "onAppForeground")

/** The value that `maybeRefreshForWarmStart` returns when it decides to skip the refresh. */
private const val SKIPPED_WARM_START = 2

/** `schedule(runnable, tag, name, delayMs)` of the handler that the loader posts its reset to. */
private val SCHEDULE_PARAMETERS = listOf(
    "Ljava/lang/Runnable;",
    "Ljava/lang/String;",
    "Ljava/lang/String;",
    "J",
)

/**
 * `NewsFeedFragmentDataController.refreshForRevisit`. Redex keeps the method name. Its last but one
 * parameter is the reason for the revisit.
 */
internal object RefreshForRevisitFingerprint : Fingerprint(
    name = "refreshForRevisit",
    returnType = "Z",
    parameters = listOf("Z", "Z", "Z", "I", "Ljava/lang/String;", "Z"),
)

/**
 * `maybeRefreshForWarmStart(source)` of the main feed loader. It logs its own name, and the name of
 * its branch for an empty feed.
 */
internal object WarmStartRefreshFingerprint : Fingerprint(
    returnType = "I",
    parameters = listOf("Ljava/lang/String;"),
    strings = listOf("maybeRefreshForWarmStart", "doHeadLoadOnEmptyFeed"),
)

/**
 * `onUserLeftApp` of the main feed loader. It logs its own name and the tag that it schedules the
 * loader reset with.
 */
internal object UserLeftAppFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Z"),
    strings = listOf("onUserLeftApp", "BaseFeedCSRDataLoaderAdapter"),
)

@Suppress("unused")
val blockFeedAutoRefreshPatch = bytecodePatch(
    name = "[Feed] Block feed auto refresh",
    description = "Keeps your place in the news feed when you come back to Facebook. Pull down " +
        "to refresh the feed.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_FACEBOOK)

    // When you come back to the app after some minutes, Facebook replaces the feed on the screen
    // in three ways, and the patch stops each one:
    //
    // 1. The loader reset. When you leave the app, onUserLeftApp schedules a reset of the feed
    //    loader. After the delay the feed is empty, so your return loads it again from the start.
    // 2. The warm start. The feed fragment's onStart asks maybeRefreshForWarmStart, which loads a
    //    new feed head (cause "warm") when the feed is stale.
    // 3. The revisit refresh. onResume and onAppForeground call refreshForRevisit. When the feed
    //    is stale, the onResume call can rank the feed again and clear the screen.
    //
    // Other paths load the feed, so they stay: pull to refresh, a tap on the Home tab, and the
    // refreshes after an activity result or a full-screen video.
    execute {
        keepLoaderInBackground(UserLeftAppFingerprint.method)
        blockWarmStartRefresh(WarmStartRefreshFingerprint.method)
        blockAutomaticRevisit(RefreshForRevisitFingerprint.method)
    }
}

/**
 * Remove the call in `onUserLeftApp` that schedules the loader reset. The method still records the
 * time that you left, which the stale checks read. The calls that cancel the reset stay too.
 */
private fun keepLoaderInBackground(method: MutableMethod) {
    val schedule = method.implementation!!.instructions.withIndex().filter { (_, instruction) ->
        val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
        reference != null &&
            reference.returnType == "V" &&
            reference.parameterTypes.map { it.toString() } == SCHEDULE_PARAMETERS
    }
    check(schedule.size == 1) { "Expected 1 loader reset schedule in onUserLeftApp, found ${schedule.size}" }

    method.removeInstruction(schedule.single().index)
}

/**
 * Make `maybeRefreshForWarmStart` skip the refresh when the feed is not empty.
 *
 * The method already returns [SKIPPED_WARM_START] when its own check decides to skip, so the
 * callers handle that value. An empty feed still loads, so a feed that has no posts yet fills.
 */
private fun blockWarmStartRefresh(method: MutableMethod) {
    val instructions = method.implementation!!.instructions.toList()

    check(
        instructions.zipWithNext().any { (constant, ret) ->
            constant.opcode == Opcode.CONST_4 &&
                (constant as NarrowLiteralInstruction).narrowLiteral == SKIPPED_WARM_START &&
                ret.opcode == Opcode.RETURN &&
                (ret as OneRegisterInstruction).registerA == (constant as OneRegisterInstruction).registerA
        },
    ) { "maybeRefreshForWarmStart no longer returns $SKIPPED_WARM_START to skip" }

    // The empty-feed check is the first private no-argument boolean method that it calls.
    val isFeedEmpty = instructions.firstNotNullOfOrNull { instruction ->
        if (instruction.opcode != Opcode.INVOKE_DIRECT) return@firstNotNullOfOrNull null
        val reference = (instruction as ReferenceInstruction).reference as MethodReference
        reference.takeIf {
            it.definingClass == method.definingClass &&
                it.parameterTypes.isEmpty() &&
                it.returnType == "Z"
        }
    } ?: error("Empty-feed check not found in maybeRefreshForWarmStart")

    // p0 can be above v15, so the call uses a range. v0 is a local that the original code sets
    // before it reads it.
    method.addInstructionsWithLabels(
        0,
        """
            invoke-direct/range { p0 .. p0 }, $isFeedEmpty
            move-result v0
            if-nez v0, :stock
            const/4 v0, $SKIPPED_WARM_START
            return v0
        """,
        ExternalLabel("stock", method.getInstruction(0)),
    )
}

/**
 * Make `refreshForRevisit` return false for the automatic reasons, before it does anything. False
 * is the result that it gives when it does not refresh, and neither caller reads it.
 */
private fun BytecodePatchContext.blockAutomaticRevisit(method: MutableMethod) {
    val owner = method.definingClass

    // A new static method holds the string checks. A label inside injected smali resolves against
    // the injected block, not the method. In a method that we build ourselves, the two address
    // spaces are the same.
    val isAutomatic = ImmutableMethod(
        owner,
        IS_AUTOMATIC,
        listOf(ImmutableMethodParameter("Ljava/lang/String;", null, null)),
        "Z",
        AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
        null,
        null,
        MutableMethodImplementation(2),
    ).toMutable().apply {
        addInstructions(
            0,
            buildString {
                AUTOMATIC_REASONS.forEach { reason ->
                    appendLine("const-string v0, \"$reason\"")
                    appendLine("invoke-virtual { v0, p0 }, Ljava/lang/String;->equals(Ljava/lang/Object;)Z")
                    appendLine("move-result v0")
                    appendLine("if-nez v0, :automatic")
                }
                appendLine("const/4 v0, 0x0")
                appendLine("return v0")
                appendLine(":automatic")
                appendLine("const/4 v0, 0x1")
                appendLine("return v0")
            },
        )
    }
    mutableClassDefBy(owner).methods.add(isAutomatic)

    // p5 is the reason. It can be above v15, so the call uses a range.
    method.addInstructionsWithLabels(
        0,
        """
            invoke-static/range { p5 .. p5 }, $owner->$IS_AUTOMATIC(Ljava/lang/String;)Z
            move-result v0
            if-eqz v0, :stock
            const/4 v0, 0x0
            return v0
        """,
        ExternalLabel("stock", method.getInstruction(0)),
    )
}
