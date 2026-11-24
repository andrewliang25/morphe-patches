# LINE Pay

How an external pay URL enters LINE, and the redirect patch. See [README.md](README.md) for the other LINE docs.

## LINE Pay intake & the "[General] Redirect LINE Pay" patch

**Why redirect instead of disable:** the messenger cannot run its own Pay flow on a re-signed build
(the bundled VKey/V-Guard check fails — see `CLAUDE.md`). The patch (still packaged
`line.disablepay`, object `disablePayPatch`) forwards the payment to the user's separately-installed
**standalone LINE Pay app** — unpatched, so integrity passes — then closes the in-app Pay screen. A
failed hand-off degrades to the old "just close" behavior.

### How an external pay URL enters LINE (decompiled 26.14.0)

```
merchant "LINE Pay" link  (line:// or https://line.me/R/…)
  ► jp.naver.line.android.activity.schemeservice.LineSchemeServiceActivity   (EXPORTED router)
  ► ah8.d.d(...) dispatcher → pay handlers (gv3.j / on3.k / ru3.f)
  ► iv3.a.b(ctx, ao3.b)  → Intent(PayLaunchActivity, data=line://pay/…)      [not exported]
    iv3.a.c(...) / PayLiffActivity$a.a(...) → Intent(PayLiffActivity, extra "linepay.intent.extra.URI")
```

- **`PayLaunchActivity`** (`Lcom/linecorp/line/pay/base/PayLaunchActivity;`) — general front door; URL
  is `getIntent().getDataString()` (a `line://pay/…` form).
- **`PayLiffActivity`** (`Lcom/linecorp/line/pay/impl/liff/common/PayLiffActivity;`) — the LIFF/web path
  for the `waitPreLogin` / `lpUsage=STANDALONE` flow. Reads its `Uri` from intent extra
  **`linepay.intent.extra.URI`** (field `f73569l`) and calls LINE's resolver **`l5().r7(uri)`**
  (obfuscated `sv3.n`) for the real `https://web-pay.line.me/…` URL before loading it in a WebView.
- `web-pay.line.me` / `web-tw-pay.line.me` / `/R/iab` are **not** literals in the APK or manifest —
  those hosts are server config. So an `ACTION_VIEW` for `https://web-tw-pay.line.me/R/iab?…` fired
  from inside LINE is not caught by the messenger. It resolves to the standalone app.

### The redirect

Both Pay activities are intercepted at `onCreate`, right after `super.onCreate`
(`PayLaunchActivityOnCreateFingerprint` / `PayLiffActivityOnCreateFingerprint`, `methodCall("onCreate")`
= the super call). Injected: `invoke-static {p0}, …LinePayRedirect;->redirect` then `finish(); return-void`.
The extension (`app/andrewliang/extension/LinePayRedirect.java`) reads the intent (extra
`linepay.intent.extra.URI`, else `getDataString()`) and builds the standalone url:

- **`…/pay/payment/<reserveId>`** deep link (the merchant checkout case) — the last path segment IS
  the `transactionReserveId` (**device-confirmed**: it decodes identically to the reserve id in the
  known-good web-pay url). Rebuilds
  `https://web-pay.line.me/web/payment/waitPreLogin?transactionReserveId=<reserveId>&locale=zh-TW_LP`.
- an already-resolved `web-pay.line.me` url — used as-is.
- anything else (for example `line://pay/main`) — no reserve id → **no redirect**, the activity just
  `finish()`es (a loop guard: never wrap a link that would round-trip back to the messenger).

then fires

```
https://web-tw-pay.line.me/R/iab?url=<urlencoded inner web-pay url>
```

with `FLAG_ACTIVITY_NEW_TASK`, swallowing all exceptions so `finish()` always runs. A token-free
breadcrumb is logged under logtag **`AndrewLinePay`**. The single-use reserve id deliberately is not.

