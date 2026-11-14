package app.andrewliang.patches.line.hideagenti

import app.andrewliang.patches.line.settings.SettingsRowsFingerprint
import app.andrewliang.patches.line.settings.settingsListGetter
import app.andrewliang.patches.line.shared.LINE_SETTINGS
import app.andrewliang.patches.line.shared.hideIfLineSetting
import app.andrewliang.patches.line.shared.lineSettingsExtensionPatch
import app.andrewliang.patches.line.shared.markLineSettingIncluded
import app.andrewliang.patches.line.shared.readLineSetting
import app.andrewliang.patches.line.shared.serverFlagAccessor
import app.andrewliang.patches.shared.Constants.COMPATIBILITY_LINE
import app.andrewliang.patches.shared.resourceIdsPatch
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.instruction.OffsetInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

private const val SWITCH = "hideAgentI"
private const val ROWS_METHOD = "andrewHideAgentIRows"

@Suppress("unused")
val hideAgentIButtonsPatch = bytecodePatch(
    name = "[General] Hide Agent i buttons",
    description = "Removes Agent i from the Home header, the search bar, the Wallet tab header and " +
        "Settings, and hides Agent i in chats permanently, with its \"Show Agent i in chat\" switch " +
        "in the + menu.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_LINE)
    dependsOn(resourceIdsPatch, lineSettingsExtensionPatch)

    // Each site reads the switch "Hide Agent i". With the switch off, LINE runs as without the
    // patch.
    execute {
        // Home header: the Agent i flag goes through the switch right after the lambda reads it.
        // The register can be above v15, so the call uses a range.
        val flagRead = HomeHeaderAgentIFingerprint.instructionMatches.first()
        val flag = (flagRead.instruction as TwoRegisterInstruction).registerA
        HomeHeaderAgentIFingerprint.method.addInstructions(
            flagRead.index + 1,
            """
                invoke-static/range {v$flag .. v$flag}, $LINE_SETTINGS->$SWITCH(Z)Z
                move-result v$flag
            """,
        )

        // Search bar: the accessor of its server flag gives false while the switch is on. The
        // search bar then hides the button and moves its guideline back, as for an account without
        // the feature. The accessor is a field getter with one return and no branch.
        val accessor = serverFlagAccessor(SearchBarAiEntryFlagFingerprint)
        val accessorCode = accessor.implementation!!.instructions
        if (accessorCode.any { it is OffsetInstruction }) throw PatchException("Search bar flag accessor branches")
        val ret = accessorCode.withIndex().singleOrNull { it.value.opcode == Opcode.RETURN }
            ?: throw PatchException("Search bar flag accessor has no single return")
        val retRegister = (ret.value as OneRegisterInstruction).registerA
        if (retRegister > 15) throw PatchException("Search bar flag accessor returns v$retRegister")
        accessor.addInstructions(ret.index, hideIfLineSetting(SWITCH, "v$retRegister"))

        // Chats: while the switch is on, both getters of the hidden-until time return
        // Long.MAX_VALUE. The chat and the Settings screen only compare it with the current time,
        // so Agent i stays hidden whatever the user picks. The result register pair is written
        // only by the getter's `move-result-wide`, so it is free at entry.
        val getters = mutableListOf<String>()
        classDefForEach { classDef ->
            val matches = classDef.methods.filter(::isAgentIInChatHiddenUntilGetter)
            if (matches.isEmpty()) return@classDefForEach

            val mutableClass = mutableClassDefBy(classDef.type)
            matches.forEach { match ->
                val getter = mutableClass.methods.first {
                    it.name == match.name && it.returnType == "J" && it.parameterTypes.isEmpty()
                }
                val result = (getter.getInstruction(2) as OneRegisterInstruction).registerA
                getter.addInstructionsWithLabels(
                    0,
                    readLineSetting(SWITCH, "v$result") +
                        """
                            if-eqz v$result, :stock
                            const-wide v$result, 0x7fffffffffffffffL
                            return-wide v$result
                        """,
                    ExternalLabel("stock", getter.getInstruction(0)),
                )
                getters += "${classDef.type}->${match.name}()J"
            }
        }
        check(getters.size == 2) { "Expected 2 Agent i in chat hidden-until getters, found $getters" }

        // + menu: the "Show Agent i in chat" switch gets the value "hidden" while the switch is on.
        // LINE then sets the view GONE and builds no content. The call goes right after the
        // check-cast, which no branch targets.
        PlusMenuAgentISwitchFingerprint.apply {
            val cast = instructionMatches[3].index
            val shown = (instructionMatches[5].instruction as OneRegisterInstruction).registerA
            if (shown > 15) throw PatchException("+ menu Agent i switch flag is in v$shown")
            method.addInstructions(cast + 1, hideIfLineSetting(SWITCH, "v$shown"))
        }

        // Wallet tab header: while the switch is on, return before the button state is built.
        // v0 is free at entry: the method writes it first.
        WalletAgentIButtonFingerprint.method.apply {
            addInstructionsWithLabels(
                0,
                readLineSetting(SWITCH, "v0") +
                    """
                        if-eqz v0, :stock
                        return-void
                    """,
                ExternalLabel("stock", getInstruction(0)),
            )
        }

        // Settings: both rows ("Agent i" in Japan, "LINE AI services" elsewhere) have the row key
        // of the `AiSubscription` constant. The list getter of the static Settings list calls a new
        // method that drops the rows with that key while the switch is on.
        val builder = SettingsRowsFingerprint.method
        val instructions = builder.implementation!!.instructions.toList()
        val constantIndex = instructions.indexOfFirst {
            it.opcode == Opcode.SGET_OBJECT &&
                ((it as ReferenceInstruction).reference as FieldReference).name == "AiSubscription"
        }
        if (constantIndex < 0) throw PatchException("No AiSubscription row in the Settings list")
        val constant = (instructions[constantIndex] as ReferenceInstruction).reference as FieldReference
        val keyAccessor = (instructions[constantIndex + 1] as ReferenceInstruction).reference as MethodReference
        if (keyAccessor.definingClass != constant.type || keyAccessor.returnType != "Ljava/lang/String;") {
            throw PatchException("Unexpected key accessor of the AiSubscription row: $keyAccessor")
        }

        val rowsClass = builder.definingClass
        val rows = MutableMethod(
            ImmutableMethod(
                rowsClass,
                ROWS_METHOD,
                listOf(ImmutableMethodParameter("Ljava/util/List;", null, null)),
                "Ljava/util/List;",
                AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
                null,
                null,
                MutableMethodImplementation(2),
            ),
        )
        rows.addInstructions(
            0,
            """
                sget-object v0, $constant
                invoke-virtual {v0}, $keyAccessor
                move-result-object v0
                invoke-static {p0, v0}, $LINE_SETTINGS->$SWITCH(Ljava/util/List;Ljava/lang/String;)Ljava/util/List;
                move-result-object p0
                return-object p0
            """,
        )
        mutableClassDefBy(rowsClass).methods.add(rows)

        // The list getter: `sget-object` of the list, then `return-object`. The call goes right
        // after the read and has no branch.
        val getter = settingsListGetter(rowsClass)
        val listRegister = (getter.getInstruction(0) as OneRegisterInstruction).registerA
        getter.addInstructions(
            1,
            """
                invoke-static {v$listRegister}, $rowsClass->$ROWS_METHOD(Ljava/util/List;)Ljava/util/List;
                move-result-object v$listRegister
            """,
        )

        markLineSettingIncluded(SWITCH)
    }
}
