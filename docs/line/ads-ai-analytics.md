# LINE ads, AI and analytics

Ads, AI entry points, analytics and traffic interception. See [README.md](README.md) for the other LINE docs.

## Analytics & the "[General] Block analytics" patch

LINE logs usage events (screens, taps, page views) through one shared logger. About 970 classes call
its interface `mk8.c` (26.17.1), for example `p(jk8.c)`. They get the logger from the shared reference
`ek8.u.b` (`AtomicReference<mk8.c>`) through `ek8.u.x()`. The SDK sends to
`uts-front.line-apps.com` (UTS).

- The reference starts with `mk8.i`, a logger that drops every event.
- The startup task `"TrackingManagerTask"` (`xp8.d.f()` on 26.17.1, `ho8.d.f()` on 26.16.0) builds
  the UTS client `ek8.z` and the real logger `mk8.h`, then puts it in with `compareAndSet(mk8.i, h)`.
  Nothing else creates `mk8.h` or reads `ek8.z`.
- The patch returns at the start of that task while the switch `block_analytics` is on. The
  reference keeps `mk8.i`, so every event is dropped.

The task runs only when the process starts, so the switch has a "Close LINE" row, not "Restart
LINE". The Restart row starts a new task in the same process.

Device result on 26.17.1, with a logging build that logged the creation of `mk8.h` and each
`mk8.h.p` call: with the switch on, `mk8.h` was not created and no event was logged. With the switch
off and a new process, `mk8.h` was created and logged 3 `jk8.c$g` events in 2 seconds.

Not covered: the crash reporters (Sentry, `io.sentry`, and Firebase Crashlytics) are separate SDKs.
They send crash reports, not usage events. `com.linecorp.trackingservice` is a 1-class native adapter
for LINE GAME (`line-game-growthy-android`).

---

## Ads — bundled SDKs, display coverage, and the parts not patched

Audited on LINE 26.11.0, 2026-08-24. Issue #69 showed that a Taiwan account does not render
every ad surface, so this audit asks one question. **Does the APK hold an ad that no patch
covers?** The display answer is no. The parts that stay are below, with the reason for each.

### Bundled ad and tracking SDKs

| SDK | Package | State in the app |
|---|---|---|
| Google Mobile Ads (AdMob / AdManager) | `com.google.android.gms.ads` (`smali_classes6`, `smali_classes7`) | Starts by itself through the `MobileAdsInitProvider` content provider (`initOrder=100`), before LINE code runs. App id `ca-app-pub-6968037135561499~1985034154` |
| LINE Ads SDK v1 (`Lad*`) | `com.linecorp.line.ladsdk` | Inventories `smartch`, `openchat`, `timeline` |
| LINE Ads SDK v2 (`Lyad*`, LINE Yahoo Ads) | `com.linecorp.line.ladsdk.ui.v2` | The newer tier of the same SDK |
| Yahoo Japan A-Cookie | `jp.co.yahoo.android.ads.acookie` | An ad identifier. Starts at every app launch in every region — see below |
| Yahoo edgeAI | `jp.co.yahoo.android.edgeai` (122 classes) | Downloads and deploys on-device models (`LoadModelWorker`, CoreML, model endpoints) |

### Display coverage is complete, and here is the proof

Do not repeat this sweep from memory. Three checks together show that every ad **view** goes
through a class that `hideadviews` hooks:

- The Google ad view bases have four subclasses in the APK: `et5/b` gives `et5/c` and `et5/d`,
  and `dg3/a` gives `dg3/b` and `dg3/c`. `et5/e` and `et5/f` extend `FrameLayout` directly.
  Those six are exactly the six that the patch covers.
- No other `View` subclass in the app names `AdManagerAdView` or `AdView`. The other classes
  that name them (`al5/d`, `c93/g`, `et5/a`, `et5/g`, `dg3/h`, `gl5/*`, `jl5/d`) are loaders,
  view models, or coroutine lambdas, and not views.
- Inside `ladsdk`, `LadAdView` and `LyadAdView` are the only lifecycle containers. Every other
  class there (`LadPostHeaderView`, `LadSlotAssetRecyclerView`, the Lights views, and more) is
  an asset view that lives inside one of the two containers.

