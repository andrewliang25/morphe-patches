# LINE chat, tabs and Home

The chat attach menu, Calendar and Events, the bottom tabs, the Home modules, unsend and message tips. See [README.md](README.md) for the other LINE docs.

## Chat "+" attach menu

Built by **`xi1.c.r(boolean)`** (`smali_classes9/xi1/c.smali`; jadx `xi1/c.java`), the RecyclerView
adapter for the attach grid (item layout `R.layout.chat_ui_attach_grid_item`). It concatenates two
kinds of items into one `[Lyi1/a;` array, then keeps each only if `((yi1.a) item).f(...)` returns
true:

1. **Static local tiles** — one `yi1.p` subclass each, constructed inline in `xi1.c.r()`.
2. **Server-driven services** — a runtime-fetched list (`h41.d.c()` → `List<k81.a>`); each entry
   becomes one `yi1.d` (the single shared "ChatAppButtonType" class), built in the loop at
   `xi1/c.smali:827`. If the list is not cached yet it returns `[]` and kicks off an async fetch,
   then re-renders.

### Item gates (`yi1.p` / `yi1.a`)

`yi1.p.f(Lxk1/b;Lwi1/b;Lyi1/a$a;)Z` is the visibility gate. It shows an item only when: the chat
type is in the item's allowed set **AND** `j(Lxk1/b;)Z` (per-type availability) **AND** `k(...)`
**AND** `l(...)` all pass. So **forcing `j()` false hides a static tile**. Forcing `f()` false hides
whatever class owns that `f()`.

**General rule for hiding a LINE UI list item:** LINE lists (attach-menu tiles, chat-menu rows,
context-menu actions) render each entry through a per-item availability predicate — force *that*
false rather than editing the often-shared list builder. For a whole server-driven category, neuter
the shared renderer's gate (`hideattachmenutools` → `yi1.d.f()`); hiding **one** server item
requires an id that drifts server-side, so avoid it.

### Static tiles (LINE 26.14.0)

| Tile (label) | Class | ctor type constant (`Lwi1/b$b;->…`) | `j()` availability |
|---|---|---|---|
| Calendar (`line_calendar_plusmenu_calendar`) | `yi1.b` | `CALENDAR` | `return true` |
| Message scheduler (`chat_plusmenu_button_scheduledmessages`) | `yi1.s` | `SCHEDULED_MESSAGE` | schedule-a-message composer |
| Transfer / LINE Pay (`chathistory_attach_dialog_label_select_linepay`) | `yi1.j` | `PAY` | `contains(ds3.a.PAY)` |
| LINE GIFT (`chathistory_attach_dialog_label_giftshop`) | `yi1.h` | `GIFT` | `contains(ds3.a.GIFT)` |
| Files `yi1.g`, Contact `yi1.f`, Location `yi1.m`, Voice `yi1.t`, Keep `yi1.i`, PayPay `yi1.p`, Live talk `yi1.l`, LINE MUSIC `yi1.n` | — | (their own) | — |

**To hide one static tile** (used by "[Chat] Hide Transfer button", "[Chat] Hide LINE GIFT button" and the Calendar
`+` tile): anchor its ctor on the **unique read of its `wi1.b$b` type constant** — each constant is read
in that one ctor only, and pinning the ctor's parameter list excludes the enum's `<clinit>` `sput` —
then `mutableClassDefBy(fp.method.definingClass)`, select `j(Lxk1/b;)Z` by descriptor, and prepend
`const/4 p0, 0x0` / `return p0`.

### Server-driven services — Poll, Reservation, Schedule, Ladder shuffle, …

These come from the server (category `e38.a.EnumC2123a.MORE`, mapped `e38.a`→`k81.a` in `h41.e.c()`).
`k81.a` (ChatAppViewData) = `{id, name, iconUrl, url, showNewBadge, availableChatTypes}` — labels,
icons and destinations all come from the server payload, **not** from local resources. (The
`chathistory_attach_dialog_label_poll/schedule/ladder_shuffle/reservation` strings still exist in
`res/` but are **dead** — unreferenced by any smali.)

- **Hide the whole category (stable):** every service is an `yi1.d`, built *only* in `xi1.c`, so forcing
  **`yi1.d.f(Lxk1/b;Lwi1/b;Lyi1/a$a;)Z`** to `return false` drops them all at once with no dependency on
  the drifting server payload. This is **"[Chat] Hide attach menu extra tools"**. Anchor: `yi1.d.f` is the only
  `f(...)Z` reading `Lk81/a;->f` (its `availableChatTypes` set), which separates it from the sibling
  `f()` overrides in `yi1.a`/`yi1.p`.
- **Hide one service (fragile — avoid):** a single service is identifiable only by its LINE service
  **channel id** (Schedule/create-event = `"1655112642"` real / `"1651805621"` beta, in enum
  `jg1.a$a.SCHEDULE` → `et1.s.g.SCHEDULE`). Channel ids are server-assigned and can change, so such a
  patch cannot be pinned to an APK version. Prefer the category gate.

---

## LINE Calendar vs Events vs Message scheduler — three distinct features

Easy to conflate. They are separate features with separate entry points, gates, and destinations.

**Calendar** (native LINE Calendar; strings `line_calendar_*`; feature gate interface `or0.d`, impl
`wr0.j`). Five in-messenger entry points, all removed by **"[Chat] Hide calendar buttons"**:

| Surface | Class / anchor | Hide technique |
|---|---|---|
| Chats-tab header button | `q11.q.CALENDAR` added to list `ki8.b` in `qz1.f.<init>` | remove the `sget CALENDAR` + following `add(...)` pair |
| Chat-room top toolbar | `ag1.d0.a`, `ag1.g1.CALENDAR_BUTTON` (two add-sites) via `ag1.s1.g(...)` | remove both `sget CALENDAR_BUTTON` + `g(...)` pairs |
| `+` attach tile | `yi1.b` (see attach-menu section) | force `j()` false |
| Slide-out chat-menu "Calendar" row | `z00.l` (holds `v31.b`), opens native Calendar Activity via `or0.g` | force the row's `isVisible` ctor arg (`z00.a.e`) false |
| Message long-press "Calendar" | provider `kh1.x0$c.a(Context,l31.a,c81.a,Z)Lc81/c;` (reads `c81.c.CALENDAR`) | force it to `return null` |

