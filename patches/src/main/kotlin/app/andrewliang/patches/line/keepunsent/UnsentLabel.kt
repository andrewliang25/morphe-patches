package app.andrewliang.patches.line.keepunsent

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableField.Companion.toMutable
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableField

private const val EXTENSION = "Lapp/andrewliang/extension/KeepUnsentMessages;"
private const val OBJECT = "Ljava/lang/Object;"
private const val TEXT_VIEW = "Landroid/widget/TextView;"
private const val VIEW = "Landroid/view/View;"

/** The field that the patch adds to the view data class. The extension reads it by this name. */
private const val MARK_FIELD = "andrewUnsent"

private val INSTANCE_FIELD_READS = setOf(
    Opcode.IGET, Opcode.IGET_WIDE, Opcode.IGET_OBJECT, Opcode.IGET_BOOLEAN,
    Opcode.IGET_BYTE, Opcode.IGET_CHAR, Opcode.IGET_SHORT,
)

/** Highest register addressable by the 4-bit operands of `invoke-*` (35c). */
private const val MAX_NIBBLE_REGISTER = 15

private fun Instruction.methodReference() = (this as? ReferenceInstruction)?.reference as? MethodReference
private fun Instruction.fieldReference() = (this as? ReferenceInstruction)?.reference as? FieldReference

private fun requireNibble(register: Int, what: String) {
    if (register > MAX_NIBBLE_REGISTER) {
        throw PatchException("unsent label: $what is v$register, past v$MAX_NIBBLE_REGISTER")
    }
}

/**
 * Shows "Unsent" in the place of LINE's "Edited" label, next to the time of each message that
 * [keepUnsentMessagesPatch] kept. The extension marks the row in the database, and these hooks
 * carry the mark from the database to the label:
 *
 * 1. The view data class gets a boolean field, and its `equals` compares that field first.
 *    Without that, the row of a message unsent while its chat is open compares equal to its old
 *    view data, and LINE does not draw it again.
 * 2. The view data builder reads the mark from the message's metadata where it reads `EDITED`, and
 *    puts it into the new field.
 * 3. The row binder calls the extension after it sets the visibility of the "Edited" label.
 */
internal fun BytecodePatchContext.addUnsentLabel() {
    val viewDataType = MessageViewDataToStringFingerprint.method.definingClass
    addMarkField(viewDataType)
    hookBuilder(viewDataType)
    hookBinder(viewDataType)
}

private fun BytecodePatchContext.addMarkField(viewDataType: String) {
    val viewData = mutableClassDefBy(viewDataType)
    viewData.fields.add(
        ImmutableField(viewDataType, MARK_FIELD, "Z", AccessFlags.PUBLIC.value, null, null, null)
            .toMutable(),
    )

    // A false result returns false. Otherwise LINE's own comparison runs unchanged. v0 is a free
    // local at the method head.
    val equals = viewData.methods.singleOrNull {
        it.name == "equals" && it.parameterTypes.map(CharSequence::toString) == listOf(OBJECT)
    } ?: throw PatchException("unsent label: equals not found in $viewDataType")
    val registers = equals.implementation!!.registerCount
    if (registers < 3) throw PatchException("unsent label: equals of $viewDataType has no local")
    requireNibble(registers - 1, "the parameter of $viewDataType.equals")
    equals.addInstructionsWithLabels(
        0,
        """
            invoke-static {p0, p1}, $EXTENSION->sameMark($OBJECT$OBJECT)Z
            move-result v0
            if-nez v0, :compare
            return v0
        """,
        ExternalLabel("compare", equals.getInstruction(0)),
    )
}

private fun BytecodePatchContext.hookBuilder(viewDataType: String) {
    val builder = MessageViewDataBuilderFingerprint.method
    val instructions = builder.implementation!!.instructions.toList()

    // The EDITED read: `invoke-virtual {vMetadata, vKey}, <metadata>->b(<key>)Z`, right after the
    // `sget-object` of IS_EDITED_MESSAGE. vMetadata holds the metadata object there.
    val editedKeyIndex = MessageViewDataBuilderFingerprint.instructionMatches.first().index
    val editedReadIndex = (editedKeyIndex + 1 until instructions.size).firstOrNull { index ->
        val reference = instructions[index].methodReference()
        instructions[index].opcode == Opcode.INVOKE_VIRTUAL &&
            reference?.returnType == "Z" && reference.parameterTypes.size == 1
    } ?: throw PatchException("unsent label: EDITED read not found in ${builder.definingClass}")
    val metadataRegister = (instructions[editedReadIndex] as FiveRegisterInstruction).registerC
    val keyRegister = (instructions[editedKeyIndex] as OneRegisterInstruction).registerA
    if ((instructions[editedReadIndex] as FiveRegisterInstruction).registerD != keyRegister) {
        throw PatchException("unsent label: EDITED read does not take the key in ${builder.definingClass}")
    }

    val constructorIndex = (editedReadIndex + 1 until instructions.size).firstOrNull { index ->
        val reference = instructions[index].methodReference()
        instructions[index].opcode == Opcode.INVOKE_DIRECT_RANGE &&
            reference?.definingClass == viewDataType && reference.name == "<init>"
    } ?: throw PatchException("unsent label: $viewDataType constructor not found in ${builder.definingClass}")
    val viewDataRegister = (instructions[constructorIndex] as RegisterRangeInstruction).startRegister
    requireNibble(viewDataRegister, "the new $viewDataType")

    // From the last index to the first, so that each index is still valid.
    builder.addInstructions(
        constructorIndex + 1,
        "invoke-static {v$viewDataRegister}, $EXTENSION->attach($OBJECT)V",
    )
    builder.addInstructions(
        editedReadIndex,
        "invoke-static {v$metadataRegister}, $EXTENSION->noteMetadata($OBJECT)V",
    )
    builder.addInstructions(0, "invoke-static {}, $EXTENSION->clearPending()V")
}

