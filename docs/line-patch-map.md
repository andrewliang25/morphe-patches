# LINE patch map & findings

Reference notes for authoring LINE (`jp.naver.line.android`) patches, distilled from decompiling
**LINE 26.11.0** (the version pinned in `app/andrewliang/patches/shared/Constants.kt`).

> ⚠️ **Obfuscation drift.** Class/method names like `hg1.d`, `az0.q`, `d00.z`, `ne1.y0$c`,
> `fg1.a$b`, `r51.a` are R8-obfuscated and **change between LINE versions**. The *concepts,
> mechanisms, and anchoring strategies* below are durable; the exact descriptors must be
> re-confirmed against the decompiled smali when bumping the target version. Prefer anchors that
> survive obfuscation: Kotlin **enum-constant names** (`CALENDAR`, `GIFT`, …), **string literals**,
> and **resource ids**.

---

## Local build & verify without GitHub Packages credentials

Building normally needs a PAT for `maven.pkg.github.com/MorpheApp/registry`. If the patcher/plugin
artifacts are already in the Gradle cache, you can build and verify **fully offline** — the
`app.morphe.patches` settings plugin only requires the credential values to be *non-null*, not
valid, when nothing is fetched:

```bash
# Compile + build the bundle offline (dummy creds satisfy the non-null credential block)
rm -rf patches/build/libs
./gradlew :patches:buildAndroid --offline --no-daemon -Pgpr.user=dummy -Pgpr.key=dummy

# List patches in the built bundle (confirm name/description/registration)
java -jar work/morphe-desktop-*.jar list-patches --patches patches/build/libs/patches-*.mpp

# Apply ONE patch exclusively against the real APK (fingerprints resolve here, not at build time)
java -jar work/morphe-desktop-*.jar patch \
  -p patches/build/libs/patches-*.mpp \
  --exclusive -e "<patch name>" \
  -o work/out.apk -t "$TMPDIR/scratch" \
  work/apkm-extract/base.apk
```

The apply log prints `Applied: <name>` and `Writing N new classes` — **N is the number of classes
your patch modified**, a fast sanity check (e.g. the Calendar patch touches 5, the attach-tools
patch touches 1).

**Disassembling the result.** The Morphe/apktool jars bundle only the smali *assembler*
(`com.android.tools.smali.smali.Main`) and a proguard-shrunk `baksmali` **library** with no CLI —
so there is no ready `baksmali` command. `apktool d` on the full APK works but is slow. The fast
path: STRIP_FAST (the default) writes every modified class into a fresh small `classes.dex`, so
unzip just that and read it with **dexlib2** (available in the Gradle cache jar
`smali-dexlib2-*.jar`):

```java
// javac -cp smali-dexlib2-*.jar:smali-util-*.jar Dump.java && java -cp .:...:... Dump
var dex = DexBackedDexFile.fromInputStream(Opcodes.getDefault(),
              new BufferedInputStream(new FileInputStream("classes.dex")));
for (ClassDef c : dex.getClasses())
    for (Method m : c.getMethods()) { /* inspect m.getImplementation().getInstructions() */ }
```

Cast instructions to `OneRegisterInstruction` / `TwoRegisterInstruction` /
`NarrowLiteralInstruction` / `ReferenceInstruction` to print registers, literals, and field/method
references.

---

## Chat "+" attach menu

Built by **`gg1.e.r(boolean)`** (`smali_classes9/gg1/e.smali`; jadx `gg1/e.java`), the RecyclerView
adapter for the attach grid (item layout `R.layout.chat_ui_attach_grid_item`). It concatenates two
kinds of items into one `[Lhg1/a;` array, then keeps each only if `((hg1.a) item).f(...)` returns
true:

1. **Static local tiles** — one `hg1.r` subclass each, constructed inline in `gg1.e.r()`.
2. **Server-driven services** — a runtime-fetched list (`r11.d.c()` → `List<r51.a>`); each entry
   becomes one `hg1.d` (the single shared "ChatAppButtonType" class), built in the loop at
   `gg1/e.smali:827`. If the list isn't cached yet it returns `[]` and kicks off an async fetch,
   then re-renders.