**Events** (chat-menu row, `chatmenu_mainlist_button_events`) — **one** entry point only. A generic
`z00.n` row built in `ChatHistoryMenuFragment` (~the `z00.n.<init>` block using string `0x7f150e87`
+ icon `0x7f0807ce`), gated by the boolean field `Lv00/o4;->l:Z` (the sole UI read of that field).
Opens a **server-configured web page** (`settings.e$c.D`), not the native calendar. Removed by
**"[Chat] Hide Events button"** — because `z00.n` is shared by other rows, patch at the build site: replace
the `iget-boolean … s4.l` (matched by `fieldAccess(Lv00/o4;,"l")` + `literal(0x7f150e87)`) with a
`const 0` into the same register.

**Message scheduler** ("send a message later"; `yi1.s`, strings `chat_scheduledmessages_*`) —
unrelated to Calendar/Events; opens the scheduled-message composer (`yr1.a`/`xr1.b`). Not currently
patched.

### Chats-tab header button set (context for `q11.q`)

The header button row (Chats tab, `com.linecorp.line.chattab.header.ChatTabHeaderStateImpl` =
`qz1.f`) is built from the Kotlin enum **`q11.q`** (constants `AI_FRIEND, ALBUM, CALENDAR, OPEN_CHAT,
PLUS_MENU` — names survive obfuscation). Buttons are `sget-object <q11.q const>` + `add(...)` into a
`ListBuilder` `ki8.b`. A separate green-dot icon `Set` uses `ki8.j` and does **not** include
`CALENDAR`. To hide a header button, remove its `sget`+`add` pair (see "[Chat] Hide calendar buttons" header
row, and the sibling "[Chat] Hide community button" which targets `OPEN_CHAT`).

---

## Main bottom-navigation tabs

Every tab patch edits the **same** builder: `b68/g.a() → List<jp.naver.line.android.activity.main.a>`
in 26.14.0 (`wy7.b.a()` in 26.11.0), appending each tab as an `sget-object <main.a const>` +
`ArrayList.add` pair. The enum is **not obfuscated**, so fingerprint on
`returnType = "Ljava/util/List;"` + `fieldAccess(MAIN_TAB, "<CONST>")`. Each patch keeps the pair and
skips it while its switch in "Andrew's Patch Setting" is on (`skipTab` in `line/shared/LineSettings.kt`).

| Constant | Tracking name | Label resource | Shown when | Hidden by |
|---|---|---|---|---|
| `HOME` / `HOME26` / `GLOBALHOME` | `hometab` / `linehome` / `globalhome` | — | always (one of the three) | — |
| `CHAT` | `chatlist` | — | always | — |
| `COMMERCE` | `commercetab` | `gnb_commerce` — "Shopping" / ja **ショッピング** | `function.maintab.commercetab` (JP) | **[Tab] Hide Shopping tab** |
| `COMMERCE_TW` | `commercetwtab` | `tw_commerce_tab_gnb` — "Discover" / zh-TW **逛逛** | `function.maintab.commercetwtab` (TW) | **[Tab] Hide Shopping tab** |
| `SQUARE` | `squaretab` | — | `rm5.b.C()` | — |
| `TIMELINE` | `timeline` | — | `db0.k0.a(m2)` | [Tab] Hide VOOM tab |
| `NEWS` / `NEWS_ROW` | `newstab` / `newsrowtab` | — | `s28.a.b()` / `kc3.d.a()` | [Tab] Hide LINE TODAY tab |
| `CALL` | `call` | — | `y28.d.c()` | — |
| `MINI` / `WALLET` | `minitab` / `wallettab` | — | `m2.a().Y().d()` / `m2.a().H0().l()` | [Tab] Hide Wallet tab |

**`COMMERCE`, `COMMERCE_TW`, `SQUARE` and `TIMELINE` share one `if`/`else-if` chain**, competing for a
single slot. Three rules follow:

- **Skip the `sget`+`add` pair; never force the gate false.** Forcing `g45.u.h()`
  (`CommerceTabConfiguration.isCommerceTabEnabled`) false falls *through* the chain and surfaces
  `SQUARE` or `TIMELINE` in the freed slot — a tab the user never had. Skipping only the body keeps
  the branch's trailing `goto`, so the slot stays empty, as stock LINE does when the gate is on.
- **Anchor each patch on its own constants only.** The tab patches run in arbitrary order against one
  method and each fingerprint resolves *after* earlier mutations, so anchoring on a constant another
  patch removes breaks the match. (Thus [Tab] Hide Shopping tab avoids `TIMELINE` and `MINI`/`WALLET`; see
  also `hidevoomtab/Fingerprints.kt`.) The patches now skip pairs instead of deleting them, so every
  constant stays, but keep the rule: a future patch may delete again.
- **The switch value goes in `v2`.** `b68/g.a()` is `.locals 4`, and `v2` is dead at all seven pairs:
  the code after each pair writes `v2` before it reads it, or never reads it again. Recheck this on a
  version bump before you reuse `skipTab`.

**The list is cached, but rebuilt on each `MainActivity.onCreate`, not once per process.**

- `b68/g` is a process-wide singleton. Its companion `b68/g$a` is an `o70.a` service-locator key.
- `b68/g.a()` builds a **new** list on every call and reads each tab's server gate then.
- `b68/g.b()` memoizes it: it returns field `e` if set, otherwise it stores `unmodifiableList(a())` in
  `e` under the lock in field `d`. Almost every reader uses `b()` (`MainActivity`, `MainActivity$a`,
  `c$h`, `c68/d`, `ze6/d`, `wt7/i`, `GnbTabDataManagerTemporaryAccessorImpl`).
