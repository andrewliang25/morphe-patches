package app.andrewliang.patches.line.callrecording

import app.andrewliang.patches.line.shared.lineSettingsExtensionPatch
import app.andrewliang.patches.line.shared.markLineSettingIncluded
import app.andrewliang.patches.line.shared.readLineSetting
import app.andrewliang.patches.shared.Constants.COMPATIBILITY_LINE
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

/**
 * Starts recording a 1-to-1 call as soon as it connects. LINE's own recorder does the capture and
 * saves the file. Use the "Auto-record calls" switch to turn it off.
 *
 * After the recording extension is set up in `premiumcall/a.a()` (`iv7.b.M(iv7.b$a)` in 26.17.1),
 * the patch calls the start method of the recorder (`hv7.a.J()`) on the same object. R8 renames all
 * three, so the patch finds them by shape. The recorder is the `check-cast` type before the
 * `instance-of`. The setup call is the first call on the `instance-of` type. The start method is the
 * `()V` method whose implementation writes `Boolean.TRUE` (stop writes `FALSE`). The setup argument
 * register is dead right after the call, so it carries the switch value.
 */
@Suppress("unused")
val autoRecordCallsPatch = bytecodePatch(
    name = "[Call] Auto-record calls",
    description = "Starts recording a 1-to-1 LINE call as soon as it connects. LINE saves the " +
        "recording. Recording a call without the other person's consent is illegal " +
        "in some places. You are responsible for following the law where you are.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_LINE)

    dependsOn(callRecordingEnablePatch, lineSettingsExtensionPatch)

    execute {
        val gate = CallRecordingGateFingerprint.method

        val instructions = gate.implementation!!.instructions.toList()
        val instanceOfIndex = instructions.indexOfFirst { it.opcode == Opcode.INSTANCE_OF }
        check(instanceOfIndex >= 0) { "could not find the recorder instance-of in premiumcall.a.a()" }
        val extensionType = (instructions[instanceOfIndex] as ReferenceInstruction).reference.toString()
        val recorderType = instructions.subList(0, instanceOfIndex)
            .last { it.opcode == Opcode.CHECK_CAST }
            .let { (it as ReferenceInstruction).reference.toString() }

        val setupIndex = instructions.indices.firstOrNull { index ->
            index > instanceOfIndex &&
                instructions[index].opcode == Opcode.INVOKE_INTERFACE &&
                ((instructions[index] as ReferenceInstruction).reference as MethodReference)
                    .definingClass == extensionType
        }
        checkNotNull(setupIndex) { "could not find the recorder setup call in premiumcall.a.a()" }
        val setup = instructions[setupIndex] as FiveRegisterInstruction
        val objectRegister = setup.registerC
        val switchRegister = setup.registerD
        val startMethod = recorderStartMethod(recorderType, extensionType)

        gate.addInstructionsWithLabels(
            setupIndex + 1,
            readLineSetting("autoRecordCalls", "v$switchRegister") +
                """
                    if-eqz v$switchRegister, :skip
                    invoke-interface {v$objectRegister}, $recorderType->$startMethod()V
                """,
            ExternalLabel("skip", gate.getInstruction(setupIndex + 1)),
        )

        markLineSettingIncluded("autoRecordCalls")
    }
}

/**
 * The name of the recorder's start method: the abstract `()V` method of [recorderType] whose
 * implementation, in the class that implements [extensionType], writes `Boolean.TRUE`.
 */
private fun BytecodePatchContext.recorderStartMethod(recorderType: String, extensionType: String): String {
    val candidates = classDefBy(recorderType).methods
        .filter { it.returnType == "V" && it.parameterTypes.isEmpty() }
        .map { it.name }
        .toSet()
    var start: String? = null
    classDefForEach { classDef ->
        if (start != null || extensionType !in classDef.interfaces) return@classDefForEach
        start = classDef.methods.firstOrNull { method ->
            method.name in candidates &&
                method.returnType == "V" &&
                method.parameterTypes.isEmpty() &&
                method.implementation?.instructions?.any {
                    it.opcode == Opcode.SGET_OBJECT &&
                        (it as ReferenceInstruction).reference.toString() ==
                        "Ljava/lang/Boolean;->TRUE:Ljava/lang/Boolean;"
                } == true
        }?.name
    }
    return checkNotNull(start) { "could not find the recorder start method on $recorderType" }
}
