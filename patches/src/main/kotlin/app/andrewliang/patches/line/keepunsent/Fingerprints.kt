package app.andrewliang.patches.line.keepunsent

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.string

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