- The tab manager constructor `jp/naver/line/android/activity/main/c.<init>` skips the cache: under the
  lock it calls `a()` and writes the result into `e` (`c.smali:334-338`). Only `MainActivity.onCreate`
  constructs it (`MainActivity.smali:1036`).
- `MainActivity` is `singleTop` and handles rotation itself, so `onCreate` runs again only when the
  activity is created again: back out of LINE, a process kill, a dark-mode change (`uiMode` is not in
  its `configChanges`), or `recreate()`.

So a tab switch shows after LINE's main screen is created again. The settings screen offers
**Restart LINE**, which starts the launcher activity as a new task. It does not end the process
(device-confirmed: the Wallet tab came and went with the same process id).

**A missing tab is safe everywhere.** Tab→index lookups are `Math.max(list.indexOf(...), 0)`, clamping
to Home (`jp/naver/line/android/activity/main/c.java`). `x66.d.a` only emits a `VoomSecondDepth`
telemetry event and `qy4.f0` only writes the `ADDITIONAL_MAIN_TAB` pref. The layout carries a view per
tab (`xy7.g` → `R.id.bnb_*`), so an absent entry never renders.

**The commerce tabs are region-gated, so they cannot be device-tested from TW** —
`function.maintab.commercetab` is server-pushed and off outside JP. Local proof stopped at
disassembly: both pairs gone, `if-eqz`/`goto` skeletons and the `SQUARE`/`TIMELINE` pairs intact,
whole-dex branch-offset sweep clean, all four tab patches coexisting under the full bundle. Then
**device-confirmed in JP on `v1.7.0-dev.1`** (2026-08-20, reporter of issue #59).

**A region-gated patch needs a pre-release and a tester in the region — plan for it.** Land on `dev`,
point the reporter at the auto-published pre-release tag, and hold the `dev → main` merge for a
screenshot. That is the only step separating "the instructions are gone" from "the tab is gone".

---

## Home tab modules

The Home tab renders a single server-driven `List<y82.j0>`. Everything on the tab is one of these
modules — the friends list, the service icons, the ads, and the whole content feed below the friends
list. Three patches filter that list: *[Home] Hide Home modules*, *[Home] Hide Home content feed*, and
*[Premium] Disable LINE Premium*. If you change this surface, update all three call sites.

**The chain.** `v52.g.a(Ls52/i;Ly82/y0;)` assembles the list from the GCS response (one giant
`packed-switch` over the payload oneof; **jadx fails on this method** — `Method not decompiled` — so
read `apktool/smali_classes9/v52/g.smali`; the `FLEX` arm is separate, at `v52/g.smali:5748` and
`jadx/sources/v52/j.java:191`). The list is stored as the first ctor arg (field `a`) of the Compose
state `lb2.g$a`, and rendered at `jb2/c2.java:296-300` via `r72.d(z.f229498a, z.f229502e.getType())`.

**Where to filter: `lb2.g$a.<init>(List, Z×5, String, Long, Long, I, Z)`, index 0.** Every build path
and every state copy funnels through this constructor, so one branchless
`invoke-static {p1}` + `move-result-object p1` covers the whole tab. Two rules, both learned the hard
way:

- **The loop must live in a new method.** A backward-branching loop injected into an existing method
  corrupts the branch layout into a runtime `VerifyError`. Add
  `lb2.g$a.filterHomeModules` / `filterHomeFeed` / `filterPremiumModules` with
  `mutableClassDefBy(...).methods.add(...)` and inject only the call.
- **All three patches prepend at index 0, and that is safe.** Each one is a pure `List → List`
  filter on `p1`. Thus the patch that applies last runs first. The dex confirms it: the ctor starts
  with the `invoke-static` + `move-result-object v1` pairs chained, then the original
  `Object.<init>` + `iput-object v1 → field a`.
- The earlier target `u82.c.e` built only the Friends sub-tab list (a single
  `FriendsSubTabFriendsList`), not the feed — confirmed via on-device logging.

### Module type inventory (LINE 26.14.0) — 48 types

`y82.k0` is a marker interface with one member, `getType() : String`. Most implementations are
nested in `y82/k0.java`; **three are top-level and easy to miss** (`y82.m0`, `y82.n0`, `y82.o0`).

The `Class` column below still carries the 26.11.0 nested-class letters (`a0$…`); the owning
interface is now `y82.k0`, and the letters shifted with it (for example
`HomeTabLypRecommendation` is `k0$q0`, was `a0$n0`). The `getType()` strings are the durable
identifiers and all survive verbatim — the patches match on those, never on the letters.

**Three types are new since 26.11.0** (45 → 48) and are *not* in any blocklist yet:
`GlobalHomeRecommendedSticker`, `GlobalHomeLoungeHoroscope` and
`GcsGlobalHomeActivityHybridContentCard` (top-level `y82.n0`). Nothing was removed. The first two
look like **[Home] Hide Home modules** candidates if they render in any region — that needs a full
hidden-vs-kept inventory and its own device pass before the blocklist widens.

Regenerate this diff on a bump by extracting the `const-string` from every `getType()` in the
classes referencing `Ly82/k0;` and comparing old tree to new.

| `getType()` | Class | Surface | Status |
|---|---|---|---|
| `HomeContentsRecommendation` | `a0$s` | recommended stickers / content | **[Home] Hide Home modules** (device-confirmed) |
| `HomePerformanceAd` | `a0$j0` | performance ads in the feed | **[Home] Hide Home modules** (device-confirmed) |
| `FLEX` | `a0$f` | 即時夯話題 hot topics **and** the bottom promo/ad block | **[Home] Hide Home modules** (device-confirmed) |
| `AdModel` | `a0$a` | generic ad module (`GcsAdModuleViewData` / `GcsAdMeta`) | **[Home] Hide Home modules** (static evidence only — never seen on device) |
| `HomeFeedPost` | `a0$z` | OA / LINE NEWS post card | **[Home] Hide Home content feed** |
| `HomeFeedLiveSingle` | `a0$w` | the `OA_LIVE` variant | **[Home] Hide Home content feed** |
| `HomeFeedMatomeSingle` / `-Carousel` | `a0$y` / `a0$x` | AI-digest ("matome") news cards | **[Home] Hide Home content feed** |
| `HomeFeedUnitBigVisual` / `-Grid` / `-Ranking` / `-ShortFormGrid` / `-Single` / `-SingleAndGrid` | `a0$b0`–`a0$g0` | content-unit layouts, each wrapping posts | **[Home] Hide Home content feed** |
| `HomeFeedDefaultPageError` / `-DefaultPageLoading` / `HomeFeedError` / `HomeFeedSeedPostError` | `a0$t` / `a0$u` / `a0$v` / `a0$a0` | that feed's error & spinner placeholders | **[Home] Hide Home content feed** |
| `FriendsSubTabFriendsList`, `-AllAlbum`, `-Calendar`, `-LatestNotifications`, `-RecentlyUpdatedProfiles` | `a0$i`, `a0$g`, `a0$h`, `a0$j`, `a0$k` | the friends list and its sub-tabs | kept |
| `HomeSocialGraph`, `HomeRecentlyProfileUpdate`, `HomeActivityFriendList`, `GlobalHomeFriendList` | `a0$m0`, `a0$k0`, `a0$r`, `a0$o` | friend updates / profiles | kept |
| `HomeServiceList`, `GlobalHomeServiceSection`, `SquareJoinedChatList`, `HomeNotificationHub` | `a0$l0`, `a0$p`, `a0$q0`, `a0$i0` | service icons, OpenChat list, notification hub | kept |
| `HomeTopBanner`, `SafetyCheckBanner`, `HomeLimitedNetworkModeBanner` | `a0$o0`, `a0$p0`, `a0$h0` | banners (not identified as ads) | kept |
| `GlobalHomePageError`, `GlobalHomeError`, `GlobalHomePageLoading` | `a0$l`, `a0$n`, `a0$m` | whole-tab error / loading | kept |
| `CommerceTwTabFriendshipGifts`, `-GreetingBanners`, `-QuickPolls`, `-Shortcuts` | `a0$b`–`a0$e` | the TW commerce tab | kept |
| `HomeActivityCard` | `a0$q` | recommendation surface (`contentList` / `extraContentList`) | **not blocked** — no device evidence |
| `GcsHomeActivityHybridContentCard` | `y82.o0` (top-level) | the hybrid variant of the above | **not blocked** — no device evidence |
| `HomeTabLypRecommendation` | `a0$n0` | LYP premium upsell | **[Premium] Disable LINE Premium** (third lever, no device evidence — see [premium.md](premium.md)) |
| `GcsDummyHybridModule` | `y82.m0` (top-level) | dev/dummy, no renderer | kept |

**Take this table from jadx, never from a smali grep.** 17 of the 43 nested classes are Kotlin
singletons whose `getType()` returns a `static final` field, so a grep for the literal nearest
`getType` silently reports a *neighbouring* method's string. `a0$m` is the trap: `getType()` returns
`"GlobalHomePageLoading"` while its `toString()` says `"GlobalHomeDefaultPageLoading"`.

### Why the content feed is matched by prefix

Every type in the feed below the friends list starts with `HomeFeed` (network models `GcsHomeFeed*`,
including `GcsHomeFeedScrollAffordance` — it is an infinite scroller). The server rotates between card
variants, so a literal list reopens the hole on the next rotation. The extension tests
`type.startsWith("HomeFeed")` instead. The error and loading placeholders are included on purpose, so
no orphan spinner or error shell is left where the cards were.

That covers the *module* spinners. It does not cover the page footer spinner, because that spinner
is not a module. The section that follows covers it.

**No `function.*` config gate exists for this feed.** `function.hometab.*`, `function.line_home.*` and
`function.my_home.*` carry no feed switch, so filtering the module list is the only cheap mechanism —
this is not a "force the gate false" surface.

### Filtering a paged list starts a refetch loop — the second and third levers

**The general lesson, before the detail: LINE measures the page *after* our filter runs.** When you
remove items from a paged surface, the change thus feeds back into the pager. Every future
list-filtering patch must check whether the surface pages. A patch that does not check ships a
silent refetch loop. `hidehomefeed` needed two levers more than the filter. The reporter of issue
 #69 found the first one on `v1.8.0-dev.2`.

**`lb2.g$a` is `PageData`.** The ctor is
`(List, Z, Z, Z, Z, Z, String, Long, Long, I, Z)` and holds, in order: `modules`, `isPageReady`,
`isPageRefreshing`, `isError`, `isPullToRefreshLoading`, **`isLoadingMore`**, `orderRequestId`,
`expiredTimeMillis`, `pageUpdatedTimeMillis`, `revision`, `isSafeMode`. The `toString()` of the
class names every field, thus it gives the order. Cross-check that order against the four consumers
(`jb2/q.java`, `jb2/r.java`, `jb2/s.java`, `jb2/t.java`). **Re-check this order on a version bump.**
The patch writes ctor parameter 6 by position.

**LINE has no empty state.** `GcsModuleListViewDataFacade.viewDataFlow` (`jb2/w.java`) reduces
`PageData` to `jb2.n`:

```java
if (any x in list has f333354g != 0) return new n.a(revision, list, delayedIsLoadingMore); // Content
if (isError) return n.b;                                                                   // Error
return delayedIsPageRefreshing ? n.d : n.c;                                                // Loading : Idle
```

`x.f333354g` counts the sub-items of the module. "Shows nothing" and "still loads" are thus the
same state to LINE. The renderer `jb2/x0.java` makes lazy-list items from that state. `n.d` becomes
a whole-page spinner (line 142). Inside `n.a`, an `isLoadingMoreContent` adds the `q2.LOADING_MORE`
footer (line 291).

**The pager trigger** is `jb2/m1.java:81` — `lastVisibleIndex + 6 >= itemCount`, gated on
`isPageReady`. A tab with no feed is short, thus this condition stays true always. One place reads
the trigger: `jb2/r1.java:38`
(`GcsPageState$observeEventsForLoadingModuleContent$1`), which calls `h2.H1(shouldLoadMore)`.

**The fetch** is `jb2.m2.H1(Z)V` (`jb2/m2.java:44`), the one implementation of `H1` that does work.
`jb2/r1.java:38` is its only caller in the app. Each fetch returns feed modules, and the filter
discards them. The item count does not grow, thus the next fetch starts. The server sends an endless
feed, so the `i0Var.f229286b` ("no more pages") guard is never true.

| Lever | Site | Injection |
|---|---|---|
| 1. filter the list | `lb2.g$a.<init>` index 0 | `invoke-static filterHomeFeed` + `move-result-object p1` |
| 2. hide the footer | same block | `const/4 p6, 0x0` (p6 = `isLoadingMore`, `.locals 0`, 12 registers, so v6) |
| 3. stop the pager | `jb2.m2.H1(Z)V` index 0 | `return-void` |

**Lever 3 is safe because LINE ships an empty `B1` of its own.** `pb2/y.java` implements `jb2.h2`
with every method empty, `B1` included. An empty `B1` is thus a state that LINE itself builds. Only
load-more goes through `B1`. The initial load, the pull-to-refresh and the visibility are separate
interface methods (`P4`, `R2`, `Q1`, `L`, `r5`, `w6`, `n4`).

**Anchor lever 3 on shape, never on the drift-prone name `B1`.** Use `returnType = "V"`,
`parameters = ["Z"]`, plus a read of the repository field (`fieldAccess(type = "Llb2/g;")`) and a
read of a field of the page class (`fieldAccess(definingClass = "Ly82/t0;")`). On 26.14.0 the method
cast to the page class. In 26.18.2 it casts to a wrapper that holds the page, so the filter takes
the field read. A sweep of every `.smali` finds one match each in 26.16.0 (`ch2/x2.C1`), 26.17.1
(`bj2/y2.C1`) and 26.18.2 (`ik2/s2.y1`).

Lever 3 prevents the spinner on its own, but lever 2 stays. Lever 2 costs one instruction on a
fingerprint that the patch already owns. Field `f` has one reader, thus lever 2 cannot break
anything else.

### The feed is region-driven — plan for a remote tester

Issue #69 (JP) reported LINE NEWS cards surviving every ad patch. The cards are `HomeFeedPost`,
identified by `GcsHomeFeedPost.platform{type ∈ {OA_POST, YOUTUBE, LINE_NEWS, LIVE_PREVIEW}, name}` (the
`carview! / LINE NEWS` header pair), `home_post_desc_linenews` = "LINE NEWS"
(`values-ja/strings.xml:5218`), `lineNewsContent{title, imageUrl}`, `createdTime`, `likeReaction` (the
きになる pill) and `home_post_menu_accounthide` (the ⋮ menu). Renderer `ze2.h extends l72.g<a0.z>`.

They were never blocked because PR #15 locked the blocklist to what a **Taiwan** account renders —
PR #14's `MorpheHomeModules` logcat showed `size=8` with **no** `HomeFeed*` type at all. Same lesson as
the commerce tabs: a region-gated surface needs a pre-release and a tester in the region.

**Diagnostic recipe** (from PR #11, revert before shipping): log every type at filter entry under tag
`MorpheHomeModules`, then `adb logcat -c && adb logcat -s MorpheHomeModules`. No lines = the filter
never ran; `ENTER` with no per-type lines = empty list at that point; per-type lines = the real strings.

### Finer discriminators, if a type string is ever too coarse

`y82.j0` carries more than the payload: `f229498a` = module id (for example
`home-feed-module_home-feed-default-page-loading`; **LINE itself filters on it** at `an2/d.java:75`),
`f229499b` = module name, `f229500c`/`f229501d` = timestamps, `f229502e` = the `y82.k0` payload,
`f229503f` = ACI gate enum `y82.y0` (`DISABLED` / `ACI_REQUIRED` / `ALWAYS`), `f229504g` = upstream
request id, `f229505h` = global service key, `f229506i` = render kind (`FLEX` / `NATIVE` / `HYBRID`).
Inside the `HomeFeedUnit*` payloads there is also `type: y82.l0` (`AUTO` / `PACKAGED` / `KEYWORD` /
`FITTED` / `MANUAL`) and `contentType: y82.k0` (`MASS` / `CLUSTER` / `PERSONAL`).

Renderers subclass `l72.g<T>` / `r72.b<T>` and register a `KClass`, so the renderer confirms a type
actually paints: `a0$z`→`ze2/h.java`, `a0$x`→`mg2/h.java`, `a0$y`→`ng2/g.java`, `a0$s`→`tg2/n.java`,
`a0$j0`→`ec2/f.java`, `a0$f`→`d62/r.java`, `a0$m0`→`f30/b.java`, `a0$l0`→`b30/b.java`,
`a0$n0`→`ac2/k.java`, `y82.o0`→`sd2/i.java`.

### Values that drift on a version bump

`lb2.g$a` (state class + ctor signature), `y82.j0` field `e`, `y82.k0` and every `a0$*` letter suffix,
the assembler `v52.g`, the consumer `jb2.c2`. The **type strings themselves are server contract** and
have been stable; re-run the jadx sweep over `y82/k0.java` to re-audit the `HomeFeed*` family, in case
LINE adds a wanted module under that prefix.

---

## Message unsend (receive side) & the "[Chat] Keep unsent messages" patch

### How an incoming unsend reaches the database

```
OpType NOTIFIED_DESTROY_MESSAGE(65) / DESTROY_MESSAGE(64)   (Lcb8/ce;, Operation = Lcb8/de;)
  ► e98.c1.b(...)  (someone else unsent)   /   e98.r  (3-line subclass: your own unsend)
  ► the la8.x lambda, run inside a chat_history transaction
```

Both ops funnel through the **same** lambda, so one patch site covers your own unsends too.

**LINE does not delete the row for 1:1/group chats.** `Lla8/x;->invoke(Ljava/lang/Object;)Ljava/lang/Object;`
(`smali_classes4/la8/x.smali`) rewrites `chat_history.type` to an `na8.c.UNSENT*` variant, NULLs
`content`, `parameter`, `attachement_type` and the location columns via `ma8.h0` →
`Lma8/b;->g(SQLiteDatabase, Lna8/k;, Lma8/h0;)I`, drops the message from the full-text-search index and
deletes its `reactions` / `multiple_image_message_mapping` rows — all behind one guard:

```smali
    check-cast v1, Lla8/b3;                   # the lambda's transaction-context parameter
    ...
    iget-object v3, v1, Lla8/b3;->b:Landroid/database/sqlite/SQLiteDatabase;
    ...
    :cond_0
    iget-object v9,  v13, Lna8/b;->g:Lna8/c;  # v13 = the fetched row
    iget-wide  v10, v13, Lna8/b;->b:J         # message id (live at the guard)
    invoke-virtual {v9}, Lna8/c;->g()Z        # already an unsend tombstone?
    move-result v9
    if-eqz v9, :cond_1                        # no  -> destructive block
    goto/16 :goto_b                           # yes -> skip, return the row unchanged
```

Forcing that register non-zero makes the unsend a **local no-op**. `Lla8/b3;` (the lambda's
parameter, `v1`) carries the transaction's `SQLiteDatabase` in field `b`.

**26.14.0 moved that field read.** On 26.11.0 the `SQLiteDatabase` `iget-object` sat *inside* the
block the guard skips; R8 has since hoisted every `Lla8/b3;` field read into the method prologue,
ahead of the guard. The patch therefore searches the **whole method** for it rather than only from
the guard onwards. It still requires a dominating `check-cast` for the holder register, which is
what rejects the method's other `SQLiteDatabase` read (`Lma8/t0;->a`, whose holder is reassigned
without a cast). Both versions select the same instruction, because it is the first
`SQLiteDatabase` read in the method either way.

The guard itself needed **no** edit across the bump: it is found by instruction shape, so its
rename (`na8.c.h()` → `na8.c.g()`) was absorbed for free. That is the argument for shape-based
lookups over name-based ones.

**OpenChat/Square is a different path and genuinely deletes**: `SquareEventType.NOTIFIED_DESTROY_MESSAGE(5)`
→ `fp5.i` → `Lla8/n0;->m(Ljava/lang/String;Ljava/util/Set;)V` → `la8.b3.c(Set)` →
`DELETE FROM chat_history WHERE id IN(...)`. Not covered by the patch.

A third path covers messages unsent while offline: full sync / message-box restore reads
`z58.b.c.KEY_UNSENT_MESSAGE` / `KEY_SILENTLY_UNSENT` from `contentMetadata` (`la8.n0`, `la8.x2`) and
stores the row already stripped — nothing local to keep.

### How LINE draws its own unsend notice

This is LINE's path for a tombstone row. The patch does not use it any more, but LINE still draws
it for rows that it stripped while the patch was off.

`chat_history.type` → content model → text, four hops:

| Hop | Descriptor |
|---|---|
| cursor → content model | `Lma8/t;->e(Lcb8/q7;Ljp/naver/line/android/util/j;Lz58/b;)Lna8/g;` — `UNSENT` builds `Lna8/g$s$h0;` from `from_mid` |
| content → UI model | `Lm11/b;->k(Lna8/g$s;)Ll11/h;` → `Ll11/h$h0;` |
| UI model → text | `Lcl1/c;->a(Landroid/content/Context;Ll11/h;Lo21/a;)Ljava/lang/CharSequence;` |
| bubble decoration | `Lnl1/b5;->K0(...)` — appends the "How to unsend discreetly" link on *your own* unsends (suppressed by *[Premium] Hide premium unsend upsells*) |

Strings: `chathistory_message_format_unsent_receiver` (`0x7f150d65`, "%1$s unsent a message.") and
`chathistory_message_format_unsent_sender` (`0x7f150d66`, "You unsent a message.") — chosen by
comparing `from_mid` against your own mid.

`Lma8/x;` (query builder) filters `UNSENT_SILENT` out of chat history entirely
(`type NOT IN (...)`), which is how LYP "unsend discreetly" hides a row it still stores.

### What the patch does

Skips the guard, then marks the kept row (`app/andrewliang/extension/KeepUnsentMessages.java`). The
row itself is not changed otherwise. It keeps its real `server_id`, so reply-jump, forwarding and
reactions keep working on the kept message.

The guard is located **by instruction shape** (no-arg `Z` call → `move-result` → `if-eqz` → `goto`) and
the `SQLiteDatabase` field reference is read from the method's own bytecode, because `na8.c`, its `h()`
and `la8.b3.b` all drift. The two register reads are anchored rather than scanned blind: the message id
must be a field of the same row object the guard reads its receiver off, and the `SQLiteDatabase` holder
register must carry a `check-cast` to the field's own owner before the guard (this method has a *second*
`SQLiteDatabase` read, `ma8.t0.a`, reusing register `v1` for a different type). Anything unresolvable —
including a register past `v15`, where `iget`/`invoke` operands stop fitting — throws rather than
applying a half-patch.