### Server-gated ad surfaces

About 80 `function.*` keys gate an ad placement. The server sends these values per account, so
a region cannot be read from the APK. The list matters because a Taiwan account renders only
some of them:

| Surface | Key prefix | Render path |
|---|---|---|
| Home tab Google banner | `function.hometab.ad_rc.*`, `function.hometab.ads.displayrate*` | Google view, covered |
| Chats tab Google banner | `function.chattab.ad_rc.*` | Google view, covered |
| Smart Channel | `function.chattab.smartch.*` | `[Ad] Hide ad views` and `[Ad] Remove banner ads` |
| Album ads | `function.album.ad.*`, `function.moa.album.ad.*` | LAD inventory, covered |
| Note ads | `function.note.ad.{list,end}_inventory_key` | LAD inventory, covered |
| OpenChat header ads (4 places) | `function.square.{chatroom,note,thread_space,your_threads}.header_ad.*` | LAD or Google, covered |
| OpenChat in-stream ads | `function.square.chatroom.integration_ad.*` | **not confirmed** |
| Official Account talkroom ads | `function.official_account.talkroom_ad.*` | **not confirmed** |
| Sticker keyboard promo banners | `function.sticker_keyboard.banner.*` | not covered, and not an ad SDK surface |

### Investigated and deliberately not patched

Each item below is patchable. None of them ships, because the gain is invisible and the risk is
real. **Do not add these to `hideadviews`.** That patch is default-on and device-confirmed, and
one more anchor that drifts makes the whole patch fail. A drifted anchor thus breaks a visible
patch to protect something that no user can see.

**The Yahoo A-Cookie startup task.** `s68/a` is `ACookieTask`, a `LineInitializationTask` that
`o68/i` registers. The body of `f()` is one call to `YJACookieLibrary.init(context)`, with no
region check and no config gate. `jb0/d` (tagged `"LAD-SDK"`) is a consumer.

A `return-void` stops it. But `init` is not a flag. It resolves `wq7.a` to `tq7.a` to `rq7.j`,
calls `j.d(context)`, moves a `"COOKIES"` SharedPreferences entry, and registers an
`Application` lifecycle callback. Without it, `getValue()` and `getValueWithName()` read an
`rq7.j` whose `SharedPreferences` field is null. That is the shape of the `hidepremium` crash:
a value that was never null becomes null, and a consumer with no null guard fails. The
consumers here are LAD SDK code. The gain is one identifier less, while LEGY, Firebase,
Crashlytics, Sentry and the LINE analytics stay.

**The AdMob content provider.** A manifest patch can remove `MobileAdsInitProvider`. Google
Mobile Ads also starts on first use, thus the patch delays the start and does not stop it. The
ad loaders (`al5/d`, `c93/g`, `gl5/*`, `et5/a`) still run after the views are hidden. If they
call an SDK that did not start, the patch adds a crash path and hides nothing new.

**Yahoo edgeAI.** A bytecode patch does not make the APK smaller. If the models never load, a
patch does nothing. If they do load, we do not know what reads them. There is no case either
way.

### Open, and waiting for a user report

Two display surfaces do not resolve to `LadAdView`, to `LyadAdView`, or to one of the six
Google wrappers. A Taiwan account renders neither, thus a device report must come first:

- `function.square.chatroom.integration_ad.*` puts ads between the messages of an OpenChat
  room. The key `embed_ad_interval_minutes` sets the interval.
- `function.official_account.talkroom_ad.*` puts ads in an Official Account chat room. The
  table `OfficialAccountTalkroomAdData` holds the data, and `w28` holds the schema. The
  renderer is not identified.

**Plan a patch when a user reports one of these, and not before.** The audit above is the
starting point, so no one needs to sweep the APK again.

---

## AI entry points & the "Hide Agent i buttons" / "Hide AI Friends button" patches

Three buttons open LINE's AI features. Each button has a boolean gate. Two of the gates are server
flags in the config classes `g45.c` and `g45.i2`, and each of these flags has a one-field accessor.

