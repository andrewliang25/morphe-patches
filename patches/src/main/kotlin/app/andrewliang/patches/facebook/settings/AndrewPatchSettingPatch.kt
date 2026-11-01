package app.andrewliang.patches.facebook.settings

import app.andrewliang.patches.facebook.shared.FACEBOOK_SETTINGS
import app.andrewliang.patches.facebook.shared.facebookSettingsExtensionPatch
import app.andrewliang.patches.facebook.shared.markFacebookIncluded
import app.andrewliang.patches.shared.Constants.COMPATIBILITY_FACEBOOK
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

/**
 * `onCreateView` of the Menu drawer fragment (`BookmarkComponentsFragment` before Redex, `LX/BS1`
 * in 577). The class name is lost, but the method keeps the old name as a string, and it names
 * its surface "sidebar_menu".
 */
internal object MenuDrawerCreateViewFingerprint : Fingerprint(
    name = "onCreateView",
    returnType = "Landroid/view/View;",
    filters = listOf(string("BookmarkComponentsFragment"), string("sidebar_menu")),
)

@Suppress("unused")
val andrewPatchSettingPatch = bytecodePatch(
    name = "[General] Andrew's Patch Setting",
    description = "Adds \"Andrew's Patch Setting\" to the top of the Facebook menu. There you can " +
        "turn some patches on or off without patching again, and see the credits and licenses.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_FACEBOOK)

    dependsOn(facebookSettingsExtensionPatch)

    // The drawer fragment returns a FrameLayout that holds the drawer's list. Before it returns,
    // the extension adds the row to that frame and moves the list down.
    execute {
        val method = MenuDrawerCreateViewFingerprint.method
        val returns = method.implementation!!.instructions.withIndex()
            .filter { it.value.opcode == Opcode.RETURN_OBJECT }
        check(returns.size == 1) { "Expected 1 return in the menu drawer onCreateView, found ${returns.size}" }

        val (index, instruction) = returns.single()
        val register = (instruction as OneRegisterInstruction).registerA
        // The range form takes any register. The return is not a branch target, so the call runs.
        method.addInstructions(
            index,
            """
                invoke-static/range { v$register .. v$register }, $FACEBOOK_SETTINGS->addMenuRow(Landroid/view/View;)Landroid/view/View;
                move-result-object v$register
            """,
        )

        markFacebookIncluded("rowIncluded")
    }
}
