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
| Keep unsent messages | `line.keepunsent` | `g38.b0.invoke` — the unsend DB write (see below) |

Each is an independent, `default = true`, user-facing `bytecodePatch` — one feature (or one feature's
full set of entry points) per patch, matching the bundle's convention (cf. *Hide Wallet tab*,
*Disable VOOM*). Most are fixed-value / instruction-level edits; *Redirect LINE Pay* and *Keep
unsent messages* carry extension code.

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

---

## Message unsend (receive side) & the "Keep unsent messages" patch

### How an incoming unsend reaches the database

```
OpType NOTIFIED_DESTROY_MESSAGE(65) / DESTROY_MESSAGE(64)   (Lcb8/ce;, Operation = Lcb8/de;)
  ► e98.c1.b(...)  (someone else unsent)   /   e98.r  (3-line subclass: your own unsend)
  ► the g38.b0 lambda, run inside a chat_history transaction
```

Both ops funnel through the **same** lambda, so one patch site covers your own unsends too.

**LINE does not delete the row for 1:1/group chats.** `Lg38/b0;->invoke(Ljava/lang/Object;)Ljava/lang/Object;`
(`smali_classes4/g38/b0.smali`) rewrites `chat_history.type` to an `i38.c.UNSENT*` variant and NULLs
`content`, `parameter`, `attachement_type` and the location columns via `h38.h0` →
`Lh38/b;->g(SQLiteDatabase, Li38/k;, Lh38/h0;)I`, then drops the message from the full-text-search
index and deletes its `reactions` / `multiple_image_message_mapping` rows. All of it sits behind one
guard:

```smali
    :cond_0
    iget-object v6, v10, Li38/b;->g:Li38/c;   # v10 = the fetched row
    iget-wide  v7, v10, Li38/b;->b:J          # message id (live at the guard)
    invoke-virtual {v6}, Li38/c;->h()Z        # already an unsend tombstone?
    move-result v6
    if-eqz v6, :cond_1                        # no  -> destructive block
    goto/16 :goto_c                           # yes -> skip, return the row unchanged
```

Forcing that register non-zero makes the unsend a **local no-op**. `Lg38/f3;` (the lambda's
parameter, `v1`) carries the transaction's `SQLiteDatabase` in field `b`.

**OpenChat/Square is a different path and genuinely deletes**: `SquareEventType.NOTIFIED_DESTROY_MESSAGE(5)`
→ `fp5.i` → `Lg38/q0;->m(Ljava/lang/String;Ljava/util/Set;)V` → `g38.f3.c(Set)` →
`DELETE FROM chat_history WHERE id IN(...)`. Not covered by the patch.

A third path exists for messages unsent while offline: full sync / message-box restore reads
`z58.b.c.KEY_UNSENT_MESSAGE` / `KEY_SILENTLY_UNSENT` from `contentMetadata` (`g38.q0`, `g38.x2`) and
stores the row already stripped. Nothing local to keep there.

### How the placeholder is rendered

`chat_history.type` → content model → text, four hops:

| Hop | Descriptor |
|---|---|
| cursor → content model | `Lh38/t;->e(Lcb8/q7;Ljp/naver/line/android/util/j;Lz58/b;)Li38/g;` — `UNSENT` builds `Li38/g$s$h0;` from `from_mid` |
| content → UI model | `Lm11/b;->k(Li38/g$s;)Ll11/h;` → `Ll11/h$h0;` |
| UI model → text | `Lcl1/c;->a(Landroid/content/Context;Ll11/h;Lo21/a;)Ljava/lang/CharSequence;` |
| bubble decoration | `Lwi1/j4;->K0(...)` — appends the "How to unsend discreetly" link on *your own* unsends (suppressed by *Hide premium unsend upsells*) |

Strings: `chathistory_message_format_unsent_receiver` (`0x7f150d65`, "%1$s unsent a message.") and
`chathistory_message_format_unsent_sender` (`0x7f150d66`, "You unsent a message.") — chosen by
comparing `from_mid` against your own mid.

`Lh38/x;` (query builder) filters `UNSENT_SILENT` out of chat history entirely
(`type NOT IN (...)`), which is how LYP "unsend discreetly" hides a row it still stores.

### What the patch does

