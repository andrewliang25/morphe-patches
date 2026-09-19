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

Together the seven patches rewrite 22 classes. `BranchSweep` then reads the 21 dex files. It
reports 197,471 classes and 630,827 methods. Every branch offset, try range and handler lands on an
instruction start.

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
