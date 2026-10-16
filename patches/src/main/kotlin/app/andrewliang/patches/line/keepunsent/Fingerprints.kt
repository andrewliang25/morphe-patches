package app.andrewliang.patches.line.keepunsent

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.value.StringEncodedValue

/**
 * The lambda that applies an incoming unsend to the local database — obfuscated
 * `la8.x.invoke(Object)`, run inside a `chat_history` transaction by the
 * `NOTIFIED_DESTROY_MESSAGE`(65) / `DESTROY_MESSAGE`(64) op handlers (`bh8.c1` / `bh8.r`).
 *
 * LINE does NOT delete the row here: it rewrites `chat_history.type` to an `na8.c.UNSENT*`
 * variant and NULLs `content`, `parameter`, `attachement_type` and the location columns, then
 * drops the message from the full-text-search index and deletes its `reactions` and
 * `multiple_image_message_mapping` rows. Every one of those writes sits behind a single
 * "is this row already an unsend tombstone?" guard (`na8.c.g()`), which is what the patch flips.
 *
 * Anchored on the four SQL table/where-clause string literals the method uses for its cleanup
 * deletes. They are non-obfuscated, appear in this exact program order, and this cluster of four
 * is unique to this one method across the whole APK (every other class that mentions
 * `multiple_image_message_mapping` or `local_message_id = ?` is missing at least one of the rest).
 * The `Object invoke(Object)` signature additionally pins it to the Kotlin lambda itself.
 *
 * The strings are only used to *locate* the method — the guard is then found by instruction shape,
 * because `na8.c` and its `g()` are obfuscated and drift between LINE versions.
 */
internal object UnsendMessageDbWriteFingerprint : Fingerprint(
    returnType = "Ljava/lang/Object;",
    parameters = listOf("Ljava/lang/Object;"),
    filters = listOf(
        string("reactions"),
        string("server_message_id=?"),
        string("multiple_image_message_mapping"),
        string("local_message_id = ?"),
    ),
)

/**
 * `toString()` of LINE's view data for one chat message (obfuscated `te1.c` on 26.15.1, `m91.c` on
 * 26.14.0). The patch adds the unsent mark to this class as a field. The class is a Kotlin data
 * class, so its `toString` keeps the original class name as a string.
 */
internal object MessageViewDataToStringFingerprint : Fingerprint(
    name = "toString",
    returnType = "Ljava/lang/String;",
    parameters = listOf(),
    filters = listOf(
        string("ChatMessageCommonViewData(chatId="),
    ),
)

/**
 * The builder of the message view data (obfuscated `re1.d` on 26.15.1, `k91.e` on 26.14.0). It reads
 * the `EDITED` key from the message's metadata, and then calls the view data constructor.
 *
 * Other methods read `IS_EDITED_MESSAGE` too: the edit merge, the edit send and a key table. Only
 * this one also calls a constructor with 25 or more parameters. Kotlin keeps enum constant names,
 * so `IS_EDITED_MESSAGE` does not drift.
 */
internal object MessageViewDataBuilderFingerprint : Fingerprint(
    filters = listOf(
        fieldAccess(name = "IS_EDITED_MESSAGE", opcode = Opcode.SGET_OBJECT),
    ),
    custom = { method, _ ->
        method.implementation?.instructions?.any { instruction ->
            val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
            instruction.opcode == Opcode.INVOKE_DIRECT_RANGE &&
                reference?.name == "<init>" &&
                reference.parameterTypes.size >= 25
        } == true
    },
)

/**
 * The coroutine that makes LINE's "Edited" label a link (`qr1.f` on 26.15.1, `hm1.e` on 26.14.0).
 * Kotlin's debug metadata keeps the name of the label's controller class,
 * `EditedMarkViewController`. The coroutine's constructor takes that controller as its first
 * parameter, which gives the controller's obfuscated class.
 */
internal object EditedMarkSpanFingerprint : Fingerprint(
    name = "invokeSuspend",
    custom = { _, classDef ->
        classDef.annotations.any { annotation ->
            annotation.elements.any { element ->
                (element.value as? StringEncodedValue)?.value
                    ?.contains("EditedMarkViewController\$applyClickableSpan") == true
            }
        }
    },
)