On 26.16.0, LINE reads the row id (`iget-wide`) between the row load and the guard, and the block
passes that register. From 26.17.1, R8 drops that read. The block then reads the id itself, from the
same field (`b`, the first `iget-wide` on the row class in the method), into a free low register
pair. It then jumps to the skip target, which returns the row. The patch refuses a skip target that
does not return the row. The block must not join the original guard branch: there the `long` pair
meets the stock path, which still reads those registers as objects, and ART rejects the method.

The mark is skipped when the row is **already** a tombstone (`type IN (27, 28, 38)`, what `na8.c.h()`
covers). The injection sits ahead of the branch it flips, so it also runs where LINE's guard would have
exited early: a redelivered unsend for a row tombstoned before the patch was installed, or one stripped
by the offline `KEY_UNSENT_MESSAGE` path, which never reaches the guard at all. LINE draws its own
notice for those rows.

### The "Unsent" label

The patch shows a red "Unsent" in the place of LINE's "Edited" label, next to the time. Device-confirmed
on 26.15.1 (sent and received rows, Black and light themes).

**The mark** is the key `ANDREW_UNSENT\ttrue` in the row's `parameter` column. That column holds the
content metadata as tab-separated `key\tvalue` pairs (`ma8.d.a` splits it, `ma8.d.c` joins it). LINE
keeps its own edit flag there (`EDITED\ttrue`, enum `IS_EDITED_MESSAGE`), so the mark is read with the
rest of the message and costs no query. The edit merge (`k91.d`) reads the whole map and writes it back,
so the mark stays when a kept message is edited.