| Button | Where | Gate (26.14.0) | Readers |
|---|---|---|---|
| Agent i | Home header, before the services grid | `wm2.j.a`, then label `access_agenti` (0x7f15006c) | `wm2.j` only |
| Agent i / AI entry | end of the search bar (`main_tab_ai_entry_icon_container`) | `function.search.line_ai_entry.enabled` → `g45.i2.b()` | `m25.i` constructor, `qz1.f.x()` |
| AI Friends | Chats tab header | `function.ai.character.display_button` → `g45.c.t()` | `qz1.f` constructor, `qz1.g`, `qy1.j0` (twice) |

**One parse pattern finds both accessors.** The parser `g45.e` reads each flag as
`const-string "<key>"`, then the accessor for its default value, then `h45.a.a(key, map, default)`.
R8 renames the accessor but keeps the key. `line/shared/ServerFlag.kt` matches the key and the
`()Z` call right after it. The patches then make that accessor return false: "Hide Agent i buttons"
and "Hide AI Friends button" pass its value through their switch before the return. Every reader then sees the value
that LINE gets when the server turns the flag off.

**Why the Home header is hidden at the view, not at a flag.** The Home header value comes from
`b3.hb.b()` → `g45.c.z()`, the master Agent i flag. The APK reads it 24 times, for example in the
AI talk suggestions settings. A false value there changes much more than one button. Thus the patch
passes only the `iget-boolean wm2.j.a` in the header lambda through `hideAgentI(Z)Z`. The register
is `p1`, which can be above v15, so the call is `invoke-static/range`. The `if-eqz` that tests it
follows the read directly, with no label between them, in all three versions.

**AI Friends and Album share one slot.** The header builder in `qz1.f` adds `AI_FRIEND` if `t()`
is true, and `ALBUM` if it is false. `qy1.j0` reads `t()` too, for the Album button and its green
dot. The patch turns off the flag, not the list entry, so the Album button comes back and all
readers agree.

**The search bar hides its own layout.** If `i2.b()` is false, the `m25.i` constructor sets the
container to GONE, moves the guideline back and attaches no click listener. This is the same state
as an account without the feature.

**Verification.** None of the three buttons shows on the test account (Taiwan). Thus a hidden
button proves nothing. A local probe forced the three sites to true. The AI Friends button then
replaced Album, and the AI button showed at the end of the Home and Chats search bars. The real
patch gives back the stock layout, with no crash. The probe cannot show the `wm2.j` site, because
this account has the older Home header (bookmark, bell, add friend, Settings). Only disassembly
confirms that site.

### Agent i in chats (issue #208)

Settings → Agent i (JP) or the AI icon visibility screen (other regions) offers "Show" and "Hide
temporarily". All regions share the one screen (`go0.q`). Only the description string differs:
`line_aiiconvisibility_desc_hidetemporarilyagenti` when `fo0.s.a()` is true (Agent i branding),
else `line_aiiconvisibility_desc_hidetemporarily`. A TW account can thus test it.

The choice is local. No server call is made. One key of the general key-value store,
`CHATROOM_AI_BUTTON_SHOWN_AFTER_MILLIS` (type long), holds the time until which Agent i stays
hidden (26.16.0 names):

| Role | Method | What it does |
|---|---|---|
| Writer | `r21.b.b(do0.a, …)` | "Hide" writes `LocalDate.now().plusDays(30)` at midnight. "Show" writes 0. Both the Settings screen and the toggle in the chat (`zm1.g`) call it. |
| Reader (chat) | `s81.c.v()` | `AiTalkChipsViewModel.r()`: hidden while `clock.millis() <= v()` |
| Reader (Settings) | `r21.b.a()` | `fo0.y`: "Hide" is selected while `currentTimeMillis() < a()` |
| Reader (Labs) | `vh8.g0$a.a(…)` | Turning on the "Scheduling agent" lab shows `LabsAiTalkChipVisibilityPopupFragment` while hidden |

In 26.17.1 the three readers are one. `x21.a` hands a function reference,
`getAiButtonShownAfterMillis()J` (`x21.a$a.invoke()`), to a shared controller. That reference reads
the key and returns it boxed with `Long.valueOf`. The chat (`e91.d`), the Settings screen (`y21.a`,
`kj8.c`) and the Labs popup (`mj8.h0`) all read the time through it.