private fun BytecodePatchContext.hookBinder(viewDataType: String) {
    // The label's controller: the first constructor parameter of the coroutine that makes the
    // label a link. Its only TextView field is the label.
    val spanClass = mutableClassDefBy(EditedMarkSpanFingerprint.method.definingClass)
    val controllerType = spanClass.methods.firstOrNull { it.name == "<init>" }
        ?.parameterTypes?.firstOrNull()?.toString()
        ?: throw PatchException("unsent label: no constructor in ${spanClass.type}")
    val labelField = mutableClassDefBy(controllerType).fields.singleOrNull { it.type == TEXT_VIEW }
        ?: throw PatchException("unsent label: no single TextView field in $controllerType")

    // The binder: the one method that reads the label, sets its visibility, and then reads the
    // visibility back.
    data class Site(val type: String, val method: String, val read: Int)
    val sites = mutableListOf<Site>()
    classDefForEach { classDef ->
        if (classDef.type.startsWith("Lapp/andrewliang/")) return@classDefForEach
        classDef.methods.forEach { method ->
            val list = method.implementation?.instructions?.toList() ?: return@forEach
            list.indices.forEach { index ->
                if (labelVisibilityRead(list, index, labelField.name, controllerType) != null) {
                    sites += Site(classDef.type, method.name + method.parameterTypes.joinToString(), index)
                }
            }
        }
    }
    val site = sites.singleOrNull()
        ?: throw PatchException("unsent label: expected 1 label binder, found ${sites.size}: $sites")

    val binder = mutableClassDefBy(site.type).methods.single {
        it.name + it.parameterTypes.joinToString() == site.method
    }
    val instructions = binder.implementation!!.instructions.toList()
    val labelRead = instructions[site.read] as TwoRegisterInstruction
    val controllerRegister = labelRead.registerB
    val moveResultIndex = labelVisibilityRead(instructions, site.read, labelField.name, controllerType)!!

    // The controller register must still hold the controller after the visibility read.
    if ((site.read + 1..moveResultIndex).any { writes(instructions[it], controllerRegister) }) {
        throw PatchException("unsent label: v$controllerRegister is written before the hook in ${site.type}")
    }

    // The view data of the row: the last read of one of its fields before the label read. That read
    // takes the view data in its object register, so the hook before it sees the same object.
    val viewDataReadIndex = (site.read - 1 downTo 0).firstOrNull { index ->
        val field = instructions[index].fieldReference()
        instructions[index].opcode in INSTANCE_FIELD_READS && field?.definingClass == viewDataType
    } ?: throw PatchException("unsent label: no $viewDataType field read in ${site.type}")
    val viewDataRegister = (instructions[viewDataReadIndex] as TwoRegisterInstruction).registerB

    // After LINE's own visibility read, so that LINE decides alone whether to make the label a link.
    binder.addInstructions(
        moveResultIndex + 1,
        "invoke-static {v$controllerRegister}, $EXTENSION->bindLabel($OBJECT)V",
    )
    binder.addInstructions(
        viewDataReadIndex,
        "invoke-static {v$viewDataRegister}, $EXTENSION->noteBound($OBJECT)V",
    )
}

/**
 * When [index] is `iget-object vL, vC, <controller>-><label>`, followed by `setVisibility` on vL,
 * then `getVisibility` on vL and its `move-result`: the index of that `move-result`. Otherwise null.
 */
private fun labelVisibilityRead(
    list: List<Instruction>,
    index: Int,
    labelName: String,
    controllerType: String,
): Int? {
    val read = list[index]
    val field = read.fieldReference()
    if (read.opcode != Opcode.IGET_OBJECT || field?.definingClass != controllerType ||
        field.name != labelName
    ) {
        return null
    }
    val label = (read as TwoRegisterInstruction).registerA

    val set = (index + 1 until minOf(index + 12, list.size)).firstOrNull {
        list[it].methodReference()?.let { reference ->
            reference.definingClass == VIEW && reference.name == "setVisibility"
        } == true && (list[it] as FiveRegisterInstruction).registerC == label
    } ?: return null
    val get = set + 1
    val reference = list.getOrNull(get)?.methodReference()
    if (reference?.name != "getVisibility" || (list[get] as FiveRegisterInstruction).registerC != label) {
        return null
    }
    return if (list.getOrNull(get + 1)?.opcode == Opcode.MOVE_RESULT) get + 1 else null
}

/** Whether [instruction] writes [register] (or the second half of a wide pair starting at it). */
private fun writes(instruction: Instruction, register: Int): Boolean {
    if (!instruction.opcode.setsRegister()) return false
    val target = when (instruction) {
        is OneRegisterInstruction -> instruction.registerA
        is TwoRegisterInstruction -> instruction.registerA
        else -> return false
    }
    return target == register || (instruction.opcode.setsWideRegister() && target + 1 == register)
}