### Item gates (`hg1.r` / `hg1.a`)

`hg1.r.f(Lgi1/b;Lfg1/a;Lhg1/a$a;)Z` is the visibility gate. It shows an item only when: the chat
type is in the item's allowed set **AND** `j(Lgi1/b;)Z` (per-type availability) **AND** `k(...)`
**AND** `l(...)` all pass. So **forcing `j()` false hides a static tile**; forcing `f()` false hides
whatever class owns that `f()`.

### Static tiles (LINE 26.11.0)

| Tile (label) | Class | ctor type constant (`Lfg1/a$b;->…`) | `j()` availability |
|---|---|---|---|
| Calendar (`line_calendar_plusmenu_calendar`) | `hg1.b` | `CALENDAR` | `return true` |
| Message scheduler (`chat_plusmenu_button_scheduledmessages`) | `hg1.s` | `SCHEDULED_MESSAGE` | schedule-a-message composer |
| Transfer / LINE Pay (`chathistory_attach_dialog_label_select_linepay`) | `hg1.k` | `PAY` | `contains(dl3.a.PAY)` |
| LINE GIFT (`chathistory_attach_dialog_label_giftshop`) | `hg1.h` | `GIFT` | `contains(dl3.a.GIFT)` |
| Files `hg1.g`, Contact `hg1.f`, Location `hg1.m`, Voice `hg1.t`, Keep `hg1.i`, PayPay `hg1.p`, Live talk `hg1.l`, LINE MUSIC `hg1.n` | — | (their own) | — |

**To hide one static tile** (pattern used by "Hide Transfer button" / "Hide LINE GIFT button", and
the Calendar `+` tile): anchor its ctor via the **unique read of its `fg1.a$b` type constant**
(each constant is read only in that one ctor; the enum's `<clinit>` `sput` is excluded by pinning
the ctor's parameter list), then `mutableClassDefBy(fp.method.definingClass)`, select
`j(Lgi1/b;)Z` by descriptor, and prepend `const/4 p0, 0x0` / `return p0`.

### Server-driven services — Poll, Reservation, Schedule, Ladder shuffle, …

These come from the server (category `e38.a.EnumC2123a.MORE`, mapped `e38.a`→`r51.a` in `r11.e.c()`).
`r51.a` (ChatAppViewData) = `{id, name, iconUrl, url, showNewBadge, availableChatTypes}` — labels,
icons and destinations all come from the server payload, **not** from local resources. (The
`chathistory_attach_dialog_label_poll/schedule/ladder_shuffle/reservation` strings still exist in
`res/` but are **dead** — unreferenced by any smali.)

- **Hide the whole category (stable):** every service is an `hg1.d`, and `hg1.d` is built *only* in
  `gg1.e` — so forcing **`hg1.d.f(Lgi1/b;Lfg1/a;Lhg1/a$a;)Z`** to `return false` drops them all at
  once, with no dependency on the (drifting) server payload. This is what **"Hide attach menu extra
  tools"** does. Anchor: `hg1.d.f` is the only `f(...)Z` that reads `Lr51/a;->f` (its
  `availableChatTypes` set), which uniquely distinguishes it from the sibling `f()` overrides in
  `hg1.a`/`hg1.r`.
- **Hide one service (fragile — avoid):** an individual service can only be identified by its LINE
  service **channel id** (e.g. Schedule/create-event = `"1655112642"` real / `"1651805621"` beta,
  hardcoded in enum `jg1.a$a.SCHEDULE` → `et1.s.g.SCHEDULE`). Channel ids are server-assigned and can
  change, so a single-service patch can't be pinned to an APK version. Prefer the category-level gate.

---

## LINE Calendar vs Events vs Message scheduler — three distinct features

Easy to conflate; they are separate features with separate entry points, gates, and destinations.

**Calendar** (native LINE Calendar; strings `line_calendar_*`; feature gate interface `jp0.d`, impl
`pp0.g`). Five in-messenger entry points, all removed by **"Hide calendar buttons"**:

| Surface | Class / anchor | Hide technique |
|---|---|---|
| Chats-tab header button | `az0.q.CALENDAR` added to list `fb8.b` in `gw1.f.<init>` | remove the `sget CALENDAR` + following `add(...)` pair |
| Chat-room top toolbar | `ed1.d0.a`, `ed1.g1.CALENDAR_BUTTON` (two add-sites) via `ed1.s1.g(...)` | remove both `sget CALENDAR_BUTTON` + `g(...)` pairs |
| `+` attach tile | `hg1.b` (see attach-menu section) | force `j()` false |
| Slide-out chat-menu "Calendar" row | `d00.o` (holds `f11.b`), opens native Calendar Activity via `jp0.g` | force the row's `isVisible` ctor arg (`d00.a.e`) false |
| Message long-press "Calendar" | provider `ne1.y0$c.a(Context,v01.a,j51.a,Z)Lj51/c;` (reads `j51.c.CALENDAR`) | force it to `return null` |

**Events** (chat-menu row, `chatmenu_mainlist_button_events`) — **one** entry point only. A generic
`d00.z` row built in `ChatHistoryMenuFragment` (~the `d00.z.<init>` block using string `0x7f150dfa`
+ icon `0x7f0807ce`), gated by the boolean field `Lyz/s4;->l:Z` (the sole UI read of that field).
Opens a **server-configured web page** (`settings.e$c.D`), not the native calendar. Removed by
**"Hide Events button"** — because `d00.z` is shared by other rows, patch at the build site: replace
the `iget-boolean … s4.l` (matched by `fieldAccess(Lyz/s4;,"l")` + `literal(0x7f150dfa)`) with a
`const 0` into the same register.

**Message scheduler** ("send a message later"; `hg1.s`, strings `chat_scheduledmessages_*`) —
unrelated to Calendar/Events; opens the scheduled-message composer (`yr1.a`/`xr1.b`). Not currently
patched.

### Chats-tab header button set (context for `az0.q`)

The header button row (Chats tab, `com.linecorp.line.chattab.header.ChatTabHeaderStateImpl` =
`gw1.f`) is built from the Kotlin enum **`az0.q`** (constants `AI_FRIEND, ALBUM, CALENDAR, OPEN_CHAT,
PLUS_MENU` — names survive obfuscation). Buttons are `sget-object <az0.q const>` + `add(...)` into a
`ListBuilder` `fb8.b`. A separate green-dot icon `Set` uses `fb8.j` and does **not** include
`CALENDAR`. To hide a header button, remove its `sget`+`add` pair (see "Hide calendar buttons" header
row, and the sibling "Hide community button" which targets `OPEN_CHAT`).

---

## Shipped / proposed patches (this line of work)

| Patch (name) | Package | Targets |
|---|---|---|
| Hide calendar buttons | `line.hidecalendar` | the 5 Calendar surfaces above |
| Hide Events button | `line.hideevents` | the `d00.z` Events chat-menu row |
| Hide Transfer button | `line.hidetransfer` | `hg1.k` (`+` Transfer/LINE Pay tile) |
| Hide LINE GIFT button | `line.hidegift` | `hg1.h` (`+` LINE GIFT tile) |
| Hide attach menu extra tools | `line.hideattachmenutools` | all server-driven `hg1.d` services |
| Redirect LINE Pay | `line.disablepay` | `PayLaunchActivity` / `PayLiffActivity` onCreate (see below) |

Each is an independent, `default = true`, user-facing `bytecodePatch` — one feature (or one feature's
full set of entry points) per patch, matching the bundle's convention (cf. *Hide Wallet tab*,
*Disable VOOM*). All but *Redirect LINE Pay* are fixed-value / instruction-level edits with no
extension.

## LINE Pay intake & the "Redirect LINE Pay" patch

**Why redirect instead of disable:** the messenger can't run its own Pay flow on a re-signed build
(the bundled VKey/V-Guard integrity check fails — see the integrity notes in `CLAUDE.md`). The
patch (still packaged under `line.disablepay`, object `disablePayPatch`) forwards the payment to the
user's **separately-installed standalone LINE Pay app** (unpatched → integrity passes) and then
closes the in-app Pay screen. A failed hand-off degrades to the old "just close" behavior.