Skips the guard, then inserts its **own** `type = UNSENT` row so the notice still appears — see
`app/andrewliang/extension/KeepUnsentMessages.java`. Keeping the original row untouched (rather than
copying it and letting LINE tombstone the original) preserves its real `server_id`, so reply-jump,
forwarding and reactions keep working on the kept message.

The guard is located **by instruction shape** (no-arg `Z` call → `move-result` → `if-eqz` → `goto`),
and the `SQLiteDatabase` field reference is read out of the method's own bytecode — `i38.c`, its
`h()`, and `g38.f3.b` are all obfuscated and drift. The two register reads are anchored rather than
scanned blind: the message id must be a field of the same row object the guard reads its receiver
off, and the `SQLiteDatabase` holder register must carry a `check-cast` to the field's own owner
before the guard (this method has a *second* `SQLiteDatabase` read, `h38.t0.a`, that reuses the same
register `v1` for a different type). Anything unresolvable — including a register spilling past
`v15`, where `iget`/`invoke` operands stop fitting — throws rather than applying a half-patch.

The insert is skipped when the row is **already** a tombstone (`type IN (27, 28, 38)` = what
`i38.c.h()` covers). The injection sits ahead of the branch it flips, so it also runs where LINE's
guard would have exited early — a redelivered unsend for a message tombstoned before the patch was
installed, or one stripped by the offline `KEY_UNSENT_MESSAGE` path, which never reaches the guard
at all. Both would otherwise draw the notice twice.

### Values that drift on a version bump