**The patch.** Both getters (`s81.c.v()`, `r21.b.a()`) are the same four instructions: `sget-object`
of the key, `invoke-static` of the long getter of the store, `move-result-wide`, `return-wide`. The
store class is `dao/d` in 26.14.0 and `dao/c` later, so the patch does not anchor on it. The patch
finds them by this shape and the key name (R8 keeps enum constant names). While the switch "Hide
Agent i" is on, they return `Long.MAX_VALUE`. On 26.17.1 the patch writes `Long.MAX_VALUE` into the
read value of the boxed reader before `Long.valueOf`. That one site also covers the Labs popup. The result register is written only by the
`move-result-wide`, so the switch value goes into it at entry. With the switch off, LINE's own
"Show" and "Hide temporarily" work again. It fails unless it finds exactly two getters or exactly one
boxed reader. Both readers only compare the value with the
current time, so the large value is safe. The writer is left alone: what it stores no longer matters.

**Side effects.** The Settings screen always shows "Hide temporarily". Tapping "Show" writes 0, the
radio stays on "Hide", and LINE still shows its "will be shown" toast. The Labs reader reads the key
directly and is left alone, so its popup shows only for a hide that was saved before patching.
Changing the "30 days" strings would need a resource patch for every language. It is not done.

**Verification (26.16.0, TW account, 2026-11-01).** The TW test account has no entry to the screen
(no "LINE AI services" row), and the activity `AiSubscriptionDisplayAiButtonActivity` is not
exported. A local probe made the "Andrew's Patch Setting" row start that activity by class name.
The screen then drew as "Show AI icon in chat". "Hide temporarily" was selected on an account that
never picked it. Tapping "Show" showed the toast `Set to "Show"`, but the check stayed on "Hide
temporarily", also after the screen was opened again. No crash and no `VerifyError`. The chat
reader (`s81.c.v()`) cannot be observed on this account, because it shows no AI chips. Only
disassembly confirms that site.

### LINE AI image editing & the "[General] Hide LINE AI image editing" patch

LINE AI image editing ("AI Edit") has no gate of its own at each button. Every entry reads one
server list, `function.ai.lineai.enabled_entry_points`, and shows only if its name is in the list.
The getter of the list is `g45.c.d()` (26.14.0), `ta5.d.e()` (26.15.1) and `ya5.d.e()` (26.16.0).

| Entry name | Surface | Reader (26.16.0) |
|---|---|---|
| `chatroom_context_menu` | "AI Edit" in the message long-press menu | `fm1.e1$k` → `kq1.a.a` |
| `chatroom_side_menu` | LINE AI button next to an image message (26.15.1 and later) | `lq1.i` → `kq1.a.a` |
| `chatroom_image_viewer` | LINE AI button in the header of the chat photo viewer | `ChatVisualEndPageViewModel$a` |
| `album_image_viewer` | Album photo viewer | `qo0.n` |
| `note_post_image_viewer` | Note photo viewer (`NoteMediaViewerActivity`) | `nr3.p` |
| `myprofile_photo_endpage` | Your own profile photo viewer | `eg8.r` |

`kq1.a.a(messageType, flag, source)` is the chat gate. It is true only for the types `SINGLE`,
`ROOM`, `GROUP` and `MEMO`, so OpenChat (`SQUARE_GROUP`) never shows the chat entries. The chat
photo viewer has the same type check. The names `aitalk_ask_ai` and `aitalk_ai_search` are entry
sources of the AI talk suggestions, and no reader looks for them in this list.

**The patch.** The parser reads the key, then `Map.get`, then the getter for the default value. The
fingerprint matches that order. The patch refuses a getter that is not a plain `iget-object` +
`return-object`. The getter has `.locals 0`, so the list it read goes through
`LineSettings.hideLineAiImageEdit(List)List` before the return. While the switch "Hide LINE AI
image editing" is on, that call gives `Collections.emptyList()`. Every reader then sees the state
of an account where the server turns on no entries. While the switch is off, the call gives the
same list back. The normal photo editor
(`chat_gallery_image_edit_button`, "Edit photo") and the text scan button are not in the list, so
they stay.

