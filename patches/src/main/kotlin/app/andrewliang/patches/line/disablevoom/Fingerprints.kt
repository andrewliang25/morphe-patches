package app.andrewliang.patches.line.disablevoom

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.AccessFlags

/**
 * `a(Context, String, k): i` — the URL-scheme handler that owns LINE's entire
 * `line://home/` scheme space (VOOM's scheme host is "home"). Every VOOM deep link, share,
 * and scheme-notification routes here. Anchored on distinctive VOOM path-segment strings
 * (`"hashtaglist"`, `"deleteCache"`) rather than the obfuscated class name.
 *
 * The disable injection returns the existing "not handled" singleton, the static field `b` of
 * the return type's `$a` companion. The patch reads the return type off the matched method.
 */
internal object VoomSchemeHandlerFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    parameters = listOf("Landroid/content/Context;", "Ljava/lang/String;", "L"),
    filters = listOf(
        string("hashtaglist"),
        string("deleteCache"),
    ),
)

/**
 * `LineVoomActivity.onCreate` — the standalone full VOOM feed, launched by the notification
 * center bypassing the scheme router (non-obfuscated). The `methodCall("onCreate")` filter
 * locates the super call so `finish()` is injected after it.
 */
internal object LineVoomActivityOnCreateFingerprint : Fingerprint(
    definingClass = "Lcom/linecorp/line/timeline/notification/impl/activity/LineVoomActivity;",
    name = "onCreate",
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;"),
    filters = listOf(methodCall(name = "onCreate")),
)