| Thing | 26.11.0 |
|---|---|
| `chat_history` table (`a68.a`) | only `id` constrained (PK + autoincrement); all other columns nullable; `IDX_SERVER_ID` is **non-unique** |
| sort columns | `IDX_CHAT_ID_ID_CREATED_TIME` = `chat_id` (eq) + `created_time`, `id` (sort) → ordering follows `created_time` |
| `created_time` | `DATE_STRING` → a **TEXT** column of epoch millis, so `+1` needs a `CAST` round-trip |
| `i38.c` db values | `MESSAGE` = 1, `UNSENT` = 27, `UNSENT_NO_MARK` = 28, `SQUARE_UNSENT_MESSAGE` = 35, `UNSENT_SILENT` = 38 — the extension hardcodes 27 (the type it writes) and 27/28/38 (`i38.c.h()`'s set, the rows it refuses to annotate) |
| `cb8.q7.NONE` | 0 (`attachement_type`) |
| chat-list unread badge | `chat.message_count - chat.read_message_count` (`c23.d` columns, read in `z13.o`) — a stored counter pair, **not** a `count(*)` over `chat_history`, so the inserted placeholder cannot move it |

---

## Outbound photo pipeline (reference — no patch ships for this)

> **A "Send original photos without the quality drop" patch was built, device-confirmed working, and
> then deliberately dropped.** It is recorded here so nobody re-derives it from scratch. It reached a
> 126.5 MP / 34 MB photo arriving at 24 MP instead of 1.64 MP, via five sites across two send paths
> plus an extension. It was dropped because:
>
> - **Coverage can't match the promise.** It fixed the chatroom `+` / photo-strip flow only. Album is
>   unfixable without inflating every album upload (no "Original" button, see below), share-to-LINE is
>   a third path that was never traced, and a user cannot tell which entry point they used or inspect
>   the result. A patch that silently applies to some sends and not others invites bug reports.
> - **Maintenance is a re-investigation, not a fingerprint refresh.** Four fingerprints, one anchored
>   on a synthetic Kotlin lambda (`th1.t$c$b`) that reshuffles whenever its enclosing method changes.
>   Worse, four device rounds were spent on sites that resolved and disassembled perfectly while
>   sitting on a path that never executed — so each LINE bump needs a device send with an oversized
>   photo, not just a clean build.
> - **The trigger is rare.** A 12 MP phone JPEG is 3–6 MB; crossing 20 MB needs a 50–200 MP high-res
>   mode, and the 100 MP gate needs pixel-shift.
>
> If it is ever revived: the minimal form is **`th1.t$c$b`'s two literals alone** — one fingerprint,
> two `replaceInstruction` calls, no extension. With the flag preserved, an oversized JPEG takes
> LINE's stock raw byte copy at *full* resolution, losslessly, which is better output than the 24 MP
> q80 re-encode the extension produced. The extension only existed to avoid putting ~34 MB on the
> wire.

### The two paths a photo can take

`u13.c1.f(dVar, fVar, uri, rotation)` writes the local file that gets uploaded. Exactly two
outcomes exist for a photo — there is no tier in between, which is the defect.

| `cw0.f` | What `c1.f` does |
|---|---|
| `IMAGE_STANDARD` | `c1.p()` only — resample to the tier's **pixel budget** and JPEG re-encode |
| `IMAGE_ORIGINAL` | `c1.p()` *and* the `u13.y0` lambda, which raw-copies or full-res re-encodes |

`d98.m1` then uploads **one** of them (`IMAGE_ORIGINAL` when the message metadata carries
`IS_SEND_ORIGINAL_IMAGE`, else `IMAGE_STANDARD`) plus a thumbnail. OBS derives the rest by URL
path (`w78.b`): `…/r/talk/m/<id>` standard, `…/m/<id>/original`, `…/m/<id>/preview`.

### The pixel budget is a *total*, not a per-side cap

`c1.k(STANDARD_IMAGE)` returns `maxDimension²` **pixels**. `c1.m()` returns the bitmap untouched
when `width*height <= budget`, so raising `maxDimension` past a photo's pixel count disables
resampling entirely. `c1.l()` decodes to within `budget * 4` first (`mul-int/lit8 … 0x4`) and then
scales precisely — that multiplier is what avoids coarse power-of-two subsampling, so lowering it
costs quality for any source between 1x and 4x the budget.

Values come from `jp.naver.line.android.util.f1.a()` — non-obfuscated, and the best anchor into
this whole area:

| Tier | Class | Server key | Default | Selected when |
|---|---|---|---|---|
| Normal | `t88.a$b$b` | `function.media.image_medium` | 1280 / q70 → **1.64 MP** | `RESIZE_IMAGE_OPTION != 2` (incl. unset) |
| High | `t88.a$b$a` | `function.media.image_high` | 2048 / q80 → **4.19 MP** | `RESIZE_IMAGE_OPTION == 2` |

`p38.a.c` is `NORMAL(0) / SMALL(1) / LARGE(2)`, but `f1` tests for exactly `2`, so `SMALL` is dead
for uploads (it only suffixes download URLs in `r78.h`). The tiers are **separate classes**, which
is what lets a patch touch one without the other. Four consumers: `dw0.c` (chat), `gg3.j`,
`ch0.j`, `od7.d`.

### The cliff

`t73.k0.b0()` stamps `rt7.c.isOriginal` (field `B`) per item as the picker finalises. With the
"Original" toggle on it clears the flag at `>= 20 MB` (`0x1400000`) or `>= 100 MP` (`0x5f5e100`),
dropping the photo onto the standard path. One byte under the threshold a photo is copied
verbatim; one byte over it loses 6–25x its pixels, with the toggle still showing on. The only hint
is `gallery_original_guide_error` — *"Videos and some photos may be sent in standard resolution."*

Nothing validates the encoded output: `c1.o()` is a single-shot `Bitmap.compress` with no size
check, and `b0()` / `m63.n0.d()` test the **source**'s length and dimensions, never the result.

The `u13.y0` lambda branches on mime (`ww0.c.a`): `image/jpeg|png|gif|bmp` (and an unresolvable
type) → raw byte copy, then `i48.a.b` strips 38 GPS/timestamp EXIF tags (Orientation survives);
anything else (HEIC, WEBP) → full-res decode + `Matrix` rotate + JPEG at the tier quality.

### The `>= 20 MB / >= 100 MP` test exists in FIVE places

This is the single most important fact about this area, and it cost four device rounds to learn. The
same pair of literals (`0x1400000`, `0x5f5e100`) is duplicated across two independent send paths
plus Album, and **any one of them alone is enough to drop the photo to the standard variant**:

| Copy | Decides | On the chatroom path? |
|---|---|---|
| `th1.t$c$b.invoke` | `Function1<w51.c, Boolean>`; `un1.f.a` calls it per item and feeds the result into `un1.k$b$c(Uri, isOriginal)` → `IS_SEND_ORIGINAL_IMAGE` | **yes — this is the one that decides** |
| `m63.n0.f()` / `d()` | media-picker toggle availability → `m63.n0.i(Z)` → `u53.e.a` | no |
| `t73.k0.b0` | media-picker per-item `rt7.c.B` stamp | no |
| `wi0.h.g` | LINE **Album**'s own compressor (`ri0.c.c`, `com.linecorp.line.album.transfer.*`) | no |
| `th1.u.invokeSuspend` | guide text only (`ORIGINAL_IMAGE_GUIDE` vs `ERROR_GUIDE`, via `th1.c0`/`d0`) | cosmetic |

Two send paths reach `u13.c1.f`, and they do **not** share the decision:

- **Chatroom `+` / photo strip** — `th1.t.h(Z)` reads the toggle (`t.f()` = the toggle view's
  `isSelected()`), launches `th1.t$c`, and passes `th1.t$c$b` to `un1.f.a`. `th1.a0` builds fresh
  `rt7.c` items with `B = t.f()`. **The media picker is never involved.**
- **Full gallery picker** — `m63.n0` + `t73.k0.b0`.

`wi0.h.g` is deliberately left alone: Album has **no "Original" button**, so its `IMAGE_ORIGINAL`
branch is unreachable and the only remaining lever there would be the always-compress branch, which
would inflate every album upload. Album's config comes from `ch0.j.m()` →
`ei0.n(maxDimension², quality, 8192|1280)` off the *same* `f1.a()` tier as chat, so if an Original
button ever appears there, the fix is to swap that config for a bounded one rather than to neutralise
the gate.

### What the dropped patch did (for reference)

Five sites. Site A was the one device-confirmed to decide the outcome; Sites 0 and 1 covered the
gallery-picker path and were never device-verified.

- **A.** `th1.t$c$b` — both gate literals → `0x7fffffff`, so the per-item predicate is always true.
- **0.** `m63.n0` — both literals in *every method holding both* (`f()` and `d()`). `onClick`'s lone
  `0x1400000` is a free-disk-space multiplier (`getFreeSpace() >= 20 MB * itemCount`) — never touch
  it, which is why the rule keys on "contains both" rather than on the size literal.
- **1.** `t73.k0.b0` — both gate literals → `0x7fffffff`, so `isOriginal` stays true. Rewriting the
  compared *value* rather than the branch keeps control flow byte-identical.
- **2.** `u13.y0` head — call an extension that re-encodes; `null` falls through to stock code. The
  fall-through target **must** be an `ExternalLabel`, not a label written inside the injected block.
- **3.** `u13.y0` — the tier-quality `iget` feeding `c1.o` → `const/16 0x50` (q80).

The extension re-derived the same `>= 20 MB || >= 100 MP` test the sites removed and returned
`null` otherwise, so every case that already worked stayed untouched — notably a 50 MP / 10 MB
JPEG, which must keep being copied byte for byte. Output was bounded to 24 MP via `inSampleSize` plus
`inScaled`/`inDensity`/`inTargetDensity`; the density scaler matters because `inSampleSize` alone
would quantise a 126 MP source to ~7.9 MP. Rotation was baked into the pixels rather than written
as an EXIF tag, matching what every other LINE encoder on this path does.

**Rotation must come from LINE, not from EXIF.** `c1.f(dVar, fVar, uri, Integer rotation)` hands
the *same* `Integer` to `c1.p` (the standard variant, and so the thumbnail and OBS's derived
`/preview`) and to the `y0` lambda. Both prefer it and only fall back to the file's EXIF
(`c1.d(uri)`, itself an `ExifInterface` "Orientation" read) when it is `null`. So the patch passed
the lambda's `Ljava/lang/Integer;` capture (`y0.c`) into the extension with EXIF as fallback
only — deriving from EXIF unconditionally would leave the original sideways relative to the
standard variant whenever the caller supplied a rotation the file itself does not carry.

Sites 2 and 3 resolved `u13.c1`, its `Context` field and the lambda's captures (`c1`, `Uri`,
`Integer` rotation) **from the matched method's own bytecode**; only the two literals and framework
types were hardcoded.

