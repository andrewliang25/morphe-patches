package app.andrewliang.patches.line.disablepay

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall

/**
 * `PayLaunchActivity.onCreate` — the non-obfuscated translucent front door every external Pay
 * route funnels through (deep links, chat send-money, split-bill, QR, Wallet-tab shortcut).
 * The `methodCall("onCreate")` filter locates the `super.onCreate` call so the disable code is
 * injected right after it (finishing before super would throw SuperNotCalledException).
 */
internal object PayLaunchActivityOnCreateFingerprint : Fingerprint(
    definingClass = "Lcom/linecorp/line/pay/base/PayLaunchActivity;",
    name = "onCreate",
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;"),
    filters = listOf(methodCall(name = "onCreate")),
)

/**
 * `PayLiffActivity.onCreate` — the LIFF (web) Pay container, an independent VKey path that a
 * LIFF pay link can reach without going through PayLaunchActivity.
 */
internal object PayLiffActivityOnCreateFingerprint : Fingerprint(
    definingClass = "Lcom/linecorp/line/pay/impl/liff/common/PayLiffActivity;",
    name = "onCreate",
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;"),
    filters = listOf(methodCall(name = "onCreate")),
)