**Verification (26.16.0, TW account, 2026-11-14).** The server list on this account is empty, so
no entry shows there, with or without the patch. A local probe filled the list. Then the side
button (robot icon above Share), "AI Edit" in the long-press menu and the viewer header button
(`chat_gallery_line_ai_button`) showed on a Keep Memo image. The probe logged `kq1.a.a` with
`type=MEMO … result=true` for `CONTEXT_MENU` and `IMAGE_MESSAGE_SIDE_MENU`, and the viewer read
the list from `ChatVisualEndPageViewModel$a`. A second test build filled the list field in the
constructor of the configuration class and also applied the patch. All three buttons were then
gone, while "Edit photo" and the text scan button stayed. No crash and no `VerifyError`. The album, Note and profile photo
viewers could not be opened on this account (no album or Note in Keep Memo, no profile photo).
Only disassembly confirms those three.

### More Agent i entries in the "[General] Hide Agent i buttons" patch

Besides the Home header, the search bar and Agent i in chats, the patch covers three more entries.
uta-a/linimal (`docs/analysis/agent-i-entry-points.md`) listed them first. All six sites read the
one switch "Hide Agent i".

| Entry | Where | Site (26.16.0) | What the patch does |
|---|---|---|---|
| Agent i button | Wallet tab header, newer design only | `WalletV3GrandDesignHeaderView.o()`: builds the button state with `onAgentIButtonClick` | Returns at entry. With no state, the header draws no button. |
| "Agent i" (JP) or "LINE AI services" | Settings list | two rows in `eh5/t2.<clinit>` with the key of `eh5/a.AiSubscription` | The list getter drops the rows with that key. |
| "Show Agent i in chat" switch | top of the + menu of a chat (`chat_ui_attach_menu_ai_talk_chip_switch`) | `sm1/f1.invoke()`: one boolean sets the view VISIBLE or GONE, then decides whether to build its content | The boolean goes through the switch right after the `check-cast` of the view. |

**Wallet header.** `wallet/impl/v3/a` calls `o()` only if the Wallet uses the newer header
(`c().G()`) and the server gives an Agent i URL. `o()` builds nothing but the Agent i state. The
class name and the string `onAgentIButtonClick()V` are kept by R8 in all three versions.

**Settings rows.** Both rows share the key `AiSubscription`. A predicate picks one of them by the
Agent i branding (`fo0.s.a()`). The row key is a `String` field of the row base class (`ec5/k0.a`
in 26.16.0). `andrewHideAgentIRows` reads the key the same way the builder does (the enum
constant, then its key accessor) and calls `LineSettings.hideAgentI(List, String)`. The
extension drops each row that has a `String` field equal to the key. "Andrew's Patch Setting"
hooks the same getter. Both patches find it with `settingsListGetter`, which accepts the getter
with or without the call of the other patch, so the order of the two patches does not matter.

**+ menu switch.** LINE shows this switch when Agent i in chat is available and Agent i is hidden
in this chat. The chat site of the patch keeps Agent i hidden in every chat, so without this site the
switch shows in every chat and does nothing. The flag register is the one that the `if-eqz` after
`setVisibility` tests (v2, v5 and v3 in the three versions).

**Not covered.** The chat menu item "Manage account" (`lineai_assistant_title_lineai`, `d60/l`)
shows only in the LINE AI chat itself (`ChatHistoryMenuFragment` compares the chat id with a
server value) and opens the settings of that chat. The Commerce tab header icon
(`commercetab_header_agent_i`) has no traced click path. Neither is an entry from elsewhere.

**Verification of the switch (26.16.0, TW account, 2026-11-14).** The account shows none of these
entries. A local probe forced the search bar flag, the "LINE AI services" row predicate
(`eh5/t2$m0`) and the + menu flag to true. With the switch on, the search bar had no AI button,
Settings had no "LINE AI services" row and the + menu of Keep Memo had no "Show Agent i in chat".
With the switch off and LINE restarted, all three showed. No crash and no `VerifyError`. Three
sites were not observed:

- The Wallet site: the account uses the older Wallet header, so `o()` never runs. Forcing
  `c().G()` switches the whole Wallet tab to the other design.
- The Home header site: the account has the older Home header (see above).
- The hidden-until getters: the "LINE AI services" row opens nothing on this account, and the
  screen activity is not exported. The unconditional form of this site was device-confirmed
  earlier (see "Agent i in chats").

