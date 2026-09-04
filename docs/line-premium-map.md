# LINE premium (LYP) gating map & findings

Reference notes on how **LINE Yahoo Premium (LYP)** feature-gating works in LINE
(`jp.naver.line.android`), distilled from decompiling **LINE 26.14.0** (the version pinned in
`app/andrewliang/patches/shared/Constants.kt`). Companion to `docs/line-patch-map.md`.

> Descriptors were remapped from 26.11.0 to 26.14.0 on 2026-09-03. Device-confirmation notes record
> the version each behaviour was *tested* on; the descriptors around them are 26.14.0.

> ⚠️ **Obfuscation drift.** Names like `z73.k`, `x73.b`, `q83.i/k/n/b/q` are R8-obfuscated and
> **change between LINE versions**. Re-confirm every descriptor on a version bump. Prefer anchors
> obfuscation cannot touch: **name-kept classes** (`PremiumStateBatchedSyncWorker`),
> Kotlin **enum-constant names** (`FEATURE_UNAVAILABLE`), framework types
> (`Ljava/lang/Boolean;`, `Lkotlin/coroutines/Continuation;`), Thrift op-name literals.

---

## The two questions, answered

1. **Is there a client-side premium flag the app reads to unlock features?** **Yes** — a central LYP
   facade, but a *rich per-feature model*, not one global boolean.

2. **Does the server double-check premium status?** **Yes.** The status the client reads is
   **server-authored** (synced from a Thrift RPC), and every premium *action* round-trips to an endpoint
   that re-verifies entitlement independently. The client status is a read-model, not the gate.

**Net:** a blanket "unlock premium" is not achievable. Flipping the client gate can at most surface UI or
enable purely-local behavior. Server-delivered or authorized content stays enforced.

---

## The LYP facade

One obfuscated component, reached everywhere via the component accessor `x73.b.Sc`:

- **Interface** `x73.b` (jadx `jadx/sources/x73/b.java`).
- **Impl** `z73.k` = `com.linecorp.line.lyppremium.impl.LypPremiumFacadeImpl`
  (jadx `jadx/sources/z73/k.java`, smali `apktool/smali/z73/k.smali`).

**Core premium predicate** (verbatim in `u()`, `h()`): the user is premium iff
current `LypUserStatus` **is** `Subscribed` (`instance-of Lq83/i$b;`) **AND** provider `l() == LYP`
(`Lq83/q;->LYP`) **AND** `productTier` is non-empty (`((Lq83/i$b;)status).f().length() > 0`).

### Accessor map (all on `z73.k` / `x73.b`)

| Accessor | Returns | Role | Caller count |
|---|---|---|---|
| `u(Feature, Continuation)` | boxed `Boolean` | per-feature boolean gate | ~18 (for example `SUBPROFILE`, `MESSAGE_EDIT`, backup/gallery/migration) |
| `s(Feature, Continuation)` | `q83.k` | per-feature status object (`k.a()Z` = "is restricted/not offered") | dominant path for `APP_ICON`, `FONT` |
| `A(Feature, Continuation)` | `q83.n` | per-feature params object | for example `APP_ICON`, `FONT` |
| `o()` | `q83.i` (`LypUserStatus`) | synchronous raw status read; callers do their own `instanceof i$b` | 38 |
| `a(Continuation)` | `q83.i` | suspend raw status read | 33 |
| `q()` | `StateFlow<q83.i>` | reactive status | — |
| `l()` | `q83.q` | subscription provider (`LYP`, …) | — |
| `h()` | `Boolean` | "is LITE plan" (`productTier == "LITE_ENJOY"`) | — |
| `z()` | `Boolean` | premium module/feature enabled flag | — |

`u()` = `subscribed(above) && !s(feature).a()`. `s`/`u`/`A` share the erased descriptor
`(Lq83/b;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;`; only `u()` boxes a `Boolean` (the class's
sole `Ljava/lang/Boolean;->valueOf` call).

### Feature enum `q83.b`

Values (matched by `.name()`): `AI_TALK_SUGGESTION, ALBUM, APP_ICON, CALL_STT, FONT,
FRIENDS_MANAGEMENT, LINE_AI, MANGA, MESSAGE_BACKUP, MESSAGE_EDIT, MESSAGE_SCHEDULING, NETFLIX,
PREMIUM_BLOCK, PREMIUM_MUTE_MESSAGE, PREMIUM_UNSEND, RING_TONE, STICKER_PREMIUM_BASIC, SUBPROFILE,
YJ_SERVICES`. The accessor varies per feature: `SUBPROFILE`/`MESSAGE_EDIT` → `u()`;
`APP_ICON`/`FONT`/`RING_TONE` → mostly `s()`/`A()`.

