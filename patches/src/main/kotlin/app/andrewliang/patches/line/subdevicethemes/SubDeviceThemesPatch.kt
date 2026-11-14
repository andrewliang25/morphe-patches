package app.andrewliang.patches.line.subdevicethemes

import app.andrewliang.patches.shared.Constants.COMPATIBILITY_LINE
import app.andrewliang.patches.shared.resourceIdsPatch
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

private const val EXTENSION = "Lapp/andrewliang/extension/SubDeviceThemes;"

private val Instruction.methodReference: MethodReference?
    get() = (this as? ReferenceInstruction)?.reference as? MethodReference

@Suppress("unused")
val subDeviceThemesPatch = bytecodePatch(
    name = "[General] Enable themes on sub devices",
    description = "Lets a sub device (a tablet or second phone logged in to the same account) use " +
        "themes and the Black theme in dark mode. Hides the Theme Shop button there, because " +
        "LINE does not open the shop on a sub device.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_LINE)
    dependsOn(resourceIdsPatch)
    extendWith("extensions/extension.mpe")

    execute {
        // 1. Dark-mode check. LINE reads the sub-device flag through
        // `sget-object key; invoke-static lookup(context, key); check-cast; invoke-interface ()Z`.
        // Clear the flag after its move-result, so the check goes on to the user's setting.
        val gate = DarkModeGateFingerprint.method
        val gateInstructions = gate.implementation!!.instructions.toList()
        val flagCall = gateInstructions.indexOfFirst {
            it.opcode == Opcode.INVOKE_INTERFACE &&
                it.methodReference!!.let { ref -> ref.returnType == "Z" && ref.parameterTypes.isEmpty() }
        }
        if (flagCall < 3 ||
            gateInstructions[flagCall + 1].opcode != Opcode.MOVE_RESULT ||
            gateInstructions[flagCall - 1].opcode != Opcode.CHECK_CAST ||
            gateInstructions[flagCall - 3].opcode != Opcode.INVOKE_STATIC
        ) {
            throw PatchException("Sub-device check not found in the dark-mode check")
        }
        val keyIndex = (flagCall - 4 downTo 0).first { gateInstructions[it].opcode == Opcode.SGET_OBJECT }
        val key = (gateInstructions[keyIndex] as ReferenceInstruction).reference
        val lookup = gateInstructions[flagCall - 3].methodReference!!
        val flagType = (gateInstructions[flagCall - 1] as ReferenceInstruction).reference
        val isSubDevice = gateInstructions[flagCall].methodReference!!
        val flag = (gateInstructions[flagCall + 1] as OneRegisterInstruction).registerA
        gate.addInstruction(flagCall + 2, "const/4 v$flag, 0x0")

        // A helper on the same class reads the flag the same way: isSubDevice(Context)Z.
        val helper = MutableMethod(
            ImmutableMethod(
                gate.definingClass,
                "isSubDevice",
                listOf(ImmutableMethodParameter("Landroid/content/Context;", null, null)),
                "Z",
                AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
                null,
                null,
                MutableMethodImplementation(2),
            ),
        ).apply {
            addInstructions(
                0,
                """
                    sget-object v0, $key
                    invoke-static { p0, v0 }, $lookup
                    move-result-object v0
                    check-cast v0, $flagType
                    invoke-interface { v0 }, $isSubDevice
                    move-result v0
                    return v0
                """,
            )
        }
        mutableClassDefBy(gate.definingClass).methods.add(helper)
        val helperRef = "${gate.definingClass}->isSubDevice(Landroid/content/Context;)Z"

        // 2. Settings Themes row: its visibility predicate is `!isSubDevice`. Pass LINE's own
        // always-true predicate instead. The predicate is the last object parameter of the row
        // constructor, and the base row class keeps the shared predicates in static fields.
        ThemesRowFingerprint.method.apply {
            val instructions = implementation!!.instructions.toList()
            val start = ThemesRowFingerprint.instructionMatches[0].index
            val ctorIndex = (start until instructions.size).first {
                instructions[it].opcode == Opcode.INVOKE_DIRECT_RANGE &&
                    instructions[it].methodReference!!.name == "<init>" &&
                    (instructions[it] as RegisterRangeInstruction).registerCount > 8
            }
            val ctor = instructions[ctorIndex] as RegisterRangeInstruction
            val ctorRef = instructions[ctorIndex].methodReference!!
            val visibilityParam = ctorRef.parameterTypes.map { it.toString() }.indexOfLast { it.startsWith("L") }
            val visibilityRegister = ctor.startRegister + 1 + visibilityParam

            val baseRow = mutableClassDefBy(mutableClassDefBy(ctorRef.definingClass).superclass!!)
            val alwaysTrue = baseRow.fields.firstOrNull { field ->
                if (field.accessFlags and AccessFlags.STATIC.value == 0) return@firstOrNull false
                val type = classDefByOrNull(field.type) ?: return@firstOrNull false
                type.methods.any { m ->
                    m.name == "invokeSuspend" && m.implementation?.instructions?.let { ins ->
                        ins.count() <= 5 && ins.any {
                            (it as? ReferenceInstruction)?.reference.toString() ==
                                "Ljava/lang/Boolean;->TRUE:Ljava/lang/Boolean;"
                        }
                    } == true
                }
            } ?: throw PatchException("Always-true predicate not found in ${baseRow.type}")

            addInstruction(
                ctorIndex,
                "sget-object v$visibilityRegister, ${baseRow.type}->${alwaysTrue.name}:${alwaysTrue.type}",
            )
        }

        // 3. My Themes: hide the Theme Shop button on a sub device. Inject after the button's
        // null check, where the next instruction (new-instance) overwrites a free register.
        ThemeShopFooterFingerprint.method.apply {
            val instructions = implementation!!.instructions.toList()
            val cast = ThemeShopFooterFingerprint.instructionMatches[2].index
            val button = (instructions[cast] as OneRegisterInstruction).registerA
            if (instructions[cast + 1].opcode != Opcode.IF_EQZ ||
                instructions[cast + 2].opcode != Opcode.NEW_INSTANCE
            ) {
                throw PatchException("Theme Shop button null check not found")
            }
            val free = (instructions[cast + 2] as OneRegisterInstruction).registerA
            if (button > 15 || free > 15) throw PatchException("Theme Shop button registers out of v0-v15")
            addInstructions(
                cast + 2,
                """
                    invoke-virtual { v$button }, Landroid/view/View;->getContext()Landroid/content/Context;
                    move-result-object v$free
                    invoke-static { v$free }, $helperRef
                    move-result v$free
                    invoke-static { v$free, v$button }, $EXTENSION->hideThemeShop(ZLandroid/view/View;)V
                """,
            )
        }
    }
}