Only disassembly confirms the switch at these three sites.

---

## Traffic interception & the "[General] Trust user-installed CAs" patch

Issue #182. The goal is to let a developer read LINE's own HTTPS traffic through their own
debugging proxy on their own device. LINE has three independent networking surfaces, and only one
is patchable.

### The three surfaces

1. **Platform TLS stack** — `HttpsURLConnection`, OkHttp, and the LIFF / mini-app WebViews. This
   stack obeys the app's Network Security Config (NSC) at `res/xml/network_security_config.xml`
   (resource id `0x7f190020`, wired by `android:networkSecurityConfig` in the manifest).
   **Patchable.**
2. **LEGY native transport** — LINE's core messaging. `liblegy.so` runs over a privately bundled
   TLS stack, `libssl3line.so` + `libcrypto3line.so` (a renamed OpenSSL/BoringSSL build). A bundled
   TLS stack does not read the OS NSC and carries its own roots, so the NSC patch does nothing for
   it. **Not patchable from resources or bytecode** (same class as LINE Pay / VKey — it needs a
   native-library change, out of scope).
3. **LINE Pay** — pinned in the NSC (`app-payatom.line-apps.com`) and also guarded by the VKey
   V-Guard native engine. Out of scope.

### Why the platform stack is blocked, and what unblocks it

Two facts in the NSC decide it:

- **Trust.** `<base-config>` is `cleartextTrafficPermitted="true"` with **no `<trust-anchors>`**.
  This build is minSdk 30 / targetSdk 36, so on Android 7+ the default applies and a user-installed
  proxy CA is rejected for every domain.
- **Pinning.** `<pin-set>` blocks exist **only** on the bank hosts (`*.linebk.com`,
  `*.kasikornbank.com`, `cwa.linebank.com.tw`) and Pay (`app-payatom.line-apps.com`). The LIFF and
  mini-app hosts (`liff.line.me`, `miniapp.line.me`) sit in a `domain-config` with **no pin-set**.

So for LIFF the only block is the missing trust. There is no OkHttp `CertificatePinner` in the
bytecode (0 refs) — all app-level pinning lives in the NSC. The six `onReceivedSslError` handlers
do not matter: once the CA is trusted, the chain validates, no SSL error fires, those handlers never
run.

### What the patch does

`patches/line/allowdebugproxy/AllowDebugProxyPatch.kt` is a single `resourcePatch`. It appends one
`<trust-anchors>` block to `<base-config>` with `src="system"` and `src="user"`. Nothing else.

- `liff.line.me` / `miniapp.line.me` have no `<trust-anchors>` of their own, so they **inherit**
  base-config's — mini-apps are covered with no edit to their block.
- Every `<pin-set>` is left byte-identical, so bank and Pay stay pinned. A pin is checked after the
  chain is trusted, so a proxy CA still fails the pin. Confirmed by diffing the patched NSC against
  `work/decompiled-line-26.14.0/apktool/res/xml/network_security_config.xml`: the only change is the
  new `<trust-anchors>` block.
- `default = false`, because the patch widens trust for all non-pinned platform traffic. It is an
  install-time choice, not a runtime toggle: an NSC edit injects no bytecode, so "Andrew's Patch
  Setting" (which gates on an injected `LineSettings->x()Z` call) cannot switch it.

### Scope note

The patch intentionally covers only the platform stack. The real chat traffic rides LEGY over the
bundled native TLS stack and stays invisible to a proxy. Do not try to remove the bank/Pay pins and
do not attempt the native stack.

### Values that drift on a version bump

| What | 26.14.0 | How to re-find it |
|---|---|---|
| NSC resource | `res/xml/network_security_config.xml` | `android:networkSecurityConfig` in the manifest; the one `<base-config>` is the target. |
| LIFF / mini-app hosts | `liff.line.me`, `miniapp.line.me` | In a `domain-config` with no `<pin-set>`; they inherit base-config trust. |
| Bundled native TLS | `libssl3line.so`, `libcrypto3line.so`, `liblegy.so` | In `split_config.arm64_v8a.apk`. The reason native traffic is not patchable. |
