package app.andrewliang.patches.line.externalbrowser

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall

/**
 * Matches `com.linecorp.browser.OpenUriActivity.h5(OpenUriActivity, Intent, Continuation)` —
 * the coroutine body that decides Custom Tab vs in-app WebView vs external for a tapped link.
 *
 * The obfuscated method name (`h5`) and the `Continuation` param are not pinned. The two
 * method-call filters — the internal/LIFF gate `v98/c.k(String)` followed by the http/https
 * test `OpenUriActivity$b.b(Uri)` — uniquely identify the method, and `instructionMatches[1]`
 * (the `$b.b` call right after the gate) is the `:cond_d` injection point (the non-internal
 * branch, i.e. after LIFF/line:// links have already been routed away).
 */
internal object OpenUriHandlerFingerprint : Fingerprint(
    definingClass = "Lcom/linecorp/browser/OpenUriActivity;",
    returnType = "Ljava/lang/Object;",
    parameters = listOf(
        "Lcom/linecorp/browser/OpenUriActivity;",
        "Landroid/content/Intent;",
        "L", // Continuation (obfuscated) -> match any object.
    ),
    filters = listOf(
        methodCall(definingClass = "Lv98/c;", name = "k"),
        methodCall(definingClass = "Lcom/linecorp/browser/OpenUriActivity\$b;", name = "b"),
    ),
)