### `LypUserStatus` (`q83.i`, `com.linecorp.line.lyppremium.model.LypUserStatus`)

Sealed type. Subtypes (anchored by `toString`): `i$b` = **`Subscribed(subscriptionType=,
productTier=, …, cancelledProviders=, incentive=)`**; `i$a` = `NotSubscribed(isFreeTrialUsed=, …)`;
`i$d` = `Unavailable`. `i$b.f()` = productTier. Serialization enum `q83.x` maps status → raw strings
`SUBSCRIBED("true")`, `SUBSCRIBED_CANCELED("true_cancelled")`, `UNSUBSCRIBED("false")`,
`UNSUBSCRIBED_IN_RETENTION("false_retention")`, `UNAVAILABLE("unknown")`.

---

## Server enforcement (why the client flag is a read-model)

- **Status originates server-side:** Thrift client
  `com.linecorp.line.lyppremium.impl.network.LinePremiumStatusServiceClient` calls op
  `"getLinePremiumStatus"` → `GetPremiumStatusResponse`, whose field **`active`** (Thrift id 1) is the
  entitlement. `LypUserStatusRepository` re-syncs revision-by-revision (`syncAllBatched`,
  `buildRevisionDrivenFlow`), so the `Subscribed`/`active` the client reads is not a local decision.
- **Every premium action re-checks server-side:**
  - Sticker Premium → `"downloadStickerPackage"`, `"getPurchasedProducts"`,
    `"getProductValidationScheme"` (content bytes are on the server, not the device).
  - Themes → `"getProductV2"` (`ThemeProductRepositoryImpl.getThemeProductWithSuspend`); the client
    `ThemeDetailViewData.isPremiumTheme` flag only toggles a badge's visibility.
  - Purchases → `"reserveSubscriptionPurchase"` / `"reserveSubscriptionChange"` +
    Google Play Billing receipt (`com.android.billingclient`, `acknowledgePurchase`, `purchaseToken`);
    entitlement is confirmed through the server op, not the local billing result.

*(Distinct product — do not conflate: `com/linecorp/line/premium/backup/**` "Premium Backup" is
device-migration chat backup, with clean non-obfuscated names and its own `getIsPremiumActive()`;
it is not the LYP subscription.)*

---

## Stable anchors vs. drift

Anchor on these (survive obfuscation): the string `"LITE_ENJOY"` (globally unique, in `h()`);
`Ljava/lang/Boolean;->valueOf`; `Lkotlin/coroutines/Continuation;`; Thrift op-name literals
(`"getLinePremiumStatus"`, `"reserveSubscriptionPurchase"`, `"downloadStickerPackage"`,
`"getProductValidationScheme"`, `"getProductV2"`); Thrift field/toString literals (`"active"`,
`Subscribed(subscriptionType=`, `NotSubscribed(isFreeTrialUsed=`); enum raw strings
(`"true_cancelled"`, `"false_retention"`, `"line_premium"`, `"line_premium_global"`); and the
non-obfuscated class refs inside `z73.k` (`LypPremiumSubscriptionActivity`,
`PremiumStateBatchedSyncWorker`). **Never** anchor on `b13`/`z03`/`t13`-style names — they drift.

---

## Unlocking is not viable (tested)

Forcing the client gate `z73.k.u(Feature) -> Boolean` to `true` was built and **tested on device — it
unlocks nothing**, confirming the analysis. That experimental patch was **dropped**. Why it cannot work:

- Features gated through the object-returning `s()`/`A()` accessors (`APP_ICON`, `FONT`) or read straight
  off raw status via `o()`/`a()` are never reached by a `u()` flip, and forcing `s()`/`A()` to a boolean
  would `ClassCastException` their callers.
- Anything the server delivers or authorizes — sticker/theme downloads, purchases, cloud-backup
  retention, message scheduling — stays enforced regardless of any client flag.

So the practical direction is **hiding** premium, not unlocking it.

---

## Disabling premium (this bundle: `Disable LINE Premium`)

Because premium cannot be unlocked, `Disable LINE Premium` **hides every premium surface** — upsell
popups/banners, badges/locks, the "LINE Premium" settings page and its entry rows, the subscribe/manage
flows — by forcing LINE's own market-availability flag off.

