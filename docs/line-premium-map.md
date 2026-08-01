# LINE premium (LYP) gating map & findings

Reference notes on how **LINE Yahoo Premium (LYP)** feature-gating works in LINE
(`jp.naver.line.android`), distilled from decompiling **LINE 26.11.0** (the version pinned in
`app/andrewliang/patches/shared/Constants.kt`). Companion to `docs/line-patch-map.md`.

> ⚠️ **Obfuscation drift.** Class/method names like `b13.l`, `z03.b`, `t13.i/k/n/b/q` are
> R8-obfuscated and **change between LINE versions**. The concepts and anchoring strategies below
> are durable; re-confirm exact descriptors against decompiled smali when bumping the target
> version. Prefer anchors that survive obfuscation: **string literals** (`"LITE_ENJOY"`), stable
> framework types (`Ljava/lang/Boolean;`, `Lkotlin/coroutines/Continuation;`), and Thrift op-name
> literals.

---

## The two questions, answered

1. **Is there a client-side premium flag the app reads to unlock features?**
   **Yes** — a central LYP facade the whole app reads through. But it is a *rich per-feature model*,
   not a single global boolean.

2. **Does the server double-check premium status?**
   **Yes.** The premium status the client reads is **server-authored** (synced from a Thrift RPC),
   and every premium *action* (content download, purchase, etc.) round-trips to a server endpoint
   that independently re-verifies entitlement. The client status is a read-model, not the gate.

**Net:** a blanket "unlock premium" is not achievable. Flipping the client gate can at most surface
UI / enable purely-local behaviors; server-delivered or authorized content stays enforced.

---

## The LYP facade

One obfuscated component, reached everywhere via the component accessor `z03.b.Sc`:

- **Interface** `z03.b` (jadx `jadx/sources/z03/b.java`).
- **Impl** `b13.l` = `com.linecorp.line.lyppremium.impl.LypPremiumFacadeImpl`
  (jadx `jadx/sources/b13/l.java`, smali `apktool/smali/b13/l.smali`).

**Core premium predicate** (verbatim in `u()`, `h()`): the user is premium iff
current `LypUserStatus` **is** `Subscribed` (`instance-of Lt13/i$b;`) **AND** provider `l() == LYP`
(`Lt13/q;->LYP`) **AND** `productTier` is non-empty (`((Lt13/i$b;)status).f().length() > 0`).

### Accessor map (all on `b13.l` / `z03.b`)

| Accessor | Returns | Role | Caller count |
|---|---|---|---|
| `u(Feature, Continuation)` | boxed `Boolean` | per-feature boolean gate | ~18 (e.g. `SUBPROFILE`, `MESSAGE_EDIT`, backup/gallery/migration) |
| `s(Feature, Continuation)` | `t13.k` | per-feature status object (`k.a()Z` = "is restricted/not offered") | dominant path for `APP_ICON`, `FONT` |
| `A(Feature, Continuation)` | `t13.n` | per-feature params object | e.g. `APP_ICON`, `FONT` |
| `o()` | `t13.i` (`LypUserStatus`) | synchronous raw status read; callers do their own `instanceof i$b` | 38 |
| `a(Continuation)` | `t13.i` | suspend raw status read | 33 |
| `q()` | `StateFlow<t13.i>` | reactive status | — |
| `l()` | `t13.q` | subscription provider (`LYP`, …) | — |
| `h()` | `Boolean` | "is LITE plan" (`productTier == "LITE_ENJOY"`) | — |
| `z()` | `Boolean` | premium module/feature enabled flag | — |

`u()` internally = `subscribed(above) && !s(feature).a()`. Note `s/u/A` share the erased bytecode
descriptor `(Lt13/b;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;` — only `u()` boxes a
`Boolean` (the sole `Ljava/lang/Boolean;->valueOf` call in the class).

### Feature enum `t13.b`

