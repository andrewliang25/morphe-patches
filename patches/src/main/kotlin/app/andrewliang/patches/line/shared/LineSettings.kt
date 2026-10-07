package app.andrewliang.patches.line.shared

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.ExternalLabel

/** The switches of "[General] Andrew's Patch Setting". See the extension class. */
internal const val LINE_SETTINGS = "Lapp/andrewliang/extension/LineSettings;"

/**
 * Bundles the extension. Each patch with a switch depends on this, because its gate calls
 * [LINE_SETTINGS] whether or not another patch in the build brings the extension.
 */
internal val lineSettingsExtensionPatch = bytecodePatch {
    extendWith("extensions/extension.mpe")
}

/**
 * Smali that leaves the switch value of [feature] in [register]: 1 when on, 0 when off.
 * [feature] is the name of a `()Z` method of [LINE_SETTINGS], such as `keepChatsUnread`.
 */
internal fun readLineSetting(feature: String, register: String) = """
    invoke-static {}, $LINE_SETTINGS->$feature()Z
    move-result $register
"""

/**
 * Tells the settings screen that the patch of [feature] is in this build, so that the screen
 * shows its switch. It rewrites `<feature>Included()`, a plain "return false", to return true.
 */
internal fun BytecodePatchContext.markLineSettingIncluded(feature: String) =
    markIncluded("${feature}Included")

/** Rewrites the `()Z` method [name] of [LINE_SETTINGS] to return true. */
internal fun BytecodePatchContext.markIncluded(name: String) {
    mutableClassDefBy(LINE_SETTINGS).methods.single { it.name == name }.addInstructions(
        0,
        """
            const/4 v0, 0x1
            return v0
        """,
    )
}

/**
 * In LINE's main tab-list builder (`b68/g.a()` in 26.14.0), skips the `sget-object` +
 * `ArrayList.add` pair at [index] while the switch [feature] is on. The pair stays in the method, so
 * the fingerprints of the other tab patches still find their constants.
 *
 * v2 holds the switch value. v2 is dead at every pair of the builder: the code after each pair
 * writes v2 before it reads it, or does not read it.
 */
internal fun MutableMethod.skipTab(index: Int, feature: String) = addInstructionsWithLabels(
    index,
    readLineSetting(feature, "v2") + "if-nez v2, :skip",
    ExternalLabel("skip", getInstruction(index + 2)),
)