**Master lever:** `a83.a.d()Z` (`return a().W()`, that is `g45.i1.W()`) — the config bit meaning "LYP
premium is available in this market". The facade reads it three ways that **all cascade from `d()`**:

| Facade read | Derivation | Effect when `d()` = false | Consumers |
|---|---|---|---|
| `z()` | `return H().d()` | `false` ("premium enabled" off) | 35 entry-point gates hide |
| `l()` | `return H().b()`; `a83.a.b()` returns `UNAVAILABLE` when `!d()` | provider = `UNAVAILABLE` | 53 region switches take the handled UNAVAILABLE/hide branch |
| `o()`/`a()` | status mapper `g83.m`: `if (!d()) return i$d` | status = `Unavailable` | 10 `instanceof i$d` sites hide |

So one edit — force `a83.a.d()` to `return false` — reproduces the state the app ships to non-premium
markets *for everything that reads the facade*: `UNAVAILABLE`/`Unavailable` are explicitly-handled hide
branches everywhere, no entry point has an unguarded `check-cast` to `i$b`/`i$a`, and there is no
retry-until-available loop.

That audit covered the facade's **enum** branches but not its **nullable** returns, nor the sibling
server flags that ship enabled alongside `d()`. One of each combined into a crash — see
[Second lever](#second-lever-the-premium-backup-flag-required).

**Premium-scoped:** `a83.a.d()` has only 4 direct callers, all in the premium module. The shared
underlying `g45.i1.W()` is read directly by 6 non-premium features (profile, and more) — those are
**not** touched (the patch neuters `a83.a.d()`, not `i1.W()`).

**Anchoring (nothing obfuscated hardcoded):** match the facade's premium-state sync method
`z73.k.z(PremiumStateBatchedSyncWorker$b)Object`, then take the **2nd `invoke-virtual`** of that
same method — `D()La83/a;` then `La83/a;->d()Z` — as the `MethodReference` for the gate, and
neuter it at apply time. Verified on 26.14.0: only `a83.a.d()` is rewritten (to
`const/4 v0,0x0` / `return v0`). The facade is untouched.

Two anchors, both R8-proof: `com.linecorp.line.lyppremium.impl.worker.PremiumStateBatchedSyncWorker`
is **name-kept** (WorkManager instantiates it reflectively) and `FEATURE_UNAVAILABLE` is a Kotlin
**enum-constant name**. Only two methods in the APK take that parameter type — this one and the
facade interface's abstract declaration, which has no body and so cannot satisfy an instruction
filter.

> **Why this anchor changed in 26.14.0.** The old anchor was the unique string `"LITE_ENJOY"` inside
> a `()Z` accessor `h()`, from which the patch reached `z()` — "uniquely the parameterless `()Z`
> with exactly two `invoke-virtual`s". 26.14.0 **deleted `h()`** (the interface now declares only
> `x()`/`y()`) and moved the `LITE_ENJOY` predicate into a suspend evaluator
> `f83.a.a(Continuation)`, so the old fingerprint matched **zero** methods. Reading the gate off
> the matched method also retires the `()Z`-shape heuristic entirely, and the new anchor matches
> 26.11.0 as well. Lesson: a string literal is only a durable anchor while it stays in a method
> whose *signature* you also depend on.

**Not covered / follow-up:** a secondary market bit `g45.m2…a().U().O()` gates a couple of surfaces
(album promo, app-icon seasonal) independently of `a83.a.d()`. If any premium surface survives on
device, neuter that bit too and record it here. Re-verify all descriptors when bumping the pinned
LINE version.

### Second lever: the premium-backup flag (required)

**Symptom:** on a patched build, **Settings ▸ Chats** threw and bounced back to Home. Stock is fine;
reproduced on both Standard (re-signed) and Root Mount, that is signing-independent. Caused by
`Disable LINE Premium` alone.

**Chain** (all verified against decompiled 26.14.0):

