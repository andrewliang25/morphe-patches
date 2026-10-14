package app.andrewliang.patches.line.keepunread

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall

/**
 * The 1:1/group read worker `d(J, String, Z)` — it performs both the local read-state update and
 * the server read receipt (`sendChatChecked`). It is the ONLY method in the APK that calls the
 * `TalkServiceClient` method `(I, String, String)V`, which makes it a stable anchor to locate the
 * read-manager class (all of its names are obfuscated and it has no string literals). R8 renames
 * that client method, so the call is matched by signature, not by name.
 *
 * We do not patch this worker (it is shared by manual mark-as-read). We only use it to find the
 * class, then no-op the sibling open-on-view wrapper `b(String, String)` in the same class.
 */
internal object ReadWorkerFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("J", "Ljava/lang/String;", "Z"),
    filters = listOf(
        methodCall(
            definingClass = "Ljp/naver/line/android/thrift/client/TalkServiceClient;",
            returnType = "V",
            parameters = listOf("I", "Ljava/lang/String;", "Ljava/lang/String;"),
        ),
    ),
)