**Where "Edited" comes from.** The layouts `chat_ui_row_layout_receive_horizontal_metadata` and
`chat_ui_row_send_edited_and_read_count` hold a `TextView` `chat_ui_row_edited_mark_text` ("Edited",
`chat_messageedit_desc_edited`). On received rows it sits above the time. On sent rows it sits in the
line above the time, between the mute icon and "Read", with dot dividers. The rich, HTML and Flex
layouts (`*_vertical`) do not have it, so those messages get no label. They are almost all official
account and bot messages.

| Hook | 26.15.1 | 26.14.0 | Anchor |
|---|---|---|---|
| view data (`ChatMessageCommonViewData`) | `te1.c` | `m91.c` | `toString` string. The patch adds the field `andrewUnsent:Z` |
| its `equals` | `te1.c.equals` | `m91.c.equals` | returns false when the two marks differ |
| view data builder | `re1.d.b` | `k91.e.b` | `sget IS_EDITED_MESSAGE` + a constructor call with 25 or more parameters |
| label controller (`EditedMarkViewController`) | `qr1.e` | `hm1.d` | first constructor parameter of the `applyClickableSpan` coroutine (`qr1.f` / `hm1.e`), found by its Kotlin debug metadata |
| row binder | `wq1.f.K0` | `nl1.g.K0` | the one method that reads the label, calls `setVisibility`, then `getVisibility` |

