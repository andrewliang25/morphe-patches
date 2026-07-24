package app.andrewliang.patches.line.readreceipts

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.AccessFlags

/**
 * Matches `LegacyTalkServiceClientImpl$e.b(Lorg/apache/thrift/o;)Ljava/lang/Object;` — the
 * method that issues the "sendChatChecked" (mark-as-read) Thrift request in LINE.
 *
 * Anchored on the stable `"sendChatChecked"` string literal; the obfuscated class and
 * method names are intentionally not pinned since they drift between app versions. The
 * string plus an `Object` return type and a single object parameter excludes the
 * `sendChatChecked_args`/`_result` structs (their `toString` carries the same string but
 * returns `String` and takes no parameter).
 */
internal object SendChatCheckedFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Ljava/lang/Object;",
    // Obfuscated parameter class (org.apache.thrift.o) -> match any object type ("L").
    parameters = listOf("L"),
    filters = listOf(
        string("sendChatChecked"),
    ),
)