### Values that drift on a version bump

| Thing | 26.11.0 |
|---|---|
| gates in `t73.k0.b0` | `0x1400000` (20 MB), `0x5f5e100` (100 MP) — also in `k0.X(Z)V` and `m63.n0.d()`, so pin the `(Ljava/util/ArrayList;)V` signature |
| lambda anchor | `u13.y0` — the only class combining `ContentResolver.getType`, `MimeTypeMap.getMimeTypeFromExtension`, `BitmapFactory.decodeStream` and `Matrix.setRotate` |
| encoder | `c1.o(I, Bitmap, File)Z`; quality read from `dw0.b$b.b:I` |
| decode multiplier | `c1.l()` → `mul-int/lit8 … 0x4` (left alone; it is tier-agnostic) |

---

## Call ringtone pipeline (investigated, deliberately not shipped)

Freeing the incoming-call ringtone from LINE's four bundled tones / paid Melody Shop tones is
**technically patchable** — the whole decision is client-side and the playback layer already accepts
an arbitrary `Uri`. Nothing shipped; see [Why nothing shipped](#why-nothing-shipped) for the reason,
which is product value, not feasibility. Recorded so this doesn't get re-derived from scratch.

### The choke point

`be7.c.a(Landroid/content/Context;Lb97/c;)Lbe7/a$a;` — `public static`, `smali_classes3/be7/c.smali` —
resolves the ringtone for **both** incoming call types, called from `c97/a.java:119` (free call) and
`c97/k.java:93` (OA call). It returns one of two shapes:

| Shape | Meaning |
|---|---|
| `be7.a$a$a(ue7.a tone, be7.q fallbackFrom)` | a bundled `res/raw` tone |
| **`be7.a$a$b(be7.q, android.net.Uri)`** | **an arbitrary URI** |

The method's own MELODY branch already builds the second from a `file://` path, so URI playback is a
shipping path, not a theoretical one.

### Playback — LINE's own MediaPlayer, in LINE's own process

`hg7.j.a()` puts the resolved tone in the RING slot (`oy.d.f261846b`) → Andromeda
`com.linecorp.andromeda.audio.s` / `t` (which unwraps the `ce7.c` wrapper back to a plain `xx.b`) →
**`xx.c.b()`**, which switches on the URI scheme:

| Scheme | How it is opened |
|---|---|
| `android.resource` | `setDataSource(context, uri)` |
| `file` | `FileInputStream(uri.getPath()).getFD()` |
| **anything else** | `setDataSource(context, uri)` — so `content://` resolves via LINE's own `ContentResolver` |

then `setAudioAttributes(USAGE_NOTIFICATION_RINGTONE)`, `setLooping(true)`, `prepare()`.

This is why it is patchable at all: the URI is opened **inside LINE's process** and never handed to
the system NotificationManager, so none of the cross-process read-permission problems that make the
Google sign-in limitation unfixable apply here. (Message *notification* sounds are a different
system entirely — ordinary Android notification channels, already customisable from system Settings
with no patch.)

### What actually limits users today

`be7.r$a.a()` picks the tone provider through **two sequential gates**. First a server-pushed config
int, `k97.m.m()` → `vb7.c.m()` → `t().G0().l().e()`:

| `m()` | Provider |
|---|---|
| `0`, or anything other than `1`/`2` | `DEFAULT` |
| `1` | `EMBEDDED` |
| `2` | fall through to the region switch below |

Only when it is `2` does region matter (`s87.i.a()`), and the premium branches additionally require
`a.b()` — all three of `k97.m.s()` / `.f()` / `.r()` non-empty, i.e. the premium-service URLs are
configured:

| Region | Provider | Ringtone setting visible? |
|---|---|---|
| JP | `MUSIC` | yes |
| TH | `FRIEND_MELODY` if `b()`, else `EMBEDDED` | only for `FRIEND_MELODY` |
| TW | `MELODY` if `b()`, else `EMBEDDED` | only for `MELODY` |
| everywhere else | `DEFAULT` | **no** |

Only MUSIC / MELODY / FRIEND_MELODY set `exposeExternalSetting = true`, which is what makes the
"Ringtones & ringback tones" entry appear. `DEFAULT` hardcodes `ue7.a.RING_1`; `EMBEDDED` resolves
through `ge7.c.e(m87.h.RING)`, which reads the *legacy* melody prefs and falls back to `RING_1` when
they are empty. **So outside JP/TH/TW there is no ringtone choice in the UI at all** — not even among
the four bundled tones, which are reachable only under JP's `MUSIC` provider (it alone reads the
`cf7.m` prefs). The config int is a server-supplied *value* consumed locally, not a server-made
*decision* — same shape as the photo tier.