The binder reads `isEditedMessage` (field `x`) and sets the label's visibility from it. The patch calls
the extension after LINE's `getVisibility`, so LINE alone decides whether to start the coroutine that
makes "Edited" a link (`e71.a.b`). For a message that is edited and also unsent, that coroutine can
still make "Unsent" a link to LINE's edit info page. For that reason the extension sets the
color on the whole `TextView`: the coroutine copies the text without its spans.

**Why `equals` is patched.** An unsend that arrives while the chat is open changes only the mark. The
new view data then compares equal to the old one, and LINE does not bind the row again. With the mark
in `equals`, the label shows at once.

**The text.** The extension has a short table. Each word is the past form of LINE's own "Unsend" verb
(`line_chat_button_menuunsend`) in that language: `已收回` (zh-TW), `已撤回` (zh-CN), `送信取消済み`
(ja), and so on. Hong Kong Chinese and every other language get English "Unsent", the same as LINE's
"Edited" for Hong Kong (`values-zh-rHK` has no translation of it). The patch does not use LINE's own
`chat_premiumunsend_toast_messageunsent` ("Message unsent"). In ja, ko, de, fr, it and id that string
is a full sentence, too long for the column beside a received bubble. The color is LINE's
`linered600` (`#ff334b`), resolved by name.

