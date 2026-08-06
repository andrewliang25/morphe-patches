package app.andrewliang.patches.line.keepunsent

import app.andrewliang.patches.shared.Constants.COMPATIBILITY_LINE
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val SQLITE_DATABASE = "Landroid/database/sqlite/SQLiteDatabase;"
private const val EXTENSION = "Lapp/andrewliang/extension/KeepUnsentMessages;"
private const val INSERT_PLACEHOLDER = "insertPlaceholder(${SQLITE_DATABASE}J)V"

/** Highest register addressable by the 4-bit operands of `invoke-*` (35c) and `iget-*` (22c). */
private const val MAX_NIBBLE_REGISTER = 15

@Suppress("unused")
val keepUnsentMessagesPatch = bytecodePatch(
    name = "Keep unsent messages",
    description = "Keeps messages that were unsent in 1:1 and group chats on your device " +
        "instead of destroying them, and shows the usual \"unsent a message\" notice directly " +
        "below the message it kept. Doesn't apply to OpenChat.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_LINE)

    extendWith("extensions/extension.mpe")

    // An incoming unsend is applied by the g38.b0 lambda, which rewrites chat_history.type to
    // i38.c.UNSENT and NULLs content/parameter/attachement_type/locations, then drops the row from
    // the search index and deletes its reactions + multiple_image_message_mapping rows. All of
    // that hangs off one guard:
    //
    //     invoke-virtual {v6}, Li38/c;->h()Z   # already an unsend tombstone?
    //     move-result v6
    //     if-eqz v6, :cond_1                   # no  -> run the destructive block
    //     goto/16 :goto_c                      # yes -> skip it all, return the row unchanged
    //
    // Forcing the guard register non-zero always takes the "skip" branch, so the message survives
    // intact — text, media, reactions and its search-index entry. LINE never learns it was
    // recalled, so nothing marks it: we insert our own i38.c.UNSENT (dbValue 27) row just below
    // it, which LINE's stock renderer draws as "<name> unsent a message." with correct sender
    // naming and localisation. See the extension for the INSERT.
    //
    // The guard is located by instruction shape rather than by name: i38.c and its h() are
    // obfuscated and drift. The SQLiteDatabase field read (g38.f3.b) is likewise taken from the
    // method's own bytecode instead of being hardcoded.
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

        // The message id, loaded from the fetched row a few instructions above the guard and still
        // live there. Which of i38.b's two long fields this is (local id vs server message id)
        // doesn't matter — the extension's WHERE clause accepts either.
        val messageIdRegister = (guardIndex - 1 downTo 0)
            .firstOrNull { instructions[it].opcode == Opcode.IGET_WIDE }
            ?.let { (instructions[it] as TwoRegisterInstruction).registerA }
            ?: throw PatchException("unsend: message id read not found in ${method.definingClass}")

        // The transaction's SQLiteDatabase, read off the g38.f3 receiver inside the block we skip.
        // Reuse that exact field reference and holder register rather than hardcoding g38.f3.b.
        val databaseRead = (guardIndex until instructions.size).firstNotNullOfOrNull { index ->
            val reference = (instructions[index] as? ReferenceInstruction)?.reference as? FieldReference
            if (instructions[index].opcode == Opcode.IGET_OBJECT && reference?.type == SQLITE_DATABASE) {
                reference to (instructions[index] as TwoRegisterInstruction).registerB
            } else {
                null
            }
        }
        val (databaseField, databaseHolderRegister) = databaseRead
            ?: throw PatchException("unsend: SQLiteDatabase field read not found in ${method.definingClass}")

        // v0-v15 only for iget-object (22c) and invoke-static (35c). On 26.11.0 these are v1/v6/v7,
        // but if a future version spills them we still keep the message and just skip the notice
        // rather than emitting instructions that silently assemble to the wrong registers.
        val registersAddressable = maxOf(
            guardRegister,
            databaseHolderRegister,
            messageIdRegister + 1,
        ) <= MAX_NIBBLE_REGISTER

        val addPlaceholder = if (registersAddressable) {
            """
                iget-object v$guardRegister, v$databaseHolderRegister, ${databaseField.definingClass}->${databaseField.name}:${databaseField.type}
                invoke-static {v$guardRegister, v$messageIdRegister, v${messageIdRegister + 1}}, $EXTENSION->$INSERT_PLACEHOLDER
            """
        } else {
            ""
        }

        // The guard register is dead here (the skipped block reassigns it immediately), so it is
        // free to borrow for the database reference before being forced to 1.
        method.addInstructions(
            guardIndex + 2,
            """
                $addPlaceholder
                const/16 v$guardRegister, 0x1
            """,
        )
    }
}