| # | Site | Effect |
|---|---|---|
| 1 | `z73/k.java:1007` — `z() { return H().d(); }` | `false` |
| 2 | `z73/k.java:375` — `E() { if (!z()) return null; … }` | **returns `null`** |
| 3 | `wk4/a.java` — `b() { return lypPremiumFacade.E(); }` (`wk4.a implements xj4.b`) | `null` |
| 4 | `f2/j.java` case 0 — `xj4.b.b()?.f307436c` (the row's badge-icon provider) | `null` |
| 5 | `com/linecorp/line/settings/chats/a.java:1758` — the one `m55.c1` (text **+ badge**) row, `R.string.line_premiumbackup_title_chathistorybackup` | row is bound |
| 6 | `r55/c3.java:66` — `setImageDrawable(getDrawable(numInvoke != null ? numInvoke.intValue() : 0))` | **`getDrawable(0)` → `Resources$NotFoundException: Resource ID #0x0`** |

Thrown synchronously while the RecyclerView binds the first screenful, so the whole fragment dies.

**Why the row was still visible.** Its gate is `nj4.d.j()` → `bk4/k0.java:949` → `bk4/z.java` →
`m2.a().i0().g()` — a **separate server-pushed premium-backup config**, not derived from
`a83.a.d()`. Stock LINE only enables it where LYP is available, so `E()` is never null while the row
shows. Forcing only `d()` false produced a pairing LINE never ships.

**Why only this screen.** `r55/c3.java:66` is the *only* settings view holder with the `: 0` fallback;
every sibling null-guards (`r55/a0.java:73`, `r55/d0.java:89`, `r55/c0.java:123,167,281` all use
`if (num != null) … getDrawable(num.intValue())`). The other two `m55.c1` badge rows are fine: Settings ▸
Friends by inspection (provider `p05.b` returns the constant `R.drawable.lyp_premium_label`), and
Settings ▸ Albums **by device test** — its provider `::providePremiumBadgeResId`
(`settings/albums/a.java:237`) is nullable with an obfuscated target, so it could not be read statically,
but that screen opened fine on the *unfixed* build, that is with the market gate already off. That is the
exact triggering condition, so the Albums provider never returns null here and there is no second null
source. **Do not re-investigate it.**

**Fix (shipped, device-confirmed 2026-08-20 on LINE 26.11.0):** also force the premium-backup gate
`nj4.d.j()` (impl `bk4.k0.j()`) to `false`. The gate is used **complementarily** in the settings UI —
`chats/a.java:880` shows the premium row on `j()`, `chats/a.java:303` shows the ordinary **"Back up chat
history"** row on `!j()` — so `false` hides the crashing row *and* restores a working non-premium backup
entry (a `m55.i1`, not a badge row, so it cannot reach `getDrawable`). All 8 call sites
(`j25/u2.java:1388,1619,1817`, `lz4/t.java:216,242`, `chats/a.java:303,730,880`) are premium-vs-classic
backup UI gating, so `false` degrades cleanly everywhere — device-checked: Settings ▸ Chats opens with
the ordinary backup row, and every other settings screen (main list, Albums, Friends) is unaffected.

**Anchoring:** `bk4.k0` is obfuscated but holds the non-obfuscated WorkManager unique name
`"PremiumBackupStatusSyncWorker"`. That string is **not** globally unique (it also appears in
`com.linecorp.line.premium.backup.impl.common.worker.a.a()V`), so the fingerprint pins the
`(String, Z)Lkotlin/Unit;` signature of `bk4.k0.h` to disambiguate, then takes `definingClass`.

> **26.14.0 note.** The `"PremiumBackupStatusSyncWorker"` enqueue body was **extracted out of the
> facade** into the (now obfuscated) helper `al4.l.b(String, Z)V`, leaving `bk4.k0.h` a three-line
> delegate with no string of its own — so the string anchor matched nothing. The fingerprint now
> pairs the `(String, Z)Lkotlin/Unit;` descriptor with `Lkotlin/Unit;->INSTANCE`; that descriptor
> occurs exactly twice APK-wide and the other one is abstract. The gate also renamed
> `nj4.d.j()` → `nj4.d.m()`, absorbed for free because the patch selects it by shape.

`m()` is
selected **by shape**, not by its drift-prone name: of `k0`'s three `()Z` methods it alone opens with an
`iget-object` (of the `bk4.z` lambda field) and contains no `invoke-interface` (`q()` opens with
`invoke-virtual`; `o()` opens with an `iget-object` of a `Lkotlin/Lazy;` read via `invoke-interface`).
`m()` is `.locals 0`, so the injection writes `p0`.

### Third lever: the Home tab upsell module (required for the Home tab)

The master lever does not reach the LYP upsell on the **Home tab**. The Home tab shows one
server-driven `List<y82.j0>` of typed modules, and this upsell is the module of type
**`HomeTabLypRecommendation`** (`y82.k0$n0`, payload `y82.x`, renderer `ac2.k`, view model `ac2.n`).

**Why the gate flip misses it:** nothing on that render path reads a premium gate. `ac2.k` and
`ac2.n` reference neither the facade `z73.k` nor the market config `a83.a`, so the module paints
whenever the server sends it — market gate false or not. The server decides whether to send it, and
the client renders what arrives.

**What the patch does:** it drops the module from the list instead of flipping another gate. That
also keeps the lesson from the Settings ▸ Chats crash — a gate flipped without its siblings produced
a state LINE never ships, whereas an absent module is a state the tab already handles (every module
in the list is optional).

The filter goes on `lb2.g$a.<init>(List, Z×5, String, Long, Long, I, Z)`, at index 0: a new method
`lb2.g$a.filterPremiumModules(List)List`, plus a branchless `invoke-static {p1}` +
`move-result-object p1` call. Notes on that surface:

- **The loop must live in a new method.** A loop with a backward branch injected inline corrupts the
  layout of an existing method, and ART then throws a `VerifyError`.
- **One literal comparison needs no extension.** The type string is compared in smali, with the
  literal as the receiver of `String.equals`, so a null type is safe. This patch stays free of
  extension code.
- **Three patches prepend at that same index** — this one, `Hide Home modules` and
  `Hide Home content feed`. All three are pure `List -> List` filters on `p1`, so the patch that
  applies last runs first and the result is the same in any order. Verified in the dex: the three
  `invoke-static` + `move-result-object v1` pairs chain, then the original `Object.<init>` and
  `iput-object v1` into field `a`.

The full Home module inventory (45 types) is in `line-patch-map.md`, section "Home tab modules".

**Not confirmed on device.** This is a static finding: the type never appeared in the on-device Home
module capture from a Taiwan account (8 modules, no `HomeTabLypRecommendation`). The module list is
region-driven and server-driven, so this needs a tester whose account gets the upsell.

### Known survivors: premium unsend upsells (patch `Hide premium unsend upsells`)

Two premium-unsend surfaces read config directly, bypassing `a83.a.d()`, so the master lever does not
hide them — a supplementary patch does:

- **"Unsend discreetly" button** in `UnsendMessageLdsDialog.onViewCreated` (shown only for the
  `…$a$c` UnsendSilently variant; the dialog gates on the *variant type*, not on LYP availability).
  It is three views: the green button `r3()` plus the icon `n3()` + label `o3()`. Two edits, anchored
  on the unique string id `0x7f150c81`: (a) force the guarding `instance-of` false so `n3()`/`o3()`
  take the hide branch; (b) `setVisibility(GONE)` on the green button `r3()` inside its
  `if (r3() != null)` guard. `r3()` exists only in the silent dialog —
  `NormalUnsendMessageLdsDialog.r3()` returns `null` — so this cannot affect the ordinary dialog, and
  the silent dialog keeps its working "Unsend" (`p3()`) and "Close" (`m3()`) buttons.
- **"How to unsend discreetly" promo link** built in the `nl1.b5` constructor, gated on
  `kh1.i2.a(i1.W() && i1.X(), …) == SUPPORTED_CHAT`. `i1.W()` is the shared bit `a83.a.d()` wraps,
  read here directly (one of the 7 non-`a83.a` `i1.W()` readers). Anchored via the unique promo
  string id `0x7f150c7a` → class `nl1.b5`. The `k2.a` first argument is forced false so the link
  handler stays null. Obfuscated `Lkh1/i2;` drifts — re-verify on version bump.
- **"Unsend" menu item for messages past the free window** — the biggest upsell ("Give yourself more
  time to delete messages you sent") is reached only by tapping the long-press "Unsend" item on a
  message older than the free window (~1h) but within the premium window (~7d). That item is built by
  the candidate predicate `kh1.x0$y.a`, whose age gate `sentTime + window >= now` uses the **premium**
  window (`Lc81/a;->p:I`) for premium-eligible chats — which is why the item (and its upsell) survives
  for up to ~7 days. Past 7d the gate fails and the item is simply never added (no popup; the
  `cantunsendafterperiod` string is dead). The patch rewrites that read to the **free** window
  (`Lc81/a;->o:I`), so the item disappears past ~1h exactly like it does past 7d — no item, no upsell;
  unsend within ~1h is unaffected. Anchored on `fieldAccess(Lc81/a;, p)` (the target read) + the
  readable enum `Lc81/c;->PREMIUM_UNSEND_MESSAGE` (disambiguator; obfuscated `Lc81/a;`/`Lc81/c;`/`p`/`o`
  drift — re-verify on version bump). Note: a premium subscriber applying the bundle also loses 1h–7d
  unsend. For a non-premium user nothing is lost (those messages were never free-unsendable anyway).