**Old notice rows.** Earlier versions inserted a `type = 27` row with `server_id` + `"_unsent"` below
each kept message. At the first unsend in each process, the extension marks the message of each such
row and deletes the rows. Device-confirmed: a message kept by the old version showed the label and
lost its notice row.

**Forwarding.** LINE builds a forwarded message's metadata from scratch for most types, so the mark is
not sent. The one exception found is a LINE MUSIC share (`na8.g.m`), whose whole map is copied in
`ig8.l`. A recipient with this patch would then see "Unsent" on that forward.

### Values that drift on a version bump

| Thing | 26.14.0 |
|---|---|
| `chat_history` table (`a68.a`) | only `id` constrained (PK + autoincrement); all other columns nullable; `IDX_SERVER_ID` is **non-unique** |
| `parameter` | tab-separated `key\tvalue` pairs. An empty column must get the pair with no leading tab. Otherwise every key and value after it shifts by one |
| `na8.c` db values | `MESSAGE` = 1, `UNSENT` = 27, `UNSENT_NO_MARK` = 28, `SQUARE_UNSENT_MESSAGE` = 35, `UNSENT_SILENT` = 38 — the extension hardcodes 27/28/38 (`na8.c.h()`'s set, the rows it does not mark) and 27 (the type of the old notice rows) |
| chat-list unread badge | `chat.message_count - chat.read_message_count` (`c23.d` columns, read in `z13.o`) — a stored counter pair, **not** a `count(*)` over `chat_history` |

---|---|
| `chat_history` table (`a68.a`) | only `id` constrained (PK + autoincrement); all other columns nullable; `IDX_SERVER_ID` is **non-unique** |
| sort columns | `IDX_CHAT_ID_ID_CREATED_TIME` = `chat_id` (eq) + `created_time`, `id` (sort) → ordering follows `created_time` |
| `created_time` | `DATE_STRING` → a **TEXT** column of epoch millis, so `+1` needs a `CAST` round-trip |
| `na8.c` db values | `MESSAGE` = 1, `UNSENT` = 27, `UNSENT_NO_MARK` = 28, `SQUARE_UNSENT_MESSAGE` = 35, `UNSENT_SILENT` = 38 — the extension hardcodes 27 (the type it writes) and 27/28/38 (`na8.c.h()`'s set, the rows it refuses to annotate) |
| `cb8.q7.NONE` | 0 (`attachement_type`) |
| chat-list unread badge | `chat.message_count - chat.read_message_count` (`c23.d` columns, read in `z13.o`) — a stored counter pair, **not** a `count(*)` over `chat_history`, so the inserted placeholder cannot move it |

---

## Message tips & the "[Chat] Hide tips under messages" patch

The "CHECK 探索如何收藏好友傳來的影片 >" pill under a received video is a message tip. The tip is
a ComposeView that inflates from the `chat_ui_entry_banner_stub` ViewStub (id `0x7f0b06e3`). Four
row layouts carry the stub: `chat_ui_row_layout_receive_horizontal`, `…_receive_vertical`,
`…_send_vertical` and `chat_ui_row_send_msg_carousel_image_viewer`.

| Role (26.14.0) | Class |
|---|---|
| `ChatBubbleEntryBannerViewBinder` (holds the stub) | `rm1.t`, built in the `fm1.e` constructor |
| Bind coroutine (`…ViewBinder$bind$1`) | `rm1.u.invokeSuspend` |
| Banner state | `sm1.g`: `g$a` = none, `g$b` = show |
| Message types that can carry a tip | `rm1.w.b`, a `Set` of `m91.w` |
| State flows, one for each tip kind | `rm1.w.f`, keyed `s31.d.VIDEO` and `s31.d.ALBUM` |
| Bind call site | `nl1.g`, which reads `fm1.e.I` |

`invokeSuspend` tests `instance-of sm1.g$b` first. If the test is false, the method calls
`se7.b.b(false)` to keep the stub hidden and clears its click listener. The patch replaces that
test with `const 0`. Only the "show" branch adds the message to `rm1.w.h` (the set of tips shown)
and starts the `rm1.v` coroutine. Thus the patch also stops LINE from recording the tip as shown.

The fingerprint does not use the obfuscated names. It matches `invokeSuspend` with the first
`instance-of`, then `String.valueOf(J)`, the `view_tree_lifecycle_owner` tag id (`0x7f0b29ab`) and
`ComposeView.setContent`, in that order. This shape is unique in the 26.14.0 APK. The patch is
device-confirmed on 26.14.0 (2026-09-25).

## Group readers & the "[Chat] Show who read messages" patch

LINE shows "Read N" under your own messages in a group. It does not show who read a message, and
it shows nothing under the messages of other members. The patch adds "Read by" to the long-press
menu of any message in a group or a multi-person chat. It lists the members who read the message
and the members who did not.

### Where the data is

LINE keeps one read range for each member of a chat, in the `read_message_range` table of its
`read_notification` database. Columns: `chat_id`, `member_id`, `from_msg_id`, `to_msg_id` and
others. A member read message M when `from_msg_id < M AND to_msg_id >= M`. LINE counts the same
rows for its own "Read N" label, so the list matches that number.

| What | Database / table | Note |
|---|---|---|
| Read ranges | `read_notification` / `read_message_range` | Filled by `NOTIFIED_READ_MESSAGE` and by the read-range sync. |
| Group members | `naver_line` / `membership` | `id` = chat id, `m_id`, `is_accepted = 1`. Includes this account. |
| Multi-person chat members | `naver_line` / `chat_member` | `chat_id`, `mid`. |
| Names | `contact` / `contacts` | `overridden_name` (the name you set), else `profile_name`. |

A group chat id starts with `c`, a multi-person chat id with `r`. One-on-one chats and OpenChat are
not covered.

Measured on 26.18.2 with a probe (2026-11-25):

- The table has no row for this account, so this account never shows as a reader.
- Sending a message does not count as reading it: the sender's range stops before their own new
  message. The list still leaves out the sender.
- The sender id (`senderMid`) of your own message is empty, and `membership` includes this account.
  So the extension reads the account id from LINE's profile to leave it out of "Not read".
- LINE's read-range sync for one chat returned no error in 165–244 ms.
- In a 4-member group, the list on another member's message showed the third member who read it.
  It showed the fourth member under "Not read" (device-confirmed on 26.18.2, 2026-11-25). The list
  runs LINE's read-range sync for the chat first. Nobody measured whether such a read reaches the
  phone without the sync.

### Anchors (26.18.2)

| Role | 26.18.2 | How the patch finds it |
|---|---|---|
| Menu grid builder | `wl1.l.i(n.c, View, fe1.d, ge1.a, …)` | The three layouts `chat_ui_message_context_menu_content`, `…_row`, `…_item` |
| Menu color call | `hk6.n.z(View, Set, f)Z` | The first interface call `(View, Set, *)Z` in the builder |
| Menu dialog | `rn1.n1` (implements `DialogInterface`) | The first field read after the color call |
| Menu params | `rn1.n1.e` (`ge1.a`) → `.b` (`ge1.b`) → `.b` (`qf1.c`) | The fields that `toString()` prints after `", messageViewData="` and `", chatMessageCommonViewData="` |
| Message data | `qf1.c`: `a` chat id, `c` server id, `e` sender | The fields printed after `"…(chatId="`, `", serverMessageId="`, `", senderMid="` |
| Service locator | `mc0.f.a(Context, mc0.a)` | The static `(Context, *)Object` call just before the color call |
| Profile | `hs3.b.n4` key, `getProfile()` → `ks3.a`, mid `b` | The interface with `getProfile()` of the type whose `toString()` prints `"Profile(authTokenV1="` |
| Read-range sync | `j30.d.a` key, `d()` → `gr8.c` (`ReadCountManager`), `gr8.e.b(db, Set, gr8.c$a)` | The only `invokeSuspend` that calls `(SQLiteDatabase, Set, *)Lorg/apache/thrift/*` |

The hook goes in just before the color call, after `getClass(grid)`, and passes the grid and the
menu dialog. It does not pass the params argument (`p4`), because R8 reuses that register for an
iterator before this point. A first build that passed `p4` crashed with
`ClassCastException: ArrayList$Itr`. The dialog keeps the params in its only field of that type.
The new cell is in the grid before LINE colors the grid, so it gets the same colors as LINE's cells. `kk1.n.s` (26.16.0) and `bl1.p.s` (26.17.1) have the
same shape.

The patch generates three methods in `GroupReaders`. `menuBuilt` reads the message fields and calls
`addCell`. `ownMid` and `syncReadRange` replace two Java stubs. The extension puts "Read by" in the
first empty cell of the last row. When the last row is full, it adds a new row of empty cells. The
menu of a photo has 8 items, and this case is device-confirmed on 26.18.2. The list dialog is light
or dark after LINE's `primaryBackground` color. The list reads the databases read
only, on a worker thread, after the sync.
