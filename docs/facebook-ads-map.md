# Facebook ad map & findings

Reference for the Facebook (`com.facebook.katana`) patches. It comes from a decompile of
**577.0.0.50.72**, the version pinned in `app/andrewliang/patches/shared/Constants.kt`.

APKMirror lists many variants of each Facebook release, and each variant has its own versionCode.
Thus the versionCode alone does not identify a download. The decompile uses this variant:

| | |
|---|---|
| APKMirror title | Facebook 577.0.0.50.72 (arm64-v8a) (360-480dpi) (Android 11+) |
| versionCode | 474426275 — for this variant only |
| ABI / density / minSdk | `arm64-v8a` / 360–480 dpi / 30 |

To get the same bytecode, select the variant by its title. Do not search for the number.

Each variant is a separate build with different DEX, not an ABI split of one bundle. Thus
`COMPATIBILITY_FACEBOOK` pins the versionCode and the minSdk of the tested variant.
**At each version bump, update the versionCode and the minSdk together with the version.**

> ⚠️ **Obfuscation drift.** `LX/1lD;`, `LX/awi;` and `LX/50Q;` are Redex names. They change on
> **every** Facebook release, which is about every two weeks. No patch hard-codes one — see
> [Anchoring](#anchoring). Confirm them again on a version bump.

---

## Shape of the target

| | |
|---|---|
| `base.apk` | 151 MB, 20 dex (about 146 MB of bytecode) |
| Splits | `maplibre`, `papaya`, `pytorch` — no app code. There is no `isSplitRequired`, so `base.apk` installs alone |
| Classes | 197,471. Of these, 175,136 (88.7%) have Redex `LX/…` names |
| Application | `com.facebook.katana.app.FacebookApplication` |
| minSdk | 30 |

Redex names are **case-sensitive**. `LX/1y1;` and `LX/1Y1;` are different classes, but they collide
on stock macOS APFS. As a result, `jadx` and `apktool` write one over the other without an error.
Decompile onto a case-sensitive volume:

```bash
hdiutil create -size 40g -type SPARSE -fs 'Case-sensitive APFS' -volname FBCS work/fbcs
hdiutil attach work/fbcs.sparseimage
jadx -j 8 --no-res --no-debug-info -d /Volumes/FBCS/jadx/c2 work/fb-extract/dex/classes2.dex
```

`classes2.dex` (12 MB) holds the whole news-feed core, and it decompiles in about 3 minutes. A
whole-APK jadx run is not worth the time. Use dexlib2 for lookups, and jadx on one dex file when you
must read the code.

---

## Anchoring

Four sources of names survive Redex. Each patch anchors on one of them, and on nothing else.

**1. Kept `com.facebook` class names.** These are GraphQL models (Parcelable and tree reflection),
plugin classes, and WorkManager workers:

```
com/facebook/graphql/model/GraphQLFeedUnitEdge, GraphQLStory, SponsoredImpression
com/facebook/graphql/model/GraphQL*FeedUnit          (the injected-unit types)
com/crossapp/graphql/facebook/enums/GraphQLFeedStoryCategory
com/facebook/stories/model/StoryBucket               (getBucketType() is kept too)
com/facebook/ads/AdsScreenshotDetector, com/facebook/feed/platformads/AppInstall*
com/facebook/feed/push/adschannel*/…Worker, …InitializerAppJob
```

**2. Kept method names.** A few survive: `addNewEdgeToCollection`, `getBucketType`,
`doHandleIntent`, `persistSeenState`, `getCachedModel`. Note that `androidx.work.Worker` keeps its
*class* name, but Redex renames its `doWork()` to `A02()LX/5qf;`. A framework name is not safe by
default.

**3. Enum-constant names.** Redex renames the fields, but the constant names stay as `<clinit>`
string literals. Bind by *position*: the name goes into one register and the instance into a
different one. Take the first `sput-object` of the enum type after the literal. A match on the
register of the name resolves the wrong constant.

**4. QPL and systrace literals.** These give the original class name and the method name:

```
"FeedUnitCollection.addElementAtTail"              -> LX/1k1;->A0J
"handling_inorganic_clash"                         -> LX/awi;->B5t
"VideoHomeDataControllerImpl.maybeInsertAds"       -> LX/50Q;->Cwp
"FeedSponsoredStoryHolder.getTopValidAd"           -> LX/1y1;->A0R
"StoryViewerMidCardDataSource.getBuckets"          -> LX/A2v;->B5t
"AdPaginatingBucketStaticInsertionDataSource.getBuckets" -> LX/gq4;->B5t
"VideoHomeDataControllerAdsUtil.maybeInsertFbShortsRealtimeIntentItem" -> LX/54e;->A02
"VideoHomeDataControllerSfdAdsUtil"                -> LX/6S7;->run
```

A Kotlin `Intrinsics` null-check message works the same way, because it carries the name of the
variable it guards. `"uninsertedMainAdsQueue"` reaches `LX/Apf;->B5t` that way; it appears in three
methods, and the `ImmutableList` return type is what picks the right one.

### `__redex_internal_original_name` is weaker than it looks

13,208 classes (6.7%) have a static string field with the name from before obfuscation. **Most are
inner Kotlin lambdas, not the class you want.** Only 27 ad-related *outer* classes have their own
(`AdBreakFetchHelper` is `LX/SMj;`, `SqueezebackAdPlugin` is `LX/TZ5;`, `InstreamAdFetchUtil` is
`LX/6yF;`, `PlayableAdPreloadManager` is `LX/74o;`, `MegaphoneFetcher` is `LX/2iY;`).

The link from a lambda to its enclosing class is **not** reliable. `LX/Uj4;`
(`PauseAdComponent$render$fbBloksComponent$2$4`) captures two unrelated obfuscated types, and
another lambda creates it. A lambda-only entry locates a surface. It is not an anchor.

---

## How Facebook marks an ad

`LX/2U1;->A00(LX/3iq;)Lcom/facebook/graphql/model/SponsoredImpression;` is the one accessor for
"is this sponsored". Every such predicate in the app goes through it. It reads `sponsored_data` from
the story tree through `GraphQLPartialStory.FIELD_NAME_HASH_CODE_sponsored_data`, then caches the
result.

`SponsoredImpression.A05()Z` returns the constant `true`, and the method on `OrganicImpression`
returns false. Thus `BaseImpression.A05()` is `isSponsored()`. `SponsoredImpression.A0E` is the
shared EMPTY instance.

**No client-side gate asks whether to render a sponsored story.** Every caller of these predicates
is a label, a menu item, or an impression logger. A patch must add the filter itself. This is why
the insertion points matter more than the predicate.

The feed patch uses a cheaper signal. `GraphQLFeedUnitEdge.B8f()` returns
`GraphQLFeedStoryCategory`, whose constants include `SPONSORED`, `PROMOTION`,
`HIGH_VALUE_PROMOTION`, `FRIENDLY_FEED_PROMOTION`, `INJECTED_STORY` and `ORGANIC`. It resolves
through `getCachedEnum(id, class, default)`, so it never returns null.

---

## Shipped patches

**A device test on 2026-09-15** used a re-signed 577.0.0.50.72 with all seven patches enabled, and
found no problems. The test was a general pass, not a check of each surface, so the risks below stay
open.

That test then missed real leakage. Ads still appeared now and then in Reels and in the story viewer,
and they were gone after the app was closed and reopened. The cause was **not** a prefetch cache. The
six insertion sites added for it in 2026-09-18 are verified in the dex — see
[Two families of insertion](#two-families-of-insertion).

The block on those sites was necessary but not sufficient. A device round on 2026-09-19 showed that
Reels ads arrive **inside the fetched page**. The server puts them there, so no insert runs, and no
insertion patch can stop them. The patch removes them from the page instead, at the level that the
screen reads. See
[Reels ads arrive inside the page](#reels-ads-arrive-inside-the-page-not-through-an-insert). The
story-viewer half of that work is still **not device-tested**.

| Patch | Target | Verified in the patched dex |
|---|---|---|
| `[Feed] Hide sponsored posts` | `LX/1lD;->addNewEdgeToCollection` guard on `GraphQLFeedStoryCategory.SPONSORED` (it is `A0K`) | The branch lands on original instruction 0. Try blocks moved from `@fb` to `@107` |
| `[Feed] Hide suggested and promoted posts` | The same chokepoint, plus a new `LX/1lD;->isSuggestedOrPromotedFeedUnit` | 15 `instance-of` arms, all of which branch to `@3e` |
| `[Stories] Hide sponsored stories` | 4 bucket data sources return their input list: `LX/awi;`, `LX/Apf;`, `LX/gq4;`, `LX/A2v;` | Each `return-object` names that method's own `p3`: `v28`, `v74`, `v9`, `v35` |
| `[Reels] Hide sponsored reels` | The page filter at the controller's `(List)Z` entry and at the item collection, plus `return-void` in `LX/50Q;->Cwp` (`maybeInsertAds`), `LX/54e;->A02`, `LX/6S7;->run`, `LX/6SZ;->run` | Every `return-void` lands before the QPL marker, so no trace section stays open. The filters are device-tested. See [Reels ads arrive inside the page](#reels-ads-arrive-inside-the-page-not-through-an-insert) |
| `[Ad] Block background ad prefetch` | 8 void methods across 7 schedulers with kept names | All are `return-void`. Constructors and the `A00()Z` gate are untouched |
| `[Ad] Block ad telemetry` | 6 void methods across 4 classes with kept names | All are `return-void`. `onStartCommand` and the predicates are untouched |
| `[Ad] Disable Audience Network` | 5 manifest components | All have `android:enabled="false"` |
| `[General] Open links in external browser` | `BrowserLiteActivity->onCreate` and `->onNewIntent`, hooked after their super call | Both branches resolve to a target index: `onCreate` to the trace-close marker load, `onNewIntent` to the original next instruction |
| `[Stories] Download any story` | The one capability check in `StoryViewerMoreButtonCallback`, plus the body of the action's tap handler | `const/4` into the register its `move-result` wrote, so the cached capability reads true; then the handler runs our own download, which skips Facebook's licensed-music check (issue #110) |

Together the eight patches rewrite 28 classes, and they add the extension on top of that. The CLI
prints this count as `Stripping N modified classes`. Two controlled runs on 2026-09-19 against
bundle 3.0.1-dev.1 give the split. The whole bundle strips **28** classes and writes 1,115 new
ones. The seven ad patches alone, with the browser patch off, strip **26** and write 1,113. Thus
the browser patch is the two browser activities and its part of the extension.
`[Stories] Download any story` came after those runs, thus it is in none of these counts.

`BranchSweep` reads the 21 dex files, and each branch offset, try range and handler lands on an
instruction start. Its last totals for classes and methods are **older than the counts in the
paragraph above**. They come from before the Reels page work of 2026-09-19: 197,471 classes and
630,827 methods for the ad patches with no extension, and 198,557 and 641,106 for the whole bundle.
Do the sweep again before you quote these numbers.

### Two families of insertion

Neither Reels nor Stories has one chokepoint. Each has a **batch path** that runs when a page loads,
and several **on-demand paths** that fetch a single ad while you are already scrolling and splice it
into the collection held in memory. Patch only the batch path and an ad still turns up after a while,
then vanishes on the next cold start, because the restart rebuilds the collection through the batch
path alone. That is the shape of the bug, and it is worth recognising on any surface: **"it goes away
when I restart the app" means the leak is an in-memory insert, not a cache.**

A prefetch cache cannot produce it. Prefetch downloads ad creative; it never inserts anything into a
feed, and a disk cache would survive the restart rather than be cleared by it.

**Stories is a chain, not a chokepoint.** `StoryviewerBucketDataController.processBucketData`
(`LX/9to;->A00`) holds an `ImmutableList` of bucket data sources and calls `B5t` on each, feeding
every result into the next. `LX/9u3;->A0C` assembles the chain per session behind a launch-config
predicate (`LX/YJ0;->A1J`, `LX/9wU;->A00`) and MobileConfig gates, so which sources are present
varies by account — which is why the leak looked random. There are 8 implementations of `B5t`:

| Impl | What it is | Size |
|---|---|---|
| `LX/Apf;` | `AdBucketDataSourceUtil` — the placement engine. `insertedMainAdsQueue`, `uninsertedMainAdsQueue`, `organicStoryQueue`, `HP_AD`, `RTI_AD`, casts to `com.facebook.audience.snacks.model.AdStory` | 3,669 |
| `LX/awi;` | the clash resolver, which orders two ad buckets that land together | 356 |
| `LX/A2v;` | `StoryViewerMidCardDataSource` — mid-cards fetched once the viewer is open | 339 |
| `LX/gq4;` | `AdPaginatingBucketStaticInsertionDataSource` — drains a queue as the viewer paginates | 83 |

`LX/9q9;->A00` is a factory returning **either** `Apf` **or** `gq4` by flag, so both ship and both
need neutering; shipping one is a coin flip. `LX/Cgk;` is a placement-rule holder reached only from
`Apf->B5t`, so it needs no separate work. `LX/bWY;` (the interface of `Apf` and `gq4`, which extends
`Cny;`) carries the live push surface `AqM` / `Aqz` / `DtB` / `Efy` that dwell and CTA tailloads use
to reach an open viewer.

**Leave the other four alone.** `LX/9tv;` reinserts inline errors, `LX/A2s;` carries DM
lightweight-reply buckets, and `LX/9ts;` / `LX/9tt;` are 21 and 24 instructions. None inserts ads.

**Reels has three siblings of `maybeInsertAds`.** `LX/54e;` (`VideoHomeDataControllerAdsUtil`) splits
into two entry families that share no code:

| Entry | Reached from |
|---|---|
| `A06` / `A07`, the batch insert | **only** `LX/50Q;->Cwp` = `maybeInsertAds` |
| `A04` → `A02`, `maybeInsertFbShortsRealtimeIntentItem` | `LX/5YP;->onFinish()`, `LX/6S6;->run()`, `LX/6VW;->invoke()` |
| SFD ad | `LX/6S7;->run()` |
| POE ad | `LX/6SZ;->run()` |

`Cwp` has exactly one caller and `LX/50Q;` is the sole implementation of its interface `LX/CrK;`, so
that patch was always tight. The other three simply reach the shared sink `LX/53B;->A06(LX/9aJ;I)`
by themselves. `LX/6SZ;->run()` logs what it inserted under `GraphQLFeedStoryCategory.A0K`
(`SPONSORED`), which is what confirms POE items are paid ads and not an injected organic unit.

Only the inserts are blocked, not the requests that feed them (`LX/54e;->A05`, `LX/6S6;->run()`).
Stopping the requests would save data, but that belongs with the prefetch patch, and those methods
have not been checked for organic side effects.

`LX/6SZ;` holds no string literal, so it is matched on the `__redex_internal_original_name` of the
task class itself. That is sound here and unsound elsewhere: the field names **that lambda**, which is
exactly what is wanted, whereas using it to infer a lambda's *enclosing* class is the trap described
in [Anchoring](#anchoring).

### Reels ads arrive inside the page, not through an insert

The block on all four insert paths did not stop the ads. They continued at two ads after every two
reels. Every blocked method is `return-void` in the shipped dex. A logging build then showed the
cause.

No insert path ran. Items reached Reels only as whole fetched pages, and the ad was already in the
page beside the organic items. **The server puts the ad in the page.** No insertion patch can stop
this, so the patch must filter the page.

A page arrives at two levels. Only the upper level reaches the screen:

| Level | What it holds | Method |
|---|---|---|
| Controller page entry | a `List` of **section wrappers**, each holding its own list of items | `LX/50Q;` sibling of `Cwp`, shape `(Ljava/util/List;)Z` |
| Item collection | the items of one section, flattened | `(ILjava/util/Collection;)Z`, plus the listener walk `(<collection>;Ljava/util/Collection;)V` |

The filter on the collection alone looked correct. It changed nothing on the screen. A device round
on **2026-09-19** caught an ad in a page and logged `dropped 1 of 2` for it. The app then showed
that ad as the third reel. The collection is a flat copy of the items. A new collection thus leaves
the section wrapper as it arrived, and the screen reads the wrapper.

**A drop count proves that the filter ran. It does not prove that the screen changed.** Only a device
round shows the difference. This is the same lesson as "Applied" in
[Two traps this work hit](#two-traps-this-work-hit).

The patch thus filters the sections as the controller gets them. It keeps the collection filter
behind them, for anything that enters the list by another route. Both filters call
`app.andrewliang.extension.ReelsAdFilter`. The patch resolves the ad base class and gives it to that
filter, because the name is a Redex name and moves on every release.

Runtime names on 577.0.0.50.72, for recognition only:

| Runtime class | What it is |
|---|---|
| `X.721` | the section wrapper. Its item list was the field `A01` |
| `X.71s` | an organic reel |
| `X.BB0` | an ad item. It extends the resolved ad base |
| `X.Aw5` | seen once among 28 organic items, not an ad base subclass, not identified |

The patch finds the item list of a section by type and never by name. `A01` will be another name
after the next release. The patch removes the ads from the list in place. Then every other holder of
that list agrees with the screen. If the list refuses, the patch replaces the field. The patch also
removes a section that is left empty.

The patch keeps a section that it cannot read, because an unreadable section is not a proven empty
section.

**Device result, 2026-09-19, 40 seconds of scrolling:** the patch dropped 13 ad sections across 32
pages. It delivered 28 organic reels. There was no reflection fallback, no stall, and no ad on the
screen. If a page becomes empty, the app fetches the next page in about 6 ms. Thus the removal of a
whole section is safe.

### Ads fetched while a reel plays

A banner over an organic reel and a mid-roll are not items in the page. Facebook fetches them while
the reel plays. Thus the page filters cannot remove them. The patch blocks their fetches instead.

**Failed futures.** Each fetch helper returns a `SettableFuture` that failed with an `IOException`.
The callers handle it as a network error. They store no ad, and no query goes out. The patch adds a
static `failedAdFetch()` to each patched class, so each site is one branchless `invoke-static`.

| Site | Callers | Change |
|---|---|---|
| `LX/9ft;->A07`, the banner helper | the idle state `LX/7ez;->A09`, the shared ad-break fetch `LX/SQq;->A02`, and pause ads `LX/7f8;->A03` | returns the failed future |
| `LX/6lf;->A02`, the ad-break server API | `SQq.A02` and the classic in-stream `AdBreakFetchHelper` (`LX/SMj;->A05`) | returns the failed future |
| `LX/6lf;->A03`, the ad-break server API | the insertion-point lookup `LX/7f5;->A0g`, the scrubber page `LX/7fE;`, and `LX/SOw;` | returns the failed future |
| `LX/7ez;->A0B` | the idle state. It runs `FBFetchReelsVideoAdsQuery` on the GraphQL executor `LX/6dp;->A11` | the executor call becomes the helper call |

The executor has 243 callers, so the patch changes only the call in `A0B`. The helper call is also
an `invoke-static` of three code units. Thus no branch offset changes.

The failure callbacks are `LX/9fv;`, `LX/UAl;`, `LX/Uc0;`, `LX/Uc1;`, `LX/SOz;` and `LX/6lk;`. Each
one clears its in-flight flag or sets the state to `FETCHING_COMPLETED`. None of them stops playback.

**The insertion-point lookup gets a stopped tick, not a failed future.** A progress poller
(`LX/7Dr;`) asks the current state for a delay through `A0g(…, position)J`. For `-1` the poller stops.
For `-2` ("in flight") it asks again after 1000 ms.

`UnresolvedWithDeferredCardState` (`LX/7f5;`) looks up where the ad breaks of a reel go. Its failure
callback clears the in-flight flag, so a failed lookup runs again at each poll. That is one retry
each second while a reel plays.

Thus `7f5.A0g` returns `-1`, the value that the state gives for a reel with no media. The poller
stops, and the state never moves to the ad-break states. `LX/7fD;` inherits this tick. `LX/7fE;` has
its own tick, and the failed future on `6lf.A03` covers it.

The machine has no "ads disabled" state. `VoidState` (`LX/SH5;`) is the base class of several
machines. `LX/SZx;`, the only state that the tick gate `SH5.A0o()` skips, is the base class of
`AdTransitionState`.

**Anchors.** Each literal is unique in the APK:

| Literal | What it finds |
|---|---|
| `"Kicking off banner ads fetch"` | the banner helper: the last call before it in `SQq.A02` that returns a `ListenableFuture` |
| `"Kicking off video ad fetch"` | the class of the ad-break server API, in the same way |
| `"FBFetchReelsVideoAdsQuery"` | `7ez.A0B`, the only `void` method with the literal |
| `"UnresolvedWithDeferredCardState"` | the state name that `7f5.A0k()` returns |

**Device status (577.0.0.50.72).** In 3 minutes of Reels there was no ad, no retry and no crash. The
banner helper, `6lf.A02` and `7ez.A0B` did not run in that session. Thus they are verified in the dex
only. `A0B` still sends the `"reels_ad_query_send"` log event, without the query.

**To see an ad on purpose,** stay on each reel for several seconds. The server decides for each
account and country, and fast scrolling skips most fetches.

### The feed chokepoint

```
LX/1lD;->addNewEdgeToCollection(ImmutableList$Builder, GraphQLFeedUnitEdge, LX/1lR;)Z
```

`FeedUnitCollectionManager` is the one funnel that every feed edge passes through into the
`FeedUnitCollection`. It has one caller (`…$processNewStories`). It is also the only caller of
`FeedUnitCollection.addElementAtTail`, apart from one FbShorts pre-EOF injector.

A rejected edge is an outcome that the app already handles. It logs "Edge not added to FUC", and the
caller continues. Both feed patches add a guard that returns `false`, so they work in either order.

Rejection at the collection boundary leaves no gap and logs no impression. A patch that hides a
rendered row does neither. The Stories patch uses the same idea and returns the input list of the
inserter unchanged. When its own insertion gate is off, Facebook does the same. Thus that patch does
not depend on the numeric type of the ad bucket.

### Two traps this work hit

* **`p2` is `v23` in a 25-register method.** `invoke-virtual` (35c) takes 4-bit register operands.
  The assembler printed `Invalid register: v23`, but the CLI **still reported `Applied`**. In that
  state the patch does nothing. Copy the register down first with `move-object/from16 v0, p2`. Never
  trust "Applied". Disassemble the result.
* **A label inside injected smali is not moved to the new address.** For this reason the
  `instance-of` chain lives in a new static method. In a method that you build yourself, the two
  address spaces are the same.

---

## Ad surface inventory

This list comes from `__redex_internal_original_name`, kept class names, QPL literals and the binary
manifest. It includes the parts that are not worth a patch, so that nobody finds them again.

### Rendered surfaces

| Surface | Where | Shipped |
|---|---|---|
| News feed sponsored posts | `LX/1lD;->addNewEdgeToCollection` | ✅ |
| Story-viewer ads | The 4 ad sources in the `processBucketData` chain: `LX/Apf;`, `LX/awi;`, `LX/A2v;`, `LX/gq4;` | ✅ |
| Stories **tray** ads (the row on the feed) | Not traced. Every `B5t` source found so far is viewer-side | ❌ inserter not located |
| Reels and Watch feed ads | `LX/50Q;->Cwp` plus the 3 on-demand inserts: `LX/54e;->A02` (realtime intent), `LX/6S7;` (SFD), `LX/6SZ;` (POE) | ✅ |
| Reels banner ads (the product card over a reel) | The banner helper `LX/9ft;->A07`. See [Ads fetched while a reel plays](#ads-fetched-while-a-reel-plays) | ✅ |
| Other Reels ad chrome | `FbShortsAdsRootKComponent`, `ReelsAdsFloatingCtaPlugin`, `FbShortsAdsPostScrollNudge*` | Not necessary once insertion stops |
| In-stream ads (pre-roll, mid-roll, post-roll) | The ad-break server API `LX/6lf;`, the idle-state query `LX/7ez;->A0B` and the lookup tick `LX/7f5;->A0g`. See [Ads fetched while a reel plays](#ads-fetched-while-a-reel-plays) | ✅ |
| Pause ads | `PauseAdComponent`, `PauseAdUtil`. The fetch `LX/7f8;->A03` uses the banner helper | ✅ through the banner site |
| Squeezeback ads (the live video becomes smaller) | `SqueezebackAdPlugin` (`LX/TZ5;`) | ❌ not built |
| Story-viewer ad chrome | `StoryViewerAdsRootContainerComponentSpec`, `StoryViewerAdsVideoComponent`, `FBStoryAdsDelayedSkipManager` | Not necessary once insertion stops |
| Search results sponsored | `LX/KoE;->A1N`, `LX/LhI;->A00`, `SearchAdActions` | ❌ not built |
| Marketplace ads | `FBMarketplaceAdsBrowserNativeModule` (React Native) | ❌ needs a different method |
| Notifications-tab ads | The `fb_notif_ad_impression` events | ❌ insertion point not found |
| Playable ads | `PlayableAdPreloadManager`, `PlayableAdPreloadService`, `PlayableAdPreloadHost`, `NekoPlayableAdActivity` | ❌ not built |
| Conversational ads | `ConversationalAdController` | ❌ not built |

### Client-side ad vending

Facebook inserts ads on the client, not only on the server. `LX/223;` and `LX/1y1;` are
`FeedSponsoredStoryHolder`. Anchor them on `"FeedSponsoredStoryHolder.getTopValidAd"` or
`".rerankWhenAddingStory"`. `FeedSponsoredPaExecutor` and the two-value enum `LX/1yu;`
(`WAITING_FOR_MORE_SPONSORED_STORY` and `IDLE`) belong to the same machinery.

This is a second lever, and also a hazard. The vending state machine can stop in
`WAITING_FOR_MORE_SPONSORED_STORY`, so a blunt edit here can stall feed pagination. A filter at the
collection boundary is the safer default.

### Background ad work

All of these classes keep their names:

```
com/facebook/feed/push/adschannelbackgroundprefetch/FeedAdsChannelBackgroundPrefetchWorker + …InitializerAppJob
com/facebook/feed/push/adschannelemergingsurfaceprefetch/FeedAdsChannelEmergingSurfacePrefetchWorker + …InitializerAppJob
com/facebook/video/videohome/prefetching/ads/background/ReelsAdsBackgroundPrefetchWorker + …AppJob
com/facebook/stories/features/ads/prefetch/StoryViewerAdsPrefetchController + …AppInitializationController
com/facebook/addelivery/deliveryvalidation/cachedadsvalidator/NewsFeedAdCacheSyncInitializerAppJob
com/facebook/feed/ads/mlranker/MlRankerAppJob        <- on-device ad-ranking ML model
```

These have a `__redex_internal_original_name` only: `FBFeedAdsPrefetcher`,
`AdExtensionsCPDPPrefetcher`, `StoryBucketMediaPrefetchUtil`, `FBAdsIabWarmingController`,
`FBInContentAdsIABWarmingUtil`, `ReelsBannerAdsFetchHelper`.

The patch blocks the schedulers, not the workers. Two of the three workers no longer override
`doWork`. The method on `androidx.work.Worker` has a new name and returns an obfuscated `Result`,
for which there is no safe value to build. **WorkManager keeps its schedule**, so a device that ran
an unpatched build keeps the work that it enqueued before. That work stops after you clear the app
data.

### Telemetry

| What | Class | Shipped |
|---|---|---|
| Screenshot-of-ad detection | `com/facebook/ads/AdsScreenshotDetector`, `…/screenshot/AdsScreenshotController` | ✅ |
| App-install attribution | `com/facebook/feed/platformads/AppInstallService` (`doHandleIntent` is kept), `AppInstallTrackerScheduler` | ✅ |
| On-device OCR of ad creative | `com/facebook/ads/visualquality/AdVisualQualityEngine`, `analyzer/OcrPreprocessor` | ❌ suspend-only entry points, and idle once the ads are hidden |
| Privacy Sandbox attribution | `MeasurementManagerUtil$registerAdImpressionSource`, `…ClickSource` | ❌ lambda-only |
| Ad ID reporting | `AdvertisingIdLogger`, `AdvertisingInfoUtil` | ❌ lambda-only |
| Impression stores and loggers | `AdImpressionStore`, `PigeonFeedUnitSponsoredImpressionLogger` (`LX/7Iy;`) | ❌ its clean entry marks an impression as *already logged*, so a no-op invites repeat logging |

### Manifest components

```
com.facebook.ads.internal.ipc.AudienceNetworkRemoteService     <- exported, process :adnw
com.facebook.ads.internal.ipc.AudienceNetworkRemoteActivity
com.facebook.ads.internal.ipc.AudienceNetworkExportedActivity
com.facebook.ads.AudienceNetworkActivity
com.facebook.audiencenetwork.AudienceNetworkService
```

Through these components the Facebook app is the **ad server for other apps** that embed the
Audience Network SDK. `[Ad] Disable Audience Network` disables them.

### Injected units that are not ads

These are not paid ads, but they use the same chokepoint. All the model classes keep their real
names, so `instance-of` is enough. `[Feed] Hide suggested and promoted posts` drops them:

```
GraphQLPagesYouMayLikeFeedUnit / Paginated… / Creative… / GraphQLPYMLWithLargeImageFeedUnit
GraphQLPagesYouMayFollowFeedUnit / GraphQLPagesYouMayAdvertiseFeedUnit / GraphQLPymgfFeedUnit
GraphQLQuickPromotionFeedUnit / …NativeTemplateFeedUnit
GraphQLEndOfFeedUpsellCustomNTFeedUnit / GraphQLExploreFeedUpsellNTUnit
GraphQLGreetingCardPromotionFeedUnit / GraphQLStoryGallerySurveyFeedUnit
GraphQLBusinessPageReviewFeedUnit / GraphQLHoldoutAdFeedUnit
```

Two are excluded. `GraphQLFriendsLocationsFeedUnit` is a real feature. People You May Know has no
container feed unit with a kept name. Only the item types are reachable, and to drop those does not
remove the row.

The nag interstitials of Facebook are the **Quick Promotion and megaphone** system
(`MegaphoneController`, `MegaphoneStore`, `MegaphoneQueue`, `MegaphoneFetcher` at `LX/2iY;`,
`QpMegaphoneWrapperComponent`). The patch above covers the ones in the feed. Only the interstitial
path has no anchor.

### Out of scope

* **`com/facebook/adinterfaces/*`, `AdCenterFragment`, `adspayments`, `adpreview`.** This is the
  *advertiser* side, which boosts a post or manages a campaign. There is nothing to block.
* **`AdsReconsiderationHub*`.** The user opens this surface to see the ads they interacted with.
* **Sponsored messages.** Only a deprecation heartbeat is left.
* **`com/facebook/camerarollprocessor/advancedpro/*`.** The name matches a search for "ad", but this
  is camera-roll ML and is not related.

---

## Link handling

Facebook opens each tapped link in its own browser. `[General] Open links in external browser`
gives the URL to the system instead.

### Why the menu action of the browser is the wrong thing to call

The browser has an **Open with** menu action, and that action does the correct thing. It builds an
`ACTION_VIEW` on the URL and starts it. But a patch cannot call it.

The action is one branch of `LX/dLG;->A01`, a dispatcher of 588 instructions that each menu item
shares. Its intent builder `LX/cyp;->A00(LX/eYb;, LX/eXt;)` takes the chrome and the state objects
of the browser. These objects exist only after the browser is built. Thus a call to the action must
start the browser and then close it. The user sees a flash, and a dead entry stays on the back
stack. Copy what the action builds, and hook earlier.

### There are two in-app browsers, and the links go to the newer one

`com.facebook.browser.lite` is not the whole story. Facebook also ships
`com.facebook.browser.`**`litev2`**. The central launcher `handleByBrowserLite` (`LX/8A2;->A03`,
about 2,800 instructions) **sets the component of the launch intent** to
`litev2.lite.BrowserLiteDIActivity`. Nothing else can redirect an explicit component bind. Thus a
hook on the original browser alone never runs for an ordinary link.

The first device round proved this at a high cost. The hook was correct in the build, and each link
still opened in the app. Both browsers have a hook now. **At each version bump, read the component
that `handleByBrowserLite` binds. Do not assume that the old class is still the live one.**

### The chokepoint

Each entry point goes through the activity, and **the URL is the data of the launch intent**, not
an extra. The v1 sites do `new Intent(ctx, BrowserLiteActivity.class).setData(uri)`, and
`handleByBrowserLite` reads and rewrites `Intent.getData()` throughout. Thus one hook for each
browser covers the feed, the comments, the Pages and the story link stickers.

`onCreate` is 16 instructions. `invoke-super` is at index 3, and the browser is built at index 10
(`LX/dSq;->A0A`). This leaves a clean gap for the hook. The hook must go **after** the super call.
If it goes before, Android answers with `SuperNotCalledException`.

| Class | Relationship | Patched |
|---|---|---|
| `litev2.lite.BrowserLiteDIActivity` | **the browser that ordinary links reach** | ✅ `onCreate` + `onNewIntent` |
| `litev2.lite.BrowserLiteDITransparentActivity` | **extends** it | ✅ for free |
| `lite.BrowserLiteActivity` | the original, which some surfaces still reach | ✅ `onCreate` + `onNewIntent` |
| `lite.BrowserLiteInMainProcessBottomSheetActivity` | **extends** it | ✅ for free |
| `DMASecureBrowserActivity` | extends `FragmentActivity` directly | ❌ on purpose — the separate browser for the EU Digital Markets Act |

All three run in the main process. Thus the extension has no cross-process problem.

`onNewIntent` needs its own hook, and that hook must read the **parameter**, not `getIntent()`.
`getIntent()` still returns the intent that started the browser, which is the previous link.

### The URL is the link shim, not the link

The third device round found the last fault. Each patch applied, the hook was correct in the dex,
and each link still opened in the app. `dumpsys` gave the answer:

```
Intent { act=android.intent.action.VIEW
         dat=https://lm.facebook.com/l.php?u=https%3A%2F%2Fwww.example.com%2Fnews%2F1.htm&h=AUAN...
         cmp=com.facebook.katana/com.facebook.browser.litev2.lite.BrowserLiteDIActivity }
```

Facebook does not give the browser the link that the user tapped. It gives it the **link shim**,
which is the click tracker of Facebook. The shim is on `lm.facebook.com`, and that host ends with
`.facebook.com`. Thus the host test of the extension reads each outbound link as internal, and each
one stays in the app. The hook ran for each link. The host test sent each one back.

`unwrapLinkShim` now reads the `u` parameter before the host test, and the rest of the work uses
that destination. The `/flx/warn/` interstitial has the same shape and the same `u`. The shim does
not go out to the browser. Thus the browser makes one request and not two, and Facebook does not
learn that the link opened. The destination still holds the `fbclid` parameter of Facebook, because
that parameter is part of the destination URL.

Two things make this fault hard to see:

* An app link never reaches the browser. A YouTube link and a Threads link opened in their own apps
  before the correction, and only the other links were wrong.
* The browser activity is not exported. `am start -n …/BrowserLiteDIActivity` answers
  `SecurityException: Permission Denial`, thus the shell cannot make the fault. Tap a link on the
  device. Then read the intent of the activity with `adb shell dumpsys activity activities`.

### Why the extension needs no package filter

An `ACTION_VIEW` on an ordinary URL cannot come back into Facebook and make a loop, because **each
`http` and `https` intent filter of Facebook is limited to a host that Facebook owns**
(`www.facebook.com`, `work.meta.com` and more). At a version bump, decode the manifest with
`aapt2 dump xmltree --file AndroidManifest.xml base.apk` and read the filters again.

Thus the extension needs no package enumeration, and it needs no `<queries>` manifest entry. An
implicit `ACTION_VIEW` to a browser is exempt from package visibility. Compare
`[Fix] Restore location maps via MicroG-RE`, which does add one entry, because `createPackageContext`
names a package.

The hosts of Facebook stay in the app on purpose. Login, checkout and the web pages of Facebook
need the JavaScript bridges and the autofill of the in-app browser. No other browser has them.

### Read an injected block whole

The second device round crashed at each link tap:

```
VerifyError: ... BrowserLiteDIActivity.onCreate ...
[0xD] register v0 has type IntegerConstant but expected Reference: android.content.Intent
```

The `move-result-object` after `getIntent()` was absent, thus the redirect got the integer that was
in `v0`. The fault came in with the rework for two browsers, which moved the intent load into a
parameter. The `move-result-object` did not move with it.

**`BranchSweep` cannot find this fault.** It makes sure that each branch, try range and handler
lands on an instruction start. It knows nothing about the *types* of the registers. Only the
verifier of ART knows them, and that verifier runs on the device, at class load.

The check that must have found the fault hid it. The injected block was read with
`grep -E "redirect|if-nez|invoke-super"`, and a filter of that shape cannot show an absent
instruction. **Dump the whole injected block, and read each line.** Look for a non-void `invoke-*`
that has no `move-result*` after it.

### Keep the trace section balanced

`onCreate` opens a trace section in its prologue (`LX/0Cv;->A00(I)I` into `v3`) and closes it at the
tail (`LX/0Cv;->A07(II)V`). A hook that returns early leaves that section open. Thus the redirect
branch jumps to the **marker load that feeds the closing call**. It does not jump to `return-void`,
and it does not jump to the call itself. A jump to the call closes the section with the boolean of
the hook in place of the marker.

The patch finds that instruction through the pair of static calls on the tracer class, and not
through an index. `v3` stays untouched. `onNewIntent` has no such pair, and it returns.

---

## Media download

Facebook ships a save feature for media and offers it only on the content that you posted. This
bundle saves media itself instead of asking Facebook to do it.

The story half ships. The video half does not, and the reason is now known. For content that you
did not post, Facebook opens a **different viewer**. That viewer has no download row in its code
at all.

> ⚠️ **Two statements that used to be in this file were wrong**, and both were disproved on a
> device. They are corrected below, and the corrections are kept visible because each one cost a
> round. The first said the reel sheet leaves its block at a null check. It does not; it adds the
> row unconditionally. The second said no patch can add the address. The address is on the player
> and is fetchable. See [Video and reels](#video-and-reels-what-four-device-rounds-showed).

### Stories: what ships

`[Stories] Download any story` has two halves, because Facebook limits the feature twice.

**The item.** The "More" menu of the story viewer is
`com.facebook.stories.viewer.ui.buckets.regular.topbar.menu.StoryViewerMoreButtonCallback`, a kept
name. It asks exactly **one** capability question before it offers the save item. Everything after
that question is unconditional, and the surface enum it reads next only decides the label. The
question is a predicate on the capability object: it takes nothing, answers a boolean, and the menu
caches the answer in a field. For the regular viewer it returns `StoryBucket.A0k()`, which means
"this is my own story", and the predicate has **exactly one caller**. So a forced answer changes the
save and nothing else. Do **not** force `StoryBucket.A0k()` itself — `shouldShowViewCount`,
`isFeedbackBarSupportedForBucket` and other capabilities read it too.

The patch names neither the predicate nor its class. The action that the menu creates reports the
event `"save_story_attempted"`, and that event is the anchor. The event is in three methods and only
one is a `void` with one parameter. The builder is then the only method on the kept class that
creates that action. The capability is then the only call in that builder that takes nothing and
answers a boolean, apart from `Boolean.booleanValue`.

**The download.** Forcing the item is not enough, and this is what issue #110 reported. The tap
handler of the action checks the story for licensed music **first**:

```
LX/agk;->Dy7(LX/a9v;)V
  1: invoke-virtual StoryCard->A1Q()Z          <- a content predicate
 10: TreeJNI->getIntValue(#-2082673550)
 12: const/16 v0, #15000                       <- a duration ceiling
 13: if-gt ... -> the branch that builds the dialog
 16: LX/bUp;->DtL(StoryCard;ZZ)V               <- the plain save
```

On a story with music it shows a copyright warning and then saves nothing, whatever the user
answers. It does not even save a copy without the sound. The check is the first thing the method
does, so there is nothing to route around. The patch prepends its own download and returns, which leaves
Facebook's body unreachable — the same shape as `facebook/shared/Neuter.kt`. The extension answers
whether it took the job, and Facebook's body still runs when it declines, so the item never does
nothing at all.

The action holds what the download needs, and both are found **by type**: the only
`Landroid/content/Context;` field and the only `Lcom/facebook/stories/model/StoryCard;` field. The
handler is the only method on the class besides its constructor. The action is constructed in
**exactly one place** in the whole dex, so one interception covers every story surface. (The
reference bundle had to patch four story surfaces on 573. Re-run that `xrefc` on a bump.)

**Device-confirmed 2026-09-22** on a re-signed 577.0.0.50.72. Three video stories with music saved
with no warning, `save finished: OK` each time, no crash. The files are real. They land in
`/sdcard/Movies/Facebook/FB_VID_*.mp4` at 200–300 KB. One pulled back holds `avc1` and `mp4a`:
one video track and one audio track, muxed and playable with sound.

### The downloader in the extension

Five classes in `app.andrewliang.extension`. Two of them hold **no Android type at all**, so the
ranking and the fetch compile and run under `javac` alone. `work/Renditions.java` checks the
ranking against a fixed set of addresses, with no device. The ranking is the part a device cannot
show. A saved file looks the same whether the best address was chosen or the first one read.

- **The address is chosen by value, never by field name.** Facebook renames the fields every
  release while the addresses in them keep their shape. The ranking refuses inline XML, `.mpd` and
  `.m3u8`, refuses a thumbnail, prefers a progressive file, then ranks by the short side in pixels.
- **Ties break on the text of the address.** `getDeclaredFields()` has no defined order on ART, so a
  tie broken by the order the fields were read is a different answer on a different device.
- **The walk is fenced.** Bounded depth, a node budget, an identity-visited set and a class-prefix
  allowlist. Without the allowlist it reaches a `View`, then a `Context`, then the whole app, on the
  thread that draws.
- **No static holds the last address seen.** Facebook prepares the reels that come next: 19
  `VideoPlayerParams` and 3 distinct video ids were built in the first seconds of one run. Anything
  remembered rather than passed in saves the wrong video and still reports success.
- **Nothing is queued.** The `oh` and `oe` parameters are signed and last hours, so the fetch starts
  on the tap. That is why `DownloadManager` is the wrong tool here despite being the obvious one.
- **The type comes from the server**, and the type decides the file name. Facebook's own save writes
  AVIF bytes into a `.jpg`, which leaves the gallery unable to draw a thumbnail. A copy of that
  behaviour copies the fault.

### The fields of the source name themselves

`com.facebook.video.engine.api.VideoDataSource` is a kept class name whose fields are renamed every
release. It also carries `EVr`. That method is a debug dump, and it pairs each field with its **real** name:

```
 2: iget-object v0, v3, VideoDataSource->A08:Landroid/net/Uri;   5: const-string "videoUri"
 9: iget-object v0, v3, VideoDataSource->A07:Landroid/net/Uri;  11: const-string "videoHdUri"
15: iget-object v0, v3, VideoDataSource->A06:Landroid/net/Uri;  17: const-string "captionsUri"
```

So a patch resolves `videoHdUri` and `videoUri` at patch time with no letter written down. It also
avoids a trap. The **third** `Uri` on the object is the subtitles, and "take any Uri" downloads
it without complaint.

Two cautions. The pairing order is **not stable between classes**. `VideoDataSource.EVr` emits
`iget` then `const-string`. `VideoPlayerParams.EVr` emits `const-string` then `iget` for
`videoId`, and it also carries labels such as `"videoDataSourceNull"` that are not field names.
So pair within a small window in either direction, and fail loudly if a name does not resolve.

Do not use `EVr` for the other hop. The `VideoDataSource` on a `VideoPlayerParams` is the only
field of that type, and the type is a kept name, so resolve it by type.

Measured on this build, for one reel:

| Field | Real name | Rendition | Bitrate |
|---|---|---|---|
| `A07` | `videoHdUri` | `dash_h264-basic-gen2_720p` | 1.23 Mbps |
| `A08` | `videoUri` | `sve_sd` (360p) | 0.30 Mbps |
| `A0C` | `abrManifestContent` | the DASH manifest, inline XML | — |

The tag and the bitrate are in the address in **plain text**, and also inside the base64 `efg`
parameter. One address taken off the device was fetched from an unrelated machine with **no headers
at all**: HTTP 200, `video/mp4`, holding `avc1` and `mp4a`.

### Video and reels: what four device rounds showed

Nothing ships. The mechanism below is the part to keep, because two earlier readings of it were
wrong and each cost a round.

**The gate is not a flag. It is which viewer opens.**

| What is opened | Sheet configuration | Download row |
|---|---|---|
| Your own profile video | `LX/Sbp;` | built — the sheet builder runs with nothing forced |
| Another account's profile video | `LX/Sbr;` | never — the builder is not reached |
| The Reels tab | `LX/Sbr;` | never |

`LX/Ti9;->A07()Ljava/util/List;` sorts fifteen configurations and decides which sections a sheet
has. Two of them, `LX/Sbq;` and `LX/Sbp;`, carry a boolean that means "this is mine" and answer with
an empty list when it is false. Forcing that boolean looks like the answer and **is not**: for
content you did not post the app never builds an `Sbp` at all. It builds an `Sbr`, and `Sbr` reaches
no download section. `LX/UPK;`, the download row, is constructed in exactly one place — inside
`LX/Tkb;->A00` — and on a device that method never ran for another account's video.

What the earlier readings got wrong, in order:

1. *"The builder leaves at the null checks above the flag."* No. Those checks branch **forward into**
   the row code. The row is added unconditionally; the flag and the media subtree only choose which
   subtitle the row shows. This came from reading a dump that prints no branch targets, so every
   `if-*` looked like an exit.
2. *"Then the flag one level up is the gate, and forcing it shows the row."* No. That flag is on the
   wrong configuration for the surface that matters, as the table above shows.

**What did work, and is the route this patch takes.** The reels sidebar is the strip of buttons
beside a reel. It is a Litho component, and it holds the item's player on a field:

```
LX/AyH;->A1N(LX/3Sr;)LX/3S3;          the UDD sidebar, 1102 instructions
  16: iget-object v0, v1, LX/AyH;->A06:LX/56N;             per-item player params
 178: iget-object v1, v1, LX/56N;->A03:VideoPlayerParams;
 180: iget-object v1, v1, VideoPlayerParams;->A0d:VideoDataSource;
1052: invoke-static/range LX/B34;->A01(..., ArrayList, ArrayList, List, ...)  the buttons
1084: invoke-static/range LX/B34;->A00(...)                                   the sidebar
```

So the tapped item's source is reachable **from a field of the component**. That gives per-item
binding by construction, with no static state and no match by video id. The button collections
are the three parallel lists that `B34.A01` takes.

**The button needs nothing invented.** The sidebar builds its own buttons through one factory, and
every part of it takes a plain value:

```
LX/2Ui;->A00(FbUserSession, LX/22J;, LX/2wz;, LX/2Rb;, LX/2Rb;, LX/Ca5;,
             Boolean, Boolean, Object, String, String,
             Function1, Function1, Function1, Function1, I, Z, Z, Z)LX/B2s;
```

`LX/2wz;` and `LX/2Rb;` are `<init>(Ljava/lang/String;Lkotlin/jvm/functions/Function1;)V` — a label
and a handler. `LX/Ca5;` is the icon, supplied as `LX/B2r;-><init>(LX/1XR;)V`, and `LX/1XR;->A82` is
the download icon that Facebook's own download row already uses. One of the two `String` slots is a
test id, and the sidebar's existing buttons pass `"like_button"`, `"comment_button"` and
`"share_button"` there. So a label is a `const-string`, not a resource, and nothing has to come from
the downloaded string pack.

The handler is the one place this bundle can do better than the reference patch. That bundle had no
extension, so it hijacked an existing multiplexed lambda of the app and its case numbers. The
`.mpe` here already carries the Kotlin standard library, so a plain Java class in the extension can
`implement Function1` and be passed straight in.

The whole shape matches the reference patch on 573, position for position. That is worth a record,
because the mapping can be derived the same way on the next bump:

| 573 | 577 |
|---|---|
| `LX/2iZ;->A00(...)LX/9yY;` | `LX/2Ui;->A00(...)LX/B2s;` |
| `LX/2vk;`, `LX/2QZ;` label and handler | `LX/2wz;`, `LX/2Rb;` |
| `LX/9yX;-><init>(LX/1Vq;)` icon | `LX/B2r;-><init>(LX/1XR;)` |
| `LX/9vm;->A1K(LX/3QZ;)LX/3Pu;` | `LX/AyH;->A1N(LX/3Sr;)LX/3S3;` |

What is still unresolved is only implementation detail: which of the three lists takes the button,
and the register plumbing at the injection point. `Fb dump` prints no operand registers for an
`invoke`, so that needs a disassembler that does.

**The cost, stated plainly.** This injection reads locals of an 1100-instruction obfuscated method by
register number, and anchors on an 18-parameter signature. Facebook releases about every two
weeks, so it needs a new derivation on most of them. It can also fail quietly rather than loudly.

### A reel opened from the story tray saves at 360p, and that is the source

Measured, so that nobody re-investigates the ranking for it. A reel reached through the story tray
is saved by the **story** half. That half reads the card rather than the player, and the card
carries four addresses of which only two are distinct:

| Candidate | What it is |
|---|---|
| `…_n.jpg` (1080p), twice | the poster image |
| `…_n.mp4` (360p), twice | the video, and the only one |

There is no high quality video address on the card at all. The ranking picks the only video there
is, so it is right; the ceiling is the source. A reel reached through the reels tray is saved by the
sidebar button instead, which reads `videoHdUri` off the player, and comes back at 720p.

To close the gap, the story half needs a route to the player. Its action does not have one: it
holds a context and a card and nothing else. The shape that works is a small map from video id to
source, filled where `VideoPlayerParams` is built, because that object carries both the id and the
source. It is keyed per item, so it is not the "most recent source" trap.

**Not built**, and worth one probe first. The 720p measured so far was reels-tray playback. If
Facebook streams the story tray at 360p, the player holds nothing better either.

### Anchors that survive a bump

None of these is a Redex name.

| Anchor | What it finds |
|---|---|
| `EVr` on `VideoDataSource` | the real name of every field of the source, at patch time |
| `com.facebook.video.engine.api.VideoDataSource` | what the player streams. Fields by `EVr` or by value, never by letter |
| `"save_story_attempted"` | the action behind the story save item, and so its tap handler |
| `getMedia` on `StoryCard` | a kept method name; the media of a story |
| `updateState:UDDSideBarComponent.onUpdateUfiState` | the reels sidebar component |
| `com.facebook.fbshorts.viewer.ui.config.api.VDDViewerUIConfig` | the sheet configurations, and through their shared superclass the section builder |
| `"reels_overflow_menu"` | the sheet builder that holds the download row |
| `"fds_control_download_video"` | the download row itself |
| `"tap_on_profile_viewer_save_video"` | the tap handler of that row |
| `"FB_VID_"` | the method that names a saved video file |
| `getBooleanValue`, `getCachedNullableString` | kept names on `TreeJNI`, which is how every gate reads the tree |

### The lessons, which cost four rounds

- **A dump without branch targets cannot tell an exit from a jump forward.** `work/Fb.java`'s
  `dump` prints no targets, and that alone produced a wrong finding that sat in this file. Decode
  the target before calling an `if-*` an exit.
- **Forcing a flag proves nothing until you know which screen reads it.** Two rounds went to a
  boolean that was never read on the surface under test. Log the runtime class of the object that
  holds it first.
- **Force and observe in the same build.** One round forced without reporting and the next reported
  without forcing, so neither could say why a row was still missing.
- A string tag and a `Menu.add` do not prove a menu builder is *the* builder: Facebook keeps whole
  old menu implementations in the dex, and a search by name finds them first. One probe run costs
  less than the device rounds it replaces.
- **It is a different claim.** Every other patch here unlocks something Facebook ships and gates in
  its own process. Saving media that the server chose not to offer belongs in the patch description,
  not in a footnote.

## Risks

* **Play Integrity.** Facebook sends attestation results to the servers of Meta
  (`performPlayIntegrityAttestation…`, `caa_play_integrity_attestation_result`,
  `zca_play_integrity_last_attested_token`). No code in the client acts on the result. But the
  signal can show its effect after some days, not in one session. Use a throwaway account first.
* **There is no check of the app signature.** The APK holds no signing-cert hash for Facebook. The
  checks that do exist (`"Incorrect signature for package "`, `LX/lZa;->A00`) are **cross-app SSO**
  against other Meta apps. Thus account SSO with Messenger and Instagram breaks on any re-signed
  build.
* **Audience Network reaches outside Facebook.** A test of Facebook shows that Facebook is correct.
  It does not show that the reward flow in another app survives the loss of the bridge.
* **Release cadence.** Facebook releases about every two weeks, which is about 6 times the rate of
  LINE. The anchors can survive most bumps, but each bump needs a new test against a 151 MB
  download.
* **A/B tests.** Ad delivery is different for each account. Thus "no ads appeared" in one session is
  weak evidence.

---

## Recipe

```bash
# unpack
unzip -q <bundle>.apkm -d work/fb-extract
unzip -q work/fb-extract/base.apk 'classes*.dex' -d work/fb-extract/dex

# search (scratch dexlib2 tool Fb.java: classes | strhost | methods | sig | dump | xrefm |
# xrefc | xreff. Redex.java prints __redex_internal_original_name)
#
# Fb.java reads its dex directory from the FBDEX ENVIRONMENT VARIABLE. A --dex argument is
# accepted and silently ignored, so it falls back to the unpatched tree and a patched site then
# looks untouched -- which reads exactly like a fingerprint that failed to match.
export FBDEX=work/fb-extract/dex
java -cp .:smali-dexlib2.jar Fb strhost 'FeedUnitCollection.addElementAtTail'

# A whole-dex index, once, then grep it instead of rescanning per query. ~8s, 1.8M lines.
java -cp .:smali-dexlib2.jar Fb sig '' > /tmp/allsig.txt

# `dump` prints NO branch targets and no invoke operand registers. Do not call an `if-*` an exit
# on its evidence -- that mistake put a wrong finding in this file. Decode the target first.

# apply, then ALWAYS disassemble the result
java -jar work/morphe-desktop-*.jar patch -p patches/build/libs/patches-*.mpp \
  --exclusive -e "<name>" -f --unsigned -o work/fb-out.apk work/fb-extract/base.apk
unzip -o -q work/fb-out.apk classes.dex -d verify && java -cp .:smali-dexlib2.jar Fb dump ...

# whole-dex branch validity (work/BranchSweep.java). It takes seconds and covers every patch.
java -cp .:smali-dexlib2.jar BranchSweep verify/classes*.dex
```