Values (matched by `.name()` — string anchors): `AI_TALK_SUGGESTION, ALBUM, APP_ICON, CALL_STT,
FONT, FRIENDS_MANAGEMENT, LINE_AI, MANGA, MESSAGE_BACKUP, MESSAGE_EDIT, MESSAGE_SCHEDULING, NETFLIX,
PREMIUM_BLOCK, PREMIUM_MUTE_MESSAGE, PREMIUM_UNSEND, RING_TONE, STICKER_PREMIUM_BASIC, SUBPROFILE,
YJ_SERVICES`. Which accessor a feature reads varies: `SUBPROFILE`/`MESSAGE_EDIT` → `u()`;
`APP_ICON`/`FONT`/`RING_TONE` → mostly `s()`/`A()`.

### `LypUserStatus` (`t13.i`, `com.linecorp.line.lyppremium.model.LypUserStatus`)

Sealed type. Subtypes (anchored by `toString`): `i$b` = **`Subscribed(subscriptionType=,
productTier=, …, cancelledProviders=, incentive=)`**; `i$a` = `NotSubscribed(isFreeTrialUsed=, …)`;
`i$d` = `Unavailable`. `i$b.f()` = productTier. Serialization enum `t13.x` maps status → raw strings
`SUBSCRIBED("true")`, `SUBSCRIBED_CANCELED("true_cancelled")`, `UNSUBSCRIBED("false")`,
`UNSUBSCRIBED_IN_RETENTION("false_retention")`, `UNAVAILABLE("unknown")`.

---

## Server enforcement (why the client flag is a read-model)

- **Status originates server-side:** Thrift client
  `com.linecorp.line.lyppremium.impl.network.LinePremiumStatusServiceClient` calls op
  `"getLinePremiumStatus"` → `GetPremiumStatusResponse` whose field **`active`** (Thrift field id 1)
  is the entitlement. `LypUserStatusRepository` re-syncs it revision-by-revision
  (`syncAllBatched`, `buildRevisionDrivenFlow`). The `Subscribed`/`active` the client reads is not a
  local decision.
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
non-obfuscated class refs inside `b13.l` (`LypPremiumSubscriptionActivity`,
`PremiumStateBatchedSyncWorker`). **Never** anchor on `b13`/`z03`/`t13`-style names — they drift.

---

## Unlocking is not viable (tested)

Forcing the client gate `b13.l.u(Feature) -> Boolean` to `true` was built and **tested on device —
it does not unlock anything**, confirming the analysis: the gate is a read-model, and every premium
*action* is server-enforced. That experimental patch has been **dropped**. Why it can't work:

- Features gated through the object-returning `s()`/`A()` accessors (e.g. `APP_ICON`, `FONT`) or read
  directly off raw status via `o()`/`a()` are not even reached by a `u()` flip; forcing `s()`/`A()`
  to a boolean would `ClassCastException` their callers.
- Anything the server delivers or authorizes — premium stickers/themes download, purchases,
  cloud-backup retention windows, message scheduling — stays enforced regardless of any client flag.

The practical direction is therefore **hiding** premium, not unlocking it (below).

---

## Disabling premium (this bundle: `Disable LINE Premium`)

The inverse of unlocking: since premium can't be unlocked, the `Disable LINE Premium` patch **hides
every premium surface** — upsell popups/banners, badges/locks, the "LINE Premium" settings page and
its entry rows, and the subscribe/manage flows — by forcing LINE's own market-availability flag off.

**Master lever:** `e13.a.d()Z` (`return a().W()`, i.e. `jw4.i1.W()`) — the config bit meaning "LYP
premium is available in this market". The facade reads it three ways that **all cascade from `d()`**:

| Facade read | Derivation | Effect when `d()` = false | Consumers |
|---|---|---|---|
| `z()` | `return H().d()` | `false` ("premium enabled" off) | 35 entry-point gates hide |
| `l()` | `return H().b()`; `e13.a.b()` returns `UNAVAILABLE` when `!d()` | provider = `UNAVAILABLE` | 53 region switches take the handled UNAVAILABLE/hide branch |
| `o()`/`a()` | status mapper `j13.m`: `if (!d()) return i$d` | status = `Unavailable` | 10 `instanceof i$d` sites hide |

So one edit — force `e13.a.d()` to `return false` — reproduces the exact state the app already ships
to non-premium markets. **Crash-safe** because that state is the app's own default: `UNAVAILABLE` /
`Unavailable` are explicitly-handled hide branches everywhere; no entry-point path has an unguarded
`check-cast` to `i$b`/`i$a`, and there's no retry-until-available loop.

