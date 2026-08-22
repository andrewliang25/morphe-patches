package app.andrewliang.patches.line.externalbrowser

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall

/**
 * `com.linecorp.browser.OpenUriActivity$b.a(...)` — the single Intent builder for opening a
 * URL. For http/https URLs it switches on the `OpenUriActivity$a` browser-mode parameter to
 * decide Custom Tab vs in-app WebView vs external. Overwriting that mode parameter with
 * `EXTERNAL_WITHOUT_CUSTOMTABS` routes every web-URL open into LINE's own native
 * external-browser path (which has its own ActivityNotFoundException fallback). Non-web
 * schemes skip the mode switch, so LIFF / line:// / tel: do not change.
 *
 * Non-obfuscated class + a distinctive signature (returns Intent, takes the OpenUriActivity$a
 * mode). The `$b.b(Uri)` web-gate call is used as an extra anchor.
 */
internal object OpenUriIntentBuilderFingerprint : Fingerprint(
    definingClass = "Lcom/linecorp/browser/OpenUriActivity\$b;",
    name = "a",
    returnType = "Landroid/content/Intent;",
    parameters = listOf(
        "Lcom/linecorp/browser/OpenUriActivity\$b;",
        "Landroid/content/Context;",
        "Landroid/net/Uri;",
        "Lcom/linecorp/browser/OpenUriActivity\$a;",
        "Lv98/k;",
        "Z",
        "Ljava/lang/String;",
        "I",
    ),
    filters = listOf(
        methodCall(definingClass = "Lcom/linecorp/browser/OpenUriActivity\$b;", name = "b"),
    ),
)
