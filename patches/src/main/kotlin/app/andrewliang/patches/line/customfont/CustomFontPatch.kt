package app.andrewliang.patches.line.customfont

import app.andrewliang.patches.line.shared.hideIfLineSetting
import app.andrewliang.patches.line.shared.lineSettingsExtensionPatch
import app.andrewliang.patches.line.shared.markLineSettingIncluded
import app.andrewliang.patches.shared.Constants.COMPATIBILITY_LINE
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import org.w3c.dom.Element

private const val EXTENSION_CLASS = "Lapp/andrewliang/extension/CustomFont;"
private const val ACTIVITY_CLASS = "app.andrewliang.extension.CustomFontActivity"

/**
 * Manifest half: declares the Activity that picks the font file. It runs in its own process, so
 * that it can end LINE's main process and start LINE again with the new font.
 */
private val customFontManifestPatch = resourcePatch {
    execute {
        document("AndroidManifest.xml").use { document ->
            val application = document.getElementsByTagName("application").item(0) as Element
            application.appendChild(
                document.createElement("activity").apply {
                    setAttribute("android:name", ACTIVITY_CLASS)
                    setAttribute("android:exported", "false")
                    setAttribute("android:process", ":customfont")
                    setAttribute("android:theme", "@android:style/Theme.Translucent.NoTitleBar")
                    setAttribute("android:configChanges", "orientation|screenSize|screenLayout|keyboardHidden")
                },
            )
        }
    }
}

@Suppress("unused")
val customFontPatch = bytecodePatch(
    name = "[General] Custom font",
    description = "Adds a button to LINE's Font page to use a font file from your device. " +
        "The page then no longer loads LINE's font list from the server.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_LINE)

    dependsOn(customFontManifestPatch, lineSettingsExtensionPatch)

    extendWith("extensions/extension.mpe")

    // LINE draws its premium fonts through `@font/premium_font`, a downloadable font that its own
    // `PremiumFontProvider` serves. The patch serves the custom font through that same path, so
    // the font reaches every view that LINE's own font setting reaches. See the extension.
    execute {
        // Find every method and every index before the first change, because the patcher does not
        // undo a partial execute.
        val listLoader = FontListLoaderFingerprint.method
        val syncFlag = findSyncFlag(listLoader) ?: throw PatchException("Font list loader: no sync flag")
        val providerOnCreate = PremiumFontProviderOnCreateFingerprint.method
        val providerCheck = FontProviderCheckFingerprint.method
        val providerQuery = PremiumFontProviderQueryFingerprint.method
        val providerOpenFile = PremiumFontProviderOpenFileFingerprint.method
        val settingView = FontSettingViewFingerprint.method

        // The certificate check fails at its last `const/4 vA, 0x0` + `return-object vA`. The
        // method reads `ProviderInfo.packageName` from the provider register, and it writes that
        // register only once.
        val checkInstructions = providerCheck.instructionList()
        val nullIndex = checkInstructions.indices.lastOrNull { i ->
            checkInstructions[i].opcode == Opcode.CONST_4 &&
                checkInstructions.getOrNull(i + 1)?.opcode == Opcode.RETURN_OBJECT
        } ?: throw PatchException("Font provider check: no null return")
        val nullRegister = (checkInstructions[nullIndex] as OneRegisterInstruction).registerA
        val providerRegister = checkInstructions.firstOrNull { instruction ->
            instruction.opcode == Opcode.IGET_OBJECT &&
                ((instruction as ReferenceInstruction).reference as FieldReference).let {
                    it.definingClass == "Landroid/content/pm/ProviderInfo;" && it.name == "packageName"
                }
        }?.let { (it as TwoRegisterInstruction).registerB }
            ?: throw PatchException("Font provider check: no ProviderInfo.packageName read")
        if (providerRegister > 15) throw PatchException("Font provider check: v$providerRegister is above v15")

        // LINE's openFile throws when it has no font file, so the patch serves the custom font
        // before LINE's body runs. That needs one local register. LINE's body writes it before it
        // reads it.
        val openFileLocals = providerOpenFile.implementation!!.registerCount - (providerOpenFile.parameters.size + 1)
        if (openFileLocals < 1) throw PatchException("PremiumFontProvider.openFile has no free register")

        val queryReturn = providerQuery.lastReturnObject()
        val queryRegister = (providerQuery.getInstruction(queryReturn) as OneRegisterInstruction).registerA
        val viewReturn = settingView.lastReturnObject()
        val viewRegister = (settingView.getInstruction(viewReturn) as OneRegisterInstruction).registerA

        // While the switch is on, never sync the Font page list from LINE's server. The sync can
        // fail, LINE hides the cause, and the page then shows only an error. Without the sync, LINE
        // builds the list from its local database: "Default" plus the fonts that an earlier sync
        // stored. The gate runs after LINE's `move` that sets the flag to "sync".
        listLoader.addInstructions(
            syncFlag.index + 1,
            hideIfLineSetting("customFont", "v${syncFlag.register}"),
        )

        // At process start, put LINE's font style on each Activity while a custom font is set.
        // The extension takes the provider itself, so the patch reuses no register of the method.
        providerOnCreate.addInstructions(
            0,
            "invoke-static { p0 }, $EXTENSION_CLASS->onProviderCreate(Landroid/content/ContentProvider;)V",
        )

        // Accept LINE's own provider where the certificate check fails.
        providerCheck.replaceInstruction(
            nullIndex,
            "invoke-static { v$providerRegister }, $EXTENSION_CLASS->" +
                "allowOwnProvider(Landroid/content/pm/ProviderInfo;)Landroid/content/pm/ProviderInfo;",
        )
        providerCheck.addInstructions(nullIndex + 1, "move-result-object v$nullRegister")

        // Pass LINE's answer through the extension, which replaces it only for a custom font.
        providerQuery.addInstructions(
            queryReturn,
            """
                invoke-static { v$queryRegister, p4 }, $EXTENSION_CLASS->query(Landroid/database/Cursor;[Ljava/lang/String;)Landroid/database/Cursor;
                move-result-object v$queryRegister
            """,
        )

        providerOpenFile.addInstructionsWithLabels(
            0,
            """
                invoke-static { }, $EXTENSION_CLASS->openFile()Landroid/os/ParcelFileDescriptor;
                move-result-object v0
                if-eqz v0, :original
                return-object v0
            """,
            ExternalLabel("original", providerOpenFile.getInstruction(0)),
        )

        // LINE builds the Font page with Jetpack Compose. Put the buttons below LINE's view.
        settingView.addInstructions(
            viewReturn,
            """
                invoke-static { v$viewRegister }, $EXTENSION_CLASS->wrapSettingsView(Landroid/view/View;)Landroid/view/View;
                move-result-object v$viewRegister
            """,
        )

        markLineSettingIncluded("customFont")
    }
}

private fun MutableMethod.instructionList() = implementation!!.instructions.toList()

private fun MutableMethod.lastReturnObject(): Int {
    val index = instructionList().indexOfLast { it.opcode == Opcode.RETURN_OBJECT }
    if (index < 0) throw PatchException("$name has no return-object")
    return index
}