Two per-call overrides sit upstream of the local choice: the **caller's** friend-melody (`m87.e` on
`VoIPFreeCallIncomingConnectInfo`) preempts it, and a per-call boolean
(`VoIPFreeCallIncomingConnectInfo.t` / `VoIPOaCallIncomingConnectInfo.v`) demotes any URI-based tone
back to `RING_1`. Head-injecting `be7.c.a` sits ahead of all of this.

### Tone inventory and storage

`ue7.a` is the bundled-tone enum; ids take the form `android.resource:///<id>`.

| Constant | `res/raw` | Title |
|---|---|---|
| `RING_1` | `original` | Xylophone (`settings_ringtone_default1`) |
| `RING_2` | `melody` | Spring |
| `RING_3` | `voice` | LINE |
| `RING_4` | `ring` | Telephone ring |
| `RINGBACK_1` | `lineapp_ringback_16k` | — |

Two parallel storage systems, both **encrypted** and therefore not readable from an extension:

- **Current** — `jp.naver.voip.ringtone` via `cf7.m` (keys `ringToneUri`, `ringToneName`,
  `ringToneResourceTypeId`, `ringToneDecodedUriFlag`; accessors `cf7.p.a` / `cf7.p.b`). Built by
  `gz6.a.b`, which is `EncryptedSharedPreferences` (androidx.security `kd.b`, Android-keystore
  backed). Read only by the `MUSIC` provider.
