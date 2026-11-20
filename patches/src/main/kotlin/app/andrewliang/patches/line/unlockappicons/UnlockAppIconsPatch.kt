package app.andrewliang.patches.line.unlockappicons

import app.andrewliang.patches.line.shared.LINE_SETTINGS
import app.andrewliang.patches.line.shared.lineSettingsExtensionPatch
import app.andrewliang.patches.line.shared.markLineSettingIncluded
import app.andrewliang.patches.line.shared.readLineSetting
import app.andrewliang.patches.shared.Constants.COMPATIBILITY_LINE
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

@Suppress("unused")
val unlockAppIconsPatch = bytecodePatch(
    name = "[Premium] Unlock app icons",
    description = "Shows the App icon page in Settings, also with \"Disable LINE Premium\" on. It also lists " +
        "the collaboration icons that are in the app but not offered now. You can apply every icon on the " +
        "page, also the Premium ones.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_LINE)

    dependsOn(lineSettingsExtensionPatch)

    // The icons are in the APK, and LINE applies one on the phone (it enables a launcher
    // activity-alias). The server only sends the list of icons that the page shows.
    execute {
        // Lever 1: the App icon gate. "[Premium] Disable LINE Premium" makes its Premium market
        // flag false, which hides the row and lets the startup rollback reset the icon. The switch makes
        // that flag true here only. The server flag and the region check stay.
        val gate = AppIconGateFingerprint.method
        val flagCall = premiumFlagCallIndex(gate)
        val flagResult = gate.getInstruction(flagCall + 1)
        if (flagResult.opcode != Opcode.MOVE_RESULT) throw PatchException("app icons: no result after the Premium flag")
        val flagRegister = (flagResult as OneRegisterInstruction).registerA
        if (flagRegister > 15) throw PatchException("app icons: the Premium flag is in v$flagRegister")

        // Lever 2: the eligibility check returns `Eligible` for APP_ICON. Other features keep
        // LINE's answer.
        val eligibility = PremiumEligibilityFingerprint.method
        val featureType = eligibility.parameterTypes.first().toString()
        val appIcon = mutableClassDefBy(featureType).fields.singleOrNull { it.name == "APP_ICON" }
            ?: throw PatchException("app icons: the feature enum $featureType has no APP_ICON")

        val eligibleClass = mutableClassDefBy(EligibleToStringFingerprint.method.definingClass)
        // The check must build its other result (`Ineligible`) from the same sealed interface.
        val resultPrefix = eligibleClass.type.substringBeforeLast('$') + "$"
        val buildsResult = eligibility.implementation!!.instructions.any { instruction ->
            instruction.opcode == Opcode.NEW_INSTANCE &&
                ((instruction as ReferenceInstruction).reference as TypeReference).type.startsWith(resultPrefix)
        }
        if (!buildsResult) throw PatchException("app icons: ${eligibleClass.type} is not a result of the eligibility check")
        val eligible = eligibleClass.fields.single { field ->
            AccessFlags.STATIC.isSet(field.accessFlags) && field.type == eligibleClass.type
        }

        // p1 is the feature: v0..v15 because `if-ne` takes 4-bit registers. v0 is dead at entry:
        // the first instruction writes it.
        val featureRegister = eligibility.implementation!!.registerCount - 2
        if (featureRegister > 15) throw PatchException("app icons: the feature is in v$featureRegister")

        // Lever 3: the App icon list gets the collaboration icons that ship in the APK but that the
        // server does not list. The first instruction casts p1 to String, so the JSON is in p1 after it.
        val listParse = AppIconListParseFingerprint.method
        if (listParse.getInstruction(0).opcode != Opcode.CHECK_CAST) {
            throw PatchException("app icons: the list parse does not start with a cast of the JSON")
        }
        val jsonRegister = listParse.implementation!!.registerCount - 1
        if (jsonRegister > 15) throw PatchException("app icons: the JSON is in v$jsonRegister")

        // Every lookup is resolved. The patch mutates from this point.
        gate.addInstructions(
            flagCall + 2,
            """
                invoke-static {v$flagRegister}, $LINE_SETTINGS->unlockAppIcons(Z)Z
                move-result v$flagRegister
            """,
        )

        eligibility.addInstructionsWithLabels(
            0,
            readLineSetting("unlockAppIcons", "v0") + """
                if-eqz v0, :stock
                sget-object v0, $featureType->${appIcon.name}:$featureType
                if-ne p1, v0, :stock
                sget-object v0, ${eligibleClass.type}->${eligible.name}:${eligibleClass.type}
                return-object v0
            """,
            ExternalLabel("stock", eligibility.getInstruction(0)),
        )

        listParse.addInstructions(
            1,
            """
                invoke-static {p1}, Lapp/andrewliang/extension/AppIcons;->addBundledIcons(Ljava/lang/String;)Ljava/lang/String;
                move-result-object p1
            """,
        )

        markLineSettingIncluded("unlockAppIcons")
    }
}
