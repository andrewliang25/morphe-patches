# Facebook ad map & findings

Reference for the Facebook (`com.facebook.katana`) patches. It comes from a decompile of
**577.0.0.50.72** (versionCode 474426275, arm64-v8a, Android 11+), the version pinned in
`app/andrewliang/patches/shared/Constants.kt`.

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
```

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

| Patch | Target | Verified in the patched dex |
|---|---|---|
| `[Feed] Hide sponsored posts` | `LX/1lD;->addNewEdgeToCollection` guard on `GraphQLFeedStoryCategory.SPONSORED` (it is `A0K`) | The branch lands on original instruction 0. Try blocks moved from `@fb` to `@107` |
| `[Feed] Hide suggested and promoted posts` | The same chokepoint, plus a new `LX/1lD;->isSuggestedOrPromotedFeedUnit` | 15 `instance-of` arms, all of which branch to `@3e` |
| `[Stories] Hide sponsored stories` | `LX/awi;->B5t` returns its input list | `return-object v28`, which is `p3`, the `ImmutableList` |
| `[Reels] Hide sponsored reels` | `LX/50Q;->Cwp`, which is `maybeInsertAds` | `return-void` before the QPL marker, so no trace section stays open |
| `[Ad] Block background ad prefetch` | 8 void methods across 7 schedulers with kept names | All are `return-void`. Constructors and the `A00()Z` gate are untouched |
| `[Ad] Block ad telemetry` | 6 void methods across 4 classes with kept names | All are `return-void`. `onStartCommand` and the predicates are untouched |
| `[Ad] Disable Audience Network` | 5 manifest components | All have `android:enabled="false"` |
| `[Video] Download any video or reel` | 5 ownership checks across the 2 video menu builders | Each forced check is a `const/4` into the register its `move-result` wrote; the download-link null check is untouched |
| `[Stories] Download any story` | The one capability check in `StoryViewerMoreButtonCallback` | `const/4` into the register its `move-result` wrote, so the cached capability reads true |

Together the nine patches rewrite 23 classes. `BranchSweep` then reads the 21 dex files. It
reports 197,471 classes and 630,827 methods. Every branch offset, try range and handler lands on an
instruction start.

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
| Stories tray ads | `LX/awi;->B5t`, `AdBucketDataSourceUtil`, `StoryBucket.getBucketType() == 9` | ✅ |
| Reels and Watch feed ads | `LX/50Q;->Cwp`, `VideoHomeDataControllerAdsUtil`, `PoeAdsUtil`, `SfdAdsUtil`, `WatchAdStoryPool` | ✅ |
| Reels ad chrome | `FbShortsAdsRootKComponent`, `ReelsBannerAdsNativeComponent`, `ReelsAdsFloatingCtaPlugin`, `FbShortsAdsPostScrollNudge*` | Not necessary once insertion stops |
| In-stream ads (pre-roll, mid-roll, post-roll) | `AdBreakStateMachineImpl`, `AdBreakFetchHelper`, `UnifiedAdBreakController`, `InstreamAdFetchUtil` | ❌ no anchor |
| Pause ads | `PauseAdComponent`, `PauseAdUtil` | ❌ no anchor |
| Squeezeback ads (the live video becomes smaller) | `SqueezebackAdPlugin` (`LX/TZ5;`) | ❌ not built |
| Story-viewer ads | `StoryViewerAdsRootContainerComponentSpec`, `StoryViewerAdsVideoComponent`, `FBStoryAdsDelayedSkipManager` | ❌ not built |
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

## Media download

Facebook ships a complete download feature for video and for stories. `[Video] Download any video or reel` writes no
UI and no downloader: it unlocks what is already there.

### Reels have no download code of their own

The reel three-dots menu is the ordinary video menu. Nothing is named after reels:

```
LX/ToL;->A00      ("THREE_DOTS_MENU", "video_id", PlayerOrigin)
  -> LX/U9i;->A00(…, GraphQLStory, PlayerOrigin, …)
    -> LX/Scj;->A0e(LX/2LO;)LX/2xZ;     factory: base builder or subclass
      -> LX/2xZ;->A0i                   feed builder
      -> LX/Sct;->A0i                   player builder (extends the base)
