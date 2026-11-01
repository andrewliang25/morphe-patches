package app.andrewliang.patches.facebook.shared

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.AccessFlags

/** The switches of "[General] Andrew's Patch Setting" for Facebook. See the extension class. */
internal const val FACEBOOK_SETTINGS = "Lapp/andrewliang/extension/FacebookSettings;"

/**
 * Bundles the extension. Each patch with a switch depends on this, because its gate calls
 * [FACEBOOK_SETTINGS] whether or not another patch in the build brings the extension.
 */
internal val facebookSettingsExtensionPatch = bytecodePatch {
    extendWith("extensions/extension.mpe")
}

/**
 * Tells the settings screen that the patch of [feature] is in this build, so that the screen
 * shows its switch. It rewrites `<feature>Included()`, a plain "return false", to return true.
 */
internal fun BytecodePatchContext.markFacebookSettingIncluded(feature: String) =
    markFacebookIncluded("${feature}Included")

/** Rewrites the `()Z` method [name] of [FACEBOOK_SETTINGS] to return true. */
internal fun BytecodePatchContext.markFacebookIncluded(name: String) {
    mutableClassDefBy(FACEBOOK_SETTINGS).methods.single { it.name == name }.addInstructions(
        0,
        """
            const/4 v0, 0x1
            return v0
        """,
    )
}

/** Smali that leaves the switch value of [feature] in [register]: 1 when on, 0 when off. */
internal fun readFacebookSetting(feature: String, register: String) = """
    invoke-static {}, $FACEBOOK_SETTINGS->$feature()Z
    move-result $register
"""

/** At the start of a method, v0 is free if it is not a parameter register. */
internal fun MutableMethod.hasFreeV0(): Boolean {
    val wide = parameterTypes.count { it.toString() == "J" || it.toString() == "D" }
    val parameterRegisters = parameterTypes.size + wide +
        if (AccessFlags.STATIC.isSet(accessFlags)) 0 else 1
    return implementation!!.registerCount > parameterRegisters
}

/**
 * Adds [smali] at the start of the method. It runs only while the switch [feature] is on. With
 * the switch off, the original body runs. [smali] can branch to `:stock`, the first original
 * instruction. The gate writes v0, thus v0 must not be a parameter register.
 */
internal fun MutableMethod.addInstructionsIfFacebookSetting(feature: String, smali: String) {
    check(hasFreeV0()) { "v0 of $definingClass->$name is a parameter, so the $feature gate cannot use it" }
    addInstructionsWithLabels(
        0,
        readFacebookSetting(feature, "v0") + "if-eqz v0, :stock\n" + smali,
        ExternalLabel("stock", getInstruction(0)),
    )
}

/**
 * Inserts [smali] at [index]. It runs only while the switch [feature] is on. [scratch] must be
 * free at [index]: [smali] can use it, and the code after it must write it before it reads it.
 */
internal fun MutableMethod.addInstructionsIfFacebookSetting(index: Int, feature: String, scratch: String, smali: String) =
    addInstructionsWithLabels(
        index,
        readFacebookSetting(feature, scratch) + "if-eqz $scratch, :skip\n" + smali,
        ExternalLabel("skip", getInstruction(index)),
    )

/**
 * Makes the boolean that a `move-result` at [index] - 1 left in [register] true while the switch
 * [feature] is on, and keeps Facebook's value while it is off. It calls the `(Z)Z` overload of
 * [feature], so it needs no free register and no branch.
 */
internal fun MutableMethod.forceTrueIfFacebookSetting(index: Int, register: Int, feature: String) =
    addInstructions(
        index,
        """
            invoke-static/range { v$register .. v$register }, $FACEBOOK_SETTINGS->$feature(Z)Z
            move-result v$register
        """,
    )
