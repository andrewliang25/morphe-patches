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
| Send original photos without the quality drop | `line.originalphoto` | `t73.k0.b0` + the `u13.y0` original-file writer (see below) |

Each is an independent, `default = true`, user-facing `bytecodePatch` — one feature (or one feature's
full set of entry points) per patch, matching the bundle's convention (cf. *Hide Wallet tab*,
*Disable VOOM*). Most are fixed-value / instruction-level edits; *Redirect LINE Pay* and
*Send original photos* carry extension code.

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

## Outbound photo pipeline & the "Send original photos" patch

### The two paths a photo can take

`u13.c1.f(dVar, fVar, uri, rotation)` writes the local file that gets uploaded. Exactly two
outcomes exist for a photo — there is no tier in between, which is the defect the patch fixes.

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

### What the patch does

Three sites, all confined to the original path or the fallback decision:

1. `t73.k0.b0` — both gate literals → `0x7fffffff`, so `isOriginal` stays true. Rewriting the
   compared *value* rather than the branch keeps control flow byte-identical.
2. `u13.y0` head — call `OriginalPhoto.writeBounded`; `null` falls through to stock code.
3. `u13.y0` — the tier-quality `iget` feeding `c1.o` → `const/16 0x50` (q80).

The extension re-derives the same `>= 20 MB || >= 100 MP` test the patch removed and returns
`null` otherwise, so every case that works today is untouched — notably a 50 MP / 10 MB JPEG,
which must keep being copied byte for byte. Output is bounded to 24 MP via `inSampleSize` plus
`inScaled`/`inDensity`/`inTargetDensity`; the density scaler matters because `inSampleSize` alone
would quantise a 108 MP source to 6.75 MP. Rotation is baked into the pixels rather than written
as an EXIF tag, matching what every other LINE encoder on this path does.

**Rotation must come from LINE, not from EXIF.** `c1.f(dVar, fVar, uri, Integer rotation)` hands
the *same* `Integer` to `c1.p` (the standard variant, and so the thumbnail and OBS's derived
`/preview`) and to the `y0` lambda. Both prefer it and only fall back to the file's EXIF
(`c1.d(uri)`, itself an `ExifInterface` "Orientation" read) when it is `null`. So the patch passes
the lambda's `Ljava/lang/Integer;` capture (`y0.c`) into the extension and EXIF is the fallback
only — deriving from EXIF unconditionally would leave the original sideways relative to the
standard variant whenever the caller supplied a rotation the file itself does not carry.

Sites 2 and 3 resolve `u13.c1`, its `Context` field and the lambda's captures (`c1`, `Uri`,
`Integer` rotation) **from the matched method's own bytecode**; only the two literals and framework
types are hardcoded.

### Values that drift on a version bump

| Thing | 26.11.0 |
|---|---|
| gates in `t73.k0.b0` | `0x1400000` (20 MB), `0x5f5e100` (100 MP) — also in `k0.X(Z)V` and `m63.n0.d()`, so pin the `(Ljava/util/ArrayList;)V` signature |
| lambda anchor | `u13.y0` — the only class combining `ContentResolver.getType`, `MimeTypeMap.getMimeTypeFromExtension`, `BitmapFactory.decodeStream` and `Matrix.setRotate` |
| encoder | `c1.o(I, Bitmap, File)Z`; quality read from `dw0.b$b.b:I` |
| decode multiplier | `c1.l()` → `mul-int/lit8 … 0x4` (left alone; it is tier-agnostic) |

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

**Note on the OBS size ceiling.** It has never been observed directly — 20 MB is inferred from
LINE's own client-side threshold. Sub-20 MB originals are known to upload byte-identical, so that
much is safe; record the real limit here if a device test ever surfaces `EXCEED_FILE_MAX_SIZE`.
