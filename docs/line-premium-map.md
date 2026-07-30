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
`Boolean` (the sole `Ljava/lang/Boolean;->valueOf` call in the class), which is how the unlock patch
disambiguates it.

### Feature enum `t13.b`

Values (matched by `.name()` — string anchors): `AI_TALK_SUGGESTION, ALBUM, APP_ICON, CALL_STT,
FONT, FRIENDS_MANAGEMENT, LINE_AI, MANGA, MESSAGE_BACKUP, MESSAGE_EDIT, MESSAGE_SCHEDULING, NETFLIX,
PREMIUM_BLOCK, PREMIUM_MUTE_MESSAGE, PREMIUM_UNSEND, RING_TONE, STICKER_PREMIUM_BASIC, SUBPROFILE,
YJ_SERVICES`. Which accessor a feature reads is what determines whether the client `u()` patch can
touch it: `SUBPROFILE`/`MESSAGE_EDIT` → `u()`; `APP_ICON`/`FONT`/`RING_TONE` → mostly `s()`/`A()`.

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

## What patching can / can't do

- **Can (client-only):** the `Unlock premium features (experimental)` patch forces
  `b13.l.u(Feature) -> Boolean` to `true`, which flips the client per-feature availability gate for
  features whose consumers read `u()`. This is UI/behavior-only.
- **Not covered by the boolean patch:** features gated through the object-returning `s()`/`A()`
  accessors (e.g. `APP_ICON`, `FONT`), or read directly off raw status via `o()`/`a()`. Forcing
  `s()`/`A()` to a boolean would `ClassCastException` their callers — they must be handled
  per-feature, not with a blanket flip.
- **Can't (server-enforced):** anything the server delivers or authorizes — premium stickers/themes
  download, purchases, cloud-backup retention windows, message scheduling — stays enforced
  regardless of the client flag.

### Empirical results (fill in after apply-and-test)

Record here which `u()`-gated features actually unlocked on a non-premium account vs. stayed
server-denied, once the experimental patch has been applied and installed. If the compelling
features (`APP_ICON`/`FONT`) turn out to route through `s()`/`A()` rather than `u()` — meaning the
patch does little visible — that is itself the finding, and a per-feature approach (fabricating the
"available" `t13.k` returned by `s()`) would be the follow-up. Update this section and re-verify the
descriptors whenever the pinned LINE version is bumped.