- **Legacy LINE MELODY** — `com.linecorp.voip.melody` via `qe7.a` (keys `ringtone_path`,
  `ringtone_title`, `ringtone_oid`, …), synced from the Talk server by `ge7.c.g()`
  (`getRingToneOnTalkServer`), which **resets to default and deletes the local files** when the
  server reports no tone.

**File-playback precedent.** The LINE MUSIC path plays a plain file out of LINE's *private internal*
dir — `cf7.k.b()` → `<applicationInfo.dataDir>/ringtone/decoded_ringtone_1|2` (`j48.h.d()` is
`applicationInfo.dataDir`, not external storage). A file placed in LINE's private storage therefore
plays with **zero runtime permissions**. Purchased-track entitlement is re-verified against the
server every 14 days (`af7.g`, pref `ringToneMusicVerifyLastTime`).

### If it is ever built

Head-inject `be7.c.a` and return `be7.a$a$b(q, uri)` early when the extension supplies a URI, else
fall through. Fingerprint on anchors obfuscation cannot touch:

```kotlin
accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC)
filters = listOf(
    instanceOf("Lcom/linecorp/voip2/access/connect/VoIPFreeCallIncomingConnectInfo;"),
    instanceOf("Lcom/linecorp/voip2/access/connect/VoIPOaCallIncomingConnectInfo;"),
    methodCall(definingClass = "Landroid/net/Uri;", name = "parse"),
)
```

Uniqueness verified against 26.11.0: only three methods in the APK carry **both** `instance-of`s —
`be7.c.a` (the target), `be7.q$d.e` (excluded by `STATIC`; it is `public final`) and
`c97.m.a(Landroid/content/Context;Lb97/c;)Z` (excluded by `Uri.parse`). Filters are in program order:
both `instance-of`s precede the MELODY branch's `Uri.parse`.

`be7/a$a$b` and `be7/q` must be read from the matched method's **own** MELODY branch rather than
hardcoded — the idiom the dropped photo patch used for `u13.c1` and the `y0` captures above: find
the `INVOKE_DIRECT` whose `name == "<init>"` and whose
`parameterTypes` end in `Landroid/net/Uri;` → its `definingClass` is `a$a$b` and `parameterTypes[0]`
is `q`; then the `SGET_OBJECT` whose field type is that `q` gives a valid enum constant. The method
is static with `.locals 5` and two params (registers v0–v6, `p0` = v5), so `invoke-direct` /
`new-instance` stay inside the 4-bit operand limit, and v0–v2 are dead on entry.

