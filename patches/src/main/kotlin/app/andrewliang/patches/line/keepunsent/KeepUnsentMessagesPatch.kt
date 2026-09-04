package app.andrewliang.patches.line.keepunsent

import app.andrewliang.patches.shared.Constants.COMPATIBILITY_LINE
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

private const val SQLITE_DATABASE = "Landroid/database/sqlite/SQLiteDatabase;"
private const val EXTENSION = "Lapp/andrewliang/extension/KeepUnsentMessages;"
private const val INSERT_PLACEHOLDER = "insertPlaceholder(${SQLITE_DATABASE}J)V"

/** Highest register addressable by the 4-bit operands of `invoke-*` (35c) and `iget-*` (22c). */
private const val MAX_NIBBLE_REGISTER = 15

@Suppress("unused")
val keepUnsentMessagesPatch = bytecodePatch(
    name = "Keep unsent messages",
    description = "Keeps unsent messages from 1:1 and group chats on your device instead of " +
        "erasing them. This patch shows the usual \"unsent a message\" notice directly below the " +
        "message that it kept. This patch does not apply to OpenChat.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_LINE)

    extendWith("extensions/extension.mpe")

    // An incoming unsend is applied by the la8.x lambda, which rewrites chat_history.type to
    // na8.c.UNSENT and NULLs content/parameter/attachement_type/locations, then drops the row from
    // the search index and deletes its reactions + multiple_image_message_mapping rows. All of
    // that hangs off one guard:
    //
    //     invoke-virtual {v6}, Lna8/c;->g()Z   # already an unsend tombstone?
    //     move-result v6
    //     if-eqz v6, :cond_1                   # no  -> run the destructive block
    //     goto/16 :goto_c                      # yes -> skip it all, return the row unchanged
    //
    // Forcing the guard register non-zero always takes the "skip" branch, so the message survives
    // intact — text, media, reactions, search-index entry. LINE never learns it was recalled, so we
    // insert our own na8.c.UNSENT (dbValue 27) row just below it, which the stock renderer draws as
    // "<name> unsent a message." with correct sender naming and localisation (INSERT in the
    // extension).
    //
    // The guard is located by instruction shape, not by name — na8.c and its g() drift — and the
    // SQLiteDatabase field read (la8.b3.b) likewise comes from the method's own bytecode.
    execute {
        val method = UnsendMessageDbWriteFingerprint.method
        val instructions = method.implementation!!.instructions.toList()

        // The guard: a no-arg boolean call whose result is immediately branched on, where the
        // fall-through is an unconditional jump (the "already unsent, skip everything" exit).
        // No other call in this method has that shape.
        val guardIndex = instructions.indices.firstOrNull { index ->
            val reference = (instructions[index] as? ReferenceInstruction)?.reference as? MethodReference
            instructions[index].opcode == Opcode.INVOKE_VIRTUAL &&
                reference?.returnType == "Z" &&
                reference.parameterTypes.isEmpty() &&
                instructions.getOrNull(index + 1)?.opcode == Opcode.MOVE_RESULT &&
                instructions.getOrNull(index + 2)?.opcode == Opcode.IF_EQZ &&
                instructions.getOrNull(index + 3)?.opcode in
                setOf(Opcode.GOTO, Opcode.GOTO_16, Opcode.GOTO_32)
        } ?: throw PatchException("unsend: already-unsent guard not found in ${method.definingClass}")

        val guardRegister = (instructions[guardIndex + 1] as OneRegisterInstruction).registerA

        // The fetched row (na8.b), taken from whatever the guard reads its receiver off. Anchoring on
        // it keeps the id search below from wandering: an `iget-wide` off the same object the guard
        // reads is the row's id, and sits in the same straight-line stretch, so its destination
        // register is live where we inject.
        val guardReceiverRegister = (instructions[guardIndex] as FiveRegisterInstruction).registerC
        val rowReadIndex = (guardIndex - 1 downTo 0).firstOrNull { index ->
            instructions[index].opcode == Opcode.IGET_OBJECT &&
                (instructions[index] as TwoRegisterInstruction).registerA == guardReceiverRegister
        } ?: throw PatchException("unsend: guard receiver read not found in ${method.definingClass}")
        val rowRegister = (instructions[rowReadIndex] as TwoRegisterInstruction).registerB

        // The message id, read off that row between load and guard. Which of na8.b's longs it is
        // (local vs server id) does not matter — the extension's WHERE clause accepts either. On
        // 26.11.0 it is `b`, the server id.
        val messageIdRegister = ((rowReadIndex + 1) until guardIndex)
            .firstOrNull { index ->
                instructions[index].opcode == Opcode.IGET_WIDE &&
                    (instructions[index] as TwoRegisterInstruction).registerB == rowRegister
            }
            ?.let { (instructions[it] as TwoRegisterInstruction).registerA }
            ?: throw PatchException("unsend: message id read not found in ${method.definingClass}")

        // Registers whose type is proven before the guard. The lambda's parameter is `check-cast`ed
        // at the method head, and that cast dominates our injection point, so such a register still
        // holds that type where we inject — which a field read taken from inside the skipped block
        // does not by itself establish.
        val castRegisters = (0 until guardIndex).mapNotNull { index ->
            val instruction = instructions[index]
            if (instruction.opcode != Opcode.CHECK_CAST) return@mapNotNull null
            val type = ((instruction as ReferenceInstruction).reference as? TypeReference)?.type
                ?: return@mapNotNull null
            (instruction as OneRegisterInstruction).registerA to type
        }.toMap()

        // The transaction's SQLiteDatabase, read off the la8.b3 receiver. Reuse that exact field
        // reference and holder register rather than hardcoding la8.b3.b. The field reference
        // travels safely (it is position-independent). The register number only does if the
        // register provably holds the field's owner at the injection point, so require a
        // dominating cast. That also rejects the other SQLiteDatabase read in this method
        // (ma8.t0.a), whose holder register is reassigned without a cast.
        //
        // Search the whole method, not just from the guard onwards: on 26.11.0 this read sat
        // inside the block the guard skips, but on 26.14.0 R8 hoisted every la8.b3 field read
        // into the prologue, ahead of the guard. Both versions select the same instruction,
        // because it is the first SQLiteDatabase read in the method either way.
        val databaseRead = instructions.indices.firstNotNullOfOrNull { index ->
            val reference = (instructions[index] as? ReferenceInstruction)?.reference as? FieldReference
            if (instructions[index].opcode != Opcode.IGET_OBJECT || reference?.type != SQLITE_DATABASE) {
                return@firstNotNullOfOrNull null
            }
            val holderRegister = (instructions[index] as TwoRegisterInstruction).registerB
            if (castRegisters[holderRegister] != reference.definingClass) {
                return@firstNotNullOfOrNull null
            }
            reference to holderRegister
        }
        val (databaseField, databaseHolderRegister) = databaseRead
            ?: throw PatchException("unsend: SQLiteDatabase field read not found in ${method.definingClass}")

        // v0-v15 only for iget-object (22c) and invoke-static (35c). Referencing v16+ there is
        // silently mis-assembled. On 26.11.0 these are v1/v6/v7, but the method is `.locals 29`,
        // so a future version could spill them. Fail loudly rather than keeping messages with no
        // notice: an unmarked kept message is indistinguishable from one that was never unsent.
        val highestRegister = maxOf(
            guardRegister,
            databaseHolderRegister,
            messageIdRegister + 1,
        )
        if (highestRegister > MAX_NIBBLE_REGISTER) {
            throw PatchException(
                "unsend: guard/database/message-id registers spilled past " +
                    "v$MAX_NIBBLE_REGISTER (highest is v$highestRegister) " +
                    "in ${method.definingClass}",
            )
        }

        // The guard register is dead here (the skipped block reassigns it immediately), so it is
        // free to borrow for the database reference before being forced to 1.
        method.addInstructions(
            guardIndex + 2,
            """
                iget-object v$guardRegister, v$databaseHolderRegister, ${databaseField.definingClass}->${databaseField.name}:${databaseField.type}
                invoke-static {v$guardRegister, v$messageIdRegister, v${messageIdRegister + 1}}, $EXTENSION->$INSERT_PLACEHOLDER
                const/16 v$guardRegister, 0x1
            """,
        )
    }
}
