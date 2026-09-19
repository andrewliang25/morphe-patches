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
| `[Stories] Download any story` | The one capability check in `StoryViewerMoreButtonCallback` | `const/4` into the register its `move-result` wrote, so the cached capability reads true |

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
| Reels ad chrome | `FbShortsAdsRootKComponent`, `ReelsBannerAdsNativeComponent`, `ReelsAdsFloatingCtaPlugin`, `FbShortsAdsPostScrollNudge*` | Not necessary once insertion stops |
| In-stream ads (pre-roll, mid-roll, post-roll) | `AdBreakStateMachineImpl`, `AdBreakFetchHelper`, `UnifiedAdBreakController`, `InstreamAdFetchUtil` | ❌ no anchor |
| Pause ads | `PauseAdComponent`, `PauseAdUtil` | ❌ no anchor |
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

Facebook ships a complete save feature for media. It has the menu item, the label, the icon, the
click handler and a general downloader. Facebook offers the feature only on the content that you
posted. The half for stories ships here. The half for video went through three attempts. Each
attempt applied cleanly, and a device round then killed it. The measurement is the part to keep.

### Stories: what ships

`[Stories] Download any story` adds the save item of Facebook to the menu of any story.

The "More" menu of the story viewer is
`com.facebook.stories.viewer.ui.buckets.regular.topbar.menu.StoryViewerMoreButtonCallback`, which is
a kept name. It asks exactly **one** capability question before it offers the save item. Everything
after that question is unconditional. The surface enum that it reads next only decides which label
the item gets.

The question is a predicate on the capability object of the menu. It takes nothing and answers a
boolean, and the menu caches the answer in a field. For the regular viewer it returns
`StoryBucket.A0k()`, which means "this is my own story". The predicate has **exactly one caller**.
Thus a forced answer changes the save and nothing else. Do **not** force `StoryBucket.A0k()` itself.
`shouldShowViewCount`, `isFeedbackBarSupportedForBucket` and other capabilities read it too.

The patch names neither the predicate nor its class. The menu class is a kept name. The action that
the menu creates reports the event `"save_story_attempted"`, and that event is the anchor. The event
is in three methods, and only one of them is a `void` with one parameter. The builder is then the
only method on the kept class that creates that action. The capability is then the only call in the
builder that takes nothing and answers a boolean, apart from `Boolean.booleanValue`.

**Why this half works and the video half does not.** The save code reads the media address of the
story itself, which is the address that the viewer already plays. Its errors are `MEDIA_URL_EMPTY`
and `VIDEO_FILE_MISSING`. That address must be present, or the story does not appear at all. Thus
nothing can withhold it.

**Device-confirmed 2026-09-19** on a re-signed 577.0.0.50.72. The item "Save photo" appears on the
story of another account. It writes the full-size picture to
`/sdcard/Pictures/Facebook/FB_IMG_*.jpg`. The bytes are AVIF under a `.jpg` name, which is the
naming of Facebook and not an error. A control build without the patch offers no save item on the
same kind of story. A **video** story is not tested.

### Video and reels: three paths, all measured, none shipped

Facebook holds **three** separate download paths for video, and each one has its own gate. Work on
this went through all three. The patch for each applied cleanly. Every forced check is a `const` in
the shipped dex. The download row **never appeared** on another account's video.

| Path | Surface | Gate | Result |
|---|---|---|---|
| Old `android.view.Menu` builders `LX/2xZ;->A0i` and `LX/Sct;->A0i` | none | 5 ownership checks | The code does not run at all |
| `MediaGalleryMenuHelper` (`LX/8R4;`) | the photo and video viewer | `A03` answers the address, or null | Forced. No video of another account opens in this viewer |
| The reel sheet (`LX/Tkb;->A00`) | reels and feed video | a tree flag, then an address | Forced. The block exits before the flag |

**All three stop at the same wall, and it is not the flag.** The measurement below is what settles
it. It took four device rounds to reach. Three of those rounds went to gates that were never the
cause.

#### What the probes said

The first probe marked the entry of the download block in both old builders, every null check inside
it, and the `Menu.add` that ends it. On a device it logged **nothing**: not for another account's
reel, not for a feed video, and **not for your own reel, where Facebook does show "Download reel"**.
A surface that shows the row without running the code is a surface built somewhere else, so those
two builders are dead code. The label resource that they pass to `getString` (`0x7f147339`) belongs
to those two methods and nothing else. The tag `"DOWNLOAD_VIDEO"` is in five methods, and all five
are the same old pair and its listeners.

The second probe logged a **stack trace** from a tap on "Download reel" on an own reel. That named
the live path in one run:

```
X.1SJ.onClick → X.TMH.A1R → X.UOz.DFH → X.UEH.A02 → X.UEH.A03
              → X.UBD.A00 → X.ajB.A04 (HTTP GET) → X.OKw.A02 (the file)
```

None of it passes through the save entry point that the gallery uses. Thus a probe on that entry
point stayed silent while the app wrote a file. `LX/OKw;->A02` names the file, and its string
`"FB_VID_"` is the anchor that found the whole chain.

The third probe marked the gate of the reel sheet and logged the address that the sheet builds:

| Reel | The gate | The address |
|---|---|---|
| Your own | reached | `https://scontent…/…mp4?…oh=…&oe=…`, 720p at 526 kbps |
| Another account's | **never reached** | none |