**Any custom URI must be probed before use.** LINE declares `READ_MEDIA_IMAGES`,
`READ_MEDIA_VIDEO`, `READ_MEDIA_VISUAL_USER_SELECTED` and legacy `READ_EXTERNAL_STORAGE`, but
**not `READ_MEDIA_AUDIO`** — so a user-added MediaStore ringtone throws `SecurityException` on open,
`xx.c` swallows it in its `catch` and calls `c()`, and **the call rings silently**. Probe with
`ContentResolver.openAssetFileDescriptor(uri, "r")` and return `null` on failure so LINE keeps its
own tone. Stock OEM ringtones (`content://media/internal/audio/…`,
`content://settings/system/ringtone`) open with no permission.

### Why nothing shipped

- **Follow the device ringtone** (`RingtoneManager.getActualDefaultRingtoneUri`) is one small patch,
  but it is a feature nobody asked for, and it silently ignores the ringtone of anyone whose phone
  tone is a file they imported themselves — see the `READ_MEDIA_AUDIO` note above.
- **A real in-app picker** needs a new Activity, an `AndroidManifest.xml` patch, SAF plus a copy into
  LINE's private dir, and a settings-row injection into obfuscated declarative Kotlin (`l15.c`'s
  `px4.b0` item list) whose natural host rows only exist in JP/TH/TW. Disproportionate to the payoff.

### Values that drift on a version bump

| Thing | 26.11.0 |
|---|---|
| resolver | `be7.c.a`; result types `be7.a$a$a` / `be7.a$a$b` |
| provider / region gate | `be7.q` (provider), `be7.r` (+ `exposeExternalSetting`), config via `k97.m.m()` |
| bundled tones | `ue7.a`; kinds `ue7.b` (RING / RING_BACK), sources `ue7.c` (BASIC / MUSIC) |
| prefs & files | `cf7.m` / `cf7.p` / `cf7.k`; legacy `qe7.a`; server sync `ge7.c` |
| player | `xx.c.b()`; sources `xx.b` / `xx.g`; call param `oy.d`; adapter `hg7.j` |
| **stable anchors** | `com.linecorp.voip2.access.connect.VoIPFreeCallIncomingConnectInfo` / `…VoIPOaCallIncomingConnectInfo` — **not** obfuscated |

---

## Dead ends (investigated, not patchable)

**Extend the unsend window.** The client windows (`j51.a.o` free, `.p` premium) are UX
pre-filters fed by server config (`function.chatroom.message.unsend.timelimit`,
`.premium.timelimit`). `unsendMessage` carries only `(seq, messageId)`; the server decides and has
a dedicated `TalkException` code `MESSAGE_NOT_DESTRUCTIBLE(71)` (`cb8.m9`), handled at `ne1.o2` /
`ne1.b2` → *"You can't unsend this message as too much time has passed."* Widening the client
window only re-shows the menu item and produces that toast. (`hidepremiumunsend` deliberately
narrows it for the same reason.)

**Remove video length / size limits.** `c81.b.c()` rejects `> 301000 ms` and `> 209715200` bytes,
and the picker sets `maxVideoDurationSec = 300` at every chat entry point. Both are trivial to
remove, but the OBS gateway enforces its own ceiling: `rc1.b` parses the
`x-line-obs-talk-exception` response header carrying `EXCEED_FILE_MAX_SIZE` / `EXCEED_DAILY_QUOTA`
/ `NOT_SUPPORT_SEND_FILE`. Removing the client checks trades a clean local toast for a mid-upload
server rejection.

**Change the ringback tone your friends hear.** The *callee's* ringback is delivered to the
**caller's** client in their connect info and played there, so no local edit can change what a friend
hears when they call you — same class as the unsend window. (The ringback *you* hear while dialing
out is client-side, `oy.d.f261845a` / `be7.a$b`, but changing that helps nobody.) The incoming-call
**ringtone** is a different matter and is patchable — see
[Call ringtone pipeline](#call-ringtone-pipeline-investigated-deliberately-not-shipped).

**Note on the OBS size ceiling.** It has never been observed directly — 20 MB is inferred from
LINE's own client-side threshold. Sub-20 MB originals are known to upload byte-identical, so that
much is safe; record the real limit here if a device test ever surfaces `EXCEED_FILE_MAX_SIZE`.