**Device-confirmed (LINE 26.11.0):** tapping a merchant "LINE Pay" button
(`http://line.me/R/pay/payment/<reserveId>`) reaches **`PayLaunchActivity`** with
`getDataString() == line://pay/payment/<reserveId>` — not `PayLiffActivity`, whose extra was null —
and the reconstruction opened the standalone app on the transaction. The `PayLiffActivity` hook is
kept as defensive coverage for the `lpUsage=STANDALONE` route; if a future version routes there with
no usable web url in the intent, reuse LINE's `r7()` resolver (anchor on the stable `"lpUsage"` /
`"STANDALONE"` literals and read the obfuscated `l5()`/`r7()` descriptors from the matches — do not
hardcode `sv3.n`, which drifts).

### Browser checkout path (issue #161)

A merchant website in a mobile browser does not link to LINE directly. Its LINE Pay button opens a
web-pay page first, and that page then opens LINE:

```
merchant checkout in the browser
  ► https://web-pay.line.me/web/payment/wait?transactionReserveId=<reserveId>
      (forwarder: a form submit on Android Chrome, else location.replace)
  ► https://web-pay.line.me/web/payment/waitPreLogin?transactionReserveId=<reserveId>
      (the user taps 繼續; the page calls mobile-check, and while the status is RESERVE:)
  ► intent://pay/payment/<reserveId>#Intent;scheme=line;action=android.intent.action.VIEW;
      category=android.intent.category.BROWSABLE;package=jp.naver.line.android;end
  ► line://pay/payment/<reserveId>  →  PayLaunchActivity
```

- The intent comes from the `SCHEME.ANDROID` template and `conf.url.ANDROID.appScheme`
  (`/pay/payment/<reserveId>`) in the inline script of `waitPreLogin`.
- On Chrome 73 and later, `init()` returns early. LINE opens only after the tap on 繼續, not on page
  load.
- LINE gets the same `line://pay/payment/<reserveId>` link as in the merchant-app case. Thus the
  redirect handles the browser path with no change.
- The two web-pay pages show the transaction only for a valid reserve id. A dummy id gives a
  `載入失敗` (load failed) page, so this flow cannot be read without a real transaction.

**Merchants that refuse the standalone app (issue #161, not fixable).** Some merchants fail on a
Standard install. The error is `無法完成付款操作 / 您必須透過LINE應用程式操作此功能，確定要開啟LINE應用程式嗎？`
("You must use the LINE app for this function. Open LINE?") with an `更新` link. The
**standalone LINE Pay app** shows this error, not LINE. A logcat capture and a screen recording
(Samsung Internet, two restaurants on the same ordering system, LINE 26.14.0) show this sequence:

```
browser web-pay page ► 繼續 ► line://pay/... ► LineSchemeServiceActivity ► PayLaunchActivity
  ► AndrewLinePay: "Redirecting com.linecorp.line.pay.base.PayLaunchActivity to the standalone LINE Pay app."
  ► com.linepaytw.upay/.biz.main.SchemeActivity ► MainActivity (web view titled 付款給廠商)
  ► the transaction loads, then the error page shows (Recents labels the card "LINE Pay")
```

- The redirect works: it finds the reserve id and opens the transaction in the standalone app.
- The standalone app refuses the transaction after it loads it. Other merchants work for the same
  user. Thus the refusal comes from a setting of this merchant or its ordering platform.
- Nothing starts LINE again after the error, unless the user taps the prompt.
- A patch cannot correct this. The standalone app sends these payments back to LINE, and LINE
  cannot run its own Pay flow on a re-signed build. The only workaround that we tell users is to
  pay in another way.
- We did not test LINE Pay on a Root Mount install, and we will not test it. A Root Mount install
  keeps the original signature, but the app still runs modified code. We do not know if LINE bans
  an account that pays from a modified app. Do not recommend Root Mount for payments.
- We do not know if the server or a script on the page makes this decision. Neither one is
  patchable from the LINE APK, so we stopped here.

LINE also has Pay web screens that the patch does not hook: `PayWebActivity` and
`PayWebStandaloneActivity` (the internal `linepay://payweb` route, `av3.b.PAY_WEB_APP`, launched from
`e24.r1`), plus the legacy `LaunchActivity` and `WebViewActivity`.