### How an external pay URL enters LINE (decompiled 26.11.0)

```
merchant "LINE Pay" link  (line:// or https://line.me/R/…)
  ► jp.naver.line.android.activity.schemeservice.LineSchemeServiceActivity   (EXPORTED router)
  ► v98.d.d(...) dispatcher → pay handlers (gv3.j / on3.k / ru3.f)
  ► iv3.a.b(ctx, ao3.b)  → Intent(PayLaunchActivity, data=line://pay/…)      [not exported]
    iv3.a.c(...) / PayLiffActivity$a.a(...) → Intent(PayLiffActivity, extra "linepay.intent.extra.URI")
```

- **`PayLaunchActivity`** (`Lcom/linecorp/line/pay/base/PayLaunchActivity;`) — general front door; its
  URL is `getIntent().getDataString()` (a `line://pay/…` scheme form).
- **`PayLiffActivity`** (`Lcom/linecorp/line/pay/impl/liff/common/PayLiffActivity;`) — the LIFF/web
  path for the `waitPreLogin` / `lpUsage=STANDALONE` web-payment flow. Reads the incoming `Uri` from
  intent extra **`linepay.intent.extra.URI`** (field `f73569l`) and calls LINE's own resolver
  **`l5().r7(uri)`** (obfuscated `sv3.n`) to produce the real `https://web-pay.line.me/…` URL right
  before loading it in a WebView.
- `web-pay.line.me` / `web-tw-pay.line.me` / `/R/iab` are **not** string literals in the APK or
  manifest — those hosts are server-config. So an `ACTION_VIEW` for `https://web-tw-pay.line.me/R/iab?…`
  fired from inside LINE is **not** caught by the messenger; it auto-resolves to the standalone app.

### The redirect

Both Pay activities are intercepted at `onCreate`, right after `super.onCreate` (same anchor the old
disable patch used: `PayLaunchActivityOnCreateFingerprint` / `PayLiffActivityOnCreateFingerprint`,
`methodCall("onCreate")` = the super call). Injected: `invoke-static {p0}, …LinePayRedirect;->redirect`
then `finish(); return-void`. The extension
(`extensions/.../app/andrewliang/extension/LinePayRedirect.java`) reads the intent (extra
`linepay.intent.extra.URI`, else `getDataString()`) and builds the standalone url:

- **`…/pay/payment/<reserveId>`** deep link (the merchant checkout case) — the last path segment IS
  the `transactionReserveId` (**device-confirmed**: it decodes identically to the reserve id in the
  known-good web-pay url). Rebuilds
  `https://web-pay.line.me/web/payment/waitPreLogin?transactionReserveId=<reserveId>&locale=zh-TW_LP`.
- an already-resolved `web-pay.line.me` url — used as-is.
- anything else (e.g. `line://pay/main`) — no reserve id → **no redirect**, the activity just
  `finish()`es (a loop guard: never wrap a link that would round-trip back to the messenger).

then fires

```
https://web-tw-pay.line.me/R/iab?url=<urlencoded inner web-pay url>
```

with `FLAG_ACTIVITY_NEW_TASK`, swallowing all exceptions so `finish()` always runs. A token-free
breadcrumb is logged under logtag **`AndrewLinePay`** (the single-use reserve id is deliberately not
logged).

**Device-confirmed path (LINE 26.11.0):** tapping a merchant "LINE Pay" button
(`http://line.me/R/pay/payment/<reserveId>`) enters LINE and reaches **`PayLaunchActivity`** with
`getDataString() == line://pay/payment/<reserveId>` (not `PayLiffActivity`; extra was null). The
reconstruction above opened the standalone LINE Pay app on the transaction. The `PayLiffActivity`
hook is retained as defensive coverage for the LIFF/web (`lpUsage=STANDALONE`) route — if a future
LINE version routes there instead and the raw intent lacks a usable web url, reuse LINE's `r7()`
resolver (anchor a fingerprint on the stable `"lpUsage"` / `"STANDALONE"` literals in
`PayLiffActivity`, read the obfuscated `l5()`/`r7()` descriptors from the matches — don't hardcode
`sv3.n`, which drifts).
