package app.andrewliang.patches.line.disablevoom

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.AccessFlags

/**
 * `bh8.n0.a(Context, String, k): i` — the URL-scheme handler that owns LINE's entire
 * `line://home/` scheme space (VOOM's scheme host is "home"). Every VOOM deep link, share,
 * and scheme-notification routes here. Anchored on distinctive VOOM path-segment strings
 * (`"hashtaglist"`, `"deleteCache"`) rather than the obfuscated class name.
 *
 * Note: the disable injection returns `Lah8/i;->b:Lah8/i$a;` (the existing "not handled"
 * singleton) — those are obfuscated names, so this target needs re-mapping on LINE updates.
 */
internal object VoomSchemeHandlerFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Lah8/i;",
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