**Premium-scoped:** `e13.a.d()` has only 4 direct callers, all in the premium module. The shared
underlying `jw4.i1.W()` is read directly by 6 non-premium features (profile, etc.) — those are
**not** touched (the patch neuters `e13.a.d()`, not `i1.W()`).

**Anchoring (no hardcoded obfuscated names):** locate facade `b13.l` via the unique `"LITE_ENJOY"`
string, then read its `z()` accessor — uniquely the parameterless `()Z` method with exactly two
`invoke-virtual` instructions (`return H().d()`; siblings `h()`/`y()`/`B()` have four) — and take
its 2nd call's `MethodReference` to resolve `e13.a.d()` at apply time. Verified on 26.11.0: only
`e13.a.d()` is rewritten (to `const/4 v0,0x0` / `return v0`); the facade is left unchanged.

**Not covered / follow-up:** a secondary market bit `jw4.m2…a().U().O()` gates a couple of surfaces
(album promo, app-icon seasonal) independently of `e13.a.d()`. If any premium surface survives on
device, neuter that bit too and record it here. Re-verify all descriptors when bumping the pinned
LINE version.

### Known survivors: premium unsend upsells (patch `Hide premium unsend upsells`)

Two premium-unsend surfaces bypass `e13.a.d()` (they read config directly), so the master lever
doesn't hide them — a separate supplementary patch does:

- **"Unsend discreetly" button** in `UnsendMessageLdsDialog.onViewCreated` (shown only for the
  `…$a$c` UnsendSilently variant; the dialog gates on the *variant type*, not on LYP availability).
  It is three views: the green button `r3()` plus the icon `n3()` + label `o3()`. Two edits, anchored
  on the unique string id `0x7f150bff`: (a) force the guarding `instance-of` false so `n3()`/`o3()`
  take the hide branch; (b) `setVisibility(GONE)` on the green button `r3()` inside its
  `if (r3() != null)` guard. `r3()` exists only in the silent dialog —
  `NormalUnsendMessageLdsDialog.r3()` returns `null` — so this can't affect the ordinary dialog, and
  the silent dialog keeps its working "Unsend" (`p3()`) and "Close" (`m3()`) buttons.
- **"How to unsend discreetly" promo link** built in the `wi1.j4` constructor, gated on
  `ne1.k2.a(i1.W() && i1.X(), …) == SUPPORTED_CHAT`. `i1.W()` is the shared bit `e13.a.d()` wraps,
  read here directly (one of the 7 non-`e13.a` `i1.W()` readers). Anchored via the unique promo
  string id `0x7f150bf8` → class `wi1.j4`; the `k2.a` first argument is forced false so the link
  handler stays null. Obfuscated `Lne1/k2;` drifts — re-verify on version bump.
- **"Unsend" menu item for messages past the free window** — the biggest upsell ("Give yourself more
  time to delete messages you sent") is reached only by tapping the long-press "Unsend" item on a
  message older than the free window (~1h) but within the premium window (~7d). That item is built by
  the candidate predicate `ne1.y0$y.a`, whose age gate `sentTime + window >= now` uses the **premium**
  window (`Lj51/a;->p:I`) for premium-eligible chats — which is why the item (and its upsell) survives
  for up to ~7 days. Past 7d the gate fails and the item is simply never added (no popup; the
  `cantunsendafterperiod` string is dead). The patch rewrites that read to the **free** window
  (`Lj51/a;->o:I`), so the item disappears past ~1h exactly like it does past 7d — no item, no upsell;
  unsend within ~1h is unaffected. Anchored on `fieldAccess(Lj51/a;, p)` (the target read) + the
  readable enum `Lj51/c;->PREMIUM_UNSEND_MESSAGE` (disambiguator; obfuscated `Lj51/a;`/`Lj51/c;`/`p`/`o`
  drift — re-verify on version bump). Note: a premium subscriber applying the bundle also loses 1h–7d
  unsend; for a non-premium user nothing is lost (those messages were never free-unsendable anyway).