On another account's reel the builder leaves the block **before** it reads the flag, at the null
checks on the media subtree above it. The flag was never what hid the row.

#### Why no patch can add the address

Each path asks the media tree for a download address, and for content that you did not post the
answer is absent. The old builders stop at `LX/KDM;->A00()`, which returns null. The gallery method
answers null. The reel sheet leaves its block at the same kind of check. A forced null cannot
replace data that never arrived: it reaches `Uri.parse`, or the save code fetches an address that is
not there.

Nothing can build the address either. A Facebook media address carries server-issued `oh` and `oe`
signatures, so no code in the client can derive one from a video id.

**Thus the permission flag is the wrong target on every path, and this is the finding worth keeping.**
A patch on the abandoned branch `feat/facebook-download-video` forces the flag at all seven places
that read it. The row stays hidden, because the data that it needs is absent.

#### The address that does exist: what the player streams

The video plays, so an address must be in the process. It is, and it is usable. The player keeps it
on `com.facebook.video.engine.api.VideoDataSource`. Redex **keeps that class name** and renames its
fields, so a probe must read the fields by reflection.

For another account's reel the player holds progressive MP4 addresses, and not only a manifest:

| Field | Rendition | Bitrate |
|---|---|---|
| `A07` | `xpv_progressive … h264-basic-gen2_720p` | 1.10 Mbps and 3.95 Mbps |
| `A08` | `xpv_progressive … h264-basic-gen2_360p` | 0.61 Mbps |
| `A0C` | the DASH manifest, as inline XML | not needed |

A test took one `A08` address off the device and fetched it from an unrelated machine, with no
headers:
**HTTP 200, `video/mp4`, 213,789 bytes, and the file holds `avc1` and `mp4a`** — one video track and
one audio track, muxed. Two risks usually kill this idea: a manifest in place of a file, and a video
track without sound. Neither occurs here. Quality is not a problem either. The own download of an
own reel was 720p at 526 kbps, and `A07` is the same size or better.

#### Why it is still not shipped

The idea is feasible. It is not cheap, and these are the costs, in the order that matters:

* **The app preloads.** The app built six sources in about 15 seconds of scrolling, because
  Facebook prepares the reels that come next. A patch that saves "the last source built" saves the wrong
  video some of the time. The save must read the source of the item that the sheet belongs to, and
  that is unproven work.
* **The rendition needs a rule.** Prefer `A07`, fall back to `A08`, and do nothing when neither is
  there. Without the rule the patch silently saves 360p.
* **The row does not exist.** Each row of the sheet is an `LX/UPK;` around an action, so a patch can
  build one. But its label comes from a downloaded string pack, which the app does not keep in
  `resources.arsc`, and its icon is a resource id.
* **The anchors move.** `LX/Tkb;->A00`, `LX/UPK;`, `LX/UOz;` and the **field offsets** of
  `VideoDataSource` all change with a release about every two weeks. Compare the one-instruction
  patches elsewhere in this bundle, which survive a bump untouched.
* **The address expires.** The `oh` and `oe` parameters are good for hours, so the save must happen
  at once and nothing can be queued.
* **It is a different claim.** Every other patch here unlocks something that Facebook ships and
  gates in its own process. This one takes media that the server decided not to offer. That belongs
  in a patch description, not in a footnote.

**Verdict: recorded, not built.** Read *"Patchable" is not "worth patching"* in `CLAUDE.md` before
starting it again.

#### Anchors that survive a bump

These are the names that found everything above, and none of them is a Redex name:

| Anchor | What it finds |
|---|---|
| `videoDownloadMediaAction` | the listener of the gallery row. One method has the name, one method calls it |
| `"FB_VID_"` | the method that names a saved video file, and through it the whole reel save chain |
| `"save_story_attempted"` | the action behind the story save item |
| `"end_screen.more_options_settings"` | the more-options model of the video player |
| `com.facebook.video.engine.api.VideoDataSource` | what the player streams. Fields by reflection, never by name |
| `getBooleanValue` and `getCachedNullableString` | kept names on `TreeJNI`, which is how every gate reads the tree |

#### The lesson, which cost two patches

A string tag and a `Menu.add` do not prove that a menu builder is *the* builder: Facebook keeps
whole old menu implementations in the dex, and a search by name finds them first. A forced flag does
not prove a gate is *the* gate either. One probe run costs less than the device rounds that it
replaces. Three searches missed the live path, and a stack trace from one tap named it. Measure
first. This is the same trap as the earlier conclusion that reels have no download code: a search
for `DOWNLOAD_REEL` found nothing, which proved only that the feature is not *named* after the
surface.

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

# search (scratch dexlib2 tool Fb.java: classes | strhost | methods | dump | xrefm | xrefc |
# fields. Redex.java prints __redex_internal_original_name)
java -cp .:smali-dexlib2.jar Fb strhost 'FeedUnitCollection.addElementAtTail' --dex work/fb-extract/dex

# apply, then ALWAYS disassemble the result
java -jar work/morphe-desktop-*.jar patch -p patches/build/libs/patches-*.mpp \
  --exclusive -e "<name>" -f --unsigned -o work/fb-out.apk work/fb-extract/base.apk
unzip -o -q work/fb-out.apk classes.dex -d verify && java -cp .:smali-dexlib2.jar Fb dump ...

# whole-dex branch validity (work/BranchSweep.java). It takes seconds and covers every patch.
java -cp .:smali-dexlib2.jar BranchSweep verify/classes*.dex
```