```

**An earlier pass concluded reels had no download action, from searching `DOWNLOAD_REEL`,
`reel_download` and the reels packages and finding nothing.** That search was mis-aimed rather than
conclusive: the button exists, tagged `"DOWNLOAD_VIDEO"` like every other video. A name-based search
only proves a feature is not *named* after the surface — find the generic path the surface delegates
to and search there.

### The gate chain

The item is added only after all of these pass, in this order:

| # | Check | Patched |
|---|---|---|
| 1 | `BaseModelWithTree.getCachedBoolean(-1433294616)` | ✅ forced true |
| 2 | media playback object non-null | left — a real precondition |
| 3 | owner id non-empty | left |
| 4 | **`ownerId.equals(myUserId)`**, off `FbUserSessionImpl.A02` | ✅ forced true |
| 5 | `TreeJNI.getBooleanValue(1585574)` | ✅ forced true |
| 6 | **download link non-null** | ❌ **left on purpose** |

The player builder folds 1–4 into `LX/Scj;->A07(LX/Scj;, GraphQLStory)Z`, a 21-instruction predicate
with exactly one caller. The patch forces that call's result at the call site rather than rewriting
the predicate, so the helper stays intact for any future caller.

Each forced check replaces the `move-result` after the call with `const/4` into **the same register
that instruction already wrote**. `move-result` and `const/4` are both one code unit, so the layout,
the branch offsets and the register allocation are all unchanged; the call still runs and only its
answer is ignored. Branches are never edited.

**Gate 6 stays.** The link is a server-supplied value, and bypassing the null check would hand
`Uri.parse(null)` to the downloader. Where the server sends no link the item simply stays hidden,
which makes the patch self-limiting rather than broken.

### The open question

Facebook checks ownership **before** it reads the link, so its own code never evaluates that field
for anyone else's video. Whether the server populates it for content you do not own **cannot be
settled by disassembly**. If the download option does not appear on other people's videos on a
patched build, that is the answer, and this belongs in *Out of scope* rather than here.

### The downloader

`LX/UGl;->onMenuItemClick` is 34 instructions: it `Uri.parse`s the captured link and calls
**`LX/gQ5;->A01(Context, Uri, Fragment, FbUserSession, …)`**, a generic "save this URI to the device"
entry point with **nine call sites** across the app, with its own progress and error UI. The icon on
the menu item resolves by name to `drawable/fb_ic_download_24`. None of this is touched.

### Stories

Built, and a **better prospect than the video half**. The story viewer's More menu is
`com.facebook.stories.viewer.ui.buckets.regular.topbar.menu.StoryViewerMoreButtonCallback` — a kept
name — and it asks exactly **one** capability question before offering the save item. Everything
after it is unconditional; the surface enum it reads next only picks which label the item gets.

That question is a no-argument predicate on the menu's capability object, cached into a field on
first use. It returns `StoryBucket.A0k()` — "is this my own story" — for the regular viewer. The
predicate has **exactly one caller**, so forcing its result is scoped to saving and nothing else.
`StoryBucket.A0k()` itself must **not** be forced: `shouldShowViewCount`,
`isFeedbackBarSupportedForBucket` and several sibling capabilities read it too.

**Why it should outperform the video unlock:** the saving code works from the story's **own media
address**, the one the viewer is already playing (its failures are `MEDIA_URL_EMPTY`,
`VIDEO_FILE_MISSING`), not a separate download link the server may withhold. That address has to be
present for the story to render at all.

Neither the predicate nor its class is named in the patch. The menu class is a kept name, and the
action it creates is reached through the `"save_story_attempted"` event its handler reports — the
event appears in three methods, but only one is a one-argument `void`. The builder is then the only
method on the kept class that creates that action, and the capability is the only call in it taking
nothing and answering a boolean once `Boolean.booleanValue` is set aside.

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
